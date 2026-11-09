package com.retroplay.player.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.DisplayMetrics
import androidx.compose.ui.graphics.toArgb
import com.retroplay.player.player.PlayState
import com.retroplay.player.player.PlayerController
import com.retroplay.player.player.VisMode
import com.retroplay.player.player.formatClock
import com.retroplay.player.ui.Skin
import kotlin.math.abs
import kotlin.math.min

/**
 * Draws the widget's LCD and sliders with plain android.graphics, mirroring the
 * Compose drawing in ui/Widgets.kt so the widget looks like the app.
 * [d] = pixels per dp for the bitmap.
 */
object WidgetRenderer {
    private val mono: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    // ---------------------------------------------------------------- LCD

    /** Clock text shown on the LCD (elapsed, or remaining when toggled). */
    fun clockText(c: PlayerController): String {
        val remaining = c.showRemaining && c.durationMs > 0
        val ms = if (remaining) c.durationMs - c.positionMs else c.positionMs
        return (if (remaining) "-" else "") + formatClock(ms)
    }

    fun lcd(c: PlayerController, s: Skin, wPx: Int, hPx: Int, d: Float, artMode: Boolean): Bitmap {
        val w = wPx.coerceAtLeast(8)
        val h = hPx.coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // Tag the bitmap's density so the widget's wrap_content ImageView shows it at
        // exactly the dp size it was drawn for (this is what sets the display height).
        bmp.density = (DisplayMetrics.DENSITY_DEFAULT * d).toInt()
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (s.isClassic) {
            // Fully opaque picture (same layers as widget_lcd_classic): some launchers stop refreshing
            // a mostly see-through image when it has little on it (oscilloscope / visualizer off).
            val e = d.coerceAtLeast(1f)
            p.style = Paint.Style.FILL
            p.color = 0xFF6E6E96.toInt(); cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.color = 0xFF0A0A12.toInt(); cv.drawRect(0f, 0f, w - e, h - e, p)
            p.color = 0xFF000000.toInt(); cv.drawRect(e, e, w - e, h - e, p)
        }
        val pad = 5 * d
        var left = pad
        val right = w - pad

        // Album cover on the left in ART mode
        if (artMode) {
            val size = h - 2 * pad
            val dst = RectF(pad, pad, pad + size, pad + size)
            val cover = if (c.currentTrack?.isStream == true) null else c.currentArt
            if (cover != null && !cover.isRecycled) {
                val side = min(cover.width, cover.height)
                val sl = (cover.width - side) / 2
                val st = (cover.height - side) / 2
                val src = Rect(sl, st, sl + side, st + side)
                cv.drawBitmap(cover, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                p.color = s.lcdDim.toArgb()
                p.style = Paint.Style.STROKE
                p.strokeWidth = d
                cv.drawRect(dst, p)
                p.style = Paint.Style.FILL
                drawNote(cv, p, dst)
            }
            left = dst.right + 6 * d
        }

        // State glyph + clock
        val stopped = c.playState == PlayState.STOPPED
        val remaining = c.showRemaining && c.durationMs > 0
        val ms = if (remaining) c.durationMs - c.positionMs else c.positionMs
        val text = (if (remaining) "-" else "") + formatClock(ms)
        val glyph = 8 * d
        val gap = 5 * d
        p.typeface = mono
        var ts = h * 0.36f
        p.textSize = ts
        while (p.measureText(text) > right - left - glyph - gap && ts > 6 * d) {
            ts -= d
            p.textSize = ts
        }
        val fm = p.fontMetrics
        val baseline = pad - fm.ascent * 0.92f
        val capH = -fm.ascent * 0.7f
        p.color = s.lcdText.toArgb()
        drawStateGlyph(cv, p, c.playState, RectF(left, baseline - capH / 2 - glyph / 2, left + glyph, baseline - capH / 2 + glyph / 2))
        p.color = (if (stopped) s.lcdDim else s.lcdText).toArgb()
        // (the clock itself is a TextView in the widget layout; see WidgetUpdater)

        // Visualizer below
        val visTop = baseline + 4 * d
        val area = RectF(left, visTop, right, h - pad)
        if (area.height() > 4 * d) {
            // A visualizer glitch must never stop the clock / widget from updating
            try { drawVis(cv, c, s, area, d) } catch (e: Exception) { android.util.Log.w("RetroPlayWidget", "visualizer draw failed", e) }
        }
        lcdLog++
        if (lcdLog % 10 == 0) {
            val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
            android.util.Log.i("RetroPlayWidget", "lcd frame: text=$text skin=${c.skin} vis=${c.visMode} size=${w}x$h hash=${px.contentHashCode()}")
        }
        return bmp
    }

    private var lcdLog = 0

    private fun drawStateGlyph(cv: Canvas, p: Paint, st: PlayState, r: RectF) {
        when (st) {
            PlayState.PLAYING -> cv.drawPath(Path().apply {
                moveTo(r.left + r.width() * 0.15f, r.top)
                lineTo(r.right, r.centerY())
                lineTo(r.left + r.width() * 0.15f, r.bottom)
                close()
            }, p)
            PlayState.PAUSED -> {
                cv.drawRect(r.left + r.width() * 0.1f, r.top, r.left + r.width() * 0.4f, r.bottom, p)
                cv.drawRect(r.left + r.width() * 0.6f, r.top, r.left + r.width() * 0.9f, r.bottom, p)
            }
            PlayState.STOPPED -> cv.drawRect(
                r.left + r.width() * 0.1f, r.top + r.height() * 0.1f,
                r.right - r.width() * 0.1f, r.bottom - r.height() * 0.1f, p,
            )
        }
    }

    private fun drawNote(cv: Canvas, p: Paint, box: RectF) {
        val u = box.width() / 5
        val cx = box.centerX()
        val cy = box.centerY()
        cv.drawOval(RectF(cx - u * 0.9f, cy + u * 0.3f, cx + u * 0.1f, cy + u * 1.0f), p)
        cv.drawRect(cx - u * 0.05f, cy - u * 1.2f, cx + u * 0.15f, cy + u * 0.7f, p)
        cv.drawRect(cx - u * 0.05f, cy - u * 1.2f, cx + u * 0.9f, cy - u * 0.85f, p)
    }

    private fun drawVis(cv: Canvas, c: PlayerController, s: Skin, r: RectF, d: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (!c.hasVisPermission || c.visMode == VisMode.OFF) {
            p.color = s.lcdDim.toArgb()
            var x = r.left
            while (x < r.right) {
                cv.drawRect(x, r.bottom - 2 * d, x + d, r.bottom - d, p)
                x += 3 * d
            }
            return
        }
        when (c.visMode) {
            VisMode.SPECTRUM -> {
                val bars = c.spectrum
                val peaks = c.peaks
                val n = bars.size
                val slot = r.width() / n
                val bw = slot * (if (s.isClassic) 0.8f else 0.66f)
                val cols = s.visBars.map { it.toArgb() }.toIntArray()
                p.shader = LinearGradient(0f, r.top, 0f, r.bottom, cols, null, Shader.TileMode.CLAMP)
                for (i in 0 until n) {
                    val x = r.left + i * slot
                    val bh = bars[i] * r.height()
                    if (bh > 0.5f) cv.drawRect(x, r.bottom - bh, x + bw, r.bottom, p)
                }
                p.shader = null
                p.color = s.visPeak.toArgb()
                for (i in 0 until n) {
                    if (peaks[i] > 0.01f) {
                        val x = r.left + i * slot
                        val py = (r.bottom - peaks[i] * r.height() - 1.5f * d).coerceAtLeast(r.top)
                        cv.drawRect(x, py, x + bw, py + 1.5f * d, p)
                    }
                }
                if (!s.isClassic) {
                    p.color = s.lcdBottom.copy(alpha = 0.8f).toArgb()
                    var y = r.bottom - 3 * d
                    while (y > r.top) {
                        cv.drawRect(r.left, y, r.right, y + d, p)
                        y -= 3 * d
                    }
                }
            }
            VisMode.SCOPE -> {
                // Drawn as thin vertical bars (like the spectrum) rather than a stroked path
                val wf = c.waveform
                if (wf.size > 1) {
                    p.style = Paint.Style.FILL
                    p.color = s.lcdText.toArgb()
                    val cols = (r.width() / (2f * d)).toInt().coerceIn(8, 96)
                    val bw = r.width() / cols
                    val mid = r.centerY()
                    for (i in 0 until cols) {
                        val v = wf[i * (wf.size - 1) / (cols - 1).coerceAtLeast(1)]
                        val half = (kotlin.math.abs(v) * r.height() / 2).coerceAtLeast(d * 0.75f)
                        val x = r.left + i * bw
                        cv.drawRect(x, mid - half, x + (bw * 0.7f).coerceAtLeast(1f), mid + half, p)
                    }
                } else {
                    p.color = s.lcdDim.toArgb()
                    p.style = Paint.Style.FILL
                    cv.drawRect(r.left, r.centerY() - d / 2, r.right, r.centerY() + d / 2, p)
                }
            }
            VisMode.OFF -> Unit
        }
    }

    // ------------------------------------------------------------- sliders

    private fun seekShader(w: Float) = LinearGradient(
        0f, 0f, w, 0f,
        intArrayOf(0xFF00E030.toInt(), 0xFFF0E000.toInt(), 0xFFF08010.toInt()), null, Shader.TileMode.CLAMP,
    )

    /** Horizontal slider, value 0..1, same look as HBar in the app. */
    fun slider(s: Skin, value: Float, fill: Int?, centered: Boolean, wPx: Int, hPx: Int, d: Float, gradient: Boolean = false): Bitmap {
        val w = wPx.coerceAtLeast(8).toFloat()
        val h = hPx.coerceAtLeast(4).toFloat()
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val v = value.coerceIn(0f, 1f)
        if (s.isClassic) {
            p.isAntiAlias = false
            val th = min(16 * d, w)
            val gh = h * 0.45f
            val gy = (h - gh) / 2
            p.color = s.groove.toArgb()
            cv.drawRect(0f, gy, w, gy + gh, p)
            val x = (w - th) * v
            val cx = x + th / 2
            if (fill != null) {
                p.color = fill
                if (centered && abs(value - 0.5f) < 0.002f) cv.drawRect(0f, gy + 1, w, gy + gh - 1, p)
                else if (centered) cv.drawRect(min(w / 2, cx), gy + 1, min(w / 2, cx) + abs(cx - w / 2), gy + gh - 1, p)
                else {
                    if (gradient) p.shader = seekShader(w)
                    cv.drawRect(0f, gy + 1, cx, gy + gh - 1, p)
                    p.shader = null
                }
            }
            p.shader = LinearGradient(0f, 0f, 0f, h, s.thumbTop.toArgb(), s.thumbBottom.toArgb(), Shader.TileMode.CLAMP)
            cv.drawRect(x, 0f, x + th, h, p)
            p.shader = null
            val e = d.coerceAtLeast(1f)
            p.color = 0xFFFFFFFF.toInt()
            cv.drawRect(x, 0f, x + th, e, p)
            cv.drawRect(x, 0f, x + e, h, p)
            p.color = s.edgeDark.toArgb()
            cv.drawRect(x, h - e, x + th, h, p)
            cv.drawRect(x + th - e, 0f, x + th, h, p)
            for (k in -1..1) {
                val lx = cx + k * 3 * d
                cv.drawRect(lx, h * 0.3f, lx + e, h * 0.7f, p)
            }
        } else {
            val gh = 6 * d
            val gy = (h - gh) / 2
            p.color = s.groove.toArgb()
            cv.drawRoundRect(RectF(0f, gy, w, gy + gh), gh / 2, gh / 2, p)
            val rad = min(h / 2, 9 * d)
            val cx = rad + (w - 2 * rad) * v
            if (fill != null) {
                p.color = fill
                val full = centered && abs(value - 0.5f) < 0.002f
                val l = if (full) 0f else if (centered) min(w / 2, cx) else 0f
                val rr = if (full) w else if (centered) l + abs(cx - w / 2) else cx
                if (gradient) p.shader = seekShader(w)
                cv.drawRoundRect(RectF(l, gy, rr, gy + gh), gh / 2, gh / 2, p)
                p.shader = null
            }
            p.shader = LinearGradient(0f, h / 2 - rad, 0f, h / 2 + rad, s.thumbTop.toArgb(), s.thumbBottom.toArgb(), Shader.TileMode.CLAMP)
            cv.drawCircle(cx, h / 2, rad - d / 2, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = d
            p.color = s.edgeDark.toArgb()
            cv.drawCircle(cx, h / 2, rad - d / 2, p)
        }
        return bmp
    }
}
