package com.svara.app.appearance.tokens

import com.svara.app.appearance.Argb
import com.svara.app.appearance.NavTab

/*
 * The token model every preset fills in. Plain Kotlin (no Compose), so contrast and resolution
 * are unit tested on the JVM; ui/theme/PresetTheme.kt turns these into Dp, TextStyle and Color.
 * Preset composables read only these tokens: no raw colours, sizes or durations of their own.
 * Where each value comes from is recorded in docs/design-source-map.md.
 */

/** Colour roles. [accentText] and [accentFill] are the AA-safe forms of [accent] (see the source map). */
data class PaletteTokens(
    val background: Argb,
    val surface: Argb,
    val surfaceHighlight: Argb,
    val primaryText: Argb,
    val secondaryText: Argb,
    /** Icons, progress, active tints and fills behind icons (3:1 against background). */
    val accent: Argb,
    val onAccent: Argb,
    val scrim: Argb,
    val divider: Argb,
    /** The accent drawn as text on background or surface (4.5:1). */
    val accentText: Argb = accent,
    /** Fill behind text labels on accent buttons, so [onAccent] text passes 4.5:1. */
    val accentFill: Argb = accent,
)

/** Dark is every preset's default; light is used only when the user follows the system. */
data class PalettePair(
    val dark: PaletteTokens,
    val light: PaletteTokens,
    /** Fallback block colours when no album colour is cached yet. */
    val tiles: List<Argb>,
) {
    fun pick(dark: Boolean): PaletteTokens = if (dark) this.dark else light
}

/** One step of the type scale. Sizes in sp; weight 100–900; letter spacing in em. */
data class TypeRole(val sizeSp: Float, val weight: Int, val lineHeightSp: Float, val letterSpacingEm: Float = 0f)

data class TypeScaleTokens(
    val display: TypeRole,
    val headline: TypeRole,
    val title: TypeRole,
    val body: TypeRole,
    val bodyEmphasis: TypeRole,
    val subhead: TypeRole,
    val caption: TypeRole,
    val micro: TypeRole,
) {
    val all: List<TypeRole> get() = listOf(display, headline, title, body, bodyEmphasis, subhead, caption, micro)
}

/** Corner radii in dp. Pills use [ThemeTokens.PILL_PERCENT]. */
data class ShapeTokens(
    val artworkRadius: Float,
    val cardRadius: Float,
    val pillRadiusPercent: Int = ThemeTokens.PILL_PERCENT,
    val sheetTopRadius: Float,
    val miniPlayerRadius: Float,
    /** Featured hero artwork (SOLACE) and category tiles (OCTAVE). */
    val heroRadius: Float = cardRadius,
    val lyricsCardRadius: Float = cardRadius,
    val searchFieldRadius: Float = cardRadius,
)

/** Layout spacing in dp. [rowHeight] is never below [ThemeTokens.MIN_TOUCH]. */
data class SpacingTokens(
    val screenMargin: Float,
    val rowHeight: Float,
    val gridGutter: Float,
    val sectionGap: Float,
)

/**
 * Motion values before the effects policy applies. The policy (and reduced motion) may shorten or
 * remove any of these; it never lengthens them.
 */
data class MotionTokens(
    val pressScale: Float,
    val pressDurationMs: Int,
    val transitionMs: Int,
    val springStiffness: Float,
    val springDamping: Float,
    val enableArtworkMotion: Boolean,
    val enableBlur: Boolean,
    val enableAmbientBackground: Boolean,
    val playPressScale: Float = ThemeTokens.PLAY_PRESS_SCALE,
    val playPressMs: Int = ThemeTokens.PLAY_PRESS_MS,
    val tabCrossfadeMs: Int = 0,
    val pagerMs: Int = transitionMs,
    val heartPopScale: Float = 1f,
    val heartPopMs: Int = 0,
    val lyricsScrollMs: Int = transitionMs,
    val pausedArtworkScale: Float = 1f,
    val swipeSnapMs: Int = transitionMs,
    /** Fraction of the artwork width a drag must pass to change track. */
    val swipeThreshold: Float = ThemeTokens.SWIPE_THRESHOLD,
    val breathingMinAlpha: Float = 0f,
    val breathingMaxAlpha: Float = 0f,
    val breathingPeriodMs: Int = 0,
    val breathingMaxFps: Int = 0,
)

enum class TabStyle(val label: String) {
    OUTLINED_TO_FILLED("Outlined to filled"),
    TINTED_PILL("Tinted pill"),
    UNDERLINE("Underline"),
}

enum class TabTint { PRIMARY_TEXT, ACCENT }

data class NavigationTokens(
    /** Bottom-bar tabs in order. Empty means "use the user's Navigation Studio order" (ETHEREAL). */
    val visibleTabs: List<NavTab>,
    val tabStyle: TabStyle,
    val activeTint: TabTint,
    /** Labels that differ from [NavTab.label], e.g. "Your Library". */
    val labels: Map<NavTab, String> = emptyMap(),
) {
    fun label(tab: NavTab): String = labels[tab] ?: tab.label
}

/** Component sizes in dp, and the few alphas the layouts specify. */
data class SizeTokens(
    val miniArtwork: Float,
    val miniPlayerHeight: Float,
    val carouselArtwork: Float,
    val quickTileArtwork: Float = miniArtwork,
    val rowArtwork: Float = ThemeTokens.MIN_TOUCH,
    val artistArtwork: Float = carouselArtwork,
    val playButton: Float,
    val playIcon: Float,
    val transportButton: Float = ThemeTokens.MIN_TOUCH + ThemeTokens.SPACE_S,
    val transportIcon: Float,
    val scrubberTrack: Float,
    val scrubberTrackDragging: Float = scrubberTrack,
    val scrubberHandle: Float,
    val handleOnlyWhileDragging: Boolean,
    val artworkElevation: Float = 0f,
    /** Now Playing artwork as a fraction of the screen height (portrait). */
    val playerArtworkFraction: Float,
    val bannerAspect: Float = ThemeTokens.WIDE_ASPECT,
    val chevron: Float = ThemeTokens.ICON_SMALL,
    /** Lyrics lines that are neither current nor upcoming. */
    val pastLineAlpha: Float = ThemeTokens.PAST_LINE_ALPHA,
    /** Lyrics lines other than the current one, where a preset doesn't separate past and upcoming. */
    val otherLineAlpha: Float = ThemeTokens.PAST_LINE_ALPHA,
    /** Black overlay over the cached blurred artwork on Now Playing. */
    val playerScrimAlpha: Float = 0f,
    val lyricsActiveSp: Float = 0f,
    /** Mini-player surface opacity (SOLACE's is translucent). */
    val miniPlayerAlpha: Float = 1f,
    /** Live blur radius for SOLACE's mini-player (Immersive, API 31+), dp. */
    val liveBlurRadius: Float = 0f,
    /** Shadow under small artwork (mini-player), dp. */
    val smallArtworkElevation: Float = 0f,
)

data class PresetTokens(
    val palette: PalettePair,
    val typeScale: TypeScaleTokens,
    val shape: ShapeTokens,
    val spacing: SpacingTokens,
    val motion: MotionTokens,
    val navigation: NavigationTokens,
    val sizes: SizeTokens,
    val signature: SignatureTokens = SignatureTokens(),
)

/** How the Cinema Now Playing style fills the screen behind the artwork. All static. */
enum class CinemaBackdrop { BLURRED_COVER, COLOUR_FIELD, GRADIENT }

/**
 * Each preset's V3 signature touches, applied inside EffectsPolicy (aura and backdrop colour need
 * the cached palette; the lift is a static shadow; none of them animate).
 * [auraAlpha] 0 turns the artwork aura off; [auraSpreadDp] is how far it reaches past the artwork;
 * [cardLiftDp] raises artwork cards (0 = flat); [cinemaBackdrop] picks the Cinema background.
 */
data class SignatureTokens(
    val auraAlpha: Float = 0.24f,
    val auraSpreadDp: Float = 40f,
    val cardLiftDp: Float = 0f,
    val cinemaBackdrop: CinemaBackdrop = CinemaBackdrop.BLURRED_COVER,
)

/** Shared scale and spacing constants. Preset specs build on these. */
object ThemeTokens {
    // 4 dp spacing scale.
    const val SPACE_XXS = 2f
    const val SPACE_XS = 4f
    const val SPACE_S = 8f
    const val SPACE_M = 12f
    const val SPACE_L = 16f
    const val SPACE_XL = 24f
    const val SPACE_XXL = 32f

    /** Minimum touch target, dp. */
    const val MIN_TOUCH = 48f
    const val HAIRLINE = 1f
    /** Outlines and dividers under High contrast. */
    const val HAIRLINE_HIGH_CONTRAST = 2f
    const val ICON = 24f
    const val ICON_SMALL = 16f
    const val ICON_LARGE = 32f
    const val PILL_PERCENT = 50

    const val WIDE_ASPECT = 16f / 9f
    const val PAST_LINE_ALPHA = 0.5f
    /** Secondary text over dark artwork backdrops (white at this alpha stays 4.5:1 on the scrim). */
    const val ON_SHADE_SECONDARY_ALPHA = 0.85f
    /** Inactive scrubber track over artwork backdrops. */
    const val ON_SHADE_TRACK_ALPHA = 0.3f
    const val DISABLED_ALPHA = 0.38f

    /** Tap feedback when the effects policy allows only feedback (Efficiency), ms. */
    const val FEEDBACK_MS = 100
    const val PRESS_SCALE = 0.95f
    const val PLAY_PRESS_SCALE = 0.92f
    const val PLAY_PRESS_MS = 80
    const val SWIPE_THRESHOLD = 0.35f
    /** v2.1 micro-animations (Balanced and Immersive; none in Efficiency or with reduced motion). */
    const val AMBIENT_CROSSFADE_MS = 600
    const val ICON_MORPH_MS = 150
    const val CHECKMARK_MS = 200
    const val SECTION_FADE_MS = 200
    /** Heart pop for every preset that has a like button. */
    const val HEART_POP_SCALE = 1.25f
    const val HEART_POP_MS = 200
    /** The scrubber handle grows by this much while dragged. */
    const val HANDLE_DRAG_SCALE = 1.33f
    /** Seek bar: the Thick style's track, the Dots style's dot count, and the gap under the time bubble. */
    const val SEEK_THICK_DP = 12f
    const val SEEK_DOTS = 32
    const val SEEK_BUBBLE_GAP_DP = 4f
    /** Ambient tint strength at intensity 100 (scaled down further if text contrast needs it). */
    const val AMBIENT_MAX_ALPHA = 0.35f
    /** Blur tint toggle: the ambient colour's share in the backdrop scrim. */
    const val BLUR_TINT_ALPHA = 0.3f
    /** Frosted surfaces: how far surfaces move toward the text colour. */
    const val FROST_AMOUNT = 0.06

    /** Marquee: pause before each pass, passes per title, and scroll speed (as basicMarquee). */
    const val MARQUEE_DELAY_MS = 1_200L
    const val MARQUEE_ITERATIONS = 3
    const val MARQUEE_DP_PER_SECOND = 30f
    /** Compose's Spring.StiffnessMedium, kept here so specs don't depend on Compose. */
    const val STIFFNESS_MEDIUM = 1500f
    const val DAMPING_NO_BOUNCE = 1f

    /** Haptic tick spacing while scrubbing (SOLACE), ms of audio. */
    const val SCRUB_TICK_MS = 5_000L

    /** Line heights relative to size: headings, body, small print. */
    const val LEADING_TIGHT = 1.2f
    const val LEADING_BODY = 1.4f
    const val LEADING_SMALL = 1.3f

    fun heading(size: Float, weight: Int) = TypeRole(size, weight, size * LEADING_TIGHT)
    fun body(size: Float, weight: Int) = TypeRole(size, weight, size * LEADING_BODY)
    fun small(size: Float, weight: Int, letterSpacingEm: Float = 0f) = TypeRole(size, weight, size * LEADING_SMALL, letterSpacingEm)

    const val REGULAR = 400
    const val MEDIUM = 500
    const val SEMIBOLD = 600
    const val BOLD = 700

    /** Packs 0xAARRGGBB, for the specs. */
    fun argb(value: Long) = Argb(value)
}
