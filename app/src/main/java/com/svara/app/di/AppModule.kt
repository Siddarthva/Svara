package com.svara.app.di

import android.content.Context
import androidx.room.Room
import com.svara.app.data.db.FavoriteDao
import com.svara.app.data.db.FolderRuleDao
import com.svara.app.data.db.HistoryDao
import com.svara.app.data.db.Migrations
import com.svara.app.data.db.PlaylistDao
import com.svara.app.data.db.QueueDao
import com.svara.app.data.db.RoomTransactionRunner
import com.svara.app.data.db.SettingsDao
import com.svara.app.data.db.SmartPlaylistDao
import com.svara.app.data.db.SavedLookDao
import com.svara.app.data.db.PaletteDao
import com.svara.app.data.appearance.AppearanceStore
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.svara.app.data.db.SongDao
import com.svara.app.data.db.SvaraDatabase
import com.svara.app.data.db.TransactionRunner
import com.svara.app.data.lyrics.LocalLyricsRepository
import com.svara.app.data.mediastore.AudioSource
import com.svara.app.data.mediastore.MediaStoreAudioSource
import com.svara.app.data.repository.Clock
import com.svara.app.data.repository.FavoritesRepositoryImpl
import com.svara.app.data.repository.FolderRepositoryImpl
import com.svara.app.data.repository.HistoryRepositoryImpl
import com.svara.app.data.repository.LibraryRepositoryImpl
import com.svara.app.data.repository.PlaylistRepositoryImpl
import com.svara.app.data.repository.QueueSnapshotRepositoryImpl
import com.svara.app.data.repository.SettingsRepositoryImpl
import com.svara.app.data.repository.SmartPlaylistRepositoryImpl
import com.svara.app.domain.repository.FavoritesRepository
import com.svara.app.domain.repository.FolderRepository
import com.svara.app.domain.repository.HistoryRepository
import com.svara.app.domain.repository.LibraryRepository
import com.svara.app.domain.repository.LyricsRepository
import com.svara.app.domain.repository.PlaylistRepository
import com.svara.app.domain.repository.QueueSnapshotRepository
import com.svara.app.domain.repository.SettingsRepository
import com.svara.app.domain.repository.SmartPlaylistRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ScanDispatcher
    fun scanDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    @Provides
    @Singleton
    @RulesDispatcher
    fun rulesDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    @Provides
    @Singleton
    @PaletteDispatcher
    fun paletteDispatcher(): CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    fun clock(): Clock = Clock { System.currentTimeMillis() }

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): SvaraDatabase =
        Room.databaseBuilder(context, SvaraDatabase::class.java, SvaraDatabase.NAME)
            // Explicit migrations only: an update must never wipe playlists, favorites or history.
            .addMigrations(*Migrations.ALL_MIGRATIONS)
            .build()

    @Provides
    @Singleton
    fun transactionRunner(db: SvaraDatabase): TransactionRunner = RoomTransactionRunner(db)

    @Provides fun songDao(db: SvaraDatabase): SongDao = db.songDao()
    @Provides fun folderRuleDao(db: SvaraDatabase): FolderRuleDao = db.folderRuleDao()
    @Provides fun playlistDao(db: SvaraDatabase): PlaylistDao = db.playlistDao()
    @Provides fun favoriteDao(db: SvaraDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun historyDao(db: SvaraDatabase): HistoryDao = db.historyDao()
    @Provides fun queueDao(db: SvaraDatabase): QueueDao = db.queueDao()
    @Provides fun settingsDao(db: SvaraDatabase): SettingsDao = db.settingsDao()
    @Provides fun smartPlaylistDao(db: SvaraDatabase): SmartPlaylistDao = db.smartPlaylistDao()
    @Provides fun savedLookDao(db: SvaraDatabase): SavedLookDao = db.savedLookDao()
    @Provides fun paletteDao(db: SvaraDatabase): PaletteDao = db.paletteDao()
    @Provides fun coverOverrideDao(db: SvaraDatabase): com.svara.app.data.db.CoverOverrideDao = db.coverOverrideDao()
    @Provides fun lyricsOverrideDao(db: SvaraDatabase): com.svara.app.data.db.LyricsOverrideDao = db.lyricsOverrideDao()
    @Provides fun metadataOverrideDao(db: SvaraDatabase): com.svara.app.data.db.MetadataOverrideDao = db.metadataOverrideDao()

    /** Appearance overrides (theme + studios). Only values that differ from defaults are stored. */
    @Provides
    @Singleton
    @AppearanceStore
    fun appearanceStore(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = { context.preferencesDataStoreFile("svara_appearance") },
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun audioSource(impl: MediaStoreAudioSource): AudioSource
    @Binds abstract fun library(impl: LibraryRepositoryImpl): LibraryRepository
    @Binds abstract fun folders(impl: FolderRepositoryImpl): FolderRepository
    @Binds abstract fun playlists(impl: PlaylistRepositoryImpl): PlaylistRepository
    @Binds abstract fun favorites(impl: FavoritesRepositoryImpl): FavoritesRepository
    @Binds abstract fun smartPlaylists(impl: SmartPlaylistRepositoryImpl): SmartPlaylistRepository
    @Binds abstract fun history(impl: HistoryRepositoryImpl): HistoryRepository
    @Binds abstract fun settings(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds abstract fun queueSnapshots(impl: QueueSnapshotRepositoryImpl): QueueSnapshotRepository
    @Binds abstract fun lyrics(impl: LocalLyricsRepository): LyricsRepository
    @Binds abstract fun lyricsOverrides(impl: com.svara.app.data.repository.LyricsOverrideRepositoryImpl): com.svara.app.domain.repository.LyricsOverrideRepository
}
