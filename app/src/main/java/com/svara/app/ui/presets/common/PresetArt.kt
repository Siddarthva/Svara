package com.svara.app.ui.presets.common

import com.svara.app.ui.theme.LocalAmbient
import com.svara.app.appearance.colour.ContrastEngine
import androidx.compose.runtime.State
import com.svara.app.ui.theme.LocalAppearance
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.svara.app.appearance.Argb
import com.svara.app.appearance.Contrast
import com.svara.app.appearance.Palettes
import com.svara.app.appearance.tokens.ThemeTokens
import com.svara.app.di.PresetArtEntryPoint
import com.svara.app.ui.theme.LocalPresetUi
import com.svara.app.ui.theme.toColor
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

@Composable
private fun rememberArtEntryPoint(): PresetArtEntryPoint {
    val context = LocalContext.current.applicationContext
    return remember(context) { EntryPointAccessors.fromApplication(context, PresetArtEntryPoint::class.java) }
}

/**
 * The cover's colour from the Room palette cache (extracted once from a 128 px image on a
 * background dispatcher, shared by every preset). Covers that don't exist get their stable
 * fallback colour. Null only until known. Re-read when this song's cover changes.
 */
@Composable
fun rememberAlbumColour(songId: Long, albumId: Long): Color? {
    val art = rememberArtEntryPoint()
    val cover by remember(songId, albumId) { art.covers().versionOf(songId, albumId) }.collectAsState(null)
    // Starts from the in-memory colour when this cover was shown before, so scrolling back or
    // switching tabs never flashes the plain placeholder.
    val initial = remember(songId, albumId) { art.palettes().peek(songId, albumId)?.let { (it.muted ?: it.accent ?: it.dominant)?.toColor() } }
    val colour by produceState(initial, songId, albumId, cover) {
        if (songId <= 0 && albumId <= 0) return@produceState
        val song = art.library().songById(songId)
        val c = runCatching {
            art.palettes().coloursFor(songId, albumId, song?.dateModifiedSec ?: 0L, song?.album, song?.fileName.orEmpty())
        }.getOrNull()
        value = (c?.muted ?: c?.accent ?: c?.dominant)?.toColor()
    }
    return colour
}

/**
 * The cached blurred cover at the user's blur strength (built once per cover version and
 * strength, off the main thread). Null while building, and always null when blur is off.
 */
@Composable
fun rememberBlurredArtwork(songId: Long, albumId: Long): ImageBitmap? {
    val art = rememberArtEntryPoint()
    val strength = LocalAppearance.current.theme.blurStrength
    val cover by remember(songId, albumId) { art.covers().versionOf(songId, albumId) }.collectAsState(null)
    val bitmap by produceState<ImageBitmap?>(null, songId, albumId, strength, cover) {
        value = runCatching { art.blurred().blurred(songId, albumId, strength) }.getOrNull()?.asImageBitmap()
    }
    return bitmap
}

private fun Color.argb() = Argb(toArgb().toLong() and ARGB_MASK)

/**
 * Mixes [colour] toward [toward] (the preset's shade in dark themes, its background in light
 * ones) until [text] reads on it at 4.5:1, so text on an artwork-derived block always passes AA.
 */
fun readableBackground(colour: Color, text: Color, toward: Color): Color {
    var c = colour.copy(alpha = 1f).argb()
    val t = text.argb()
    val target = toward.copy(alpha = 1f).argb()
    repeat(STEPS) {
        if (Contrast.passesAA(t, c)) return c.toColor()
        c = Palettes.mix(c, target, STEP)
    }
    return c.toColor()
}

private const val ARGB_MASK = 0xFFFFFFFFL

private const val STEPS = 20
private const val STEP = 0.12

/**
 * Now Playing backdrop: the cached blurred artwork scaled up (bilinear filtering keeps it soft),
 * a black overlay at [scrimAlpha], and optionally a fade into [fadeTo] at the bottom. With
 * [breathingFps] > 0 (OCTAVE, Immersive only) the overlay drifts between [breathMin] and
 * [breathMax] over [breathPeriodMs], drawn in the draw phase and only while the screen is resumed.
 */
@Composable
fun BlurredBackdrop(
    songId: Long,
    albumId: Long,
    scrimAlpha: Float,
    modifier: Modifier = Modifier,
    fadeTo: Color? = null,
    breathingFps: Int = 0,
    breathMin: Float = scrimAlpha,
    breathMax: Float = scrimAlpha,
    breathPeriodMs: Int = 0,
    /** Breathing runs only while music plays: nothing ticks while paused. */
    playing: Boolean = true,
) {
    val p = LocalPresetUi.current
    val a = LocalAppearance.current
    // Efficiency (and battery saver) show a static scrim instead of any blur.
    val bitmap = if (p.effects.cachedBlur) rememberBlurredArtwork(songId, albumId) else null
    // Very bright artwork raises the overlay just enough for white text to keep 4.5:1.
    val floor = remember(bitmap) { bitmap?.let { minimumScrim(it, scrimAlpha) } ?: scrimAlpha }
    val alpha = remember { mutableFloatStateOf(floor) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (breathingFps > 0 && breathPeriodMs > 0 && playing) {
        LaunchedEffect(breathingFps, breathPeriodMs, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val frameMs = (MILLIS_PER_SECOND / breathingFps).toLong()
                var t = 0L
                while (true) {
                    val phase = sin(2 * PI * t / breathPeriodMs).toFloat()
                    alpha.floatValue = maxOf(floor, breathMin + (breathMax - breathMin) * (phase + 1f) / 2f)
                    delay(frameMs)
                    t = (t + frameMs) % breathPeriodMs
                }
            }
        }
    } else {
        alpha.floatValue = floor
    }
    // Tint toggle: the scrim takes a deep shade of the cover colour (dark enough for white text).
    val tint = a.ambient?.dominant?.takeIf { a.theme.blurTint && !a.highContrast }
    val scrim = remember(tint, p.colors.shade) { tint?.let { ContrastEngine.deepShade(it).toColor() } ?: p.colors.shade }
    val ambient = LocalAmbient.current
    Box(modifier.fillMaxSize().background(p.colors.background)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().drawBehind {
                // Ambient tint under the scrim, so text contrast still comes from the scrim.
                val wash = ambient.value
                if (wash.alpha > 0f) drawRect(wash)
                drawRect(scrim.copy(alpha = alpha.floatValue))
                if (fadeTo != null) drawRect(Brush.verticalGradient(listOf(fadeTo.copy(alpha = 0f), fadeTo), startY = size.height * FADE_START))
            },
        )
    }
}

/**
 * The album page header's cinematic background: the cached blurred cover fading into [fadeTo]
 * at the bottom, under a [fadeTo]-coloured overlay strong enough for [text] to keep 4.5:1. Static;
 * nothing at all in Efficiency.
 */
@Composable
fun AlbumHeaderBackdrop(songId: Long, albumId: Long, fadeTo: Color, text: Color, modifier: Modifier = Modifier) {
    val p = LocalPresetUi.current
    if (!p.effects.cachedBlur) return
    val bitmap = rememberBlurredArtwork(songId, albumId) ?: return
    val overlay = remember(bitmap, fadeTo, text) { minimumOverlay(bitmap, fadeTo.argb(), text.argb()) }
    Box(modifier) {
        Image(bitmap, null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low, modifier = Modifier.matchParentSize())
        Box(
            Modifier.matchParentSize().drawBehind {
                drawRect(fadeTo.copy(alpha = overlay))
                drawRect(Brush.verticalGradient(listOf(fadeTo.copy(alpha = 0f), fadeTo), startY = size.height * FADE_START))
            },
        )
    }
}

/** A tint wash from the top (home headers): the ambient colour fading out by 40 % of the height. Draw phase only. */
fun Modifier.ambientWash(ambient: State<Color>): Modifier = drawBehind {
    val c = ambient.value
    if (c.alpha > 0f) drawRect(Brush.verticalGradient(0f to c, AMBIENT_WASH_END to c.copy(alpha = 0f)))
}

/** A flat ambient tint (mini-players). Draw phase only. */
fun Modifier.ambientFill(ambient: State<Color>): Modifier = drawBehind {
    val c = ambient.value
    if (c.alpha > 0f) drawRect(c)
}

private const val AMBIENT_WASH_END = 0.4f

/**
 * The smallest alpha of an [overlay]-coloured layer over the brightest pixel of [bitmap] at
 * which [text] reads at 4.5:1.
 */
fun minimumOverlay(bitmap: ImageBitmap, overlay: Argb, text: Argb): Float {
    val px = IntArray(bitmap.width * bitmap.height)
    bitmap.readPixels(px)
    // The pixel that's hardest for this text: farthest from the overlay in luminance terms.
    val worst = px.map { Argb(it.toLong() and ARGB_MASK) }.minByOrNull { Contrast.ratio(text, it) } ?: return MAX_SCRIM
    var a = 0f
    while (a < 1f) {
        val under = Palettes.mix(worst, overlay, a.toDouble())
        if (Contrast.ratio(Contrast.composite(text, under), under) >= Contrast.AA_TEXT) return a
        a += SCRIM_STEP
    }
    return 1f
}

/**
 * The smallest overlay alpha (at least [spec]) at which secondary text (white at
 * [ThemeTokens.ON_SHADE_SECONDARY_ALPHA]) passes 4.5:1 over the brightest pixel of the 32 px
 * blurred artwork. Primary (opaque white) text then passes too.
 */
fun minimumScrim(bitmap: ImageBitmap, spec: Float): Float {
    val px = IntArray(bitmap.width * bitmap.height)
    bitmap.readPixels(px)
    val brightest = px.maxByOrNull { Contrast.luminance(Argb(it.toLong() and ARGB_MASK)) } ?: return spec
    val secondary = Argb(((ThemeTokens.ON_SHADE_SECONDARY_ALPHA * ALPHA_MAX).toLong() shl ALPHA_SHIFT) or WHITE_RGB)
    val black = Argb(BLACK)
    var a = spec
    while (a < MAX_SCRIM) {
        val shaded = Palettes.mix(Argb(brightest.toLong() and ARGB_MASK), black, a.toDouble())
        if (Contrast.ratio(Contrast.composite(secondary, shaded), shaded) >= Contrast.AA_TEXT) return a
        a += SCRIM_STEP
    }
    return MAX_SCRIM
}

private const val WHITE_RGB = 0x00FFFFFFL
private const val ALPHA_MAX = 255f
private const val ALPHA_SHIFT = 24
private const val BLACK = 0xFF000000L
private const val MAX_SCRIM = 0.9f
private const val SCRIM_STEP = 0.05f
private const val MILLIS_PER_SECOND = 1000f
/** The bottom fade starts at this fraction of the height. */
private const val FADE_START = 0.55f
