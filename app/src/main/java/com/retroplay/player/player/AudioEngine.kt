package com.retroplay.player.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.PowerManager
import kotlin.math.ln

/**
 * Thin wrapper around MediaPlayer + the platform Equalizer and Visualizer effects.
 * One fixed audio session is reused for every track, so the effects stay attached.
 */
class AudioEngine(private val ctx: Context) {

    val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val sessionId = ctx.getSystemService(AudioManager::class.java).generateAudioSessionId()
    private var player: MediaPlayer? = null
    private var eq: Equalizer? = null
    private var vis: Visualizer? = null

    var prepared = false
        private set

    var onPrepared: (() -> Unit)? = null
    var onCompletion: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onVisData: ((fft: ByteArray?, wave: ByteArray?) -> Unit)? = null

    val position: Int get() = if (prepared) player?.currentPosition ?: 0 else 0
    val duration: Int get() = if (prepared) player?.duration ?: 0 else 0

    private fun obtain(): MediaPlayer = player ?: MediaPlayer().also { p ->
        p.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK)
        p.setOnPreparedListener {
            prepared = true
            onPrepared?.invoke()
        }
        p.setOnCompletionListener { onCompletion?.invoke() }
        p.setOnErrorListener { _, what, extra ->
            prepared = false
            onError?.invoke("Playback error ($what, $extra)")
            true
        }
        player = p
    }

    /** Throws if the file can't be opened. Calls [onPrepared] when ready. */
    fun load(uri: Uri) {
        val p = obtain()
        prepared = false
        p.reset()
        p.setAudioAttributes(attributes)
        p.audioSessionId = sessionId
        p.setDataSource(ctx, uri)
        p.prepareAsync()
    }

    fun play() {
        if (prepared) player?.start()
    }

    fun pause() {
        if (prepared && player?.isPlaying == true) player?.pause()
    }

    fun stop() {
        if (!prepared) return
        player?.let {
            if (it.isPlaying) it.pause()
            it.seekTo(0)
        }
    }

    fun seekTo(ms: Int) {
        if (prepared) player?.seekTo(ms)
    }

    fun setVolume(left: Float, right: Float) {
        try { player?.setVolume(left, right) } catch (_: Exception) {}
    }

    // ---------------------------------------------------------------- Equalizer

    /**
     * The UI has 10 Winamp-style bands; phones usually expose 5 hardware bands.
     * Each hardware band gets the UI curve's value at its centre frequency
     * (interpolated on a log-frequency axis), plus the preamp.
     */
    fun applyEq(enabled: Boolean, preampDb: Float, bandsDb: List<Float>) {
        if (eq == null) eq = try { Equalizer(0, sessionId) } catch (_: Exception) { null }
        val e = eq ?: return
        try {
            e.enabled = enabled
            if (!enabled) return
            val range = e.bandLevelRange
            for (b in 0 until e.numberOfBands.toInt()) {
                val hz = e.getCenterFreq(b.toShort()) / 1000f
                val db = curveAt(hz, bandsDb) + preampDb
                val mb = (db * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                e.setBandLevel(b.toShort(), mb.toShort())
            }
        } catch (_: Exception) {
        }
    }

    private fun curveAt(hz: Float, bands: List<Float>): Float {
        if (hz <= EQ_FREQS.first()) return bands.first()
        if (hz >= EQ_FREQS.last()) return bands.last()
        for (i in 0 until EQ_FREQS.size - 1) {
            val f0 = EQ_FREQS[i]
            val f1 = EQ_FREQS[i + 1]
            if (hz in f0..f1) {
                val t = (ln(hz) - ln(f0)) / (ln(f1) - ln(f0))
                return bands[i] + (bands[i + 1] - bands[i]) * t
            }
        }
        return 0f
    }

    // --------------------------------------------------------------- Visualizer

    fun setVisualizer(on: Boolean) {
        if (!on) {
            vis?.let { try { it.enabled = false; it.release() } catch (_: Exception) {} }
            vis = null
            return
        }
        if (vis != null || player == null) return
        vis = try {
            Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, rate: Int) {
                        onVisData?.invoke(null, waveform?.copyOf())
                    }

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {
                        onVisData?.invoke(fft?.copyOf(), null)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, true)
                enabled = true
            }
        } catch (_: Exception) {
            null
        }
    }
}
