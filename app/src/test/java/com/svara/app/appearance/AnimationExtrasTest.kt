package com.svara.app.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationExtrasTest {
    private fun groups(limits: EffectLimits, reduced: Boolean = false) =
        MotionGroups.resolve(limits, MotionSpec.from(limits, AnimationStyle.SUBTLE, reduced, 1f, 100, false), Customization(), null)

    private val balanced get() = groups(EffectsPolicy.limitsFor(PerformanceMode.BALANCED))

    @Test
    fun `every extra is off in the four reduced conditions`() {
        val reduced = listOf(
            groups(EffectsPolicy.limitsFor(PerformanceMode.IMMERSIVE), reduced = true),
            groups(EffectsPolicy.limitsFor(PerformanceMode.EFFICIENCY)),
            groups(EffectsPolicy.resolve(PerformanceMode.IMMERSIVE, true, ThermalLevel.NONE)),
            groups(EffectsPolicy.resolve(PerformanceMode.IMMERSIVE, false, ThermalLevel.SEVERE)),
        )
        for (g in reduced) for (e in AnimationExtra.entries) for (p in PresetId.entries) {
            assertFalse("$e $p", ExtrasPolicy.allowed(e, p, g, Customization()))
        }
    }

    @Test
    fun `built extras run only in their themes and obey both switches`() {
        assertTrue(ExtrasPolicy.allowed(AnimationExtra.HEART_BURST, PresetId.SHRUTI, balanced, Customization()))
        assertFalse(ExtrasPolicy.allowed(AnimationExtra.HEART_BURST, PresetId.TALA, balanced, Customization()))
        assertFalse(ExtrasPolicy.allowed(AnimationExtra.HEART_BURST, PresetId.SHRUTI, balanced, Customization(animationExtras = false)))
        assertFalse(ExtrasPolicy.allowed(AnimationExtra.HEART_BURST, PresetId.SHRUTI, balanced, Customization(extrasOff = setOf(AnimationExtra.HEART_BURST))))
        assertTrue(ExtrasPolicy.allowed(AnimationExtra.NOTE_DRIFT, PresetId.RAGA, balanced, Customization()))
    }

    @Test
    fun `unbuilt extras never run and vinyl starts off`() {
        AnimationExtra.entries.filter { !it.built }.forEach { e ->
            PresetId.entries.forEach { p -> assertFalse(ExtrasPolicy.allowed(e, p, balanced, Customization(extrasOff = emptySet()))) }
        }
        assertTrue(AnimationExtra.VINYL in Customization().extrasOff)
    }

    @Test
    fun `extras settings are in the motion reset and decode safely`() {
        assertEquals(StudioSection.MOTION, CustomField.EXTRAS.section)
        assertEquals(StudioSection.MOTION, CustomField.EXTRAS_OFF.section)
        val c = CustomizationCodec.decode(mapOf(CustomField.EXTRAS_OFF to "HEART_BURST,NOPE"))
        assertEquals(setOf(AnimationExtra.HEART_BURST), c.extrasOff)
        assertEquals(25, AnimationExtra.entries.size)
    }
}
