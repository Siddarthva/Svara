package com.svara.app.ui.components

import androidx.compose.runtime.collectAsState
import dagger.hilt.android.EntryPointAccessors
import com.svara.app.di.CoverEntryPoint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.svara.app.ui.artwork.ArtworkFallbackContent
import com.svara.app.ui.artwork.readableOn
import com.svara.app.ui.artwork.sharedArtwork
import com.svara.app.ui.presets.common.rememberAlbumColour
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import com.svara.app.ui.theme.LocalReducedMotion
import com.svara.app.data.artwork.Artwork
import java.util.Locale

/**
 * Album art with a neutral placeholder underneath while it loads. When the song has no artwork
 * the placeholder takes the album's deterministic fallback colour with a music symbol and, when
 * [fallbackLabel] is given and the art is large enough, that text. [sharedKey] makes the art a
 * shared element (see [sharedArtwork]); it does nothing in Efficiency or with reduced motion.
 */
@Composable
fun ArtworkImage(
    songId: Long,
    albumId: Long,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    placeholderIcon: ImageVector = Icons.Rounded.MusicNote,
    iconSize: Dp = 24.dp,
    sharedKey: Any? = null,
    fallbackLabel: String? = null,
) {
    var missing by remember(songId, albumId) { mutableStateOf(songId <= 0 && albumId <= 0) }
    // Only covers that failed to load pay for the palette lookup.
    val fallback = if (missing && (songId > 0 || albumId > 0)) rememberAlbumColour(songId, albumId) else null
    val scheme = MaterialTheme.colorScheme
    val onFallback = remember(fallback) { fallback?.let { readableOn(it) } }
    Box(
        modifier = modifier
            .then(if (sharedKey != null) Modifier.sharedArtwork(sharedKey) else Modifier)
            .clip(shape)
            .background(fallback ?: scheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (fallback != null && fallbackLabel != null) {
            ArtworkFallbackContent(placeholderIcon, iconSize, fallbackLabel, onFallback ?: scheme.onSurfaceVariant)
        } else {
            Icon(
                imageVector = placeholderIcon,
                contentDescription = null,
                // Full strength: at reduced alpha the symbol could fall under 3:1 on the plain tile.
                tint = onFallback ?: scheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize),
            )
        }
        if (songId > 0 || albumId > 0) {
            val context = LocalContext.current
            val crossfade = !LocalReducedMotion.current
            val scroll = LocalScrollTracker.current
            // Collects only this song's cover: a change elsewhere doesn't recompose this row.
            val covers = remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, CoverEntryPoint::class.java).covers() }
            val cover by remember(songId, albumId) { covers.versionOf(songId, albumId) }.collectAsState(covers.custom(songId, albumId))
            AsyncImage(
                model = remember(songId, albumId, crossfade, cover) {
                    ImageRequest.Builder(context)
                        .data(Artwork(songId, albumId, cover?.scope, cover?.version ?: 0))
                        // No crossfade for covers that arrive while a list flings past them.
                        .crossfade(crossfade && scroll?.flinging != true).build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Error -> missing = true
                        is AsyncImagePainter.State.Success -> missing = false
                        else -> Unit
                    }
                },
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        trailing?.invoke()
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
    }
}

/** Sort picker: choose a field, tap the selected field again to flip the direction. */
@Composable
fun <T : Enum<T>> SortMenuButton(
    fields: List<T>,
    selected: T,
    ascending: Boolean,
    label: (T) -> String,
    onSelect: (field: T, ascending: Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort, currently ${label(selected)} ${if (ascending) "ascending" else "descending"}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fields.forEach { field ->
                val isSelected = field == selected
                DropdownMenuItem(
                    text = { Text(label(field)) },
                    leadingIcon = { if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null) },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                contentDescription = if (ascending) "Ascending" else "Descending",
                            )
                        }
                    },
                    onClick = {
                        onSelect(field, if (isSelected) !ascending else true)
                        open = false
                    },
                )
            }
        }
    }
}

/** Time left, shown as "−m:ss" next to the elapsed time. */
fun formatRemaining(positionMs: Long, durationMs: Long): String =
    "−" + formatDuration((durationMs - positionMs).coerceAtLeast(0))

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}

fun formatTotalDuration(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60} hr ${minutes % 60} min" else "$minutes min"
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> String.format(Locale.ROOT, "%.0f KB", bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

fun songCountLabel(count: Int): String = if (count == 1) "1 song" else "$count songs"
