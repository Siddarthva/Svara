package com.svara.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.withTransaction

@Database(
    entities = [
        SongEntity::class,
        FolderRuleEntity::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        FavoriteEntity::class,
        PlayEventEntity::class,
        QueueItemEntity::class,
        PlaybackSnapshotEntity::class,
        SettingEntity::class,
        SmartPlaylistEntity::class,
        SmartPinEntity::class,
        SavedLookEntity::class,
        AlbumPaletteEntity::class,
        CoverOverrideEntity::class,
        LyricsOverrideEntity::class,
        MetadataOverrideEntity::class,
        RecapCacheEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class SvaraDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun folderRuleDao(): FolderRuleDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun queueDao(): QueueDao
    abstract fun settingsDao(): SettingsDao
    abstract fun smartPlaylistDao(): SmartPlaylistDao
    abstract fun savedLookDao(): SavedLookDao
    abstract fun paletteDao(): PaletteDao
    abstract fun coverOverrideDao(): CoverOverrideDao
    abstract fun lyricsOverrideDao(): LyricsOverrideDao
    abstract fun metadataOverrideDao(): MetadataOverrideDao

    companion object {
        const val NAME = "svara.db"
    }
}

/** Abstraction over database transactions so repositories can be unit tested with fakes. */
interface TransactionRunner {
    suspend fun <R> run(block: suspend () -> R): R
}

class RoomTransactionRunner(private val db: SvaraDatabase) : TransactionRunner {
    override suspend fun <R> run(block: suspend () -> R): R = db.withTransaction { block() }
}
