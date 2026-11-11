package com.svara.app.ui.presets.shruti

import com.svara.app.ui.motion.PlayerWaveLayer
import com.svara.app.ui.artwork.ArtworkKeys
import com.svara.app.ui.presets.common.ambientWash
import com.svara.app.ui.theme.LocalAmbient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.svara.app.appearance.tokens.ShrutiSpec
import com.svara.app.nowplaying.LyricsUi
import com.svara.app.playback.QueueEntry
import com.svara.app.playback.RepeatSetting
import com.svara.app.ui.presets.common.Cover
import com.svara.app.ui.presets.common.LyricsColours
import com.svara.app.ui.presets.common.MarqueeText
import com.svara.app.ui.presets.common.PlayPauseButton
import com.svara.app.ui.presets.common.PlayerHost
import com.svara.app.ui.presets.common.PoppingHeart
import com.svara.app.appearance.tokens.ThemeTokens
import com.svara.app.ui.presets.common.PresetScrubber
import com.svara.app.ui.presets.common.SyncedLyrics
import com.svara.app.ui.presets.common.currentLyricLine
import com.svara.app.ui.presets.common.presetClickable
import com.svara.app.ui.presets.common.readableBackground
import com.svara.app.ui.presets.common.rememberAlbumColour
import com.svara.app.ui.theme.LocalPresetUi

/**
 * Halo Now Playing. A static vertical gradient from the cached artwork colour to black; header
 * with "PLAYING FROM" (the album: the player doesn't record where a queue came from); square art
 * at about half the screen height; left-aligned title and a tappable artist; the seek bar with a
 * handle only while dragging; controls with a white play circle; queue and share. Below the fold,
 * a lyrics card in the artwork's colour opens full-screen lyrics.
 */
@Composable
fun ShrutiPlayer(host: PlayerHost, entry: QueueEntry) {
    val p = LocalPresetUi.current
    val a = host.actions
    var fullLyrics by rememberSaveable { mutableStateOf(false) }
    // The lyrics card is part of this screen, so lyrics load while it is open.
    DisposableEffect(Unit) {
        a.setLyricsWanted(true)
        onDispose { a.setLyricsWanted(false) }
    }
    val art = rememberAlbumColour(entry.songId, entry.albumId)
    val top = art?.let { readableBackground(it, p.colors.primaryText, p.colors.background) } ?: p.colors.surfaceHighlight
    val bottom = if (p.dark) p.colors.shade else p.colors.background
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top.copy(alpha = ShrutiSpec.PLAYER_GRADIENT_ALPHA), bottom))).ambientWash(LocalAmbient.current)) {
        val viewport = maxHeight
        PlayerWaveLayer()
        val artSize = minOf(maxWidth - p.spacing.screenMargin * 2, viewport * p.sizes.playerArtworkFraction)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.heightIn(min = viewport).safeDrawingPadding().padding(horizontal = p.spacing.screenMargin)) {
                Header(entry.album, onClose = a.close, onOptions = a.openOptions)
                Spacer(Modifier.weight(1f))
                Cover(
                    entry.songId, entry.albumId, artSize, p.shapes.artwork,
                    Modifier.align(Alignment.CenterHorizontally).shadow(p.sizes.artworkElevation, p.shapes.artwork),
                    sharedKey = ArtworkKeys.nowPlaying(entry.songId),
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        MarqueeText(entry.title, p.type.headline, p.colors.primaryText)
                        Text(
                            entry.artist, style = p.type.body, color = p.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.presetClickable(onClickLabel = "Open artist", role = Role.Button, scale = 1f) { a.openArtist() },
                        )
                    }
                    PoppingHeart(host.isFavourite, likedTint = p.colors.accent, idleTint = p.colors.primaryText) { a.haptic(); a.toggleFavourite() }
                }
                PresetScrubber(
                    host.progress, a.seekTo, a.seekBack, a.seekForward, host.seekIncrementSec,
                    activeColor = p.colors.primaryText, inactiveColor = p.colors.primaryText.copy(alpha = p.sizes.disabledAlpha),
                    handleColor = p.colors.primaryText, timeColor = p.colors.secondaryText, timeStyle = p.type.micro,
                    // The source asks for subtle vibrations while adjusting the scrub bar.
                    tickEveryMs = if (host.hapticsOn) ThemeTokens.SCRUB_TICK_MS else 0L,
                    onTick = a.haptic,
                )
                Transport(host)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = a.openQueue) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", tint = p.colors.primaryText) }
                    IconButton(onClick = a.share) { Icon(Icons.Rounded.Share, "Share", tint = p.colors.primaryText) }
                }
                Spacer(Modifier.height(p.spacing.l))
            }
            LyricsCard(host, art, onOpen = { fullLyrics = true })
            Spacer(Modifier.height(p.spacing.sectionGap))
        }
    }
    if (fullLyrics) {
        BackHandler { fullLyrics = false }
        FullLyrics(host, p.colors.background, onClose = { fullLyrics = false })
    }
}

@Composable
private fun Header(source: String, onClose: () -> Unit, onOptions: () -> Unit) {
    val p = LocalPresetUi.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close now playing", tint = p.colors.primaryText) }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PLAYING FROM ALBUM", style = p.type.micro, color = p.colors.secondaryText, maxLines = 1)
            Text(source, style = p.type.bodyEmphasis, color = p.colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onOptions) { Icon(Icons.Rounded.MoreHoriz, "More options", tint = p.colors.primaryText) }
    }
}

/** Shuffle, previous, play/pause (white circle, black icon in dark), next, repeat. */
@Composable
private fun Transport(host: PlayerHost) {
    val p = LocalPresetUi.current
    val a = host.actions
    val s = host.state
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { a.haptic(); a.toggleShuffle() }, modifier = Modifier.semantics { stateDescription = if (s.shuffleEnabled) "On" else "Off" }) {
            Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (s.shuffleEnabled) p.colors.accent else p.colors.primaryText)
        }
        IconButton(onClick = { a.haptic(); a.previous() }) {
            Icon(Icons.Rounded.SkipPrevious, "Previous", tint = p.colors.primaryText, modifier = Modifier.size(p.sizes.transportIcon))
        }
        PlayPauseButton(s, p.sizes.playButton, p.sizes.playIcon, container = p.colors.primaryText, iconColor = p.colors.background, onClick = { a.haptic(); a.togglePlayPause() })
        IconButton(onClick = { a.haptic(); a.next() }, enabled = s.hasNext) {
            Icon(Icons.Rounded.SkipNext, "Next", tint = if (s.hasNext) p.colors.primaryText else p.colors.secondaryText, modifier = Modifier.size(p.sizes.transportIcon))
        }
        IconButton(onClick = { a.haptic(); a.cycleRepeat() }, modifier = Modifier.semantics { stateDescription = repeatLabel(s.repeat) }) {
            Icon(if (s.repeat == RepeatSetting.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat", tint = if (s.repeat != RepeatSetting.OFF) p.colors.accent else p.colors.primaryText)
        }
    }
}

fun repeatLabel(r: RepeatSetting) = when (r) {
    RepeatSetting.OFF -> "Off"
    RepeatSetting.ALL -> "Repeat all"
    RepeatSetting.ONE -> "Repeat one"
}

@Composable
private fun LyricsCard(host: PlayerHost, art: Color?, onOpen: () -> Unit) {
    val p = LocalPresetUi.current
    val lyrics = host.lyrics
    val card = readableBackground(art ?: p.colors.surfaceHighlight, p.colors.primaryText, p.colors.background)
    Column(
        Modifier
            .padding(horizontal = p.spacing.screenMargin)
            .fillMaxWidth()
            .clip(p.shapes.lyricsCard)
            .background(card)
            .then(if (lyrics is LyricsUi.Found) Modifier.presetClickable(onClickLabel = "Open lyrics", scale = 1f) { onOpen() } else Modifier)
            .padding(p.spacing.l)
            .semantics(mergeDescendants = true) { contentDescription = "Lyrics" },
    ) {
        Text("Lyrics", style = p.type.title, color = p.colors.primaryText)
        Spacer(Modifier.height(p.spacing.s))
        when (lyrics) {
            is LyricsUi.Found -> Text(currentLyricLine(lyrics.lyrics, host.progress), style = p.type.headline, color = p.colors.primaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            LyricsUi.NotFound -> Text("No lyrics yet. Import or write them in Lyrics Studio (options menu).", style = p.type.body, color = p.colors.primaryText)
            else -> Text("…", style = p.type.body, color = p.colors.primaryText)
        }
    }
}

/** Current line primary, upcoming secondary, past lines at 50 %; no per-frame animation. */
@Composable
private fun FullLyrics(host: PlayerHost, background: Color, onClose: () -> Unit) {
    val p = LocalPresetUi.current
    val lyrics = (host.lyrics as? LyricsUi.Found)?.lyrics ?: return
    Column(Modifier.fillMaxSize().background(background).safeDrawingPadding()) {
        Box(Modifier.fillMaxWidth()) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close lyrics", tint = p.colors.primaryText) }
        }
        SyncedLyrics(
            lyrics, host.progress,
            activeStyle = p.type.headline, otherStyle = p.type.headline,
            colours = LyricsColours(p.colors.primaryText, p.colors.secondaryText, p.colors.primaryText.copy(alpha = p.sizes.pastLineAlpha)),
            scrollMs = p.effects.transitionMs,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = p.spacing.screenMargin, vertical = p.spacing.xl),
            onSeek = host.actions.seekTo,
        )
    }
}
