package com.svara.app.data

import com.svara.app.data.db.SvaraDatabase
import com.svara.app.data.repository.Clock
import com.svara.app.data.repository.FavoritesRepositoryImpl
import com.svara.app.data.repository.HistoryRepositoryImpl
import com.svara.app.data.repository.PlaylistRepositoryImpl
import com.svara.app.data.repository.QueueSnapshotRepositoryImpl
import com.svara.app.data.repository.SmartPlaylistRepositoryImpl
import com.svara.app.domain.model.SmartRule
import kotlinx.coroutines.Dispatchers
import com.svara.app.domain.model.QueueSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserDataRepositoriesTest {

    private lateinit var db: SvaraDatabase
    private var now = 1_000L
    private val clock = Clock { now++ }
    private val settings = FakeSettings()

    @Before fun setUp() = runTest {
        db = inMemoryDb()
        db.songDao().upsert((1L..5L).map { song(it, title = "S$it") } + song(6, title = "Hidden").copy(visible = false))
    }

    @After fun tearDown() = db.close()

    private fun playlists() = PlaylistRepositoryImpl(db.playlistDao(), settings, db.tx(), clock)

    @Test
    fun `playlist add skips duplicates and keeps order`() = runTest {
        val repo = playlists()
        val id = repo.create("  Road trip ")
        assertEquals(3, repo.addSongs(id, listOf(3, 1, 3, 2)))
        assertEquals(1, repo.addSongs(id, listOf(1, 4)))
        assertEquals(listOf(3L, 1L, 2L, 4L), repo.observeItems(id).first().map { it.song.id })
        assertEquals("Road trip", repo.observePlaylist(id).first()?.name)
        assertEquals(4, repo.playlists.first().single().songCount)
    }

    @Test
    fun `playlist move and remove renumber positions`() = runTest {
        val repo = playlists()
        val id = repo.create("Mix")
        repo.addSongs(id, listOf(1, 2, 3, 4))
        repo.move(id, from = 0, to = 3)
        val items = repo.observeItems(id).first()
        assertEquals(listOf(2L, 3L, 4L, 1L), items.map { it.song.id })
        assertEquals(listOf(0, 1, 2, 3), items.map { it.position })

        repo.removeEntry(id, items[1].entryId)
        val after = repo.observeItems(id).first()
        assertEquals(listOf(2L, 4L, 1L), after.map { it.song.id })
        assertEquals(listOf(0, 1, 2), after.map { it.position })
        assertEquals(items[0].entryId, after[0].entryId) // stable ids for the UI
    }

    @Test
    fun `hidden songs are skipped but keep their absolute position`() = runTest {
        val repo = playlists()
        val id = repo.create("Mixed")
        repo.addSongs(id, listOf(1, 6, 2))
        val items = repo.observeItems(id).first()
        assertEquals(listOf(1L, 2L), items.map { it.song.id })
        assertEquals(listOf(0, 2), items.map { it.position })
    }

    @Test
    fun `undo of a removal puts the song back where it was`() = runTest {
        val repo = playlists()
        val id = repo.create("Undo")
        repo.addSongs(id, listOf(1, 2, 3))
        val removed = repo.observeItems(id).first()[1]
        repo.removeEntry(id, removed.entryId)
        repo.insertAt(id, removed.song.id, removed.position)
        assertEquals(listOf(1L, 2L, 3L), repo.observeItems(id).first().map { it.song.id })
    }

    private fun smart() = SmartPlaylistRepositoryImpl(
        db.smartPlaylistDao(), db.historyDao(), settings, db.tx(), Clock { 40L * 24 * 3600 * 1000 }, Dispatchers.Default,
    )

    @Test
    fun `smart playlist rules evaluate in SQL and respect the limit`() = runTest {
        val history = HistoryRepositoryImpl(db.historyDao(), settings)
        val day = 24L * 3600 * 1000
        history.recordPlay(1, 1 * day)   // played 39 days ago
        history.recordPlay(2, 35 * day)  // played 5 days ago
        history.recordPlay(2, 36 * day)
        history.recordPlay(3, 38 * day)
        val repo = smart()

        val notRecent = repo.create("Rediscover", SmartRule.NOT_PLAYED_RECENTLY, 10)
        // Song 1 was played longest ago; then never-played songs (4, 5) newest first.
        assertEquals(listOf(1L, 5L, 4L), repo.observeSongs(notRecent).first().map { it.id })

        val most = repo.create("Top", SmartRule.MOST_PLAYED, 10)
        assertEquals(2L, repo.observeSongs(most).first().first().id)

        val added = repo.create("New", SmartRule.RECENTLY_ADDED, 10)
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), repo.observeSongs(added).first().map { it.id }) // hidden song 6 excluded

        val limited = repo.create("Two", SmartRule.RECENTLY_ADDED, 2)
        assertEquals(2, repo.observeSongs(limited).first().size)
    }

    @Test
    fun `favorite artists rule follows hearted songs`() = runTest {
        db.songDao().upsert(listOf(song(10, title = "A1", artist = "Alpha"), song(11, title = "A2", artist = "Alpha"), song(12, title = "B1", artist = "Beta")))
        FavoritesRepositoryImpl(db.favoriteDao(), settings, clock).toggle(10)
        val repo = smart()
        val id = repo.create("Fav artists", SmartRule.FAVORITE_ARTISTS, 30)
        assertEquals(setOf(10L, 11L), repo.observeSongs(id).first().map { it.id }.toSet())
    }

    @Test
    fun `pinning freezes contents until unpinned`() = runTest {
        val repo = smart()
        val id = repo.create("Frozen", SmartRule.RECENTLY_ADDED, 10)
        repo.setPinned(id, true)
        db.songDao().upsert(listOf(song(99, title = "Newest", dateAdded = 999)))
        assertTrue(99L !in repo.observeSongs(id).first().map { it.id })
        repo.setPinned(id, false)
        assertEquals(99L, repo.observeSongs(id).first().first().id)
    }

    @Test
    fun `rename and delete playlists`() = runTest {
        val repo = playlists()
        val id = repo.create("Old")
        repo.addSongs(id, listOf(1))
        repo.rename(id, "New")
        assertEquals("New", repo.playlists.first().single().name)
        // Delete moves it to Recently deleted with its songs; restore brings it back unchanged.
        repo.delete(id)
        assertTrue(repo.playlists.first().isEmpty())
        assertEquals(listOf(id), repo.deleted.first().map { it.id })
        assertEquals(listOf(1L), db.playlistDao().getEntries(id).map { it.songId })
        repo.restore(id)
        assertEquals(listOf("New"), repo.playlists.first().map { it.name })
        assertTrue(repo.deleted.first().isEmpty())
        // Purge deletes for good.
        repo.delete(id)
        repo.purge(id)
        assertTrue(repo.deleted.first().isEmpty())
        assertTrue(db.playlistDao().getEntries(id).isEmpty()) // cascade
    }

    @Test
    fun `duplicate copies songs, order, description and cover, and old deletions expire after 30 days`() = runTest {
        val repo = playlists()
        val id = repo.create("Original")
        repo.addSongs(id, listOf(3, 1, 2))
        repo.setDescription(id, "  For the train  ")
        repo.setCover(id, 2)
        val copy = repo.duplicate(id, "Copy")
        assertEquals(listOf(3L, 1L, 2L), db.playlistDao().getEntries(copy).map { it.songId })
        val copied = db.playlistDao().get(copy)!!
        assertEquals("For the train", copied.description)
        assertEquals(2L, copied.coverSongId)

        repo.delete(id)
        assertEquals(0, repo.purgeExpired(clock.now() + 29L * 24 * 60 * 60 * 1000))
        assertEquals(1, repo.purgeExpired(clock.now() + 31L * 24 * 60 * 60 * 1000))
        assertTrue(repo.deleted.first().isEmpty())
        assertEquals(listOf("Copy"), repo.playlists.first().map { it.name })
    }

    @Test
    fun `favorites toggle and list newest first`() = runTest {
        val repo = FavoritesRepositoryImpl(db.favoriteDao(), settings, clock)
        repo.toggle(2)
        repo.toggle(5)
        repo.toggle(6) // hidden song: remembered but not listed
        assertEquals(setOf(2L, 5L, 6L), repo.favoriteIds.first())
        assertEquals(listOf(5L, 2L), repo.favorites().first().map { it.id })
        repo.toggle(2)
        assertFalse(2L in repo.favoriteIds.first())
    }

    @Test
    fun `history orders recent and most played`() = runTest {
        val repo = HistoryRepositoryImpl(db.historyDao(), settings)
        repo.recordPlay(1, 10)
        repo.recordPlay(2, 20)
        repo.recordPlay(1, 30)
        repo.recordPlay(3, 40)
        repo.recordPlay(1, 50)
        repo.recordPlay(3, 60)
        assertEquals(listOf(3L, 1L, 2L), repo.recentlyPlayed(10).first().map { it.id })
        assertEquals(listOf(1L to 3, 3L to 2, 2L to 1), repo.mostPlayed(10).first().map { it.song.id to it.count })
        assertEquals(listOf(3L), repo.recentlyPlayed(1).first().map { it.id })
        repo.clear()
        assertTrue(repo.recentlyPlayed(10).first().isEmpty())
    }

    @Test
    fun `reset statistics zeroes play counts but keeps recently played`() = runTest {
        val repo = HistoryRepositoryImpl(db.historyDao(), settings)
        repo.recordPlay(1, 10)
        repo.recordPlay(1, 20)
        repo.recordPlay(2, 30)
        settings.update { it.copy(statsSince = 100) }
        repo.recordPlay(3, 150)
        assertEquals(listOf(3L), repo.mostPlayed(10).first().map { it.song.id })
        assertEquals(listOf(3L, 2L, 1L), repo.recentlyPlayed(10).first().map { it.id })
    }

    @Test
    fun `queue snapshot round trips and position updates in place`() = runTest {
        val repo = QueueSnapshotRepositoryImpl(db.queueDao(), db.tx())
        assertEquals(QueueSnapshot.EMPTY, repo.load())
        val snap = QueueSnapshot(listOf(5, 3, 1), 1, 12_345, true, listOf(1, 2, 0), 2, 1.25f, 1f)
        repo.save(snap)
        assertEquals(snap, repo.load())
        repo.savePosition(2, 999)
        assertEquals(snap.copy(currentIndex = 2, positionMs = 999), repo.load())
        repo.save(snap.copy(songIds = listOf(7)))
        assertEquals(listOf(7L), repo.load().songIds)
    }
}
