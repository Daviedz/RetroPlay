package com.retroplay.player.player

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.ui.graphics.asAndroidBitmap
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.retroplay.player.PlaybackService
import com.retroplay.player.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class SkinId(val label: String) { CLASSIC("Classic Pixel"), MODERN("Chrome 2K") }

/**
 * All player state lives here (Compose snapshot state), owned by the Application so the
 * UI and the background PlaybackService share one instance.
 */
class PlayerController(private val app: Application) {

    companion object {
        const val BARS = 20
    }

    private val engine = AudioEngine(app)
    private val prefs = app.getSharedPreferences("retroplay", Context.MODE_PRIVATE)
    private val mainScope = MainScope()
    private val audioManager = app.getSystemService(AudioManager::class.java)

    // ---- playlist
    val playlist = mutableStateListOf<Track>()
    var selection by mutableStateOf(emptySet<Int>()); private set
    var current by mutableIntStateOf(-1); private set

    // ---- transport
    var playState by mutableStateOf(PlayState.STOPPED); private set
    var positionMs by mutableLongStateOf(0L); private set
    var durationMs by mutableLongStateOf(0L); private set
    var info by mutableStateOf<StreamInfo?>(null); private set
    var volume by mutableFloatStateOf(0.8f); private set
    var balance by mutableFloatStateOf(0f); private set
    var shuffleOn by mutableStateOf(false); private set
    var repeatOn by mutableStateOf(true); private set

    // ---- equalizer
    var eqOn by mutableStateOf(true); private set
    var preamp by mutableFloatStateOf(0f); private set
    val bands = mutableStateListOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

    // ---- look
    var skin by mutableStateOf(SkinId.CLASSIC); private set
    var visMode by mutableStateOf(VisMode.SPECTRUM); private set
    var hasVisPermission by mutableStateOf(false); private set
    var showRemaining by mutableStateOf(false)
    var showEq by mutableStateOf(true)
    var showPl by mutableStateOf(true)
    var showArt by mutableStateOf(true)
    var spectrum by mutableStateOf(FloatArray(BARS)); private set
    var peaks by mutableStateOf(FloatArray(BARS)); private set
    var waveform by mutableStateOf(FloatArray(0)); private set

    val totalDurationMs: Long get() = playlist.sumOf { it.durationMs }

    // ---- media library / saved playlists / radio
    /** Which library tab is open; null = library closed. */
    var libraryTab by mutableStateOf<LibTab?>(null)

    // ---- song search window + lyrics overlay
    var showSearch by mutableStateOf(false)
    var addToListTracks by mutableStateOf<List<Track>?>(null)   // songs waiting for a playlist name
    var showLyrics by mutableStateOf(false); private set
    var lyrics by mutableStateOf<LyricsResult?>(null); private set
    var lyricsLoading by mutableStateOf(false); private set
    var lyricsError by mutableStateOf<String?>(null); private set
    private var lyricsFor: Uri? = null
    var library by mutableStateOf<List<Track>>(emptyList()); private set
    var libraryState by mutableStateOf(LibState.NOT_LOADED); private set
    var savedPlaylists by mutableStateOf<Map<String, List<Track>>>(emptyMap()); private set
    var favorites by mutableStateOf<List<Station>>(emptyList()); private set
    var radioResults by mutableStateOf<List<Station>>(emptyList()); private set
    var radioLoading by mutableStateOf(false); private set
    var radioError by mutableStateOf<String?>(null); private set

    val currentTrack: Track? get() = playlist.getOrNull(current)

    /** Show the player over the lock screen (MainActivity reads this). */
    var showOnLock by mutableStateOf(true)

    /** Home-screen widget display: 0 = Small (no EQ/PL/ART/ML row), 1 = Medium. */
    var widgetLcd by mutableIntStateOf(1)

    /** Cover of the current track for the lock screen / notification. */
    var currentArt: Bitmap? = null
        private set
    private var artFor: Uri? = null

    // ---- phone volume sync: the VOL slider *is* the phone's media volume
    private val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    private var lastSysVol = -1
    private var volumeDragging = false
    private var tickCount = 0

    /** Media session: lock-screen controls, Bluetooth / headset buttons, smartwatch controls. */
    val session: MediaSessionCompat = MediaSessionCompat(app, "RetroPlay").apply {
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                when (playState) {
                    PlayState.PAUSED -> pause()
                    PlayState.STOPPED -> play()
                    PlayState.PLAYING -> Unit
                }
            }
            override fun onPause() { if (playState == PlayState.PLAYING) pause() }
            override fun onSkipToNext() { next() }
            override fun onSkipToPrevious() { prev() }
            override fun onStop() { stop() }
            override fun onSeekTo(pos: Long) { seekToMs(pos) }
        })
        isActive = true
    }

    private var loadedIndex = -1
    private var playWhenReady = false

    /** A track is loading and will start playing as soon as it's ready. */
    val isStarting: Boolean get() = playWhenReady && playState == PlayState.STOPPED
    private var resumeOnFocusGain = false
    private var focusRequest: AudioFocusRequest? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                if (playState == PlayState.PLAYING) pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (playState == PlayState.PLAYING) {
                resumeOnFocusGain = true
                pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocusGain && playState == PlayState.PAUSED) {
                resumeOnFocusGain = false
                startPlayback()
            }
        }
    }

    // Headphones unplugged -> pause
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (playState == PlayState.PLAYING) pause()
        }
    }

    init {
        hasVisPermission = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        restore()
        engine.onPrepared = { handlePrepared() }
        engine.onCompletion = { onTrackEnded() }
        engine.onError = { msg ->
            toast(msg)
            playWhenReady = false
            playState = PlayState.STOPPED
            loadedIndex = -1
            refreshService()
        }
        engine.onVisData = { fft, wave -> handleVis(fft, wave) }
        ContextCompat.registerReceiver(
            app, noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        applyVolume()
        pollSystemVolume()
        mainScope.launch {
            while (isActive) {
                // One bad tick must never kill the loop (it drives the clock, visualizer and widget)
                try { tick() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { android.util.Log.w("RetroPlayWidget", "tick failed", e) }
                delay(50)
            }
        }
    }

    // =============================================================== transport

    fun playIndex(i: Int) {
        if (i !in playlist.indices) return
        current = i
        positionMs = 0
        durationMs = playlist[i].durationMs
        info = null
        playWhenReady = true
        loadCurrent()
    }

    private fun loadCurrent() {
        val t = playlist.getOrNull(current) ?: return
        loadedIndex = current
        onTrackChangedForLyrics()
        try {
            engine.load(t.uri)
        } catch (e: Exception) {
            loadedIndex = -1
            playState = PlayState.STOPPED
            toast("Can't open: ${t.title}")
            refreshService()
            return
        }
        if (t.isStream) return
        mainScope.launch {
            val r = withContext(Dispatchers.IO) { MediaLibrary.streamInfo(app, t.uri) }
            if (playlist.getOrNull(current)?.uri == t.uri) info = r
        }
    }

    private fun handlePrepared() {
        if (currentTrack?.isStream == true) durationMs = 0
        else engine.duration.toLong().takeIf { it > 0 }?.let { durationMs = it }
        applyVolume()
        applyEq()
        engine.setVisualizer(visMode != VisMode.OFF && hasVisPermission)
        if (playWhenReady) startPlayback()
    }

    private fun startPlayback() {
        if (!requestFocus()) {
            toast("Another app is holding the audio")
            return
        }
        engine.play()
        playState = PlayState.PLAYING
        refreshService()
    }

    /** Winamp semantics: Play resumes when paused, restarts the track otherwise. */
    fun play() {
        if (playlist.isEmpty()) return
        when {
            playState == PlayState.PAUSED -> startPlayback()
            loadedIndex == current && current >= 0 && engine.prepared -> {
                engine.seekTo(0)
                positionMs = 0
                startPlayback()
            }
            else -> playIndex(current.coerceIn(0, playlist.lastIndex))
        }
    }

    /** Pause toggles between paused and playing. */
    fun pause() {
        when (playState) {
            PlayState.PLAYING -> {
                engine.pause()
                playState = PlayState.PAUSED
                refreshService()
            }
            PlayState.PAUSED -> startPlayback()
            PlayState.STOPPED -> Unit
        }
    }

    fun stop() {
        playWhenReady = false
        engine.stop()
        positionMs = 0
        playState = PlayState.STOPPED
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        refreshService()
    }

    fun next() = nextIndex(wrap = true)?.let { changeTrack(it) }

    fun prev() {
        if (playlist.isEmpty()) return
        changeTrack(if (current <= 0) playlist.lastIndex else current - 1)
    }

    /** Next/Prev while stopped only moves the cursor, like Winamp. */
    private fun changeTrack(i: Int) {
        if (playState == PlayState.STOPPED) {
            current = i
            positionMs = 0
            durationMs = playlist[i].durationMs
            info = null
        } else {
            playIndex(i)
        }
    }

    private fun nextIndex(wrap: Boolean): Int? {
        if (playlist.isEmpty()) return null
        if (shuffleOn && playlist.size > 1) {
            var n: Int
            do { n = playlist.indices.random() } while (n == current)
            return n
        }
        val n = current + 1
        return if (n <= playlist.lastIndex) n else if (wrap) 0 else null
    }

    private fun onTrackEnded() {
        val n = nextIndex(wrap = repeatOn)
        if (n == null) stop() else playIndex(n)
    }

    fun seekTo(fraction: Float) {
        if (!engine.prepared || durationMs <= 0) return
        val ms = (fraction.coerceIn(0f, 1f) * durationMs).toLong()
        engine.seekTo(ms.toInt())
        positionMs = ms
        updateSession()
    }

    fun seekToMs(ms: Long) {
        if (durationMs > 0) seekTo(ms.toFloat() / durationMs)
    }

    /** Moves the phone's media volume (so the hardware keys and this slider stay in sync). */
    fun setVolumeLevel(v: Float) {
        volumeDragging = true
        volume = v.coerceIn(0f, 1f)
        val idx = (volume * maxVol).roundToInt()
        if (idx != lastSysVol) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, idx, 0)
                lastSysVol = idx
            } catch (_: SecurityException) {
                // e.g. Do Not Disturb blocks volume changes
            }
        }
    }

    fun endVolumeDrag() {
        volumeDragging = false
        if (lastSysVol >= 0) volume = lastSysVol.toFloat() / maxVol
    }

    private fun pollSystemVolume() {
        val v = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (v != lastSysVol) {
            lastSysVol = v
            if (!volumeDragging) volume = v.toFloat() / maxVol
        }
    }

    fun setBalanceLevel(b: Float) {
        balance = if (abs(b) < 0.08f) 0f else b.coerceIn(-1f, 1f)
        applyVolume()
    }

    private fun applyVolume() {
        // Loudness is the phone volume; the player itself only applies balance.
        engine.setVolume(1f - max(0f, balance), 1f - max(0f, -balance))
    }

    fun toggleShuffle() { shuffleOn = !shuffleOn }
    fun toggleRepeat() { repeatOn = !repeatOn }

    private fun requestFocus(): Boolean {
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(engine.attributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
            .also { focusRequest = it }
        return audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    // =============================================================== equalizer

    fun toggleEq() {
        eqOn = !eqOn
        applyEq()
    }

    fun setPreampDb(db: Float) {
        preamp = snap(db)
        applyEq()
    }

    fun setBandDb(i: Int, db: Float) {
        bands[i] = snap(db)
        applyEq()
    }

    fun applyPreset(p: EqPreset) {
        preamp = p.preamp
        p.bands.forEachIndexed { i, v -> bands[i] = v }
        applyEq()
    }

    private fun snap(db: Float) = if (abs(db) < 0.7f) 0f else db.coerceIn(-12f, 12f)

    private fun applyEq() = engine.applyEq(eqOn, preamp, bands.toList())

    // ============================================================== visualizer

    fun cycleVisMode() {
        visMode = VisMode.entries[(visMode.ordinal + 1) % VisMode.entries.size]
        engine.setVisualizer(visMode != VisMode.OFF && hasVisPermission)
    }

    fun onVisPermission(granted: Boolean) {
        hasVisPermission = granted
        if (granted && visMode == VisMode.OFF) visMode = VisMode.SPECTRUM
        engine.setVisualizer(granted && visMode != VisMode.OFF)
        if (!granted) toast("The spectrum analyzer needs the audio permission")
    }

    private fun handleVis(fft: ByteArray?, wave: ByteArray?) {
        if (playState != PlayState.PLAYING) return
        if (fft != null && fft.size >= 4) {
            val n = fft.size / 2
            val mags = FloatArray(n) { k ->
                if (k == 0) abs(fft[0].toFloat())
                else {
                    val re = fft[2 * k].toFloat()
                    val im = fft[2 * k + 1].toFloat()
                    sqrt(re * re + im * im)
                }
            }
            val old = spectrum
            val out = FloatArray(BARS)
            val maxBin = n.toDouble()
            for (b in 0 until BARS) {
                val lo = maxBin.pow(b.toDouble() / BARS).toInt().coerceIn(1, n - 1)
                val hi = maxBin.pow((b + 1).toDouble() / BARS).toInt().coerceIn(lo + 1, n)
                var m = 0f
                for (k in lo until hi) m = max(m, mags[k])
                val v = (ln(1f + m) / ln(1f + 90f)).coerceIn(0f, 1f)
                out[b] = max(v, old[b] - 0.08f)
            }
            spectrum = out
        }
        if (wave != null && wave.isNotEmpty()) {
            waveform = FloatArray(64) { i -> ((wave[i * wave.size / 64].toInt() and 0xFF) - 128) / 128f }
        }
    }

    private fun tick() {
        if (tickCount++ % 5 == 0) pollSystemVolume()
        if (tickCount % 10 == 0) try { WidgetUpdater.onTick(app, this) } catch (e: Throwable) { android.util.Log.w("RetroPlayWidget", "widget tick failed", e) }
        if (engine.prepared && playState != PlayState.STOPPED) positionMs = engine.position.toLong()
        val s = spectrum
        val p = peaks
        if (playState != PlayState.PLAYING) {
            if (s.any { it > 0f } || p.any { it > 0f }) {
                spectrum = FloatArray(BARS) { (s[it] - 0.05f).coerceAtLeast(0f) }
                peaks = FloatArray(BARS) { (p[it] - 0.03f).coerceAtLeast(0f) }
            }
            if (waveform.isNotEmpty()) waveform = FloatArray(0)
        } else {
            peaks = FloatArray(BARS) { max(p[it] - 0.012f, s[it]) }
        }
    }

    // ================================================================ playlist

    fun addDocuments(uris: List<Uri>) {
        mainScope.launch {
            val tracks = withContext(Dispatchers.IO) { uris.map { MediaLibrary.fromDocument(app, it) } }
            addTracks(tracks)
        }
    }

    fun scanLibrary() {
        mainScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                try { MediaLibrary.scanDevice(app) } catch (_: Exception) { emptyList() }
            }
            if (tracks.isNotEmpty()) {
                library = tracks
                libraryState = LibState.READY
            }
            if (tracks.isEmpty()) toast("No music found on this device") else addTracks(tracks)
        }
    }

    // =========================================================== media library

    fun loadLibrary() {
        if (libraryState == LibState.LOADING) return
        libraryState = LibState.LOADING
        mainScope.launch {
            library = withContext(Dispatchers.IO) {
                try { MediaLibrary.scanDevice(app) } catch (_: Exception) { emptyList() }
            }
            libraryState = LibState.READY
        }
    }

    /** "Play by folder" for a folder picked with the system picker (SD card, USB, Downloads…). */
    fun playFolderTree(tree: Uri) {
        toast("Reading folder…")
        mainScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                try { MediaLibrary.scanTree(app, tree) } catch (_: Exception) { emptyList() }
            }
            if (tracks.isEmpty()) toast("No audio files in that folder") else {
                replaceAndPlay(tracks, 0)
                libraryTab = null
            }
        }
    }

    /** Replace the playlist with [tracks] and start playing at [start]. */
    fun replaceAndPlay(tracks: List<Track>, start: Int = 0) {
        if (tracks.isEmpty()) return
        stop()
        playlist.clear()
        playlist.addAll(tracks)
        selection = emptySet()
        loadedIndex = -1
        playIndex(start.coerceIn(0, tracks.lastIndex))
        save()
    }

    fun appendTracks(tracks: List<Track>) = addTracks(tracks)

    // ========================================================= saved playlists

    fun saveCurrentAs(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty()) { toast("Type a name first"); return false }
        if (playlist.isEmpty()) { toast("The playlist is empty"); return false }
        savedPlaylists = savedPlaylists + (n to playlist.toList())
        save()
        toast("Saved \"$n\" (${playlist.size} tracks)")
        return true
    }

    /** Long-press: take the pressed row (or the whole selection) and ask which playlist it goes to. */
    fun beginAddToPlaylist(index: Int) {
        if (index !in playlist.indices) return
        if (index !in selection) selection = setOf(index)
        addToListTracks = selection.sorted().mapNotNull { playlist.getOrNull(it) }
    }

    fun beginAddSelectedToPlaylist() {
        if (selection.isEmpty()) return toast("Tap tracks to select them first")
        addToListTracks = selection.sorted().mapNotNull { playlist.getOrNull(it) }
    }

    /** Creates the playlist if the name is new, otherwise appends (skipping songs it already has). */
    fun addTracksToSaved(name: String, tracks: List<Track>): Boolean {
        val n = name.trim()
        if (n.isEmpty()) { toast("Type a name first"); return false }
        val old = savedPlaylists[n]
        val merged = if (old == null) tracks else old + tracks.filter { t -> old.none { it.uri == t.uri } }
        savedPlaylists = savedPlaylists + (n to merged)
        save()
        val added = merged.size - (old?.size ?: 0)
        toast(if (old == null) "Created \"$n\" with ${tracks.size} song(s)" else "Added $added song(s) to \"$n\"")
        return true
    }

    fun loadSaved(name: String) {
        savedPlaylists[name]?.let { replaceAndPlay(it, 0) }
    }

    fun addSaved(name: String) {
        savedPlaylists[name]?.let { addTracks(it) }
    }

    fun deleteSaved(name: String) {
        savedPlaylists = savedPlaylists - name
        save()
    }

    // ================================================================== radio

    fun searchRadio(name: String?, countryCode: String?) {
        radioLoading = true
        radioError = null
        mainScope.launch {
            try {
                radioResults = withContext(Dispatchers.IO) { RadioBrowser.search(name, countryCode) }
                if (radioResults.isEmpty()) radioError = "No stations found"
            } catch (_: Exception) {
                radioResults = emptyList()
                radioError = "Couldn't reach the radio directory. Check your internet connection."
            } finally {
                radioLoading = false
            }
        }
    }

    /** Streams go into the playlist like any track (shown as LIVE) and start playing. */
    fun playStation(st: Station) {
        val uri = Uri.parse(st.url.trim())
        var idx = playlist.indexOfFirst { it.uri == uri }
        if (idx < 0) {
            playlist.add(Track(uri, st.name, "", 0, album = "Internet radio", folder = st.country, isStream = true))
            idx = playlist.lastIndex
        }
        playIndex(idx)
        save()
    }

    fun isFavorite(st: Station) = favorites.any { it.url == st.url }

    fun toggleFavorite(st: Station) {
        favorites = if (isFavorite(st)) favorites.filterNot { it.url == st.url } else favorites + st
        save()
    }

    fun addCustomStation(name: String, url: String): Station? {
        val u = url.trim()
        if (!(u.startsWith("http://") || u.startsWith("https://"))) {
            toast("Enter a stream URL starting with http:// or https://")
            return null
        }
        val st = Station(name.trim().ifBlank { u.substringAfter("://").substringBefore('/') }, u, "Custom")
        if (!isFavorite(st)) favorites = favorites + st
        save()
        return st
    }

    private fun addTracks(tracks: List<Track>) {
        val existing = playlist.map { it.uri }.toHashSet()
        val fresh = tracks.filter { it.uri !in existing }
        if (fresh.isEmpty()) {
            toast("Already in the playlist")
            return
        }
        val wasEmpty = playlist.isEmpty()
        playlist.addAll(fresh)
        if (wasEmpty) {
            current = 0
            durationMs = playlist[0].durationMs
        }
        toast("Added ${fresh.size} track" + if (fresh.size == 1) "" else "s")
        save()
    }

    fun toggleSelect(i: Int) {
        selection = if (i in selection) selection - i else selection + i
    }

    fun selectAll() { selection = playlist.indices.toSet() }
    fun selectNone() { selection = emptySet() }
    fun invertSelection() { selection = playlist.indices.toSet() - selection }

    fun removeSelected() {
        if (selection.isEmpty()) return toast("Tap tracks to select them first")
        reorder(playlist.filterIndexed { i, _ -> i !in selection })
    }

    fun cropSelected() {
        if (selection.isEmpty()) return toast("Tap tracks to select them first")
        reorder(playlist.filterIndexed { i, _ -> i in selection })
    }

    fun clearPlaylist() {
        stop()
        reorder(emptyList())
        info = null
        durationMs = 0
    }

    fun sortByTitle() = reorder(playlist.sortedBy { it.title.lowercase() })
    fun sortByArtist() = reorder(playlist.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() })))
    fun randomize() = reorder(playlist.shuffled())
    fun reverse() = reorder(playlist.reversed())

    private fun reorder(newList: List<Track>) {
        val cur = playlist.getOrNull(current)
        val loaded = playlist.getOrNull(loadedIndex)
        playlist.clear()
        playlist.addAll(newList)
        current = if (cur == null) -1 else newList.indexOf(cur)
        loadedIndex = if (loaded == null) -1 else newList.indexOf(loaded)
        if (current == -1 && playState == PlayState.STOPPED && newList.isNotEmpty()) current = 0
        selection = emptySet()
        save()
    }

    // =================================================================== misc

    fun setSkinId(id: SkinId) {
        skin = id
        save()
    }

    private fun updateSession() {
        val st = when (playState) {
            PlayState.PLAYING -> PlaybackStateCompat.STATE_PLAYING
            PlayState.PAUSED -> PlaybackStateCompat.STATE_PAUSED
            PlayState.STOPPED -> PlaybackStateCompat.STATE_STOPPED
        }
        var actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP
        if (durationMs > 0) actions = actions or PlaybackStateCompat.ACTION_SEEK_TO
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(st, positionMs, if (playState == PlayState.PLAYING) 1f else 0f)
                .build()
        )
        val t = currentTrack
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, t?.title ?: "RetroPlay")
                .putString(
                    MediaMetadataCompat.METADATA_KEY_ARTIST,
                    if (t?.isStream == true) "Live radio" else t?.artist ?: "",
                )
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, t?.album ?: "")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, if (durationMs > 0) durationMs else -1L)
                .apply { if (currentArt != null && artFor == t?.uri) putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentArt) }
                .build()
        )
        // Fetch the cover once per track, then refresh the lock screen / notification.
        if (t != null && artFor != t.uri) {
            artFor = t.uri
            currentArt = null
            mainScope.launch {
                val bmp = withContext(Dispatchers.IO) { ArtLoader.load(app, t, 512)?.asAndroidBitmap() }
                if (bmp != null && currentTrack?.uri == t.uri) {
                    currentArt = bmp
                    refreshService()
                }
            }
        }
    }

    private fun refreshService() {
        updateSession()
        val i = Intent(app, PlaybackService::class.java)
        try {
            if (playState == PlayState.STOPPED) app.stopService(i)
            else ContextCompat.startForegroundService(app, i)
        } catch (_: Exception) {
        }
    }

    /** Plays [t], adding it to the playlist first when it isn't there yet (used by search). */
    fun playTrack(t: Track) {
        var idx = playlist.indexOfFirst { it.uri == t.uri }
        if (idx < 0) { playlist.add(t); idx = playlist.lastIndex; save() }
        playIndex(idx)
    }

    fun openLyrics() {
        val t = currentTrack
        if (t == null) { toast("Play a song first"); return }
        if (t.isStream) { toast("Lyrics aren't available for radio streams"); return }
        showLyrics = true
        loadLyricsIfNeeded()
    }

    fun closeLyrics() { showLyrics = false }

    private fun loadLyricsIfNeeded() {
        val t = currentTrack ?: return
        if (t.uri == lyricsFor && (lyrics != null || lyricsLoading)) return
        lyricsFor = t.uri
        lyrics = null
        lyricsError = null
        lyricsLoading = true
        mainScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { LyricsFetcher.fetch(t.title, t.artist, t.album, t.durationMs) }
            }
            if (lyricsFor != t.uri) return@launch // the song changed meanwhile
            lyricsLoading = false
            r.onSuccess {
                lyrics = it
                if (it == null) lyricsError = "No lyrics found for this song."
            }.onFailure { lyricsError = "Couldn't reach the lyrics service. Check your internet connection." }
        }
    }

    /** Called when the track changes: refresh the open lyrics overlay for the new song. */
    private fun onTrackChangedForLyrics() {
        if (showLyrics) {
            if (currentTrack?.isStream == true) showLyrics = false else loadLyricsIfNeeded()
        }
    }

    fun checkForUpdate() = Updater.check(app, manual = true)

    /** Exports RetroPlay's own recent log (plus a state summary) and opens the share sheet. */
    private val NL = System.lineSeparator()

    fun shareDebugLog() {
        mainScope.launch {
            val f = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = java.io.File(app.cacheDir, "logs").apply { mkdirs() }
                    val out = java.io.File(dir, "retroplay-log.txt")
                    val text = try {
                        Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime", "-t", "3000"))
                            .inputStream.bufferedReader().readText()
                    } catch (e: Throwable) { "logcat failed: $e" }
                    val ver = app.packageManager.getPackageInfo(app.packageName, 0).versionName
                    out.writeText("RetroPlay $ver" + NL + WidgetUpdater.diag(app, this@PlayerController) + NL + NL + text)
                    out
                }
            }
            f.onSuccess {
                val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.updates", it)
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                app.startActivity(Intent.createChooser(send, "Share RetroPlay log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { toast("Couldn't create the log") }
        }
    }

    private fun toast(msg: String) = Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()

    fun save() {
        val saved = JSONObject()
        savedPlaylists.forEach { (name, tracks) -> saved.put(name, tracksToJson(tracks)) }
        val favs = JSONArray()
        favorites.forEach {
            favs.put(
                JSONObject().put("n", it.name).put("u", it.url).put("c", it.country)
                    .put("k", it.codec).put("b", it.bitrate)
            )
        }
        prefs.edit()
            .putString("playlist", tracksToJson(playlist).toString())
            .putString("saved", saved.toString())
            .putString("favs", favs.toString())
            .putBoolean("showArt", showArt)
            .putBoolean("showOnLock", showOnLock)
            .putInt("widgetLcd", widgetLcd)
            .putInt("current", current)
            .putFloat("volume", volume)
            .putFloat("balance", balance)
            .putBoolean("shuffle", shuffleOn)
            .putBoolean("repeat", repeatOn)
            .putBoolean("eqOn", eqOn)
            .putFloat("preamp", preamp)
            .putString("bands", bands.joinToString(","))
            .putString("skin", skin.name)
            .putString("vis", visMode.name)
            .putBoolean("showEq", showEq)
            .putBoolean("showPl", showPl)
            .putBoolean("remaining", showRemaining)
            .apply()
    }

    private fun tracksToJson(tracks: List<Track>): JSONArray {
        val arr = JSONArray()
        tracks.forEach {
            arr.put(
                JSONObject()
                    .put("u", it.uri.toString()).put("t", it.title).put("a", it.artist)
                    .put("d", it.durationMs).put("al", it.album).put("ai", it.albumId)
                    .put("f", it.folder).put("s", it.isStream)
            )
        }
        return arr
    }

    private fun tracksFromJson(arr: JSONArray): List<Track> = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Track(
            uri = Uri.parse(o.getString("u")),
            title = o.optString("t", "Unknown"),
            artist = o.optString("a"),
            durationMs = o.optLong("d"),
            album = o.optString("al"),
            albumId = o.optLong("ai", -1),
            folder = o.optString("f"),
            isStream = o.optBoolean("s"),
        )
    }

    private fun restore() {
        try {
            playlist += tracksFromJson(JSONArray(prefs.getString("playlist", "[]")))
            val saved = JSONObject(prefs.getString("saved", "{}") ?: "{}")
            savedPlaylists = saved.keys().asSequence().associateWith { tracksFromJson(saved.getJSONArray(it)) }
            val favs = JSONArray(prefs.getString("favs", "[]"))
            favorites = (0 until favs.length()).map { i ->
                val o = favs.getJSONObject(i)
                Station(o.getString("n"), o.getString("u"), o.optString("c"), o.optString("k"), o.optInt("b"))
            }
            showArt = prefs.getBoolean("showArt", true)
            showOnLock = prefs.getBoolean("showOnLock", true)
            widgetLcd = prefs.getInt("widgetLcd", 1).coerceIn(0, 1)
            current = prefs.getInt("current", if (playlist.isEmpty()) -1 else 0)
                .coerceIn(-1, playlist.lastIndex)
            playlist.getOrNull(current)?.let { durationMs = it.durationMs }
            volume = prefs.getFloat("volume", 0.8f)
            balance = prefs.getFloat("balance", 0f)
            shuffleOn = prefs.getBoolean("shuffle", false)
            repeatOn = prefs.getBoolean("repeat", true)
            eqOn = prefs.getBoolean("eqOn", true)
            preamp = prefs.getFloat("preamp", 0f)
            prefs.getString("bands", null)?.split(",")?.mapNotNull { it.toFloatOrNull() }
                ?.takeIf { it.size == 10 }?.forEachIndexed { i, v -> bands[i] = v }
            skin = SkinId.valueOf(prefs.getString("skin", SkinId.CLASSIC.name)!!)
            visMode = VisMode.valueOf(prefs.getString("vis", VisMode.SPECTRUM.name)!!)
            showEq = prefs.getBoolean("showEq", true)
            showPl = prefs.getBoolean("showPl", true)
            showRemaining = prefs.getBoolean("remaining", false)
        } catch (_: Exception) {
        }
    }
}
