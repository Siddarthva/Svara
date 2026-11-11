package com.svara.app.domain

import com.svara.app.data.repository.SettingsCodec
import com.svara.app.domain.library.IssueKind
import com.svara.app.domain.library.LibraryDoctor
import com.svara.app.domain.library.MetadataField
import com.svara.app.domain.model.AppSettings
import com.svara.app.domain.model.Song
import com.svara.app.domain.playlist.RuleSong
import com.svara.app.domain.playlist.SmartCondition
import com.svara.app.domain.playlist.SmartField
import com.svara.app.domain.playlist.SmartMatch
import com.svara.app.domain.playlist.SmartRuleEngine
import com.svara.app.domain.playlist.SmartRules
import com.svara.app.domain.playlist.SmartSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 2 pure logic: smart playlist rules, Library Doctor checks and the new settings. */
class CoreListeningLogicTest {

    private fun song(
        id: Long, title: String = "Song $id", artist: String = "Artist", album: String = "Album",
        addedDaysAgo: Int = 100, minutes: Double = 4.0, genre: String? = null, path: String = "Music/", file: String = "song$id.mp3",
    ) = Song(
        id = id, title = title, artist = artist, album = album, albumArtist = null, albumId = id, artistId = 1,
        durationMs = (minutes * 60_000).toLong(), trackNumber = 1, discNumber = 1, year = 2020,
        dateAddedSec = (NOW - addedDaysAgo * DAY) / 1000, dateModifiedSec = 0, sizeBytes = 1, mimeType = "audio/mpeg",
        genre = genre, volume = "primary", relativePath = path, fileName = file, dataPath = null,
    )

    private fun rs(s: Song, plays: Int = 0, last: Long = 0, fav: Boolean = false) = RuleSong(s, plays, last, fav)

    @Test
    fun `rules combine with all or any and ignore unusable values`() {
        val songs = listOf(
            rs(song(1, artist = "Anoushka Shankar", addedDaysAgo = 3), plays = 5, fav = true),
            rs(song(2, artist = "Ravi Shankar", addedDaysAgo = 40), plays = 1),
            rs(song(3, artist = "Other", addedDaysAgo = 2, genre = "Jazz"), plays = 0),
        )
        val all = SmartRules(SmartMatch.ALL, listOf(SmartCondition(SmartField.ARTIST, "shankar"), SmartCondition(SmartField.ADDED_WITHIN_DAYS, "7")))
        assertEquals(listOf(1L), SmartRuleEngine.evaluate(all, songs, 50, NOW).map { it.id })
        val any = all.copy(match = SmartMatch.ANY)
        assertEquals(setOf(1L, 2L, 3L), SmartRuleEngine.evaluate(any, songs, 50, NOW).map { it.id }.toSet())
        // An empty text condition is ignored, not "matches nothing".
        val blank = SmartRules(SmartMatch.ALL, listOf(SmartCondition(SmartField.GENRE, "  "), SmartCondition(SmartField.FAVOURITE)))
        assertEquals(listOf(1L), SmartRuleEngine.evaluate(blank, songs, 50, NOW).map { it.id })
        assertEquals(listOf(3L), SmartRuleEngine.evaluate(SmartRules(conditions = listOf(SmartCondition(SmartField.GENRE, "jazz"))), songs, 50, NOW).map { it.id })
    }

    @Test
    fun `play count, duration and folder rules, sorting and limit`() {
        val songs = listOf(
            rs(song(1, minutes = 2.0, path = "Music/Live/"), plays = 9, last = 300),
            rs(song(2, minutes = 8.0, path = "Music/Studio/"), plays = 3, last = 500),
            rs(song(3, minutes = 5.0, path = "Music/Live/2020/"), plays = 0),
        )
        fun ids(vararg c: SmartCondition, sort: SmartSort = SmartSort.MOST_PLAYED, limit: Int = 10) =
            SmartRuleEngine.evaluate(SmartRules(SmartMatch.ALL, c.toList(), sort), songs, limit, NOW).map { it.id }
        assertEquals(listOf(1L, 2L), ids(SmartCondition(SmartField.PLAYS_AT_LEAST, "1")))
        assertEquals(listOf(2L, 3L), ids(SmartCondition(SmartField.PLAYS_AT_MOST, "3")))
        assertEquals(listOf(2L, 3L), ids(SmartCondition(SmartField.LONGER_THAN_MIN, "4")))
        assertEquals(listOf(1L), ids(SmartCondition(SmartField.SHORTER_THAN_MIN, "3")))
        assertEquals(setOf(1L, 3L), ids(SmartCondition(SmartField.FOLDER, "Music/Live")).toSet())
        assertEquals(listOf(2L, 1L, 3L), ids(sort = SmartSort.RECENTLY_PLAYED))
        assertEquals(listOf(2L, 3L, 1L), ids(sort = SmartSort.LONGEST))
        assertEquals(listOf(1L), ids(limit = 1))
    }

    @Test
    fun `rules survive storage, including tabs and new lines in values`() {
        val rules = SmartRules(SmartMatch.ANY, listOf(SmartCondition(SmartField.ALBUM, "A\tB\nC\\D"), SmartCondition(SmartField.FAVOURITE)), SmartSort.TITLE)
        assertEquals(rules, SmartRuleEngine.decode(SmartRuleEngine.encode(rules)))
        assertNull(SmartRuleEngine.decode(null))
        // Unknown fields from a newer version are dropped, the rest kept.
        assertEquals(listOf(SmartField.FAVOURITE), SmartRuleEngine.decode("ALL\tTITLE\nMOOD\tcalm\nFAVOURITE\t")!!.conditions.map { it.field })
    }

    @Test
    fun `artist spellings converge on the most used form`() {
        val songs = listOf(
            song(1, artist = "A. R. Rahman"), song(2, artist = "A. R. Rahman"), song(3, artist = "a r rahman"),
            song(4, artist = "Simon & Garfunkel"), song(5, artist = "Simon and Garfunkel"), song(6, artist = "Simon & Garfunkel"),
            song(7, artist = "Solo"),
        )
        val issues = LibraryDoctor.artistSpellings(songs).associate { it.song.id to it.proposed }
        assertEquals(mapOf(3L to "A. R. Rahman", 5L to "Simon & Garfunkel"), issues)
    }

    @Test
    fun `title checks fix numbering, extensions and underscores but keep real titles`() {
        assertEquals("Intro", LibraryDoctor.cleanTitle("01 - Intro"))
        assertEquals("Intro", LibraryDoctor.cleanTitle("01. Intro"))
        assertEquals("Intro", LibraryDoctor.cleanTitle("03 Intro.mp3"))
        assertEquals("Blue in Green", LibraryDoctor.cleanTitle("Blue_in_Green"))
        assertEquals("99 Problems", LibraryDoctor.cleanTitle("99 Problems"))
        assertEquals("2.0", LibraryDoctor.cleanTitle("2.0"))
        assertEquals("7 Rings", LibraryDoctor.cleanTitle("7 Rings"))
        val issues = LibraryDoctor.titleProblems(listOf(song(1, title = "02 - Raga"), song(2, title = "Raga")))
        assertEquals(listOf(1L), issues.map { it.song.id })
        assertEquals("Raga", issues.single().proposed)
    }

    @Test
    fun `empty metadata proposes what the file name gives and nothing it can't`() {
        val issues = LibraryDoctor.emptyMetadata(listOf(song(1, title = "", artist = "", album = "", file = "Ravi Shankar - Raga Jog.flac")))
        val byField = issues.associateBy { it.field }
        assertEquals("Raga Jog", byField[MetadataField.TITLE]?.proposed)
        assertEquals("Ravi Shankar", byField[MetadataField.ARTIST]?.proposed)
        assertNull("albums can't be guessed", byField[MetadataField.ALBUM]?.proposed)
        assertEquals(IssueKind.MISSING_ALBUM, byField[MetadataField.ALBUM]?.kind)
    }

    @Test
    fun `new settings round-trip and fall back on bad values`() {
        val s = AppSettings(startScreen = "PLAYLISTS", sleepCustomMinutes = 90, lyricsTextScale = 130, lyricsLineSpacing = 18,
            recentSearches = listOf("raga, jog", "night"), oneHandedSearch = true)
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
        val bad = SettingsCodec.decode(mapOf("playback.sleepCustomMinutes" to "9999", "lyrics.textScale" to "x", "nav.startScreen" to ""))
        assertEquals(AppSettings.SLEEP_CUSTOM_RANGE.last, bad.sleepCustomMinutes)
        assertEquals(100, bad.lyricsTextScale)
        assertEquals(AppSettings.START_LAST, bad.startScreen)
        assertTrue(AppSettings().recentSearches.isEmpty())
    }

    private companion object {
        const val DAY = 86_400_000L
        const val NOW = 1_800_000_000_000L
    }
}
