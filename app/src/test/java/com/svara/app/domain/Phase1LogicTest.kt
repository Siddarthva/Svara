package com.svara.app.domain

import com.svara.app.domain.library.CollectionSearch
import com.svara.app.domain.library.FolderFilter
import com.svara.app.domain.library.FuzzyMatch
import com.svara.app.domain.library.SongQueries
import com.svara.app.domain.library.TextKeys
import com.svara.app.domain.model.FolderKey
import com.svara.app.domain.model.FolderRule
import com.svara.app.domain.model.FolderRuleType
import com.svara.app.domain.model.QueueState
import com.svara.app.domain.model.SongFilter
import com.svara.app.domain.model.SongSortField
import com.svara.app.domain.model.SortOrder
import com.svara.app.domain.playlist.M3u
import com.svara.app.domain.queue.QueueHistory
import com.svara.app.playback.ReplayGain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase1LogicTest {

    private fun score(query: String, text: String) = FuzzyMatch.score(TextKeys.searchTerms(query), TextKeys.search(text))

    @Test
    fun `partial matching finds words by prefix and substring`() {
        assertEquals(FuzzyMatch.EXACT, score("bohem", "Bohemian Rhapsody Queen"))
        assertEquals(FuzzyMatch.PARTIAL, score("hemian", "Bohemian Rhapsody"))
        assertNotNull(score("queen bohem", "Bohemian Rhapsody Queen"))
    }

    @Test
    fun `typo tolerance scales with word length`() {
        assertNotNull("one swap in a long word", score("bohemain", "Bohemian Rhapsody"))
        assertNotNull("two edits in a long word", score("rhapsdoy", "Bohemian Rhapsody"))
        assertNotNull("one edit in a medium word", score("quen", "Queen"))
        assertNull("no typos allowed in 3-letter terms", score("abx", "Abba"))
        assertNull("three edits is too many", score("bxhxmxan", "Bohemian"))
    }

    @Test
    fun `typo prefix matching works while typing`() {
        assertNotNull(score("bohme", "Bohemian Rhapsody")) // transposed prefix
    }

    @Test
    fun `exact matches rank above fuzzy`() {
        val ranked = CollectionSearch.rank(listOf("Bohemian Like You", "Bohemain Typo Album"), TextKeys.searchTerms("bohemian"), text = { it }, sortKey = { it })
        assertEquals("Bohemian Like You", ranked.first())
        assertEquals(2, ranked.size)
    }

    @Test
    fun `OSA distance counts a swap as one edit`() {
        assertEquals(1, FuzzyMatch.distance("ab", "ba", 2))
        assertEquals(1, FuzzyMatch.distance("kitten", "kittan", 2))
        assertEquals(3, FuzzyMatch.distance("abc", "xyz", 2)) // capped at max + 1
    }

    @Test
    fun `system folders are hidden on any volume and a user include wins the tie`() {
        val rules = FolderFilter.effectiveRules(emptyList(), excludeSystemFolders = true)
        assertFalse(FolderFilter.isVisible(FolderKey("primary", "Ringtones/"), rules))
        assertFalse(FolderFilter.isVisible(FolderKey("1A2B-3C4D", "Notifications/Sub/"), rules))
        assertTrue(FolderFilter.isVisible(FolderKey("primary", "Music/"), rules))

        val withInclude = FolderFilter.effectiveRules(
            listOf(FolderRule(FolderKey("primary", "Ringtones/"), FolderRuleType.INCLUDE, null, 0)),
            excludeSystemFolders = true,
        )
        assertTrue(FolderFilter.isVisible(FolderKey("primary", "Ringtones/"), withInclude))
        assertTrue(FolderFilter.isVisible(FolderKey("primary", "Ringtones/"), FolderFilter.effectiveRules(emptyList(), false)))
    }

    @Test
    fun `downloads filter and location sort build the expected SQL`() {
        val (sql, _) = SongQueries.list(SortOrder(SongSortField.LOCATION), 0, filter = SongFilter.DOWNLOADS)
        assertTrue(sql.contains("relativePath LIKE 'Download/%'"))
        assertTrue(sql.contains("ORDER BY volume ASC, relativePath COLLATE NOCASE ASC, fileName COLLATE NOCASE ASC"))
    }

    @Test
    fun `undo history keeps the last 20 edits, newest first`() {
        val h = QueueHistory(capacity = 20)
        repeat(25) { i -> h.push("edit $i", QueueState(listOf(i.toLong()), listOf(0), 0, 0, false)) }
        assertEquals(20, h.size)
        assertEquals("edit 24", h.pop()!!.label)
        assertEquals(listOf(23L), h.pop()!!.before.songIds)
        repeat(18) { h.pop() }
        assertNull(h.pop())
    }

    @Test
    fun `m3u parsing handles extinf, comments, windows paths and BOM`() {
        val text = "\uFEFF#EXTM3U\n#EXTINF:215,Queen - Bohemian Rhapsody\n..\\Music\\Queen\\01 Bohemian Rhapsody.mp3\n# comment\nsong2.flac\n#EXTINF:-1,No Artist Title\n/storage/emulated/0/Music/x.ogg\n"
        val entries = M3u.parse(text)
        assertEquals(3, entries.size)
        assertEquals("../Music/Queen/01 Bohemian Rhapsody.mp3", entries[0].location)
        assertEquals(215, entries[0].durationSec)
        assertEquals("Queen", entries[0].artist)
        assertEquals("Bohemian Rhapsody", entries[0].title)
        assertNull(entries[1].title)
        assertNull(entries[2].durationSec)
        assertEquals("No Artist Title", entries[2].title)
        assertEquals("music/queen/01 bohemian rhapsody.mp3", M3u.normalizedTail(entries[0].location))
        assertEquals("01 Bohemian Rhapsody.mp3", M3u.fileName(entries[0].location))
    }

    @Test
    fun `m3u write round trips`() {
        val text = M3u.write(listOf(M3u.WriteItem("Music/A/b.mp3", 61, "Artist", "Title")))
        val back = M3u.parse(text).single()
        assertEquals("Music/A/b.mp3", back.location)
        assertEquals(61, back.durationSec)
        assertEquals("Artist", back.artist)
    }

    @Test
    fun `replaygain parsing and attenuation only`() {
        assertEquals(-6.54, ReplayGain.parseDb("REPLAYGAIN_TRACK_GAIN", "-6.54 dB")!!, 1e-9)
        assertEquals(2.0, ReplayGain.parseDb("replaygain_track_gain", "+2,0 dB")!!, 1e-9)
        assertEquals(5.0 - 1.0, ReplayGain.parseDb("R128_TRACK_GAIN", "-256")!!, 1e-9)
        assertNull(ReplayGain.parseDb("REPLAYGAIN_TRACK_GAIN", "loud"))
        assertEquals(0.5f, ReplayGain.linear(-6.0206), 0.001f)
        assertEquals(1f, ReplayGain.linear(3.0)) // never boosts
    }
}
