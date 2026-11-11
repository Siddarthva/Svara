package com.svara.app.data.repository

import com.svara.app.data.db.LyricsOverrideDao
import com.svara.app.data.db.LyricsOverrideEntity

import com.svara.app.domain.repository.LyricsOverrideRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Imported or edited lyrics and timing offsets, kept in SVARĀ's database; audio files are never touched. */
@Singleton
class LyricsOverrideRepositoryImpl @Inject constructor(
    private val dao: LyricsOverrideDao,
    private val clock: Clock,
) : LyricsOverrideRepository {

    override fun observe(songId: Long): Flow<LyricsOverrideEntity?> = dao.observe(songId)

    override suspend fun setText(songId: Long, text: String?) {
        val current = dao.get(songId)
        val cleaned = text?.takeIf { it.isNotBlank() }
        if (cleaned == null && (current?.offsetMs ?: 0L) == 0L) {
            dao.delete(songId)
            return
        }
        dao.upsert(LyricsOverrideEntity(songId, cleaned, current?.offsetMs ?: 0, clock.now()))
    }

    override suspend fun setOffset(songId: Long, offsetMs: Long) {
        val current = dao.get(songId)
        dao.upsert(LyricsOverrideEntity(songId, current?.text, offsetMs.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS), clock.now()))
    }

    override suspend fun clear(songId: Long) = dao.delete(songId)

    companion object {
        const val MAX_OFFSET_MS = 30_000L
    }
}
