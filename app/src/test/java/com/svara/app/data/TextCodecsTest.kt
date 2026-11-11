package com.svara.app.data

import com.svara.app.data.lyrics.LyricsText
import com.svara.app.data.repository.SettingsCodec
import com.svara.app.domain.model.AppSettings
import com.svara.app.domain.model.SleepTimerOption
import com.svara.app.domain.model.SongSortField
import com.svara.app.domain.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextCodecsTest {

    @Test
    fun `settings round trip`() {
        val s = AppSettings(
            songSort = SortOrder(SongSortField.DATE_ADDED, ascending = false),
            defaultSleepTimer = SleepTimerOption.END_OF_TRACK,
            restoreQueueOnLaunch = false,
            skipSilence = true,
            minTrackDurationSec = 30,
        )
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun `malformed settings fall back to defaults`() {
        val decoded = SettingsCodec.decode(
            mapOf("library.songSort" to "NOPE:desc", "playback.defaultSleepTimer" to "MIN_99", "library.minDurationSec" to "-5")
        )
        assertEquals(AppSettings().songSort, decoded.songSort)
        assertEquals(AppSettings().defaultSleepTimer, decoded.defaultSleepTimer)
        assertEquals(0, decoded.minTrackDurationSec)
    }

    @Test
    fun `lrc timing and tags are stripped`() {
        val lrc = "[ar:Someone]\n[ti:Song]\n[00:01.00]First line\n[00:05.50][01:05.50]Chorus\n[00:09.00]<00:09.00>Word <00:09.50>timed\n"
        assertEquals("First line\nChorus\nWord timed", LyricsText.toPlainText(lrc))
    }

    @Test
    fun `USLT latin1 and utf8 frames`() {
        val latin = byteArrayOf(0) + "eng".toByteArray() + byteArrayOf(0) + "Hello lyrics".toByteArray(Charsets.ISO_8859_1)
        assertEquals("Hello lyrics", LyricsText.parseUslt(latin))
        val utf8 = byteArrayOf(3) + "eng".toByteArray() + "desc".toByteArray() + byteArrayOf(0) + "Grüße ✓".toByteArray()
        assertEquals("Grüße ✓", LyricsText.parseUslt(utf8))
    }

    @Test
    fun `USLT utf16 frame with BOM`() {
        val text = "Line A\nLine B"
        val frame = byteArrayOf(1) + "eng".toByteArray() + byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0) +
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(text, LyricsText.parseUslt(frame))
    }

    @Test
    fun `lyrics frames include USLT, MP4 lyr and lyric-named TXXX`() {
        assertEquals(true, LyricsText.isLyricsFrame("USLT", null))
        assertEquals(true, LyricsText.isLyricsFrame("©lyr", null))
        assertEquals(true, LyricsText.isLyricsFrame("TXXX", "Lyrics"))
        assertEquals(true, LyricsText.isLyricsFrame("TXXX", "UNSYNCEDLYRICS"))
        assertEquals(true, LyricsText.isLyricsFrame("TXXX", "USLT")) // what ffmpeg writes
        assertEquals(false, LyricsText.isLyricsFrame("TXXX", "MusicBrainz Album Id"))
        assertEquals(false, LyricsText.isLyricsFrame("TIT2", null))
    }

    @Test
    fun `USLT with no text is null`() {
        assertNull(LyricsText.parseUslt(byteArrayOf(0) + "eng".toByteArray() + byteArrayOf(0)))
    }

    @Test
    fun `sidecar decoding handles BOMs and latin1 fallback`() {
        assertEquals("abc", LyricsText.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "abc".toByteArray()))
        assertEquals("é", LyricsText.decode(byteArrayOf(0xE9.toByte())))
    }
}
