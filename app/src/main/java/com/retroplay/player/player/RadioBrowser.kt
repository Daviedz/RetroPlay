package com.retroplay.player.player

import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder

/**
 * Client for the free, community-run radio-browser.info station directory (no API key).
 * Servers are discovered the way the project recommends: resolve all.api.radio-browser.info,
 * then reverse-lookup each address to get a server name. A few known mirrors are a fallback.
 * All calls block — run them on Dispatchers.IO.
 */
object RadioBrowser {
    private const val USER_AGENT = "RetroPlay/1.1 (Android music player)"
    private val fallback = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
        "https://de2.api.radio-browser.info",
        "https://fi1.api.radio-browser.info",
    )

    @Volatile
    private var servers: List<String>? = null

    private fun servers(): List<String> = servers ?: run {
        val found = try {
            InetAddress.getAllByName("all.api.radio-browser.info")
                .mapNotNull { addr -> addr.canonicalHostName.takeIf { it.contains("radio-browser") } }
                .map { "https://$it" }
                .distinct()
                .shuffled()
        } catch (_: Exception) {
            emptyList()
        }
        (found + fallback).distinct().also { servers = it }
    }

    /** Most-played stations, optionally filtered by name and/or ISO country code (e.g. "PH"). */
    fun search(name: String? = null, countryCode: String? = null, limit: Int = 100): List<Station> {
        val q = buildString {
            append("/json/stations/search?hidebroken=true&order=clickcount&reverse=true&limit=$limit")
            if (!name.isNullOrBlank()) append("&name=").append(URLEncoder.encode(name.trim(), "UTF-8"))
            if (!countryCode.isNullOrBlank()) append("&countrycode=").append(countryCode)
        }
        var last: Exception? = null
        for (base in servers().take(4)) {
            try {
                return parse(get(base + q))
            } catch (e: Exception) {
                last = e
            }
        }
        servers = null // rediscover next time
        throw last ?: IOException("No radio directory server reachable")
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 12000
        c.setRequestProperty("User-Agent", USER_AGENT)
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun parse(json: String): List<Station> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val url = o.optString("url_resolved").ifBlank { o.optString("url") }
            if (url.isBlank()) null
            else Station(
                name = o.optString("name").trim().ifBlank { "Unnamed station" },
                url = url,
                country = o.optString("country"),
                codec = o.optString("codec"),
                bitrate = o.optInt("bitrate"),
            )
        }.distinctBy { it.url }
    }
}
