package com.svara.app.appearance

import com.svara.app.appearance.colour.Swatches
import com.svara.app.appearance.colour.Swatch
import com.svara.app.appearance.tokens.AnahataSpec
import com.svara.app.appearance.tokens.PresetTokens
import com.svara.app.appearance.tokens.RagaSpec
import com.svara.app.appearance.tokens.ShrutiSpec
import com.svara.app.appearance.tokens.SpandaSpec
import com.svara.app.appearance.tokens.TalaSpec

/** How heavy the visuals may be; independent of the theme. */
enum class PerformanceMode(val label: String) {
    EFFICIENCY("Efficiency"),
    BALANCED("Balanced"),
    IMMERSIVE("Immersive"),
}

enum class PresetId(
    val displayName: String,
    val personality: String,
    val description: String,
    val defaultTier: PerformanceMode,
) {
    ANAHATA("ETHEREAL", "Premium, balanced, timeless", "The unstruck sound", PerformanceMode.BALANCED),
    SHRUTI("Halo", "Dark, dense, artwork-led", "Listening as an art", PerformanceMode.BALANCED),
    TALA("SOLACE", "Bright, spacious, large titles", "Rhythm in motion", PerformanceMode.BALANCED),
    RAGA("OCTAVE", "Bold, banner-led, colourful", "Music with colour and feeling", PerformanceMode.BALANCED),
    SPANDA("ZENITH", "Deep black, swipeable, reactive", "Sound in motion", PerformanceMode.BALANCED),
    ;

    companion object {
        val DEFAULT = ANAHATA
        fun parse(value: String?): PresetId = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

enum class HomeLayout(val label: String) {
    ARTWORK_FIRST("Artwork first"),
    COMPACT_LIBRARY("Compact library"),
    PLAYLIST_CARDS("Playlist cards"),
    RECENT_FIRST("Recently played first"),
}

/**
 * Where the accent comes from. The Appearance page offers the four [selectable] sources; WARM and
 * COOL remain only so looks saved before v2.1 still resolve.
 */
enum class ColourSystem(val label: String, val selectable: Boolean = true) {
    /** The preset's own accent (the four layout presets' default). */
    PRESET("Preset colours"),
    ALBUM("Album artwork"),
    MONOCHROME("Monochrome"),
    WARM("Warm and earthy", selectable = false),
    COOL("Cool and minimal", selectable = false),
    CUSTOM("Custom accent"),
}

/** The surface family behind everything (dark themes; light themes ignore Pure black). */
enum class BackgroundTone(val label: String) {
    PURE_BLACK("Pure black (OLED)"),
    SOFT_DARK("Soft dark"),
    TINTED("Tinted"),
}

/** Defaults for the v2.1 colour, ambient and blur settings (same for every preset). */
object AppearanceDefaults {
    val BACKGROUND_TONE = BackgroundTone.SOFT_DARK
    const val AMBIENT_INTENSITY = 60
    /** Blur strength bucket, 1 (lightest) to [BLUR_BUCKETS]. */
    const val BLUR_STRENGTH = 3
    const val BLUR_BUCKETS = 5
    const val FROSTED = false
    const val BLUR_TINT = false
}

enum class AnimationStyle(val label: String) {
    SUBTLE("Subtle"),
    CINEMATIC("Cinematic"),
    PLAYFUL("Playful"),
    REDUCED("Reduced motion"),
}

enum class ArtworkSize(val label: String, val fraction: Float) {
    SMALL("Small", 0.6f),
    MEDIUM("Medium", 0.8f),
    LARGE("Large", 1f),
    IMMERSIVE("Immersive full-screen", 1f),
}

enum class VisualiserMode(val label: String, val needsAnalysis: Boolean) {
    OFF("Off", false),
    AMBIENT("Ambient", false),
    REACTIVE("Reactive", true),
    FULL_SPECTRUM("Full Spectrum", true),
}

/** Overall composition of each preset's screens. */
enum class LayoutSet { ARTWORK_CENTRED, EDITORIAL, DENSE, COLOUR_TILES, IMMERSIVE }

enum class PlayerLayout { CENTRED_FOCUS, EDITORIAL, CONTROL_FIRST, COLOUR_FIELD, BACKDROP_VISUALISER }

enum class NavStyle { STANDARD, SELECTED_LABEL_ONLY, COMPACT }

/** Text family per role. Only the bundled sans-serif exists; see ui/theme/AppFont.kt. */
enum class FontKind { SANS }

/** Type tokens: families and weights per role; sizes come from the shared scale. */
data class TypeTokens(
    val display: FontKind,
    val body: FontKind,
    /** 400–800. */
    val headlineWeight: Int,
)

/**
 * Everything a preset decides about appearance. Studio overrides apply on top of these defaults.
 * [tokens] holds the palette, type scale, shape, spacing, motion and navigation; [palette] is the
 * Material bridge derived from it, used by screens shared across presets.
 */
data class ThemeSpec(
    val id: PresetId,
    val tokens: PresetTokens,
    val layoutSet: LayoutSet,
    val typography: TypeTokens,
    val colourSystem: ColourSystem,
    val palette: PresetPalette,
    val animationStyle: AnimationStyle,
    val fadesOnly: Boolean,
    val homeLayout: HomeLayout,
    val artworkSize: ArtworkSize,
    val glass: Boolean,
    val glow: Boolean,
    val playerBlur: Boolean,
    val visualiser: VisualiserMode,
    val playerLayout: PlayerLayout,
    val navStyle: NavStyle,
    val defaultTier: PerformanceMode,
)

object Presets {
    fun tokens(id: PresetId): PresetTokens = when (id) {
        PresetId.ANAHATA -> AnahataSpec.tokens
        PresetId.SHRUTI -> ShrutiSpec.tokens
        PresetId.TALA -> TalaSpec.tokens
        PresetId.RAGA -> RagaSpec.tokens
        PresetId.SPANDA -> SpandaSpec.tokens
    }

    fun spec(id: PresetId): ThemeSpec = when (id) {
        PresetId.ANAHATA -> ThemeSpec(
            id, tokens(id), LayoutSet.ARTWORK_CENTRED, TypeTokens(FontKind.SANS, FontKind.SANS, 600),
            ColourSystem.ALBUM, Palettes.ANAHATA, AnimationStyle.SUBTLE, fadesOnly = false,
            homeLayout = HomeLayout.ARTWORK_FIRST, artworkSize = ArtworkSize.LARGE,
            glass = false, glow = false, playerBlur = false, visualiser = VisualiserMode.OFF,
            playerLayout = PlayerLayout.CENTRED_FOCUS, navStyle = NavStyle.STANDARD, defaultTier = id.defaultTier,
        )
        PresetId.SHRUTI -> ThemeSpec(
            id, tokens(id), LayoutSet.EDITORIAL, TypeTokens(FontKind.SANS, FontKind.SANS, 700),
            ColourSystem.PRESET, Palettes.SHRUTI, AnimationStyle.SUBTLE, fadesOnly = false,
            homeLayout = HomeLayout.ARTWORK_FIRST, artworkSize = ArtworkSize.LARGE,
            glass = false, glow = false, playerBlur = false, visualiser = VisualiserMode.OFF,
            playerLayout = PlayerLayout.EDITORIAL, navStyle = NavStyle.SELECTED_LABEL_ONLY, defaultTier = id.defaultTier,
        )
        PresetId.TALA -> ThemeSpec(
            id, tokens(id), LayoutSet.DENSE, TypeTokens(FontKind.SANS, FontKind.SANS, 700),
            ColourSystem.PRESET, Palettes.TALA, AnimationStyle.SUBTLE, fadesOnly = false,
            homeLayout = HomeLayout.ARTWORK_FIRST, artworkSize = ArtworkSize.LARGE,
            glass = false, glow = false, playerBlur = false, visualiser = VisualiserMode.OFF,
            playerLayout = PlayerLayout.CONTROL_FIRST, navStyle = NavStyle.COMPACT, defaultTier = id.defaultTier,
        )
        PresetId.RAGA -> ThemeSpec(
            id, tokens(id), LayoutSet.COLOUR_TILES, TypeTokens(FontKind.SANS, FontKind.SANS, 700),
            ColourSystem.PRESET, Palettes.RAGA, AnimationStyle.SUBTLE, fadesOnly = false,
            homeLayout = HomeLayout.ARTWORK_FIRST, artworkSize = ArtworkSize.LARGE,
            glass = false, glow = false, playerBlur = false, visualiser = VisualiserMode.OFF,
            playerLayout = PlayerLayout.COLOUR_FIELD, navStyle = NavStyle.STANDARD, defaultTier = id.defaultTier,
        )
        PresetId.SPANDA -> ThemeSpec(
            id, tokens(id), LayoutSet.IMMERSIVE, TypeTokens(FontKind.SANS, FontKind.SANS, 700),
            ColourSystem.PRESET, Palettes.SPANDA, AnimationStyle.SUBTLE, fadesOnly = false,
            homeLayout = HomeLayout.ARTWORK_FIRST, artworkSize = ArtworkSize.LARGE,
            // The audio-reactive layer is off by default; Theme Studio turns it on.
            glass = false, glow = false, playerBlur = false, visualiser = VisualiserMode.OFF,
            playerLayout = PlayerLayout.BACKDROP_VISUALISER, navStyle = NavStyle.SELECTED_LABEL_ONLY, defaultTier = id.defaultTier,
        )
    }
}

/** Theme Studio sections; "Reset this section" removes every override in one. */
enum class ThemeSection(val label: String) {
    LAYOUT("Layout"), COLOUR("Colour"), MOTION("Motion"), ARTWORK("Artwork"), VISUALISER("Visualiser"),
    AMBIENT("Ambient"), BLUR("Cinematic blur"),
}

/** Every Theme Studio control writes exactly one of these overrides. */
enum class ThemeField(val section: ThemeSection) {
    HOME_LAYOUT(ThemeSection.LAYOUT),
    COLOUR_SYSTEM(ThemeSection.COLOUR),
    CUSTOM_ACCENT(ThemeSection.COLOUR),
    ANIMATION_STYLE(ThemeSection.MOTION),
    ARTWORK_SIZE(ThemeSection.ARTWORK),
    GLASS(ThemeSection.ARTWORK),
    GLOW(ThemeSection.ARTWORK),
    VISUALISER(ThemeSection.VISUALISER),
    /** A swatch id from [com.svara.app.appearance.colour.Swatches]. */
    CUSTOM_SWATCH(ThemeSection.COLOUR),
    /** A hue in degrees from the hue slider; wins over [CUSTOM_SWATCH] ("Custom hue"). */
    CUSTOM_HUE(ThemeSection.COLOUR),
    BACKGROUND_TONE(ThemeSection.COLOUR),
    AMBIENT_INTENSITY(ThemeSection.AMBIENT),
    BLUR_STRENGTH(ThemeSection.BLUR),
    FROSTED(ThemeSection.BLUR),
    BLUR_TINT(ThemeSection.BLUR),
}

/** What is stored: the preset plus only the fields the user changed. */
data class ThemeConfig(
    val preset: PresetId = PresetId.DEFAULT,
    val overrides: Map<ThemeField, String> = emptyMap(),
) {
    fun with(field: ThemeField, value: String) = copy(overrides = overrides + (field to value))
    fun without(field: ThemeField) = copy(overrides = overrides - field)
    fun resetSection(section: ThemeSection) = copy(overrides = overrides.filterKeys { it.section != section })
    fun resetAll() = copy(overrides = emptyMap())
}

/** The theme actually used: preset defaults with valid overrides applied. */
data class ResolvedTheme(
    val spec: ThemeSpec,
    val homeLayout: HomeLayout,
    val colourSystem: ColourSystem,
    /** Index into [Palettes.ACCENTS]; only used with [ColourSystem.CUSTOM]. */
    val customAccent: Int,
    val animationStyle: AnimationStyle,
    val artworkSize: ArtworkSize,
    val glass: Boolean,
    val glow: Boolean,
    val visualiser: VisualiserMode,
    val customSwatch: Swatch? = null,
    val customHue: Float? = null,
    val backgroundTone: BackgroundTone = AppearanceDefaults.BACKGROUND_TONE,
    /** 0..100; 0 turns ambient tints off. */
    val ambientIntensity: Int = AppearanceDefaults.AMBIENT_INTENSITY,
    /** 1..[AppearanceDefaults.BLUR_BUCKETS]. */
    val blurStrength: Int = AppearanceDefaults.BLUR_STRENGTH,
    val frosted: Boolean = AppearanceDefaults.FROSTED,
    val blurTint: Boolean = AppearanceDefaults.BLUR_TINT,
) {
    val preset: PresetId get() = spec.id
}

object ThemeResolver {
    /** Unknown or corrupt override values are ignored, falling back to the preset default. */
    fun resolve(config: ThemeConfig): ResolvedTheme {
        val spec = Presets.spec(config.preset)
        val o = config.overrides
        val visualiser = enumOr(o[ThemeField.VISUALISER], spec.visualiser)
        return ResolvedTheme(
            spec = spec,
            homeLayout = enumOr(o[ThemeField.HOME_LAYOUT], spec.homeLayout),
            colourSystem = enumOr(o[ThemeField.COLOUR_SYSTEM], spec.colourSystem),
            customAccent = o[ThemeField.CUSTOM_ACCENT]?.toIntOrNull()?.takeIf { it in Palettes.ACCENTS.indices } ?: 0,
            animationStyle = enumOr(o[ThemeField.ANIMATION_STYLE], spec.animationStyle),
            artworkSize = enumOr(o[ThemeField.ARTWORK_SIZE], spec.artworkSize),
            glass = o[ThemeField.GLASS]?.toBooleanStrictOrNull() ?: spec.glass,
            glow = o[ThemeField.GLOW]?.toBooleanStrictOrNull() ?: spec.glow,
            // The visualiser is a ZENITH feature; other presets always resolve to Off.
            visualiser = if (spec.id == PresetId.SPANDA) visualiser else VisualiserMode.OFF,
            customSwatch = Swatches.byId(o[ThemeField.CUSTOM_SWATCH]),
            customHue = o[ThemeField.CUSTOM_HUE]?.toFloatOrNull()?.takeIf { it in 0f..HUE_MAX },
            backgroundTone = enumOr(o[ThemeField.BACKGROUND_TONE], AppearanceDefaults.BACKGROUND_TONE),
            ambientIntensity = o[ThemeField.AMBIENT_INTENSITY]?.toIntOrNull()?.coerceIn(0, PERCENT) ?: AppearanceDefaults.AMBIENT_INTENSITY,
            blurStrength = o[ThemeField.BLUR_STRENGTH]?.toIntOrNull()?.coerceIn(1, AppearanceDefaults.BLUR_BUCKETS) ?: AppearanceDefaults.BLUR_STRENGTH,
            frosted = o[ThemeField.FROSTED]?.toBooleanStrictOrNull() ?: AppearanceDefaults.FROSTED,
            blurTint = o[ThemeField.BLUR_TINT]?.toBooleanStrictOrNull() ?: AppearanceDefaults.BLUR_TINT,
        )
    }

    private const val HUE_MAX = 360f
    private const val PERCENT = 100

    /** The custom accent's name for summaries: "Custom hue", a swatch name, or a legacy accent. */
    fun customName(t: ResolvedTheme): String = when {
        t.customHue != null -> "Custom hue"
        t.customSwatch != null -> t.customSwatch.name
        else -> Palettes.ACCENTS.getOrNull(t.customAccent)?.name ?: "Custom"
    }

    /** Plain-language summary shown at the bottom of Theme Studio. */
    fun summary(t: ResolvedTheme): String = buildString {
        append("Preset: ${t.preset.displayName}. ")
        append("Layout: ${t.homeLayout.label}. ")
        append("Palette: ${if (t.colourSystem == ColourSystem.CUSTOM) "Custom accent (${customName(t)})" else t.colourSystem.label}. ")
        append("Motion: ${t.animationStyle.label.removeSuffix(" motion").replaceFirstChar { it.uppercase() }}. ")
        append("Artwork: ${t.artworkSize.label}. ")
        append("Glass: ${if (t.glass) "On" else "Off"}. ")
        append("Glow: ${if (t.glow) "On" else "Off"}.")
        if (t.preset == PresetId.SPANDA) append(" Visualiser: ${t.visualiser.label}.")
    }

    inline fun <reified T : Enum<T>> enumOr(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}

/** Stored as "FIELD=value" pairs separated by ';'. Corrupt entries are skipped. */
object ThemeConfigCodec {
    fun encodeOverrides(overrides: Map<ThemeField, String>): String =
        overrides.entries.sortedBy { it.key.ordinal }.joinToString(";") { "${it.key.name}=${it.value}" }

    fun decodeOverrides(raw: String?): Map<ThemeField, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(';').mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) return@mapNotNull null
            val field = ThemeField.entries.firstOrNull { it.name == pair.substring(0, i) } ?: return@mapNotNull null
            field to pair.substring(i + 1)
        }.toMap()
    }
}
