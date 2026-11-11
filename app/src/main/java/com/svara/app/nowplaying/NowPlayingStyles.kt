package com.svara.app.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import com.svara.app.appearance.Argb
import com.svara.app.appearance.BackgroundKind
import com.svara.app.appearance.Contrast
import com.svara.app.appearance.colour.ContrastEngine
import com.svara.app.appearance.tokens.CinemaBackdrop
import com.svara.app.playback.PlaybackState
import com.svara.app.ui.artwork.artworkAura
import com.svara.app.ui.components.ArtworkImage
import com.svara.app.ui.presets.PlayerParts
import com.svara.app.ui.presets.common.AlbumHeaderBackdrop
import com.svara.app.ui.presets.common.rememberAlbumColour
import com.svara.app.ui.theme.LocalAppearance
import com.svara.app.ui.theme.LocalPresetUi
import com.svara.app.ui.theme.NowPlayingTokens
import com.svara.app.ui.theme.Spacing
import com.svara.app.ui.theme.rememberArtworkShape

/** Close, title, audio information and more options; shared by the Cinema and Compact styles. */
@Composable
fun NowPlayingTopBar(onClose: () -> Unit, onInfo: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close now playing") }
        Text(
            "Now playing",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        IconButton(onClick = onInfo) { Icon(Icons.Rounded.Info, contentDescription = "Audio information") }
        IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, contentDescription = "More options") }
    }
}

/** Everything the extra-controls row can do. */
class ExtraActions(
    val sleepActive: Boolean,
    val showLyrics: Boolean,
    val onSleep: () -> Unit,
    val onToggleLyrics: () -> Unit,
    val onQueue: () -> Unit,
    val onSpeed: () -> Unit,
    val onInfo: () -> Unit,
    val onEqualizer: () -> Unit,
)

/**
 * Lyrics, queue, sleep timer, speed, equalizer and audio information. With [expandable] the row
 * stays behind a "More controls" button until asked for.
 */
@Composable
fun ExtraControls(actions: ExtraActions, expandable: Boolean, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    if (expandable && !expanded) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = Spacing.touch)) { Text("More controls") }
        }
        return
    }
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconToggleButton(checked = actions.showLyrics, onCheckedChange = { actions.onToggleLyrics() }) {
            Icon(
                Icons.Rounded.Lyrics,
                contentDescription = if (actions.showLyrics) "Hide lyrics" else "Show lyrics",
                tint = if (actions.showLyrics) scheme.primary else scheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = actions.onQueue) {
            Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Queue", tint = scheme.onSurfaceVariant)
        }
        IconButton(onClick = actions.onSleep) {
            Icon(
                Icons.Rounded.Bedtime,
                contentDescription = if (actions.sleepActive) "Sleep timer, on" else "Sleep timer",
                tint = if (actions.sleepActive) scheme.primary else scheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = actions.onSpeed) {
            Icon(Icons.Rounded.Speed, contentDescription = "Playback speed", tint = scheme.onSurfaceVariant)
        }
        IconButton(onClick = actions.onEqualizer) {
            Icon(Icons.Rounded.Equalizer, contentDescription = "Equalizer", tint = scheme.onSurfaceVariant)
        }
        IconButton(onClick = actions.onInfo) {
            Icon(Icons.Rounded.Info, contentDescription = "Audio information", tint = scheme.onSurfaceVariant)
        }
        if (expandable) TextButton(onClick = { expanded = false }, modifier = Modifier.heightIn(min = Spacing.touch)) { Text("Less") }
    }
}


/**
 * Cinema: large artwork over the preset's signature backdrop, with title, progress and transport
 * only; extra controls wait behind "More controls". Lyrics replace the artwork when shown.
 */
@Composable
fun CinemaPlayer(
    parts: PlayerParts,
    songId: Long,
    albumId: Long,
    showLyrics: Boolean,
    topBar: @Composable () -> Unit,
    extras: ExtraActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        CinemaBackground(songId, albumId, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            topBar()
            Box(Modifier.weight(1f).fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                if (showLyrics) {
                    parts.lyrics(Modifier.fillMaxSize())
                } else {
                    parts.artwork(
                        Modifier
                            .widthIn(max = NowPlayingTokens.cinemaMaxArtwork)
                            .fillMaxWidth(NowPlayingTokens.CINEMA_ARTWORK_FRACTION)
                            .artworkAura(songId, albumId),
                    )
                }
            }
            Column(Modifier.padding(horizontal = Spacing.xl)) {
                parts.info(Modifier.fillMaxWidth(), true)
                Spacer(Modifier.height(Spacing.s))
                parts.progress(Modifier.fillMaxWidth())
                parts.transport(Modifier.fillMaxWidth(), true, null)
                ExtraControls(extras, expandable = true)
                Spacer(Modifier.height(Spacing.l))
            }
        }
    }
}

/**
 * Cinema's background, chosen by the preset's signature: the cached blurred cover, a colour
 * field or a top gradient in the cover's cached colour. Static. Each keeps text at 4.5:1 (the
 * overlay or tint strength is computed against the theme's text colours). Plain background in
 * Efficiency, battery saver and high contrast.
 */
@Composable
private fun CinemaBackground(songId: Long, albumId: Long, modifier: Modifier) {
    val a = LocalAppearance.current
    val p = LocalPresetUi.current
    val scheme = MaterialTheme.colorScheme
    Box(modifier.background(scheme.background)) {
        if (a.highContrast || BackgroundKind.CACHED_PALETTE !in a.limits.backgrounds) return@Box
        when (p.signature.cinemaBackdrop) {
            CinemaBackdrop.BLURRED_COVER -> AlbumHeaderBackdrop(songId, albumId, scheme.background, scheme.onSurface, Modifier.fillMaxSize())
            CinemaBackdrop.COLOUR_FIELD, CinemaBackdrop.GRADIENT -> {
                val colour = rememberAlbumColour(songId, albumId) ?: return@Box
                val field = p.signature.cinemaBackdrop == CinemaBackdrop.COLOUR_FIELD
                val desired = if (field) NowPlayingTokens.COLOUR_FIELD_ALPHA else NowPlayingTokens.GRADIENT_ALPHA
                val safe = remember(colour, scheme.background, scheme.onSurface, scheme.onSurfaceVariant, desired) {
                    ContrastEngine.maxTintAlpha(
                        colour.argb(), scheme.background.argb(),
                        listOf(scheme.onSurface.argb(), scheme.onSurfaceVariant.argb()),
                        Contrast.AA_TEXT, desired.toDouble(),
                    ).toFloat()
                }
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        if (field) drawRect(colour.copy(alpha = safe))
                        else drawRect(Brush.verticalGradient(listOf(colour.copy(alpha = safe), colour.copy(alpha = 0f))))
                    },
                )
            }
        }
    }
}

/**
 * Compact: small artwork beside the title, every control visible, and the next songs in the
 * queue (lyrics take their place when shown). Tapping an upcoming song plays it.
 */
@Composable
fun CompactPlayer(
    parts: PlayerParts,
    state: PlaybackState,
    showLyrics: Boolean,
    topBar: @Composable () -> Unit,
    extras: ExtraActions,
    onSkipTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = state.current ?: return
    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        topBar()
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            parts.artwork(Modifier.size(NowPlayingTokens.compactArtwork).artworkAura(current.songId, current.albumId))
            Spacer(Modifier.width(Spacing.l))
            parts.info(Modifier.weight(1f), false)
        }
        Column(Modifier.padding(horizontal = Spacing.l)) {
            Spacer(Modifier.height(Spacing.s))
            parts.progress(Modifier.fillMaxWidth())
            parts.transport(Modifier.fillMaxWidth(), false, null)
            ExtraControls(extras, expandable = false)
        }
        HorizontalDivider()
        if (showLyrics) parts.lyrics(Modifier.weight(1f).fillMaxWidth().padding(Spacing.l))
        else UpNext(state, onSkipTo, extras.onQueue, Modifier.weight(1f))
    }
}

@Composable
private fun UpNext(state: PlaybackState, onSkipTo: (Int) -> Unit, onOpenQueue: () -> Unit, modifier: Modifier) {
    val start = state.currentPosition + 1
    val upcoming = if (start in 1..state.queue.lastIndex) state.queue.subList(start, minOf(state.queue.size, start + NowPlayingTokens.UP_NEXT_COUNT)) else emptyList()
    val remaining = (state.queue.size - start).coerceAtLeast(0)
    val shape = rememberArtworkShape()
    LazyColumn(modifier.fillMaxWidth()) {
        item(key = "title") {
            Row(Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text("Up next", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = onOpenQueue, modifier = Modifier.heightIn(min = Spacing.touch)) {
                    Text(if (remaining > 0) "Open queue ($remaining)" else "Open queue")
                }
            }
        }
        if (upcoming.isEmpty()) item(key = "empty") {
            Text(
                "Nothing after this song.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
            )
        }
        itemsIndexed(upcoming, key = { _, e -> e.key }) { i, entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.touch)
                    .clickable(onClickLabel = "Play") { onSkipTo(start + i) }
                    .padding(horizontal = Spacing.l, vertical = Spacing.xs)
                    .semantics(mergeDescendants = true) { contentDescription = "${entry.title}, ${entry.artist}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(entry.songId, entry.albumId, Modifier.size(NowPlayingTokens.upNextArtwork), shape = shape)
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        entry.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Swipe down anywhere on Now Playing to close it. Lists inside (lyrics, up next) scroll first;
 * only the downward drag they can't use counts. The screen follows the finger unless motion is
 * off; TalkBack users get a "Close" action instead.
 */
@Composable
fun Modifier.swipeDownToDismiss(onDismiss: () -> Unit): Modifier {
    val threshold = with(LocalDensity.current) { NowPlayingTokens.dismissDistance.toPx() }
    val follow = LocalAppearance.current.motion.enabled
    val offset = remember { mutableFloatStateOf(0f) }
    val dismiss by rememberUpdatedState(onDismiss)
    fun release() {
        if (offset.floatValue >= threshold) dismiss()
        offset.floatValue = 0f
    }
    val connection = remember(threshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Pulling back up first undoes our offset before the list scrolls.
                if (available.y < 0 && offset.floatValue > 0f) {
                    val used = maxOf(available.y, -offset.floatValue)
                    offset.floatValue += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    offset.floatValue += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val wasPulling = offset.floatValue > 0f
                release()
                return if (wasPulling) available else Velocity.Zero
            }
        }
    }
    return this
        .semantics { customActions = listOf(CustomAccessibilityAction("Close now playing") { dismiss(); true }) }
        .nestedScroll(connection)
        .pointerInput(threshold) {
            detectVerticalDragGestures(
                onDragEnd = { release() },
                onDragCancel = { offset.floatValue = 0f },
            ) { change, dy ->
                val next = (offset.floatValue + dy).coerceAtLeast(0f)
                if (next != offset.floatValue) {
                    change.consume()
                    offset.floatValue = next
                }
            }
        }
        .graphicsLayer { translationY = if (follow) offset.floatValue * NowPlayingTokens.DISMISS_FOLLOW else 0f }
}

private fun Color.argb() = Argb(toArgb().toLong() and ARGB_MASK)

private const val ARGB_MASK = 0xFFFFFFFFL
