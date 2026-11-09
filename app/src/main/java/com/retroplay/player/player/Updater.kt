package com.retroplay.player.player

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A newer release found on GitHub. */
data class UpdateInfo(val version: String, val apkUrl: String, val notes: String)

/**
 * Self-update from GitHub Releases (Daviedz/RetroPlay). Android never installs silently:
 * the app downloads the APK of the latest release and hands it to the system installer,
 * the user taps Install. The APK must be signed with the same key as the installed app.
 * All state is Compose state so the UI dialog reacts to it.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/Daviedz/RetroPlay/releases/latest"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Set when a newer release exists; the UI shows a dialog while this is non-null. */
    var available by mutableStateOf<UpdateInfo?>(null)
    var busy by mutableStateOf(false)
        private set
    var progress by mutableStateOf(-1) // percent while downloading, -1 otherwise
        private set

    private val scope = CoroutineScope(Dispatchers.Main)

    private fun installedVersion(app: Application): String =
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "0"

    /** "v1.10" / "1.10.2" -> [1, 10, 2]; anything non-numeric is ignored. */
    private fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
        .split('.', '-', '_').mapNotNull { it.toIntOrNull() }

    private fun isNewer(remote: String, local: String): Boolean {
        val r = parts(remote); val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** At most once a day, silently (only sets [available] if there is something new). */
    fun autoCheck(app: Application) {
        val prefs = app.getSharedPreferences("retroplay", Application.MODE_PRIVATE)
        val last = prefs.getLong("updateChecked", 0L)
        if (System.currentTimeMillis() - last < DAY_MS) return
        check(app, manual = false)
    }

    fun check(app: Application, manual: Boolean) {
        if (busy) return
        busy = true
        if (manual) toast(app, "Checking for updates…")
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { fetchLatest() } }
            busy = false
            val info = result.getOrNull()
            if (result.isFailure) {
                if (manual) toast(app, "Couldn't reach GitHub")
                return@launch
            }
            app.getSharedPreferences("retroplay", Application.MODE_PRIVATE).edit()
                .putLong("updateChecked", System.currentTimeMillis()).apply()
            if (info != null && isNewer(info.version, installedVersion(app))) available = info
            else if (manual) toast(app, "RetroPlay ${installedVersion(app)} is up to date")
        }
    }

    private fun fetchLatest(): UpdateInfo? {
        val json = JSONObject(httpGet(LATEST))
        val tag = json.optString("tag_name")
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                return UpdateInfo(tag, a.getString("browser_download_url"), json.optString("body").trim())
            }
        }
        return null
    }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 30000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "RetroPlay-Updater")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        return c
    }

    private fun httpGet(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    /** Download the APK, then open the system installer. */
    fun install(app: Application, info: UpdateInfo) {
        if (busy) return
        // Android asks the user once to allow this app to install packages.
        if (!app.packageManager.canRequestPackageInstalls()) {
            toast(app, "Allow RetroPlay to install apps, then tap Update again")
            app.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        busy = true
        progress = 0
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching { download(app, info.apkUrl) }
            }
            busy = false
            progress = -1
            file.onSuccess {
                available = null
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", it)
                app.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { toast(app, "Download failed") }
        }
    }

    private suspend fun download(app: Application, url: String): File {
        val dir = File(app.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "RetroPlay-update.apk")
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            val total = c.contentLengthLong
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var n: Int
                    while (input.read(buf).also { n = it } > 0) {
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != progress) withContext(Dispatchers.Main) { progress = pct }
                        }
                    }
                }
            }
        } finally { c.disconnect() }
        return out
    }

    private fun toast(app: Application, msg: String) =
        Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
}
