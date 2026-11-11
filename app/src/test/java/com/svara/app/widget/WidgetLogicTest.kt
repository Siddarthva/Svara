package com.svara.app.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.svara.app.playback.MultiPressDetector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Widget settings, snapshot rules and headset press counting. */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetLogicTest {

    @Test
    fun `widget settings default sensibly and read what the configuration saved`() {
        assertEquals(WidgetSettings(), WidgetSettings.from(mutablePreferencesOf()))
        val p = mutablePreferencesOf(
            WidgetSettings.ART to false,
            WidgetSettings.BACKGROUND to WidgetSettings.Background.TRANSPARENT.name,
            WidgetSettings.ACCENT to WidgetSettings.AccentSource.ARTWORK.name,
            WidgetSettings.COMPACT to true,
            WidgetSettings.TEXT to false,
            WidgetSettings.CONTROLS to false,
        )
        assertEquals(WidgetSettings(false, WidgetSettings.Background.TRANSPARENT, WidgetSettings.AccentSource.ARTWORK, true, false, false), WidgetSettings.from(p))
        // Unknown stored names (from a future version) fall back.
        assertEquals(WidgetSettings.Background.TRANSLUCENT, WidgetSettings.from(mutablePreferencesOf(WidgetSettings.BACKGROUND to "GLASS")).background)
    }

    @Test
    fun `snapshot knows when nothing is queued and clamps progress`() {
        assertTrue(NowPlayingSnapshot().empty)
        assertFalse(NowPlayingSnapshot(songId = 4, title = "Song").empty)
        assertEquals(0.5f, NowPlayingSnapshot(positionMs = 50, durationMs = 100).progress)
        assertEquals(1f, NowPlayingSnapshot(positionMs = 500, durationMs = 100).progress)
        assertEquals(0f, NowPlayingSnapshot(positionMs = 5, durationMs = 0).progress)
    }

    @Test
    fun `headset presses within the window are counted together`() = runTest {
        val results = mutableListOf<Int>()
        val d = MultiPressDetector(windowMs = 400)
        d.press(this) { results += it }
        advanceTimeBy(500); runCurrent()
        repeat(2) { d.press(this) { results += it }; advanceTimeBy(100) }
        advanceTimeBy(500); runCurrent()
        repeat(5) { d.press(this) { results += it }; advanceTimeBy(100) }
        advanceTimeBy(500); runCurrent()
        assertEquals(listOf(1, 2, 3), results)
    }
}
