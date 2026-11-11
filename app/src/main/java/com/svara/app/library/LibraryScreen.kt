package com.svara.app.library

import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.foundation.background
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.svara.app.ui.components.PinnedHeaderLayout
import com.svara.app.ui.theme.Spacing
import androidx.compose.material.icons.rounded.Album
import com.svara.app.ui.states.SkeletonRow
import com.svara.app.ui.states.SkeletonList
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.svara.app.domain.model.SongFilter
import com.svara.app.ui.components.ChooseMusicFolderButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.svara.app.domain.model.AlbumSortField
import com.svara.app.domain.model.ArtistSortField
import com.svara.app.domain.model.LibraryTab
import com.svara.app.domain.model.SongSortField
import com.svara.app.ui.components.EmptyState
import com.svara.app.ui.components.SongActions
import com.svara.app.ui.components.SongRow
import com.svara.app.ui.components.SortMenuButton
import com.svara.app.ui.components.WithSongActions
import com.svara.app.ui.components.songCountLabel
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.presets.albumGridMinSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(navigator: Navigator, viewModel: LibraryViewModel = hiltViewModel()) {
    val songCount by viewModel.songCount.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val scan by viewModel.scanState.collectAsStateWithLifecycle()
    val songSort by viewModel.songSort.collectAsStateWithLifecycle()
    val albumSort by viewModel.albumSort.collectAsStateWithLifecycle()
    val artistSort by viewModel.artistSort.collectAsStateWithLifecycle()

    // Genres only show up when the files actually carry genre tags.
    val tabs = LibraryTab.entries.filter { it != LibraryTab.GENRES || genres.isNotEmpty() }
    var tabName by rememberSaveable { mutableStateOf((viewModel.initialTab ?: LibraryTab.SONGS).name) }
    val tab = tabs.firstOrNull { it.name == tabName } ?: LibraryTab.SONGS
    LaunchedEffect(tab) { viewModel.setFilter(if (tab == LibraryTab.DOWNLOADS) SongFilter.DOWNLOADS else SongFilter.ALL) }
    val selecting = selection.isNotEmpty()

    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    // In landscape on a phone the bars leave room for about one row: there the title bar
    // scrolls away with the list and comes back on scroll up.
    val compactHeight = LocalConfiguration.current.screenHeightDp < COMPACT_HEIGHT_DP
    val collapse = TopAppBarDefaults.enterAlwaysScrollBehavior()
    WithSongActions(navigator) { actions ->
        Scaffold(
            modifier = if (compactHeight) Modifier.nestedScroll(collapse.nestedScrollConnection) else Modifier,
            topBar = {
                if (selecting) {
                    SelectionTopBar(
                        count = selection.size,
                        onClose = viewModel::clearSelection,
                        onSelectAll = viewModel::selectAll,
                        onPlayNext = { viewModel.withSelection(actions::playNext) },
                        onAddToQueue = { viewModel.withSelection(actions::addToQueue) },
                        onAddToPlaylist = { viewModel.withSelection(actions::addToPlaylist) },
                        onFavourite = { viewModel.withSelection(actions::toggleFavorites) },
                    )
                } else {
                    TopAppBar(
                        title = { Text("Library") },
                        actions = {
                            IconButton(onClick = navigator::search) { Icon(Icons.Rounded.Search, contentDescription = "Search") }
                            when (tab) {
                                LibraryTab.SONGS, LibraryTab.DOWNLOADS -> SortMenuButton(SongSortField.entries, songSort.field, songSort.ascending, ::songSortLabel, viewModel::setSongSort)
                                LibraryTab.ALBUMS -> SortMenuButton(AlbumSortField.entries, albumSort.field, albumSort.ascending, ::albumSortLabel, viewModel::setAlbumSort)
                                LibraryTab.ARTISTS -> SortMenuButton(ArtistSortField.entries, artistSort.field, artistSort.ascending, ::artistSortLabel, viewModel::setArtistSort)
                                else -> Unit
                            }
                            IconButton(onClick = navigator::libraryCheck) {
                                Icon(Icons.AutoMirrored.Rounded.FactCheck, contentDescription = "Check library for duplicates and missing files")
                            }
                        },
                        scrollBehavior = if (compactHeight) collapse else null,
                    )
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOf(tab), edgePadding = 8.dp) {
                    tabs.forEach { t ->
                        Tab(selected = t == tab, onClick = { tabName = t.name; viewModel.clearSelection() }, text = { Text(t.label) })
                    }
                }
                ScanProgressBar(viewModel)
                val count = songCount
                PullToRefreshBox(
                    isRefreshing = scan.isScanning && (count ?: 0) > 0,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        count == null || (count == 0 && scan.isScanning) -> SkeletonList(Modifier.fillMaxSize())
                        count == 0 -> EmptyLibrary(scan.error, onRescan = viewModel::refresh, onFolders = navigator::folders)
                        else -> when (tab) {
                            LibraryTab.SONGS, LibraryTab.DOWNLOADS -> SongsTab(viewModel, actions, selection, tab)
                            LibraryTab.ALBUMS -> AlbumsTab(viewModel, navigator)
                            LibraryTab.ARTISTS -> ArtistsTab(viewModel, navigator)
                            LibraryTab.FOLDERS -> FoldersTab(viewModel, navigator)
                            LibraryTab.GENRES -> GenresTab(viewModel, navigator)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Scan progress in its own composable: it is the only thing that recomposes as the scan
 * advances, not the list beneath it.
 */
@Composable
private fun ScanProgressBar(viewModel: LibraryViewModel) {
    val progress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val p = progress ?: return
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                if (p.total > 0) "Scanning ${p.scanned} of ${p.total}" else "Scanning…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (p.total > 0) {
                LinearProgressIndicator(progress = { p.scanned / p.total.toFloat() }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        TextButton(onClick = viewModel::cancelScan) { Text("Stop") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongsTab(viewModel: LibraryViewModel, actions: SongActions, selection: Set<Long>, tab: LibraryTab) {
    val songs = viewModel.songs.collectAsLazyPagingItems()
    val count = viewModel.filteredCount.collectAsStateWithLifecycle().value ?: 0
    val activeFilter by viewModel.activeFilter.collectAsStateWithLifecycle()
    if (tab == LibraryTab.DOWNLOADS && count == 0) {
        EmptyState(Icons.Rounded.Download, "No downloads", "Audio in your Download folder shows up here.")
        return
    }
    val currentId by viewModel.currentSongId.collectAsStateWithLifecycle()
    val selecting = selection.isNotEmpty()
    // The filter chips stay pinned above the list; the list starts exactly below them.
    PinnedHeaderLayout(
        header = {
            if (tab == LibraryTab.SONGS) androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).testTag("song_filters"),
                contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(SONG_FILTERS, key = { it.name }, contentType = { "filter" }) { f ->
                    androidx.compose.material3.FilterChip(
                        selected = activeFilter == f,
                        onClick = { viewModel.setFilter(f) },
                        label = { Text(f.label, maxLines = 1) },
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { top ->
    LazyColumn(Modifier.fillMaxSize().testTag("songs_list"), contentPadding = PaddingValues(top = top.calculateTopPadding(), bottom = Spacing.s)) {
        if (count == 0 && activeFilter != SongFilter.ALL) item(key = "filter-empty") {
            EmptyState(Icons.Rounded.FilterList, "Nothing here", if (activeFilter == SongFilter.FAVOURITES) "Heart songs to see them here." else "Every song has artist and album tags.")
        }
        item(key = "header", contentType = "header") {
            // Wraps instead of squeezing: at large font sizes the count moves to its own line
            // rather than being crushed to one letter per line beside the buttons.
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = viewModel::playAll) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Text("Play all", Modifier.padding(start = Spacing.s), maxLines = 1)
                }
                FilledTonalButton(onClick = viewModel::shuffleAll) {
                    Icon(Icons.Rounded.Shuffle, null)
                    Text("Shuffle", Modifier.padding(start = Spacing.s), maxLines = 1)
                }
                Text(
                    songCountLabel(count),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = Spacing.s),
                )
            }
        }
        items(
            count = songs.itemCount,
            key = songs.itemKey { it.id },
            contentType = songs.itemContentType { "song" },
        ) { index ->
            val song = songs[index]
            if (song == null) {
                SongPlaceholderRow()
            } else {
                SongRow(
                    song = song,
                    actions = actions,
                    isCurrent = song.id == currentId,
                    selectionMode = selecting,
                    selected = song.id in selection,
                    onClick = { if (selecting) viewModel.toggleSelection(song.id) else viewModel.playFrom(song) },
                    onLongClick = { viewModel.toggleSelection(song.id) },
                    modifier = Modifier.testTag("song_row"),
                )
            }
        }
    }
    }
}

/** Below this window height (phones in landscape) the title bar collapses on scroll. */
private const val COMPACT_HEIGHT_DP = 480

private val SONG_FILTERS = listOf(SongFilter.ALL, SongFilter.FAVOURITES, SongFilter.MISSING_TAGS)

/** Fixed-height stand-in while a page loads, so the scroll position never jumps. */
@Composable
private fun SongPlaceholderRow() = SkeletonRow()

@Composable
private fun AlbumsTab(viewModel: LibraryViewModel, navigator: Navigator) {
    val albums = viewModel.albums.collectAsStateWithLifecycle().value ?: return SkeletonList(Modifier.fillMaxSize())
    if (albums.isEmpty()) return EmptyState(Icons.Rounded.Album, "No albums", "Songs without album tags are listed under Songs.")
    LazyVerticalGrid(
        columns = GridCells.Adaptive(albumGridMinSize()),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.id }, contentType = { "album" }) { album ->
            AlbumCard(album, onClick = { navigator.album(album.id) })
        }
    }
}

@Composable
private fun ArtistsTab(viewModel: LibraryViewModel, navigator: Navigator) {
    val artists = viewModel.artists.collectAsStateWithLifecycle().value ?: return SkeletonList(Modifier.fillMaxSize())
    if (artists.isEmpty()) return EmptyState(Icons.Rounded.Person, "No artists", "Songs without artist tags are listed under Songs.")
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists, key = { it.name.lowercase() }, contentType = { "artist" }) { artist ->
            CollectionRow(
                title = artist.name,
                subtitle = "${songCountLabel(artist.songCount)} • ${artist.albumCount} album${if (artist.albumCount == 1) "" else "s"}",
                coverSongId = artist.coverSongId,
                coverAlbumId = artist.coverAlbumId,
                onClick = { navigator.artist(artist.name) },
                icon = Icons.Rounded.Person,
                circular = true,
            )
        }
    }
}

@Composable
private fun FoldersTab(viewModel: LibraryViewModel, navigator: Navigator) {
    val folders = viewModel.folders.collectAsStateWithLifecycle().value ?: return SkeletonList(Modifier.fillMaxSize(), artwork = false)
    if (folders.isEmpty()) return EmptyState(Icons.Rounded.Folder, "No folders", "Every folder with music is hidden by your folder rules.")
    LazyColumn(Modifier.fillMaxSize()) {
        items(folders, key = { it.key.id.lowercase() }, contentType = { "folder" }) { folder ->
            CollectionRow(
                title = folder.key.displayName,
                subtitle = "${folder.key.path.ifEmpty { "/" }} • ${songCountLabel(folder.songCount)}",
                coverSongId = -1,
                coverAlbumId = -1,
                onClick = { navigator.folder(folder.key.id) },
                icon = Icons.Rounded.Folder,
            )
        }
    }
}

@Composable
private fun GenresTab(viewModel: LibraryViewModel, navigator: Navigator) {
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize()) {
        items(genres, key = { it.name.lowercase() }, contentType = { "genre" }) { genre ->
            CollectionRow(
                title = genre.name,
                subtitle = songCountLabel(genre.songCount),
                coverSongId = genre.coverSongId,
                coverAlbumId = genre.coverAlbumId,
                onClick = { navigator.genre(genre.name) },
                icon = Icons.Rounded.Style,
            )
        }
    }
}

@Composable
private fun EmptyLibrary(error: String?, onRescan: () -> Unit, onFolders: () -> Unit) {
    // Scrollable so pull-to-refresh still works on an empty library.
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            EmptyState(
                icon = Icons.Rounded.LibraryMusic,
                title = if (error != null) "Couldn't read your music" else "No music found",
                message = error ?: "SVARĀ looks for audio files on this device. Copy some music to your phone, " +
                    "or check which folders are included.",
                action = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRescan) { Text("Scan again") }
                        ChooseMusicFolderButton()
                        OutlinedButton(onClick = onFolders) {
                            Icon(Icons.Rounded.Folder, null)
                            Text("Folder settings", Modifier.padding(start = 8.dp))
                        }
                    }
                },
            )
        }
    }
}

fun songSortLabel(field: SongSortField) = when (field) {
    SongSortField.TITLE -> "Title"
    SongSortField.ARTIST -> "Artist"
    SongSortField.ALBUM -> "Album"
    SongSortField.DATE_ADDED -> "Date added"
    SongSortField.DURATION -> "Duration"
    SongSortField.LOCATION -> "File location"
}

fun albumSortLabel(field: AlbumSortField) = when (field) {
    AlbumSortField.TITLE -> "Title"
    AlbumSortField.ARTIST -> "Artist"
    AlbumSortField.YEAR -> "Year"
    AlbumSortField.SONG_COUNT -> "Number of songs"
}

fun artistSortLabel(field: ArtistSortField) = when (field) {
    ArtistSortField.NAME -> "Name"
    ArtistSortField.SONG_COUNT -> "Number of songs"
}
