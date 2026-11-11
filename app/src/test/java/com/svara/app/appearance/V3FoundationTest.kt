package com.svara.app.appearance

import com.svara.app.appearance.tokens.CinemaBackdrop

import com.svara.app.appearance.tokens.SignatureTokens
import com.svara.app.ui.artwork.ArtRef
import com.svara.app.ui.artwork.ArtworkFormat
import com.svara.app.ui.artwork.Composition
import com.svara.app.ui.components.formatRemaining
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1 foundation: Now Playing style settings, signature touches, artwork formats and compositions. */
class V3FoundationTest {

    @Test
    fun `now playing style and aura default to classic and on`() {
        val c = CustomizationCodec.decode(emptyMap())
        assertEquals(NowPlayingStyle.CLASSIC, c.nowPlayingStyle)
        assertTrue(c.artworkAura)
    }

    @Test
    fun `now playing style and aura round-trip through stored overrides`() {
        NowPlayingStyle.entries.forEach { style ->
            val c = CustomizationCodec.decode(mapOf(CustomField.NOW_PLAYING_STYLE to style.name, CustomField.ARTWORK_AURA to "false"))
            assertEquals(style, c.nowPlayingStyle)
            assertEquals(false, c.artworkAura)
        }
    }

    @Test
    fun `corrupt now playing values fall back to defaults`() {
        val c = CustomizationCodec.decode(mapOf(CustomField.NOW_PLAYING_STYLE to "THEATRE", CustomField.ARTWORK_AURA to "maybe"))
        assertEquals(NowPlayingStyle.CLASSIC, c.nowPlayingStyle)
        assertTrue(c.artworkAura)
    }

    @Test
    fun `now playing fields belong to the now playing studio section, so its reset covers them`() {
        assertEquals(StudioSection.NOW_PLAYING, CustomField.NOW_PLAYING_STYLE.section)
        assertEquals(StudioSection.NOW_PLAYING, CustomField.ARTWORK_AURA.section)
    }

    /** Two or three touches each, all within subtle limits. */
    @Test
    fun `every preset has two or three signature touches within limits`() {
        val default = SignatureTokens()
        PresetId.entries.forEach { id ->
            val s = Presets.tokens(id).signature
            val touches = listOf(s.auraAlpha > 0f, s.cardLiftDp > 0f, true /* Cinema backdrop is always chosen */).count { it }
            assertTrue("${id.name}: $touches touches", touches in 2..3)
            assertTrue("${id.name}: aura too strong (${s.auraAlpha})", s.auraAlpha in 0f..MAX_AURA)
            assertTrue("${id.name}: aura reaches too far (${s.auraSpreadDp})", s.auraSpreadDp in 0f..MAX_SPREAD)
            assertTrue("${id.name}: lift too high (${s.cardLiftDp})", s.cardLiftDp in 0f..MAX_LIFT)
            assertTrue("${id.name}: uses the shared default instead of its own touches", s != default)
        }
        // Presets differ from each other, so the touches are each preset's own.
        assertEquals(PresetId.entries.size, PresetId.entries.map { Presets.tokens(it).signature }.toSet().size)
        assertTrue(PresetId.entries.map { Presets.tokens(it).signature.cinemaBackdrop }.toSet().size >= 2)
        assertTrue(CinemaBackdrop.entries.isNotEmpty())
    }

    @Test
    fun `artwork formats keep their aspect ratios`() {
        assertEquals(1f, ArtworkFormat.SQUARE.ratio)
        assertEquals(0.75f, ArtworkFormat.PORTRAIT.ratio, 1e-6f)
        assertEquals(16f / 9f, ArtworkFormat.WIDE.ratio, 1e-6f)
    }

    @Test
    fun `playlist compositions pick one cover per album`() {
        assertEquals(Composition.Kind.EMPTY, Composition.of(emptyList()).kind)
        assertEquals(Composition.Kind.SINGLE, Composition.of(listOf(ArtRef(1, 10), ArtRef(2, 10))).kind)
        val stack = Composition.of(listOf(ArtRef(1, 10), ArtRef(2, 20), ArtRef(3, 20), ArtRef(4, 30)))
        assertEquals(Composition.Kind.STACK, stack.kind)
        assertEquals(listOf(10L, 20L, 30L), stack.covers.map { it.albumId })
        val mosaic = Composition.of((1L..9L).map { ArtRef(it, it * 10) })
        assertEquals(Composition.Kind.MOSAIC, mosaic.kind)
        assertEquals(4, mosaic.covers.size)
        assertEquals(listOf(1L, 2L, 3L, 4L), mosaic.covers.map { it.songId })
        // Songs without an album count separately.
        assertEquals(Composition.Kind.STACK, Composition.of(listOf(ArtRef(1, 0), ArtRef(2, 0))).kind)
    }

    @Test
    fun `remaining time counts down and never goes negative`() {
        assertEquals("−2:00", formatRemaining(60_000, 180_000))
        assertEquals("−0:00", formatRemaining(200_000, 180_000))
        assertEquals("−1:02:03", formatRemaining(0, 3_723_000))
    }

    private companion object {
        const val MAX_AURA = 0.4f
        const val MAX_SPREAD = 64f
        const val MAX_LIFT = 8f
    }
}
