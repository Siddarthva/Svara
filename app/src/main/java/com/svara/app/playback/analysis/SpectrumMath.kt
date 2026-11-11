package com.svara.app.playback.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Energy per band, each 0..1 after normalisation. Not pitch: these are loudness bands only.
 * [spectrum] holds [SpectrumMath.SPECTRUM_BANDS] smoothed, log-spaced bands (low to high) for
 * the visualiser gallery; it is a small array of band values, never raw audio.
 */
class AudioBands(val bass: Float, val mid: Float, val treble: Float, val energy: Float, val spectrum: FloatArray = EMPTY) {
    companion object {
        private val EMPTY = FloatArray(SpectrumMath.SPECTRUM_BANDS)
        val ZERO = AudioBands(0f, 0f, 0f, 0f)
    }
}

/** Windowed FFT and band energies. Pure functions so they are unit tested on the JVM. */
object SpectrumMath {
    const val BASS_MAX_HZ = 250.0
    const val MID_MAX_HZ = 4_000.0

    /** In-place iterative radix-2 FFT. [re] and [im] must have the same power-of-two length. */
    fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(n == im.size && n > 0 && (n and (n - 1)) == 0) { "length must be a power of two" }
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2 * PI / len
            val wr = cos(angle)
            val wi = sin(angle)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val ar = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val ai = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k + len / 2] = re[i + k] - ar
                    im[i + k + len / 2] = im[i + k] - ai
                    re[i + k] += ar
                    im[i + k] += ai
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * Raw (un-normalised) band magnitudes for mono [samples] in -1..1, Hann-windowed.
     * Returns RMS-like magnitudes for bass (<250 Hz), mid (250 Hz–4 kHz) and treble (>4 kHz).
     */
    fun rawBands(samples: FloatArray, sampleRate: Int): DoubleArray = analyse(samples, sampleRate).first

    /**
     * One FFT for both outputs: the three raw bands and [SPECTRUM_BANDS] raw log-spaced bands
     * between [SPECTRUM_LOW_HZ] and [SPECTRUM_HIGH_HZ].
     */
    fun analyse(samples: FloatArray, sampleRate: Int): Pair<DoubleArray, DoubleArray> {
        val n = samples.size
        val re = DoubleArray(n) { i -> samples[i] * (0.5 - 0.5 * cos(2 * PI * i / (n - 1))) }
        val im = DoubleArray(n)
        fft(re, im)
        val binHz = sampleRate.toDouble() / n
        val sums = DoubleArray(3)
        val counts = IntArray(3)
        for (k in 1 until n / 2) {
            val f = k * binHz
            val band = when {
                f < BASS_MAX_HZ -> 0
                f < MID_MAX_HZ -> 1
                else -> 2
            }
            sums[band] += re[k] * re[k] + im[k] * im[k]
            counts[band]++
        }
        val three = DoubleArray(3) { b -> if (counts[b] == 0) 0.0 else sqrt(sums[b] / counts[b]) / n }
        val spec = DoubleArray(SPECTRUM_BANDS)
        val specCounts = IntArray(SPECTRUM_BANDS)
        val logLow = ln(SPECTRUM_LOW_HZ)
        val logSpan = ln(SPECTRUM_HIGH_HZ) - logLow
        for (k in 1 until n / 2) {
            val f = k * binHz
            if (f < SPECTRUM_LOW_HZ || f >= SPECTRUM_HIGH_HZ) continue
            val b = ((ln(f) - logLow) / logSpan * SPECTRUM_BANDS).toInt().coerceIn(0, SPECTRUM_BANDS - 1)
            spec[b] += re[k] * re[k] + im[k] * im[k]
            specCounts[b]++
        }
        for (b in 0 until SPECTRUM_BANDS) spec[b] = if (specCounts[b] == 0) 0.0 else sqrt(spec[b] / specCounts[b]) / n
        // Low bands may contain no bin at this resolution: borrow the nearest filled neighbour.
        for (b in 0 until SPECTRUM_BANDS) if (specCounts[b] == 0) spec[b] = spec.getOrElse(b + 1) { 0.0 }.takeIf { specCounts.getOrElse(b + 1) { 0 } > 0 } ?: spec.getOrElse(b - 1) { 0.0 }
        return three to spec
    }

    const val SPECTRUM_BANDS = 32
    const val SPECTRUM_LOW_HZ = 40.0
    const val SPECTRUM_HIGH_HZ = 16_000.0

    /** Averages [bands] down (or spreads them out) to [count] values for drawing. */
    fun resample(bands: FloatArray, count: Int): FloatArray {
        if (count <= 0 || bands.isEmpty()) return FloatArray(count.coerceAtLeast(0))
        return FloatArray(count) { i ->
            val from = i * bands.size / count
            val to = ((i + 1) * bands.size / count).coerceAtLeast(from + 1).coerceAtMost(bands.size)
            var sum = 0f
            for (j in from until to) sum += bands[j]
            sum / (to - from)
        }
    }

    /** Maps a magnitude to 0..1 on a log scale (about 60 dB of range). */
    fun normalise(magnitude: Double): Float {
        if (magnitude <= 1e-6) return 0f
        val db = 20 * ln(magnitude) / ln(10.0) // ~ -120..0
        return ((db + 70) / 60).toFloat().coerceIn(0f, 1f)
    }

    /** Fast attack, slower release, so visuals react to beats without flicker. */
    fun smooth(previous: Float, target: Float): Float =
        if (target > previous) previous + (target - previous) * 0.6f else previous + (target - previous) * 0.15f
}
