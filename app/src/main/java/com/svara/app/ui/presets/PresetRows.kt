package com.svara.app.ui.presets

import com.svara.app.ui.theme.AnahataTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.svara.app.appearance.Contrast
import com.svara.app.appearance.LayoutSet
import com.svara.app.appearance.Palettes
import com.svara.app.domain.model.Song
import com.svara.app.ui.components.ArtworkImage
import com.svara.app.ui.components.SongActions
import com.svara.app.ui.theme.LocalAppearance
import com.svara.app.ui.theme.Radii
import com.svara.app.ui.theme.Spacing
import com.svara.app.ui.theme.rememberArtworkShape
import com.svara.app.ui.theme.toColor

/** SOLACE: one-tap "Add to queue" on every song row. */
@Composable
fun PresetRowActions(song: Song, actions: SongActions) {
    if (LocalAppearance.current.layout != LayoutSet.DENSE) return
    IconButton(onClick = { actions.addToQueue(song) }) {
        Icon(Icons.Rounded.AddToQueue, contentDescription = "Add ${song.title} to queue")
    }
}

/** SOLACE: create a playlist from a row's menu in one step. */
@Composable
fun PresetMenuItems(song: Song, actions: SongActions, dismiss: () -> Unit) {
    if (LocalAppearance.current.layout != LayoutSet.DENSE) return
    DropdownMenuItem(
        text = { Text("New playlist with this song") },
        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
        onClick = { actions.newPlaylistWith(listOf(song.id)); dismiss() },
    )
}

/** Tile width per preset, so the home rows match each layout's density. */
@Composable
fun presetTileWidth(): Dp = when (LocalAppearance.current.layout) {
    LayoutSet.ARTWORK_CENTRED -> AnahataTokens.tileArtworkCentred
    LayoutSet.EDITORIAL -> AnahataTokens.tileEditorial
    LayoutSet.DENSE -> AnahataTokens.tileDense
    LayoutSet.COLOUR_TILES -> AnahataTokens.tileColour
    LayoutSet.IMMERSIVE -> AnahataTokens.tileColour
}

/**
 * A cover tile for home rows and album grids. OCTAVE's tiles are colour-led: the cover sits on a
 * colour block (from the preset's tile palette, keyed by name, so a collection keeps its colour).
 */
@Composable
fun PresetTile(
    title: String,
    subtitle: String,
    songId: Long,
    albumId: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val a = LocalAppearance.current
    val colourLed = a.layout == LayoutSet.COLOUR_TILES && !a.highContrast
    val scheme = MaterialTheme.colorScheme
    val tile = if (colourLed) Palettes.tileFor(a.theme.spec.palette, title).toColor() else null
    Column(
        modifier
            .clip(RoundedCornerShape(if (colourLed) Radii.medium else Radii.small))
            .then(if (tile != null) Modifier.background(tile) else Modifier)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $subtitle" }
            .padding(if (colourLed) Spacing.s else Spacing.xs),
    ) {
        ArtworkImage(
            songId, albumId,
            Modifier.fillMaxWidth().aspectRatio(1f),
            shape = rememberArtworkShape(if (a.layout == LayoutSet.EDITORIAL) Radii.small / 2 else Radii.small),
            placeholderIcon = Icons.AutoMirrored.Rounded.QueueMusic,
            iconSize = AnahataTokens.tileIcon,
        )
        Spacer(Modifier.height(AnahataTokens.tileGap))
        // Colour tiles carry their own text colour, chosen for contrast with the tile.
        val onTile = if (tile != null) Contrast.onColour(Palettes.tileFor(a.theme.spec.palette, title)).toColor() else scheme.onSurface
        Text(
            title,
            style = if (a.layout == LayoutSet.EDITORIAL) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = onTile,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = if (tile != null) onTile.copy(alpha = 0.85f) else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Album grid cell size: Halo's album-first grid uses large covers. */
@Composable
fun albumGridMinSize(): Dp = when (LocalAppearance.current.layout) {
    LayoutSet.EDITORIAL -> AnahataTokens.gridEditorial
    LayoutSet.DENSE -> AnahataTokens.gridDense
    else -> AnahataTokens.gridDefault
}

/** Rows used where a compact list replaces tile rows (Compact library home layout). */
@Composable
fun CompactTileRow(title: String, subtitle: String, songId: Long, albumId: Long, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $subtitle" }
            .padding(horizontal = Spacing.l, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(songId, albumId, Modifier.width(Spacing.touch).aspectRatio(1f), shape = rememberArtworkShape())
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
