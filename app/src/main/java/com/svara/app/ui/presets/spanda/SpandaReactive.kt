package com.svara.app.ui.presets.spanda

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svara.app.appearance.VisualiserMode
import com.svara.app.appearance.tokens.SpandaSpec
import com.svara.app.nowplaying.VisualiserViewModel
import com.svara.app.playback.QueueEntry
import com.svara.app.playback.analysis.AudioBands
import com.svara.app.ui.presets.common.PlayerHost
import com.svara.app.ui.presets.common.rememberAlbumColour
import com.svara.app.ui.theme.LocalPresetUi

/*
 * ZENITH's optional audio-reactive layer (Theme Studio: Off, Ambient, Reactive, Full Spectrum;
 * Off by default). The effects policy decides what actually runs: nothing in Efficiency, at most
 * the static Ambient glow in Balanced or with reduced motion, analysis only in Immersive.
 * Analysis attaches only while Now Playing is resumed (visible, app in the foreground) and
 * playing, and detaches on pause, on background and when leaving the screen.
 */

/** Attaches analysis for [fps] while resumed and playing; returns the band state (read in draw). */
@Composable
private fun rememberBands(fps: Int, playing: Boolean): androidx.compose.runtime.State<AudioBands>? {
    if (fps <= 0) return null
    val vm: VisualiserViewModel = hiltViewModel()
    val supported by vm.supported.collectAsStateWithLifecycle()
    if (!supported) return null
    LifecycleResumeEffect(fps, playing) {
        if (playing) vm.show(fps) else vm.hide()
        onPauseOrDispose { vm.hide() }
    }
    return vm.bands.collectAsStateWithLifecycle()
}

/**
 * A soft radial glow behind the artwork in the artwork's colour. Ambient: static, updated on
 * track change only. Reactive and Full Spectrum: bass energy scales it, up to 20 fps, drawn in
 * the draw phase so band updates never recompose. Static when analysis can't attach.
 */
@Composable
fun ReactiveGlow(host: PlayerHost, entry: QueueEntry, artSize: Dp) {
    val p = LocalPresetUi.current
    val mode = p.effects.visualiser
    if (mode == VisualiserMode.OFF) return
    val colour = rememberAlbumColour(entry.songId, entry.albumId) ?: p.colors.accent
    val bands = rememberBands(p.effects.glowFps, host.state.isPlaying)
    Box(
        Modifier
            .size(artSize)
            .drawBehind {
                val bass = bands?.value?.bass ?: 0f
                val radius = size.minDimension * (1f + bass * SpandaSpec.GLOW_BASS_GAIN)
                drawCircle(
                    Brush.radialGradient(listOf(colour.copy(alpha = SpandaSpec.GLOW_ALPHA), colour.copy(alpha = 0f)), center, radius),
                    radius = radius,
                )
            },
    )
}

/**
 * Full Spectrum's bars under the seek bar, up to 30 fps. SVARĀ's analyser reports bass, mid and
 * treble energy, so the bars spread those three bands across the width (low to high).
 */
@Composable
fun SpectrumBars(host: PlayerHost, modifier: Modifier) {
    val p = LocalPresetUi.current
    if (p.effects.visualiser != VisualiserMode.FULL_SPECTRUM) return
    val bands = rememberBands(p.effects.spectrumFps, host.state.isPlaying) ?: return
    val colour = p.colors.accent
    Box(
        modifier
            .height(p.spacing.l)
            .drawBehind {
                val b = bands.value
                val minBar = p.spacing.xxs.toPx()
                val n = SpandaSpec.SPECTRUM_BARS
                val gap = size.width / n * BAR_GAP
                val w = size.width / n - gap
                for (i in 0 until n) {
                    val t = i / (n - 1f)
                    // Bass on the left, mid in the middle, treble on the right.
                    val level = if (t < HALF) b.bass + (b.mid - b.bass) * (t / HALF) else b.mid + (b.treble - b.mid) * ((t - HALF) / HALF)
                    val h = (size.height * level.coerceIn(0f, 1f)).coerceAtLeast(minBar)
                    drawRoundRect(colour, Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(w / 2f, w / 2f))
                }
            },
    )
}

private const val HALF = 0.5f
private const val BAR_GAP = 0.35f
