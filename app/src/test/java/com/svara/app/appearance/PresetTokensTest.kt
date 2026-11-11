package com.svara.app.appearance

import com.svara.app.appearance.tokens.AnahataSpec
import com.svara.app.appearance.tokens.PaletteTokens
import com.svara.app.appearance.tokens.RagaSpec
import com.svara.app.appearance.tokens.ShrutiSpec
import com.svara.app.appearance.tokens.SpandaSpec
import com.svara.app.appearance.tokens.TabStyle
import com.svara.app.appearance.tokens.TalaSpec
import com.svara.app.appearance.tokens.ThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetTokensTest {

    @Test
    fun `each preset resolves to its own spec object`() {
        assertSame(AnahataSpec.tokens, Presets.spec(PresetId.ANAHATA).tokens)
        assertSame(ShrutiSpec.tokens, Presets.spec(PresetId.SHRUTI).tokens)
        assertSame(TalaSpec.tokens, Presets.spec(PresetId.TALA).tokens)
        assertSame(RagaSpec.tokens, Presets.spec(PresetId.RAGA).tokens)
        assertSame(SpandaSpec.tokens, Presets.spec(PresetId.SPANDA).tokens)
        PresetId.entries.forEach { id -> assertSame(Presets.spec(id).tokens, ThemeResolver.resolve(ThemeConfig(id)).spec.tokens) }
    }

    @Test
    fun `source values land in the specs`() {
        // Spot checks against docs/design-source-map.md.
        assertEquals(Argb(0xFF1DB954), ShrutiSpec.palette.dark.accent)
        assertEquals(Argb(0xFF121212), ShrutiSpec.palette.dark.background)
        assertEquals(24f, ShrutiSpec.typeScale.display.sizeSp)
        assertEquals(Argb(0xFFFA243C), TalaSpec.palette.dark.accent)
        assertEquals(34f, TalaSpec.typeScale.display.sizeSp)
        assertEquals(28f, TalaSpec.tokens.shape.sheetTopRadius)
        assertEquals(Argb(0xFFE52D27), RagaSpec.palette.dark.accent)
        assertEquals(Argb(0xFF1E1E1E), RagaSpec.palette.dark.surface)
        assertEquals(Argb(0xFF00A8E1), SpandaSpec.palette.dark.accent)
        assertEquals(Argb(0xFF191922), SpandaSpec.palette.dark.surface)
        assertEquals(14.5f, SpandaSpec.typeScale.bodyEmphasis.sizeSp)
        assertEquals(6f, SpandaSpec.tokens.shape.cardRadius)
    }

    @Test
    fun `rows are at least 48 dp and tabs never empty for layout presets`() {
        PresetId.entries.forEach { id ->
            val t = Presets.tokens(id)
            assertTrue("$id row", t.spacing.rowHeight >= ThemeTokens.MIN_TOUCH)
            if (id != PresetId.ANAHATA) {
                assertEquals("$id tabs", 3, t.navigation.visibleTabs.size)
                assertEquals(NavTab.HOME, t.navigation.visibleTabs.first())
                assertTrue(t.navigation.tabStyle == TabStyle.OUTLINED_TO_FILLED)
            }
        }
        assertEquals("Your Library", ShrutiSpec.tokens.navigation.label(NavTab.LIBRARY))
        assertEquals("My Music", RagaSpec.tokens.navigation.label(NavTab.LIBRARY))
        assertEquals("Find", SpandaSpec.tokens.navigation.label(NavTab.SEARCH))
    }

    /** Text pairs need 4.5:1; icons and indicators need 3:1 (WCAG 1.4.11). */
    private fun check(name: String, p: PaletteTokens) {
        fun text(fg: Argb, bg: Argb, what: String) {
            val ratio = Contrast.ratio(Contrast.composite(fg, bg), bg)
            assertTrue("$name $what: %.2f".format(ratio), ratio >= Contrast.AA_TEXT)
        }
        fun icon(fg: Argb, bg: Argb, what: String) {
            val ratio = Contrast.ratio(Contrast.composite(fg, bg), bg)
            assertTrue("$name $what: %.2f".format(ratio), ratio >= Contrast.AA_NON_TEXT)
        }
        listOf(p.background to "background", p.surface to "surface", p.surfaceHighlight to "surfaceHighlight").forEach { (bg, bgName) ->
            text(p.primaryText, bg, "primaryText on $bgName")
            text(p.secondaryText, bg, "secondaryText on $bgName")
        }
        text(p.accentText, p.background, "accentText on background")
        text(p.accentText, p.surface, "accentText on surface")
        text(p.onAccent, p.accentFill, "onAccent text on accentFill")
        icon(p.onAccent, p.accent, "onAccent icon on accent")
        icon(p.accent, p.background, "accent indicator on background")
        icon(p.accent, p.surface, "accent indicator on surface")
    }

    @Test
    fun `every text and icon token pair passes WCAG AA in dark and light`() {
        mapOf(
            "ETHEREAL" to AnahataSpec.palette, "Halo" to ShrutiSpec.palette, "SOLACE" to TalaSpec.palette,
            "OCTAVE" to RagaSpec.palette, "ZENITH" to SpandaSpec.palette,
        ).forEach { (name, pair) ->
            check("$name dark", pair.dark)
            check("$name light", pair.light)
        }
    }

    @Test
    fun `translucent secondary text is checked as composited`() {
        // SOLACE light: #3C3C43 at 76 % over white.
        val c = Contrast.composite(TalaSpec.palette.light.secondaryText, Argb(0xFFFFFFFF))
        assertTrue(Contrast.ratio(c, Argb(0xFFFFFFFF)) >= Contrast.AA_TEXT)
        assertEquals(0xFF, c.alpha)
    }
}
