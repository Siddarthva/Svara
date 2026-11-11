package com.svara.app.ui.presets.common

import com.svara.app.ui.components.ArtworkImage
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Immutable
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.svara.app.library.PresetLibraryViewModel
import com.svara.app.library.PresetSort
import com.svara.app.ui.components.ChooseMusicFolderButton
import com.svara.app.ui.components.EmptyState
import com.svara.app.ui.components.WithSongActions
import com.svara.app.ui.components.songCountLabel
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.theme.LocalPresetUi

/** What a library filter pill shows. */
enum class LibFilter { PLAYLISTS, SONGS, ALBUMS, ARTISTS, LIKED, FOLDERS }

class LibFilterSpec(val filter: LibFilter, val label: String)

/** Sort options that make sense for each list. */
fun sortsFor(filter: LibFilter): List<PresetSort> = when (filter) {
    LibFilter.SONGS -> PresetSort.entries
    LibFilter.ALBUMS -> listOf(PresetSort.TITLE, PresetSort.ARTIST)
    LibFilter.ARTISTS -> listOf(PresetSort.TITLE)
    LibFilter.PLAYLISTS -> listOf(PresetSort.RECENTLY_ADDED, PresetSort.TITLE)
    LibFilter.LIKED, LibFilter.FOLDERS -> emptyList()
}

/** Shown in place of a list when the library has no music yet. */
@Composable
fun PresetEmptyLibrary(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Rounded.LibraryMusic,
        title = "No music yet",
        message = "SVARĀ didn't find any music on this device. Choose the folder where your music lives, or copy some songs to the phone.",
        modifier = modifier,
        action = { ChooseMusicFolderButton() },
    )
}

/**
 * A library with a title, a row of pill filters and one list per filter. Artists use circular
 * art. Songs are paged with stable keys; every row has the shared song menu.
 */
@Composable
fun PillFilterLibrary(
    navigator: Navigator,
    title: String,
    filters: List<LibFilterSpec>,
    sortLabel: (PresetSort) -> String = { it.label },
    showSort: Boolean = true,
    /** Halo's source offers a grid view next to the list view. */
    gridToggle: Boolean = false,
    vm: PresetLibraryViewModel = hiltViewModel(),
) {
    val p = LocalPresetUi.current
    var selected by rememberSaveable { mutableStateOf(filters.first().filter) }
    var grid by rememberSaveable { mutableStateOf(false) }
    val sort by vm.sort.collectAsStateWithLifecycle()
    val count by vm.songCount.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(p.colors.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = p.spacing.screenMargin, end = p.spacing.xs, top = p.spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = p.type.display, color = p.colors.primaryText, modifier = Modifier.weight(1f).semantics { heading() })
            val options = sortsFor(selected)
            if (gridToggle && selected in GRIDDABLE) IconButton(onClick = { grid = !grid }) {
                Icon(
                    if (grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                    contentDescription = if (grid) "Show as list" else "Show as grid",
                    tint = p.colors.primaryText,
                )
            }
            if (showSort && options.isNotEmpty()) SortDropdown(options, sort.takeIf { it in options } ?: options.first(), sortLabel, vm::setSort)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = p.spacing.screenMargin),
            horizontalArrangement = Arrangement.spacedBy(p.spacing.s),
        ) {
            items(filters, key = { it.filter }) { f ->
                PresetPill(f.label, f.filter == selected, onClick = {
                    selected = f.filter
                    sortsFor(f.filter).firstOrNull()?.let { if (sort !in sortsFor(f.filter)) vm.setSort(it) }
                })
            }
        }
        if (count == 0) {
            PresetEmptyLibrary(Modifier.weight(1f))
            return@Column
        }
        Box(Modifier.weight(1f)) { LibraryList(navigator, selected, vm, grid = gridToggle && grid) }
    }
}

@Composable
private fun SortDropdown(options: List<PresetSort>, current: PresetSort, label: (PresetSort) -> String, onSelect: (PresetSort) -> Unit) {
    val p = LocalPresetUi.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort by ${label(current)}", tint = p.colors.primaryText)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(label(o)) },
                    trailingIcon = { if (o == current) Icon(Icons.Rounded.Check, contentDescription = "Selected") },
                    onClick = { open = false; onSelect(o) },
                )
            }
        }
    }
}

@Composable
fun LibraryList(navigator: Navigator, filter: LibFilter, vm: PresetLibraryViewModel, grid: Boolean = false) {
    val p = LocalPresetUi.current
    val current by vm.currentSongId.collectAsStateWithLifecycle()
    val padding = PaddingValues(top = p.spacing.s, bottom = p.spacing.l)
    WithSongActions(navigator) { actions ->
        when (filter) {
            LibFilter.SONGS -> {
                val songs = vm.songs.collectAsLazyPagingItems()
                LazyColumn(Modifier.fillMaxSize().testTag("songs_list"), contentPadding = padding) {
                    items(songs.itemCount, key = songs.itemKey { it.id }, contentType = { SONG_ROW }) { i ->
                        val song = songs[i] ?: return@items
                        PresetSongRow(song, actions, song.id == current, onClick = { vm.playFrom(song) }, modifier = Modifier.testTag("song_row"))
                    }
                }
            }
            LibFilter.LIKED -> {
                val liked by vm.favorites.collectAsStateWithLifecycle()
                LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                    if (liked.isEmpty()) item(key = "empty") { EmptyLine("No liked songs yet. Tap the heart on Now Playing to add one.") }
                    itemsIndexed(liked, key = { _, s -> s.id }, contentType = { _, _ -> SONG_ROW }) { i, song ->
                        PresetSongRow(song, actions, song.id == current, onClick = { vm.play(liked, i) })
                    }
                }
            }
            LibFilter.ALBUMS -> {
                val albums by vm.albums.collectAsStateWithLifecycle()
                Entries(albums.map { a -> Entry("a${a.id}", a.title, a.artist, a.coverSongId, a.id, false) { navigator.album(a.id) } }, grid, "No albums yet.")
            }
            LibFilter.ARTISTS -> {
                val artists by vm.artists.collectAsStateWithLifecycle()
                Entries(
                    artists.map { a -> Entry("r${a.sortKey}", a.name, songCountLabel(a.songCount), a.coverSongId, a.coverAlbumId, true) { navigator.artist(a.name) } },
                    grid, "No artists yet.",
                )
            }
            LibFilter.PLAYLISTS -> {
                val lists by vm.playlists.collectAsStateWithLifecycle()
                Entries(
                    lists.map { pl -> Entry("p${pl.id}", pl.name, songCountLabel(pl.songCount), pl.coverSongId ?: -1, pl.coverAlbumId ?: -1, false) { navigator.playlist(pl.id) } },
                    grid, "No playlists yet. Add songs to a playlist from any song's menu.",
                )
            }
            LibFilter.FOLDERS -> {
                val folders by vm.folders.collectAsStateWithLifecycle()
                LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                    if (folders.isEmpty()) item(key = "empty") { EmptyLine("No folders with music yet.") }
                    items(folders, key = { it.key.id.lowercase() }, contentType = { ENTRY_ROW }) { f ->
                        PresetRow(f.key.displayName, "${f.key.path} • ${songCountLabel(f.songCount)}", f.coverSongId, f.coverAlbumId, onClick = { navigator.folder(f.key.id) })
                    }
                }
            }
        }
    }
}

/** One quiet line where a list has nothing to show yet. */
@Composable
private fun EmptyLine(text: String) {
    val p = LocalPresetUi.current
    Text(text, style = p.type.body, color = p.colors.secondaryText, modifier = Modifier.padding(horizontal = p.spacing.screenMargin, vertical = p.spacing.l))
}

/** Lists that can switch to a grid. */
private val GRIDDABLE = setOf(LibFilter.ALBUMS, LibFilter.ARTISTS, LibFilter.PLAYLISTS)

/** One album, artist or playlist in a library list or grid. */
@Immutable
private class Entry(val key: String, val title: String, val subtitle: String, val songId: Long, val albumId: Long, val circular: Boolean, val onClick: () -> Unit)

/** Rows, or a two-column grid of covers; stable keys either way. */
@Composable
private fun Entries(entries: List<Entry>, grid: Boolean, empty: String) {
    val p = LocalPresetUi.current
    val padding = PaddingValues(top = p.spacing.s, bottom = p.spacing.l)
    if (entries.isEmpty()) {
        EmptyLine(empty)
        return
    }
    if (!grid) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            items(entries, key = { it.key }, contentType = { ENTRY_ROW }) { e -> PresetRow(e.title, e.subtitle, e.songId, e.albumId, onClick = e.onClick, circular = e.circular) }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = p.spacing.screenMargin, vertical = p.spacing.s),
        horizontalArrangement = Arrangement.spacedBy(p.spacing.gridGutter),
        verticalArrangement = Arrangement.spacedBy(p.spacing.l),
    ) {
        gridItems(entries, key = { it.key }, contentType = { ENTRY_ROW }) { e ->
            Column(Modifier.presetClickable { e.onClick() }.semantics(mergeDescendants = true) { contentDescription = "${e.title}, ${e.subtitle}" }) {
                ArtworkImage(
                    e.songId, e.albumId, Modifier.fillMaxWidth().aspectRatio(1f),
                    shape = if (e.circular) p.shapes.circle else p.shapes.artwork, iconSize = p.sizes.iconLarge,
                )
                Text(e.title, style = p.type.bodyEmphasis, color = p.colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = p.spacing.s))
                Text(e.subtitle, style = p.type.subhead, color = p.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private const val SONG_ROW = "song"
private const val ENTRY_ROW = "entry"
