package com.svara.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.svara.app.data.db.CoverOverrideEntity
import com.svara.app.data.db.Migrations
import com.svara.app.data.db.SvaraDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Builds a version-3 database from the exported schema (the v2 build's schema), fills it with
 * user data, and opens it with the current database. The v3 → v4 step (cover_override table,
 * palette dominant colour) must be additive: nothing the user has may change.
 */
@RunWith(RobolectricTestRunner::class)
class SchemaV3MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-v3-test.db"

    private fun createVersion3() {
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/com.svara.app.data.db.SvaraDatabase/3.json").readText()).getJSONObject("database")
        assertEquals(3, schema.getInt("version"))
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
            "INSERT INTO album_palettes (albumId, version, accent, muted) VALUES (12, 100, ${0xFF336699}, NULL)",
        ).forEach(db::execSQL)
        db.version = 3
        db.close()
    }

    @Test
    fun `version 3 gains custom covers without touching user data`() = runTest {
        createVersion3()
        val db = Room.databaseBuilder(context, SvaraDatabase::class.java, name)
            .addMigrations(*Migrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(listOf(4L, 5L), db.playlistDao().getEntries(1).map { it.songId })
            assertEquals("Late night", db.playlistDao().allPlaylists().single().name)
            assertTrue(db.favoriteDao().isFavorite(5))
            assertEquals(3, db.historyDao().observeEventCount().first())
            assertEquals(listOf(5L, 4L), db.queueDao().getItems().map { it.songId })
            assertEquals(15_000L, db.queueDao().getSnapshot()?.positionMs)
            assertEquals("6", db.settingsDao().getAll().associate { it.key to it.value }["playback.crossfadeSec"])
            assertEquals("Mine", db.savedLookDao().get(1)?.name)
            // Existing palette rows keep their colours; the new column starts empty.
            val palette = db.paletteDao().get(12)!!
            assertEquals(0xFF336699, palette.accent)
            assertNull(palette.dominant)

            // The new table works.
            db.coverOverrideDao().upsert(CoverOverrideEntity("ALBUM", 12, "/data/covers/album_12_1.jpg", 1, 3))
            assertEquals(1, db.coverOverrideDao().all().size)
            db.coverOverrideDao().delete("ALBUM", 12)
            assertTrue(db.coverOverrideDao().all().isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
