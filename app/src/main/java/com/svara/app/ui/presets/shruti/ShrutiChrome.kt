package com.svara.app.ui.presets.shruti

import com.svara.app.ui.motion.SongChange
import com.svara.app.appearance.tokens.PageMotion
import com.svara.app.ui.artwork.ArtworkKeys
import com.svara.app.ui.presets.common.ambientFill
import com.svara.app.ui.theme.LocalAmbient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.svara.app.ui.presets.MiniPlayerModel
import com.svara.app.ui.presets.common.Cover
import com.svara.app.ui.presets.common.MarqueeText
import com.svara.app.ui.presets.common.MiniFrame
import com.svara.app.ui.theme.LocalPresetUi
import com.svara.app.ui.theme.rememberHaptic

/**
 * Halo mini-player: floats above the bar on the surface colour with a 1 dp divider. Square
 * 48 dp art, title and artist (marquee only when too long), play/pause. Sideways swipe skips,
 * upward swipe or a tap on the bar expands.
 */
@Composable
fun ShrutiMiniPlayer(model: MiniPlayerModel, onOpen: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit) {
    val title = model.title ?: return
    val p = LocalPresetUi.current
    val haptic = rememberHaptic()
    Column(Modifier.fillMaxWidth().padding(horizontal = p.spacing.s).padding(bottom = p.spacing.xs)) {
        Column(Modifier.clip(p.shapes.miniPlayer).background(p.colors.surface).ambientFill(LocalAmbient.current)) {
            MiniFrame(
                description = "Now playing: $title by ${model.artist}",
                onOpen = onOpen,
                onNext = { haptic(); onNext() },
                onPrevious = { haptic(); onPrevious() },
                swipeUpOpens = true,
            ) {
                Cover(model.songId, model.albumId, p.sizes.miniArtwork, p.shapes.card, sharedKey = ArtworkKeys.nowPlaying(model.songId))
                Spacer(Modifier.width(p.spacing.m))
                Column(Modifier.weight(1f)) {
                    SongChange(title to model.artist, durationMs = PageMotion.MINI_PLAYER_MS) { (t, _) -> MarqueeText(t, p.type.bodyEmphasis, p.colors.primaryText) }
                    MarqueeText(model.artist, p.type.subhead, p.colors.secondaryText)
                }
                IconButton(onClick = { haptic(); onPlayPause() }) {
                    Icon(
                        if (model.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (model.isPlaying) "Pause" else "Play",
                        tint = p.colors.primaryText,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(p.spacing.hairline).background(p.colors.divider))
        }
    }
}
