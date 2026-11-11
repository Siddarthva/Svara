package com.svara.app.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.svara.app.domain.model.Album
import com.svara.app.domain.model.Artist
import com.svara.app.domain.model.Folder
import com.svara.app.domain.model.Playlist
import com.svara.app.domain.model.Song
import com.svara.app.domain.model.SongSortField
import com.svara.app.domain.model.SortOrder
import com.svara.app.domain.repository.FavoritesRepository
import com.svara.app.domain.repository.LibraryRepository
import com.svara.app.domain.repository.PlaylistRepository
import com.svara.app.playback.PlaybackController
import com.svara.app.ui.home.CoverItem
import com.svara.app.ui.home.PresetHomeBuilder
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
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sort choices the layout presets' libraries offer; each list applies the ones that fit it. */
enum class PresetSort(val label: String) {
    RECENTLY_ADDED("Recently added"),
    TITLE("Title"),
    ARTIST("Artist"),
    DURATION("Duration"),
}

/** Pure sorting for the in-memory lists (albums, artists, playlists). Unit tested. */
object PresetSorting {
    fun albums(list: List<Album>, sort: PresetSort): List<Album> = when (sort) {
        PresetSort.ARTIST -> list.sortedWith(compareBy({ it.artistSortKey }, { it.sortKey }))
        PresetSort.DURATION -> list.sortedByDescending { it.durationMs }
        else -> list.sortedBy { it.sortKey }
    }

    fun artists(list: List<Artist>, sort: PresetSort): List<Artist> = when (sort) {
        PresetSort.DURATION -> list.sortedByDescending { it.songCount }
        else -> list.sortedBy { it.sortKey }
    }

    fun playlists(list: List<Playlist>, sort: PresetSort): List<Playlist> = when (sort) {
        PresetSort.RECENTLY_ADDED -> list.sortedByDescending { it.createdAt }
        else -> list.sortedBy { it.name.lowercase() }
    }

    fun songOrder(sort: PresetSort): SortOrder<SongSortField> = when (sort) {
        PresetSort.RECENTLY_ADDED -> SortOrder(SongSortField.DATE_ADDED, ascending = false)
        PresetSort.TITLE -> SortOrder(SongSortField.TITLE)
        PresetSort.ARTIST -> SortOrder(SongSortField.ARTIST)
        PresetSort.DURATION -> SortOrder(SongSortField.DURATION, ascending = false)
    }
}

/**
 * Data for the layout presets' library tabs. Songs are paged from Room (sorted in SQL); the
 * other lists are small aggregates kept in memory. Changing preset never touches any of this.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PresetLibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    playlists: PlaylistRepository,
    favorites: FavoritesRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    private val _sort = MutableStateFlow(PresetSort.RECENTLY_ADDED)
    val sort: StateFlow<PresetSort> = _sort.asStateFlow()
    fun setSort(value: PresetSort) {
        _sort.value = value
    }

    private val songOrder = _sort.map(PresetSorting::songOrder).distinctUntilChanged()

    val songs: Flow<PagingData<Song>> = songOrder.flatMapLatest { library.pagedSongs(it) }.cachedIn(viewModelScope)

    val songCount: StateFlow<Int?> = library.songCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val albums: StateFlow<List<Album>> = combine(library.albums, _sort, PresetSorting::albums)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val artists: StateFlow<List<Artist>> = combine(library.artists, _sort, PresetSorting::artists)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<Playlist>> = combine(playlists.playlists, _sort, PresetSorting::playlists)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders: StateFlow<List<Folder>> = library.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<Song>> = favorites.favorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** SOLACE's "Recently added" grid. */
    val recentlyAddedAlbums: StateFlow<List<CoverItem>> = library.recentlyAdded(RECENT_SOURCE)
        .map { PresetHomeBuilder.albums(it).take(RECENT_GRID) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentSongId: StateFlow<Long?> = playback.state.map { it.current?.songId }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Plays the whole library in the current order from [song]; only ids cross to the player. */
    fun playFrom(song: Song) {
        viewModelScope.launch {
            val ids = library.songIds(PresetSorting.songOrder(_sort.value))
            playback.playQueue(ids, ids.indexOf(song.id).coerceAtLeast(0))
        }
    }

    fun play(songs: List<Song>, index: Int) = playback.playSongs(songs, index)

    private companion object {
        const val RECENT_SOURCE = 80
        const val RECENT_GRID = 12
    }
}
