package com.retroplay.player

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.retroplay.player.player.PlayState
import com.retroplay.player.player.PlayerController

/** Keeps the process alive while music plays, with a notification holding the controls. */
class PlaybackService : Service() {

    companion object {
        const val CHANNEL = "playback"
        const val ACTION_TOGGLE = "com.retroplay.player.TOGGLE"
        const val ACTION_NEXT = "com.retroplay.player.NEXT"
        const val ACTION_PREV = "com.retroplay.player.PREV"
        const val ACTION_STOP = "com.retroplay.player.STOP"
        /** Widget Play: resume when paused, otherwise (re)start the track, like Winamp. */
        const val ACTION_PLAY = "com.retroplay.player.PLAY"
        /** Widget Pause: toggles pause. */
        const val ACTION_PAUSE = "com.retroplay.player.PAUSE"
        private const val NOTIF_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val c = (application as RetroPlayApp).controller
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        // Always go foreground first: widget buttons start this service with
        // startForegroundService(), which must be answered within a few seconds.
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(c), type)
        when (intent?.action) {
            ACTION_TOGGLE -> if (c.playState == PlayState.STOPPED) c.play() else c.pause()
            ACTION_PLAY -> c.play()
            ACTION_PAUSE -> c.pause()
            ACTION_NEXT -> c.next()
            ACTION_PREV -> c.prev()
            ACTION_STOP -> c.stop()
        }
        if (c.playState == PlayState.STOPPED && !c.isStarting) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(c), type)
        return START_NOT_STICKY
    }

    private fun buildNotification(c: PlayerController): Notification {
        val t = c.playlist.getOrNull(c.current)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(a: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, PlaybackService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE
        )
        val playing = c.playState == PlayState.PLAYING
        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(c.session.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setLargeIcon(c.currentArt)
            .setContentTitle(t?.title ?: getString(R.string.app_name))
            .setContentText(
                when {
                    t?.isStream == true -> "Live radio"
                    !t?.artist.isNullOrBlank() -> t?.artist
                    else -> if (playing) "Playing" else "Paused"
                }
            )
            .setContentIntent(open)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(style)
            .addAction(R.drawable.ic_prev, "Previous", action(ACTION_PREV, 1))
            .addAction(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                if (playing) "Pause" else "Play",
                action(ACTION_TOGGLE, 2),
            )
            .addAction(R.drawable.ic_next, "Next", action(ACTION_NEXT, 3))
            .addAction(R.drawable.ic_stop, "Stop", action(ACTION_STOP, 4))
            .build()
    }
}
