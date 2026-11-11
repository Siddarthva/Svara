package com.svara.app.ui

import com.svara.app.ui.motion.DisplayModeSpec
import com.svara.app.ui.motion.DisplayRate
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayRateTest {
    private val m60 = DisplayModeSpec(1, 1080, 2400, 60f)
    private val m90 = DisplayModeSpec(2, 1080, 2400, 90f)
    private val m120 = DisplayModeSpec(3, 1080, 2400, 120f)
    private val m120Big = DisplayModeSpec(4, 1440, 3200, 120f)
    private val m144Big = DisplayModeSpec(5, 1440, 3200, 144f)

    @Test
    fun `asks for the fastest mode at the current resolution`() {
        assertEquals(3, DisplayRate.pick(listOf(m60, m90, m120, m144Big), m60, enabled = true))
    }

    @Test
    fun `never asks for a mode that changes the resolution`() {
        assertEquals(3, DisplayRate.pick(listOf(m60, m120, m120Big, m144Big), m60, enabled = true))
        assertEquals(5, DisplayRate.pick(listOf(m60, m120, m120Big, m144Big), m120Big, enabled = true))
    }

    @Test
    fun `keeps asking once the fast mode is current`() {
        assertEquals(3, DisplayRate.pick(listOf(m60, m120), m120, enabled = true))
    }

    @Test
    fun `off lets the system choose`() {
        assertEquals(DisplayRate.NO_PREFERENCE, DisplayRate.pick(listOf(m60, m120), m60, enabled = false))
        assertEquals(0f, DisplayRate.pickRate(listOf(m60, m120), m60, enabled = false))
    }

    @Test
    fun `older versions get the highest rate at the current resolution`() {
        assertEquals(90f, DisplayRate.pickRate(listOf(m60, m90, m144Big), m60, enabled = true))
    }

    @Test
    fun `smooth display is on by default and in the performance section`() {
        assertEquals(true, com.svara.app.appearance.Customization().smoothDisplay)
        assertEquals(com.svara.app.appearance.StudioSection.PERFORMANCE, com.svara.app.appearance.CustomField.SMOOTH_DISPLAY.section)
        assertEquals("true", com.svara.app.data.appearance.CustomizationRepository.defaultValue(com.svara.app.appearance.CustomField.SMOOTH_DISPLAY))
    }

    @Test
    fun `saved looks keep smooth display off`() {
        val codec = com.svara.app.data.appearance.CustomOverridesCodec
        val field = com.svara.app.appearance.CustomField.SMOOTH_DISPLAY
        val look = codec.decode(codec.encode(mapOf(field to "false")))
        assertEquals("false", look[field])
        assertEquals(false, com.svara.app.appearance.CustomizationCodec.decode(look).smoothDisplay)
    }
}
