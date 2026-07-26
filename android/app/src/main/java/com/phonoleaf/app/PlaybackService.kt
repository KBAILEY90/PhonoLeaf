package com.phonoleaf.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Minimal media-playback foreground service.
 *
 * Why this exists: our audio is a CHAIN of one-sentence clips played by the
 * WebView (<audio> + a JS onended → synthesize-next loop). When the app is
 * backgrounded, Android suspends it — the current clip and the one already
 * prefetched finish, then the chain dies (the exact symptom the owner saw). A
 * foreground service keeps the process alive so the loop keeps running with the
 * screen off.
 *
 * This replaces @jofr/capacitor-media-session, which crashed the app ~1-2s
 * after play: it targets Capacitor 6 / older Android, and foreground-service
 * rules are far stricter on targetSdk 36 (Android 16). Owning this means we can
 * satisfy them exactly:
 *   - declared with android:foregroundServiceType="mediaPlayback"
 *   - FOREGROUND_SERVICE + FOREGROUND_SERVICE_MEDIA_PLAYBACK permissions
 *   - startForeground() called IMMEDIATELY in onStartCommand (Android kills the
 *     app if that doesn't happen within ~5s — the likely old crash)
 *   - the matching service type passed to startForeground (required API 29+)
 *
 * MediaSessionCompat (2026-07-22) adds working lock-screen PLAY/PAUSE
 * buttons. Unlike the first cut of this feature, the foreground service now
 * SURVIVES a pause: index.html TTS._mediaState(playing) always calls
 * startPlaybackService (never stopPlaybackService) for both play and pause,
 * carrying a `playing` flag so this service can show "Playing"+Pause or
 * "Paused"+Play without tearing anything down — a pause-only teardown left
 * nothing to press "play" on afterward (owner-reported after the first
 * version shipped). The CPU wake lock still tracks play/pause precisely
 * (held only while actually playing — nothing to keep the CPU awake for
 * while paused). The service is only ever genuinely stopped by
 * TTS._mediaStop(), called from App.signOut() — there's no other "stop
 * reading this book" action in the app today (Reader.close() exists but has
 * no caller; minimizing keeps the mini-player, and playback is meant to
 * persist across tabs), so signing out is the one clear "done" signal.
 */
class PlaybackService : Service() {

    companion object {
        // True while this service is alive. Lets PhonoLeafTtsPlugin tell an
        // "update the notification of the service that's already running" call
        // apart from a "cold-start a new foreground service" one — Android's
        // background-start restriction only applies to the latter, and the
        // update case ORIGINATES from the background by definition (pressing
        // pause on the lock screen), so it must not be gated on the app being
        // foreground. @Volatile: written on the main thread, read from the
        // plugin's bridge call.
        @Volatile
        var isRunning = false
            private set

        // Book cover, shown behind the system's lock-screen media controls.
        // Held statically (NOT passed through the Intent): startService parcels
        // its extras through binder even for a same-process service, and a
        // bitmap would blow the ~1 MB transaction limit. The plugin decodes it
        // once per book and drops it here just before starting the service.
        @Volatile
        private var artwork: android.graphics.Bitmap? = null
        fun setArtwork(bmp: android.graphics.Bitmap?) { artwork = bmp }

        const val CHANNEL_ID = "phonoleaf_playback"
        const val NOTIF_ID = 1001
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_PAGE = "page"       // "Page X / Y" within the chapter; may be ""
        const val EXTRA_PLAYING = "playing" // boolean, defaults true — see updateMediaSession
        // The notification's OWN play/pause button targets this service
        // directly (a plain custom action, not android.intent.action.MEDIA_BUTTON)
        // — deliberately NOT MediaButtonReceiver: that path additionally needs a
        // manifest <receiver> and routing hardware/Bluetooth KeyEvents through
        // MediaButtonReceiver.handleIntent(), which isn't verifiable without a
        // device. Lock-screen/quick-settings transport controls DON'T need any
        // of that — Android delivers those to MediaSessionCompat.Callback
        // automatically once the session is active, which is all this uses.
        const val ACTION_PAUSE = "com.phonoleaf.app.ACTION_PAUSE_PLAYBACK"
        const val ACTION_PLAY = "com.phonoleaf.app.ACTION_PLAY_PLAYBACK"
        const val ACTION_PREV_PAGE = "com.phonoleaf.app.ACTION_PREV_PAGE"
        const val ACTION_NEXT_PAGE = "com.phonoleaf.app.ACTION_NEXT_PAGE"
        const val ACTION_PREV_CHAPTER = "com.phonoleaf.app.ACTION_PREV_CHAPTER"
        const val ACTION_NEXT_CHAPTER = "com.phonoleaf.app.ACTION_NEXT_CHAPTER"

        // Chapter skip has no standard PlaybackState action, so it goes through
        // CUSTOM actions. Android 13+ builds the media UI from the SESSION (not
        // the notification's actions) and allocates 5 slots: play/pause,
        // SKIP_TO_PREVIOUS, SKIP_TO_NEXT, then custom actions in order. Mapping
        // page turns onto skip-prev/next and chapters onto the two custom slots
        // fills all five exactly, and puts the page buttons in the slots that
        // also survive into the collapsed/compact view.
        const val CUSTOM_PREV_CHAPTER = "phonoleaf.prev_chapter"
        const val CUSTOM_NEXT_CHAPTER = "phonoleaf.next_chapter"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSessionCompat? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        // Create the channel up front so onStartCommand can call startForeground
        // as its very first action with zero setup — Android force-crashes the
        // app (ForegroundServiceDidNotStartInTimeException) if startForeground
        // doesn't happen within ~5s of startForegroundService().
        try { ensureChannel() } catch (_: Throwable) {}
        try { setupMediaSession() } catch (_: Throwable) {}
    }

    private fun setupMediaSession() {
        val session = MediaSessionCompat(this, "PhonoLeafPlayback")
        session.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        session.setCallback(object : MediaSessionCompat.Callback() {
            // Lock screen / quick settings / Bluetooth transport controls all
            // route here automatically once the session is active — forward to
            // JS, which decides what to actually do (see TTS._mediaSetup).
            override fun onPause() {
                try { PhonoLeafTtsPlugin.notifyMediaButton("pause") } catch (_: Throwable) {}
            }
            override fun onPlay() {
                try { PhonoLeafTtsPlugin.notifyMediaButton("play") } catch (_: Throwable) {}
            }
            override fun onSkipToPrevious() {
                try { PhonoLeafTtsPlugin.notifyMediaButton("prevPage") } catch (_: Throwable) {}
            }
            override fun onSkipToNext() {
                try { PhonoLeafTtsPlugin.notifyMediaButton("nextPage") } catch (_: Throwable) {}
            }
            override fun onCustomAction(action: String?, extras: android.os.Bundle?) {
                val which = when (action) {
                    CUSTOM_PREV_CHAPTER -> "prevChapter"
                    CUSTOM_NEXT_CHAPTER -> "nextChapter"
                    else -> return
                }
                try { PhonoLeafTtsPlugin.notifyMediaButton(which) } catch (_: Throwable) {}
            }
        })
        mediaSession = session
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A notification button tap (routed here directly, not via the media
        // session — see ACTION_PAUSE's comment above). Same JS round-trip as the
        // session callbacks either way; JS decides what actually happens and
        // calls back into _mediaState, which updates this notification. These
        // never touch the foreground state, so no startForeground() is needed.
        val button = when (intent?.action) {
            ACTION_PAUSE -> "pause"
            ACTION_PLAY -> "play"
            ACTION_PREV_PAGE -> "prevPage"
            ACTION_NEXT_PAGE -> "nextPage"
            ACTION_PREV_CHAPTER -> "prevChapter"
            ACTION_NEXT_CHAPTER -> "nextChapter"
            else -> null
        }
        if (button != null) {
            try { PhonoLeafTtsPlugin.notifyMediaButton(button) } catch (_: Throwable) {}
            return START_NOT_STICKY
        }
        // startForeground MUST be the first thing we do — the 5s watchdog is
        // already ticking. Read extras defensively; never do slow work before it.
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "PhonoLeaf"
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Reading aloud"
        val page = intent?.getStringExtra(EXTRA_PAGE) ?: ""
        val playing = intent?.getBooleanExtra(EXTRA_PLAYING, true) ?: true
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(title, text, page, playing),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
            )
        } catch (e: Throwable) {
            // startForeground was refused (e.g. FGS-start not allowed from the
            // current state). The caller (PhonoLeafTtsPlugin) already gates this
            // on the app being foreground, so this is a last-ditch guard: stop
            // cleanly and never take the app down. Foreground reading is fine.
            android.util.Log.w("PhonoLeafPlayback", "startForeground refused: ${e.message}")
            try { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
            stopSelf()
            return START_NOT_STICKY
        }
        // Only after we're safely foreground do the rest (can't crash the watchdog now).
        try {
            if (playing) acquireCpuWakeLock() else releaseCpuWakeLock()
            updateMediaSession(title, text, page, playing)
            android.util.Log.i("PhonoLeafPlayback", "foreground service up ($title/$text/$page, playing=$playing), wakeLock=${wakeLock?.isHeld}")
        } catch (e: Throwable) {
            android.util.Log.w("PhonoLeafPlayback", "post-foreground setup failed: ${e.message}")
        }
        return START_NOT_STICKY
    }

    // Marks the session active + PLAYING/PAUSED with matching metadata. Called
    // on every onStartCommand, including the metadata-only refreshes
    // TTS._mediaMeta triggers as the chapter changes while playing (see
    // index.html) — cheap to repeat. Both actions are always advertised so
    // the system UI shows the right one (or toggles) regardless of OEM.
    private fun updateMediaSession(title: String, text: String, page: String, playing: Boolean) {
        val session = mediaSession ?: return
        // The modern system media UI shows metadata, not the notification's own
        // title/text, so the chapter and page have to be combined into one
        // subtitle line here to be visible on the lock screen.
        val subtitle = if (page.isNotEmpty()) "$text · $page" else text
        val md = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
        artwork?.let {
            // ALBUM_ART is what the system media player renders behind the
            // controls; ART is the older key some OEM skins still read.
            md.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
            md.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
        }
        session.setMetadata(md.build())
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or // = previous page
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT        // = next page
                )
                .addCustomAction(
                    PlaybackStateCompat.CustomAction.Builder(
                        CUSTOM_PREV_CHAPTER, "Previous chapter", android.R.drawable.ic_media_rew
                    ).build()
                )
                .addCustomAction(
                    PlaybackStateCompat.CustomAction.Builder(
                        CUSTOM_NEXT_CHAPTER, "Next chapter", android.R.drawable.ic_media_ff
                    ).build()
                )
                .setState(
                    if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f
                )
                .build()
        )
        session.isActive = true
    }

    /**
     * PARTIAL wake lock = keep the CPU running with the screen off.
     *
     * The foreground service only stops the app being KILLED; it does NOT stop
     * the CPU sleeping when the screen locks. Our playback needs the CPU because
     * every sentence runs JS (the onended → synthesize-next loop) and native
     * Piper inference — so without this, playback died a sentence or two after
     * locking (i.e. once the pre-generated buffer ran out), even with the
     * service running and battery set to unrestricted.
     *
     * NB the app's other wake lock (navigator.wakeLock) is a SCREEN lock, which
     * Android releases the instant the screen turns off — useless here.
     */
    private fun acquireCpuWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhonoLeaf:playback").apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L) // safety timeout: never outlive a listening session
            }
        } catch (e: Throwable) {
            android.util.Log.w("PhonoLeafPlayback", "wake lock failed: ${e.message}")
        }
    }

    // Paused playback needs no CPU (nothing is generating/advancing) — release
    // it so a long pause (service now stays alive across pauses, see the class
    // doc) doesn't hold the CPU awake for no reason. Re-acquired by the next
    // acquireCpuWakeLock() call when play resumes.
    private fun releaseCpuWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        isRunning = false
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Throwable) {}
        wakeLock = null
        try { mediaSession?.isActive = false; mediaSession?.release() } catch (_: Throwable) {}
        mediaSession = null
        super.onDestroy()
    }

    // Each button needs its OWN request code: FLAG_UPDATE_CURRENT reuses a
    // cached PendingIntent keyed by (requestCode, ...), so sharing one would let
    // the buttons collide and fire each other's action. 0 is the content tap.
    private fun notifAction(icon: Int, label: String, action: String, requestCode: Int): NotificationCompat.Action {
        val pi = PendingIntent.getService(
            this, requestCode, Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // IMMUTABLE required API 31+
        )
        return NotificationCompat.Action(icon, label, pi)
    }

    private fun buildNotification(title: String, text: String, page: String, playing: Boolean): android.app.Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val tap = if (launch != null) PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) else null
        val toggle = if (playing)
            notifAction(android.R.drawable.ic_media_pause, "Pause", ACTION_PAUSE, 2)
        else
            notifAction(android.R.drawable.ic_media_play, "Play", ACTION_PLAY, 3)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            // The app's own launcher icon, not a stock control glyph — this is
            // the notification's badge/identity icon (tapping the body/icon
            // opens the app via `tap` below, which is standard for ALL media
            // notifications), not a playback control. The old stock play-
            // triangle (android.R.drawable.ic_media_play) looked exactly like
            // an extra play button and confused it for one (owner-reported).
            // MediaStyle notifications show this un-tinted on the lock-screen
            // media widget (unlike the plain status bar, which always forces
            // small icons to a flat monochrome silhouette).
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(tap)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW) // quiet, no sound/vibration
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Order matters — the compact view below indexes into this list.
            .addAction(notifAction(android.R.drawable.ic_media_rew, "Previous chapter", ACTION_PREV_CHAPTER, 4))
            .addAction(notifAction(android.R.drawable.ic_media_previous, "Previous page", ACTION_PREV_PAGE, 5))
            .addAction(toggle)
            .addAction(notifAction(android.R.drawable.ic_media_next, "Next page", ACTION_NEXT_PAGE, 6))
            .addAction(notifAction(android.R.drawable.ic_media_ff, "Next chapter", ACTION_NEXT_CHAPTER, 7))
        if (page.isNotEmpty()) builder.setSubText(page)
        artwork?.let { builder.setLargeIcon(it) }
        // MediaStyle: purely cosmetic/display (tells the system to render this
        // as a media notification and back the lock-screen media widget with
        // this session) — independent of how button taps are routed above.
        // Compact view keeps the three middle actions: previous page,
        // play/pause, next page (indices 1, 2, 3 of the list added above).
        mediaSession?.sessionToken?.let { token ->
            builder.setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(token)
                    .setShowActionsInCompactView(1, 2, 3)
            )
        }
        return builder.build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps reading aloud while the app is in the background"
                setShowBadge(false)
            }
        )
    }
}
