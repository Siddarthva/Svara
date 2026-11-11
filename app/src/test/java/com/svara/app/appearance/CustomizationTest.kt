package com.svara.app.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomizationTest {

    @Test
    fun `no overrides gives the defaults`() {
        assertEquals(Customization(), CustomizationCodec.decode(emptyMap()))
    }

    @Test
    fun `text scale maps into 85 to 130 percent`() {
        assertEquals(100, CustomizationCodec.textScale(null))
        assertEquals(100, CustomizationCodec.textScale("big"))
        assertEquals(85, CustomizationCodec.textScale("50"))
        assertEquals(130, CustomizationCodec.textScale("400"))
        assertEquals(115, CustomizationCodec.textScale("115"))
        // Multiplies the system font scale: 1.3 system × 130 % ≈ 1.69.
        assertEquals(1.69f, 1.3f * CustomizationCodec.textScale("130") / 100f, 0.0001f)
    }

    @Test
    fun `seek increment is 5, 10 or 15 seconds`() {
        assertEquals(5, CustomizationCodec.seekIncrement("5"))
        assertEquals(15, CustomizationCodec.seekIncrement("15"))
        assertEquals(10, CustomizationCodec.seekIncrement("7"))
        assertEquals(10, CustomizationCodec.seekIncrement("x"))
        assertEquals(10, CustomizationCodec.seekIncrement(null))
    }

    @Test
    fun `tab order keeps saved order, drops junk and appends missing tabs`() {
        val order = CustomizationCodec.decodeOrder("SETTINGS,BOGUS,HOME,HOME")
        assertEquals(listOf(NavTab.SETTINGS, NavTab.HOME), order.take(2))
        // ETHEREAL's bar never gains the layout presets' Search and Library tabs.
        assertEquals(NavTab.ANAHATA.toSet(), order.toSet())
        assertEquals(NavTab.ANAHATA.size, order.size)
    }

    @Test
    fun `hidden tabs never leave the bar empty or overfull`() {
        val all = NavTab.entries.joinToString(",") { it.name }
        assertEquals(Customization.DEFAULT_NAV_HIDDEN, CustomizationCodec.decodeHidden(all))
        assertEquals(Customization.DEFAULT_NAV_HIDDEN, CustomizationCodec.decodeHidden(""))
        assertEquals(setOf(NavTab.SONGS, NavTab.ALBUMS), CustomizationCodec.decodeHidden("SONGS,ALBUMS"))
    }

    @Test
    fun `the last visible tab can't be hidden`() {
        val one = Customization(navHidden = NavTab.ANAHATA.toSet() - NavTab.PLAYLISTS)
        assertEquals(listOf(NavTab.PLAYLISTS), one.visibleTabs)
        assertNotNull(CustomizationCodec.canHide(one, NavTab.PLAYLISTS))
        assertNull(CustomizationCodec.canHide(Customization(), NavTab.HOME))
    }

    @Test
    fun `a full bar can't take another tab`() {
        val full = Customization()
        assertEquals(Customization.MAX_VISIBLE_TABS, full.visibleTabs.size)
        assertNotNull(CustomizationCodec.canShow(full, NavTab.ARTISTS))
        assertNull(CustomizationCodec.canShow(full, NavTab.HOME))
    }

    @Test
    fun `control order must be a permutation and play pause is never stored`() {
        assertEquals(listOf(ControlId.REPEAT, ControlId.NEXT, ControlId.PREVIOUS, ControlId.SHUFFLE), CustomizationCodec.decodeControls("REPEAT,NEXT,PREVIOUS,SHUFFLE"))
        assertEquals(Customization.DEFAULT_CONTROL_ORDER, CustomizationCodec.decodeControls("NEXT,NEXT,PREVIOUS,SHUFFLE"))
        assertEquals(Customization.DEFAULT_CONTROL_ORDER, CustomizationCodec.decodeControls("PLAY,NEXT,PREVIOUS,SHUFFLE"))
        assertEquals(Customization.DEFAULT_CONTROL_ORDER, CustomizationCodec.decodeControls("NEXT"))
    }

    @Test
    fun `corrupt studio values fall back to defaults`() {
        val c = CustomizationCodec.decode(
            mapOf(
                CustomField.PROGRESS_STYLE to "WAVY",
                CustomField.SWIPE_SKIP to "yes",
                CustomField.TRANSITION_SPEED to "9",
                CustomField.INTENSITY to "250",
                CustomField.PERFORMANCE_MODE to "TURBO",
                CustomField.CORNER to "CIRCLE",
            )
        )
        assertEquals(ProgressStyle.LINE, c.progressStyle)
        assertTrue(c.swipeToSkip)
        assertEquals(1f, c.transitionSpeed)
        assertEquals(100, c.intensity)
        assertNull(c.performanceMode)
        assertEquals(ArtworkCorner.CIRCLE, c.corner)
    }
}

class HomeLayoutOrderTest {
    private val sections = com.svara.app.domain.model.HomeSection.entries

    @org.junit.Test
    fun `layouts reorder sections without dropping any`() {
        HomeLayout.entries.forEach { layout ->
            val ordered = com.svara.app.ui.home.orderForLayout(sections, layout)
            assertEquals(sections.toSet(), ordered.toSet())
            assertEquals(sections.size, ordered.size)
        }
        assertEquals(com.svara.app.domain.model.HomeSection.PLAYLISTS, com.svara.app.ui.home.orderForLayout(sections, HomeLayout.PLAYLIST_CARDS).first())
        assertEquals(com.svara.app.domain.model.HomeSection.RECENTLY_PLAYED, com.svara.app.ui.home.orderForLayout(sections, HomeLayout.RECENT_FIRST).first())
        assertEquals(sections, com.svara.app.ui.home.orderForLayout(sections, HomeLayout.ARTWORK_FIRST))
    }
}
