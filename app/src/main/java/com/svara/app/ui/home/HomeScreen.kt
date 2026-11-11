package com.svara.app.ui.home

import com.svara.app.ui.states.SkeletonList
import com.svara.app.ui.presets.common.ambientWash
import com.svara.app.ui.theme.LocalAmbient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.svara.app.domain.model.HomeSection
import com.svara.app.domain.model.LibraryTab
import com.svara.app.domain.model.PlayCount
import com.svara.app.domain.model.Playlist
import com.svara.app.domain.model.Song
import com.svara.app.domain.model.Unfinished
import com.svara.app.domain.repository.FavoritesRepository
import com.svara.app.domain.repository.HistoryRepository
import com.svara.app.domain.repository.LibraryRepository
import com.svara.app.domain.repository.PlaylistRepository
import com.svara.app.domain.repository.SettingsRepository
import com.svara.app.playback.PlaybackController
import com.svara.app.playback.QueueEntry
import com.svara.app.ui.components.ArtworkImage
import com.svara.app.ui.components.ChooseMusicFolderButton
import com.svara.app.ui.components.EmptyState
import com.svara.app.ui.components.SectionHeader
import com.svara.app.ui.components.songCountLabel
import com.svara.app.ui.navigation.CollectionType
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.navigation.Routes
import com.svara.app.appearance.HomeLayout
import com.svara.app.appearance.LayoutSet
import com.svara.app.appearance.NavTab
import com.svara.app.domain.model.Genre
import com.svara.app.ui.presets.CompactTileRow
import com.svara.app.ui.presets.PresetTile
import com.svara.app.ui.presets.presetTileWidth
import com.svara.app.ui.theme.LocalAppearance
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilterChip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUi(
    val loading: Boolean = true,
    val songCount: Int = 0,
    val sections: List<HomeSection> = HomeSection.entries,
    val hidden: Set<HomeSection> = emptySet(),
    val recentlyPlayed: List<Song> = emptyList(),
    val mostPlayed: List<PlayCount> = emptyList(),
    val favorites: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val recentlyAdded: List<Song> = emptyList(),
    val unfinished: List<Unfinished> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    library: LibraryRepository,
    history: HistoryRepository,
    favorites: FavoritesRepository,
    playlists: PlaylistRepository,
    private val settings: SettingsRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    private val since = System.currentTimeMillis() - UNFINISHED_WINDOW_MS

    private val head = combine(
        library.songCount,
        settings.settings.map { it.homeSections to it.hiddenHomeSections }.distinctUntilChanged(),
        history.recentlyPlayed(ROW_LIMIT),
        history.mostPlayed(ROW_LIMIT),
    ) { count, layout, recent, most -> HomeUi(false, count, layout.first, layout.second, recent, most) }

    private val tail = combine(
        favorites.favorites(ROW_LIMIT),
        playlists.playlists,
        library.recentlyAdded(ROW_LIMIT),
        combine(library.unfinishedAlbums(since, 6), playlists.unfinished(since, 4)) { a, p -> (a + p).take(8) },
    ) { favs, lists, added, unfinished -> HomeUi(favorites = favs, playlists = lists, recentlyAdded = added, unfinished = unfinished) }

    val ui: StateFlow<HomeUi> = combine(head, tail) { h, t ->
        h.copy(favorites = t.favorites, playlists = t.playlists, recentlyAdded = t.recentlyAdded, unfinished = t.unfinished)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    val current: StateFlow<QueueEntry?> = playback.state.map { it.current }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val isPlaying: StateFlow<Boolean> = playback.state.map { it.isPlaying }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Genre chips (OCTAVE) come only from the files' own genre tags. */
    val genres: StateFlow<List<Genre>> = library.genres
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun play(songs: List<Song>, index: Int) = playback.playSongs(songs, index)
    fun resume() = playback.togglePlayPause()

    fun saveLayout(order: List<HomeSection>, hidden: Set<HomeSection>) {
        viewModelScope.launch { settings.update { it.copy(homeSections = order, hiddenHomeSections = hidden) } }
    }

    private companion object {
        const val ROW_LIMIT = 15
        const val UNFINISHED_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
    }
}

private val HISTORY_SECTIONS = setOf(HomeSection.RECENTLY_PLAYED, HomeSection.MOST_PLAYED, HomeSection.JUMP_BACK_IN)

/** Theme Studio's home layout reorders the user's sections; hidden sections stay hidden. */
internal fun orderForLayout(sections: List<HomeSection>, layout: HomeLayout): List<HomeSection> {
    fun first(vararg lead: HomeSection) = lead.filter { it in sections } + sections.filter { it !in lead }
    return when (layout) {
        HomeLayout.ARTWORK_FIRST, HomeLayout.COMPACT_LIBRARY -> sections
        HomeLayout.PLAYLIST_CARDS -> first(HomeSection.PLAYLISTS)
        HomeLayout.RECENT_FIRST -> first(HomeSection.RECENTLY_PLAYED)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navigator: Navigator, hideHistory: Boolean, viewModel: HomeViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val current by viewModel.current.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    var customizing by rememberSaveable { mutableStateOf(false) }
    val appearance = LocalAppearance.current
    val homeLayout = appearance.theme.homeLayout
    val compactRows = homeLayout == HomeLayout.COMPACT_LIBRARY
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val settingsHidden = NavTab.SETTINGS !in appearance.custom.visibleTabs

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SVARĀ") },
                actions = {
                    IconButton(onClick = navigator::search) { Icon(Icons.Rounded.Search, contentDescription = "Search") }
                    IconButton(onClick = { customizing = true }) { Icon(Icons.Rounded.Tune, contentDescription = "Customize home") }
                    // Navigation Studio may hide the Settings tab; it stays one tap away here.
                    if (settingsHidden) IconButton(onClick = navigator::settings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings") }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading -> SkeletonList(Modifier.padding(padding))
            ui.songCount == 0 -> EmptyState(
                icon = Icons.Rounded.LibraryMusic,
                title = "No music yet",
                message = "SVARĀ didn't find any music on this device. Choose the folder where your music lives, or copy some songs to the phone.",
                modifier = Modifier.padding(padding),
                action = { ChooseMusicFolderButton() },
            )
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize().ambientWash(LocalAmbient.current), contentPadding = PaddingValues(bottom = 16.dp)) {
                item(key = "recap") { com.svara.app.recap.RecapHomeCard(navigator) }
                item(key = "filters") { QuickFilters(navigator) }
                if (appearance.layout == LayoutSet.COLOUR_TILES && genres.isNotEmpty()) item(key = "genres") { GenreChips(genres, navigator) }
                orderForLayout(ui.sections, homeLayout).filter { it !in ui.hidden && !(hideHistory && it in HISTORY_SECTIONS) }.forEach { section ->
                    when (section) {
                        HomeSection.CONTINUE -> current?.let { entry ->
                            item(key = "continue") { ContinueCard(entry, isPlaying, onOpen = navigator::nowPlaying, onResume = viewModel::resume) }
                        }
                        HomeSection.JUMP_BACK_IN -> if (ui.unfinished.isNotEmpty()) item(key = "jump") {
                            SectionHeader(section.label)
                            TileRow(ui.unfinished, compactRows, key = { "${it.kind}${it.id}" }) { u ->
                                TileData(u.title, "${u.played} of ${u.total} played", u.coverSongId, u.coverAlbumId) {
                                    if (u.kind == Unfinished.Kind.ALBUM) navigator.album(u.id) else navigator.playlist(u.id)
                                }
                            }
                        }
                        HomeSection.RECENTLY_PLAYED -> songRow(section.label, ui.recentlyPlayed, compactRows, { navigator.collection(CollectionType.RECENTLY_PLAYED) }, viewModel::play)
                        HomeSection.MOST_PLAYED -> songRow(section.label, ui.mostPlayed.map { it.song }, compactRows, { navigator.collection(CollectionType.MOST_PLAYED) }, viewModel::play)
                        HomeSection.RECENTLY_ADDED -> songRow(section.label, ui.recentlyAdded, compactRows, { navigator.collection(CollectionType.RECENTLY_ADDED) }, viewModel::play)
                        HomeSection.FAVORITES -> songRow(section.label, ui.favorites, compactRows, { navigator.collection(CollectionType.FAVORITES) }, viewModel::play)
                        HomeSection.PLAYLISTS -> if (ui.playlists.isNotEmpty()) item(key = "playlists") {
                            SectionHeader(section.label) { TextButton(onClick = { navigator.topLevel(Routes.PLAYLISTS) }) { Text("See all") } }
                            val toTile: (Playlist) -> TileData = { playlist ->
                                TileData(playlist.name, songCountLabel(playlist.songCount), playlist.coverSongId ?: -1, playlist.coverAlbumId ?: -1) {
                                    navigator.playlist(playlist.id)
                                }
                            }
                            if (homeLayout == HomeLayout.PLAYLIST_CARDS) PlaylistCards(ui.playlists.take(6).map(toTile))
                            else TileRow(ui.playlists, compactRows, key = { it.id }, toTile = toTile)
                        }
                    }
                }
            }
        }
    }

    if (customizing) {
        CustomizeHomeDialog(ui.sections, ui.hidden, onSave = { o, h -> viewModel.saveLayout(o, h); customizing = false }, onDismiss = { customizing = false })
    }
}

@Composable
private fun QuickFilters(navigator: Navigator) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(LibraryTab.SONGS, "Songs", Icons.Rounded.MusicNote),
            Triple(LibraryTab.ALBUMS, "Albums", Icons.Rounded.Album),
            Triple(LibraryTab.ARTISTS, "Artists", Icons.Rounded.Person),
            Triple(LibraryTab.FOLDERS, "Folders", Icons.Rounded.Folder),
            Triple(LibraryTab.DOWNLOADS, "Downloads", Icons.Rounded.Download),
        ).forEach { (tab, label, icon) ->
            AssistChip(
                onClick = { navigator.library(tab) },
                label = { Text(label) },
                leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun ContinueCard(entry: QueueEntry, isPlaying: Boolean, onOpen: () -> Unit, onResume: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClickLabel = "Open now playing", onClick = onOpen)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkImage(entry.songId, entry.albumId, Modifier.size(64.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(if (isPlaying) "Now playing" else "Continue listening", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (!isPlaying) {
                FilledIconButton(onClick = onResume, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Resume ${entry.title}") }
            }
        }
    }
}

private fun LazyListScope.songRow(title: String, songs: List<Song>, compact: Boolean, onSeeAll: () -> Unit, onPlay: (List<Song>, Int) -> Unit) {
    if (songs.isEmpty()) return
    item(key = title) {
        SectionHeader(title) { TextButton(onClick = onSeeAll) { Text("See all") } }
        TileRow(songs.withIndex().toList(), compact, key = { it.value.id }) { (index, song) ->
            TileData(song.title, song.displayArtist, song.id, song.albumId) { onPlay(songs, index) }
        }
    }
}

private class TileData(val title: String, val subtitle: String, val songId: Long, val albumId: Long, val onClick: () -> Unit)

/** A horizontal row of preset tiles, or (Compact library layout) a short vertical list. */
@Composable
private fun <T> TileRow(items: List<T>, compact: Boolean, key: (T) -> Any, toTile: (T) -> TileData) {
    if (compact) {
        Column { items.take(COMPACT_ROWS).forEach { val t = toTile(it); CompactTileRow(t.title, t.subtitle, t.songId, t.albumId, t.onClick) } }
    } else {
        val width = presetTileWidth()
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
            items(items, key = key) { item ->
                val t = toTile(item)
                PresetTile(t.title, t.subtitle, t.songId, t.albumId, t.onClick, Modifier.width(width).padding(horizontal = 2.dp))
            }
        }
    }
}

/** Playlist cards layout: two large cards per row. */
@Composable
private fun PlaylistCards(tiles: List<TileData>) {
    Column(Modifier.padding(horizontal = 12.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { t -> PresetTile(t.title, t.subtitle, t.songId, t.albumId, t.onClick, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** OCTAVE: chips from local genre tags. Mood chips would appear here only for user-set mood tags; SVARĀ never computes moods. */
@Composable
private fun GenreChips(genres: List<Genre>, navigator: Navigator) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        genres.take(12).forEach { genre ->
            FilterChip(
                selected = false,
                onClick = { navigator.genre(genre.name) },
                label = { Text(genre.name) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

private const val COMPACT_ROWS = 5

/** Reorder with up/down buttons (works with TalkBack) and show or hide each section. */
@Composable
private fun CustomizeHomeDialog(
    sections: List<HomeSection>,
    hidden: Set<HomeSection>,
    onSave: (List<HomeSection>, Set<HomeSection>) -> Unit,
    onDismiss: () -> Unit,
) {
    var order by rememberSaveable { mutableStateOf(sections) }
    var off by rememberSaveable { mutableStateOf(hidden) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize home") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                order.forEachIndexed { i, section ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = section !in off, onCheckedChange = { on -> off = if (on) off - section else off + section })
                        Text(section.label, Modifier.weight(1f))
                        IconButton(onClick = { order = order.toMutableList().apply { add(i - 1, removeAt(i)) } }, enabled = i > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move ${section.label} up")
                        }
                        IconButton(onClick = { order = order.toMutableList().apply { add(i + 1, removeAt(i)) } }, enabled = i < order.lastIndex) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move ${section.label} down")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(order, off) }) { Text("Save") } },
        dismissButton = {
            TextButton(onClick = { order = HomeSection.entries; off = emptySet() }) { Text("Reset") }
        },
    )
}
