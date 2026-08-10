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
 * Minimal foreground service that keeps a voice-pack download alive with the
 * screen off.
 *
 * Why this exists: downloadPack() in PhonoLeafTtsPlugin.kt runs a plain
 * background thread with no protection at all — unlike TTS playback, which
 * already needed (and has, see PlaybackService.kt) a foreground service PLUS
 * a CPU wake lock to survive the screen locking. A voice pack is 65-140 MB;
 * almost nobody keeps the screen on for the couple of minutes that takes.
 * Without this, Android's Doze/App Standby suspends the download thread and
 * throttles network access once the screen locks, so the transfer stalls and
 * never completes (owner-reported 2026-08-08: "if I download language packs,
 * it never completes the download... because the download fails if the
 * screen shuts down").
 *
 * Deliberately much simpler than PlaybackService: no media session, no lock-
 * screen controls, just a progress notification and a wake lock — but it
 * follows the exact same hard-won rules, since they're Android's rules, not
 * this app's:
 *   - declared with android:foregroundServiceType="dataSync" (the type
 *     Android's own docs specify for "transferring data through the network
 *     and shouldn't be interrupted by the system" — mediaPlayback would be
 *     the wrong type for this and could be rejected/killed as a mismatch)
 *   - FOREGROUND_SERVICE + FOREGROUND_SERVICE_DATA_SYNC permissions
 *   - startForeground() called IMMEDIATELY in onStartCommand
 *   - context.startService() (NOT startForegroundService()) to start it —
 *     PlaybackService's own history found startForegroundService() arms a ~5s
 *     watchdog that can fire (ForegroundServiceDidNotStartInTimeException)
 *     if the main thread is busy; startService() doesn't arm it and is valid
 *     here because a download always starts from a user tapping Download
 *     while the app is genuinely in the foreground.
 *
 * Multiple downloads can be queued (PhonoLeafTtsPlugin's downloadExecutor is
 * single-threaded, so only one ever runs at once, but several can be
 * pending). Tracked with a simple counter rather than one service per
 * download: start() increments it and starts the service on the 0→1
 * transition; finish() decrements it and stops the service on 1→0. A
 * mid-queue transition from one pack to the next just updates the existing
 * notification's text/progress instead of stopping and restarting.
 */
class PackDownloadService : Service() {

    companion object {
        @Volatile private var active = 0
        @Volatile private var currentModel: String? = null
        @Volatile private var currentPct: Int = 0

        const val CHANNEL_ID = "phonoleaf_download"
        const val NOTIF_ID = 1002
        const val EXTRA_MODEL = "model"
        const val EXTRA_PCT = "pct"

        /** Call right before a download task actually starts running. */
        fun start(context: Context, model: String) {
            active++
            currentModel = model
            currentPct = 0
            val i = Intent(context, PackDownloadService::class.java)
            i.putExtra(EXTRA_MODEL, model)
            i.putExtra(EXTRA_PCT, 0)
            try { context.startService(i) } catch (e: Throwable) {
                // Never let a notification/service problem block the download
                // itself — worst case it just doesn't survive the screen
                // locking, same as before this fix.
                android.util.Log.w("PhonoLeafDownload", "startService failed: ${e.message}")
                active = maxOf(0, active - 1)
            }
        }

        /** Progress update for whichever pack is currently showing. */
        fun progress(context: Context, model: String, pct: Int) {
            if (active <= 0) return // service not up (start() failed) — nothing to update
            currentModel = model
            currentPct = pct
            val i = Intent(context, PackDownloadService::class.java)
            i.putExtra(EXTRA_MODEL, model)
            i.putExtra(EXTRA_PCT, pct)
            try { context.startService(i) } catch (_: Throwable) {}
        }

        /** Call when a download task ends, however it ends (success/fail/cancel). */
        fun finish(context: Context) {
            active = maxOf(0, active - 1)
            if (active == 0) {
                try { context.stopService(Intent(context, PackDownloadService::class.java)) }
                catch (_: Throwable) {}
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try { ensureChannel() } catch (_: Throwable) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val model = intent?.getStringExtra(EXTRA_MODEL) ?: currentModel ?: "voice"
        val pct = intent?.getIntExtra(EXTRA_PCT, currentPct) ?: currentPct
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(model, pct),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
            acquireWakeLock()
        } catch (e: Throwable) {
            android.util.Log.w("PhonoLeafDownload", "startForeground refused: ${e.message}")
            try { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
            stopSelf()
        }
        return START_NOT_STICKY
    }

    // PARTIAL wake lock: the foreground service alone stops the app being
    // killed, not the CPU sleeping once the screen locks, and the download
    // thread needs the CPU (and the radio active) to keep pulling bytes.
    // Capped at 30 minutes — long enough for the biggest pack (~140 MB) on a
    // slow connection, short enough that a stuck download can't hold the CPU
    // awake indefinitely if something goes wrong.
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhonoLeaf:download").apply {
                setReferenceCounted(false)
                acquire(30 * 60 * 1000L)
            }
        } catch (e: Throwable) {
            android.util.Log.w("PhonoLeafDownload", "wake lock failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Throwable) {}
        wakeLock = null
        super.onDestroy()
    }

    private fun buildNotification(model: String, pct: Int): android.app.Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val tap = if (launch != null) PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) else null
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading voice pack")
            .setContentText("$pct%")
            .setProgress(100, pct.coerceIn(0, 100), pct <= 0)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tap)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Voice downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps a voice pack download going while the screen is off"
                setShowBadge(false)
            }
        )
    }
}
