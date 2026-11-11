package com.svara.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.svara.app.appearance.Argb
import com.svara.app.appearance.colour.AmbientColours
import com.svara.app.appearance.ArtworkCorner
import com.svara.app.appearance.Customization
import com.svara.app.appearance.EffectLimits
import com.svara.app.appearance.EffectsPolicy
import com.svara.app.appearance.LayoutSet
import com.svara.app.appearance.MetadataDensity
import com.svara.app.appearance.MotionSpec
import com.svara.app.appearance.NavTab
import com.svara.app.appearance.TabVisibility
import com.svara.app.appearance.PerformanceMode
import com.svara.app.appearance.PresetId
import com.svara.app.appearance.ResolvedTheme
import com.svara.app.appearance.ThemeConfig
import com.svara.app.appearance.ThemeResolver

/**
 * Everything presentation-related, resolved once at the root. Screens read this instead of
 * checking settings, battery or thermal state themselves.
 */
@Immutable
data class Appearance(
    val theme: ResolvedTheme = ThemeResolver.resolve(ThemeConfig()),
    val custom: Customization = Customization(),
    val limits: EffectLimits = EffectsPolicy.limitsFor(PerformanceMode.BALANCED),
    val motion: MotionSpec = MotionSpec.NONE,
    val dark: Boolean = true,
    val highContrast: Boolean = false,
    /** The current album's accent from the palette cache, when available. */
    val albumAccent: Argb? = null,
    /** The current cover's colours (album-artwork source), when available. */
    val ambient: AmbientColours? = null,
) {
    val preset: PresetId get() = theme.preset
    val layout: LayoutSet get() = theme.spec.layoutSet

    /** Translucent surfaces: user setting, allowed by the policy, and never with high contrast. */
    val glass: Boolean get() = theme.glass && limits.glassAllowed && !highContrast
    val glow: Boolean get() = theme.glow && limits.glowAllowed && !highContrast

    /** Player background blur in dp; 0 when not allowed. Lists are never blurred. */
    val playerBlurDp: Int
        get() = if ((custom.playerBlur ?: theme.spec.playerBlur) && !highContrast) limits.playerBlurDp else 0

    val compact: Boolean get() = custom.density == MetadataDensity.COMPACT || layout == LayoutSet.DENSE

    /** Every tab this preset can show: its own list, or ETHEREAL's Navigation Studio set. */
    val tabUniverse: List<NavTab>
        get() = theme.spec.tokens.navigation.visibleTabs.ifEmpty { NavTab.ANAHATA }

    /** The bottom bar's tabs: the preset's list minus Navigation Studio's hidden tabs (never empty). */
    val visibleTabs: List<NavTab>
        get() = TabVisibility.visible(theme.spec.tokens.navigation.visibleTabs, custom)

    /** Whether this preset draws its own screens (everything except ETHEREAL). */
    val layoutPreset: Boolean get() = preset != PresetId.ANAHATA
}

val LocalAppearance = staticCompositionLocalOf { Appearance() }

/** Spacing on the 4 dp grid. */
object Spacing {
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    /** Screen side margin on phones; [marginTablet] on wide screens. */
    val margin: Dp = 16.dp
    val marginTablet: Dp = 20.dp
    /** Minimum touch target. */
    val touch: Dp = 48.dp
}

object Radii {
    val small: Dp = 8.dp
    val medium: Dp = 16.dp
    val large: Dp = 28.dp
}

/** At most two tonal elevation levels, never stacked shadows. */
object Elevation {
    val level1: Dp = 1.dp
    val level2: Dp = 3.dp
}

/** Artwork shape from Artwork Studio. [rounded] is the radius used for "Rounded". */
fun artworkShape(corner: ArtworkCorner, rounded: Dp = Radii.small): Shape = when (corner) {
    ArtworkCorner.SQUARE -> RectangleShape
    ArtworkCorner.ROUNDED -> RoundedCornerShape(rounded)
    ArtworkCorner.CIRCLE -> CircleShape
}

@Composable
fun rememberArtworkShape(rounded: Dp = Radii.small): Shape {
    val corner = LocalAppearance.current.custom.corner
    return remember(corner, rounded) { artworkShape(corner, rounded) }
}

fun Argb.toColor(): Color = Color(value.toInt())

/** Haptic feedback for controls; a no-op when the user turned haptics off. Needs no permission. */
@Composable
fun rememberHaptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    val enabled = LocalAppearance.current.custom.haptics
    return remember(haptics, enabled) {
        if (enabled) ({ haptics.performHapticFeedback(HapticFeedbackType.ContextClick) }) else ({})
    }
}
