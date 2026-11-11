package com.svara.app.ui.presets.tala

import com.svara.app.ui.motion.PlayerWaveLayer
import com.svara.app.ui.artwork.ArtworkKeys
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.svara.app.appearance.tokens.ThemeTokens
import com.svara.app.nowplaying.LyricsUi
import com.svara.app.playback.QueueEntry
import com.svara.app.ui.presets.common.BlurredBackdrop
import com.svara.app.ui.presets.common.Cover
import com.svara.app.ui.presets.common.LyricsColours
import com.svara.app.ui.presets.common.PlayPauseButton
import com.svara.app.ui.presets.common.PlayerHost
import com.svara.app.ui.presets.common.PresetScrubber
import com.svara.app.ui.presets.common.SyncedLyrics
import com.svara.app.ui.presets.common.presetClickable
import com.svara.app.ui.theme.LocalPresetUi

/**
 * SOLACE Now Playing, over the cached blurred artwork with a 60 % scrim (never a live blur).
 * Grabber pill and context menu; large art that springs to 0.9 when paused (the only artwork
 * motion here); title, artist and a quality badge that opens Audio Information; a thick seek bar
 * that grows while dragging, with a haptic tick each 5 s; skip back, play/pause, skip forward.
 * No volume row (the system volume applies). Lyrics and queue below; audio routing is not shown.
 */
@Composable
fun TalaPlayer(host: PlayerHost, entry: QueueEntry) {
    val p = LocalPresetUi.current
    val a = host.actions
    var lyrics by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        BlurredBackdrop(entry.songId, entry.albumId, scrimAlpha = p.sizes.playerScrimAlpha)
        PlayerWaveLayer()
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = p.spacing.screenMargin)) {
            val artSize = minOf(maxWidth, maxHeight * p.sizes.playerArtworkFraction)
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .padding(top = p.spacing.s)
                            .presetClickable(onClickLabel = "Close now playing", scale = 1f) { a.close() }
                            .padding(p.spacing.s),
                    ) {
                        Box(Modifier.width(p.spacing.xxl).height(p.spacing.xs).clip(p.shapes.pill).background(p.colors.onShadeSecondary))
                    }
                    IconButton(onClick = a.openOptions, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.Rounded.MoreHoriz, "More options", tint = p.colors.onShade)
                    }
                }
                Spacer(Modifier.weight(1f))
                PausingArtwork(host, entry, artSize, Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.weight(1f))
                Text(entry.title, style = p.type.title, color = p.colors.onShade, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.artist, style = p.type.subhead, color = p.colors.onShadeSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).presetClickable(onClickLabel = "Open artist", scale = 1f) { a.openArtist() },
                    )
                    host.badge?.let { QualityBadge(it, a.openAudioInfo) }
                }
                Spacer(Modifier.height(p.spacing.s))
                PresetScrubber(
                    host.progress, a.seekTo, a.seekBack, a.seekForward, host.seekIncrementSec,
                    activeColor = p.colors.onShade, inactiveColor = p.colors.onShadeTrack, handleColor = p.colors.onShade,
                    timeColor = p.colors.onShadeSecondary, timeStyle = p.type.caption,
                    tickEveryMs = if (host.hapticsOn) ThemeTokens.SCRUB_TICK_MS else 0L,
                    onTick = a.haptic,
                )
                Transport(host)
                Row(Modifier.fillMaxWidth().padding(bottom = p.spacing.s), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { lyrics = true; a.setLyricsWanted(true) }) { Icon(Icons.Rounded.FormatQuote, "Lyrics", tint = p.colors.onShade) }
                    IconButton(onClick = a.openQueue) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Playing next", tint = p.colors.onShade) }
                }
            }
        }
        if (lyrics) {
            BackHandler { lyrics = false; a.setLyricsWanted(false) }
            LyricsOverlay(host, entry, onClose = { lyrics = false; a.setLyricsWanted(false) })
        }
    }
}

/** Scales to 0.9 with a spring (medium stiffness, damping 0.8) when paused; instant when motion is off. */
@Composable
private fun PausingArtwork(host: PlayerHost, entry: QueueEntry, size: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    val p = LocalPresetUi.current
    val target = if (host.state.isPlaying) 1f else p.motion.pausedArtworkScale
    val scale by animateFloatAsState(
        target,
        if (p.effects.artworkSpring) spring(dampingRatio = p.motion.springDamping, stiffness = p.motion.springStiffness) else snap(),
        label = "artwork",
    )
    Cover(
        entry.songId, entry.albumId, size, p.shapes.hero,
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(p.sizes.artworkElevation, p.shapes.hero),
        sharedKey = ArtworkKeys.nowPlaying(entry.songId),
    )
}

/** "FLAC", "MP3 320"…: tapping opens bit depth, sample rate, bitrate and codec. */
@Composable
fun QualityBadge(label: String, onClick: () -> Unit) {
    val p = LocalPresetUi.current
    Text(
        label,
        style = p.type.micro,
        color = p.colors.onShade,
        maxLines = 1,
        modifier = Modifier
            .padding(start = p.spacing.s)
            .clip(p.shapes.card)
            .background(p.colors.scrim)
            .presetClickable(onClickLabel = "Audio information", scale = 1f) { onClick() }
            .padding(horizontal = p.spacing.s, vertical = p.spacing.xs)
            .semantics { contentDescription = "Audio quality $label"; role = Role.Button },
    )
}

/** Tap skips tracks; long press seeks by the Control Studio increment. No labels. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Transport(host: PlayerHost) {
    val p = LocalPresetUi.current
    val a = host.actions
    val inc = host.seekIncrementSec
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(p.sizes.transportButton)
                .clip(p.shapes.circle)
                .combinedClickable(onClickLabel = "Previous track", onLongClickLabel = "Back $inc seconds", onLongClick = { a.haptic(); a.seekBack() }) { a.haptic(); a.previous() }
                .semantics { contentDescription = "Previous"; customActions = listOf(CustomAccessibilityAction("Back $inc seconds") { a.seekBack(); true }) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.SkipPrevious, null, tint = p.colors.onShade, modifier = Modifier.size(p.sizes.transportIcon)) }
        PlayPauseButton(host.state, p.sizes.playButton, p.sizes.playIcon, container = null, iconColor = p.colors.onShade, onClick = { a.haptic(); a.togglePlayPause() })
        Box(
            Modifier
                .size(p.sizes.transportButton)
                .clip(p.shapes.circle)
                .combinedClickable(onClickLabel = "Next track", onLongClickLabel = "Forward $inc seconds", onLongClick = { a.haptic(); a.seekForward() }) { a.haptic(); a.next() }
                .semantics { contentDescription = "Next"; customActions = listOf(CustomAccessibilityAction("Forward $inc seconds") { a.seekForward(); true }) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.SkipNext, null, tint = if (host.state.hasNext) p.colors.onShade else p.colors.onShadeTrack, modifier = Modifier.size(p.sizes.transportIcon)) }
    }
}

/** Full-screen lyrics over the blurred background: current line 28 sp bold, others at 40 %, no blur. */
@Composable
private fun LyricsOverlay(host: PlayerHost, entry: QueueEntry, onClose: () -> Unit) {
    val p = LocalPresetUi.current
    Box(Modifier.fillMaxSize()) {
        BlurredBackdrop(entry.songId, entry.albumId, scrimAlpha = p.sizes.playerScrimAlpha)
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close lyrics", tint = p.colors.onShade) }
            when (val l = host.lyrics) {
                is LyricsUi.Found -> SyncedLyrics(
                    l.lyrics, host.progress,
                    activeStyle = p.type.lyricsActive, otherStyle = p.type.lyricsActive,
                    colours = LyricsColours(p.colors.onShade, p.colors.onShade.copy(alpha = p.sizes.otherLineAlpha), p.colors.onShade.copy(alpha = p.sizes.otherLineAlpha)),
                    scrollMs = p.effects.lyricsScrollMs,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = p.spacing.screenMargin, vertical = p.spacing.xl),
                    onSeek = host.actions.seekTo,
                )
                LyricsUi.NotFound -> Text("No lyrics yet. Import or write them in Lyrics Studio (options menu).", style = p.type.body, color = p.colors.onShade, modifier = Modifier.padding(p.spacing.screenMargin))
                else -> Unit
            }
        }
    }
}
