package com.svara.app.appearance

import com.svara.app.appearance.tokens.RagaSpec
import com.svara.app.appearance.tokens.ShrutiSpec
import com.svara.app.appearance.tokens.SpandaSpec
import com.svara.app.appearance.tokens.TalaSpec
import com.svara.app.data.artwork.BlurredArtworkCache
import com.svara.app.data.artwork.VersionedLruCache
import com.svara.app.data.audioinfo.AudioInfoRepository
import com.svara.app.data.lyrics.LyricsText
import com.svara.app.domain.model.Album
import com.svara.app.domain.model.AudioInfo
import com.svara.app.domain.model.LyricLine
import com.svara.app.domain.model.LyricsSource
import com.svara.app.domain.model.PlayCount
import com.svara.app.domain.model.Playlist
import com.svara.app.domain.model.Song
import com.svara.app.domain.model.SongSortField
import com.svara.app.domain.model.Unfinished
import com.svara.app.library.PresetSort
import com.svara.app.library.PresetSorting
import com.svara.app.ui.home.CoverItem
import com.svara.app.ui.home.PresetHomeBuilder
import com.svara.app.ui.home.greetingFor
import com.svara.app.ui.navigation.CollectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetLogicTest {

    // ---- Synced lyrics ---------------------------------------------------------------------

    @Test
    fun `LRC lines keep their times, sorted, with repeats, fractions and offset`() {
        val lrc = """
            [ar:Someone]
            [offset:+500]
            [00:12.30]First
            [00:05]Intro
            [01:02.5][00:30.25]Chorus <00:31.00>word
        """.trimIndent()
        val lines = LyricsText.parseTimed(lrc)
        assertEquals(
            listOf(LyricLine(4_500, "Intro"), LyricLine(11_800, "First"), LyricLine(29_750, "Chorus word"), LyricLine(62_000, "Chorus word")),
            lines,
        )
        assertTrue(LyricsText.parseTimed("Just words\nNo timing").isEmpty())
    }

    @Test
    fun `active line is the last one that has started`() {
        val lines = listOf(LyricLine(1_000, "a"), LyricLine(5_000, "b"), LyricLine(9_000, "c"))
        assertEquals(-1, LyricsText.activeIndex(lines, 500))
        assertEquals(0, LyricsText.activeIndex(lines, 1_000))
        assertEquals(1, LyricsText.activeIndex(lines, 8_999))
        assertEquals(2, LyricsText.activeIndex(lines, 60_000))
        assertEquals(-1, LyricsText.activeIndex(emptyList(), 1_000))
    }

    @Test
    fun `untimed embedded lyrics stay exactly as tagged`() {
        val raw = "  Verse one\n\n  Verse two  "
        val l = LyricsText.lyrics(raw, LyricsSource.EMBEDDED)
        assertEquals(raw, l.text)
        assertTrue(l.timed.isEmpty())
        val timed = LyricsText.lyrics("[00:01]Hi\n[00:02]There", LyricsSource.SIDECAR_FILE)
        assertEquals("Hi\nThere", timed.text)
        assertEquals(2, timed.timed.size)
    }

    // ---- Cached blur bitmap ----------------------------------------------------------------

    @Test
    fun `blurred artwork is built once per album, reused, and rebuilt on a new artwork version`() {
        val cache = VersionedLruCache<Long, String>(BlurredArtworkCache.CAPACITY)
        var builds = 0
        fun get(album: Long, version: Long) = cache.getOrBuild(album, version) { builds++; "blur-$album-$version" }

        assertEquals("blur-7-1", get(7, 1))
        assertEquals("blur-7-1", get(7, 1))
        assertEquals("built once, then reused", 1, builds)
        assertEquals("blur-7-2", get(7, 2))
        assertEquals("rebuilt when the artwork version changes", 2, builds)
        assertEquals("blur-7-2", get(7, 2))
        assertEquals(2, builds)
    }

    @Test
    fun `the cache keeps the 24 most recently used albums`() {
        val cache = VersionedLruCache<Long, String>(BlurredArtworkCache.CAPACITY)
        assertEquals(24, BlurredArtworkCache.CAPACITY)
        assertEquals(32, BlurredArtworkCache.SIZE_PX)
        (1L..24L).forEach { cache.put(it, 0, "a$it") }
        cache.get(1, 0) // touch album 1 so album 2 becomes the eldest
        cache.put(25, 0, "a25")
        assertEquals(24, cache.size)
        assertEquals("a1", cache.get(1, 0))
        assertNull("least recently used album evicted", cache.get(2, 0))
        var builds = 0
        cache.getOrBuild(9L, 0) { builds++; "x" }
        assertEquals(0, builds)
        assertNull("a failed build stores nothing", cache.getOrBuild(99L, 0) { null })
        assertEquals(24, cache.size)
    }

    // ---- Quality badge ---------------------------------------------------------------------

    private fun info(codec: String?, format: String? = null, bitrate: Int? = null, calculated: Int? = null, depth: Int? = null) =
        AudioInfo(format, codec, bitrate, calculated, 44_100, 2, depth, 180_000, 1_000_000, null)

    @Test
    fun `quality badges name the format and the rate`() {
        assertEquals("FLAC", AudioInfoRepository.badge(info("FLAC", "FLAC", depth = 16)))
        assertEquals("FLAC 24-bit", AudioInfoRepository.badge(info("FLAC", "FLAC", depth = 24)))
        assertEquals("WAV", AudioInfoRepository.badge(info("PCM", "WAV")))
        assertEquals("MP3 320", AudioInfoRepository.badge(info("MP3", "MP3", bitrate = 320_000)))
        assertEquals("MP3 320", AudioInfoRepository.badge(info("MP3", "MP3", calculated = 316_400)))
        assertEquals("AAC 256", AudioInfoRepository.badge(info("AAC", "M4A", bitrate = 255_000)))
        assertEquals("Opus 141", AudioInfoRepository.badge(info("Opus", "OPUS", bitrate = 141_000)))
        assertEquals("MP3", AudioInfoRepository.badge(info("MP3", "MP3")))
        assertEquals("OGG", AudioInfoRepository.badge(info(null, "OGG")))
    }

    // ---- Home and library data -------------------------------------------------------------

    private fun song(id: Long, album: Long, artist: String = "Artist $album") = Song(
        id = id, title = "Song $id", artist = artist, album = "Album $album", albumArtist = null, albumId = album, artistId = 1,
        durationMs = 1_000L * id, trackNumber = id.toInt(), discNumber = 1, year = 2020, dateAddedSec = id, dateModifiedSec = 0,
        sizeBytes = 1, mimeType = "audio/mpeg", genre = null, volume = "primary", relativePath = "Music/", fileName = "$id.mp3", dataPath = null,
    )

    @Test
    fun `home cards come from history and the library in the specified order`() {
        val recent = listOf(song(1, 10), song(2, 10), song(3, 11))
        val most = listOf(PlayCount(song(4, 12), 9), PlayCount(song(5, 13), 3), PlayCount(song(6, 12), 4))
        val added = listOf(song(7, 14), song(8, 15))
        val home = PresetHomeBuilder.build(8, recent, most, added, emptyList(), emptyList(), emptyList())

        assertEquals(listOf("album-10", "album-11"), home.recentlyPlayed.map { it.key })
        assertEquals("most played albums rank by total plays", listOf("album-12", "album-13"), home.topAlbums.map { it.key })
        // Quick grid: recently played, then most played, then recently added, without repeats.
        assertEquals(listOf("album-10", "album-11", "album-12", "album-13", "album-14", "album-15"), home.quickGrid.map { it.key })
        assertEquals("album-14", home.newestAlbum?.key)
        assertEquals(listOf(4L, 5L, 6L), home.topSongs.map { it.id })
        assertEquals(CoverItem.Target.SongAt(1), home.topCharts[1].target)
        assertEquals(listOf("artist 12", "artist 13"), home.artists.map { it.title.lowercase() })
        assertTrue(home.heroAlbums.size <= PresetHomeBuilder.HERO)
        assertTrue(home.topAlbums.size <= PresetHomeBuilder.BANNER)
        assertEquals(CoverItem.Target.Collection(CollectionType.MOST_PLAYED), home.madeForYou.first { it.key == "mix-most" }.target)
    }

    @Test
    fun `empty history falls back to recently added for the hero and banner`() {
        val home = PresetHomeBuilder.build(2, emptyList(), emptyList(), listOf(song(1, 20), song(2, 21)), emptyList(), emptyList(), emptyList())
        assertEquals(listOf("album-20", "album-21"), home.heroAlbums.map { it.key })
        assertEquals(listOf("album-20", "album-21"), home.topAlbums.map { it.key })
        val unfinished = listOf(Unfinished(Unfinished.Kind.PLAYLIST, 5, "Mix", 2, 9, 1, 20))
        val withJump = PresetHomeBuilder.build(2, emptyList(), emptyList(), emptyList(), unfinished, emptyList(), emptyList())
        assertEquals(CoverItem.Target.Playlist(5), withJump.jumpBackIn.single().target)
    }

    @Test
    fun `library sorting per list`() {
        val albums = listOf(Album(1, "b", "Zed", 2000, 1, 100, 1), Album(2, "a", "Amy", 2001, 1, 300, 2))
        assertEquals(listOf(2L, 1L), PresetSorting.albums(albums, PresetSort.TITLE).map { it.id })
        assertEquals(listOf(2L, 1L), PresetSorting.albums(albums, PresetSort.ARTIST).map { it.id })
        assertEquals(listOf(2L, 1L), PresetSorting.albums(albums, PresetSort.DURATION).map { it.id })
        val lists = listOf(Playlist(1, "Zebra", 1, createdAt = 5, updatedAt = 5, null, null), Playlist(2, "apple pie", 1, createdAt = 9, updatedAt = 9, null, null))
        assertEquals(listOf(2L, 1L), PresetSorting.playlists(lists, PresetSort.RECENTLY_ADDED).map { it.id })
        assertEquals(listOf(2L, 1L), PresetSorting.playlists(lists, PresetSort.TITLE).map { it.id })
        assertEquals(SongSortField.DATE_ADDED, PresetSorting.songOrder(PresetSort.RECENTLY_ADDED).field)
        assertEquals(false, PresetSorting.songOrder(PresetSort.RECENTLY_ADDED).ascending)
        assertEquals(SongSortField.DURATION, PresetSorting.songOrder(PresetSort.DURATION).field)
    }

    @Test
    fun `greeting follows local time`() {
        assertEquals("Good morning", greetingFor(5))
        assertEquals("Good morning", greetingFor(11))
        assertEquals("Good afternoon", greetingFor(12))
        assertEquals("Good afternoon", greetingFor(16))
        assertEquals("Good evening", greetingFor(17))
        assertEquals("Good evening", greetingFor(2))
    }

    // ---- Tabs ------------------------------------------------------------------------------

    @Test
    fun `each preset shows its own tabs and at least one always stays visible`() {
        val none = Customization()
        assertEquals(listOf(NavTab.HOME, NavTab.SEARCH, NavTab.LIBRARY), TabVisibility.visible(ShrutiSpec.tokens.navigation.visibleTabs, none))
        assertEquals(listOf(NavTab.HOME, NavTab.LIBRARY, NavTab.SEARCH), TabVisibility.visible(TalaSpec.tokens.navigation.visibleTabs, none))
        assertEquals(listOf(NavTab.HOME, NavTab.SEARCH, NavTab.LIBRARY), TabVisibility.visible(RagaSpec.tokens.navigation.visibleTabs, none))
        assertEquals(listOf(NavTab.HOME, NavTab.SEARCH, NavTab.LIBRARY), TabVisibility.visible(SpandaSpec.tokens.navigation.visibleTabs, none))
        // ETHEREAL keeps Navigation Studio's own list.
        assertEquals(none.visibleTabs, TabVisibility.visible(emptyList(), none))

        val hideSearch = Customization(navHidden = setOf(NavTab.SEARCH))
        assertEquals(listOf(NavTab.HOME, NavTab.LIBRARY), TabVisibility.visible(TalaSpec.tokens.navigation.visibleTabs, hideSearch))
        val hideAll = Customization(navHidden = NavTab.entries.toSet())
        assertEquals(listOf(NavTab.HOME), TabVisibility.visible(TalaSpec.tokens.navigation.visibleTabs, hideAll))
        val onlyHome = Customization(navHidden = setOf(NavTab.SEARCH, NavTab.LIBRARY))
        assertTrue(TabVisibility.canHide(TalaSpec.tokens.navigation.visibleTabs, onlyHome, NavTab.HOME) != null)
        assertNull(TabVisibility.canHide(TalaSpec.tokens.navigation.visibleTabs, none, NavTab.HOME))
    }

    @Test
    fun `the last tab is kept across a preset change when the new preset has it`() {
        assertSame(NavTab.SEARCH, TabVisibility.keep(NavTab.SEARCH, TalaSpec.tokens.navigation.visibleTabs))
        assertSame(NavTab.HOME, TabVisibility.keep(NavTab.SONGS, SpandaSpec.tokens.navigation.visibleTabs))
        assertSame(NavTab.HOME, TabVisibility.keep(null, RagaSpec.tokens.navigation.visibleTabs))
    }
}
