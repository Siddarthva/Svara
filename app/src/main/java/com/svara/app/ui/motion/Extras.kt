package com.svara.app.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.svara.app.appearance.AnimationExtra
import com.svara.app.appearance.ExtrasPolicy
import com.svara.app.appearance.tokens.ExtraTokens
import com.svara.app.ui.theme.LocalAppearance
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun extraAllowed(extra: AnimationExtra): Boolean {
    val a = LocalAppearance.current
    return ExtrasPolicy.allowed(extra, a.preset, LocalMotionGroups.current, a.custom)
}

/**
 * Heart burst and note drift: a one-shot ring of dots (or rising dots) drawn over the heart each
 * time [trigger] changes to a new positive value. Draw phase only; nothing runs between taps.
 */
@Composable
fun FavouriteBurst(trigger: Int, colour: Color, modifier: Modifier = Modifier) {
    val burst = extraAllowed(AnimationExtra.HEART_BURST)
    val drift = extraAllowed(AnimationExtra.NOTE_DRIFT)
    if (!burst && !drift) return
    val t = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        t.snapTo(0f)
        t.animateTo(1f, tween(if (drift && !burst) ExtraTokens.NOTE_DRIFT_MS else ExtraTokens.BURST_MS))
    }
    Canvas(modifier.clearAndSetSemantics { }) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val dot = size.minDimension * ExtraTokens.DOT_FRACTION
        val centre = Offset(size.width / 2, size.height / 2)
        if (burst) {
            val reach = size.minDimension * ExtraTokens.BURST_REACH * p
            repeat(ExtraTokens.BURST_DOTS) { i ->
                val angle = (i.toFloat() / ExtraTokens.BURST_DOTS) * 2 * Math.PI.toFloat()
                drawCircle(colour.copy(alpha = 1f - p), dot, centre + Offset(cos(angle) * reach, sin(angle) * reach))
            }
        }
        if (drift) {
            repeat(ExtraTokens.NOTES) { i ->
                val x = centre.x + (i - (ExtraTokens.NOTES - 1) / 2f) * dot * ExtraTokens.NOTE_SPACING
                drawCircle(colour.copy(alpha = 1f - p), dot * ExtraTokens.NOTE_SCALE, Offset(x, centre.y - size.height * p))
            }
        }
    }
}
