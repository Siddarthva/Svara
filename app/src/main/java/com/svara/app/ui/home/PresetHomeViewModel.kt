package com.svara.app.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svara.app.domain.model.Genre
import com.svara.app.domain.model.PlayCount
import com.svara.app.domain.model.SmartPlaylist
import com.svara.app.domain.model.Song
import com.svara.app.domain.model.Unfinished
import com.svara.app.domain.repository.HistoryRepository
import com.svara.app.domain.repository.LibraryRepository
import com.svara.app.domain.repository.PlaylistRepository
import com.svara.app.domain.repository.SmartPlaylistRepository
import com.svara.app.playback.PlaybackController
import com.svara.app.ui.navigation.CollectionType
import com.svara.app.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One card on a layout preset's home. [target] says what a tap opens. */
@Immutable
data class CoverItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val songId: Long,
    val albumId: Long,
    val target: Target,
) {
    sealed interface Target {
        data class Album(val id: Long) : Target
        data class Playlist(val id: Long) : Target
        data class Smart(val id: Long) : Target
        data class Artist(val name: String) : Target
        data class Collection(val type: CollectionType) : Target
        /** A song inside [CoverItem]'s row: play the row from here. */
        data class SongAt(val index: Int) : Target
    }
}

@Immutable
data class PresetHome(
    val loading: Boolean = true,
    val songCount: Int = 0,
    /** Halo's 2 × 3 grid: recently played, then most played, then recently added. */
    val quickGrid: List<CoverItem> = emptyList(),
    val recentlyPlayed: List<CoverItem> = emptyList(),
    val jumpBackIn: List<CoverItem> = emptyList(),
    val madeForYou: List<CoverItem> = emptyList(),
    val recentlyAdded: List<CoverItem> = emptyList(),
    /** SOLACE's hero pager: up to five recent albums. */
    val heroAlbums: List<CoverItem> = emptyList(),
    /** OCTAVE's banner: the three most-played albums. */
    val topAlbums: List<CoverItem> = emptyList(),
    /** OCTAVE's "Top charts": most-played songs ([CoverItem.Target.SongAt] into [topSongs]). */
    val topCharts: List<CoverItem> = emptyList(),
    val topSongs: List<Song> = emptyList(),
    /** ZENITH's banner: the most recently added album. */
    val newestAlbum: CoverItem? = null,
    val artists: List<CoverItem> = emptyList(),
    val genres: List<Genre> = emptyList(),
)

/** Pure: turns history and library rows into each layout's cards. Unit tested. */
object PresetHomeBuilder {
    const val QUICK_GRID = 6
    const val HERO = 5
    const val BANNER = 3
    const val ROW = 12

    private fun Song.album() = CoverItem(
        key = "album-$albumId", title = displayAlbum, subtitle = albumArtist?.takeIf { it.isNotBlank() } ?: displayArtist,
        songId = id, albumId = albumId, target = CoverItem.Target.Album(albumId),
    )

    /** Distinct albums in first-seen order. */
    fun albums(songs: List<Song>): List<CoverItem> = songs.distinctBy { it.albumId }.map { it.album() }

    /** Albums ranked by total plays of their songs. */
    fun topAlbums(most: List<PlayCount>): List<CoverItem> =
        most.groupBy { it.song.albumId }
            .entries.sortedByDescending { e -> e.value.sumOf { it.count } }
            .map { it.value.first().song.album() }

    fun artists(most: List<PlayCount>): List<CoverItem> =
        most.distinctBy { it.song.displayArtist.lowercase() }.map { pc ->
            CoverItem(
                key = "artist-${pc.song.displayArtist.lowercase()}", title = pc.song.displayArtist, subtitle = "Artist",
                songId = pc.song.id, albumId = pc.song.albumId, target = CoverItem.Target.Artist(pc.song.displayArtist),
            )
        }

    fun build(
        songCount: Int,
        recent: List<Song>,
        most: List<PlayCount>,
        added: List<Song>,
        unfinished: List<Unfinished>,
        smart: List<SmartPlaylist>,
        genres: List<Genre>,
    ): PresetHome {
        val recentAlbums = albums(recent)
        val top = topAlbums(most)
        val addedAlbums = albums(added)
        val quick = (recentAlbums + top + addedAlbums).distinctBy { it.key }.take(QUICK_GRID)
        val jump = unfinished.map { u ->
            CoverItem(
                key = "${u.kind}-${u.id}", title = u.title, subtitle = "${u.played} of ${u.total} played",
                songId = u.coverSongId, albumId = u.coverAlbumId,
                target = if (u.kind == Unfinished.Kind.ALBUM) CoverItem.Target.Album(u.id) else CoverItem.Target.Playlist(u.id),
            )
        }
        // Smart playlists first, then the built-in mixes, so the row is never empty.
        val made = smart.map { s ->
            CoverItem("smart-${s.id}", s.name, "Smart playlist", -1, -1, CoverItem.Target.Smart(s.id))
        } + listOf(
            CoverItem("mix-most", "Most played", "Your top songs", most.firstOrNull()?.song?.id ?: -1, most.firstOrNull()?.song?.albumId ?: -1, CoverItem.Target.Collection(CollectionType.MOST_PLAYED)),
            CoverItem("mix-fav", "Favourites", "Songs you liked", -1, -1, CoverItem.Target.Collection(CollectionType.FAVORITES)),
        )
        val topSongs = most.map { it.song }
        return PresetHome(
            loading = false,
            songCount = songCount,
            quickGrid = quick,
            recentlyPlayed = recentAlbums.take(ROW),
            jumpBackIn = jump,
            madeForYou = made,
            recentlyAdded = addedAlbums.take(ROW),
            heroAlbums = recentAlbums.ifEmpty { addedAlbums }.take(HERO),
            topAlbums = top.ifEmpty { addedAlbums }.take(BANNER),
            topCharts = topSongs.take(ROW).mapIndexed { i, s ->
                CoverItem("top-${s.id}", s.title, s.displayArtist, s.id, s.albumId, CoverItem.Target.SongAt(i))
            },
            topSongs = topSongs,
            newestAlbum = addedAlbums.firstOrNull(),
            artists = artists(most).take(ROW),
            genres = genres,
        )
    }
}

/** Halo's greeting from local time. */
fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@HiltViewModel
class PresetHomeViewModel @Inject constructor(
    private val library: LibraryRepository,
    history: HistoryRepository,
    playlists: PlaylistRepository,
    smartPlaylists: SmartPlaylistRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    private val since = System.currentTimeMillis() - UNFINISHED_WINDOW_MS

    private val head = combine(
        library.songCount,
        history.recentlyPlayed(SOURCE_LIMIT),
        history.mostPlayed(SOURCE_LIMIT),
        library.recentlyAdded(SOURCE_LIMIT),
    ) { count, recent, most, added -> Head(count, recent, most, added) }

    private val tail = combine(
        combine(library.unfinishedAlbums(since, 6), playlists.unfinished(since, 4)) { a, p -> (a + p).take(8) },
        smartPlaylists.smartPlaylists,
        library.genres,
    ) { unfinished, smart, genres -> Triple(unfinished, smart, genres) }

    val home: StateFlow<PresetHome> = combine(head, tail) { h, (unfinished, smart, genres) ->
        PresetHomeBuilder.build(h.count, h.recent, h.most, h.added, unfinished, smart, genres)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PresetHome())

    fun play(songs: List<Song>, index: Int) = playback.playSongs(songs, index)

    /** ZENITH's banner "Play": the album from its first track. */
    fun playAlbum(albumId: Long) {
        viewModelScope.launch {
            val songs = library.albumSongs(albumId).first()
            if (songs.isNotEmpty()) playback.playSongs(songs, 0)
        }
    }

    private class Head(val count: Int, val recent: List<Song>, val most: List<PlayCount>, val added: List<Song>)

    private companion object {
        const val SOURCE_LIMIT = 40
        const val UNFINISHED_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
    }
}

/** Opens what a card points at. Song cards play [songs] from their index. */
fun Navigator.openCover(item: CoverItem, songs: List<Song> = emptyList(), play: (List<Song>, Int) -> Unit = { _, _ -> }) {
    when (val t = item.target) {
        is CoverItem.Target.Album -> album(t.id)
        is CoverItem.Target.Playlist -> playlist(t.id)
        is CoverItem.Target.Smart -> smartPlaylist(t.id)
        is CoverItem.Target.Artist -> artist(t.name)
        is CoverItem.Target.Collection -> collection(t.type)
        is CoverItem.Target.SongAt -> if (songs.isNotEmpty()) play(songs, t.index)
    }
}
