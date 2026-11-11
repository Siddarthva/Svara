package com.svara.app.ui.presets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.svara.app.appearance.VisualiserMode
import com.svara.app.nowplaying.VisualiserViewModel
import com.svara.app.ui.theme.LocalAppearance
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Frame rate the visualiser may use right now; 0 means it stays static. */
@Composable
fun visualiserFps(mode: VisualiserMode): Int {
    val a = LocalAppearance.current
    if (!a.motion.enabled) return 0
    return when (mode) {
        VisualiserMode.REACTIVE -> min(BASS_PULSE_MAX_FPS, a.limits.visualiserFps)
        VisualiserMode.FULL_SPECTRUM -> a.limits.visualiserFps
        VisualiserMode.OFF, VisualiserMode.AMBIENT -> 0
    }
}

private const val BASS_PULSE_MAX_FPS = 20

/**
 * Bass Pulse: a circle behind the play button that scales with bass energy (Reactive, up to
 * 20 fps), plus rings for mid and treble in Full Spectrum (up to 30 fps). Static in Ambient,
 * in Efficiency and Balanced (the policy gives 0 fps), with reduced motion, and when analysis is
 * unavailable. Analysis runs only while this is on screen and the activity is resumed.
 */
@Composable
fun BassPulse(mode: VisualiserMode, size: Dp, modifier: Modifier = Modifier) {
    if (mode == VisualiserMode.OFF) return
    val a = LocalAppearance.current
    val vm: VisualiserViewModel = hiltViewModel()
    val supported by vm.supported.collectAsStateWithLifecycle()
    val fps = visualiserFps(mode)
    val reactive = mode.needsAnalysis && fps > 0 && supported
    if (reactive) {
        LifecycleResumeEffect(fps) {
            vm.show(fps)
            onPauseOrDispose { vm.hide() }
        }
    }
    val bands = vm.bands.collectAsStateWithLifecycle()
    val colour = MaterialTheme.colorScheme.primary
    val intensity = a.motion.intensity
    val glow = a.glow
    Box(
        modifier
            .size(size)
            .drawBehind {
                // Read in the draw phase: band updates redraw this box without recomposition.
                val b = if (reactive) bands.value else null
                val base = this.size.minDimension / 2f * 0.72f
                val scale = 1f + (b?.bass ?: 0f) * 0.4f * intensity
                if (glow) {
                    drawCircle(
                        Brush.radialGradient(listOf(colour.copy(alpha = 0.35f), colour.copy(alpha = 0f)), center, base * 1.6f * scale),
                        radius = base * 1.6f * scale,
                    )
                }
                drawCircle(colour.copy(alpha = 0.22f), radius = base * scale)
                if (mode == VisualiserMode.FULL_SPECTRUM && b != null) {
                    drawCircle(colour.copy(alpha = 0.18f + b.mid * 0.4f), radius = base * (1.18f + b.mid * 0.25f * intensity), style = Stroke(width = base * 0.06f))
                    drawCircle(colour.copy(alpha = 0.12f + b.treble * 0.4f), radius = base * (1.36f + b.treble * 0.2f * intensity), style = Stroke(width = base * 0.04f))
                }
            },
    )
}

/**
 * Slow ambient light for ZENITH's Ambient mode. Not driven by audio. Runs at the policy's
 * ambient frame rate and only while the screen is resumed; otherwise draws one static frame.
 */
@Composable
fun AmbientGlow(modifier: Modifier = Modifier) {
    val a = LocalAppearance.current
    val fps = if (a.motion.enabled) a.limits.ambientFps else 0
    val phase = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (fps > 0) {
        LaunchedEffect(fps, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val frameMs = 1000L / fps
                var t = phase.floatValue
                while (true) {
                    delay(frameMs)
                    t = (t + frameMs / AMBIENT_PERIOD_MS) % 1f
                    phase.floatValue = t
                }
            }
        }
    }
    val colour = MaterialTheme.colorScheme.primary
    val intensity = a.motion.intensity
    Box(
        modifier.drawBehind {
            val p = phase.floatValue * 2 * PI
            val c = Offset(
                size.width * (0.5f + 0.18f * intensity * sin(p).toFloat()),
                size.height * (0.38f + 0.1f * intensity * sin(p * 2).toFloat()),
            )
            val r = size.minDimension * 0.75f
            drawRect(Brush.radialGradient(listOf(colour.copy(alpha = 0.28f), colour.copy(alpha = 0f)), c, r))
        },
    )
}

private const val AMBIENT_PERIOD_MS = 12_000f
