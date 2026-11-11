package com.svara.app.playback

import com.svara.app.playback.analysis.SpectrumMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SpectrumMathTest {

    private fun tone(hz: Double, rate: Int = 44_100, n: Int = 1024, amp: Double = 0.8) =
        FloatArray(n) { i -> (amp * sin(2 * PI * hz * i / rate)).toFloat() }

    @Test
    fun `fft finds a pure tone's bin`() {
        val n = 64
        val re = DoubleArray(n) { i -> sin(2 * PI * 8 * i / n) }
        val im = DoubleArray(n)
        SpectrumMath.fft(re, im)
        val mags = DoubleArray(n / 2) { re[it] * re[it] + im[it] * im[it] }
        assertEquals(8, mags.indices.maxBy { mags[it] })
    }

    @Test
    fun `low, middle and high tones land in bass, mid and treble`() {
        val bass = SpectrumMath.rawBands(tone(80.0), 44_100)
        assertTrue(bass[0] > bass[1] && bass[0] > bass[2])
        val mid = SpectrumMath.rawBands(tone(1_000.0), 44_100)
        assertTrue(mid[1] > mid[0] && mid[1] > mid[2])
        val treble = SpectrumMath.rawBands(tone(9_000.0), 44_100)
        assertTrue(treble[2] > treble[0] && treble[2] > treble[1])
    }

    @Test
    fun `silence is zero and loud is near one`() {
        val silent = SpectrumMath.rawBands(FloatArray(1024), 44_100)
        assertEquals(0f, SpectrumMath.normalise(silent[0]))
        assertTrue(SpectrumMath.normalise(SpectrumMath.rawBands(tone(80.0, amp = 1.0), 44_100)[0]) > 0.5f)
        assertEquals(1f, SpectrumMath.normalise(1.0))
    }

    @Test
    fun `smoothing attacks fast and releases slowly`() {
        val up = SpectrumMath.smooth(0f, 1f)
        val down = SpectrumMath.smooth(1f, 0f)
        assertTrue(up > 0.5f)
        assertTrue(down > 0.8f)
    }
}
