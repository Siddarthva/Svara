package com.svara.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A cover chosen in SVARĀ for one song or a whole album. The image lives in app-private storage
 * ([path]); the audio file is never touched. [version] changes on every change, and is part of
 * every cache key for this cover.
 */
@Entity(tableName = "cover_override", primaryKeys = ["scope", "key"])
data class CoverOverrideEntity(
    /** "SONG" or "ALBUM". */
    val scope: String,
    /** Song id or album id. */
    val key: Long,
    val path: String,
    val version: Long,
    val createdAt: Long,
)

@Dao
interface CoverOverrideDao {
    @Query("SELECT * FROM cover_override")
    fun observeAll(): Flow<List<CoverOverrideEntity>>

    @Query("SELECT * FROM cover_override")
    suspend fun all(): List<CoverOverrideEntity>

    @Query("SELECT * FROM cover_override WHERE scope = :scope AND `key` = :key")
    suspend fun get(scope: String, key: Long): CoverOverrideEntity?

    @Upsert
    suspend fun upsert(cover: CoverOverrideEntity)

    @Query("DELETE FROM cover_override WHERE scope = :scope AND `key` = :key")
    suspend fun delete(scope: String, key: Long)

    @Query("DELETE FROM cover_override")
    suspend fun clear()
}
