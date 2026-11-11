package com.svara.app.appearance

import com.svara.app.appearance.tokens.MotionTokens
import com.svara.app.appearance.tokens.RagaSpec
import com.svara.app.appearance.tokens.ShrutiSpec
import com.svara.app.appearance.tokens.SpandaSpec
import com.svara.app.appearance.tokens.TalaSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Part 3's animation-by-mode table, applied to each layout preset's motion tokens. */
class PresetEffectsTest {

    private fun effects(
        mode: PerformanceMode,
        motion: MotionTokens = TalaSpec.tokens.motion,
        motionEnabled: Boolean = true,
        visualiser: VisualiserMode = VisualiserMode.OFF,
        batterySaver: Boolean = false,
        thermal: ThermalLevel = ThermalLevel.NONE,
    ) = PresetEffects.resolve(motion, EffectsPolicy.resolve(mode, batterySaver, thermal), motionEnabled, visualiser)

    private val presets = listOf(ShrutiSpec.tokens, TalaSpec.tokens, RagaSpec.tokens, SpandaSpec.tokens)

    @Test
    fun `efficiency keeps only 100 ms tap feedback`() {
        presets.forEach { t ->
            val e = effects(PerformanceMode.EFFICIENCY, t.motion, visualiser = VisualiserMode.FULL_SPECTRUM)
            assertTrue(e.pressMs in 1..100)
            assertTrue(e.playPressMs <= 100)
            assertEquals(0, e.transitionMs)
            assertEquals(0, e.tabCrossfadeMs)
            assertEquals(0, e.pagerMs)
            assertFalse(e.heartPop)
            assertFalse(e.artworkSpring)
            assertEquals(0, e.breathingFps)
            assertFalse(e.liveBlur)
            assertEquals(VisualiserMode.OFF, e.visualiser)
            assertEquals(0, e.glowFps)
            assertEquals(0, e.spectrumFps)
        }
    }

    @Test
    fun `balanced allows feedback micro-animations but keeps backgrounds static`() {
        val tala = effects(PerformanceMode.BALANCED, TalaSpec.tokens.motion)
        assertTrue(tala.artworkSpring)
        assertEquals(150, tala.tabCrossfadeMs)
        assertEquals(200, tala.pagerMs)
        assertTrue(tala.transitionMs in 150..250)
        assertFalse(tala.liveBlur)
        val raga = effects(PerformanceMode.BALANCED, RagaSpec.tokens.motion)
        assertTrue(raga.heartPop)
        assertEquals(200, raga.heartPopMs)
        assertEquals(0, raga.breathingFps)
        // Reactive layers need Immersive; Balanced shows the static Ambient glow instead.
        val spanda = effects(PerformanceMode.BALANCED, SpandaSpec.tokens.motion, visualiser = VisualiserMode.REACTIVE)
        assertEquals(VisualiserMode.AMBIENT, spanda.visualiser)
        assertEquals(0, spanda.glowFps)
    }

    @Test
    fun `immersive adds breathing, live blur and audio-reactive layers within their caps`() {
        assertEquals(10, effects(PerformanceMode.IMMERSIVE, RagaSpec.tokens.motion).breathingFps)
        assertTrue(effects(PerformanceMode.IMMERSIVE, TalaSpec.tokens.motion).liveBlur)
        assertFalse(effects(PerformanceMode.IMMERSIVE, SpandaSpec.tokens.motion).liveBlur)
        val reactive = effects(PerformanceMode.IMMERSIVE, SpandaSpec.tokens.motion, visualiser = VisualiserMode.REACTIVE)
        assertEquals(20, reactive.glowFps)
        assertEquals(0, reactive.spectrumFps)
        val full = effects(PerformanceMode.IMMERSIVE, SpandaSpec.tokens.motion, visualiser = VisualiserMode.FULL_SPECTRUM)
        assertEquals(20, full.glowFps)
        assertEquals(30, full.spectrumFps)
        assertTrue(effects(PerformanceMode.IMMERSIVE, TalaSpec.tokens.motion).transitionMs <= 400)
    }

    @Test
    fun `reduced motion removes every animation`() {
        presets.forEach { t ->
            val e = effects(PerformanceMode.IMMERSIVE, t.motion, motionEnabled = false, visualiser = VisualiserMode.FULL_SPECTRUM)
            assertEquals(0, e.pressMs)
            assertEquals(1f, e.pressScale)
            assertEquals(0, e.playPressMs)
            assertEquals(0, e.transitionMs)
            assertEquals(0, e.tabCrossfadeMs)
            assertEquals(0, e.lyricsScrollMs)
            assertEquals(0, e.swipeSnapMs)
            assertFalse(e.heartPop)
            assertFalse(e.artworkSpring)
            assertEquals(0, e.breathingFps)
            assertEquals(0, e.glowFps)
            assertEquals(0, e.spectrumFps)
        }
    }

    @Test
    fun `battery saver and heat act like efficiency`() {
        val saver = effects(PerformanceMode.IMMERSIVE, SpandaSpec.tokens.motion, visualiser = VisualiserMode.FULL_SPECTRUM, batterySaver = true)
        assertEquals(VisualiserMode.OFF, saver.visualiser)
        assertEquals(0, saver.spectrumFps)
        val hot = effects(PerformanceMode.IMMERSIVE, RagaSpec.tokens.motion, thermal = ThermalLevel.SEVERE)
        assertEquals(0, hot.breathingFps)
        assertFalse(hot.heartPop)
    }

    @Test
    fun `the audio-reactive layer is off by default and only ZENITH can turn it on`() {
        PresetId.entries.forEach { id ->
            assertEquals(VisualiserMode.OFF, ThemeResolver.resolve(ThemeConfig(id)).visualiser)
            val asked = ThemeResolver.resolve(ThemeConfig(id).with(ThemeField.VISUALISER, VisualiserMode.FULL_SPECTRUM.name)).visualiser
            assertEquals(if (id == PresetId.SPANDA) VisualiserMode.FULL_SPECTRUM else VisualiserMode.OFF, asked)
        }
        val byDefault = PresetEffects.resolve(
            SpandaSpec.tokens.motion,
            EffectsPolicy.limitsFor(PerformanceMode.IMMERSIVE),
            motionEnabled = true,
            visualiser = ThemeResolver.resolve(ThemeConfig(PresetId.SPANDA)).visualiser,
        )
        assertEquals(VisualiserMode.OFF, byDefault.visualiser)
        assertEquals(0, byDefault.glowFps)
    }

    @Test
    fun `v2_1 cached blur and ambient cross-fade by mode`() {
        presets.forEach { t ->
            val eff = effects(PerformanceMode.EFFICIENCY, t.motion)
            assertFalse("Efficiency shows a static scrim, no blur", eff.cachedBlur)
            assertEquals("Efficiency switches ambient colours instantly", 0, eff.ambientCrossfadeMs)
            listOf(PerformanceMode.BALANCED, PerformanceMode.IMMERSIVE).forEach { mode ->
                val e = effects(mode, t.motion)
                assertTrue(e.cachedBlur)
                assertEquals(600, e.ambientCrossfadeMs)
                assertEquals(150, e.iconMorphMs)
                assertEquals(200, e.checkmarkMs)
                assertEquals(200, e.sectionFadeMs)
            }
        }
    }

    @Test
    fun `v2_1 battery saver and heat turn blur and cross-fades off`() {
        presets.forEach { t ->
            listOf(
                effects(PerformanceMode.IMMERSIVE, t.motion, batterySaver = true),
                effects(PerformanceMode.IMMERSIVE, t.motion, thermal = ThermalLevel.SEVERE),
            ).forEach { e ->
                assertFalse(e.cachedBlur)
                assertFalse(e.liveBlur)
                assertEquals(0, e.ambientCrossfadeMs)
                assertEquals(0, e.iconMorphMs)
                assertEquals(0, e.checkmarkMs)
                assertEquals(0, e.sectionFadeMs)
            }
        }
    }

    @Test
    fun `v2_1 reduced motion removes every micro-animation`() {
        presets.forEach { t ->
            val e = effects(PerformanceMode.IMMERSIVE, t.motion, motionEnabled = false)
            assertEquals(0, e.ambientCrossfadeMs)
            assertEquals(0, e.iconMorphMs)
            assertEquals(0, e.checkmarkMs)
            assertEquals(0, e.sectionFadeMs)
        }
    }

    @Test
    fun `v2_1 live blur only on Tala in Immersive`() {
        assertTrue(effects(PerformanceMode.IMMERSIVE, TalaSpec.tokens.motion).liveBlur)
        assertFalse(effects(PerformanceMode.BALANCED, TalaSpec.tokens.motion).liveBlur)
        listOf(ShrutiSpec.tokens, RagaSpec.tokens, SpandaSpec.tokens).forEach { t ->
            assertFalse(effects(PerformanceMode.IMMERSIVE, t.motion).liveBlur)
        }
    }
}
