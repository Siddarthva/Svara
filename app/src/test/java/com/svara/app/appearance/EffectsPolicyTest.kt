package com.svara.app.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsPolicyTest {

    @Test
    fun `efficiency row`() {
        val l = EffectsPolicy.limitsFor(PerformanceMode.EFFICIENCY)
        assertEquals(setOf(BackgroundKind.STATIC), l.backgrounds)
        assertEquals(0, l.playerBlurDp)
        assertFalse(l.blurInLists)
        assertTrue(l.fadesOnly)
        assertEquals(100, l.transitionMinMs)
        assertEquals(100, l.transitionMaxMs)
        assertEquals(0, l.ambientFps)
        assertEquals(0, l.visualiserFps)
        assertFalse(l.glowAllowed)
        assertFalse(l.glassAllowed)
        assertFalse(l.microAnimations)
        assertFalse(l.liveBlurAllowed)
    }

    @Test
    fun `balanced row`() {
        val l = EffectsPolicy.limitsFor(PerformanceMode.BALANCED)
        assertEquals(setOf(BackgroundKind.STATIC, BackgroundKind.CACHED_PALETTE), l.backgrounds)
        assertFalse(l.blurInLists)
        // Static backgrounds: no live blur, nothing ambient.
        assertEquals(0, l.playerBlurDp)
        assertFalse(l.liveBlurAllowed)
        assertEquals(150, l.transitionMinMs)
        assertEquals(250, l.transitionMaxMs)
        assertEquals(0, l.ambientFps)
        assertEquals(0, l.visualiserFps)
        assertTrue(l.microAnimations)
        assertTrue(l.glowAllowed && l.glassAllowed)
    }

    @Test
    fun `immersive row`() {
        val l = EffectsPolicy.limitsFor(PerformanceMode.IMMERSIVE)
        assertTrue(BackgroundKind.AMBIENT in l.backgrounds)
        assertFalse(l.blurInLists)
        // Live blur only where a preset asks for it (SOLACE's mini-player); never a live player blur.
        assertEquals(0, l.playerBlurDp)
        assertTrue(l.liveBlurAllowed)
        assertEquals(400, l.transitionMaxMs)
        assertEquals(10, l.ambientFps)
        assertEquals(30, l.visualiserFps)
    }

    @Test
    fun `lists are never blurred in any mode or condition`() {
        PerformanceMode.entries.forEach { mode ->
            listOf(true, false).forEach { saver ->
                ThermalLevel.entries.forEach { thermal -> assertFalse(EffectsPolicy.resolve(mode, saver, thermal).blurInLists) }
            }
        }
    }

    @Test
    fun `battery saver forces efficiency with a notice and keeps the user's choice`() {
        PerformanceMode.entries.forEach { mode ->
            val l = EffectsPolicy.resolve(mode, batterySaver = true, thermal = ThermalLevel.NONE)
            assertEquals(PerformanceMode.EFFICIENCY, l.mode)
            assertEquals(mode, l.selectedMode)
            assertEquals("Battery saver is on. Visual effects are reduced.", l.notice)
        }
    }

    @Test
    fun `each thermal level`() {
        ThermalLevel.entries.forEach { thermal ->
            val l = EffectsPolicy.resolve(PerformanceMode.IMMERSIVE, batterySaver = false, thermal = thermal)
            if (thermal >= ThermalLevel.SEVERE) {
                assertEquals("$thermal", PerformanceMode.EFFICIENCY, l.mode)
                assertEquals(PolicyOverride.THERMAL, l.override)
                assertTrue(l.notice!!.isNotBlank() && !l.notice!!.contains('\n'))
            } else {
                assertEquals("$thermal", PerformanceMode.IMMERSIVE, l.mode)
                assertNull(l.notice)
            }
        }
        assertEquals(ThermalLevel.SEVERE, ThermalLevel.fromStatus(3))
        assertEquals(ThermalLevel.NONE, ThermalLevel.fromStatus(42))
    }

    @Test
    fun `selected mode returns when the condition clears`() {
        val hot = EffectsPolicy.resolve(PerformanceMode.BALANCED, batterySaver = false, thermal = ThermalLevel.CRITICAL)
        assertEquals(PerformanceMode.EFFICIENCY, hot.mode)
        val cool = EffectsPolicy.resolve(PerformanceMode.BALANCED, batterySaver = false, thermal = ThermalLevel.LIGHT)
        assertEquals(PerformanceMode.BALANCED, cool.mode)
        assertEquals(EffectsPolicy.limitsFor(PerformanceMode.BALANCED), cool)
    }

    @Test
    fun `battery saver wins over heat for the notice`() {
        val l = EffectsPolicy.resolve(PerformanceMode.IMMERSIVE, batterySaver = true, thermal = ThermalLevel.EMERGENCY)
        assertEquals(PolicyOverride.BATTERY_SAVER, l.override)
    }

    @Test
    fun `motion stays inside the policy's duration limits`() {
        PerformanceMode.entries.forEach { mode ->
            val limits = EffectsPolicy.limitsFor(mode)
            AnimationStyle.entries.filter { it != AnimationStyle.REDUCED }.forEach { style ->
                Customization.SPEEDS.forEach { speed ->
                    val m = MotionSpec.from(limits, style, reducedMotion = false, speed = speed, intensityPercent = 100, presetFadesOnly = false)
                    assertTrue(m.screen in limits.transitionMinMs..limits.transitionMaxMs)
                    assertTrue(m.feedback in limits.transitionMinMs..limits.transitionMaxMs)
                    assertTrue(m.screen <= 400)
                }
            }
        }
    }

    @Test
    fun `reduced motion, the reduced style and zero intensity turn animation off`() {
        val limits = EffectsPolicy.limitsFor(PerformanceMode.IMMERSIVE)
        assertFalse(MotionSpec.from(limits, AnimationStyle.CINEMATIC, true, 1f, 100, false).enabled)
        assertFalse(MotionSpec.from(limits, AnimationStyle.REDUCED, false, 1f, 100, false).enabled)
        assertFalse(MotionSpec.from(limits, AnimationStyle.PLAYFUL, false, 1f, 0, false).enabled)
        assertEquals(0, MotionSpec.NONE.screen)
        assertEquals(0, MotionSpec.NONE.feedback)
    }

    @Test
    fun `efficiency and fades-only presets fade`() {
        val efficiency = MotionSpec.from(EffectsPolicy.limitsFor(PerformanceMode.EFFICIENCY), AnimationStyle.PLAYFUL, false, 1f, 100, false)
        assertTrue(efficiency.fadesOnly)
        assertFalse(efficiency.playful)
        assertEquals(100, efficiency.screen)
        val shruti = MotionSpec.from(EffectsPolicy.limitsFor(PerformanceMode.BALANCED), AnimationStyle.SUBTLE, false, 1f, 100, presetFadesOnly = true)
        assertTrue(shruti.fadesOnly)
    }

    @Test
    fun `faster speed shortens transitions`() {
        val limits = EffectsPolicy.limitsFor(PerformanceMode.IMMERSIVE)
        val slow = MotionSpec.from(limits, AnimationStyle.CINEMATIC, false, 0.5f, 100, false)
        val fast = MotionSpec.from(limits, AnimationStyle.CINEMATIC, false, 2f, 100, false)
        assertTrue(fast.screen < slow.screen)
    }

    @Test
    fun `every preset works with every mode`() {
        PresetId.entries.forEach { id ->
            PerformanceMode.entries.forEach { mode ->
                val limits = EffectsPolicy.resolve(mode, false, ThermalLevel.NONE)
                val theme = ThemeResolver.resolve(ThemeConfig(id))
                val motion = MotionSpec.from(limits, theme.animationStyle, false, 1f, 100, theme.spec.fadesOnly)
                assertEquals(mode, limits.mode)
                assertTrue(motion.screen <= limits.transitionMaxMs)
            }
        }
    }
}
