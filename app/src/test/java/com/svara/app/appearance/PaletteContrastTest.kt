package com.svara.app.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteContrastTest {

    private val presets = PresetId.entries.map { Presets.spec(it) }

    @Test
    fun `contrast ratio matches WCAG reference values`() {
        assertEquals(21.0, Contrast.ratio(Argb(0xFF000000), Argb(0xFFFFFFFF)), 0.01)
        assertEquals(1.0, Contrast.ratio(Argb(0xFF777777), Argb(0xFF777777)), 0.001)
        assertEquals(4.48, Contrast.ratio(Argb(0xFF777777), Argb(0xFFFFFFFF)), 0.01)
    }

    @Test
    fun `every preset's default palette passes WCAG AA in dark and light`() {
        presets.forEach { spec ->
            listOf(spec.palette.dark, spec.palette.light).forEach { t ->
                val name = spec.id.displayName
                assertTrue("$name onSurface", Contrast.passesAA(t.onSurface, t.surface))
                assertTrue("$name onSurface/background", Contrast.passesAA(t.onSurface, t.background))
                assertTrue("$name onSurfaceVariant", Contrast.passesAA(t.onSurfaceVariant, t.surface))
                assertTrue("$name onSurface on surfaceVariant", Contrast.passesAA(t.onSurface, t.surfaceVariant))
                assertTrue("$name accent", Contrast.passesAA(t.accent, t.background))
                assertTrue("$name onAccent", Contrast.passesAA(t.onAccent, t.accent))
            }
        }
    }

    @Test
    fun `every custom accent offered passes AA on the background`() {
        presets.forEach { spec ->
            listOf(spec.palette.dark, spec.palette.light).forEach { t ->
                val offered = Palettes.availableAccents(t.background)
                offered.forEach { (_, accent) -> assertTrue("${spec.id} ${accent.name}", Contrast.passesAA(accent.colour, t.background)) }
            }
            // On the dark default all twelve are offered.
            assertEquals(12, Palettes.availableAccents(spec.palette.dark.background).size)
        }
    }

    @Test
    fun `resolved accents always pass AA, falling back to the preset default`() {
        val unreadable = listOf(Argb(0xFF000000), Argb(0xFF101010), Argb(0xFFFFFFFF), Argb(0xFF3030FF), null)
        presets.forEach { spec ->
            listOf(true, false).forEach { dark ->
                ColourSystem.entries.forEach { system ->
                    Palettes.ACCENTS.indices.forEach { custom ->
                        unreadable.forEach { album ->
                            val t = Palettes.tokens(spec.palette, dark, system, custom, album)
                            assertTrue("${spec.id} $system $dark", Contrast.passesAA(t.accent, t.background))
                            assertTrue("${spec.id} $system $dark onAccent", Contrast.passesAA(t.onAccent, t.accent))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `album colours are lightened on dark backgrounds until readable`() {
        val bg = Palettes.ANAHATA.dark.background
        val adjusted = Palettes.adjustForBackground(Argb(0xFF402060), bg, dark = true)
        assertTrue(Contrast.passesAA(adjusted, bg))
    }

    @Test
    fun `tile colours are stable per name`() {
        assertEquals(Palettes.tileFor(Palettes.RAGA, "Kind of Blue"), Palettes.tileFor(Palettes.RAGA, "Kind of Blue"))
    }
}
