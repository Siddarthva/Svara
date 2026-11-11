package com.svara.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.svara.app.appearance.Argb
import com.svara.app.appearance.EffectLimits
import com.svara.app.appearance.EffectsPolicy
import com.svara.app.appearance.PerformanceMode
import com.svara.app.appearance.PresetEffects
import com.svara.app.appearance.PresetId
import com.svara.app.appearance.VisualiserMode
import com.svara.app.appearance.colour.ContrastEngine
import com.svara.app.appearance.colour.ContrastTargets
import com.svara.app.appearance.tokens.AnahataSpec
import com.svara.app.appearance.tokens.MotionTokens
import com.svara.app.appearance.tokens.NavigationTokens
import com.svara.app.appearance.tokens.SignatureTokens
import com.svara.app.appearance.tokens.PaletteTokens
import com.svara.app.appearance.tokens.PresetTokens
import com.svara.app.appearance.tokens.ThemeTokens
import com.svara.app.appearance.tokens.TypeRole
import com.svara.app.appearance.tokens.TypeScaleTokens

/*
 * Compose form of the preset tokens. This file (with appearance/tokens) is where raw numbers
 * become Dp, sp and Color; preset composables only read LocalPresetUi.
 */

@Immutable
data class PresetColors(
    val background: Color,
    val surface: Color,
    val surfaceHighlight: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val onAccent: Color,
    val scrim: Color,
    val divider: Color,
    val accentText: Color,
    val accentFill: Color,
    /** Plain black, for artwork scrims and shadows (the specs' overlays are black at an alpha). */
    val shade: Color,
    /** Plain white, for text over artwork scrims, which are always dark. */
    val onShade: Color,
    val onShadeSecondary: Color,
    val onShadeTrack: Color,
    /** Accent as text over dark backdrops: the dark palette's AA-safe accent, in any theme. */
    val accentOnShade: Color,
    val transparent: Color,
)

@Immutable
data class PresetType(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodyEmphasis: TextStyle,
    val subhead: TextStyle,
    val caption: TextStyle,
    val micro: TextStyle,
    /** SOLACE's lyrics line; falls back to headline. */
    val lyricsActive: TextStyle,
)

@Immutable
data class PresetShapes(
    val artwork: Shape,
    val card: Shape,
    val pill: Shape,
    val sheetTop: Shape,
    val miniPlayer: Shape,
    val hero: Shape,
    val lyricsCard: Shape,
    val searchField: Shape,
    val circle: Shape,
    val artworkRadius: Dp,
    val sheetTopRadius: Dp,
)

@Immutable
data class PresetSpacing(
    val screenMargin: Dp,
    val rowHeight: Dp,
    val gridGutter: Dp,
    val sectionGap: Dp,
    val xxs: Dp,
    val xs: Dp,
    val s: Dp,
    val m: Dp,
    val l: Dp,
    val xl: Dp,
    val xxl: Dp,
    val touch: Dp,
    val hairline: Dp,
)

@Immutable
data class PresetSizes(
    val miniArtwork: Dp,
    val miniPlayerHeight: Dp,
    val carouselArtwork: Dp,
    val quickTileArtwork: Dp,
    val rowArtwork: Dp,
    val artistArtwork: Dp,
    val playButton: Dp,
    val playIcon: Dp,
    val transportButton: Dp,
    val transportIcon: Dp,
    val scrubberTrack: Dp,
    val scrubberTrackDragging: Dp,
    val scrubberHandle: Dp,
    val handleOnlyWhileDragging: Boolean,
    val artworkElevation: Dp,
    val playerArtworkFraction: Float,
    val bannerAspect: Float,
    val chevron: Dp,
    val icon: Dp,
    val iconSmall: Dp,
    val iconLarge: Dp,
    val pastLineAlpha: Float,
    val otherLineAlpha: Float,
    val playerScrimAlpha: Float,
    val disabledAlpha: Float,
    val miniPlayerAlpha: Float,
    val liveBlurRadius: Dp,
    val smallArtworkElevation: Dp,
)

/** Everything a layout preset's composables need, resolved once per theme change. */
@Immutable
class PresetUi(
    val id: PresetId,
    val dark: Boolean,
    val colors: PresetColors,
    val type: PresetType,
    val shapes: PresetShapes,
    val spacing: PresetSpacing,
    val sizes: PresetSizes,
    val motion: MotionTokens,
    val effects: PresetEffects,
    val navigation: NavigationTokens,
    val signature: SignatureTokens = SignatureTokens(),
) {
    companion object {
        /** [palette] is the effective palette (see EffectivePalette); High contrast is already in it. */
        fun build(
            id: PresetId,
            tokens: PresetTokens,
            palette: PaletteTokens,
            dark: Boolean,
            highContrast: Boolean,
            limits: EffectLimits,
            motionEnabled: Boolean,
            visualiser: VisualiserMode,
        ): PresetUi {
            // Over dark backdrops the accent as text must read on near-black, in any theme.
            val onShadeAccent = ContrastEngine.fit(palette.accent, listOf(Argb(SHADE)), ContrastTargets.NORMAL.text).colour
            return PresetUi(
                id = id,
                dark = dark,
                colors = colors(palette, onShadeAccent),
                type = type(tokens.typeScale, tokens.sizes.lyricsActiveSp),
                shapes = shapes(tokens),
                spacing = spacing(tokens, highContrast),
                sizes = sizes(tokens),
                motion = tokens.motion,
                effects = PresetEffects.resolve(tokens.motion, limits, motionEnabled, visualiser),
                navigation = tokens.navigation,
                signature = tokens.signature,
            )
        }

        private const val SHADE = 0xFF000000L

        val DEFAULT: PresetUi = build(
            PresetId.ANAHATA, AnahataSpec.tokens, AnahataSpec.palette.dark, dark = true, highContrast = false,
            limits = EffectsPolicy.limitsFor(PerformanceMode.BALANCED), motionEnabled = true, visualiser = VisualiserMode.OFF,
        )
    }
}

val LocalPresetUi = staticCompositionLocalOf { PresetUi.DEFAULT }

/**
 * The ambient tint (cover colour at its contrast-safe strength), cross-faded on track change.
 * A State, read only at draw time, so a track change redraws tinted areas without recomposing.
 */
val LocalAmbient = staticCompositionLocalOf<State<Color>> { mutableStateOf(Color.Transparent) }

private fun Argb.c() = toColor()

private fun colors(p: PaletteTokens, accentOnShade: Argb) = PresetColors(
    background = p.background.c(),
    surface = p.surface.c(),
    surfaceHighlight = p.surfaceHighlight.c(),
    primaryText = p.primaryText.c(),
    secondaryText = p.secondaryText.c(),
    accent = p.accent.c(),
    onAccent = p.onAccent.c(),
    scrim = p.scrim.c(),
    divider = p.divider.c(),
    accentText = p.accentText.c(),
    accentFill = p.accentFill.c(),
    shade = Color.Black,
    onShade = Color.White,
    onShadeSecondary = Color.White.copy(alpha = ThemeTokens.ON_SHADE_SECONDARY_ALPHA),
    onShadeTrack = Color.White.copy(alpha = ThemeTokens.ON_SHADE_TRACK_ALPHA),
    accentOnShade = accentOnShade.c(),
    transparent = Color.Transparent,
)

fun TypeRole.toTextStyle(): TextStyle = TextStyle(
    fontFamily = AppFont,
    fontSize = sizeSp.sp,
    fontWeight = FontWeight(weight),
    lineHeight = lineHeightSp.sp,
    letterSpacing = letterSpacingEm.em,
)

private fun type(t: TypeScaleTokens, lyricsActiveSp: Float): PresetType {
    val headline = t.headline.toTextStyle()
    return PresetType(
        display = t.display.toTextStyle(),
        headline = headline,
        title = t.title.toTextStyle(),
        body = t.body.toTextStyle(),
        bodyEmphasis = t.bodyEmphasis.toTextStyle(),
        subhead = t.subhead.toTextStyle(),
        caption = t.caption.toTextStyle(),
        micro = t.micro.toTextStyle(),
        lyricsActive = if (lyricsActiveSp > 0f) {
            headline.copy(fontSize = lyricsActiveSp.sp, lineHeight = (lyricsActiveSp * ThemeTokens.LEADING_TIGHT).sp, fontWeight = FontWeight.Bold)
        } else headline,
    )
}

private fun shapes(t: PresetTokens): PresetShapes {
    val s = t.shape
    return PresetShapes(
        artwork = RoundedCornerShape(s.artworkRadius.dp),
        card = RoundedCornerShape(s.cardRadius.dp),
        pill = RoundedCornerShape(s.pillRadiusPercent),
        sheetTop = RoundedCornerShape(topStart = s.sheetTopRadius.dp, topEnd = s.sheetTopRadius.dp),
        miniPlayer = RoundedCornerShape(s.miniPlayerRadius.dp),
        hero = RoundedCornerShape(s.heroRadius.dp),
        lyricsCard = RoundedCornerShape(s.lyricsCardRadius.dp),
        searchField = RoundedCornerShape(s.searchFieldRadius.dp),
        circle = RoundedCornerShape(ThemeTokens.PILL_PERCENT),
        artworkRadius = s.artworkRadius.dp,
        sheetTopRadius = s.sheetTopRadius.dp,
    )
}

private fun spacing(t: PresetTokens, highContrast: Boolean) = PresetSpacing(
    screenMargin = t.spacing.screenMargin.dp,
    rowHeight = maxOf(t.spacing.rowHeight, ThemeTokens.MIN_TOUCH).dp,
    gridGutter = t.spacing.gridGutter.dp,
    sectionGap = t.spacing.sectionGap.dp,
    xxs = ThemeTokens.SPACE_XXS.dp,
    xs = ThemeTokens.SPACE_XS.dp,
    s = ThemeTokens.SPACE_S.dp,
    m = ThemeTokens.SPACE_M.dp,
    l = ThemeTokens.SPACE_L.dp,
    xl = ThemeTokens.SPACE_XL.dp,
    xxl = ThemeTokens.SPACE_XXL.dp,
    touch = ThemeTokens.MIN_TOUCH.dp,
    // High contrast thickens outlines and dividers.
    hairline = (if (highContrast) ThemeTokens.HAIRLINE_HIGH_CONTRAST else ThemeTokens.HAIRLINE).dp,
)

private fun sizes(t: PresetTokens): PresetSizes {
    val z = t.sizes
    return PresetSizes(
        miniArtwork = z.miniArtwork.dp,
        miniPlayerHeight = z.miniPlayerHeight.dp,
        carouselArtwork = z.carouselArtwork.dp,
        quickTileArtwork = z.quickTileArtwork.dp,
        rowArtwork = z.rowArtwork.dp,
        artistArtwork = z.artistArtwork.dp,
        playButton = z.playButton.dp,
        playIcon = z.playIcon.dp,
        transportButton = z.transportButton.dp,
        transportIcon = z.transportIcon.dp,
        scrubberTrack = z.scrubberTrack.dp,
        scrubberTrackDragging = z.scrubberTrackDragging.dp,
        scrubberHandle = z.scrubberHandle.dp,
        handleOnlyWhileDragging = z.handleOnlyWhileDragging,
        artworkElevation = z.artworkElevation.dp,
        playerArtworkFraction = z.playerArtworkFraction,
        bannerAspect = z.bannerAspect,
        chevron = z.chevron.dp,
        icon = ThemeTokens.ICON.dp,
        iconSmall = ThemeTokens.ICON_SMALL.dp,
        iconLarge = ThemeTokens.ICON_LARGE.dp,
        pastLineAlpha = z.pastLineAlpha,
        otherLineAlpha = z.otherLineAlpha,
        playerScrimAlpha = z.playerScrimAlpha,
        disabledAlpha = ThemeTokens.DISABLED_ALPHA,
        miniPlayerAlpha = z.miniPlayerAlpha,
        liveBlurRadius = z.liveBlurRadius.dp,
        smallArtworkElevation = z.smallArtworkElevation.dp,
    )
}

/** Material typography for shared screens (Settings, lists) under a layout preset: Roboto, preset sizes. */
fun materialTypography(t: TypeScaleTokens): Typography {
    val display = t.display.toTextStyle()
    val headline = t.headline.toTextStyle()
    val title = t.title.toTextStyle()
    val body = t.body.toTextStyle()
    val emphasis = t.bodyEmphasis.toTextStyle()
    val subhead = t.subhead.toTextStyle()
    val caption = t.caption.toTextStyle()
    return Typography(
        displayLarge = display, displayMedium = display, displaySmall = display,
        headlineLarge = headline, headlineMedium = headline, headlineSmall = headline,
        titleLarge = title, titleMedium = emphasis, titleSmall = emphasis,
        bodyLarge = body, bodyMedium = body, bodySmall = subhead,
        labelLarge = emphasis, labelMedium = caption, labelSmall = caption,
    )
}

/** Material shapes for shared components; sheets take the preset's top radius. */
fun materialShapes(t: PresetTokens): Shapes = Shapes(
    extraSmall = RoundedCornerShape(t.shape.artworkRadius.dp),
    small = RoundedCornerShape(t.shape.artworkRadius.dp),
    medium = RoundedCornerShape(t.shape.cardRadius.dp),
    large = RoundedCornerShape(t.shape.sheetTopRadius.dp),
    extraLarge = RoundedCornerShape(t.shape.sheetTopRadius.dp),
)
