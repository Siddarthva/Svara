package com.svara.app.playback.analysis

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audio analysis for ZENITH's visuals. Runs only while all of these hold: a visual that needs it
 * is on screen and the app is in the foreground ([setWanted]), audio is playing ([setPlaying]),
 * and the effects policy allows a non-zero frame rate. Publishes band energies, never raw audio.
 */
@Singleton
class AudioAnalysis @Inject constructor() {

    private val _bands = MutableStateFlow(AudioBands.ZERO)
    val bands: StateFlow<AudioBands> = _bands.asStateFlow()

    private val _supported = MutableStateFlow(true)
    /** False once the device delivered audio in a format the processor cannot read. */
    val supported: StateFlow<Boolean> = _supported.asStateFlow()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** Read on the audio thread. */
    @Volatile internal var enabled = false
        private set
    @Volatile internal var minIntervalNanos = 50_000_000L
        private set

    private var wanted = false
    private var playing = false
    private var fps = 0

    /** Work done, for measurement. */
    val analysedFrames = AtomicLong()
    val analysisNanos = AtomicLong()

    private val executor = ScheduledThreadPoolExecutor(1) { r -> Thread(r, "svara-analysis").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private var previous = AudioBands.ZERO

    @Synchronized
    fun setWanted(wanted: Boolean, maxFps: Int) {
        this.wanted = wanted
        this.fps = maxFps
        update(if (wanted) "visual shown" else "visual hidden or app backgrounded")
    }

    @Synchronized
    fun setPlaying(playing: Boolean) {
        this.playing = playing
        update(if (playing) "playing" else "paused")
    }

    internal fun reportSupported(value: Boolean) {
        if (_supported.value != value) _supported.value = value
    }

    private fun update(reason: String) {
        val next = wanted && playing && fps > 0 && _supported.value
        if (fps > 0) minIntervalNanos = 1_000_000_000L / fps
        if (!next) executor.queue.clear()
        if (next == enabled) return
        enabled = next
        _active.value = next
        if (!next) {
            previous = AudioBands.ZERO
            _bands.value = AudioBands.ZERO
        }
        Log.i(TAG, "Analysis ${if (next) "started" else "stopped"} ($reason) at ${SystemClock.elapsedRealtime()}")
    }

    /**
     * Called from the audio thread with a mono window. Media3 hands audio over in bursts, so each
     * window is analysed after [delayNanos] (its offset within the burst) to keep updates evenly
     * spaced. Windows are dropped if the analysis thread falls behind.
     */
    internal fun submit(window: FloatArray, sampleRate: Int, delayNanos: Long) {
        if (executor.queue.size >= MAX_PENDING) return
        executor.schedule({
            if (!enabled) return@schedule
            val t0 = System.nanoTime()
            try {
                val (raw, rawSpectrum) = SpectrumMath.analyse(window, sampleRate)
                val bass = SpectrumMath.smooth(previous.bass, SpectrumMath.normalise(raw[0]))
                val mid = SpectrumMath.smooth(previous.mid, SpectrumMath.normalise(raw[1]))
                val treble = SpectrumMath.smooth(previous.treble, SpectrumMath.normalise(raw[2]))
                val spectrum = FloatArray(SpectrumMath.SPECTRUM_BANDS) { i ->
                    SpectrumMath.smooth(previous.spectrum.getOrElse(i) { 0f }, SpectrumMath.normalise(rawSpectrum[i]))
                }
                val next = AudioBands(bass, mid, treble, (bass * 0.5f + mid * 0.35f + treble * 0.15f), spectrum)
                previous = next
                if (enabled) _bands.value = next
                analysedFrames.incrementAndGet()
            } finally {
                analysisNanos.addAndGet(System.nanoTime() - t0)
            }
        }, delayNanos, TimeUnit.NANOSECONDS)
    }

    private companion object {
        const val TAG = "AudioAnalysis"
        const val MAX_PENDING = 64
    }
}

/**
 * Sits in the audio pipeline for the life of the player. Audio passes through unchanged (a plain
 * copy). Media3 fixes processors when the player is built, so instead of attaching and detaching,
 * the analysis work is switched on and off by [AudioAnalysis.enabled]. For formats it can't read
 * it reports itself inactive, and Media3 skips it entirely.
 */
@UnstableApi
class SpectrumAudioProcessor(private val analysis: AudioAnalysis) : BaseAudioProcessor() {

    private val window = FloatArray(WINDOW)
    private var filled = 0
    private var sampleRate = 44_100
    private var channels = 2
    /** Samples since the last analysed window; throttles by audio time, not wall time. */
    private var sinceSubmit = 0L

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            analysis.reportSupported(false)
            return AudioFormat.NOT_SET // inactive: Media3 bypasses this processor
        }
        analysis.reportSupported(true)
        filled = 0
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        if (analysis.enabled) tap(inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN))
        replaceOutputBuffer(size).put(inputBuffer).flip()
    }

    private fun tap(data: ByteBuffer) {
        val frameBytes = 2 * channels
        val samplesPerUpdate = sampleRate * analysis.minIntervalNanos / 1_000_000_000L
        var frameInBuffer = 0L
        while (data.remaining() >= frameBytes) {
            var sum = 0
            for (c in 0 until channels) sum += data.short.toInt()
            window[filled] = sum / (channels * 32768f)
            filled = (filled + 1) % WINDOW
            frameInBuffer++
            if (++sinceSubmit >= samplesPerUpdate) {
                sinceSubmit = 0
                // The ring buffer holds the latest WINDOW samples; unroll it in order.
                val ordered = FloatArray(WINDOW) { i -> window[(filled + i) % WINDOW] }
                analysis.submit(ordered, sampleRate, frameInBuffer * 1_000_000_000L / sampleRate)
            }
        }
    }

    private companion object {
        /** 1024 samples ≈ 23 ms at 44.1 kHz: enough resolution for bass/mid/treble. */
        const val WINDOW = 1024
    }
}
