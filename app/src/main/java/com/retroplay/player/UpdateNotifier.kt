package com.retroplay.player

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.retroplay.player.player.Updater
import kotlin.concurrent.thread

/**
 * Checks GitHub for a new release about twice a day, even when the app is closed, and posts a
 * notification ("RetroPlay x.y.z is available"). Tapping it opens the app, which shows the Update dialog.
 * The alarm is re-created after a reboot or an app update.
 */
class UpdateNotifier : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        schedule(ctx)                                    // (re)arm; harmless if already set
        if (intent.action != ACTION_CHECK) return        // boot / app-replaced: only re-arm
        val app = ctx.applicationContext as Application
        val pending = goAsync()
        thread {
            try {
                val info = runCatching { Updater.fetchLatest() }.getOrNull()
                if (info != null && Updater.isNewer(info.version, Updater.installedVersion(app))) notify(app, info.version)
            } finally { pending.finish() }
        }
    }

    private fun notify(app: Application, version: String) {
        val prefs = app.getSharedPreferences("retroplay", Context.MODE_PRIVATE)
        if (prefs.getString("notifiedVersion", "") == version) return          // only once per release
        if (Build.VERSION.SDK_INT >= 33 &&
            app.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            NOTIF_ID,
            NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("RetroPlay $version is available")
                .setContentText("Tap to open RetroPlay and install the update.")
                .setContentIntent(open).setAutoCancel(true).build(),
        )
        prefs.edit().putString("notifiedVersion", version).apply()
    }

    companion object {
        const val ACTION_CHECK = "com.retroplay.player.CHECK_UPDATE"
        private const val CHANNEL = "updates"
        private const val NOTIF_ID = 4201

        fun schedule(ctx: Context) {
            val pi = PendingIntent.getBroadcast(
                ctx, 0, Intent(ctx, UpdateNotifier::class.java).setAction(ACTION_CHECK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            ctx.getSystemService(AlarmManager::class.java).setInexactRepeating(
                AlarmManager.RTC, System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR, AlarmManager.INTERVAL_HALF_DAY, pi,
            )
        }
    }
}
