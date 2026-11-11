package com.svara.app.appearance

import com.svara.app.appearance.colour.ContrastEngine
import com.svara.app.appearance.colour.ContrastTargets
import com.svara.app.appearance.colour.EffectivePalette
import com.svara.app.appearance.colour.Swatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastEngineTest {

    private fun ratio(fg: Argb, bg: Argb) = Contrast.ratio(Contrast.composite(fg, bg), bg)

    /** Hue may drift a little where chroma had to shrink to fit sRGB; never by much. */
    private val hueTolerance = 12.0

    @Test
    fun `every swatch on every preset background meets text and non-text targets`() {
        PresetId.entries.forEach { id ->
            val pair = Presets.tokens(id).palette
            listOf(pair.dark, pair.light).forEach { p ->
                listOf(ContrastTargets.NORMAL, ContrastTargets.HIGH).forEach { targets ->
                    Swatches.all.forEach { s ->
                        val set = ContrastEngine.accentSet(s.colour, p.background, p.surface, targets)
                        val where = "$id ${s.name} on ${ContrastEngine.hex(p.background)} (${targets.text})"
                        assertTrue("$where text", ratio(set.accentText.colour, p.background) >= targets.text)
                        assertTrue("$where text/surface", ratio(set.accentText.colour, p.surface) >= targets.text)
                        assertTrue("$where non-text", ratio(set.accent.colour, p.background) >= targets.nonText)
                        assertTrue("$where label on fill", ratio(set.onAccent, set.accentFill.colour) >= targets.text)
                    }
                }
            }
        }
    }

    @Test
    fun `hue is kept while lightness changes`() {
        val bg = Presets.tokens(PresetId.TALA).palette.dark.background
        Swatches.chromatic.forEach { s ->
            val fitted = ContrastEngine.fit(s.colour, listOf(bg), ContrastTargets.HIGH.text)
            val chroma = ContrastEngine.toOklch(fitted.colour).c
            // Near-white results have almost no chroma, and then hue is meaningless.
            if (chroma > 0.03) assertTrue("${s.name} hue moved", ContrastEngine.hueDistance(s.colour, fitted.colour) <= hueTolerance)
        }
    }

    @Test
    fun `the adjusted flag is set only when a colour changed`() {
        val black = Argb(0xFF000000)
        val white = Argb(0xFFFFFFFF)
        val readable = ContrastEngine.fit(Argb(0xFFFFD700), listOf(black), Contrast.AA_TEXT)
        assertFalse(readable.adjusted)
        assertEquals(Argb(0xFFFFD700), readable.colour)
        val ink = ContrastEngine.fit(Swatches.byId("ink")!!.colour, listOf(black), Contrast.AA_TEXT)
        assertTrue(ink.adjusted)
        assertTrue(ratio(ink.colour, black) >= Contrast.AA_TEXT)
        val lemon = ContrastEngine.fit(Swatches.byId("lemon")!!.colour, listOf(white), Contrast.AA_TEXT)
        assertTrue(lemon.adjusted)
        assertTrue(ratio(lemon.colour, white) >= Contrast.AA_TEXT)
    }

    @Test
    fun `colour round trip through OKLCH is stable`() {
        Swatches.all.forEach { s ->
            val back = ContrastEngine.fromOklch(ContrastEngine.toOklch(s.colour))
            assertTrue(s.name, kotlin.math.abs(back.r - s.colour.r) <= 1 && kotlin.math.abs(back.g - s.colour.g) <= 1 && kotlin.math.abs(back.b - s.colour.b) <= 1)
        }
    }

    // ---- High contrast ---------------------------------------------------------------------

    private fun theme(id: PresetId, vararg overrides: Pair<ThemeField, String>) =
        ThemeResolver.resolve(ThemeConfig(id, overrides.toMap()))

    @Test
    fun `high contrast keeps each preset's own hue and never one shared accent`() {
        listOf(true, false).forEach { dark ->
            val accents = PresetId.entries.associateWith { id ->
                val t = theme(id)
                val normal = EffectivePalette.build(t.spec.tokens, t, dark, highContrast = false, albumAccent = null).palette
                val high = EffectivePalette.build(t.spec.tokens, t, dark, highContrast = true, albumAccent = null)
                assertTrue("$id hue", ContrastEngine.hueDistance(normal.accent, high.palette.accent) <= hueTolerance)
                assertTrue("$id 7:1 text", ratio(high.palette.accentText, high.palette.background) >= ContrastTargets.HIGH.text)
                assertTrue("$id 7:1 secondary", ratio(high.palette.secondaryText, high.palette.background) >= ContrastTargets.HIGH.text)
                high.palette.accent
            }
            // Five presets, five different accents (no shared yellow).
            assertEquals(accents.values.toSet().size, accents.size)
        }
    }

    @Test
    fun `high contrast keeps a custom accent's hue`() {
        val t = theme(PresetId.SHRUTI, ThemeField.COLOUR_SYSTEM to ColourSystem.CUSTOM.name, ThemeField.CUSTOM_SWATCH to "sapphire")
        val high = EffectivePalette.build(t.spec.tokens, t, dark = true, highContrast = true, albumAccent = null).palette
        assertTrue(ContrastEngine.hueDistance(Swatches.byId("sapphire")!!.colour, high.accent) <= hueTolerance)
        assertNotEquals(Argb(0xFFFFE066), high.accent)
    }

    @Test
    fun `default preset colours pass through untouched`() {
        PresetId.entries.forEach { id ->
            val t = theme(id)
            listOf(true, false).forEach { dark ->
                if (t.colourSystem == ColourSystem.PRESET) {
                    assertEquals(t.spec.tokens.palette.pick(dark), EffectivePalette.build(t.spec.tokens, t, dark, false, null).palette)
                }
            }
        }
    }

    @Test
    fun `tones and monochrome keep text readable`() {
        PresetId.entries.forEach { id ->
            BackgroundTone.entries.forEach { tone ->
                listOf(ColourSystem.PRESET, ColourSystem.MONOCHROME, ColourSystem.CUSTOM).forEach { source ->
                    val t = theme(id, ThemeField.BACKGROUND_TONE to tone.name, ThemeField.COLOUR_SYSTEM to source.name, ThemeField.CUSTOM_SWATCH to "lemon")
                    listOf(true, false).forEach { dark ->
                        val p = EffectivePalette.build(t.spec.tokens, t, dark, false, null).palette
                        listOf(p.background, p.surface, p.surfaceHighlight).forEach { bg ->
                            assertTrue("$id $tone $source primary", ratio(p.primaryText, bg) >= Contrast.AA_TEXT)
                            assertTrue("$id $tone $source secondary", ratio(p.secondaryText, bg) >= Contrast.AA_TEXT)
                        }
                        assertTrue("$id $tone $source accentText", ratio(p.accentText, p.background) >= Contrast.AA_TEXT)
                        if (dark && tone == BackgroundTone.PURE_BLACK) assertEquals(Argb(0xFF000000), p.background)
                    }
                }
            }
        }
    }

    @Test
    fun `the adjusted note reports only colours the user chose`() {
        val dark = theme(PresetId.ANAHATA, ThemeField.COLOUR_SYSTEM to ColourSystem.CUSTOM.name, ThemeField.CUSTOM_SWATCH to "ink")
        val r = EffectivePalette.build(dark.spec.tokens, dark, true, false, null)
        assertTrue(r.adjustment.any)
        assertTrue(r.adjustment.text)
        val fine = theme(PresetId.ANAHATA, ThemeField.COLOUR_SYSTEM to ColourSystem.CUSTOM.name, ThemeField.CUSTOM_SWATCH to "gold")
        assertFalse(EffectivePalette.build(fine.spec.tokens, fine, true, false, null).adjustment.text)
        val preset = theme(PresetId.TALA)
        assertFalse(EffectivePalette.build(preset.spec.tokens, preset, true, true, null).adjustment.any)
    }
}
