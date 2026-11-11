package com.svara.app.library

import com.svara.app.ui.states.SkeletonList
import com.svara.app.ui.artwork.ArtworkKeys
import com.svara.app.ui.presets.common.AlbumHeaderBackdrop
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Image
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svara.app.ui.components.ArtworkImage
import com.svara.app.ui.components.BackButton
import com.svara.app.ui.components.EmptyState
import com.svara.app.ui.components.SectionHeader
import com.svara.app.ui.components.SongRow
import com.svara.app.ui.components.WithSongActions
import com.svara.app.ui.navigation.CollectionType
import com.svara.app.ui.navigation.Navigator
import com.svara.app.appearance.PresetId
import com.svara.app.ui.presets.tala.TalaAlbum
import com.svara.app.ui.theme.LocalAppearance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(navigator: Navigator, viewModel: CollectionViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val currentId by viewModel.currentSongId.collectAsStateWithLifecycle()
    val selecting = selection.isNotEmpty()
    val icon = iconFor(viewModel.type)

    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    // SOLACE draws its own album page; every other collection keeps this screen.
    if (LocalAppearance.current.preset == PresetId.TALA && viewModel.type == CollectionType.ALBUM && !ui.loading) {
        TalaAlbum(navigator, ui.title, ui.subtitle, ui.songs, ui.coverSongId, ui.coverAlbumId, currentId, viewModel::play, viewModel::shuffle, viewModel::playFrom)
        return
    }

    WithSongActions(navigator) { actions ->
        Scaffold(
            topBar = {
                if (selecting) {
                    SelectionTopBar(
                        count = selection.size,
                        onClose = viewModel::clearSelection,
                        onSelectAll = viewModel::selectAll,
                        onPlayNext = { actions.playNext(viewModel.selectedIds()); viewModel.clearSelection() },
                        onAddToQueue = { actions.addToQueue(viewModel.selectedIds()); viewModel.clearSelection() },
                        onAddToPlaylist = { actions.addToPlaylist(viewModel.selectedIds()); viewModel.clearSelection() },
                        onFavourite = { actions.toggleFavorites(viewModel.selectedIds()); viewModel.clearSelection() },
                    )
                } else {
                    TopAppBar(
                        title = { Text(ui.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = { BackButton(navigator::back) },
                        actions = {
                            val first = ui.songs.firstOrNull()
                            if (viewModel.type == CollectionType.ALBUM && first != null) {
                                IconButton(onClick = { navigator.changeCover(first.id, first.albumId) }) {
                                    Icon(Icons.Rounded.Image, contentDescription = "Change album cover")
                                }
                            }
                        },
                    )
                }
            },
        ) { padding ->
            if (ui.loading) {
                Column(Modifier.padding(padding).fillMaxSize()) {
                    // An album's art is known from the route, so it can arrive with the card it came from.
                    viewModel.albumIdOrNull()?.let { albumId ->
                        ArtworkImage(
                            -1, albumId, Modifier.padding(16.dp).size(120.dp), shape = RoundedCornerShape(12.dp),
                            placeholderIcon = icon, iconSize = 48.dp, sharedKey = ArtworkKeys.album(albumId),
                        )
                    }
                    SkeletonList()
                }
                return@Scaffold
            }
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item(key = "header") {
                    Box {
                    // Album pages: the cached cinematic blur behind the header (none in Efficiency).
                    if (viewModel.type == CollectionType.ALBUM) {
                        AlbumHeaderBackdrop(
                            ui.coverSongId, ui.coverAlbumId,
                            fadeTo = MaterialTheme.colorScheme.background, text = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    CollectionHeader(
                        title = ui.title,
                        subtitle = ui.subtitle,
                        coverSongId = ui.coverSongId,
                        coverAlbumId = ui.coverAlbumId,
                        icon = icon,
                        showCover = viewModel.type in setOf(CollectionType.ALBUM, CollectionType.ARTIST, CollectionType.GENRE, CollectionType.FOLDER),
                        canPlay = ui.songs.isNotEmpty(),
                        onPlay = viewModel::play,
                        onShuffle = viewModel::shuffle,
                        sharedKey = if (viewModel.type == CollectionType.ALBUM) ArtworkKeys.album(ui.coverAlbumId) else null,
                    )
                    }
                }
                if (ui.albums.size > 1) {
                    item(key = "albums") {
                        SectionHeader("Albums")
                        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                            items(ui.albums, key = { it.id }) { album ->
                                AlbumCard(album, onClick = { navigator.album(album.id) }, modifier = Modifier.width(150.dp))
                            }
                        }
                        SectionHeader("Songs")
                    }
                }
                if (ui.songs.isEmpty()) {
                    item(key = "empty") { EmptyState(icon, "Nothing here yet", ui.emptyMessage) }
                }
                itemsIndexed(ui.songs, key = { _, s -> s.id }) { index, song ->
                    val annotation = ui.annotations[song.id]
                    SongRow(
                        song = song,
                        actions = actions,
                        isCurrent = song.id == currentId,
                        selectionMode = selecting,
                        selected = song.id in selection,
                        onClick = { if (selecting) viewModel.toggleSelection(song.id) else viewModel.playFrom(index) },
                        onLongClick = { viewModel.toggleSelection(song.id) },
                        showArtwork = viewModel.type != CollectionType.ALBUM,
                        leading = if (viewModel.type == CollectionType.ALBUM) {
                            { TrackNumber(song.trackNumber) }
                        } else null,
                    )
                    if (annotation != null) {
                        Text(
                            annotation,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 76.dp, bottom = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackNumber(number: Int) {
    Text(
        if (number > 0) number.toString() else "–",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(32.dp),
    )
}

@Composable
fun CollectionHeader(
    title: String,
    subtitle: String,
    coverSongId: Long,
    coverAlbumId: Long,
    icon: ImageVector,
    showCover: Boolean,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    sharedKey: Any? = null,
    /** Replaces the single cover, e.g. with a playlist's layered composition. */
    artwork: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (artwork != null) {
                Box(Modifier.size(120.dp)) { artwork() }
            } else if (showCover) {
                ArtworkImage(coverSongId, coverAlbumId, Modifier.size(120.dp), shape = RoundedCornerShape(12.dp), placeholderIcon = icon, iconSize = 48.dp, sharedKey = sharedKey, fallbackLabel = title)
            } else {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (canPlay) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Text("Play", Modifier.padding(start = 8.dp))
                }
                FilledTonalButton(onClick = onShuffle, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Shuffle, null)
                    Text("Shuffle", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

private fun iconFor(type: CollectionType): ImageVector = when (type) {
    CollectionType.ALBUM -> Icons.Rounded.Album
    CollectionType.ARTIST -> Icons.Rounded.Person
    CollectionType.FOLDER -> Icons.Rounded.Folder
    CollectionType.GENRE -> Icons.Rounded.Style
    CollectionType.FAVORITES -> Icons.Rounded.Favorite
    CollectionType.RECENTLY_PLAYED -> Icons.Rounded.History
    CollectionType.MOST_PLAYED -> Icons.AutoMirrored.Rounded.TrendingUp
    CollectionType.RECENTLY_ADDED -> Icons.Rounded.NewReleases
}
