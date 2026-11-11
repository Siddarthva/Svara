package com.svara.app.ui.presets.tala

import com.svara.app.ui.motion.SongChange
import com.svara.app.appearance.tokens.PageMotion
import com.svara.app.ui.artwork.ArtworkKeys
import com.svara.app.ui.presets.common.ambientFill
import com.svara.app.ui.theme.LocalAmbient
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.presets.MiniPlayerModel
import com.svara.app.ui.presets.common.Cover
import com.svara.app.ui.presets.common.MarqueeText
import com.svara.app.ui.presets.common.MiniFrame
import com.svara.app.ui.presets.common.rememberBlurredArtwork
import com.svara.app.ui.theme.LocalPresetUi
import com.svara.app.ui.theme.rememberHaptic

/**
 * SOLACE mini-player: floats above the bar on a translucent surface with a hairline border. In
 * Immersive mode on Android 12+ the surface sits over a live RenderEffect blur of the artwork
 * (the only live blur in the app); otherwise it is plain translucent surface. Art with a soft
 * shadow, title and artist (marquee if long), play/pause and next. Tap expands; long press
 * opens a context sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalaMiniPlayer(model: MiniPlayerModel, navigator: Navigator?, onOpen: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
    val title = model.title ?: return
    val p = LocalPresetUi.current
    val haptic = rememberHaptic()
    var menu by remember { mutableStateOf(false) }
    val liveBlur = p.effects.liveBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = p.spacing.s)
            .padding(bottom = p.spacing.xs)
            .clip(p.shapes.miniPlayer)
            .border(p.spacing.hairline, p.colors.divider, p.shapes.miniPlayer),
    ) {
        if (liveBlur) {
            val blurred = rememberBlurredArtwork(model.songId, model.albumId)
            if (blurred != null) {
                Image(blurred, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().blur(p.sizes.liveBlurRadius))
            }
        }
        Box(Modifier.matchParentSize().background(p.colors.surface.copy(alpha = p.sizes.miniPlayerAlpha)).ambientFill(LocalAmbient.current))
        MiniFrame(
            description = "Now playing: $title by ${model.artist}",
            onOpen = onOpen,
            onLongPress = if (navigator != null) ({ haptic(); menu = true }) else null,
        ) {
            Cover(
                model.songId, model.albumId, p.sizes.miniArtwork, p.shapes.artwork,
                Modifier.shadow(p.sizes.smallArtworkElevation, p.shapes.artwork),
                sharedKey = ArtworkKeys.nowPlaying(model.songId),
            )
            Spacer(Modifier.width(p.spacing.m))
            Column(Modifier.weight(1f)) {
                SongChange(title to model.artist, durationMs = PageMotion.MINI_PLAYER_MS) { (t, _) -> MarqueeText(t, p.type.bodyEmphasis, p.colors.primaryText) }
                MarqueeText(model.artist, p.type.subhead, p.colors.secondaryText)
            }
            IconButton(onClick = { haptic(); onPlayPause() }) {
                Icon(if (model.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (model.isPlaying) "Pause" else "Play", tint = p.colors.primaryText)
            }
            IconButton(onClick = { haptic(); onNext() }, enabled = model.hasNext) {
                Icon(Icons.Rounded.SkipNext, "Next", tint = if (model.hasNext) p.colors.primaryText else p.colors.secondaryText)
            }
        }
    }
    if (menu && navigator != null) {
        ModalBottomSheet(onDismissRequest = { menu = false }, shape = p.shapes.sheetTop, containerColor = p.colors.surface) {
            Column(Modifier.navigationBarsPadding().padding(bottom = p.spacing.l)) {
                SheetRow(Icons.AutoMirrored.Rounded.QueueMusic, "Playing next") { menu = false; navigator.queue() }
                SheetRow(Icons.Rounded.Album, "Go to album") { menu = false; navigator.album(model.albumId) }
                SheetRow(Icons.Rounded.Person, "Go to artist") { menu = false; navigator.artist(model.artist) }
                SheetRow(Icons.Rounded.ExpandLess, "Open Now Playing") { menu = false; onOpen() }
            }
        }
    }
}

@Composable
fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val p = LocalPresetUi.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = p.spacing.rowHeight).clickable(onClick = onClick).padding(horizontal = p.spacing.screenMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.colors.accent, modifier = Modifier.size(p.sizes.icon))
        Spacer(Modifier.width(p.spacing.l))
        Text(label, style = p.type.body, color = p.colors.primaryText)
    }
}
