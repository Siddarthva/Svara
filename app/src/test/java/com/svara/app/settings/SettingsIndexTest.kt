package com.svara.app.settings

import com.svara.app.data.repository.SettingsCodec
import com.svara.app.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsIndexTest {

    @Test
    fun `every entry appears once and opens a known screen`() {
        val titles = SETTINGS_INDEX.map { it.title }
        assertEquals(titles.toSet().size, titles.size)
        val sections = setOf("appearance", "playback", "audio", "library", "data", "about")
        SETTINGS_INDEX.forEach { assertTrue(it.section, it.section in sections) }
    }

    @Test
    fun `common words find the right screen`() {
        mapOf(
            "dark" to "appearance", "battery" to "appearance", "blur" to "appearance", "folders" to "library",
            "font size" to "appearance", "sleep" to "playback", "recap" to "library",
        ).forEach { (q, section) -> assertTrue(q, searchSettings(q).any { it.section == section }) }
        assertTrue(searchSettings("   ").isEmpty())
    }

    @Test
    fun `both recap entry points share one stored key`() {
        val stored = SettingsCodec.encode(AppSettings(recapAutoAdvance = false, recapHomeCard = false))
        assertEquals("false", stored["recap.autoAdvance"])
        assertEquals("false", stored["recap.homeCard"])
        val loaded = SettingsCodec.decode(stored)
        assertEquals(false, loaded.recapAutoAdvance)
        assertEquals(false, loaded.recapHomeCard)
    }

    @Test
    fun `values saved before the reorganisation still load`() {
        val old = mapOf("playback.crossfadeSec" to "6", "privacy.historyEnabled" to "false", "a11y.highContrast" to "true")
        val s = SettingsCodec.decode(old)
        assertEquals(6, s.crossfadeSec)
        assertEquals(false, s.historyEnabled)
        assertEquals(true, s.highContrast)
        assertEquals(true, s.recapAutoAdvance)
    }
}
