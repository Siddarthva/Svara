package com.svara.app.ui.presets.raga

import com.svara.app.ui.motion.PlayerWaveLayer
import com.svara.app.ui.artwork.ArtworkKeys
import com.svara.app.appearance.tokens.ThemeTokens
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.svara.app.nowplaying.LyricsUi
import com.svara.app.playback.QueueEntry
import com.svara.app.playback.RepeatSetting
import com.svara.app.ui.presets.common.BlurredBackdrop
import com.svara.app.ui.presets.common.Cover
import com.svara.app.ui.presets.common.LyricsColours
import com.svara.app.ui.presets.common.PlayPauseButton
import com.svara.app.ui.presets.common.PlayerHost
import com.svara.app.ui.presets.common.PoppingHeart
import com.svara.app.ui.presets.common.PresetScrubber
import com.svara.app.ui.presets.common.SyncedLyrics
import com.svara.app.ui.presets.shruti.repeatLabel
import com.svara.app.ui.theme.LocalPresetUi

/**
 * OCTAVE Now Playing: cached blurred artwork under a 60 % overlay that, in Immersive mode only,
 * breathes between 55 % and 65 % over 6 s at no more than 10 fps. "Playing from" header; centred
 * art (swipe up for lyrics); h1 title with a heart that pops when tapped; a thin white seek bar
 * with an accent fill and 8 dp handle; an accent play circle. No download icon: it's all local.
 */
@Composable
fun RagaPlayer(host: PlayerHost, entry: QueueEntry) {
    val p = LocalPresetUi.current
    val a = host.actions
    var lyrics by rememberSaveable { mutableStateOf(false) }
    val openLyrics = { lyrics = true; a.setLyricsWanted(true) }
    Box(Modifier.fillMaxSize()) {
        BlurredBackdrop(
            entry.songId, entry.albumId,
            scrimAlpha = p.sizes.playerScrimAlpha,
            breathingFps = p.effects.breathingFps,
            breathMin = p.motion.breathingMinAlpha,
            breathMax = p.motion.breathingMaxAlpha,
            breathPeriodMs = p.motion.breathingPeriodMs,
            playing = host.state.isPlaying,
        )
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
                Cover(
                    entry.songId, entry.albumId, artSize, p.shapes.artwork,
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .pointerInput(Unit) {
                            var dy = 0f
                            detectVerticalDragGestures(
                                onDragStart = { dy = 0f },
                                onDragEnd = { if (dy < -p.spacing.touch.toPx()) openLyrics() },
                            ) { change, amount -> change.consume(); dy += amount }
                        }
                        .semantics { customActions = listOf(CustomAccessibilityAction("Show lyrics") { openLyrics(); true }) },
                    sharedKey = ArtworkKeys.nowPlaying(entry.songId),
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, style = p.type.headline, color = p.colors.onShade, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.artist, style = p.type.subhead, color = p.colors.onShadeSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    PoppingHeart(host.isFavourite, likedTint = p.colors.accent, idleTint = p.colors.onShade) { a.haptic(); a.toggleFavourite() }
                }
                PresetScrubber(
                    host.progress, a.seekTo, a.seekBack, a.seekForward, host.seekIncrementSec,
                    activeColor = p.colors.accent, inactiveColor = p.colors.onShadeTrack, handleColor = p.colors.onShade,
                    timeColor = p.colors.onShadeSecondary, timeStyle = p.type.caption,
                    tickEveryMs = if (host.hapticsOn) ThemeTokens.SCRUB_TICK_MS else 0L,
                    onTick = a.haptic,
                )
                Transport(host)
                Row(Modifier.fillMaxWidth().padding(bottom = p.spacing.s), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = openLyrics) { Icon(Icons.Rounded.Lyrics, "Lyrics", tint = p.colors.onShade) }
                    IconButton(onClick = a.openQueue) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", tint = p.colors.onShade) }
                }
            }
        }
        if (lyrics) {
            BackHandler { lyrics = false; a.setLyricsWanted(false) }
            Karaoke(host, entry, onClose = { lyrics = false; a.setLyricsWanted(false) })
        }
    }
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

/** Karaoke: the current line in the accent at h1 size, other lines at 50 %. Changes per line only. */
@Composable
private fun Karaoke(host: PlayerHost, entry: QueueEntry, onClose: () -> Unit) {
    val p = LocalPresetUi.current
    Box(Modifier.fillMaxSize()) {
        BlurredBackdrop(entry.songId, entry.albumId, scrimAlpha = p.sizes.playerScrimAlpha)
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close lyrics", tint = p.colors.onShade) }
            when (val l = host.lyrics) {
                is LyricsUi.Found -> SyncedLyrics(
                    l.lyrics, host.progress,
                    activeStyle = p.type.headline, otherStyle = p.type.bodyEmphasis,
                    colours = LyricsColours(p.colors.accentOnShade, p.colors.onShade.copy(alpha = p.sizes.otherLineAlpha), p.colors.onShade.copy(alpha = p.sizes.otherLineAlpha)),
                    scrollMs = p.effects.transitionMs,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = p.spacing.screenMargin, vertical = p.spacing.xl),
                    align = TextAlign.Center,
                    onSeek = host.actions.seekTo,
                )
                LyricsUi.NotFound -> Text("No lyrics yet. Import or write them in Lyrics Studio (options menu).", style = p.type.body, color = p.colors.onShade, modifier = Modifier.padding(p.spacing.screenMargin))
                else -> Spacer(Modifier.height(p.spacing.l))
            }
        }
    }
}
