package com.retroplay.player

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.retroplay.player.player.PlayerController

class RetroPlayApp : Application() {
    lateinit var controller: PlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(PlaybackService.CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW)
        )
        controller = PlayerController(this)
        UpdateNotifier.schedule(this)                    // background update check -> notification
    }
}
