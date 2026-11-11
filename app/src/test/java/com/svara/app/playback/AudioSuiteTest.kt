package com.svara.app.playback

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import com.svara.app.appearance.VisualSettings
import com.svara.app.appearance.VisualStyle
import com.svara.app.data.repository.SettingsCodec
import com.svara.app.domain.audio.EffectsCapabilities
import com.svara.app.domain.audio.EqMath
import com.svara.app.domain.audio.EqPreset
import com.svara.app.domain.audio.EqSettings
import com.svara.app.domain.audio.FadeCurve
import com.svara.app.domain.model.AppSettings
import com.svara.app.nowplaying.VisualRenderer
import com.svara.app.playback.analysis.AudioBands
import com.svara.app.playback.analysis.SpectrumMath
import com.svara.app.playback.effects.AudioEffectsController
import com.svara.app.playback.effects.AudioEffectsHub
import com.svara.app.playback.effects.EffectFactory
import com.svara.app.playback.effects.EqualizerHandle
import com.svara.app.playback.effects.LoudnessHandle
import com.svara.app.playback.effects.StrengthHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** Phase 3: equalizer maths and failure containment, crossfade curves, spectrum bands, visual guard. */
class AudioSuiteTest {

    private class FakeEq(var failOnLevel: Boolean = false) : EqualizerHandle {
        override val bandCentersHz = listOf(60, 230, 910, 3_600, 14_000)
        override val minLevelMb = -1500
        override val maxLevelMb = 1500
        val levels = IntArray(5)
        var on = false
        var released = false
        override fun setLevel(band: Int, levelMb: Int) {
            if (failOnLevel) throw IllegalStateException("driver error")
            levels[band] = levelMb
        }
        override fun setEnabled(on: Boolean) { this.on = on }
        override fun release() { released = true }
    }

    private class FakeStrength : StrengthHandle {
        var level = 0
        var on = false
        var released = false
        override fun setStrength(strength: Int) { level = strength }
        override fun setEnabled(on: Boolean) { this.on = on }
        override fun release() { released = true }
    }

    private class Factory(
        val eq: FakeEq? = FakeEq(),
        val bass: FakeStrength? = FakeStrength(),
        val unsupported: Boolean = false,
    ) : EffectFactory {
        override fun equalizer(session: Int): EqualizerHandle = if (unsupported || eq == null) throw UnsupportedOperationException("no eq") else eq
        override fun bassBoost(session: Int): StrengthHandle = if (unsupported || bass == null) throw UnsupportedOperationException("no bass") else bass
        override fun virtualizer(session: Int): StrengthHandle = throw UnsupportedOperationException("no virtualizer")
        override fun loudness(session: Int): LoudnessHandle = throw UnsupportedOperationException("no loudness")
    }

    @After
    fun restoreRenderer() {
        VisualRenderer.drawHook = { s, b, c, g -> with(VisualRenderer) { draw(s, b, c, g) } }
    }

    @Test
    fun `presets are computed for the device's bands and clamped`() {
        val centers = listOf(60, 230, 910, 3_600, 14_000)
        assertEquals(List(5) { 0 }, EqMath.levelsFor(EqPreset.FLAT, centers, -1500, 1500))
        val bass = EqMath.levelsFor(EqPreset.BASS_BOOST, centers, -1500, 1500)
        assertTrue("bass boost lifts the lowest band most", bass[0] > bass[2] && bass[0] > 0)
        val treble = EqMath.levelsFor(EqPreset.TREBLE, centers, -1500, 1500)
        assertTrue(treble[4] > treble[1])
        // A device with a narrow range clamps.
        assertTrue(EqMath.levelsFor(EqPreset.BASS_BOOST, centers, -300, 300).all { it in -300..300 })
        EqPreset.entries.forEach { p -> assertEquals(5, EqMath.levelsFor(p, centers, -1500, 1500).size) }
    }

    @Test
    fun `custom and user preset levels are refitted when the band count changes`() {
        val caps = EffectsCapabilities(equalizer = true, bandCentersHz = listOf(100, 1000, 10_000), minLevelMb = -1000, maxLevelMb = 1000, attached = true)
        assertEquals(listOf(200, -1000, 1000), EqMath.resolve(EqSettings(preset = null, bandLevelsMb = listOf(200, -5000, 5000)), caps))
        assertEquals(listOf(0, 0, 0), EqMath.resolve(EqSettings(preset = null, bandLevelsMb = listOf(1, 2)), caps))
        val user = EqSettings(preset = "Mine", userPresets = mapOf("Mine" to listOf(100, 200, 300)))
        assertEquals(listOf(100, 200, 300), EqMath.resolve(user, caps))
    }

    @Test
    fun `effects apply, report capabilities and release`() {
        val factory = Factory()
        val hub = AudioEffectsHub()
        val controller = AudioEffectsController(factory, hub) {}
        controller.attach(42)
        val caps = hub.capabilities.value
        assertTrue(caps.attached && caps.equalizer && caps.bassBoost)
        assertFalse("unsupported effects are reported as absent", caps.virtualizer || caps.loudness)
        assertTrue("absent effects aren't failures", caps.failures.isEmpty())
        controller.apply(EqSettings(enabled = true, preset = EqPreset.BASS_BOOST.name, bassBoost = 600))
        assertTrue(factory.eq!!.on)
        assertTrue(factory.eq.levels[0] > 0)
        assertEquals(600, factory.bass!!.level)
        assertTrue(factory.bass.on)
        controller.apply(EqSettings(enabled = false))
        assertFalse("effects off: nothing processes the audio", factory.eq.on || factory.bass.on)
        controller.release()
        assertTrue(factory.eq.released && factory.bass.released)
        assertFalse(hub.capabilities.value.attached)
    }

    @Test
    fun `an effect that fails is turned off and reported, without touching the others`() {
        val factory = Factory(eq = FakeEq(failOnLevel = true))
        val hub = AudioEffectsHub()
        val messages = mutableListOf<String>()
        val controller = AudioEffectsController(factory, hub) { messages += it }
        controller.attach(7)
        controller.apply(EqSettings(enabled = true, preset = EqPreset.VOCAL.name, bassBoost = 300))
        val caps = hub.capabilities.value
        assertFalse("the failing equalizer is turned off", caps.equalizer)
        assertTrue(factory.eq!!.released)
        assertTrue(caps.failures.containsKey(AudioEffectsController.EQUALIZER))
        assertEquals(1, messages.size)
        assertTrue(messages.single().contains("Playback isn't affected"))
        assertEquals("bass boost still applied", 300, factory.bass!!.level)
    }

    @Test
    fun `a device without effects gives a clear unsupported state and no crash`() {
        val hub = AudioEffectsHub()
        val controller = AudioEffectsController(Factory(unsupported = true), hub) {}
        controller.attach(3)
        controller.apply(EqSettings(enabled = true, bassBoost = 500))
        assertFalse(hub.capabilities.value.anySupported)
        assertTrue(hub.capabilities.value.attached)
    }

    @Test
    fun `fade curves start silent, end full and rise steadily`() {
        FadeCurve.entries.forEach { c ->
            assertEquals(0f, c.gain(0f), 1e-4f)
            assertEquals(1f, c.gain(1f), 1e-4f)
            var last = -1f
            for (i in 0..20) {
                val g = c.gain(i / 20f)
                assertTrue("${c.name} not monotonic", g >= last)
                last = g
            }
        }
        assertTrue("equal power is louder at the midpoint", FadeCurve.EQUAL_POWER.gain(0.5f) > FadeCurve.LINEAR.gain(0.5f))
    }

    @Test
    fun `equalizer and curve settings round-trip`() {
        val s = AppSettings(
            crossfadeCurve = FadeCurve.S_CURVE,
            eq = EqSettings(enabled = true, preset = null, bandLevelsMb = listOf(-300, 0, 450), bassBoost = 250, virtualizer = 100, loudnessMb = 600,
                userPresets = mapOf("Car" to listOf(1, 2, 3), "Late, night" to listOf(-1, -2))),
        )
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
        assertEquals(EqSettings(), SettingsCodec.decode(emptyMap()).eq)
    }

    @Test
    fun `a 1 kHz tone lands in the matching spectrum band`() {
        val rate = 44_100
        val samples = FloatArray(1024) { i -> sin(2 * PI * 1_000 * i / rate).toFloat() * 0.5f }
        val (three, spectrum) = SpectrumMath.analyse(samples, rate)
        assertEquals(3, three.size)
        assertEquals(SpectrumMath.SPECTRUM_BANDS, spectrum.size)
        val peak = spectrum.indices.maxBy { spectrum[it] }
        val expected = ((kotlin.math.ln(1_000.0) - kotlin.math.ln(SpectrumMath.SPECTRUM_LOW_HZ)) /
            (kotlin.math.ln(SpectrumMath.SPECTRUM_HIGH_HZ) - kotlin.math.ln(SpectrumMath.SPECTRUM_LOW_HZ)) * SpectrumMath.SPECTRUM_BANDS).toInt()
        assertTrue("peak at $peak, expected near $expected", kotlin.math.abs(peak - expected) <= 1)
        assertEquals(8, SpectrumMath.resample(FloatArray(32) { 1f }, 8).size)
        assertTrue(SpectrumMath.resample(FloatArray(32) { 1f }, 48).all { it == 1f })
    }

    @Test
    fun `a visual that throws freezes instead of crashing`() {
        var failed = false
        VisualRenderer.drawHook = { _, _, _, _ -> throw IllegalStateException("broken style") }
        VisualRenderer.safeDraw(CanvasDrawScope(), VisualSettings(style = VisualStyle.CIRCULAR), AudioBands.ZERO, Color.Unspecified, false) { failed = true }
        assertTrue(failed)
    }

    @Test
    fun `visual levels follow sensitivity and intensity and stay in range`() {
        val bands = AudioBands(1f, 1f, 1f, 1f, FloatArray(SpectrumMath.SPECTRUM_BANDS) { 0.5f })
        val quiet = VisualRenderer.levels(VisualSettings(intensity = 10, sensitivity = 0), bands, 16)
        val loud = VisualRenderer.levels(VisualSettings(intensity = 100, sensitivity = 100), bands, 16)
        assertTrue(quiet.all { it in 0f..1f } && loud.all { it in 0f..1f })
        assertTrue(loud[0] > quiet[0])
        assertEquals(16, loud.size)
    }
}
