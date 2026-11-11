package com.svara.app.nowplaying

import com.svara.app.domain.repository.LyricsOverrideRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svara.app.domain.model.Lyrics
import com.svara.app.domain.model.Song
import com.svara.app.domain.model.AudioInfo
import com.svara.app.domain.model.OutputDeviceInfo
import com.svara.app.data.audioinfo.AudioInfoRepository
import com.svara.app.domain.repository.PlaylistRepository
import com.svara.app.playback.UserMessages
import kotlinx.coroutines.flow.asStateFlow
import com.svara.app.domain.model.SleepTimerOption
import com.svara.app.domain.repository.FavoritesRepository
import com.svara.app.domain.repository.LibraryRepository
import com.svara.app.domain.repository.LyricsRepository
import com.svara.app.domain.repository.SettingsRepository
import com.svara.app.playback.PlaybackController
import com.svara.app.playback.PlaybackProgress
import com.svara.app.playback.PlaybackState
import com.svara.app.playback.SleepTimerState
import com.svara.app.playback.analysis.AudioAnalysis
import com.svara.app.playback.analysis.AudioBands
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LyricsUi {
    data object Hidden : LyricsUi
    data object Loading : LyricsUi
    data object NotFound : LyricsUi
    data class Found(val lyrics: Lyrics) : LyricsUi
}

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playback: PlaybackController,
    private val favorites: FavoritesRepository,
    private val library: LibraryRepository,
    private val lyricsRepository: LyricsRepository,
    private val audioInfo: AudioInfoRepository,
    private val settingsRepository: SettingsRepository,
    private val lyricsOverrides: LyricsOverrideRepository,
) : ViewModel() {
    private val settings = settingsRepository

    val state: StateFlow<PlaybackState> = playback.state
    /** The seek bar needs smooth movement: 4 updates a second, only while playing and visible. */
    val progress: StateFlow<PlaybackProgress> = playback.progress(SEEK_BAR_INTERVAL_MS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), PlaybackProgress())
    val sleepTimer: StateFlow<SleepTimerState> = playback.sleepTimerState

    private val currentSongId = state.map { it.current?.songId }.distinctUntilChanged()

    val isFavorite: StateFlow<Boolean> = combine(currentSongId, favorites.favoriteIds) { id, ids -> id != null && id in ids }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val defaultSleepTimer: StateFlow<SleepTimerOption> = settings.settings.map { it.defaultSleepTimer }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SleepTimerOption.MIN_30)

    val leftHanded: StateFlow<Boolean> = settings.settings.map { it.leftHanded }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val preservePitch: StateFlow<Boolean> = settings.settings.map { it.preservePitch }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** The current song's full row (year, genre) for credits; null while loading. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSong: StateFlow<Song?> = currentSongId
        .flatMapLatest { id -> flow { emit(id?.let { library.songById(it) }) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "FLAC", "MP3 320"…: read from the file once per track, only while Now Playing is open. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val qualityBadge: StateFlow<String?> = currentSongId
        .flatMapLatest { id ->
            flow<String?> {
                emit(null)
                val song = id?.let { library.songById(it) } ?: return@flow
                emit(runCatching { AudioInfoRepository.badge(audioInfo.fileInfo(song)) }.getOrNull())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _info = MutableStateFlow<Pair<AudioInfo, List<OutputDeviceInfo>>?>(null)
    /** File properties and output devices for the Audio Information panel; null while loading. */
    val info: StateFlow<Pair<AudioInfo, List<OutputDeviceInfo>>?> = _info.asStateFlow()

    fun loadAudioInfo() {
        _info.value = null
        val id = state.value.current?.songId ?: return
        viewModelScope.launch {
            val song = library.songById(id) ?: return@launch
            _info.value = audioInfo.fileInfo(song) to audioInfo.outputDevices()
        }
    }

    fun setPreservePitch(on: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(preservePitch = on) }
            playback.setSpeed(state.value.speed)
        }
    }

    private val showLyrics = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val lyrics: StateFlow<LyricsUi> = combine(currentSongId, showLyrics) { id, show -> id to show }
        .flatMapLatest { (id, show) ->
            if (!show || id == null) flowOf<LyricsUi>(LyricsUi.Hidden)
            else lyricsOverrides.observe(id).flatMapLatest {
                flow<LyricsUi> {
                    emit(LyricsUi.Loading)
                    val song = library.songById(id)
                    val found = song?.let { lyricsRepository.lyricsFor(it) }
                    emit(if (found != null) LyricsUi.Found(found) else LyricsUi.NotFound)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsUi.Hidden)

    // A double tap acts once.
    private val taps = com.svara.app.ui.TapGuard()
    fun togglePlayPause() = taps.run("play") { playback.togglePlayPause() }
    fun skipNext() = taps.run("next") { playback.skipNext() }
    fun skipPrevious() = taps.run("previous") { playback.skipPrevious() }
    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)
    fun seekBack() = playback.seekBack()
    fun seekForward() = playback.seekForward()
    fun toggleShuffle() = taps.run("shuffle") { playback.toggleShuffle() }
    fun cycleRepeat() = taps.run("repeat") { playback.cycleRepeat() }
    fun skipToQueuePosition(position: Int) = playback.skipToQueuePosition(position)
    fun setSpeed(speed: Float) = playback.setSpeed(speed)
    fun startSleepTimer(option: SleepTimerOption) = playback.startSleepTimer(option)
    fun startSleepTimerMinutes(minutes: Int) {
        playback.startSleepTimerMinutes(minutes)
        viewModelScope.launch { settingsRepository.update { it.copy(sleepCustomMinutes = minutes) } }
    }
    val sleepCustomMinutes: StateFlow<Int> = settings.settings.map { it.sleepCustomMinutes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 45)
    val sleepFadeOut: StateFlow<Boolean> = settings.settings.map { it.sleepFadeOut }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setSleepFadeOut(on: Boolean) {
        viewModelScope.launch { settingsRepository.update { it.copy(sleepFadeOut = on) } }
    }
    fun cancelSleepTimer() = playback.cancelSleepTimer()
    fun setLyricsVisible(visible: Boolean) {
        showLyrics.value = visible
    }

    fun toggleFavorite() {
        val id = state.value.current?.songId ?: return
        taps.run("favourite" to id) { viewModelScope.launch { favorites.toggle(id) } }
    }

    private companion object {
        const val SEEK_BAR_INTERVAL_MS = 250L
    }
}

/** The mini-player's thin progress line only needs one update a second. */
@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    private val playback: PlaybackController,
) : ViewModel() {
    val state: StateFlow<PlaybackState> = playback.state
    val progress: StateFlow<PlaybackProgress> = playback.progress(1_000L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), PlaybackProgress())

    // A double tap acts once.
    private val taps = com.svara.app.ui.TapGuard()
    fun togglePlayPause() = taps.run("play") { playback.togglePlayPause() }
    fun skipNext() = taps.run("next") { playback.skipNext() }
    fun skipPrevious() = taps.run("previous") { playback.skipPrevious() }
    fun retry() = playback.retryConnection()
}

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val playback: PlaybackController,
    private val playlists: PlaylistRepository,
    private val messages: UserMessages,
) : ViewModel() {
    val state: StateFlow<PlaybackState> = playback.state
    val undoLabel: StateFlow<String?> = playback.undoLabel

    fun undo() = playback.undoQueueChange()

    fun saveAsPlaylist(name: String) {
        val ids = playback.queueSongIdsInPlayOrder()
        if (ids.isEmpty() || name.isBlank()) return
        viewModelScope.launch {
            val id = playlists.create(name)
            val added = playlists.addSongs(id, ids)
            messages.emit("Saved queue as “${name.trim()}” ($added songs)")
        }
    }

    fun skipTo(position: Int) = playback.skipToQueuePosition(position)
    fun move(from: Int, to: Int) = playback.moveQueueItem(from, to)
    fun remove(position: Int) = playback.removeQueueItem(position)
    fun clear() = playback.clearQueue()
}

/** Connects ZENITH's visuals to audio analysis while (and only while) they are on screen. */
@HiltViewModel
class VisualiserViewModel @Inject constructor(
    private val analysis: AudioAnalysis,
) : ViewModel() {
    val bands: StateFlow<AudioBands> = analysis.bands
    val supported: StateFlow<Boolean> = analysis.supported

    fun show(fps: Int) = analysis.setWanted(true, fps)
    fun hide() = analysis.setWanted(false, 0)

    override fun onCleared() = hide()
}
