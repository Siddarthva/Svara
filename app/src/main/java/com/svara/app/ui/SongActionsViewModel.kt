package com.svara.app.ui

import kotlinx.coroutines.flow.first
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svara.app.domain.model.Playlist
import com.svara.app.domain.repository.FavoritesRepository
import com.svara.app.domain.repository.PlaylistRepository
import com.svara.app.playback.PlaybackController
import com.svara.app.playback.UserMessages
import com.svara.app.ui.components.songCountLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared actions available on any song: queueing, favorites and playlists. Everything works on
 * song ids, so bulk actions on thousands of selected songs never load full rows.
 */
@HiltViewModel
class SongActionsViewModel @Inject constructor(
    private val playback: PlaybackController,
    private val favorites: FavoritesRepository,
    private val playlists: PlaylistRepository,
    private val messages: UserMessages,
    @com.svara.app.di.ApplicationScope private val appScope: kotlinx.coroutines.CoroutineScope,
) : ViewModel() {

    val allPlaylists: StateFlow<List<Playlist>> =
        playlists.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteIds: StateFlow<Set<Long>> =
        favorites.favoriteIds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // A double tap on a menu entry must not queue the songs twice.
    private val taps = TapGuard()

    fun playNext(ids: List<Long>, title: String?) {
        if (!taps.accept("next" to ids)) return
        playback.playNext(ids)
        messages.emit(if (ids.size == 1 && title != null) "“$title” will play next" else "${songCountLabel(ids.size)} will play next")
    }

    fun addToQueue(ids: List<Long>, title: String?) {
        if (!taps.accept("queue" to ids)) return
        playback.addToQueue(ids)
        messages.emit(if (ids.size == 1 && title != null) "Added “$title” to the queue" else "Added ${songCountLabel(ids.size)} to the queue")
    }

    /** Hearts every song in [ids] (or un-hearts them all when [favourite] is false), with Undo. */
    fun setFavorites(ids: List<Long>, favourite: Boolean) {
        viewModelScope.launch {
            val before = favorites.favoriteIds.first()
            ids.forEach { favorites.setFavorite(it, favourite) }
            val changed = ids.filter { (it in before) != favourite }
            messages.emit(
                if (favourite) "Added ${changed.size} to favourites" else "Removed ${changed.size} from favourites",
                "Undo",
            ) { appScope.launch { changed.forEach { favorites.setFavorite(it, !favourite) } } }
        }
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch { favorites.toggle(songId) }
    }

    fun addToPlaylist(playlist: Playlist, ids: List<Long>) {
        viewModelScope.launch {
            val added = playlists.addSongs(playlist.id, ids)
            messages.emit(if (added == 0) "Already in “${playlist.name}”" else "Added ${songCountLabel(added)} to “${playlist.name}”")
        }
    }

    fun createPlaylistAndAdd(name: String, ids: List<Long>) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = playlists.create(name)
            val added = playlists.addSongs(id, ids)
            messages.emit("Created “${name.trim()}” with ${songCountLabel(added)}")
        }
    }
}
