package com.svara.app.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svara.app.domain.library.TextKeys
import com.svara.app.domain.model.Album
import com.svara.app.domain.model.FolderKey
import com.svara.app.domain.model.Song
import com.svara.app.domain.repository.FavoritesRepository
import com.svara.app.domain.repository.HistoryRepository
import com.svara.app.domain.repository.LibraryRepository
import com.svara.app.playback.PlaybackController
import com.svara.app.ui.components.formatTotalDuration
import com.svara.app.ui.components.songCountLabel
import com.svara.app.ui.navigation.CollectionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class CollectionUi(
    val loading: Boolean = true,
    val title: String = "",
    val subtitle: String = "",
    val songs: List<Song> = emptyList(),
    val coverSongId: Long = -1,
    val coverAlbumId: Long = -1,
    /** Albums by this artist; only for artist pages. */
    val albums: List<Album> = emptyList(),
    /** Optional per-song annotation, e.g. play counts. */
    val annotations: Map<Long, String> = emptyMap(),
    val emptyMessage: String = "No songs here.",
)

/** Backs every "list of songs" page: album, artist, folder, genre, favorites and history lists. */
@HiltViewModel
class CollectionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    library: LibraryRepository,
    favorites: FavoritesRepository,
    history: HistoryRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    val type: CollectionType = savedState.get<String>("type")
        ?.let { t -> CollectionType.entries.firstOrNull { it.name == t } }
        ?: CollectionType.RECENTLY_ADDED
    private val id: String = savedState.get<String>("id").orEmpty()

    val currentSongId: StateFlow<Long?> = playback.state.map { it.current?.songId }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun simple(songs: List<Song>, title: String, subtitle: String = summary(songs), emptyMessage: String = "No songs here.") =
        CollectionUi(false, title, subtitle, songs, songs.firstOrNull()?.id ?: -1, songs.firstOrNull()?.albumId ?: -1, emptyMessage = emptyMessage)

    private val source: Flow<CollectionUi> = when (type) {
        CollectionType.ALBUM -> library.albumSongs(id.toLongOrNull() ?: -1).map { songs ->
            val first = songs.firstOrNull()
            val year = songs.maxOfOrNull { it.year }?.takeIf { it > 0 }
            val artist = songs.firstNotNullOfOrNull { it.albumArtist } ?: first?.displayArtist
            simple(songs, first?.displayAlbum ?: "Album", listOfNotNull(artist, year?.toString(), summary(songs)).joinToString(" • "))
        }
        CollectionType.ARTIST -> combine(library.artistSongs(TextKeys.sort(id)), library.albums) { songs, albums ->
            val albumIds = songs.mapTo(HashSet()) { it.albumId }
            simple(songs, id).copy(albums = albums.filter { it.id in albumIds }.sortedByDescending { it.year })
        }
        CollectionType.FOLDER -> {
            val key = FolderKey.fromId(id)
            if (key == null) flowOf(simple(emptyList(), "Folder")) else library.folderSongs(key).map { songs ->
                simple(songs, key.displayName, "${key.path} • ${summary(songs)}")
            }
        }
        CollectionType.GENRE -> library.genreSongs(id).map { simple(it, id) }
        CollectionType.FAVORITES -> favorites.favorites(HISTORY_LIMIT * 10).map {
            simple(it, "Favorites", emptyMessage = "Tap the heart on any song to keep it here.")
        }
        CollectionType.RECENTLY_PLAYED -> history.recentlyPlayed(HISTORY_LIMIT).map {
            simple(it, "Recently played", emptyMessage = "Songs you listen to will show up here.")
        }
        CollectionType.MOST_PLAYED -> history.mostPlayed(HISTORY_LIMIT).map { counts ->
            simple(counts.map { it.song }, "Most played", emptyMessage = "Listen for a while and your favourites will rise to the top.")
                .copy(annotations = counts.associate { it.song.id to if (it.count == 1) "1 play" else "${it.count} plays" })
        }
        CollectionType.RECENTLY_ADDED -> library.recentlyAdded(HISTORY_LIMIT).map {
            simple(it, "Recently added", emptyMessage = "New music you copy to the device appears here.")
        }
    }

    val ui: StateFlow<CollectionUi> = source.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionUi())

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    /** The album id from the route, for album pages; null for other collections. */
    fun albumIdOrNull(): Long? = if (type == CollectionType.ALBUM) id.toLongOrNull() else null

    fun play() = playback.playSongs(ui.value.songs, 0, shuffle = false)
    fun playFrom(index: Int) = playback.playSongs(ui.value.songs, index)
    fun shuffle() = playback.shuffleAll(ui.value.songs.map { it.id })

    fun toggleSelection(songId: Long) = _selection.update { if (songId in it) it - songId else it + songId }
    fun selectAll() {
        _selection.value = ui.value.songs.mapTo(LinkedHashSet()) { it.id }
    }
    fun clearSelection() {
        _selection.value = emptySet()
    }
    fun selectedIds(): List<Long> = ui.value.songs.filter { it.id in _selection.value }.map { it.id }

    private fun summary(songs: List<Song>) = "${songCountLabel(songs.size)} • ${formatTotalDuration(songs.sumOf { it.durationMs })}"

    private companion object {
        const val HISTORY_LIMIT = 100
    }
}
