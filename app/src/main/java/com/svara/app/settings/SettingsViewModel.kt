package com.svara.app.settings

import com.svara.app.data.appearance.PaletteRepository
import com.svara.app.data.covers.CoverRepository
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.svara.app.data.db.SvaraDatabase
import com.svara.app.data.prefs.FlagsStore
import com.svara.app.data.db.HistoryDao
import com.svara.app.data.portability.Portability
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import com.svara.app.di.IoDispatcher
import com.svara.app.domain.model.AppSettings
import com.svara.app.domain.model.FolderKey
import com.svara.app.domain.model.FolderRule
import com.svara.app.domain.model.FolderRuleType
import com.svara.app.domain.model.LibraryStats
import com.svara.app.domain.model.SleepTimerOption
import com.svara.app.domain.repository.FolderRepository
import com.svara.app.domain.repository.HistoryRepository
import com.svara.app.domain.repository.LibraryRepository
import com.svara.app.domain.repository.SettingsRepository
import com.svara.app.playback.UserMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class StorageInfo(val databaseBytes: Long = 0, val artworkCacheBytes: Long = 0, val coverBytes: Long = 0, val paletteEntries: Int = 0)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val library: LibraryRepository,
    private val folders: FolderRepository,
    private val history: HistoryRepository,
    private val messages: UserMessages,
    private val portability: Portability,
    private val coverRepository: CoverRepository,
    private val palettes: PaletteRepository,
    historyDao: HistoryDao,
    flags: FlagsStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
    val stats: StateFlow<LibraryStats?> = library.stats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val rules: StateFlow<List<FolderRule>> = folders.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val lastScanAt: StateFlow<Long> = flags.lastScanAtMillis.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val isScanning: StateFlow<Boolean> = library.scanState.map { it.isScanning }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val historyEvents: StateFlow<Int> = historyDao.observeEventCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Whether the device has a fingerprint, face or screen lock the app lock can use. */
    val appLockAvailable: Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    private val _storage = MutableStateFlow(StorageInfo())
    val storage: StateFlow<StorageInfo> = _storage.asStateFlow()

    fun refreshStorage() {
        viewModelScope.launch {
            _storage.value = withContext(io) {
                StorageInfo(
                    databaseBytes = context.getDatabasePath(SvaraDatabase.NAME).let { db ->
                        listOf(db, java.io.File(db.path + "-wal"), java.io.File(db.path + "-shm")).sumOf { if (it.exists()) it.length() else 0L }
                    },
                    artworkCacheBytes = context.cacheDir.resolve("artwork").walkBottomUp().filter { it.isFile }.sumOf { it.length() },
                    coverBytes = coverRepository.totalBytes(),
                    paletteEntries = palettes.count(),
                )
            }
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    /** System-folder exclusion changes visibility, so re-apply folder rules (no rescan needed). */
    fun setExcludeSystemFolders(on: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(excludeSystemFolders = on) }
            library.applyFolderRules()
        }
    }

    fun exportBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            val json = portability.exportBackup()
            val written = withContext(io) {
                try {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) } != null
                } catch (e: Exception) {
                    false
                }
            }
            messages.emit(if (written) "Exported settings and playlists (no audio)" else "Couldn't save the export file")
        }
    }

    /** Restores a backup chosen by the user; nothing changes if the file isn't valid. */
    fun importBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            val text = withContext(io) { runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull() }
            if (text == null) { messages.emit("Couldn't read that file"); return@launch }
            val message = try {
                val r = portability.importBackup(text)
                "Restored ${r.playlists} playlists, ${r.favourites} favourites and ${r.plays} plays" +
                    if (r.notFound > 0) ". ${r.notFound} songs weren't found on this device" else ""
            } catch (e: com.svara.app.data.portability.BackupFormat.InvalidBackup) {
                e.message ?: "This file isn't a SVARĀ backup."
            } catch (e: Exception) {
                "Couldn't restore the backup"
            }
            messages.emit(message)
        }
    }

    /** The on-device crash log, newest first; empty when nothing has crashed. */
    fun crashLog(): String = com.svara.app.data.CrashLog.read(context)

    fun clearCrashLog() = com.svara.app.data.CrashLog.clear(context)

    fun exportCrashLog(uri: android.net.Uri) {
        viewModelScope.launch {
            val ok = withContext(io) { runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(crashLog().toByteArray()) } != null }.getOrDefault(false) }
            messages.emit(if (ok) "Crash log saved" else "Couldn't save the crash log")
        }
    }

    fun setDefaultSleepTimer(option: SleepTimerOption) = update { it.copy(defaultSleepTimer = option) }

    fun rescan() {
        viewModelScope.launch {
            val r = library.rescan()
            if (!r.cancelled) messages.emit("Library updated: ${r.total} songs (${r.added} new, ${r.removed} removed)")
        }
    }

    fun addFolder(treeUri: String, type: FolderRuleType) {
        viewModelScope.launch {
            val rule = folders.addFolder(treeUri, type)
            messages.emit(
                if (rule == null) "That location can't be used. Choose a folder on this device's storage or SD card."
                else "${if (type == FolderRuleType.INCLUDE) "Including" else "Excluding"} “${rule.key.displayName}”"
            )
        }
    }

    fun removeRule(key: FolderKey) {
        viewModelScope.launch { folders.removeRule(key) }
    }

    /** Removes every custom cover; the original artwork shows everywhere again. */
    fun clearCustomCovers() {
        viewModelScope.launch {
            coverRepository.clearAll()
            messages.emit("Custom covers cleared. The original artwork is back.")
            refreshStorage()
        }
    }

    fun clearArtworkCache() {
        viewModelScope.launch {
            withContext(io) { SingletonImageLoader.get(context).diskCache?.clear() }
            SingletonImageLoader.get(context).memoryCache?.clear()
            refreshStorage()
            messages.emit("Artwork cache cleared")
        }
    }

    /** Deletes all play history: recently played, most played and history-based smart playlists start over. */
    fun clearHistory() {
        viewModelScope.launch {
            history.clear()
            messages.emit("Listening history cleared")
        }
    }

    /** Zeroes play counts (most played) from now on, keeping the recently played list. */
    fun resetStatistics() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(statsSince = System.currentTimeMillis()) }
            messages.emit("Statistics reset")
        }
    }

    fun clearLibraryCache() {
        viewModelScope.launch {
            val r = library.clearCacheAndRescan()
            refreshStorage()
            if (!r.cancelled) messages.emit("Library cache rebuilt: ${r.total} songs")
        }
    }
}
