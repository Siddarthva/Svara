package com.svara.app.ui.theme

import com.svara.app.appearance.tokens.ThemeTokens
import com.svara.app.appearance.colour.ContrastEngine
import com.svara.app.appearance.ColourSystem
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.svara.app.appearance.Argb
import com.svara.app.appearance.colour.EffectivePalette
import com.svara.app.appearance.ColourTokens
import com.svara.app.appearance.FontKind
import com.svara.app.appearance.MotionSpec
import com.svara.app.appearance.Palettes
import com.svara.app.appearance.PresetId
import com.svara.app.appearance.TypeTokens

/** True when the user asked for reduced motion in SVARĀ, or turned animations off system-wide. */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * Root theme. Colours come from the effective palette (preset tokens, colour source, background
 * tone and High contrast), type from the preset's scale in the bundled sans-serif, and text size
 * is Typography Studio's scale multiplied with the system font scale.
 */
@Composable
fun SvaraTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemAnimationsOff = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val resolved = if (systemAnimationsOff && appearance.motion.enabled) appearance.copy(motion = MotionSpec.NONE) else appearance
    val dark = resolved.dark
    val theme = resolved.theme
    // One palette for every screen: the preset's tokens after colour source, tone and contrast.
    // High contrast strengthens this palette; it never swaps in a different accent.
    val palette = remember(resolved.highContrast, dark, theme, resolved.albumAccent) {
        EffectivePalette.build(theme.spec.tokens, theme, dark, resolved.highContrast, resolved.albumAccent)
    }
    val colorScheme = remember(palette, dark) { schemeFrom(Palettes.bridge(palette.palette), dark) }
    val layoutPreset = theme.preset != PresetId.ANAHATA
    val typography = remember(theme.spec) {
        if (layoutPreset) materialTypography(theme.spec.tokens.typeScale) else typographyFor(theme.spec.typography)
    }
    val shapes = remember(theme.spec) { if (layoutPreset) materialShapes(theme.spec.tokens) else SvaraShapes }
    val presetUi = remember(theme.spec, palette, dark, resolved.highContrast, resolved.limits, resolved.motion.enabled, theme.visualiser) {
        PresetUi.build(theme.preset, theme.spec.tokens, palette.palette, dark, resolved.highContrast, resolved.limits, resolved.motion.enabled, theme.visualiser)
    }
    // Ambient tint: the cover's dominant colour, only with the album-artwork source and an
    // intensity above 0, at the largest alpha that keeps text at its contrast target.
    val ambientTarget = remember(resolved.ambient, theme.colourSystem, theme.ambientIntensity, palette) {
        val colours = resolved.ambient
        if (colours == null || theme.colourSystem != ColourSystem.ALBUM || theme.ambientIntensity <= 0) Color.Transparent
        else {
            val pal = palette.palette
            val desired = theme.ambientIntensity / PERCENT * ThemeTokens.AMBIENT_MAX_ALPHA
            val safe = ContrastEngine.maxTintAlpha(colours.dominant, pal.background, listOf(pal.primaryText, pal.secondaryText), palette.targets.text, desired.toDouble())
            colours.dominant.toColor().copy(alpha = safe.toFloat())
        }
    }
    val ambient = animateColorAsState(ambientTarget, tween(presetUi.effects.ambientCrossfadeMs), label = "ambient")
    val density = LocalDensity.current
    val scale = resolved.custom.textScale / 100f
    val scaled = remember(density, scale) { Density(density.density, density.fontScale * scale) }
    CompositionLocalProvider(
        LocalAppearance provides resolved,
        LocalReducedMotion provides !resolved.motion.enabled,
        LocalDensity provides scaled,
        LocalPresetUi provides presetUi,
        LocalAmbient provides ambient,
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = shapes, content = content)
    }
}

private const val PERCENT = 100f

private val SvaraShapes = Shapes(
    extraSmall = RoundedCornerShape(Radii.small),
    small = RoundedCornerShape(Radii.small),
    medium = RoundedCornerShape(Radii.medium),
    large = RoundedCornerShape(Radii.large),
    extraLarge = RoundedCornerShape(Radii.large),
)

/** Builds a Material scheme from semantic tokens; containers are tonal mixes of accent and surface. */
fun schemeFrom(t: ColourTokens, dark: Boolean): ColorScheme {
    fun c(a: Argb): Color = a.toColor()
    val container = Palettes.mix(t.surface, t.accent, if (dark) 0.22 else 0.16)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c(t.accent),
        onPrimary = c(t.onAccent),
        primaryContainer = c(container),
        onPrimaryContainer = c(t.onSurface),
        secondary = c(t.accent),
        onSecondary = c(t.onAccent),
        secondaryContainer = c(container),
        onSecondaryContainer = c(t.onSurface),
        tertiary = c(t.accent),
        onTertiary = c(t.onAccent),
        tertiaryContainer = c(container),
        onTertiaryContainer = c(t.onSurface),
        background = c(t.background),
        onBackground = c(t.onSurface),
        surface = c(t.surface),
        onSurface = c(t.onSurface),
        surfaceVariant = c(t.surfaceVariant),
        onSurfaceVariant = c(t.onSurfaceVariant),
        surfaceTint = c(t.accent),
        inverseSurface = c(t.onSurface),
        inverseOnSurface = c(t.surface),
        outline = c(t.onSurfaceVariant),
        outlineVariant = c(t.surfaceVariant),
        scrim = c(t.scrim),
        surfaceBright = c(t.surfaceVariant),
        surfaceDim = c(t.background),
        surfaceContainerLowest = c(t.background),
        surfaceContainerLow = c(t.surface),
        surfaceContainer = c(Palettes.mix(t.surface, t.surfaceVariant, 0.35)),
        surfaceContainerHigh = c(Palettes.mix(t.surface, t.surfaceVariant, 0.6)),
        surfaceContainerHighest = c(t.surfaceVariant),
    )
}

/** The 5-step type scale: label, body, title, headline, display. In sp, so it follows font scale. */
object TypeScale {
    val label: TextUnit = 12.sp
    val body: TextUnit = 15.sp
    val title: TextUnit = 18.sp
    val headline: TextUnit = 26.sp
    val display: TextUnit = 36.sp
}

/** Every kind maps to the bundled family; the platform "sans-serif" can be a serif face on some phones. */
@Suppress("UNUSED_PARAMETER")
private fun family(kind: FontKind): FontFamily = AppFont

fun typographyFor(tokens: TypeTokens): Typography {
    val display = family(tokens.display)
    val body = family(tokens.body)
    val heading = FontWeight(tokens.headlineWeight.coerceIn(100, 900))
    fun style(f: FontFamily, size: TextUnit, weight: FontWeight, lineHeight: Float) =
        TextStyle(fontFamily = f, fontSize = size, fontWeight = weight, lineHeight = size * lineHeight)
    val label = style(body, TypeScale.label, FontWeight.Medium, 1.35f)
    return Typography(
        displayLarge = style(display, TypeScale.display, heading, 1.15f),
        displayMedium = style(display, TypeScale.display, heading, 1.15f),
        displaySmall = style(display, TypeScale.display, FontWeight.Normal, 1.15f),
        headlineLarge = style(display, TypeScale.headline, heading, 1.2f),
        headlineMedium = style(display, TypeScale.headline, heading, 1.2f),
        headlineSmall = style(display, TypeScale.headline, FontWeight.Normal, 1.2f),
        titleLarge = style(display, TypeScale.title, heading, 1.3f),
        titleMedium = style(body, TypeScale.title, FontWeight.Medium, 1.3f),
        titleSmall = style(body, TypeScale.body, FontWeight.SemiBold, 1.35f),
        bodyLarge = style(body, TypeScale.body, FontWeight.Normal, 1.4f),
        bodyMedium = style(body, TypeScale.body, FontWeight.Normal, 1.4f),
        bodySmall = style(body, TypeScale.label, FontWeight.Normal, 1.35f),
        labelLarge = label.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = label,
        labelSmall = label,
    )
}
