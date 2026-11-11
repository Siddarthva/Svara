package com.svara.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.svara.app.data.db.Migrations
import com.svara.app.data.db.SvaraDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Builds a real version-1 database (the first schema SVARĀ shipped with) and opens it with the
 * current app database. Room validates the migrated schema against the current entities, so a
 * mistake in the migration SQL fails this test.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    private fun createVersion1() {
        context.deleteDatabase(name)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null)
        listOf(
            "CREATE TABLE songs (id INTEGER NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL, albumArtist TEXT, " +
                "albumId INTEGER NOT NULL, artistId INTEGER NOT NULL, durationMs INTEGER NOT NULL, trackNumber INTEGER NOT NULL, " +
                "discNumber INTEGER NOT NULL, year INTEGER NOT NULL, dateAddedSec INTEGER NOT NULL, dateModifiedSec INTEGER NOT NULL, " +
                "sizeBytes INTEGER NOT NULL, mimeType TEXT NOT NULL, genre TEXT, volume TEXT NOT NULL, relativePath TEXT NOT NULL, " +
                "fileName TEXT NOT NULL, dataPath TEXT, PRIMARY KEY(id))",
            "CREATE INDEX index_songs_albumId ON songs (albumId)",
            "CREATE INDEX index_songs_dateAddedSec ON songs (dateAddedSec)",
            "CREATE TABLE folder_rules (id TEXT NOT NULL, volume TEXT NOT NULL, path TEXT NOT NULL, type TEXT NOT NULL, treeUri TEXT, " +
                "addedAt INTEGER NOT NULL, accessGranted INTEGER NOT NULL, PRIMARY KEY(id))",
            "CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
            "CREATE TABLE playlist_entries (entryId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, playlistId INTEGER NOT NULL, songId INTEGER NOT NULL, " +
                "position INTEGER NOT NULL, FOREIGN KEY(playlistId) REFERENCES playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX index_playlist_entries_playlistId_position ON playlist_entries (playlistId, position)",
            "CREATE TABLE favorites (songId INTEGER NOT NULL, addedAt INTEGER NOT NULL, PRIMARY KEY(songId))",
            "CREATE TABLE play_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, songId INTEGER NOT NULL, playedAt INTEGER NOT NULL)",
            "CREATE INDEX index_play_events_songId ON play_events (songId)",
            "CREATE INDEX index_play_events_playedAt ON play_events (playedAt)",
            "CREATE TABLE queue_items (position INTEGER NOT NULL, songId INTEGER NOT NULL, PRIMARY KEY(position))",
            "CREATE TABLE playback_snapshot (id INTEGER NOT NULL, currentIndex INTEGER NOT NULL, positionMs INTEGER NOT NULL, " +
                "shuffleEnabled INTEGER NOT NULL, shuffleOrder TEXT NOT NULL, repeatMode INTEGER NOT NULL, speed REAL NOT NULL, pitch REAL NOT NULL, PRIMARY KEY(id))",
            "CREATE TABLE settings (`key` TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(`key`))",
            // User data that must survive.
            "INSERT INTO playlists (id, name, createdAt, updatedAt) VALUES (1, 'Road trip', 1, 1)",
            "INSERT INTO playlist_entries (playlistId, songId, position) VALUES (1, 101, 0), (1, 102, 1)",
            "INSERT INTO favorites (songId, addedAt) VALUES (101, 5)",
            "INSERT INTO play_events (songId, playedAt) VALUES (101, 10), (102, 20), (101, 30)",
            "INSERT INTO queue_items (position, songId) VALUES (0, 102), (1, 101)",
            "INSERT INTO playback_snapshot VALUES (0, 1, 42000, 0, '', 2, 1.0, 1.0)",
            "INSERT INTO settings (`key`, value) VALUES ('playback.skipSilence', 'true')",
            "INSERT INTO songs VALUES (101, 'Old', 'A', 'B', NULL, 1, 1, 1000, 1, 1, 0, 1, 1, 1, 'audio/mpeg', NULL, 'primary', 'Music/', 'old.mp3', NULL)",
        ).forEach(db::execSQL)
        db.version = 1
        db.close()
    }

    @Test
    fun `version 1 migrates to the current schema without losing user data`() = runTest {
        createVersion1()
        val db = Room.databaseBuilder(context, SvaraDatabase::class.java, name)
            .addMigrations(*Migrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            // Opening validates the migrated schema against the entities.
            assertEquals(listOf(101L, 102L), db.playlistDao().getEntries(1).map { it.songId })
            assertEquals("Road trip", db.playlistDao().allPlaylists().single().name)
            assertTrue(db.favoriteDao().isFavorite(101))
            assertEquals(listOf(102L, 101L), db.queueDao().getItems().map { it.songId })
            assertEquals(42_000L, db.queueDao().getSnapshot()?.positionMs)
            assertEquals("true", db.settingsDao().getAll().single { it.key == "playback.skipSilence" }.value)
            assertEquals(3, db.historyDao().observeEventCount().first())
            // The song cache starts empty and is refilled by the next scan.
            assertTrue(db.songDao().fingerprints().isEmpty())
            // New tables exist and work.
            assertTrue(db.smartPlaylistDao().observeAll().first().isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
