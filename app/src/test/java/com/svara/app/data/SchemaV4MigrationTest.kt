package com.svara.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.svara.app.data.db.LyricsOverrideEntity
import com.svara.app.data.db.MetadataOverrideEntity
import com.svara.app.data.db.Migrations
import com.svara.app.data.db.SvaraDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Builds a version-4 database (the v2.1 schema) full of user data and opens it with the current
 * database. The v4 → v5 step adds listening time, playlist description / cover / recently
 * deleted, smart-playlist rules, imported lyrics and metadata overrides. It must be additive:
 * playlists, favourites, history, queue, settings, covers and saved looks all survive.
 */
@RunWith(RobolectricTestRunner::class)
class SchemaV4MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-v4-test.db"

    private fun createVersion4() {
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/com.svara.app.data.db.SvaraDatabase/4.json").readText()).getJSONObject("database")
        assertEquals(4, schema.getInt("version"))
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        listOf(
            "INSERT INTO playlists (id, name, createdAt, updatedAt) VALUES (1, 'Late night', 1, 1)",
            "INSERT INTO playlist_entries (playlistId, songId, position) VALUES (1, 4, 0), (1, 5, 1)",
            "INSERT INTO favorites (songId, addedAt) VALUES (5, 9)",
            "INSERT INTO play_events (songId, playedAt) VALUES (4, 10), (5, 20), (5, 30)",
            "INSERT INTO queue_items (position, songId) VALUES (0, 5), (1, 4)",
            "INSERT INTO playback_snapshot VALUES (0, 0, 15000, 0, '', 0, 1.0, 1.0)",
            "INSERT INTO settings (`key`, value) VALUES ('playback.crossfadeSec', '6')",
            "INSERT INTO saved_looks (id, name, createdAt, presetId, overrides) VALUES (1, 'Mine', 2, 'TALA', 'GLOW=true')",
            "INSERT INTO album_palettes (albumId, version, accent, muted, dominant) VALUES (12, 100, ${0xFF336699}, NULL, ${0xFF112233})",
            "INSERT INTO cover_override (scope, `key`, path, version, createdAt) VALUES ('ALBUM', 12, '/covers/album_12_1.jpg', 1, 3)",
            "INSERT INTO smart_playlists (id, name, rule, limitCount, pinned, createdAt, updatedAt) VALUES (7, 'Fresh', 'RECENTLY_ADDED', 30, 0, 1, 1)",
        ).forEach(db::execSQL)
        db.version = 4
        db.close()
    }

    @Test
    fun `version 4 gains core listening tables and columns without touching user data`() = runTest {
        createVersion4()
        val db = Room.databaseBuilder(context, SvaraDatabase::class.java, name)
            .addMigrations(*Migrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            // Everything the user had.
            assertEquals(listOf(4L, 5L), db.playlistDao().getEntries(1).map { it.songId })
            val playlist = db.playlistDao().get(1)!!
            assertEquals("Late night", playlist.name)
            assertNull("existing playlists aren't deleted", playlist.deletedAt)
            assertNull(playlist.description)
            assertNull(playlist.coverSongId)
            assertEquals(listOf("Late night"), db.playlistDao().observeAll().first().map { it.name })
            assertTrue(db.favoriteDao().isFavorite(5))
            assertEquals(3, db.historyDao().observeEventCount().first())
            assertEquals(listOf(5L, 4L), db.queueDao().getItems().map { it.songId })
            assertEquals(15_000L, db.queueDao().getSnapshot()?.positionMs)
            assertEquals("6", db.settingsDao().getAll().associate { it.key to it.value }["playback.crossfadeSec"])
            assertEquals("Mine", db.savedLookDao().get(1)?.name)
            assertEquals("/covers/album_12_1.jpg", db.coverOverrideDao().get("ALBUM", 12)?.path)
            assertEquals(0xFF112233, db.paletteDao().get(12)?.dominant)
            val smart = db.smartPlaylistDao().get(7)!!
            assertEquals("RECENTLY_ADDED", smart.rule)
            assertNull("old smart playlists keep their preset rule", smart.rules)
            // Plays recorded before v5 have no listening time.
            assertNull(db.historyDao().observeFirstTimedPlay().first())

            // The new columns and tables work.
            db.playlistDao().setDescription(1, "Quiet ones", 5)
            db.playlistDao().setDeleted(1, 6)
            assertEquals("Quiet ones", db.playlistDao().get(1)?.description)
            assertTrue(db.playlistDao().observeAll().first().isEmpty())
            assertEquals(listOf(1L), db.playlistDao().observeDeleted().first().map { it.id })
            db.playlistDao().setDeleted(1, null)
            assertFalse(db.playlistDao().observeAll().first().isEmpty())

            db.lyricsOverrideDao().upsert(LyricsOverrideEntity(4, "[00:01.00]Hello", 250, 7))
            assertEquals(250L, db.lyricsOverrideDao().get(4)?.offsetMs)
            db.metadataOverrideDao().upsert(MetadataOverrideEntity(4, "ARTIST", "Fixed", "fixed", 8))
            assertEquals("fixed", db.metadataOverrideDao().get(4, "ARTIST")?.original)
            val id = db.historyDao().insertReturningId(com.svara.app.data.db.PlayEventEntity(songId = 4, playedAt = 40))
            db.historyDao().setListened(id, 61_000)
            assertEquals(40L, db.historyDao().observeFirstTimedPlay().first())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
