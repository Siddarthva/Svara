package com.svara.app.ui.presets.spanda

import com.svara.app.ui.motion.PlayerWaveLayer
import com.svara.app.ui.artwork.ArtworkKeys
import com.svara.app.appearance.tokens.ThemeTokens
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.svara.app.domain.model.Song
import com.svara.app.nowplaying.LyricsUi
import com.svara.app.playback.QueueEntry
import com.svara.app.playback.RepeatSetting
import com.svara.app.ui.presets.common.BlurredBackdrop
import com.svara.app.ui.presets.common.Cover
import com.svara.app.ui.presets.common.LyricsColours
import com.svara.app.ui.presets.common.MarqueeText
import com.svara.app.ui.presets.common.PlayPauseButton
import com.svara.app.ui.presets.common.PlayerHost
import com.svara.app.ui.presets.common.PoppingHeart
import com.svara.app.ui.presets.common.PresetScrubber
import com.svara.app.ui.presets.common.SyncedLyrics
import com.svara.app.ui.presets.common.currentLyricLine
import com.svara.app.ui.presets.common.presetClickable
import com.svara.app.ui.presets.shruti.repeatLabel
import com.svara.app.ui.presets.tala.QualityBadge
import com.svara.app.ui.theme.LocalPresetUi
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * ZENITH Now Playing: cached blurred art under a 45 % overlay fading to black (static), plus the
 * optional audio-reactive layer in Immersive. Swipe the art to change tracks; title (marquee
 * only when it overflows), artist and quality badge; a thin accent seek bar; an accent play
 * circle; a card with the current lyric line and credits from the file's tags; lyrics, queue and
 * share (title and artist text only).
 */
@Composable
fun SpandaPlayer(host: PlayerHost, entry: QueueEntry, song: Song?) {
    val p = LocalPresetUi.current
    val a = host.actions
    var lyrics by rememberSaveable { mutableStateOf(false) }
    // The credits card shows the current line, so lyrics load while this screen is open.
    DisposableEffect(Unit) {
        a.setLyricsWanted(true)
        onDispose { a.setLyricsWanted(false) }
    }
    Box(Modifier.fillMaxSize()) {
        BlurredBackdrop(entry.songId, entry.albumId, scrimAlpha = p.sizes.playerScrimAlpha, fadeTo = p.colors.shade)
        PlayerWaveLayer()
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = p.spacing.screenMargin)) {
            val artSize = minOf(maxWidth, maxHeight * p.sizes.playerArtworkFraction)
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = a.close) { Icon(Icons.Rounded.KeyboardArrowDown, "Close now playing", tint = p.colors.onShade) }
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Playing from", style = p.type.caption, color = p.colors.onShadeSecondary)
                        Text(entry.album, style = p.type.bodyEmphasis, color = p.colors.onShade, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = a.openOptions) { Icon(Icons.Rounded.MoreVert, "More options", tint = p.colors.onShade) }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                    ReactiveGlow(host, entry, artSize)
                    SwipeArtwork(host, entry, artSize)
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MarqueeText(entry.title, p.type.headline, p.colors.onShade, Modifier.weight(1f))
                    // The source's like button: fills cyan and pops.
                    PoppingHeart(host.isFavourite, likedTint = p.colors.accent, idleTint = p.colors.onShade) { a.haptic(); a.toggleFavourite() }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.artist, style = p.type.subhead, color = p.colors.onShadeSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).presetClickable(onClickLabel = "Open artist", scale = 1f) { a.openArtist() },
                    )
                    host.badge?.let { QualityBadge(it, a.openAudioInfo) }
                }
                PresetScrubber(
                    host.progress, a.seekTo, a.seekBack, a.seekForward, host.seekIncrementSec,
                    activeColor = p.colors.accent, inactiveColor = p.colors.onShadeTrack, handleColor = p.colors.accent,
                    timeColor = p.colors.onShadeSecondary, timeStyle = p.type.subhead,
                    tickEveryMs = if (host.hapticsOn) ThemeTokens.SCRUB_TICK_MS else 0L,
                    onTick = a.haptic,
                )
                SpectrumBars(host, Modifier.fillMaxWidth())
                Transport(host)
                CreditsCard(host, song, onOpenLyrics = { lyrics = true })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { lyrics = true }) { Icon(Icons.Rounded.Lyrics, "Lyrics", tint = p.colors.onShade) }
                    IconButton(onClick = a.openQueue) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", tint = p.colors.onShade) }
                    IconButton(onClick = a.share) { Icon(Icons.Rounded.Share, "Share", tint = p.colors.onShade) }
                }
            }
        }
        if (lyrics) {
            BackHandler { lyrics = false }
            Box(Modifier.fillMaxSize()) {
                BlurredBackdrop(entry.songId, entry.albumId, scrimAlpha = p.sizes.playerScrimAlpha, fadeTo = p.colors.shade)
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    IconButton(onClick = { lyrics = false }) { Icon(Icons.Rounded.KeyboardArrowDown, "Close lyrics", tint = p.colors.onShade) }
                    (host.lyrics as? LyricsUi.Found)?.let { l ->
                        SyncedLyrics(
                            l.lyrics, host.progress,
                            activeStyle = p.type.headline, otherStyle = p.type.title,
                            colours = LyricsColours(p.colors.onShade, p.colors.onShade.copy(alpha = p.sizes.otherLineAlpha), p.colors.onShade.copy(alpha = p.sizes.otherLineAlpha)),
                            scrollMs = p.effects.transitionMs,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = p.spacing.screenMargin, vertical = p.spacing.xl),
                            onSeek = a.seekTo,
                        )
                    } ?: Text("No lyrics in this file or beside it.", style = p.type.body, color = p.colors.onShade, modifier = Modifier.padding(p.spacing.screenMargin))
                }
            }
        }
    }
}

/**
 * Follows the finger 1:1. Past 35 % of the width on release it slides out and changes track
 * (200 ms); otherwise it returns. It won't move toward a missing next or previous track.
 */
@Composable
private fun SwipeArtwork(host: PlayerHost, entry: QueueEntry, size: Dp) {
    val p = LocalPresetUi.current
    val a = host.actions
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val ms = p.effects.swipeSnapMs
    val hasNext = host.state.hasNext
    val hasPrevious = host.state.hasPrevious
    Cover(
        entry.songId, entry.albumId, size, p.shapes.artwork,
        Modifier
            .graphicsLayer { translationX = offset.value }
            .shadow(p.sizes.artworkElevation, p.shapes.artwork)
            .pointerInput(entry.songId, hasNext, hasPrevious) {
                val width = this.size.width.toFloat()
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val x = offset.value
                            val change = abs(x) > width * p.motion.swipeThreshold
                            if (change) {
                                val target = if (x < 0) -width else width
                                if (ms > 0) offset.animateTo(target, tween(ms, easing = FastOutSlowInEasing))
                                if (x < 0) a.next() else a.previous()
                                a.haptic()
                            } else if (ms > 0) {
                                offset.animateTo(0f, tween(ms, easing = FastOutSlowInEasing))
                            }
                            offset.snapTo(0f)
                        }
                    },
                    onDragCancel = { scope.launch { offset.snapTo(0f) } },
                ) { change, amount ->
                    change.consume()
                    val next = offset.value + amount
                    // Left reveals the next track, right the previous; blocked when there isn't one.
                    val allowed = (next < 0 && hasNext) || (next > 0 && hasPrevious) || next == 0f
                    scope.launch { offset.snapTo(if (allowed) next else 0f) }
                }
            }
            .semantics {
                contentDescription = "Album artwork. Swipe to change track."
                customActions = listOfNotNull(
                    if (hasNext) CustomAccessibilityAction("Next track") { a.next(); true } else null,
                    if (hasPrevious) CustomAccessibilityAction("Previous track") { a.previous(); true } else null,
                )
            },
        sharedKey = ArtworkKeys.nowPlaying(entry.songId),
    )
}

@Composable
private fun Transport(host: PlayerHost) {
    val p = LocalPresetUi.current
    val a = host.actions
    val s = host.state
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { a.haptic(); a.toggleShuffle() }, modifier = Modifier.semantics { stateDescription = if (s.shuffleEnabled) "On" else "Off" }) {
            Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (s.shuffleEnabled) p.colors.accent else p.colors.onShade)
        }
        IconButton(onClick = { a.haptic(); a.previous() }) { Icon(Icons.Rounded.SkipPrevious, "Previous", tint = p.colors.onShade, modifier = Modifier.size(p.sizes.transportIcon)) }
        PlayPauseButton(s, p.sizes.playButton, p.sizes.playIcon, container = p.colors.accent, iconColor = p.colors.onAccent, onClick = { a.haptic(); a.togglePlayPause() })
        IconButton(onClick = { a.haptic(); a.next() }, enabled = s.hasNext) {
            Icon(Icons.Rounded.SkipNext, "Next", tint = if (s.hasNext) p.colors.onShade else p.colors.onShadeTrack, modifier = Modifier.size(p.sizes.transportIcon))
        }
        IconButton(onClick = { a.haptic(); a.cycleRepeat() }, modifier = Modifier.semantics { stateDescription = repeatLabel(s.repeat) }) {
            Icon(if (s.repeat == RepeatSetting.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat", tint = if (s.repeat != RepeatSetting.OFF) p.colors.accent else p.colors.onShade)
        }
    }
}

/** The lyrics-and-credits card: current line and credits from local tags (no trivia). Swipe up or tap for lyrics. */
@Composable
private fun CreditsCard(host: PlayerHost, song: Song?, onOpenLyrics: () -> Unit) {
    val p = LocalPresetUi.current
    val line = (host.lyrics as? LyricsUi.Found)?.let { currentLyricLine(it.lyrics, host.progress) }
    val credits = listOfNotNull(
        song?.displayArtist,
        song?.displayAlbum,
        song?.year?.takeIf { it > 0 }?.toString(),
        song?.genre?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = p.spacing.s)
            .clip(p.shapes.lyricsCard)
            .background(p.colors.scrim)
            .presetClickable(onClickLabel = "Open lyrics", scale = 1f) { onOpenLyrics() }
            .pointerInput(Unit) {
                var dy = 0f
                detectVerticalDragGestures(onDragStart = { dy = 0f }, onDragEnd = { if (dy < -p.spacing.touch.toPx()) onOpenLyrics() }) { c, amount -> c.consume(); dy += amount }
            }
            .padding(p.spacing.m),
    ) {
        Text(line?.ifBlank { "♪" } ?: "No lyrics in this file", style = p.type.bodyEmphasis, color = p.colors.onShade, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (credits.isNotEmpty()) Text(credits, style = p.type.subhead, color = p.colors.onShadeSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
