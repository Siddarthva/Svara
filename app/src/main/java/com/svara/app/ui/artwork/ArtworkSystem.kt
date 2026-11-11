package com.svara.app.ui.artwork

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.svara.app.appearance.BackgroundKind
import com.svara.app.appearance.Argb
import com.svara.app.appearance.Contrast
import com.svara.app.ui.components.ArtworkImage
import com.svara.app.ui.presets.common.rememberAlbumColour
import com.svara.app.ui.theme.ArtworkTokens
import com.svara.app.ui.theme.LocalAppearance
import com.svara.app.ui.theme.LocalPresetUi
import com.svara.app.ui.theme.toColor

/** Card formats of the artwork system. The cover is centre-cropped into the frame, never stretched. */
enum class ArtworkFormat(val ratio: Float) {
    SQUARE(ArtworkTokens.SQUARE_RATIO),
    PORTRAIT(ArtworkTokens.PORTRAIT_RATIO),
    WIDE(ArtworkTokens.WIDE_RATIO),
}

/** One cover reference: a song, and the album its art falls back to. */
data class ArtRef(val songId: Long, val albumId: Long)

/**
 * A cover in one of the [ArtworkFormat]s. Coil decodes it at the frame's display size (bucketed
 * and disk-cached by the one artwork loader), so a small tile never decodes a full-size image.
 * The preset's signature lift raises the card with a static shadow.
 */
@Composable
fun ArtworkFrame(
    art: ArtRef,
    format: ArtworkFormat,
    shape: Shape,
    modifier: Modifier = Modifier,
    label: String? = null,
    sharedKey: Any? = null,
    placeholder: ImageVector = Icons.AutoMirrored.Rounded.QueueMusic,
) {
    val p = LocalPresetUi.current
    val lift = p.signature.cardLiftDp.dp
    BoxWithConstraints(modifier.aspectRatio(format.ratio)) {
        val showLabel = minOf(maxWidth, maxHeight) >= ArtworkTokens.fallbackLabelMinSize
        ArtworkImage(
            art.songId, art.albumId,
            Modifier.fillMaxSize().then(if (lift.value > 0f) Modifier.shadow(lift, shape, clip = false) else Modifier),
            shape = shape,
            placeholderIcon = placeholder,
            iconSize = p.sizes.iconLarge,
            sharedKey = sharedKey,
            fallbackLabel = label?.takeIf { showLabel },
        )
    }
}

/**
 * A playlist's cover built from its songs: one cover alone, two or three as a layered stack
 * (front cover full size, the others inset and offset behind it), four or more as a 2×2 mosaic.
 * An empty playlist shows the fallback symbol.
 */
@Composable
fun LayeredArtwork(covers: List<ArtRef>, shape: Shape, modifier: Modifier = Modifier, label: String? = null) {
    val composition = Composition.of(covers)
    when (composition.kind) {
        Composition.Kind.EMPTY -> ArtworkFrame(ArtRef(-1, -1), ArtworkFormat.SQUARE, shape, modifier, label)
        Composition.Kind.SINGLE -> ArtworkFrame(composition.covers.first(), ArtworkFormat.SQUARE, shape, modifier, label)
        Composition.Kind.MOSAIC -> Mosaic(composition.covers, shape, modifier)
        Composition.Kind.STACK -> Stack(composition.covers, shape, modifier)
    }
}

/** Which composition a set of covers gets, and the covers it uses (one per album, in order). */
data class Composition(val kind: Kind, val covers: List<ArtRef>) {
    enum class Kind { EMPTY, SINGLE, STACK, MOSAIC }

    companion object {
        fun of(covers: List<ArtRef>): Composition {
            val distinct = covers.distinctBy { it.albumId.takeIf { id -> id > 0 } ?: -it.songId }
            return when {
                distinct.isEmpty() -> Composition(Kind.EMPTY, emptyList())
                distinct.size == 1 -> Composition(Kind.SINGLE, distinct)
                distinct.size >= ArtworkTokens.MOSAIC_COUNT -> Composition(Kind.MOSAIC, distinct.take(ArtworkTokens.MOSAIC_COUNT))
                else -> Composition(Kind.STACK, distinct.take(ArtworkTokens.LAYERED_MAX))
            }
        }
    }
}

@Composable
private fun Mosaic(covers: List<ArtRef>, shape: Shape, modifier: Modifier) {
    val gap = ArtworkTokens.mosaicGap
    Box(modifier.aspectRatio(ArtworkTokens.SQUARE_RATIO)) {
        Column(Modifier.fillMaxSize().graphicsLayer { this.shape = shape; clip = true }, verticalArrangement = Arrangement.spacedBy(gap)) {
            covers.chunked(2).forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { c -> ArtworkImage(c.songId, c.albumId, Modifier.weight(1f).fillMaxSize(), shape = RectangleShape) }
                }
            }
        }
    }
}

@Composable
private fun Stack(covers: List<ArtRef>, shape: Shape, modifier: Modifier) {
    BoxWithConstraints(modifier.aspectRatio(ArtworkTokens.SQUARE_RATIO)) {
        val inset = maxWidth * ArtworkTokens.LAYER_INSET_FRACTION
        val offset = maxWidth * ArtworkTokens.LAYER_OFFSET_FRACTION
        // Back to front: the first cover is drawn last, on top.
        covers.indices.reversed().forEach { i ->
            val depth = i.toFloat()
            ArtworkImage(
                covers[i].songId, covers[i].albumId,
                Modifier
                    .fillMaxSize()
                    .padding(start = inset * depth, top = inset * depth, end = inset * depth, bottom = inset * depth)
                    .graphicsLayer {
                        translationY = -offset.toPx() * depth
                        alpha = if (i == 0) 1f else ArtworkTokens.LAYER_BACK_ALPHA
                    },
                shape = shape,
            )
        }
    }
}

/** The symbol and label drawn on fallback art. */
@Composable
internal fun ArtworkFallbackContent(icon: ImageVector, iconSize: Dp, label: String, colour: Color) {
    Column(
        Modifier.fillMaxSize().padding(ArtworkTokens.fallbackPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colour, modifier = Modifier.size(iconSize))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = colour,
            textAlign = TextAlign.Center,
            maxLines = ArtworkTokens.FALLBACK_LABEL_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Black or white, whichever reads better on [background] (both are checked for 4.5:1 first). */
internal fun readableOn(background: Color): Color {
    val bg = Argb(background.toArgb().toLong() and ARGB_MASK)
    val white = Argb(WHITE)
    val black = Argb(BLACK)
    return (if (Contrast.ratio(white, bg) >= Contrast.ratio(black, bg)) white else black).toColor()
}

/**
 * A soft glow in the cover's cached colour behind Now Playing artwork, reaching
 * [com.svara.app.appearance.tokens.SignatureTokens.auraSpreadDp] past it. Static (drawn once per
 * colour), no blur. Off when the user turns it off, in Efficiency or battery saver (no cached
 * palette backgrounds), with high contrast, or when the preset's aura strength is 0.
 */
@Composable
fun Modifier.artworkAura(songId: Long, albumId: Long): Modifier {
    val a = LocalAppearance.current
    val p = LocalPresetUi.current
    val allowed = a.custom.artworkAura && !a.highContrast && p.signature.auraAlpha > 0f &&
        BackgroundKind.CACHED_PALETTE in a.limits.backgrounds
    if (!allowed) return this
    val colour = rememberAlbumColour(songId, albumId) ?: return this
    val spread = p.signature.auraSpreadDp.dp
    val alpha = p.signature.auraAlpha
    return drawBehind {
        val reach = spread.toPx()
        val radius = maxOf(size.width, size.height) / 2f + reach
        drawCircle(
            Brush.radialGradient(
                0f to colour.copy(alpha = alpha),
                ArtworkTokens.AURA_FADE_STOP to colour.copy(alpha = 0f),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = radius,
            ),
            radius = radius,
            center = Offset(size.width / 2f, size.height / 2f),
        )
    }
}

private const val ARGB_MASK = 0xFFFFFFFFL
private const val WHITE = 0xFFFFFFFFL
private const val BLACK = 0xFF000000L
