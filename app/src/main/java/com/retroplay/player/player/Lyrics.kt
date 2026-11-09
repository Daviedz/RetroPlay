package com.retroplay.player.player

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One timed lyric line (synced lyrics); [timeMs] is -1 for plain, un-timed lyrics. */
data class LyricLine(val timeMs: Long, val text: String)

data class LyricsResult(val title: String, val artist: String, val lines: List<LyricLine>) {
    val synced: Boolean get() = lines.any { it.timeMs >= 0 }
}

/**
 * Fetches lyrics from the free lrclib.net database (no API key). Prefers time-synced lyrics.
 * Blocking: call from Dispatchers.IO. Returns null when nothing was found.
 */
object LyricsFetcher {
    private const val BASE = "https://lrclib.net/api"

    fun fetch(title: String, artist: String, album: String, durationMs: Long): LyricsResult? {
        val cleanTitle = clean(title)
        val cleanArtist = artist.takeIf { it.isNotBlank() && it != "<unknown>" }.orEmpty()
        // 1) exact match
        if (cleanArtist.isNotEmpty()) {
            val q = "track_name=${enc(cleanTitle)}&artist_name=${enc(cleanArtist)}" +
                (if (album.isNotBlank()) "&album_name=${enc(album)}" else "") +
                (if (durationMs > 0) "&duration=${durationMs / 1000}" else "")
            runCatching { parse(JSONObject(get("$BASE/get?$q"))) }.getOrNull()?.let { return it }
        }
        // 2) free-text search, best hit that has lyrics
        val q = enc((cleanArtist + " " + cleanTitle).trim())
        val arr = runCatching { JSONArray(get("$BASE/search?q=$q")) }.getOrNull() ?: return null
        var best: LyricsResult? = null
        for (i in 0 until arr.length()) {
            val r = parse(arr.getJSONObject(i)) ?: continue
            if (r.synced) return r
            if (best == null) best = r
        }
        return best
    }

    /** "Song (Official Video) [HD]" -> "Song" */
    private fun clean(t: String) = t.replace(Regex("[\\(\\[][^\\)\\]]*[\\)\\]]"), "").trim().ifEmpty { t }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun parse(o: JSONObject): LyricsResult? {
        if (o.optBoolean("instrumental")) return null
        val synced = o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        val lines = when {
            synced != null -> parseLrc(synced)
            plain != null -> plain.lines().map { LyricLine(-1, it) }
            else -> return null
        }
        if (lines.isEmpty()) return null
        return LyricsResult(o.optString("trackName"), o.optString("artistName"), lines)
    }

    private val lrc = Regex("^\\[(\\d+):(\\d+(?:\\.\\d+)?)](.*)$")

    private fun parseLrc(text: String): List<LyricLine> = text.lines().mapNotNull { l ->
        val m = lrc.find(l.trim()) ?: return@mapNotNull null
        val ms = ((m.groupValues[1].toLong() * 60 + m.groupValues[2].toDouble()) * 1000).toLong()
        LyricLine(ms, m.groupValues[3].trim())
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 12000
        c.setRequestProperty("User-Agent", "RetroPlay (https://github.com/Daviedz/RetroPlay)")
        try {
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
}
