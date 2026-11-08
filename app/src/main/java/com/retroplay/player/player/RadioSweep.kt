package com.retroplay.player.player

import android.os.SystemClock

/** Position for the live-radio seek bar: a slow sweep, 60 min to the end, then back again. Pauses with playback. */
object RadioSweep {
    private const val PASS_MS = 60 * 60 * 1000L
    private var key: Any? = null
    private var accum = 0L
    private var since = -1L

    fun fraction(track: Any?, playing: Boolean): Float {
        val now = SystemClock.elapsedRealtime()
        if (track !== key) { key = track; accum = 0L; since = -1L }
        if (playing) { if (since < 0) since = now }
        else if (since >= 0) { accum += now - since; since = -1L }
        val el = accum + (if (since >= 0) now - since else 0L)
        val t = (el % (2 * PASS_MS)).toFloat() / PASS_MS
        return if (t <= 1f) t else 2f - t
    }
}
