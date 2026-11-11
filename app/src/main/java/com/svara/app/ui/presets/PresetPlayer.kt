package com.svara.app.ui.presets

import com.svara.app.ui.theme.AnahataTokens
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.svara.app.appearance.ArtworkSize
import com.svara.app.appearance.BackgroundKind
import com.svara.app.appearance.LyricsPosition
import com.svara.app.appearance.PlayerLayout
import com.svara.app.appearance.VisualiserMode
import com.svara.app.ui.components.ArtworkImage
import com.svara.app.ui.theme.LocalAppearance
import com.svara.app.ui.theme.Spacing
import com.svara.app.ui.theme.toColor

/**
 * The pieces of the full-screen player. The screen and its ViewModel build these once; each
 * preset only decides where they go. Nothing here talks to the player directly.
 */
@Immutable
class PlayerParts(
    val artwork: @Composable (Modifier) -> Unit,
    val lyrics: @Composable (Modifier) -> Unit,
    /** Title, artist, favourite. [emphasis] asks for the preset's larger editorial treatment. */
    val info: @Composable (modifier: Modifier, emphasis: Boolean) -> Unit,
    val progress: @Composable (Modifier) -> Unit,
    /** Transport row; [playBackdrop] draws behind play/pause (ZENITH's Bass Pulse). */
    val transport: @Composable (modifier: Modifier, large: Boolean, playBackdrop: (@Composable () -> Unit)?) -> Unit,
    val secondary: @Composable (Modifier) -> Unit,
)

@Composable
fun PresetPlayerBody(
    parts: PlayerParts,
    songId: Long,
    albumId: Long,
    showLyrics: Boolean,
    modifier: Modifier = Modifier,
) {
    val a = LocalAppearance.current
    val layout = a.theme.spec.playerLayout
    val fraction = a.theme.artworkSize.fraction
    val immersiveArt = a.theme.artworkSize == ArtworkSize.IMMERSIVE

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val landscape = maxWidth > maxHeight
        PlayerBackground(layout, songId, albumId, Modifier.matchParentSize())
        val fullLyrics = showLyrics && a.custom.lyricsPosition == LyricsPosition.FULL_SCREEN
        when {
            fullLyrics -> Column(Modifier.fillMaxSize().padding(horizontal = Spacing.xl)) {
                parts.lyrics(Modifier.weight(1f).fillMaxWidth().padding(vertical = Spacing.l))
                parts.progress(Modifier.fillMaxWidth())
                parts.transport(Modifier.fillMaxWidth().padding(bottom = Spacing.l), false, null)
            }
            landscape -> Row(Modifier.fillMaxSize().padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Visual(parts, showLyrics, 1f, Alignment.Center, Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f).padding(start = Spacing.xl)) { ControlStack(parts, layout) }
            }
            else -> Column(Modifier.fillMaxSize().padding(horizontal = if (immersiveArt) AnahataTokens.none else Spacing.xl)) {
                val visualAlign = if (layout == PlayerLayout.EDITORIAL) Alignment.BottomStart else Alignment.Center
                val visualFraction = when (layout) {
                    PlayerLayout.CONTROL_FIRST -> fraction * 0.75f
                    PlayerLayout.EDITORIAL -> fraction * 0.9f
                    else -> fraction
                }
                Visual(parts, showLyrics, visualFraction, visualAlign, Modifier.weight(1f).fillMaxWidth().padding(vertical = Spacing.l))
                Column(Modifier.padding(horizontal = if (immersiveArt) Spacing.xl else AnahataTokens.none)) { ControlStack(parts, layout) }
            }
        }
    }
}

/** Info, progress and controls in the order each preset uses. */
@Composable
private fun ColumnScope.ControlStack(parts: PlayerParts, layout: PlayerLayout) {
    val a = LocalAppearance.current
    val pulse: (@Composable () -> Unit)? = if (layout == PlayerLayout.BACKDROP_VISUALISER && a.theme.visualiser != VisualiserMode.OFF) {
        { BassPulse(a.theme.visualiser, AnahataTokens.bassPulse) }
    } else null
    parts.info(Modifier.fillMaxWidth(), layout == PlayerLayout.EDITORIAL)
    Spacer(Modifier.height(Spacing.s))
    parts.progress(Modifier.fillMaxWidth())
    parts.transport(Modifier.fillMaxWidth(), layout == PlayerLayout.CONTROL_FIRST, pulse)
    Spacer(Modifier.height(Spacing.s))
    if (layout == PlayerLayout.CENTRED_FOCUS) {
        // Focus mode: secondary controls stay out of the way until asked for.
        var revealed by rememberSaveable { mutableStateOf(false) }
        if (revealed) parts.secondary(Modifier.fillMaxWidth())
        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { revealed = true }, modifier = Modifier.heightIn(min = Spacing.touch)) { Text("More controls") }
        }
    } else {
        parts.secondary(Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(Spacing.l))
}

@Composable
private fun Visual(parts: PlayerParts, showLyrics: Boolean, fraction: Float, align: Alignment, modifier: Modifier) {
    if (showLyrics) {
        // Lyrics below a smaller cover.
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            parts.artwork(Modifier.fillMaxWidth(0.4f))
            Spacer(Modifier.height(Spacing.m))
            parts.lyrics(Modifier.weight(1f).fillMaxWidth())
        }
    } else {
        Box(modifier, contentAlignment = align) {
            parts.artwork(Modifier.widthIn(max = AnahataTokens.playerMaxArtwork).fillMaxWidth(fraction.coerceIn(0.3f, 1f)))
        }
    }
}

/**
 * Background per preset, always within the effects policy: OCTAVE's colour field uses the cached
 * palette (static colour in Efficiency); ZENITH's backdrop is blurred art on Android 12+ with a
 * gradient fallback below, never blurred when the policy says no.
 */
@Composable
private fun PlayerBackground(layout: PlayerLayout, songId: Long, albumId: Long, modifier: Modifier) {
    val a = LocalAppearance.current
    if (a.highContrast) return
    val scheme = MaterialTheme.colorScheme
    val paletteAllowed = BackgroundKind.CACHED_PALETTE in a.limits.backgrounds
    val accent = a.albumAccent?.toColor() ?: scheme.primary
    when (layout) {
        PlayerLayout.COLOUR_FIELD -> if (paletteAllowed) {
            Box(modifier.background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.45f), scheme.surface))))
        } else {
            Box(modifier.background(scheme.primaryContainer))
        }
        PlayerLayout.BACKDROP_VISUALISER -> Box(modifier) {
            val blur = a.playerBlurDp
            if (blur > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ArtworkImage(songId, albumId, Modifier.matchParentSize().blur(blur.dp), shape = androidx.compose.ui.graphics.RectangleShape)
                Box(Modifier.matchParentSize().background(scheme.scrim.copy(alpha = 0.55f)))
            } else if (paletteAllowed) {
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), scheme.background))))
            }
            if (a.theme.visualiser == VisualiserMode.AMBIENT) AmbientGlow(Modifier.matchParentSize())
        }
        else -> if (a.glow && paletteAllowed) {
            Box(modifier.background(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0f)))))
        }
    }
}
