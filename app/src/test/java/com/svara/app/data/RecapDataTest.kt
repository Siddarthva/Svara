package com.svara.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.svara.app.appearance.CustomField
import com.svara.app.appearance.StudioSection
import com.svara.app.data.db.Migrations
import com.svara.app.data.db.PlayEventEntity
import com.svara.app.data.db.SvaraDatabase
import com.svara.app.data.repository.HistoryRepositoryImpl
import com.svara.app.data.repository.RecapRepository
import com.svara.app.data.repository.SettingsCodec
import com.svara.app.domain.model.AppSettings
import com.svara.app.domain.recap.RecapKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RecapDataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `version 6 gains the recap cache and keeps history and every user table`() = runTest {
        val name = "migration-v6-test.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/com.svara.app.data.db.SvaraDatabase/6.json").readText()).getJSONObject("database")
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
            "INSERT INTO play_events (songId, playedAt, listenedMs) VALUES (4, 10, 61000)",
            "INSERT INTO play_events (songId, playedAt, listenedMs) VALUES (5, 20, 0)",
            "INSERT INTO settings (`key`, value) VALUES ('eq.enabled', 'true')",
            "INSERT INTO saved_looks (id, name, createdAt, presetId, overrides, custom) VALUES (1, 'Mine', 2, 'TALA', 'GLOW=true', 'INTENSITY=80')",
        ).forEach(raw::execSQL)
        raw.version = 6
        raw.close()
        val db = Room.databaseBuilder(context, SvaraDatabase::class.java, name).addMigrations(*Migrations.ALL_MIGRATIONS).allowMainThreadQueries().build()
        try {
            assertEquals(2, db.historyDao().observeEventCount().first())
            assertEquals(61000L, db.historyDao().listenedBetween(0, 100))
            assertEquals("Quiet", db.playlistDao().get(1)?.description)
            assertEquals("true", db.settingsDao().getAll().associate { it.key to it.value }["eq.enabled"])
            assertEquals("INTENSITY=80", db.savedLookDao().get(1)?.custom)
            assertNull(db.historyDao().cachedRecap("WEEK:2026-10-19"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun `cache is reused, invalidated by new plays, and erased with history`() = runTest {
        val db = inMemoryDb()
        try {
            val settings = FakeSettings()
            val dao = db.historyDao()
            val recaps = RecapRepository(dao, settings)
            val period = recaps.current(RecapKind.YEAR).previous()
            val zone = java.time.ZoneId.systemDefault()
            val t = period.startMs(zone) + 86_400_000L
            repeat(5) { dao.insert(PlayEventEntity(songId = 1, playedAt = t + it * 1000, listenedMs = 60_000)) }
            assertEquals(5, recaps.recap(period).plays)
            assertNotNull("finished periods are cached", dao.cachedRecap(period.key))
            dao.insert(PlayEventEntity(songId = 2, playedAt = t + 9000, listenedMs = 60_000))
            assertEquals("a new play invalidates the cache", 6, recaps.recap(period).plays)

            HistoryRepositoryImpl(dao, settings).clear()
            assertNull("erasing history erases recaps", dao.cachedRecap(period.key))
            assertEquals(0, recaps.recap(period).plays)
            assertTrue(recaps.periods(RecapKind.YEAR).isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `statistics reset hides older plays from recaps`() = runTest {
        val db = inMemoryDb()
        try {
            val dao = db.historyDao()
            val settings = FakeSettings()
            val recaps = RecapRepository(dao, settings)
            val period = recaps.current(RecapKind.YEAR).previous()
            val t = period.startMs(java.time.ZoneId.systemDefault()) + 86_400_000L
            dao.insert(PlayEventEntity(songId = 1, playedAt = t, listenedMs = 60_000))
            dao.insert(PlayEventEntity(songId = 2, playedAt = t + 10_000, listenedMs = 60_000))
            settings.update { it.copy(statsSince = t + 5_000) }
            assertEquals(1, recaps.recap(period).plays)
        } finally {
            db.close()
        }
    }

    @Test
    fun `recap settings default on, persist, and new motion field resets with its section`() {
        val d = SettingsCodec.decode(emptyMap())
        assertTrue(d.recapAutoAdvance && d.recapHomeCard)
        val back = SettingsCodec.decode(SettingsCodec.encode(AppSettings(recapAutoAdvance = false, recapHomeCard = false)))
        assertEquals(false, back.recapAutoAdvance)
        assertEquals(false, back.recapHomeCard)
        assertEquals(StudioSection.MOTION, CustomField.RECAP_ANIMATIONS.section)
    }
}
