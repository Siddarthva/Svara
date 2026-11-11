package com.svara.app.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.svara.app.domain.model.LibraryTab
import com.svara.app.domain.model.SongFilter
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.svara.app.domain.library.LibrarySorting
import com.svara.app.domain.model.Album
import com.svara.app.domain.model.AlbumSortField
import com.svara.app.domain.model.AppSettings
import com.svara.app.domain.model.Artist
import com.svara.app.domain.model.ArtistSortField
import com.svara.app.domain.model.Folder
import com.svara.app.domain.model.Genre
import com.svara.app.domain.model.ScanProgress
import com.svara.app.domain.model.Song
import com.svara.app.domain.model.SongSortField
import com.svara.app.domain.model.SortOrder
import com.svara.app.domain.repository.LibraryRepository
import com.svara.app.domain.repository.ScanState
import com.svara.app.domain.repository.SettingsRepository
import com.svara.app.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    /** Tab requested by navigation (e.g. a Home quick filter), if any. */
    val initialTab: LibraryTab? = savedState.get<String>("tab")?.let { t -> LibraryTab.entries.firstOrNull { it.name == t } }

    /** Songs or Downloads; both use the same paged list with a different SQL filter. */
    private val filter = MutableStateFlow(SongFilter.ALL)
    val activeFilter: StateFlow<SongFilter> = filter

    fun setFilter(value: SongFilter) {
        if (filter.value != value) {
            filter.value = value
            clearSelection()
        }
    }

    val scanState: StateFlow<ScanState> = library.scanState
    val scanProgress: StateFlow<ScanProgress?> = library.scanProgress

    /** null until the database answers, so the screen can tell "loading" from "empty". */
    val songCount: StateFlow<Int?> = library.songCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Count for the active filter (all songs or downloads). */
    val filteredCount: StateFlow<Int?> = filter.flatMapLatest { library.songCount(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val currentSongId: StateFlow<Long?> = playback.state.map { it.current?.songId }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val songSort: StateFlow<SortOrder<SongSortField>> = settings.settings.map { it.songSort }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.settings.value.songSort)
    val albumSort: StateFlow<SortOrder<AlbumSortField>> = settings.settings.map { it.albumSort }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.settings.value.albumSort)
    val artistSort: StateFlow<SortOrder<ArtistSortField>> = settings.settings.map { it.artistSort }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.settings.value.artistSort)

    /** Paged from Room; sorting happens in SQL on indexed keys. */
    val songs: Flow<PagingData<Song>> = combine(songSort, filter, ::Pair)
        .flatMapLatest { (sort, f) -> library.pagedSongs(sort, f) }
        .cachedIn(viewModelScope)

    /** Null until the first result arrives, so the screen can tell loading from empty. */
    val albums: StateFlow<List<Album>?> = combine(library.albums, albumSort, LibrarySorting::sortAlbums)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val artists: StateFlow<List<Artist>?> = combine(library.artists, artistSort, LibrarySorting::sortArtists)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val folders: StateFlow<List<Folder>?> = library.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val genres: StateFlow<List<Genre>> = library.genres
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    fun refresh() {
        viewModelScope.launch { library.rescan() }
    }

    fun cancelScan() = library.cancelScan()

    /** Plays the whole sorted library starting at [song]; only ids are loaded. */
    fun playFrom(song: Song) {
        viewModelScope.launch {
            val ids = library.songIds(songSort.value, filter.value)
            playback.playQueue(ids, ids.indexOf(song.id).coerceAtLeast(0))
        }
    }

    fun playAll() {
        viewModelScope.launch { playback.playQueue(library.songIds(songSort.value, filter.value), 0, shuffle = false) }
    }

    fun shuffleAll() {
        viewModelScope.launch { playback.shuffleAll(library.songIds(songSort.value, filter.value)) }
    }

    fun setSongSort(field: SongSortField, ascending: Boolean) = updateSettings { it.copy(songSort = SortOrder(field, ascending)) }
    fun setAlbumSort(field: AlbumSortField, ascending: Boolean) = updateSettings { it.copy(albumSort = SortOrder(field, ascending)) }
    fun setArtistSort(field: ArtistSortField, ascending: Boolean) = updateSettings { it.copy(artistSort = SortOrder(field, ascending)) }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    fun toggleSelection(songId: Long) = _selection.update { if (songId in it) it - songId else it + songId }

    fun selectAll() {
        viewModelScope.launch { _selection.value = LinkedHashSet(library.songIds(songSort.value, filter.value)) }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    /** Selected ids in list order. */
    suspend fun selectedIds(): List<Long> {
        val selected = _selection.value
        return library.songIds(songSort.value, filter.value).filter { it in selected }
    }

    /** Runs [action] with the selection in list order, then leaves selection mode. */
    fun withSelection(action: (List<Long>) -> Unit) {
        viewModelScope.launch {
            action(selectedIds())
            clearSelection()
        }
    }
}
