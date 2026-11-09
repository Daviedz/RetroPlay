package com.retroplay.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AColor
import android.os.Build
import android.os.PowerManager
import android.util.SizeF
import android.util.TypedValue
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.retroplay.player.MainActivity
import com.retroplay.player.PlaybackService
import com.retroplay.player.R
import com.retroplay.player.player.PlayState
import com.retroplay.player.player.PlayerController
import com.retroplay.player.ui.Skin
import com.retroplay.player.ui.marqueeText
import com.retroplay.player.ui.skinFor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pushes player state to every home-screen widget.
 * - A full update whenever something visible changes (track, state, toggles, volume…).
 * - A cheap partial update (LCD + seek bitmaps only) twice a second while playing
 *   and the screen is on, so the clock and spectrum move.
 */
object WidgetUpdater {

    private val VOL_ZONES = intArrayOf(
        R.id.w_vol_z0, R.id.w_vol_z1, R.id.w_vol_z2, R.id.w_vol_z3, R.id.w_vol_z4, R.id.w_vol_z5,
        R.id.w_vol_z6, R.id.w_vol_z7, R.id.w_vol_z8, R.id.w_vol_z9, R.id.w_vol_z10,
    )
    private val BAL_ZONES = intArrayOf(R.id.w_bal_z0, R.id.w_bal_z1, R.id.w_bal_z2, R.id.w_bal_z3, R.id.w_bal_z4)
    private val SEEK_ZONES = intArrayOf(
        R.id.w_seek_z0, R.id.w_seek_z1, R.id.w_seek_z2, R.id.w_seek_z3, R.id.w_seek_z4,
        R.id.w_seek_z5, R.id.w_seek_z6, R.id.w_seek_z7, R.id.w_seek_z8, R.id.w_seek_z9,
        R.id.w_seek_z10, R.id.w_seek_z11, R.id.w_seek_z12, R.id.w_seek_z13, R.id.w_seek_z14,
        R.id.w_seek_z15, R.id.w_seek_z16, R.id.w_seek_z17, R.id.w_seek_z18, R.id.w_seek_z19,
    )
    private val BUTTONS = intArrayOf(
        R.id.w_eq, R.id.w_pl, R.id.w_art, R.id.w_ml,
        R.id.w_prev, R.id.w_play, R.id.w_pause, R.id.w_stop, R.id.w_next, R.id.w_eject,
        R.id.w_shuf, R.id.w_rep,
    )

    @Volatile
    private var cachedIds: IntArray? = null
    private var lastSig: List<Any?>? = null

    /** True while RetroPlay's own screen is showing (the home screen isn't, so skip live redraws). */
    @Volatile
    var appVisible = false

    fun invalidate() {
        cachedIds = null
        lastSig = null
    }

    private fun ids(ctx: Context): IntArray = cachedIds ?: AppWidgetManager.getInstance(ctx)
        .getAppWidgetIds(ComponentName(ctx, RetroPlayWidget::class.java))
        .also { cachedIds = it }

    // ----------------------------------------------------------- ART mode

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("widget", Context.MODE_PRIVATE)
    fun artMode(ctx: Context) = prefs(ctx).getBoolean("art", false)
    fun toggleArt(ctx: Context) = prefs(ctx).edit().putBoolean("art", !artMode(ctx)).apply()

    // -------------------------------------------------------------- update

    /** Called by the controller every 500 ms. */
    private var diagCount = 0

    /** One-line state summary for the exported debug log. */
    fun diag(ctx: Context, c: PlayerController): String {
        val interactive = try { ctx.getSystemService(PowerManager::class.java).isInteractive } catch (_: Throwable) { null }
        return "widgetIds=${ids(ctx).size} interactive=$interactive appVisible=$appVisible skin=${c.skin} vis=${c.visMode} " +
            "state=${c.playState} pos=${c.positionMs} sigChanged=${signature(ctx, c) != lastSig}"
    }

    fun onTick(ctx: Context, c: PlayerController) {
        if (diagCount++ % 10 == 0) android.util.Log.i("RetroPlayWidget", "tick: " + diag(ctx, c))
        val ids = ids(ctx)
        if (ids.isEmpty()) return
        if (!ctx.getSystemService(PowerManager::class.java).isInteractive) return
        val sig = signature(ctx, c)
        if (sig != lastSig) pushAll(ctx, c)
        else if (c.playState == PlayState.PLAYING && !appVisible) {
            val mgr = AppWidgetManager.getInstance(ctx)
            ids.forEach { id ->
                try { mgr.partiallyUpdateAppWidget(id, build(ctx, c, id, full = false)) } catch (e: Throwable) { android.util.Log.w("RetroPlayWidget", "partial update failed", e) }
            }
        }
    }

    fun pushAll(ctx: Context, c: PlayerController) {
        val ids = ids(ctx)
        lastSig = signature(ctx, c)
        val mgr = AppWidgetManager.getInstance(ctx)
        ids.forEach { id ->
            try { mgr.updateAppWidget(id, build(ctx, c, id, full = true)) } catch (e: Throwable) { android.util.Log.w("RetroPlayWidget", "full update failed", e) }
        }
    }

    private fun signature(ctx: Context, c: PlayerController): List<Any?> = listOf(
        c.playState, c.current, c.currentTrack?.uri, c.playlist.isEmpty(),
        c.shuffleOn, c.repeatOn, c.eqOn, c.showPl, c.skin,
        (c.volume * 100).roundToInt(), c.balance, c.info, c.durationMs,
        c.currentArt != null, artMode(ctx), c.showRemaining, c.hasVisPermission, c.visMode, c.widgetLcd,
        if (c.playState != PlayState.PLAYING) c.positionMs / 1000 else null,
    )

    // ------------------------------------------------------- geometry

    // Small hides the EQ / PL / ART / ML row so the whole widget fits in 2 rows.
    private const val LCD_SMALL = 48
    private const val LCD_MEDIUM = 92

    private class Geo(val titleChars: Int, val lcdDp: Int, val d: Float, val lcdW: Int, val lcdH: Int, val volW: Int, val balW: Int, val seekW: Int, val barH: Int, val seekH: Int)

    /** Mirrors the dp sizes in widget_player.xml so bitmaps match their views' aspect. */
    private fun geometry(ctx: Context, id: Int, size: Int): Geo {
        val o = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(id)
        val land = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val wDp = (if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH))
            .takeIf { it > 0 } ?: 320
        var hDp = (if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT))
            .takeIf { it > 0 } ?: 200
        if (Build.VERSION.SDK_INT >= 31) {
            // Newer launchers report the exact sizes here; prefer the tallest one
            val sizes: ArrayList<SizeF>? =
                if (Build.VERSION.SDK_INT >= 33) o.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
                else @Suppress("DEPRECATION") o.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            sizes?.maxOfOrNull { it.height }?.toInt()?.takeIf { it > 0 }?.let { hDp = maxOf(hDp, it) }
        }
        val d = min(ctx.resources.displayMetrics.density, 2f)
        val inner = (wDp - 12).coerceAtLeast(200)
        val lcdW = (inner - 6) * 0.42f
        // Small / Medium are fixed sizes and never depend on what the launcher reports
        // (some launchers report a smaller height than they show). The right-hand column
        // needs 66dp, so the display is never shorter than that.
        // Large fills the widget: everything except the LCD row takes about 110dp.
        val lcdH = if (size == 0) LCD_SMALL else LCD_MEDIUM
        val sliders = (inner - 62).toFloat()
        // Title box: right column minus its 4dp side padding. Bold monospace at 11dp is ~6.7dp per char.
        val titleBox = (inner - 6) * 0.58f - 8
        val titleChars = (titleBox / 6.9f).toInt().coerceAtLeast(8)
        return Geo(
            titleChars, lcdH, d,
            (lcdW * d).toInt(), (lcdH * d).toInt(),
            (sliders * 0.62f * d).toInt(), (sliders * 0.38f * d).toInt(),
            (inner * d).toInt(), (16 * d).toInt(), (14 * d).toInt(),
        )
    }

    // ------------------------------------------------------- views

    private fun build(ctx: Context, c: PlayerController, id: Int, full: Boolean): RemoteViews {
        val s = skinFor(c.skin)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_player)
        val g = geometry(ctx, id, c.widgetLcd)
        val art = artMode(ctx)

        rv.setImageViewBitmap(R.id.w_lcd, WidgetRenderer.lcd(c, s, g.lcdW, g.lcdH, g.d, art))
        if (Build.VERSION.SDK_INT >= 31) {
            // Set the display height explicitly too, rather than relying only on the bitmap size
            rv.setViewLayoutHeight(R.id.w_lcd, g.lcdDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        }
        // Clock as plain text on top of the LCD picture (sized/placed like the picture's own clock)
        val clock = WidgetRenderer.clockText(c)
        val availDp = g.lcdW / g.d - 10f - 8f - 5f
        val tsDp = min(g.lcdDp * 0.36f, availDp / (clock.length * 0.6f))
        rv.setTextViewText(R.id.w_clock, clock)
        rv.setTextViewTextSize(R.id.w_clock, TypedValue.COMPLEX_UNIT_DIP, tsDp)
        rv.setTextColor(R.id.w_clock, (if (c.playState == PlayState.STOPPED) s.lcdDim else s.lcdText).toArgb())
        rv.setViewPadding(R.id.w_clock, (18 * g.d).toInt(), (5 * g.d).toInt(), 0, 0)
        val radio = c.currentTrack?.isStream == true
        val pos = when {
            radio -> com.retroplay.player.player.RadioSweep.fraction(
                c.currentTrack, c.playState == com.retroplay.player.player.PlayState.PLAYING)
            c.durationMs > 0 -> c.positionMs.toFloat() / c.durationMs
            else -> 0f
        }
        val seekFill = 0xFFFFF59D.toInt()
        rv.setImageViewBitmap(R.id.w_seek, WidgetRenderer.slider(s, pos, seekFill, false, g.seekW, g.seekH, g.d, gradient = true))
        if (!full) return rv

        val volFill = if (s.isClassic) volumeColor(c.volume) else s.volumeFill.toArgb()
        rv.setImageViewBitmap(R.id.w_vol, WidgetRenderer.slider(s, c.volume, volFill, false, g.volW, g.barH, g.d))
        rv.setImageViewBitmap(
            R.id.w_bal,
            WidgetRenderer.slider(s, (c.balance + 1f) / 2f, (if (c.balance == 0f) 0xFF33C24A.toInt() else 0xFFE53935.toInt()), true, g.balW, g.barH, g.d),
        )

        // ---- skin
        val classic = s.isClassic
        val btnBg = if (classic) R.drawable.widget_btn_classic else R.drawable.widget_btn_modern
        val lcdBg = if (classic) R.drawable.widget_lcd_classic else R.drawable.widget_lcd_modern
        rv.setInt(R.id.w_root, "setBackgroundResource", if (classic) R.drawable.widget_face_classic else R.drawable.widget_face_modern)
        rv.setInt(R.id.w_title, "setBackgroundResource", if (classic) R.drawable.widget_title_classic else R.drawable.widget_title_modern)
        val stripes = if (classic) R.drawable.widget_stripes_classic else R.drawable.widget_stripes_modern
        rv.setInt(R.id.w_stripe1, "setBackgroundResource", stripes)
        rv.setInt(R.id.w_stripe2, "setBackgroundResource", stripes)
        intArrayOf(R.id.w_lcd, R.id.w_marquee_box, R.id.w_kbps, R.id.w_khz).forEach {
            rv.setInt(it, "setBackgroundResource", lcdBg)
        }
        BUTTONS.forEach { rv.setInt(it, "setBackgroundResource", btnBg) }
        rv.setTextColor(R.id.w_menu, s.titleText.toArgb())
        rv.setTextColor(R.id.w_title_text, s.titleText.toArgb())
        val label = s.labelText.toArgb()
        intArrayOf(R.id.w_kbps_l, R.id.w_khz_l, R.id.w_vol_l, R.id.w_bal_l).forEach { rv.setTextColor(it, label) }

        // ---- text
        rv.removeAllViews(R.id.w_title_flip)
        titlePieces(marqueeText(c, upper = true).substringBefore("   ***").trim(), g.titleChars).forEach { piece ->
            val t = RemoteViews(ctx.packageName, R.layout.widget_title_piece)
            t.setTextViewText(R.id.w_piece, piece)
            t.setTextColor(R.id.w_piece, s.lcdText.toArgb())
            rv.addView(R.id.w_title_flip, t)
        }
        rv.setTextViewText(R.id.w_kbps, c.info?.kbps?.takeIf { it > 0 }?.toString() ?: "")
        rv.setTextViewText(R.id.w_khz, c.info?.khz?.takeIf { it > 0 }?.toString() ?: "")
        rv.setTextColor(R.id.w_kbps, s.lcdText.toArgb())
        rv.setTextColor(R.id.w_khz, s.lcdText.toArgb())
        rv.setTextViewText(
            R.id.w_stereo,
            when (c.info?.stereo) { true -> "stereo"; false -> "mono"; null -> "" },
        )
        rv.setTextColor(R.id.w_stereo, label)

        // ---- toggles with LEDs (the row is hidden in Small)
        rv.setViewVisibility(R.id.w_toggles, if (c.widgetLcd == 0) View.GONE else View.VISIBLE)
        led(rv, s, R.id.w_eq_led, c.eqOn)
        led(rv, s, R.id.w_pl_led, c.showPl)
        led(rv, s, R.id.w_art_led, art)
        led(rv, s, R.id.w_ml_led, false)
        led(rv, s, R.id.w_shuf_led, c.shuffleOn)
        led(rv, s, R.id.w_rep_led, c.repeatOn)
        intArrayOf(R.id.w_eq_text, R.id.w_pl_text, R.id.w_art_text, R.id.w_ml_text, R.id.w_shuf_text)
            .forEach { rv.setTextColor(it, s.btnText.toArgb()) }
        rv.setInt(R.id.w_rep_icon, "setColorFilter", (if (c.repeatOn) s.activeGlyph else s.btnText).toArgb())

        // ---- transport: the button matching the state is lit green
        val st = c.playState
        val hasTrack = c.currentTrack != null
        transport(rv, s, R.id.w_prev_icon, R.id.w_prev_led, false)
        transport(rv, s, R.id.w_play_icon, R.id.w_play_led, st == PlayState.PLAYING)
        transport(rv, s, R.id.w_pause_icon, R.id.w_pause_led, st == PlayState.PAUSED)
        transport(rv, s, R.id.w_stop_icon, R.id.w_stop_led, st == PlayState.STOPPED && hasTrack)
        transport(rv, s, R.id.w_next_icon, R.id.w_next_led, false)
        transport(rv, s, R.id.w_eject_icon, R.id.w_eject_led, false)

        // ---- clicks
        rv.setOnClickPendingIntent(R.id.w_prev, service(ctx, PlaybackService.ACTION_PREV, 11))
        rv.setOnClickPendingIntent(
            R.id.w_play,
            if (c.playlist.isEmpty()) activity(ctx, "files", 101) else service(ctx, PlaybackService.ACTION_PLAY, 12),
        )
        rv.setOnClickPendingIntent(R.id.w_pause, service(ctx, PlaybackService.ACTION_PAUSE, 13))
        rv.setOnClickPendingIntent(R.id.w_stop, service(ctx, PlaybackService.ACTION_STOP, 14))
        rv.setOnClickPendingIntent(R.id.w_next, service(ctx, PlaybackService.ACTION_NEXT, 15))
        rv.setOnClickPendingIntent(R.id.w_eject, activity(ctx, "files", 101))
        rv.setOnClickPendingIntent(R.id.w_menu, command(ctx, "size", 0, 204))
        rv.setOnClickPendingIntent(R.id.w_title_text, activity(ctx, null, 100))
        rv.setOnClickPendingIntent(R.id.w_lcd, activity(ctx, null, 100))
        rv.setOnClickPendingIntent(R.id.w_marquee_box, activity(ctx, null, 100))
        rv.setOnClickPendingIntent(R.id.w_pl, activity(ctx, "playlist", 102))
        rv.setOnClickPendingIntent(R.id.w_ml, activity(ctx, "library", 103))
        rv.setOnClickPendingIntent(R.id.w_eq, command(ctx, "eq", 0, 200))
        rv.setOnClickPendingIntent(R.id.w_art, command(ctx, "art", 0, 201))
        rv.setOnClickPendingIntent(R.id.w_shuf, command(ctx, "shuffle", 0, 202))
        rv.setOnClickPendingIntent(R.id.w_rep, command(ctx, "repeat", 0, 203))
        VOL_ZONES.forEachIndexed { i, z -> rv.setOnClickPendingIntent(z, command(ctx, "vol", i, 300 + i)) }
        BAL_ZONES.forEachIndexed { i, z -> rv.setOnClickPendingIntent(z, command(ctx, "bal", i, 400 + i)) }
        SEEK_ZONES.forEachIndexed { i, z -> rv.setOnClickPendingIntent(z, command(ctx, "seek", i, 500 + i)) }
        return rv
    }

    /** Splits the title into pieces of at most [max] chars, breaking at spaces where possible. */
    private fun titlePieces(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val out = ArrayList<String>()
        var line = StringBuilder()
        for (word in text.split(' ').filter { it.isNotEmpty() }) {
            var wd = word
            while (wd.length > max) { // a single word longer than the box
                if (line.isNotEmpty()) { out += line.toString(); line = StringBuilder() }
                out += wd.take(max)
                wd = wd.drop(max)
            }
            if (line.isEmpty()) line.append(wd)
            else if (line.length + 1 + wd.length <= max) line.append(' ').append(wd)
            else { out += line.toString(); line = StringBuilder(wd) }
        }
        if (line.isNotEmpty()) out += line.toString()
        return out.ifEmpty { listOf(text.take(max)) }
    }

    private fun led(rv: RemoteViews, s: Skin, id: Int, on: Boolean) {
        rv.setImageViewResource(id, if (s.isClassic) R.drawable.widget_led_square else R.drawable.widget_led_round)
        rv.setInt(id, "setColorFilter", (if (on) s.ledOn else s.ledOff).toArgb())
    }

    private fun transport(rv: RemoteViews, s: Skin, icon: Int, ledId: Int, active: Boolean) {
        rv.setInt(icon, "setColorFilter", (if (active) s.activeGlyph else s.btnText).toArgb())
        rv.setViewVisibility(ledId, if (active) View.VISIBLE else View.GONE)
        if (active) led(rv, s, ledId, true)
    }

    private fun stereoText(stereo: Boolean?, color: Int): CharSequence {
        val dim = AColor.argb(77, AColor.red(color), AColor.green(color), AColor.blue(color))
        val b = SpannableStringBuilder()
        fun part(t: String, on: Boolean) {
            val start = b.length
            b.append(t)
            b.setSpan(ForegroundColorSpan(if (on) color else dim), start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        part("mono ", stereo == false)
        part("stereo", stereo == true)
        return b
    }

    private fun volumeColor(v: Float): Int = when {
        v < 0.5f -> lerp(Color(0xFF2DB84A), Color(0xFFE8A33A), v / 0.5f)
        else -> lerp(Color(0xFFE8A33A), Color(0xFFE0402A), (v - 0.5f) / 0.5f)
    }.toArgb()

    // ------------------------------------------------------- intents

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun service(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getForegroundService(ctx, code, Intent(ctx, PlaybackService::class.java).setAction(action), FLAGS)

    private fun command(ctx: Context, cmd: String, arg: Int, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, RetroPlayWidget::class.java)
                .setAction(RetroPlayWidget.ACTION_CMD)
                .putExtra(RetroPlayWidget.EXTRA_CMD, cmd)
                .putExtra(RetroPlayWidget.EXTRA_ARG, arg),
            FLAGS,
        )

    private fun activity(ctx: Context, open: String?, code: Int): PendingIntent =
        PendingIntent.getActivity(
            ctx, code,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { if (open != null) putExtra(MainActivity.EXTRA_OPEN, open) },
            FLAGS,
        )
}
