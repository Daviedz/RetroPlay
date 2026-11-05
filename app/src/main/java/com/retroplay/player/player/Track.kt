package com.retroplay.player.player

import android.net.Uri

data class Track(
    val uri: Uri,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val album: String = "",
    val albumId: Long = -1,
    val folder: String = "",
    val isStream: Boolean = false,
) {
    val display: String
        get() = if (artist.isBlank() || artist == "<unknown>") title else "$artist - $title"
}

/** An internet radio station. */
data class Station(
    val name: String,
    val url: String,
    val country: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
) {
    val subtitle: String
        get() = listOf(
            country,
            codec.takeIf { it.isNotBlank() && !it.equals("unknown", true) } ?: "",
            if (bitrate > 0) "$bitrate kbps" else "",
        ).filter { it.isNotBlank() }.joinToString(" • ")
}

data class Album(val id: Long, val title: String, val artist: String, val tracks: List<Track>)
data class Folder(val path: String, val tracks: List<Track>) {
    val name: String get() = path.trimEnd('/').substringAfterLast('/').ifBlank { path.ifBlank { "/" } }
}

data class StreamInfo(val kbps: Int, val khz: Int, val stereo: Boolean)

enum class PlayState { STOPPED, PLAYING, PAUSED }

enum class VisMode(val label: String) { SPECTRUM("Spectrum"), SCOPE("Oscilloscope"), OFF("Off") }

enum class LibState { NOT_LOADED, LOADING, READY }

enum class LibTab(val label: String) { ALBUMS("ALBUMS"), FOLDERS("FOLDERS"), PLAYLISTS("LISTS"), RADIO("RADIO") }

data class EqPreset(val name: String, val preamp: Float, val bands: List<Float>)

val EQ_PRESETS = listOf(
    EqPreset("Flat", 0f, listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    EqPreset("Rock", 0f, listOf(5f, 3f, -3f, -5f, -2f, 2f, 5f, 6f, 6f, 6f)),
    EqPreset("Pop", 0f, listOf(-1f, 3f, 5f, 5f, 3f, 0f, -1f, -1f, -1f, -1f)),
    EqPreset("Classical", 0f, listOf(0f, 0f, 0f, 0f, 0f, 0f, -4f, -4f, -4f, -6f)),
    EqPreset("Dance", 0f, listOf(6f, 5f, 2f, 0f, 0f, -3f, -4f, -4f, 0f, 0f)),
    EqPreset("Full Bass", -2f, listOf(7f, 7f, 7f, 4f, 1f, -3f, -6f, -7f, -8f, -8f)),
    EqPreset("Full Treble", -2f, listOf(-6f, -6f, -6f, -2f, 2f, 7f, 10f, 10f, 10f, 11f)),
    EqPreset("Headphones", 0f, listOf(3f, 7f, 3f, -2f, -1f, 1f, 3f, 6f, 7f, 8f)),
    EqPreset("Live", 0f, listOf(-3f, 0f, 2f, 3f, 3f, 3f, 2f, 1f, 1f, 1f)),
    EqPreset("Techno", 0f, listOf(5f, 3f, 0f, -3f, -3f, 0f, 5f, 6f, 6f, 5f)),
    EqPreset("Vocal Boost", 0f, listOf(-2f, -3f, -3f, 1f, 4f, 4f, 3f, 1f, 0f, -2f)),
)

val EQ_FREQS = floatArrayOf(60f, 170f, 310f, 600f, 1000f, 3000f, 6000f, 12000f, 14000f, 16000f)
val EQ_LABELS = listOf("60", "170", "310", "600", "1K", "3K", "6K", "12K", "14K", "16K")

/** m:ss, used in the playlist */
fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** h:mm:ss once it passes an hour (playlist totals) */
fun formatLong(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else formatTime(ms)
}

/** mm:ss, used on the big LCD */
fun formatClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

fun Track.durationLabel(): String = if (isStream) "LIVE" else formatTime(durationMs)
