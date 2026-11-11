package com.svara.app.domain

import com.svara.app.data.mediastore.MediaStoreAudioSource
import com.svara.app.domain.library.CollectionSearch
import com.svara.app.domain.library.FolderFilter
import com.svara.app.domain.library.LibrarySorting
import com.svara.app.domain.library.SongQueries
import com.svara.app.domain.library.TextKeys
import com.svara.app.domain.model.Album
import com.svara.app.domain.model.AlbumSortField
import com.svara.app.domain.model.FolderKey
import com.svara.app.domain.model.FolderRule
import com.svara.app.domain.model.FolderRuleType
import com.svara.app.domain.model.SongSortField
import com.svara.app.domain.model.SortOrder
import com.svara.app.playback.MediaItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryLogicTest {

    private fun rule(path: String, type: FolderRuleType, volume: String = "primary") =
        FolderRule(FolderKey(volume, FolderKey.normalizePath(path)), type, null, 0)

    @Test
    fun `no rules shows everything`() {
        assertTrue(FolderFilter.isVisible(FolderKey("primary", "Recordings/"), emptyList()))
    }

    @Test
    fun `exclude hides a folder and its subfolders only`() {
        val rules = listOf(rule("Recordings", FolderRuleType.EXCLUDE))
        assertFalse(FolderFilter.isVisible(FolderKey("primary", "Recordings/Calls/"), rules))
        assertTrue(FolderFilter.isVisible(FolderKey("primary", "Music/"), rules))
    }

    @Test
    fun `include limits the library and a nested exclude wins inside it`() {
        val rules = listOf(rule("Music", FolderRuleType.INCLUDE), rule("Music/Podcasts", FolderRuleType.EXCLUDE))
        assertTrue(FolderFilter.isVisible(FolderKey("primary", "Music/Rock/"), rules))
        assertFalse(FolderFilter.isVisible(FolderKey("primary", "Music/Podcasts/"), rules))
        assertFalse(FolderFilter.isVisible(FolderKey("primary", "Download/"), rules))
    }

    @Test
    fun `volume comparison ignores case and prefix must be a folder boundary`() {
        val rules = listOf(rule("Music", FolderRuleType.INCLUDE, volume = "1A2B-3C4D"))
        assertTrue(FolderFilter.isVisible(FolderKey("1a2b-3c4d", "Music/"), rules))
        assertFalse(FolderFilter.isVisible(FolderKey("1A2B-3C4D", "MusicVideos/"), rules))
    }

    @Test
    fun `folder ids round trip`() {
        val key = FolderKey.fromId("primary:Music/Rock")
        assertEquals(FolderKey("primary", "Music/Rock/"), key)
        assertEquals("Rock", key!!.displayName)
        assertNull(FolderKey.fromId("nonsense"))
    }

    @Test
    fun `mediastore locations map to SAF style keys`() {
        assertEquals(FolderKey("primary", "Music/A/"), MediaStoreAudioSource.resolveLocation("Music/A/", "external_primary", null))
        assertEquals(FolderKey("1A2B-3C4D", "Music/"), MediaStoreAudioSource.resolveLocation("Music/", "1a2b-3c4d", null))
        assertEquals(FolderKey("primary", "Music/B/"), MediaStoreAudioSource.resolveLocation(null, null, "/storage/emulated/0/Music/B/x.mp3"))
        assertEquals(FolderKey("1A2B-3C4D", "Songs/"), MediaStoreAudioSource.resolveLocation(null, null, "/storage/1a2b-3c4d/Songs/y.mp3"))
    }

    @Test
    fun `text keys fold case and accents and drop leading articles`() {
        assertEquals("bjork sørensen", TextKeys.search("Björk  Sørensen"))
        assertEquals("beatles", TextKeys.sort("The Beatles"))
        assertEquals("the", TextKeys.sort("The"))
        assertEquals(listOf("cafe", "senorita"), TextKeys.searchTerms("  Café   Señorita "))
    }

    @Test
    fun `song query orders by indexed keys with stable tiebreak`() {
        val (sql, args) = SongQueries.list(SortOrder(SongSortField.ARTIST, ascending = false), minMs = 10_000)
        assertTrue(sql.contains("ORDER BY artistKey DESC, albumKey DESC"))
        assertTrue(sql.endsWith("titleKey DESC, id DESC"))
        assertEquals(10_000L, args.single())
    }

    @Test
    fun `search query escapes LIKE wildcards`() {
        val (sql, args) = SongQueries.search(listOf("100%", "a_b"), 0, 50)
        assertEquals(2, Regex("searchText LIKE").findAll(sql).count())
        assertEquals(listOf<Any>(0L, "%100\\%%", "%a\\_b%", "100\\%%", 50), args.toList())
    }

    @Test
    fun `album sorting and search`() {
        val albums = listOf(
            Album(1, "Zebra", "Artist", 2001, 3, 0, 1, "zebra", "artist"),
            Album(2, "Apple", "Artist", 1999, 9, 0, 2, "apple", "artist"),
        )
        assertEquals(listOf(2L, 1L), LibrarySorting.sortAlbums(albums, SortOrder(AlbumSortField.TITLE)).map { it.id })
        assertEquals(listOf(2L, 1L), LibrarySorting.sortAlbums(albums, SortOrder(AlbumSortField.SONG_COUNT, false)).map { it.id })
        assertEquals(listOf(1L), CollectionSearch.albums(albums, listOf("zeb")).map { it.id })
    }

    @Test
    fun `media ids carry the song id and stay unique`() {
        val a = MediaItems.newMediaId(42)
        val b = MediaItems.newMediaId(42)
        assertTrue(a != b)
        assertEquals(42L, MediaItems.songIdOf(a))
        assertNull(MediaItems.songIdOf("garbage"))
    }
}
