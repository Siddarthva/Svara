package com.svara.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.svara.app.data.db.AlbumPaletteEntity
import com.svara.app.data.db.Migrations
import com.svara.app.data.db.SavedLookEntity
import com.svara.app.data.db.SvaraDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Builds a version-2 database from the exported schema (app/schemas/…/2.json, the schema the
 * previous release shipped), fills it with user data, and opens it with the current database.
 * Room validates the migrated schema against the entities; the v2 → v3 step must be additive.
 */
@RunWith(RobolectricTestRunner::class)
class SchemaV2MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-v2-test.db"

    private fun createVersion2() {
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/com.svara.app.data.db.SvaraDatabase/2.json").readText()).getJSONObject("database")
        assertEquals(2, schema.getInt("version"))
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
            "INSERT INTO playlists (id, name, createdAt, updatedAt) VALUES (1, 'Evening', 1, 1)",
            "INSERT INTO playlist_entries (playlistId, songId, position) VALUES (1, 7, 0), (1, 8, 1), (1, 9, 2)",
            "INSERT INTO favorites (songId, addedAt) VALUES (8, 5), (9, 6)",
            "INSERT INTO play_events (songId, playedAt) VALUES (7, 10), (8, 20)",
            "INSERT INTO queue_items (position, songId) VALUES (0, 9), (1, 7)",
            "INSERT INTO playback_snapshot VALUES (0, 1, 61000, 1, '1,0', 1, 1.25, 1.0)",
            "INSERT INTO settings (`key`, value) VALUES ('playback.crossfadeSec', '4'), ('library.scanFingerprint', 'v:1:2:3')",
            "INSERT INTO smart_playlists (id, name, rule, limitCount, pinned, createdAt, updatedAt) VALUES (1, 'Short', '{}', 50, 1, 1, 1)",
        ).forEach(db::execSQL)
        db.version = 2
        db.close()
    }

    @Test
    fun `version 2 gains saved looks and palettes without touching user data`() = runTest {
        createVersion2()
        val db = Room.databaseBuilder(context, SvaraDatabase::class.java, name)
            .addMigrations(*Migrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(listOf(7L, 8L, 9L), db.playlistDao().getEntries(1).map { it.songId })
            assertEquals("Evening", db.playlistDao().allPlaylists().single().name)
            assertTrue(db.favoriteDao().isFavorite(8) && db.favoriteDao().isFavorite(9))
            assertEquals(2, db.historyDao().observeEventCount().first())
            assertEquals(listOf(9L, 7L), db.queueDao().getItems().map { it.songId })
            val snapshot = db.queueDao().getSnapshot()
            assertEquals(61_000L, snapshot?.positionMs)
            assertEquals(1.25f, snapshot?.speed)
            val settings = db.settingsDao().getAll().associate { it.key to it.value }
            assertEquals("4", settings["playback.crossfadeSec"])
            assertEquals("v:1:2:3", settings["library.scanFingerprint"])
            assertEquals("Short", db.smartPlaylistDao().observeAll().first().single().name)

            // The new tables work.
            val id = db.savedLookDao().insert(SavedLookEntity(name = "Mine", createdAt = 3, presetId = "SHRUTI", overrides = "GLOW=true"))
            assertEquals("Mine", db.savedLookDao().get(id)?.name)
            db.paletteDao().upsert(AlbumPaletteEntity(albumId = 5, version = 1, accent = 0xFF336699, muted = null))
            assertEquals(1, db.paletteDao().count())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
