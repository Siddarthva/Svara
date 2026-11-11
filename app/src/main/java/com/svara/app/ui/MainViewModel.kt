package com.svara.app.ui

import com.svara.app.domain.model.SongSortField
import com.svara.app.domain.model.SortOrder
import com.svara.app.ui.navigation.CollectionType
import com.svara.app.ui.navigation.Routes
import com.svara.app.playback.PlaybackController
import com.svara.app.MainActivity
import com.svara.app.domain.repository.PlaylistRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svara.app.data.prefs.FlagsStore
import com.svara.app.appearance.NavTab
import kotlinx.coroutines.flow.first
import com.svara.app.domain.model.AppSettings
import com.svara.app.domain.repository.FolderRepository
import com.svara.app.domain.repository.LibraryRepository
import com.svara.app.domain.repository.SettingsRepository
import com.svara.app.playback.UiMessage
import com.svara.app.playback.UserMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val folders: FolderRepository,
    private val flags: FlagsStore,
    private val settingsRepository: SettingsRepository,
    private val playlists: PlaylistRepository,
    private val playback: PlaybackController,
    messages: UserMessages,
) : ViewModel() {

    private val _launchRoute = MutableStateFlow<String?>(null)
    /** A screen a widget, the tile or a shortcut asked for; the scaffold opens it once. */
    val launchRoute: StateFlow<String?> = _launchRoute.asStateFlow()

    fun consumeLaunchRoute() {
        _launchRoute.value = null
    }

    /** App shortcuts and widget taps. Playback goes through the same controller as the UI. */
    fun handleAction(action: String?) {
        when (action) {
            MainActivity.ACTION_OPEN_NOW_PLAYING -> _launchRoute.value = Routes.NOW_PLAYING
            MainActivity.ACTION_OPEN_QUEUE -> _launchRoute.value = Routes.QUEUE
            MainActivity.ACTION_RECENTLY_PLAYED -> _launchRoute.value = Routes.collection(CollectionType.RECENTLY_PLAYED)
            MainActivity.ACTION_SHUFFLE_ALL -> viewModelScope.launch {
                val ids = library.songIds(SortOrder(SongSortField.TITLE))
                if (ids.isNotEmpty()) {
                    playback.shuffleAll(ids)
                    _launchRoute.value = Routes.NOW_PLAYING
                }
            }
            MainActivity.ACTION_RESUME -> {
                playback.resume()
                _launchRoute.value = Routes.NOW_PLAYING
            }
        }
    }

    init {
        // Recently deleted playlists older than 30 days are removed for good.
        viewModelScope.launch { playlists.purgeExpired(System.currentTimeMillis()) }
    }

    /** null until the first check; then whether SVARĀ may read audio files. */
    private val _hasPermission = MutableStateFlow<Boolean?>(null)
    val hasPermission: StateFlow<Boolean?> = _hasPermission.asStateFlow()

    val onboardingComplete: StateFlow<Boolean?> = flags.onboardingComplete.map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val settings: StateFlow<AppSettings> = settingsRepository.settings
    val messages: SharedFlow<UiMessage> = messages.messages

    /** App lock: unlocked for the rest of this foreground session once the user authenticates. */
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private var startupScanDone = false

    fun onUnlocked() {
        _unlocked.value = true
    }

    /** Called when the app leaves the foreground. */
    fun lock() {
        _unlocked.value = false
    }

    /** The tab saved at the last tab change; null when none was saved. */
    suspend fun savedTab(): NavTab? = flags.lastTab.first()?.let { name -> NavTab.entries.firstOrNull { it.name == name } }

    /** Where SVARĀ opens: the chosen start screen, or the last tab when set to "Where I left off". */
    suspend fun startTab(): NavTab? {
        val chosen = settingsRepository.current().startScreen
        return NavTab.entries.firstOrNull { it.name == chosen } ?: savedTab()
    }

    fun rememberSearch(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        viewModelScope.launch {
            settingsRepository.update { s -> s.copy(recentSearches = (listOf(q) + s.recentSearches.filterNot { it.equals(q, ignoreCase = true) }).take(AppSettings.RECENT_SEARCH_LIMIT)) }
        }
    }

    fun rememberTab(tab: NavTab) {
        viewModelScope.launch { flags.setLastTab(tab.name) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { flags.setOnboardingComplete() }
    }

    /**
     * Called on every resume, so revoking access in system settings is noticed immediately.
     * At app start the library is rescanned only if MediaStore changed since the last scan;
     * otherwise only on pull-to-refresh.
     */
    fun onPermissionChecked(granted: Boolean) {
        _hasPermission.value = granted
        if (!granted) return
        viewModelScope.launch {
            folders.refreshAccess()
            if (!startupScanDone) {
                startupScanDone = true
                val result = library.rescanIfStale()
                if (result != null && !result.cancelled) flags.setLastScanAt(System.currentTimeMillis())
            }
        }
    }
}
