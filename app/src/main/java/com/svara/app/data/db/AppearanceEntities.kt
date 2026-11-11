package com.svara.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A named Theme Studio configuration: a preset plus the user's overrides. */
@Entity(tableName = "saved_looks")
data class SavedLookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val presetId: String,
    /** Encoded with ThemeConfigCodec ("FIELD=value;…"); never a full preset copy. */
    val overrides: String,
    /** v6: Customization overrides (Now Playing style, visualiser, navigation, motion…); null for looks saved before v6. */
    val custom: String? = null,
)

/**
 * Colours extracted from a cover's 128 px image. [albumId] is the cover identity's key (album id,
 * or minus the song id for song covers); [version] changes whenever that cover changes.
 */
@Entity(tableName = "album_palettes")
data class AlbumPaletteEntity(
    @PrimaryKey val albumId: Long,
    val version: Long,
    /** ARGB of the most useful swatch, or null when the art has no usable colour. */
    val accent: Long?,
    val muted: Long?,
    /** The dominant colour (v4); null in rows written before it existed. */
    val dominant: Long? = null,
)

@Dao
interface SavedLookDao {
    @Query("SELECT * FROM saved_looks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedLookEntity>>

    @Query("SELECT * FROM saved_looks WHERE id = :id")
    suspend fun get(id: Long): SavedLookEntity?

    @Insert
    suspend fun insert(look: SavedLookEntity): Long

    @Query("UPDATE saved_looks SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM saved_looks WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PaletteDao {
    @Query("SELECT * FROM album_palettes WHERE albumId = :albumId")
    suspend fun get(albumId: Long): AlbumPaletteEntity?

    @Upsert
    suspend fun upsert(palette: AlbumPaletteEntity)

    @Query("DELETE FROM album_palettes")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM album_palettes")
    suspend fun count(): Int
}
