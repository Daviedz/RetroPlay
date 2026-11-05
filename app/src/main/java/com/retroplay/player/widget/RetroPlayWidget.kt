package com.retroplay.player.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.retroplay.player.RetroPlayApp

/** Home-screen widget: draws the player and handles the non-playback buttons and slider taps. */
class RetroPlayWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_CMD = "com.retroplay.player.WIDGET_CMD"
        const val EXTRA_CMD = "cmd"
        const val EXTRA_ARG = "arg"
    }

    private fun controller(ctx: Context) = (ctx.applicationContext as RetroPlayApp).controller

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        WidgetUpdater.invalidate()
        WidgetUpdater.pushAll(ctx, controller(ctx))
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        WidgetUpdater.invalidate()
        WidgetUpdater.pushAll(ctx, controller(ctx))
    }

    override fun onDeleted(ctx: Context, ids: IntArray) = WidgetUpdater.invalidate()
    override fun onEnabled(ctx: Context) = WidgetUpdater.invalidate()
    override fun onDisabled(ctx: Context) = WidgetUpdater.invalidate()

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action != ACTION_CMD) return
        val c = controller(ctx)
        val arg = intent.getIntExtra(EXTRA_ARG, 0)
        when (intent.getStringExtra(EXTRA_CMD)) {
            "eq" -> c.toggleEq()
            "size" -> c.widgetLcd = if (c.widgetLcd == 0) 1 else 0 // Small ⇄ Medium
            "art" -> WidgetUpdater.toggleArt(ctx)
            "shuffle" -> c.toggleShuffle()
            "repeat" -> c.toggleRepeat()
            "vol" -> {
                c.setVolumeLevel(arg / 10f) // 11 zones: 0 %, 10 % … 100 %
                c.endVolumeDrag()
            }
            "bal" -> c.setBalanceLevel((arg - 2) / 2f) // L, l, centre, r, R
            "seek" -> if (c.currentTrack?.isStream != true) c.seekTo((arg + 0.5f) / 20f)
        }
        c.save()
        WidgetUpdater.pushAll(ctx, c)
    }
}
