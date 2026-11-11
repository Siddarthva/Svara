package com.svara.app.appearance

import com.svara.app.appearance.colour.SwatchGroup
import com.svara.app.appearance.colour.Swatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwatchesTest {

    @Test
    fun `exactly 64 swatches in eight groups of eight, in the fixed order`() {
        assertEquals(64, Swatches.all.size)
        assertEquals(SwatchGroup.entries, Swatches.grouped().keys.toList())
        Swatches.grouped().values.forEach { assertEquals(8, it.size) }
        assertEquals(listOf("White", "Pearl", "Silver", "Ash", "Slate", "Graphite", "Charcoal", "Ink"), Swatches.grouped()[SwatchGroup.NEUTRALS]!!.map { it.name })
        assertEquals("Raspberry", Swatches.all.last().name)
        assertEquals(56, Swatches.chromatic.size)
    }

    @Test
    fun `ids and names are unique and every colour is opaque`() {
        assertEquals(64, Swatches.all.map { it.id }.toSet().size)
        assertEquals(64, Swatches.all.map { it.name }.toSet().size)
        Swatches.all.forEach { assertEquals(it.name, 0xFF, it.colour.alpha) }
    }

    @Test
    fun `hex values match the specification`() {
        val spot = mapOf(
            "white" to 0xFFFFFF, "ink" to 0x1F2226, "crimson" to 0xDC143C, "burnt_orange" to 0xCC5500,
            "champagne" to 0xF7E7A1, "teal_green" to 0x00827F, "midnight" to 0x191970, "lilac" to 0xC8A2C8,
            "coral_pink" to 0xF88379, "raspberry" to 0xE30B5C,
        )
        spot.forEach { (id, rgb) -> assertEquals(id, rgb.toLong(), Swatches.byId(id)!!.colour.value and 0xFFFFFF) }
        assertTrue(Swatches.byId("nope") == null)
    }
}
