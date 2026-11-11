package com.svara.app.ui.presets

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.svara.app.appearance.LayoutSet
import com.svara.app.appearance.Palettes
import com.svara.app.appearance.PresetId
import com.svara.app.appearance.Presets
import com.svara.app.ui.theme.LocalAppearance
import com.svara.app.ui.theme.toColor

/**
 * A preset's preview card art. Drawn once from the preset's own tokens (no bitmap assets, no
 * animation). Only the selected card passes [live], which adds a small level meter that moves
 * while the chooser is on screen and motion is allowed.
 */
@Composable
fun PresetPreview(id: PresetId, live: Boolean, modifier: Modifier = Modifier) {
    val spec = remember(id) { Presets.spec(id) }
    val t = spec.palette.dark
    val tiles = spec.palette.tiles.map { it.toColor() }
    val bg = t.background.toColor()
    val surface = t.surfaceVariant.toColor()
    val on = t.onSurfaceVariant.toColor()
    val accent = t.accent.toColor()
    val motion = LocalAppearance.current.motion
    val level: State<Float> = if (live && motion.enabled) {
        val transition = rememberInfiniteTransition(label = "preview")
        transition.animateFloat(0.2f, 1f, infiniteRepeatable(tween(motion.screen.coerceAtLeast(300) * 2, easing = LinearEasing), RepeatMode.Reverse), label = "level")
    } else remember { mutableFloatStateOf(0.6f) }

    Box(modifier.semantics { contentDescription = "${id.displayName} preview" }) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(bg)
            when (spec.layoutSet) {
                LayoutSet.ARTWORK_CENTRED -> {
                    val side = size.minDimension * 0.5f
                    roundRect(surface, Offset((size.width - side) / 2, size.height * 0.12f), Size(side, side), 12f)
                    line(on, size.height * 0.12f + side + 16f, 0.5f)
                    line(on.copy(alpha = 0.6f), size.height * 0.12f + side + 34f, 0.35f)
                    drawCircle(accent, size.minDimension * 0.07f, Offset(size.width / 2, size.height * 0.86f))
                }
                LayoutSet.EDITORIAL -> {
                    val gap = 10f
                    val cell = (size.width - gap * 3) / 2
                    for (i in 0 until 2) roundRect(surface, Offset(gap + i * (cell + gap), gap), Size(cell, cell * 0.8f), 4f)
                    line(on, cell * 0.8f + gap * 3, 0.6f, start = gap)
                    line(on.copy(alpha = 0.6f), cell * 0.8f + gap * 5, 0.4f, start = gap)
                }
                LayoutSet.DENSE -> for (i in 0 until 5) {
                    val y = 10f + i * size.height / 5.4f
                    roundRect(surface, Offset(10f, y), Size(size.height / 7f, size.height / 7f), 4f)
                    line(on, y + size.height / 20f, 0.5f, start = size.height / 7f + 22f)
                    drawCircle(accent, 5f, Offset(size.width - 16f, y + size.height / 14f))
                }
                LayoutSet.COLOUR_TILES -> {
                    val gap = 8f
                    val w = (size.width - gap * 3) / 2
                    val h = (size.height - gap * 3) / 2
                    tiles.take(4).forEachIndexed { i, c -> roundRect(c, Offset(gap + (i % 2) * (w + gap), gap + (i / 2) * (h + gap)), Size(w, h), 14f) }
                }
                LayoutSet.IMMERSIVE -> {
                    drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0f)), Offset(size.width / 2, size.height * 0.45f), size.minDimension * 0.7f))
                    drawCircle(accent.copy(alpha = 0.3f), size.minDimension * 0.22f, Offset(size.width / 2, size.height * 0.45f))
                    drawCircle(accent, size.minDimension * 0.08f, Offset(size.width / 2, size.height * 0.45f))
                }
            }
            // Level meter: static unless this is the selected, live card.
            val v = level.value
            for (i in 0 until 3) {
                val h = size.height * 0.18f * (0.4f + 0.6f * ((v + i * 0.33f) % 1f))
                roundRect(accent, Offset(size.width - 44f + i * 12f, size.height - 10f - h), Size(8f, h), 3f)
            }
        }
    }
}

private fun DrawScope.roundRect(color: Color, topLeft: Offset, size: Size, radius: Float) =
    drawRoundRect(color, topLeft, size, CornerRadius(radius, radius))

private fun DrawScope.line(color: Color, y: Float, widthFraction: Float, start: Float = -1f) {
    val w = size.width * widthFraction
    val x = if (start >= 0) start else (size.width - w) / 2
    drawRoundRect(color, Offset(x, y), Size(w, 8f), CornerRadius(4f, 4f))
}

/** Swatch colour for the custom-accent picker. */
fun accentSwatch(index: Int): Color = Palettes.ACCENTS[index].colour.toColor()
