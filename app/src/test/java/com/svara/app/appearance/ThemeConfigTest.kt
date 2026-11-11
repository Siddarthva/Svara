package com.svara.app.appearance

import com.svara.app.data.appearance.ThemeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeConfigTest {

    @Test
    fun `preset names, descriptions and tiers match the spec`() {
        assertEquals(listOf("ETHEREAL", "Halo", "SOLACE", "OCTAVE", "ZENITH"), PresetId.entries.map { it.displayName })
        assertEquals("Premium, balanced, timeless", PresetId.ANAHATA.personality)
        assertEquals("The unstruck sound", PresetId.ANAHATA.description)
        assertEquals("Listening as an art", PresetId.SHRUTI.description)
        assertEquals("Rhythm in motion", PresetId.TALA.description)
        assertEquals("Music with colour and feeling", PresetId.RAGA.description)
        assertEquals("Sound in motion", PresetId.SPANDA.description)
        assertEquals(PresetId.ANAHATA, PresetId.DEFAULT)
        PresetId.entries.forEach { assertEquals(it.defaultTier, Presets.spec(it).defaultTier) }
    }

    @Test
    fun `no overrides resolves to the preset defaults`() {
        PresetId.entries.forEach { id ->
            val t = ThemeResolver.resolve(ThemeConfig(id))
            val spec = Presets.spec(id)
            assertEquals(spec.homeLayout, t.homeLayout)
            assertEquals(spec.colourSystem, t.colourSystem)
            assertEquals(spec.animationStyle, t.animationStyle)
            assertEquals(spec.artworkSize, t.artworkSize)
            assertEquals(spec.glass, t.glass)
            assertEquals(spec.glow, t.glow)
        }
    }

    @Test
    fun `overrides win over the preset and only the overridden field changes`() {
        val config = ThemeConfig(PresetId.SHRUTI).with(ThemeField.GLOW, "true").with(ThemeField.HOME_LAYOUT, HomeLayout.PLAYLIST_CARDS.name)
        val t = ThemeResolver.resolve(config)
        assertTrue(t.glow)
        assertEquals(HomeLayout.PLAYLIST_CARDS, t.homeLayout)
        assertEquals(Presets.spec(PresetId.SHRUTI).colourSystem, t.colourSystem)
        assertEquals(Presets.spec(PresetId.SHRUTI).artworkSize, t.artworkSize)
    }

    @Test
    fun `the same overrides apply on top of a different preset`() {
        val config = ThemeConfig(PresetId.ANAHATA).with(ThemeField.ARTWORK_SIZE, ArtworkSize.SMALL.name)
        val moved = ThemeResolver.resolve(config.copy(preset = PresetId.RAGA))
        assertEquals(ArtworkSize.SMALL, moved.artworkSize)
        assertEquals(Presets.spec(PresetId.RAGA).colourSystem, moved.colourSystem)
    }

    @Test
    fun `reset removes the override instead of storing the default`() {
        val config = ThemeConfig(PresetId.TALA).with(ThemeField.GLASS, "true")
        val reset = config.without(ThemeField.GLASS)
        assertTrue(reset.overrides.isEmpty())
        assertFalse(ThemeResolver.resolve(reset).glass)
    }

    @Test
    fun `reset section removes only that section's overrides`() {
        val config = ThemeConfig(PresetId.ANAHATA)
            .with(ThemeField.GLASS, "true")
            .with(ThemeField.GLOW, "true")
            .with(ThemeField.ARTWORK_SIZE, ArtworkSize.SMALL.name)
            .with(ThemeField.COLOUR_SYSTEM, ColourSystem.WARM.name)
            .with(ThemeField.ANIMATION_STYLE, AnimationStyle.PLAYFUL.name)
        val reset = config.resetSection(ThemeSection.ARTWORK)
        assertEquals(setOf(ThemeField.COLOUR_SYSTEM, ThemeField.ANIMATION_STYLE), reset.overrides.keys)
        assertEquals(PresetId.ANAHATA, reset.preset)
    }

    @Test
    fun `reset everything keeps the preset and clears every override`() {
        val config = ThemeConfig(PresetId.RAGA).with(ThemeField.GLASS, "true").with(ThemeField.VISUALISER, "FULL_SPECTRUM")
        val reset = config.resetAll()
        assertEquals(PresetId.RAGA, reset.preset)
        assertTrue(reset.overrides.isEmpty())
    }

    @Test
    fun `pruning stores only real changes, never copies of the preset`() {
        val spec = Presets.spec(PresetId.SPANDA)
        val config = ThemeConfig(PresetId.SPANDA)
            .with(ThemeField.GLASS, spec.glass.toString()) // equals default
            .with(ThemeField.ARTWORK_SIZE, spec.artworkSize.name) // equals default
            .with(ThemeField.HOME_LAYOUT, HomeLayout.COMPACT_LIBRARY.name) // real change
        assertEquals(setOf(ThemeField.HOME_LAYOUT), ThemeRepository.prune(config).overrides.keys)
    }

    @Test
    fun `corrupt or unknown values fall back to the preset default`() {
        val raw = "HOME_LAYOUT=SIDEWAYS;GLASS=maybe;CUSTOM_ACCENT=99;NOT_A_FIELD=1;=x;ARTWORK_SIZE"
        val config = ThemeConfig(PresetId.ANAHATA, ThemeConfigCodec.decodeOverrides(raw))
        val t = ThemeResolver.resolve(config)
        val spec = Presets.spec(PresetId.ANAHATA)
        assertEquals(spec.homeLayout, t.homeLayout)
        assertEquals(spec.glass, t.glass)
        assertEquals(0, t.customAccent)
        assertEquals(spec.artworkSize, t.artworkSize)
        assertEquals(PresetId.ANAHATA, PresetId.parse("NOPE"))
        assertEquals(PresetId.ANAHATA, PresetId.parse(null))
    }

    @Test
    fun `codec round-trips overrides`() {
        val overrides = mapOf(ThemeField.GLOW to "true", ThemeField.CUSTOM_ACCENT to "3", ThemeField.HOME_LAYOUT to "RECENT_FIRST")
        assertEquals(overrides, ThemeConfigCodec.decodeOverrides(ThemeConfigCodec.encodeOverrides(overrides)))
        assertTrue(ThemeConfigCodec.decodeOverrides(null).isEmpty())
        assertTrue(ThemeConfigCodec.decodeOverrides("").isEmpty())
    }

    @Test
    fun `the visualiser is a ZENITH feature`() {
        val config = ThemeConfig(PresetId.ANAHATA).with(ThemeField.VISUALISER, VisualiserMode.FULL_SPECTRUM.name)
        assertEquals(VisualiserMode.OFF, ThemeResolver.resolve(config).visualiser)
        assertEquals(VisualiserMode.FULL_SPECTRUM, ThemeResolver.resolve(config.copy(preset = PresetId.SPANDA)).visualiser)
    }

    @Test
    fun `summary reads like the spec example`() {
        val config = ThemeConfig(PresetId.ANAHATA)
            .with(ThemeField.ANIMATION_STYLE, AnimationStyle.CINEMATIC.name)
            .with(ThemeField.GLASS, "true")
        assertEquals(
            "Preset: ETHEREAL. Layout: Artwork first. Palette: Album artwork. Motion: Cinematic. Artwork: Large. Glass: On. Glow: Off.",
            ThemeResolver.summary(ThemeResolver.resolve(config)),
        )
    }
}
