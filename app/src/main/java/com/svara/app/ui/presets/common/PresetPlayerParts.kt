package com.svara.app.ui.presets.common

import com.svara.app.appearance.tokens.ThemeTokens
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.dp
import androidx.compose.animation.Crossfade
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import com.svara.app.ui.motion.FavouriteBurst
import com.svara.app.ui.motion.extraAllowed
import com.svara.app.appearance.AnimationExtra
import com.svara.app.appearance.tokens.ExtraTokens
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.svara.app.data.lyrics.LyricsText
import com.svara.app.domain.model.Lyrics
import com.svara.app.nowplaying.LyricsUi
import com.svara.app.playback.PlaybackProgress
import com.svara.app.playback.PlaybackState
import com.svara.app.ui.components.formatDuration
import com.svara.app.ui.components.formatRemaining
import com.svara.app.ui.theme.LocalPresetUi

/** Everything a preset's Now Playing calls back into. The screen owns the sheets and dialogs. */
@Immutable
class PlayerActions(
    val togglePlayPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seekTo: (Long) -> Unit,
    val seekBack: () -> Unit,
    val seekForward: () -> Unit,
    val toggleShuffle: () -> Unit,
    val cycleRepeat: () -> Unit,
    val toggleFavourite: () -> Unit,
    val close: () -> Unit,
    val openQueue: () -> Unit,
    val openOptions: () -> Unit,
    val openAudioInfo: () -> Unit,
    val openArtist: () -> Unit,
    val share: () -> Unit,
    /** Lyrics load only while something on screen needs them. */
    val setLyricsWanted: (Boolean) -> Unit,
    val haptic: () -> Unit,
)

/** The state a preset's Now Playing reads. [progress] is read lazily so only the seek bar redraws. */
@Immutable
class PlayerHost(
    val state: PlaybackState,
    val progress: State<PlaybackProgress>,
    val isFavourite: Boolean,
    val lyrics: LyricsUi,
    val badge: String?,
    val seekIncrementSec: Int,
    val hapticsOn: Boolean,
    val actions: PlayerActions,
)

/** Sends title and artist as plain text to the system share sheet. The file is never shared. */
fun shareTrack(context: Context, title: String, artist: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$title — $artist")
    }
    context.startActivity(Intent.createChooser(send, "Share song").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * Seek bar drawn from tokens: one track (or a row of dots), the played portion, a handle that
 * grows only while dragging (and may show only then), and a time bubble above the handle while
 * dragging, which scales in with Scrubber magnify. Progress is read in the draw phase, so
 * playback ticks redraw the bar without recomposing it. [tickEveryMs] > 0 calls [onTick] each
 * time the dragged position crosses such a boundary (SOLACE's haptic ticks).
 */
@Composable
fun PresetScrubber(
    progress: State<PlaybackProgress>,
    onSeek: (Long) -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    incrementSec: Int,
    activeColor: Color,
    inactiveColor: Color,
    handleColor: Color,
    timeColor: Color,
    timeStyle: TextStyle,
    modifier: Modifier = Modifier,
    tickEveryMs: Long = 0L,
    onTick: () -> Unit = {},
    /** Overrides the theme's track height (the Thick style). */
    trackHeight: Dp? = null,
    dots: Boolean = false,
    bubbleColor: Color = LocalPresetUi.current.colors.primaryText,
    bubbleTextColor: Color = LocalPresetUi.current.colors.background,
) {
    val p = LocalPresetUi.current
    val magnify = extraAllowed(AnimationExtra.SCRUBBER_MAGNIFY)
    var dragging by remember { mutableStateOf(false) }
    val dragFraction = remember { mutableFloatStateOf(0f) }
    val track = trackHeight ?: p.sizes.scrubberTrack
    val trackDragging = trackHeight ?: p.sizes.scrubberTrackDragging
    val handle = p.sizes.scrubberHandle
    val handleAlways = !p.sizes.handleOnlyWhileDragging
    val handleScale by animateFloatAsState(
        if (dragging) ThemeTokens.HANDLE_DRAG_SCALE else 1f,
        tween(p.effects.pressMs.coerceAtLeast(1)),
        label = "handle",
    )
    val thickness by animateFloatAsState(
        if (dragging) trackDragging.value else track.value,
        tween(p.effects.pressMs.coerceAtLeast(1)),
        label = "track",
    )
    fun duration() = progress.value.durationMs.coerceAtLeast(1)
    fun fraction() = if (dragging) dragFraction.floatValue else (progress.value.positionMs.toFloat() / duration()).coerceIn(0f, 1f)
    var lastTick by remember { mutableLongStateOf(-1L) }
    fun drag(f: Float) {
        dragFraction.floatValue = f.coerceIn(0f, 1f)
        if (tickEveryMs > 0) {
            val bucket = (dragFraction.floatValue * duration()).toLong() / tickEveryMs
            if (lastTick >= 0 && bucket != lastTick) onTick()
            lastTick = bucket
        }
    }
    Column(modifier) {
        Box {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(p.spacing.touch)
                .pointerInput(Unit) {
                    detectTapGestures { o -> onSeek((o.x / size.width * duration()).toLong()) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragging = true; lastTick = -1L; drag(o.x / size.width) },
                        onDragEnd = { onSeek((dragFraction.floatValue * duration()).toLong()); dragging = false },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ -> change.consume(); drag(change.position.x / size.width) },
                    )
                }
                .semantics {
                    contentDescription = "Seek"
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction(), 0f..1f)
                    setProgress { v -> onSeek((v * duration()).toLong()); true }
                    customActions = listOf(
                        CustomAccessibilityAction("Rewind $incrementSec seconds") { onSeekBack(); true },
                        CustomAccessibilityAction("Forward $incrementSec seconds") { onSeekForward(); true },
                    )
                },
        ) {
            val h = thickness * density
            val y = size.height / 2f
            val f = fraction()
            if (dots) {
                val step = size.width / ThemeTokens.SEEK_DOTS
                val r = minOf(step * DOT_RADIUS, h / 2f)
                for (i in 0 until ThemeTokens.SEEK_DOTS) {
                    drawCircle(if ((i + HALF) / ThemeTokens.SEEK_DOTS <= f) activeColor else inactiveColor, r, Offset(step * (i + HALF), y))
                }
            } else {
                val radius = CornerRadius(h / 2f, h / 2f)
                drawRoundRect(inactiveColor, Offset(0f, y - h / 2f), Size(size.width, h), radius)
                if (f > 0f) drawRoundRect(activeColor, Offset(0f, y - h / 2f), Size(maxOf(size.width * f, h), h), radius)
            }
            if (handle.value > 0f && (handleAlways || dragging)) {
                drawCircle(handleColor, handle.toPx() / 2f * (if (dragging) handleScale else 1f), Offset(size.width * f, y))
            }
        }
        if (dragging) TimeBubble(
            fraction = { dragFraction.floatValue },
            durationMs = { duration() },
            scale = { if (magnify && ThemeTokens.HANDLE_DRAG_SCALE > 1f) ((handleScale - 1f) / (ThemeTokens.HANDLE_DRAG_SCALE - 1f)).coerceIn(0f, 1f) else 1f },
            style = timeStyle,
            color = bubbleColor,
            textColor = bubbleTextColor,
        )
        }
        ScrubberTimes(progress, { if (dragging) dragFraction.floatValue else null }, timeStyle, timeColor)
    }
}

/**
 * The dragged position over the handle: centred on it, kept inside the bar, just above the
 * track. Only this recomposes while dragging, and only when the shown second changes.
 */
@Composable
private fun TimeBubble(fraction: () -> Float, durationMs: () -> Long, scale: () -> Float, style: TextStyle, color: Color, textColor: Color) {
    val p = LocalPresetUi.current
    val gap = with(LocalDensity.current) { ThemeTokens.SEEK_BUBBLE_GAP_DP.dp.roundToPx() }
    val seconds by remember { derivedStateOf { (fraction() * durationMs()).toLong() / MS_PER_SECOND } }
    Text(
        formatDuration(seconds * MS_PER_SECOND),
        style = style,
        color = textColor,
        modifier = Modifier
            .layout { measurable, constraints ->
                val bubble = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(0, 0) {
                    val x = (fraction() * constraints.maxWidth - bubble.width / 2f).toInt()
                        .coerceIn(0, (constraints.maxWidth - bubble.width).coerceAtLeast(0))
                    bubble.place(x, -bubble.height - gap)
                }
            }
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(HALF, 1f)
            }
            .background(color, p.shapes.pill)
            .padding(horizontal = p.spacing.s, vertical = p.spacing.xs),
    )
}

/** Elapsed and remaining time; the only part of the seek bar that recomposes as playback moves, once a second. */
@Composable
private fun ScrubberTimes(progress: State<PlaybackProgress>, dragFraction: () -> Float?, style: TextStyle, color: Color) {
    // Derived to whole seconds: playback ticks several times a second, the text changes once.
    val drag by rememberUpdatedState(dragFraction)
    val duration by remember { derivedStateOf { progress.value.durationMs } }
    val shown by remember {
        derivedStateOf { (drag()?.let { (it * duration.coerceAtLeast(1)).toLong() } ?: progress.value.positionMs) / MS_PER_SECOND * MS_PER_SECOND }
    }
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { stateDescription = "${formatDuration(shown)} of ${formatDuration(duration)}, ${formatDuration((duration - shown).coerceAtLeast(0))} left" }) {
        Text(formatDuration(shown), style = style, color = color)
        Spacer(Modifier.weight(1f))
        Text(formatRemaining(shown, duration), style = style, color = color)
    }
}

/**
 * Play/pause: a filled circle ([container]) or a bare glyph when [container] is null. Scales to
 * the preset's play-press value while pressed.
 */
@Composable
fun PlayPauseButton(
    state: PlaybackState,
    size: Dp,
    iconSize: Dp,
    container: Color?,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPresetUi.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val ms = p.effects.playPressMs
    val depress = extraAllowed(AnimationExtra.PRESS_DEPRESS)
    val morph = extraAllowed(AnimationExtra.PLAY_PAUSE_MORPH)
    val scale by animateFloatAsState(
        when {
            pressed && depress -> ExtraTokens.PRESS_SCALE
            pressed && ms > 0 -> p.effects.playPressScale
            else -> 1f
        },
        if (depress) spring(dampingRatio = ExtraTokens.PRESS_DAMPING, stiffness = ExtraTokens.PRESS_STIFFNESS) else tween(ms.coerceAtLeast(1)),
        label = "play",
    )
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (container != null) Modifier.background(container, p.shapes.circle) else Modifier)
            .clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current, onClickLabel = null) { onClick() }
            .semantics { contentDescription = if (state.isPlaying) "Pause" else "Play" },
        contentAlignment = Alignment.Center,
    ) {
        if (state.isBuffering && !state.isPlaying) {
            CircularProgressIndicator(Modifier.size(iconSize), color = iconColor)
        } else {
            // Play/pause morph: a short cross-fade between the two glyphs (instant without motion).
            if (morph) {
                // Play/pause morph extra: the new glyph scales and fades in as the old one leaves.
                AnimatedContent(
                    state.isPlaying,
                    transitionSpec = {
                        (fadeIn(tween(ExtraTokens.MORPH_MS)) + scaleIn(tween(ExtraTokens.MORPH_MS), initialScale = ExtraTokens.MORPH_START_SCALE)) togetherWith
                            fadeOut(tween(ExtraTokens.MORPH_MS))
                    },
                    label = "playPauseMorph",
                ) { playing ->
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = iconColor, modifier = Modifier.size(iconSize))
                }
            } else Crossfade(state.isPlaying, animationSpec = tween(p.effects.iconMorphMs), label = "playPause") { playing ->
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = iconColor, modifier = Modifier.size(iconSize))
            }
        }
    }
}

/** Which colour each lyrics line gets relative to the active one. */
@Immutable
class LyricsColours(val active: Color, val upcoming: Color, val past: Color)

/**
 * Lyrics with the active line highlighted. Timed lyrics follow playback: the active index is a
 * derived state, so this recomposes only when the line changes, never per frame. The list
 * scrolls the active line into view over [scrollMs] (0 = jump). Untimed lyrics are shown plain.
 */
@Composable
fun SyncedLyrics(
    lyrics: Lyrics,
    progress: State<PlaybackProgress>,
    activeStyle: TextStyle,
    otherStyle: TextStyle,
    colours: LyricsColours,
    scrollMs: Int,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    align: TextAlign = TextAlign.Start,
    onSeek: ((Long) -> Unit)? = null,
) {
    val p = LocalPresetUi.current
    val timed = lyrics.timed
    val lines = remember(lyrics) { if (timed.isNotEmpty()) timed.map { it.text } else lyrics.text.lines() }
    val active by remember(timed, lyrics.offsetMs) { derivedStateOf { if (timed.isEmpty()) -1 else LyricsText.activeIndex(timed, progress.value.positionMs - lyrics.offsetMs) } }
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (active < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == active }
        if (item != null && scrollMs > 0) {
            val target = item.offset - (info.viewportEndOffset - info.viewportStartOffset) / ACTIVE_LINE_DIVISOR
            listState.animateScrollBy(target.toFloat(), tween(scrollMs, easing = LinearOutSlowInEasing))
        } else {
            listState.scrollToItem(active, -(info.viewportEndOffset - info.viewportStartOffset) / ACTIVE_LINE_DIVISOR)
        }
    }
    LazyColumn(modifier, state = listState, contentPadding = contentPadding) {
        itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
            val isActive = i == active
            val colour = when {
                active < 0 -> if (timed.isEmpty()) colours.active else colours.upcoming
                isActive -> colours.active
                i < active -> colours.past
                else -> colours.upcoming
            }
            Text(
                line.ifBlank { "♪" },
                style = if (isActive) activeStyle else otherStyle,
                color = colour,
                textAlign = align,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = p.spacing.touch)
                    .then(if (onSeek != null && timed.isNotEmpty()) Modifier.clickable { onSeek(timed[i].timeMs) } else Modifier)
                    .padding(vertical = p.spacing.xs),
            )
        }
    }
}

/** The active line sits this fraction of the way down the lyrics viewport (1/3). */
private const val ACTIVE_LINE_DIVISOR = 3

/** The first lyric line to preview (the current one when timed). */
@Composable
fun currentLyricLine(lyrics: Lyrics, progress: State<PlaybackProgress>): String {
    val timed = lyrics.timed
    val line by remember(lyrics) {
        derivedStateOf {
            if (timed.isEmpty()) lyrics.text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            else timed.getOrNull(LyricsText.activeIndex(timed, progress.value.positionMs - lyrics.offsetMs).coerceAtLeast(0))?.text.orEmpty()
        }
    }
    return line
}

/**
 * The like button. Filled with [likedTint] when liked; when tapped (and the effects policy
 * allows micro-animations) it pops to the preset's scale and back over its pop duration.
 */
@Composable
fun PoppingHeart(liked: Boolean, likedTint: Color, idleTint: Color, onToggle: () -> Unit) {
    val p = LocalPresetUi.current
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val burstOn = extraAllowed(AnimationExtra.HEART_BURST)
    var burst by remember { mutableIntStateOf(0) }
    IconButton(onClick = {
        val becomingLiked = !liked
        onToggle()
        if (becomingLiked) burst++
        if (burstOn && becomingLiked) scope.launch {
            scale.animateTo(ExtraTokens.HEART_SCALE, spring(dampingRatio = ExtraTokens.HEART_DAMPING, stiffness = ExtraTokens.HEART_STIFFNESS))
            scale.animateTo(1f, spring(dampingRatio = ExtraTokens.HEART_DAMPING, stiffness = ExtraTokens.HEART_STIFFNESS))
        } else if (p.effects.heartPop) scope.launch {
            val half = p.effects.heartPopMs / 2
            scale.animateTo(p.effects.heartPopScale, tween(half))
            scale.animateTo(1f, tween(half))
        }
    }) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (liked) "Remove from liked songs" else "Like",
                tint = if (liked) likedTint else idleTint,
                modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
            )
            FavouriteBurst(burst, likedTint, Modifier.matchParentSize())
        }
    }
}

private const val DOT_RADIUS = 0.3f
private const val HALF = 0.5f
private const val MS_PER_SECOND = 1000L
