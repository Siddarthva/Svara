package com.svara.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
 * Version 5 (Phase 2 schema) full of user data, opened with the current database: the v5 → v6
 * step only adds a column to saved looks, and every user table survives.
 */
@RunWith(RobolectricTestRunner::class)
class SchemaV5MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-v5-test.db"

    @Test
    fun `version 5 gains saved-look customization without touching user data`() = runTest {
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/com.svara.app.data.db.SvaraDatabase/5.json").readText()).getJSONObject("database")
        assertEquals(5, schema.getInt("version"))
        val raw = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            raw.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) raw.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        listOf(
            "INSERT INTO playlists (id, name, createdAt, updatedAt, description) VALUES (1, 'Late night', 1, 1, 'Quiet')",
            "INSERT INTO playlist_entries (playlistId, songId, position) VALUES (1, 4, 0)",
            "INSERT INTO favorites (songId, addedAt) VALUES (4, 9)",
            "INSERT INTO play_events (songId, playedAt, listenedMs) VALUES (4, 10, 61000)",
            "INSERT INTO queue_items (position, songId) VALUES (0, 4)",
            "INSERT INTO playback_snapshot VALUES (0, 0, 9000, 0, '', 0, 1.0, 1.0)",
            "INSERT INTO settings (`key`, value) VALUES ('eq.enabled', 'true')",
            "INSERT INTO saved_looks (id, name, createdAt, presetId, overrides) VALUES (1, 'Mine', 2, 'TALA', 'GLOW=true')",
            "INSERT INTO cover_override (scope, `key`, path, version, createdAt) VALUES ('ALBUM', 12, '/covers/a.jpg', 1, 3)",
            "INSERT INTO lyrics_override (songId, text, offsetMs, updatedAt) VALUES (4, '[00:01.00]Hi', 200, 5)",
            "INSERT INTO metadata_override (songId, field, value, original, createdAt) VALUES (4, 'ARTIST', 'Fixed', '', 6)",
        ).forEach(raw::execSQL)
        raw.version = 5
        raw.close()

        val db = Room.databaseBuilder(context, SvaraDatabase::class.java, name).addMigrations(*Migrations.ALL_MIGRATIONS).allowMainThreadQueries().build()
        try {
            assertEquals("Quiet", db.playlistDao().get(1)?.description)
            assertEquals(listOf(4L), db.playlistDao().getEntries(1).map { it.songId })
            assertTrue(db.favoriteDao().isFavorite(4))
            assertEquals(1, db.historyDao().observeEventCount().first())
            assertEquals(listOf(4L), db.queueDao().getItems().map { it.songId })
            assertEquals("true", db.settingsDao().getAll().associate { it.key to it.value }["eq.enabled"])
            val look = db.savedLookDao().get(1)!!
            assertEquals("Mine", look.name)
            assertNull("old looks have no customization", look.custom)
            assertEquals("/covers/a.jpg", db.coverOverrideDao().get("ALBUM", 12)?.path)
            assertEquals(200L, db.lyricsOverrideDao().get(4)?.offsetMs)
            assertEquals("Fixed", db.metadataOverrideDao().get(4, "ARTIST")?.value)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
