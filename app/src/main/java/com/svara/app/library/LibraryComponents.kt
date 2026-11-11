package com.svara.app.library

import androidx.compose.material.icons.rounded.FavoriteBorder
import com.svara.app.ui.artwork.ArtworkFormat
import com.svara.app.ui.artwork.ArtRef
import com.svara.app.ui.artwork.ArtworkFrame
import androidx.compose.foundation.layout.Box
import com.svara.app.ui.artwork.ArtworkKeys
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.svara.app.appearance.LayoutSet
import com.svara.app.ui.presets.PresetTile
import com.svara.app.ui.theme.LocalAppearance
import com.svara.app.ui.theme.rememberArtworkShape
import com.svara.app.domain.model.Album
import com.svara.app.ui.components.ArtworkImage
import com.svara.app.ui.components.songCountLabel

/** Replaces the top bar while songs are selected. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: (() -> Unit)?,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onFavourite: (() -> Unit)? = null,
) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Cancel selection") }
        },
        actions = {
            if (onSelectAll != null) {
                IconButton(onClick = onSelectAll) { Icon(Icons.Rounded.SelectAll, contentDescription = "Select all") }
            }
            IconButton(onClick = onPlayNext, enabled = count > 0) { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = "Play next") }
            IconButton(onClick = onAddToQueue, enabled = count > 0) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Add to queue")
            }
            IconButton(onClick = onAddToPlaylist, enabled = count > 0) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Add to playlist")
            }
            if (onFavourite != null) IconButton(onClick = onFavourite, enabled = count > 0) {
                Icon(Icons.Rounded.FavoriteBorder, contentDescription = "Add to or remove from favourites")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    )
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // OCTAVE's albums are colour-led tiles; the other presets keep the plain card.
    if (LocalAppearance.current.layout == LayoutSet.COLOUR_TILES) {
        PresetTile(album.title, album.artist + if (album.year > 0) " • ${album.year}" else "", album.coverSongId, album.id, onClick, modifier.padding(4.dp))
        return
    }
    Column(
        modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Album ${album.title} by ${album.artist}, ${songCountLabel(album.songCount)}" }
            .padding(8.dp),
    ) {
        ArtworkFrame(
            ArtRef(album.coverSongId, album.id), ArtworkFormat.SQUARE, rememberArtworkShape(), Modifier.fillMaxWidth(),
            label = album.title, sharedKey = ArtworkKeys.album(album.id),
        )
        Spacer(Modifier.height(8.dp))
        Text(album.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            album.artist + if (album.year > 0) " • ${album.year}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Row used for artists, folders and genres. */
@Composable
fun CollectionRow(
    title: String,
    subtitle: String,
    coverSongId: Long,
    coverAlbumId: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Person,
    circular: Boolean = false,
    /** Replaces the single cover, e.g. with a playlist's layered composition. */
    artwork: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (artwork != null) {
            Box(Modifier.size(48.dp)) { artwork() }
        } else if (circular) {
            ArtworkImage(coverSongId, coverAlbumId, Modifier.size(48.dp), shape = CircleShape, placeholderIcon = icon)
        } else {
            ArtworkImage(coverSongId, coverAlbumId, Modifier.size(48.dp), shape = rememberArtworkShape(), placeholderIcon = icon)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
