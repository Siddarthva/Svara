package com.svara.app.ui.presets.common

import androidx.compose.ui.draw.shadow
import com.svara.app.appearance.Argb
import androidx.compose.ui.graphics.toArgb
import com.svara.app.appearance.colour.ContrastEngine
import com.svara.app.appearance.Contrast
import com.svara.app.appearance.ColourSystem
import com.svara.app.ui.theme.LocalAppearance
import kotlinx.coroutines.delay
import com.svara.app.appearance.tokens.ThemeTokens
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import com.svara.app.ui.theme.LocalReducedMotion
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.svara.app.domain.model.Song
import com.svara.app.ui.components.ArtworkImage
import com.svara.app.ui.components.SongActions
import com.svara.app.ui.components.SongMenu
import com.svara.app.ui.home.CoverItem
import com.svara.app.ui.theme.LocalPresetUi

/*
 * Building blocks shared by the four layout presets. Every colour, size and duration comes from
 * LocalPresetUi (the preset's tokens after the effects policy); nothing here is hard-coded.
 */

/**
 * Clickable with the preset's tap feedback: scales to [scale] while pressed, over the policy's
 * press duration, through graphicsLayer (no relayout). With reduced motion nothing animates.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.presetClickable(
    onClickLabel: String? = null,
    role: Role? = Role.Button,
    scale: Float = LocalPresetUi.current.effects.pressScale,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val ms = LocalPresetUi.current.effects.pressMs
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val animated by animateFloatAsState(if (pressed && ms > 0) scale else 1f, tween(ms.coerceAtLeast(1)), label = "press")
    return this
        .graphicsLayer {
            scaleX = animated
            scaleY = animated
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = androidx.compose.foundation.LocalIndication.current,
            onClickLabel = onClickLabel,
            role = role,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/**
 * One line of text that scrolls only when it overflows, and only while motion is allowed (never
 * in Efficiency or with reduced motion, where it ellipsises instead). The drawing area reaches
 * a small lead into the left padding and the text rests just inside it. Once the text moves,
 * glyphs fade out across that lead and the first part of the column instead of being cut at the
 * column edge; at rest nothing is faded, so the title looks exactly like plain text. The right
 * edge fades whenever text is hidden there. The offset is read in the draw phase only, so scrolling never
 * recomposes.
 */
@Composable
fun MarqueeText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val p = LocalPresetUi.current
    if (p.effects.transitionMs <= 0 || LocalReducedMotion.current) {
        Text(text, style = style, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = modifier)
        return
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val maxPx = constraints.maxWidth
        val layout = remember(text, style, density) { measurer.measure(text, style, maxLines = 1, softWrap = false) }
        val textPx = layout.size.width.toFloat()
        if (textPx <= maxPx) {
            Text(text, style = style, color = color, maxLines = 1, softWrap = false)
            return@BoxWithConstraints
        }
        val gapPx = with(density) { p.spacing.xxl.toPx() }
        val fadePx = with(density) { p.spacing.m.toPx() }
        val leadPx = with(density) { p.spacing.s.roundToPx() }
        val travel = textPx + gapPx
        val offset = remember(text) { Animatable(0f) }
        LaunchedEffect(text, maxPx) {
            val ms = (with(density) { travel / ThemeTokens.MARQUEE_DP_PER_SECOND.dp.toPx() } * MILLIS).toInt()
            repeat(ThemeTokens.MARQUEE_ITERATIONS) {
                delay(ThemeTokens.MARQUEE_DELAY_MS)
                offset.animateTo(-travel, tween(ms, easing = LinearEasing))
                offset.snapTo(0f)
            }
        }
        val mask = p.colors.shade
        // Erases towards the left edge: fully at the far edge of the lead, not at all a fade's
        // width inside the column. Drawn at a strength that ramps in as the text starts to move.
        val leftErase = remember(mask, leadPx, fadePx) { Brush.horizontalGradient(listOf(mask, mask.copy(alpha = 0f)), startX = 0f, endX = leadPx + fadePx) }
        val width = (maxPx + leadPx).toFloat()
        val rightMask = remember(mask, width, fadePx) { Brush.horizontalGradient(listOf(mask, mask.copy(alpha = 0f)), startX = width - fadePx, endX = width) }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(with(density) { layout.size.height.toDp() })
                // Widen into the left padding without moving the resting text.
                .layout { measurable, constraints ->
                    val wide = constraints.maxWidth + leadPx
                    val placeable = measurable.measure(constraints.copy(minWidth = wide, maxWidth = wide))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(-leadPx, 0) }
                }
                // Offscreen and clipped, so the masks fade the text itself, not what's behind it.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; clip = true }
                .semantics { contentDescription = text },
        ) {
            val x = leadPx + offset.value
            drawText(layout, color, Offset(x, 0f))
            drawText(layout, color, Offset(x + travel, 0f))
            val strength = (-offset.value / fadePx).coerceIn(0f, 1f)
            if (strength > 0f) drawRect(leftErase, size = Size(leadPx + fadePx, size.height), alpha = strength, blendMode = BlendMode.DstOut)
            drawRect(
                rightMask,
                topLeft = Offset(size.width - fadePx, 0f), size = Size(fadePx, size.height), blendMode = BlendMode.DstIn,
            )
        }
    }
}

private const val MILLIS = 1000

/**
 * First appearance: the content fades in once per screen visit (one animated container), over
 * the policy's section duration. Nothing animates in Efficiency or with reduced motion.
 */
@Composable
fun Modifier.sectionFadeIn(): Modifier {
    val ms = LocalPresetUi.current.effects.sectionFadeMs
    val alpha = remember { Animatable(if (ms > 0) 0f else 1f) }
    LaunchedEffect(Unit) { if (ms > 0) alpha.animateTo(1f, tween(ms)) }
    return graphicsLayer { this.alpha = alpha.value }
}

/** Album art at a token size; decoded at display size by Coil. */
@Composable
fun Cover(
    songId: Long,
    albumId: Long,
    size: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
    placeholder: ImageVector = Icons.AutoMirrored.Rounded.QueueMusic,
    sharedKey: Any? = null,
) {
    val p = LocalPresetUi.current
    ArtworkImage(songId, albumId, modifier.size(size), shape = shape, placeholderIcon = placeholder, iconSize = p.sizes.icon, sharedKey = sharedKey)
}

/** Section title row with an optional trailing action. */
@Composable
fun SectionTitle(title: String, style: TextStyle, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val p = LocalPresetUi.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = p.spacing.screenMargin).heightIn(min = p.spacing.touch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = style, color = p.colors.primaryText, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}

/** A cover with title and subtitle underneath; no card border (artwork and text alignment only). */
@Composable
fun CoverCard(
    item: CoverItem,
    size: Dp,
    titleStyle: TextStyle,
    subtitleStyle: TextStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleLines: Int = 1,
    circular: Boolean = false,
    shape: Shape = LocalPresetUi.current.shapes.artwork,
) {
    val p = LocalPresetUi.current
    val a = LocalAppearance.current
    // With the album-artwork source, the card takes a gradient in its own cover's colour,
    // capped so its title and subtitle keep their contrast.
    val tinted = a.theme.colourSystem == ColourSystem.ALBUM && a.theme.ambientIntensity > 0
    val cardColour = if (tinted) rememberAlbumColour(item.songId, item.albumId) else null
    val wash = remember(cardColour, p.colors, a.theme.ambientIntensity) {
        cardColour?.let { c ->
            val bg = p.colors.background.toArgbValue()
            val desired = a.theme.ambientIntensity / PERCENT * ThemeTokens.AMBIENT_MAX_ALPHA
            val safe = ContrastEngine.maxTintAlpha(c.toArgbValue(), bg, listOf(p.colors.primaryText.toArgbValue(), p.colors.secondaryText.toArgbValue()), Contrast.AA_TEXT, desired.toDouble())
            c.copy(alpha = safe.toFloat())
        }
    }
    Column(
        modifier
            .width(size)
            .clip(p.shapes.card)
            .then(if (wash != null) Modifier.background(Brush.verticalGradient(listOf(wash.copy(alpha = 0f), wash))) else Modifier)
            .presetClickable(onClickLabel = "Open") { onClick() }
            .semantics(mergeDescendants = true) { contentDescription = "${item.title}, ${item.subtitle}" },
    ) {
        // Signature lift: a static shadow at the preset's strength (none with high contrast).
        val lift = p.signature.cardLiftDp
        val coverShape = if (circular) p.shapes.circle else shape
        Cover(
            item.songId, item.albumId, size, coverShape,
            if (lift > 0f && !a.highContrast) Modifier.shadow(lift.dp, coverShape) else Modifier,
        )
        Spacer(Modifier.height(p.spacing.s))
        Text(item.title, style = titleStyle, color = p.colors.primaryText, maxLines = titleLines, overflow = TextOverflow.Ellipsis)
        Text(item.subtitle, style = subtitleStyle, color = p.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A horizontal carousel with a title. Stable keys; artwork loads only for visible items. */
@Composable
fun Carousel(
    title: String,
    items: List<CoverItem>,
    size: Dp,
    titleStyle: TextStyle,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
    onClick: (CoverItem) -> Unit,
    titleLines: Int = 1,
    circular: Boolean = false,
    gap: Dp = LocalPresetUi.current.spacing.gridGutter,
) {
    if (items.isEmpty()) return
    val p = LocalPresetUi.current
    Column(Modifier.fillMaxWidth().padding(top = p.spacing.sectionGap)) {
        SectionTitle(title, titleStyle)
        Spacer(Modifier.height(p.spacing.s))
        LazyRow(
            contentPadding = PaddingValues(horizontal = p.spacing.screenMargin),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(items, key = { it.key }) { item ->
                CoverCard(item, size, cardTitleStyle, cardSubtitleStyle, { onClick(item) }, titleLines = titleLines, circular = circular)
            }
        }
    }
}

/** Pill-shaped toggle (library filters, language selector). */
@Composable
fun PresetPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedFill: Color = LocalPresetUi.current.colors.accentFill,
    selectedText: Color = LocalPresetUi.current.colors.onAccent,
) {
    val p = LocalPresetUi.current
    Text(
        label,
        style = p.type.bodyEmphasis,
        color = if (selected) selectedText else p.colors.primaryText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .heightIn(min = p.spacing.touch)
            .padding(vertical = p.spacing.xs)
            .clip(p.shapes.pill)
            .then(
                if (selected) Modifier.background(selectedFill)
                else Modifier.background(p.colors.surfaceHighlight).border(p.spacing.hairline, p.colors.divider, p.shapes.pill)
            )
            .presetClickable(role = Role.Tab) { onClick() }
            .padding(horizontal = p.spacing.l, vertical = p.spacing.s)
            .semantics { contentDescription = if (selected) "$label, selected" else label },
    )
}

/** List row: artwork, title and artist, three-dot menu. [circular] for artists. */
@Composable
fun PresetRow(
    title: String,
    subtitle: String,
    songId: Long,
    albumId: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
    titleColor: Color = LocalPresetUi.current.colors.primaryText,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = LocalPresetUi.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = p.spacing.rowHeight)
            .presetClickable(scale = 1f) { onClick() }
            .padding(start = p.spacing.screenMargin, end = p.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(songId, albumId, p.sizes.rowArtwork, if (circular) p.shapes.circle else p.shapes.artwork)
        Spacer(Modifier.width(p.spacing.m))
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(title, style = p.type.bodyEmphasis, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = p.type.subhead, color = p.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

/** A song row with the shared song menu (play next, add to playlist, go to album…). */
@Composable
fun PresetSongRow(song: Song, actions: SongActions, isCurrent: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPresetUi.current
    PresetRow(
        title = song.title,
        subtitle = song.displayArtist,
        songId = song.id,
        albumId = song.albumId,
        onClick = onClick,
        modifier = modifier,
        titleColor = if (isCurrent) p.colors.accentText else p.colors.primaryText,
        trailing = { SongMenu(song, actions) },
    )
}

private fun Color.toArgbValue() = Argb(toArgb().toLong() and ARGB_BITS)
private const val ARGB_BITS = 0xFFFFFFFFL
private const val PERCENT = 100f
