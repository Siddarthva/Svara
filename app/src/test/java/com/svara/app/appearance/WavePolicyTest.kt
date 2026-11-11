package com.svara.app.appearance

import com.svara.app.appearance.tokens.WaveTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavePolicyTest {
    private fun groups(limits: EffectLimits, reduced: Boolean = false) =
        MotionGroups.resolve(limits, MotionSpec.from(limits, AnimationStyle.SUBTLE, reduced, 1f, 100, false), Customization(), null)

    @Test
    fun `off in reduced motion, efficiency, battery saver and severe heat`() {
        listOf(
            groups(EffectsPolicy.limitsFor(PerformanceMode.IMMERSIVE), reduced = true),
            groups(EffectsPolicy.limitsFor(PerformanceMode.EFFICIENCY)),
            groups(EffectsPolicy.resolve(PerformanceMode.IMMERSIVE, true, ThermalLevel.NONE)),
            groups(EffectsPolicy.resolve(PerformanceMode.IMMERSIVE, false, ThermalLevel.SEVERE)),
        ).forEach { assertEquals(WaveSpec.OFF, WavePolicy.resolve(it, Customization())) }
    }

    @Test
    fun `frame caps and layers per mode`() {
        val balanced = WavePolicy.resolve(groups(EffectsPolicy.limitsFor(PerformanceMode.BALANCED)), Customization())
        assertEquals(WaveTokens.BALANCED_FPS, balanced.fps)
        assertEquals(2, balanced.layers)
        val immersive = WavePolicy.resolve(groups(EffectsPolicy.limitsFor(PerformanceMode.IMMERSIVE)), Customization())
        assertEquals(WaveTokens.IMMERSIVE_FPS, immersive.fps)
        assertEquals(3, immersive.layers)
        assertTrue(immersive.fps <= 30 && balanced.fps <= 20)
    }

    @Test
    fun `switch and strength`() {
        val g = groups(EffectsPolicy.limitsFor(PerformanceMode.BALANCED))
        assertEquals(WaveSpec.OFF, WavePolicy.resolve(g, Customization(liveWave = false)))
        WaveStrength.entries.forEach { s -> assertTrue(WavePolicy.resolve(g, Customization(waveStrength = s)).alpha in 0.12f..0.20f) }
    }

    @Test
    fun `wave settings reset with motion and decode safely`() {
        assertEquals(StudioSection.MOTION, CustomField.LIVE_WAVE.section)
        assertEquals(StudioSection.MOTION, CustomField.WAVE_STRENGTH.section)
        val c = CustomizationCodec.decode(mapOf(CustomField.WAVE_STRENGTH to "garbage"))
        assertEquals(WaveStrength.NORMAL, c.waveStrength)
        assertTrue(c.liveWave)
    }
}
