package com.svara.app.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.svara.app.appearance.WavePolicy
import com.svara.app.appearance.tokens.WaveTokens
import com.svara.app.ui.theme.LocalAppearance
import com.svara.app.ui.theme.toColor
import com.svara.app.appearance.ColourSystem
import androidx.compose.material3.MaterialTheme
import kotlin.math.PI
import kotlin.math.sin

/**
 * A slow, layered sine wave tinted with the accent (or the album colour when the colour source is
 * Album artwork). One Canvas, drawn in the draw phase only; the clock runs at the policy's frame
 * cap only while playing (or fading out), and only while the screen is resumed. No pointer input.
 */
@Composable
fun AmbientWave(playing: Boolean, modifier: Modifier = Modifier) {
    val a = LocalAppearance.current
    val spec = WavePolicy.resolve(LocalMotionGroups.current, a.custom)
    if (spec.fps == 0) return
    val colour = (if (a.theme.colourSystem == ColourSystem.ALBUM) a.albumAccent?.toColor() else null) ?: MaterialTheme.colorScheme.primary
    val target by rememberUpdatedState(colour)
    val visibility = remember { Animatable(0f) }
    val clock = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(playing) {
        visibility.animateTo(if (playing) 1f else 0f, tween(WaveTokens.FADE_MS))
    }
    // The clock runs while visible at all; after the fade-out completes it stops entirely.
    // Derived, so the fade's frames don't recompose this composable; only the on/off flip does.
    val isPlaying by rememberUpdatedState(playing)
    val running by remember { derivedStateOf { isPlaying || visibility.value > 0f } }
    LaunchedEffect(running, spec.fps, lifecycle) {
        if (!running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val frameNs = 1_000_000_000L / spec.fps
            var last = 0L
            while (true) {
                withFrameNanos { t ->
                    if (t - last >= frameNs) {
                        last = t
                        clock.floatValue = (t / 1_000_000L % WaveTokens.LOOP_MS).toFloat() / WaveTokens.LOOP_MS
                    }
                }
            }
        }
    }

    val path = remember { Path() }
    // The brush is built once per size or colour change; each frame only moves the path.
    Spacer(
        modifier.clearAndSetSemantics { }.drawWithCache {
            val brush = Brush.verticalGradient(listOf(target.copy(alpha = spec.alpha), Color.Transparent), startY = 0f, endY = size.height)
            val step = size.width / WaveTokens.SEGMENTS
            onDrawBehind {
                val v = visibility.value
                if (v <= 0f) return@onDrawBehind
                val t = clock.floatValue
                for (layer in 0 until spec.layers) {
                    val amp = size.height * WaveTokens.AMPLITUDE[layer] * spec.amplitude
                    val phase = (t * WaveTokens.SPEED[layer] + WaveTokens.PHASE[layer]) * 2 * PI.toFloat()
                    val base = size.height * WaveTokens.BASELINE[layer]
                    path.reset()
                    path.moveTo(0f, size.height)
                    var x = 0f
                    while (x <= size.width + step) {
                        path.lineTo(x, base + amp * sin(x / size.width * WaveTokens.CYCLES[layer] * 2 * PI.toFloat() + phase))
                        x += step
                    }
                    path.lineTo(size.width, size.height)
                    path.close()
                    drawPath(path, brush, alpha = v)
                }
            }
        },
    )
}

/**
 * Whether the Now Playing wave should run, provided by Now Playing only (null elsewhere, so
 * previews and other screens draw no wave).
 */
val LocalPlayerWave = compositionLocalOf<Boolean?> { null }

/**
 * The Now Playing wave, placed by each player right after its background and before its
 * artwork and controls, so it always sits behind them. It fills and is clipped to the bottom
 * third of the screen.
 */
@Composable
fun BoxScope.PlayerWaveLayer() {
    val playing = LocalPlayerWave.current ?: return
    AmbientWave(
        playing,
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(WaveTokens.PLAYER_FRACTION).clipToBounds(),
    )
}
