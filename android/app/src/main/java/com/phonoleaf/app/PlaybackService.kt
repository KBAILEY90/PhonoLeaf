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
 * Deliberately NOT a MediaSession yet: lock-screen transport controls are a
 * separate (nice-to-have) step. This only needs to keep playback alive.
 */
class PlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "phonoleaf_playback"
        const val NOTIF_ID = 1001
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Create the channel up front so onStartCommand can call startForeground
        // as its very first action with zero setup — Android force-crashes the
        // app (ForegroundServiceDidNotStartInTimeException) if startForeground
        // doesn't happen within ~5s of startForegroundService().
        try { ensureChannel() } catch (_: Throwable) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground MUST be the first thing we do — the 5s watchdog is
        // already ticking. Read extras defensively; never do slow work before it.
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "PhonoLeaf"
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Reading aloud"
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(title, text),
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
            acquireCpuWakeLock()
            android.util.Log.i("PhonoLeafPlayback", "foreground service up, wakeLock=${wakeLock?.isHeld}")
        } catch (e: Throwable) {
            android.util.Log.w("PhonoLeafPlayback", "post-foreground setup failed: ${e.message}")
        }
        return START_NOT_STICKY
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

    override fun onDestroy() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Throwable) {}
        wakeLock = null
        super.onDestroy()
    }

    private fun buildNotification(title: String, text: String): android.app.Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val tap = if (launch != null) PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // IMMUTABLE required API 31+
        ) else null
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play) // stock icon: no asset needed
            .setContentIntent(tap)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW) // quiet, no sound/vibration
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
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
