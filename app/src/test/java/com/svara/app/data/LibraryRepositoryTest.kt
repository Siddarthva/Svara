package com.svara.app.data

import androidx.paging.testing.asSnapshot
import com.svara.app.data.db.FolderRuleEntity
import com.svara.app.data.db.SvaraDatabase
import com.svara.app.data.repository.LibraryRepositoryImpl
import com.svara.app.domain.model.FolderKey
import com.svara.app.domain.model.SongFilter
import com.svara.app.domain.model.SongSortField
import com.svara.app.domain.model.SortOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryTest {

    private lateinit var db: SvaraDatabase
    private val source = FakeAudioSource()
    private val settings = FakeSettings()

    @Before fun setUp() {
        db = inMemoryDb()
    }

    @After fun tearDown() = db.close()

    private fun TestScope.repo() = LibraryRepositoryImpl(
        source, db.songDao(), db.folderRuleDao(), db.settingsDao(), settings, db.tx(),
        scope = backgroundScope, scanDispatcher = Dispatchers.IO, compute = Dispatchers.Default, metadataOverrides = db.metadataOverrideDao(),
    )

    private suspend fun LibraryRepositoryImpl.ids(field: SongSortField, asc: Boolean = true) = songIds(SortOrder(field, asc))

    @Test
    fun `rescan writes only the difference`() = runTest {
        val repo = repo()
        source.songs = listOf(song(1), song(2), song(3))
        assertEquals(3, repo.rescan().added)

        // 1 unchanged, 2 modified, 3 deleted, 4 new.
        source.songs = listOf(song(1), song(2, title = "Renamed", modified = 2), song(4))
        val r = repo.rescan()
        assertEquals(1, r.added)
        assertEquals(1, r.updated)
        assertEquals(1, r.removed)
        assertEquals(listOf(4L, 2L, 1L), repo.ids(SongSortField.DATE_ADDED, asc = false))
        assertEquals("Renamed", repo.songById(2)?.title)
    }

    @Test
    fun `sorting runs in SQL on folded keys`() = runTest {
        val repo = repo()
        source.songs = listOf(
            song(1, title = "Zebra", artist = "The Beatles", durationMs = 300_000),
            song(2, title = "Ålesund", artist = "Abba", durationMs = 100_000),
            song(3, title = "apple", artist = "Coldplay", durationMs = 200_000),
        )
        repo.rescan()
        assertEquals(listOf(2L, 3L, 1L), repo.ids(SongSortField.TITLE)) // Å folds to A, case-insensitive
        assertEquals(listOf(1L, 3L, 2L), repo.ids(SongSortField.TITLE, asc = false))
        assertEquals(listOf(2L, 1L, 3L), repo.ids(SongSortField.ARTIST)) // "The Beatles" sorts as "beatles"
        assertEquals(listOf(2L, 3L, 1L), repo.ids(SongSortField.DURATION))
    }

    @Test
    fun `search is accent-insensitive, multi-term and escapes wildcards`() = runTest {
        val repo = repo()
        source.songs = listOf(
            song(1, title = "Café del Mar", artist = "Señor Sol", album = "Noches"),
            song(2, title = "Night Drive", artist = "Neon", album = "100% Pure"),
            song(3, title = "Morning", artist = "Cafe Crew", album = "Day"),
        )
        repo.rescan()
        assertEquals(setOf(1L, 3L), repo.search("cafe").songs.map { it.id }.toSet())
        assertEquals(listOf(1L), repo.search("cafe senor").songs.map { it.id })
        assertEquals(listOf(2L), repo.search("100%").songs.map { it.id })
        assertEquals(1L, repo.search("caf").songs.first().id) // title prefix ranks first
        assertEquals(listOf("Señor Sol"), repo.search("senor").artists.map { it.name })
        assertTrue(repo.search("   ").isEmpty)
    }

    @Test
    fun `folder rules hide songs without rescanning`() = runTest {
        val repo = repo()
        source.songs = listOf(song(1, path = "Music/"), song(2, path = "VoiceMemos/"), song(3, path = "Music/Podcasts/"))
        repo.rescan()
        assertEquals(3, repo.songCount.first())

        db.folderRuleDao().upsert(FolderRuleEntity("primary:voicememos/", "primary", "VoiceMemos/", "EXCLUDE", null, 0, true))
        repo.applyFolderRules()
        assertEquals(listOf(1L, 3L), repo.ids(SongSortField.TITLE))

        db.folderRuleDao().upsert(FolderRuleEntity("primary:music/podcasts/", "primary", "Music/Podcasts/", "EXCLUDE", null, 1, true))
        repo.applyFolderRules()
        assertEquals(listOf(1L), repo.ids(SongSortField.TITLE))
        assertEquals(1, source.scans) // never rescanned

        // Hidden songs stay cached, so lookups by id (queue restore) still work.
        assertEquals(3, repo.songsByIds(listOf(1, 2, 3)).size)
    }

    @Test
    fun `minimum duration setting filters in SQL`() = runTest {
        val repo = repo()
        source.songs = listOf(song(1, durationMs = 5_000), song(2, durationMs = 60_000), song(3, durationMs = 0))
        repo.rescan()
        settings.update { it.copy(minTrackDurationSec = 10) }
        // Unknown duration (0) is kept; MediaStore may not have parsed it yet.
        assertEquals(listOf(2L, 3L), repo.ids(SongSortField.TITLE))
    }

    @Test
    fun `albums artists and genres are grouped in SQL`() = runTest {
        val repo = repo()
        source.songs = listOf(
            song(1, artist = "A", album = "One", albumId = 10, genre = "Rock"),
            song(2, artist = "A", album = "One", albumId = 10, genre = "rock"),
            song(3, artist = "B", album = "Two", albumId = 20, genre = "Jazz"),
        )
        repo.rescan()
        val albums = repo.albums.first()
        assertEquals(setOf(10L to 2, 20L to 1), albums.map { it.id to it.songCount }.toSet())
        assertEquals(setOf("A" to 2, "B" to 1), repo.artists.first().map { it.name to it.songCount }.toSet())
        assertEquals(listOf(2, 1), repo.genres.first().sortedBy { it.name.lowercase() }.map { it.songCount }.reversed())
        assertEquals(listOf(1L, 2L), repo.albumSongs(10).first().map { it.id })
    }

    @Test
    fun `paged songs follow the requested order`() = runTest {
        val repo = repo()
        source.songs = (1L..250L).map { song(it, title = "T%03d".format(it)) }
        repo.rescan()
        val page = repo.pagedSongs(SortOrder(SongSortField.TITLE, ascending = false)).asSnapshot()
        assertTrue(page.size in 60..250)
        assertEquals("T250", page.first().title)
        assertEquals(page.sortedByDescending { it.title }, page)
    }

    @Test
    fun `scan can be cancelled and leaves the cache untouched`() = runTest {
        val repo = repo()
        source.songs = listOf(song(1))
        repo.rescan()

        source.songs = listOf(song(1), song(2), song(3))
        val gate = CompletableDeferred<Unit>()
        source.gate = gate
        val running = async(Dispatchers.Default) { repo.rescan() }
        withTimeout(5_000) { repo.scanState.filter { it.isScanning }.first() }
        repo.cancelScan()
        val result = running.await()
        assertTrue(result.cancelled)
        assertEquals(listOf(1L), repo.ids(SongSortField.TITLE))
        assertTrue(!repo.scanState.value.isScanning)
    }

    @Test
    fun `clearing the cache rebuilds it and keeps playlists attached`() = runTest {
        val repo = repo()
        source.songs = listOf(song(1), song(2))
        repo.rescan()
        val playlists = com.svara.app.data.repository.PlaylistRepositoryImpl(db.playlistDao(), settings, db.tx(), com.svara.app.data.repository.Clock { 0 })
        val pid = playlists.create("Keep")
        playlists.addSongs(pid, listOf(2))
        val r = repo.clearCacheAndRescan()
        assertEquals(2, r.added) // everything was re-read
        assertEquals(listOf(2L), playlists.observeItems(pid).first().map { it.song.id })
    }

    @Test
    fun `startup scan is skipped when nothing changed`() = runTest {
        val repo = repo()
        source.songs = listOf(song(1), song(2))
        assertEquals(2, repo.rescanIfStale()?.added)        // never scanned: runs
        assertEquals(null, repo.rescanIfStale())             // unchanged: skipped
        assertEquals(1, source.scans)
        source.songs = listOf(song(1), song(2), song(3))
        assertEquals(1, repo.rescanIfStale()?.added)        // MediaStore changed: runs
        assertEquals(2, source.scans)
    }

    @Test
    fun `removing missing songs drops their rows`() = runTest {
        val repo = repo()
        source.songs = listOf(song(1), song(2))
        repo.rescan()
        repo.removeSongs(listOf(2))
        assertEquals(listOf(1L), repo.ids(SongSortField.TITLE))
    }

    @Test
    fun `folder-scoped rescan only touches that folder`() = runTest {
        val repo = repo()
        source.songs = listOf(song(1, path = "Music/A/"), song(2, path = "Music/B/"))
        repo.rescan()
        // B's song disappears from MediaStore and A gets a new song; only A is rescanned.
        source.songs = listOf(song(1, path = "Music/A/"), song(3, path = "Music/A/"))
        val r = repo.rescanFolders(listOf(FolderKey("primary", "Music/A/")))
        assertEquals(1, r.added)
        assertEquals(0, r.removed) // song 2 is outside the scanned folder, so it stays
        assertEquals(listOf(1L, 2L, 3L), repo.ids(SongSortField.TITLE))
        assertEquals(listOf(FolderKey("primary", "Music/A/")), source.lastFolders)
    }

    @Test
    fun `system sound folders are hidden by default and shown when the setting is off`() = runTest {
        val repo = repo()
        source.songs = listOf(song(1, path = "Music/"), song(2, path = "Ringtones/"), song(3, path = "Recordings/Calls/"))
        repo.rescan()
        assertEquals(listOf(1L), repo.ids(SongSortField.TITLE))
        settings.update { it.copy(excludeSystemFolders = false) }
        repo.applyFolderRules()
        assertEquals(listOf(1L, 2L, 3L), repo.ids(SongSortField.TITLE))
    }

    @Test
    fun `downloads is a filter over the download folder`() = runTest {
        val repo = repo()
        source.songs = listOf(song(1, path = "Download/"), song(2, path = "Download/Albums/"), song(3, path = "Music/Download/"))
        repo.rescan()
        assertEquals(listOf(1L, 2L), repo.songIds(SortOrder(SongSortField.TITLE), SongFilter.DOWNLOADS))
        assertEquals(2, repo.songCount(SongFilter.DOWNLOADS).first())
    }

    @Test
    fun `duplicates group by title, artist and duration and flag identical sizes`() = runTest {
        val repo = repo()
        source.songs = listOf(
            song(1, title = "Same", artist = "X", durationMs = 200_400, size = 5000, path = "Music/A/"),
            song(2, title = "same", artist = "x", durationMs = 200_900, size = 5000, path = "Music/B/"),
            song(3, title = "Other", artist = "X", durationMs = 100_000, size = 7000),
            song(4, title = "Live", artist = "Y", durationMs = 300_000, size = 9000),
            song(5, title = "Live", artist = "Y", durationMs = 300_000, size = 4000),
        )
        repo.rescan()
        val groups = repo.duplicates().associateBy { it.songs.first().title.lowercase() }
        assertEquals(2, groups.size)
        assertTrue(groups.getValue("same").identicalSize)
        assertTrue(!groups.getValue("live").identicalSize)
    }

    @Test
    fun `search falls back to typo-tolerant matches`() = runTest {
        val repo = repo()
        source.songs = listOf(song(1, title = "Bohemian Rhapsody", artist = "Queen"), song(2, title = "Under Pressure", artist = "Queen"))
        repo.rescan()
        val exact = repo.search("bohem")
        assertEquals(listOf(1L), exact.songs.map { it.id })
        assertTrue(!exact.includesFuzzy)
        val typo = repo.search("bohemain rapsody")
        assertEquals(listOf(1L), typo.songs.map { it.id })
        assertTrue(typo.includesFuzzy)
        assertEquals(listOf("Queen"), repo.search("quen").artists.map { it.name })
    }
}
