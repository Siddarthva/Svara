package com.svara.app.ui.presets

import com.svara.app.ui.motion.SongChange
import com.svara.app.appearance.tokens.PageMotion
import com.svara.app.ui.artwork.ArtworkKeys
import com.svara.app.ui.theme.AnahataTokens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.svara.app.appearance.LayoutSet
import com.svara.app.ui.components.ArtworkImage
import com.svara.app.ui.theme.Elevation
import com.svara.app.ui.theme.LocalAppearance
import com.svara.app.ui.theme.Spacing
import com.svara.app.ui.theme.rememberArtworkShape
import com.svara.app.ui.theme.rememberHaptic

/** What the mini-player shows; null [title] means nothing is queued. */
class MiniPlayerModel(
    val songId: Long,
    val albumId: Long,
    val title: String?,
    val artist: String,
    val isPlaying: Boolean,
    val hasNext: Boolean,
    val progress: () -> Float,
)

/**
 * Mini-player per preset. SOLACE's is persistent (shown even when nothing is queued) with large
 * play and skip targets; ZENITH's may be translucent when the policy allows; the rest are compact.
 */
@Composable
fun PresetMiniPlayer(
    model: MiniPlayerModel,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val a = LocalAppearance.current
    val dense = a.layout == LayoutSet.DENSE
    val title = model.title
    if (title == null && !dense) return
    val haptic = rememberHaptic()
    val scheme = MaterialTheme.colorScheme
    val container = if (a.glass) scheme.surfaceContainerHigh.copy(alpha = 0.82f) else scheme.surfaceContainerHigh

    Surface(color = container, tonalElevation = Elevation.level2, modifier = Modifier.fillMaxWidth().testTag("mini_player")) {
        Column {
            if (title != null) {
                LinearProgressIndicator(
                    progress = model.progress,
                    modifier = Modifier.fillMaxWidth().height(if (dense) AnahataTokens.miniProgressDense else AnahataTokens.miniProgress),
                    drawStopIndicator = {},
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (dense) AnahataTokens.miniHeightDense else AnahataTokens.miniHeight)
                    .then(
                        if (title == null) Modifier else Modifier
                            .clickable(onClickLabel = "Open now playing", onClick = onOpen)
                            // Swipe up to expand; swipe sideways to skip.
                            .pointerInput(Unit) {
                                var dx = 0f
                                var dy = 0f
                                detectDragGestures(
                                    onDragStart = { dx = 0f; dy = 0f },
                                    onDragEnd = {
                                        val threshold = AnahataTokens.miniSwipe.toPx()
                                        when {
                                            dy < -threshold && -dy > kotlin.math.abs(dx) -> onOpen()
                                            dx < -threshold -> onNext()
                                            dx > threshold -> onPrevious()
                                        }
                                    },
                                ) { change, amount ->
                                    change.consume()
                                    dx += amount.x
                                    dy += amount.y
                                }
                            }
                    )
                    .padding(start = Spacing.m, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s)
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (title == null) "Nothing playing" else "Now playing: $title by ${model.artist}"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(model.songId, model.albumId, Modifier.size(if (dense) AnahataTokens.miniArtDense else AnahataTokens.miniArt), shape = rememberArtworkShape(), sharedKey = ArtworkKeys.nowPlaying(model.songId))
                Spacer(Modifier.width(Spacing.m))
                SongChange(title to model.artist, Modifier.weight(1f), durationMs = PageMotion.MINI_PLAYER_MS) { (t, a) -> Column {
                    Text(t ?: "Nothing playing", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (t != null) {
                        Text(
                            a,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } }
                if (dense) {
                    IconButton(onClick = { haptic(); onPrevious() }, enabled = title != null, modifier = Modifier.size(AnahataTokens.denseButton)) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(AnahataTokens.denseIcon))
                    }
                    FilledIconButton(onClick = { haptic(); onPlayPause() }, enabled = title != null, modifier = Modifier.size(AnahataTokens.denseButton)) {
                        Icon(
                            if (model.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (model.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(AnahataTokens.denseIcon),
                        )
                    }
                    IconButton(onClick = { haptic(); onNext() }, enabled = model.hasNext, modifier = Modifier.size(AnahataTokens.denseButton)) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(AnahataTokens.denseIcon))
                    }
                } else {
                    IconButton(onClick = { haptic(); onPlayPause() }) {
                        Icon(
                            if (model.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (model.isPlaying) "Pause" else "Play",
                        )
                    }
                    IconButton(onClick = { haptic(); onNext() }, enabled = model.hasNext) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next")
                    }
                }
            }
        }
    }
}
