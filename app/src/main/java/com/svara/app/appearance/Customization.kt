package com.svara.app.appearance

/**
 * Bottom-navigation destinations. ETHEREAL's bar is built from the [anahata] tabs in the user's
 * order; the layout presets declare their own ordered tabs (which include [SEARCH] and [LIBRARY]).
 */
enum class NavTab(val label: String, val anahata: Boolean = true) {
    HOME("Home"), SONGS("Songs"), ALBUMS("Albums"), ARTISTS("Artists"), FOLDERS("Folders"), PLAYLISTS("Playlists"), SETTINGS("Settings"),
    SEARCH("Search", anahata = false), LIBRARY("Library", anahata = false);

    companion object {
        val ANAHATA: List<NavTab> = entries.filter { it.anahata }
    }
}

/**
 * Which tabs a preset shows. A preset with its own tab list keeps that order; Navigation Studio
 * can hide tabs within it, but at least one always stays visible.
 */
object TabVisibility {
    fun visible(presetTabs: List<NavTab>, custom: Customization): List<NavTab> {
        if (presetTabs.isEmpty()) return custom.visibleTabs
        return presetTabs.filter { it !in custom.navHidden }.ifEmpty { listOf(presetTabs.first()) }
    }

    /** The tab to show after a preset change: the last one if it still exists, otherwise the first. */
    fun keep(last: NavTab?, tabs: List<NavTab>): NavTab = last?.takeIf { it in tabs } ?: tabs.first()

    /** null when hiding is allowed, otherwise the reason. */
    fun canHide(presetTabs: List<NavTab>, custom: Customization, tab: NavTab): String? =
        if (presetTabs.isEmpty()) CustomizationCodec.canHide(custom, tab)
        else if (visible(presetTabs, custom) == listOf(tab)) "At least one tab must stay visible." else null
}

/** Transport controls that can move; play/pause is fixed in the centre. */
enum class ControlId(val label: String) { SHUFFLE("Shuffle"), PREVIOUS("Previous"), NEXT("Next"), REPEAT("Repeat") }

enum class ProgressStyle(val label: String) { LINE("Line"), THICK("Thick"), DOTS("Dots") }
enum class LyricsPosition(val label: String) { BELOW_ARTWORK("Below artwork"), FULL_SCREEN("Full screen") }
enum class QueueStyle(val label: String) { LIST("List"), SHEET("Bottom sheet") }
enum class DoubleTapAction(val label: String) { PLAY_PAUSE("Play or pause"), FAVOURITE("Favourite"), LYRICS("Show lyrics") }
enum class MetadataDensity(val label: String) { COMPACT("Compact"), COMFORTABLE("Comfortable") }
enum class ArtworkCorner(val label: String) { SQUARE("Square"), ROUNDED("Rounded"), CIRCLE("Circle") }
/**
 * How Now Playing is composed. One engine and one state; the style only arranges the same parts.
 * Classic keeps each preset's own approved player.
 */
enum class NowPlayingStyle(val label: String, val description: String) {
    CLASSIC("Classic", "Each theme's own balanced player"),
    CINEMA("Cinema", "Large artwork over a softened cover, fewer controls"),
    COMPACT("Compact", "Small artwork, every control and what's up next"),
}

/** ZENITH visualiser gallery. Pulse styles run at most 20 fps, the others 30, within EffectsPolicy. */
enum class VisualStyle(val label: String, val pulse: Boolean) {
    WAVEFORM("Waveform", false),
    SPECTRUM_BARS("Spectrum bars", false),
    CIRCULAR("Circular spectrum", false),
    PULSE("Minimal ambient pulse", true),
    ALBUM_AURA("Album aura", true),
    IMMERSIVE("Full-screen immersive", false),
}

enum class VisualColour(val label: String) { ACCENT("Theme accent"), ARTWORK("Album artwork"), MONOCHROME("Monochrome") }

enum class WaveStrength(val label: String) { SUBTLE("Subtle"), NORMAL("Normal"), LIVELY("Lively") }

enum class Brightness(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("Follow system") }

enum class StudioSection(val label: String) {
    NAVIGATION("Navigation"), NOW_PLAYING("Now Playing"), GESTURES("Gestures"), TYPOGRAPHY("Typography"),
    ARTWORK("Artwork"), MOTION("Motion"), CONTROL("Controls"), PERFORMANCE("Performance"), DISPLAY("Display"), VISUALISER("Visualiser"),
}

/** One stored override per field; absent means "default". */
enum class CustomField(val section: StudioSection) {
    NAV_ORDER(StudioSection.NAVIGATION),
    NAV_HIDDEN(StudioSection.NAVIGATION),
    CONTROL_ORDER(StudioSection.NOW_PLAYING),
    PROGRESS_STYLE(StudioSection.NOW_PLAYING),
    LYRICS_POSITION(StudioSection.NOW_PLAYING),
    QUEUE_STYLE(StudioSection.NOW_PLAYING),
    NOW_PLAYING_STYLE(StudioSection.NOW_PLAYING),
    ARTWORK_AURA(StudioSection.NOW_PLAYING),
    SWIPE_SKIP(StudioSection.GESTURES),
    DOUBLE_TAP(StudioSection.GESTURES),
    TEXT_SCALE(StudioSection.TYPOGRAPHY),
    DENSITY(StudioSection.TYPOGRAPHY),
    CORNER(StudioSection.ARTWORK),
    PLAYER_BLUR(StudioSection.ARTWORK),
    TRANSITION_SPEED(StudioSection.MOTION),
    INTENSITY(StudioSection.MOTION),
    PAGE_TRANSITIONS(StudioSection.MOTION),
    SCROLL_EFFECTS(StudioSection.MOTION),
    SONG_CHANGE_ANIMATION(StudioSection.MOTION),
    SMART_SMOOTHING(StudioSection.MOTION),
    RECAP_ANIMATIONS(StudioSection.MOTION),
    LIVE_WAVE(StudioSection.MOTION),
    WAVE_STRENGTH(StudioSection.MOTION),
    EXTRAS(StudioSection.MOTION),
    EXTRAS_OFF(StudioSection.MOTION),
    SEEK_INCREMENT(StudioSection.CONTROL),
    HAPTICS(StudioSection.CONTROL),
    PERFORMANCE_MODE(StudioSection.PERFORMANCE),
    SMOOTH_DISPLAY(StudioSection.PERFORMANCE),
    BRIGHTNESS(StudioSection.DISPLAY),
    VIS_STYLE(StudioSection.VISUALISER),
    VIS_INTENSITY(StudioSection.VISUALISER),
    VIS_BARS(StudioSection.VISUALISER),
    VIS_GLOW(StudioSection.VISUALISER),
    VIS_BACKGROUND(StudioSection.VISUALISER),
    VIS_COLOUR(StudioSection.VISUALISER),
    VIS_ARTWORK(StudioSection.VISUALISER),
    VIS_SENSITIVITY(StudioSection.VISUALISER),
}

/** Visualiser presentation, one override per field like every other customization. */
data class VisualSettings(
    val style: VisualStyle = VisualStyle.SPECTRUM_BARS,
    /** 0..100: how far the visual moves. */
    val intensity: Int = 70,
    val bars: Int = 24,
    val glow: Boolean = true,
    /** 0..100: how strongly the background is dimmed behind the visual. */
    val backgroundOpacity: Int = 60,
    val colour: VisualColour = VisualColour.ACCENT,
    val showArtwork: Boolean = true,
    /** 0..100: gain applied to the analysed bands before drawing. */
    val sensitivity: Int = 50,
) {
    companion object {
        val BAR_RANGE = 8..48
    }
}

data class Customization(
    val navOrder: List<NavTab> = DEFAULT_NAV_ORDER,
    val navHidden: Set<NavTab> = DEFAULT_NAV_HIDDEN,
    /** Exactly four controls: the first two sit left of play/pause, the last two right. */
    val controlOrder: List<ControlId> = DEFAULT_CONTROL_ORDER,
    val progressStyle: ProgressStyle = ProgressStyle.LINE,
    val lyricsPosition: LyricsPosition = LyricsPosition.BELOW_ARTWORK,
    val queueStyle: QueueStyle = QueueStyle.LIST,
    val nowPlayingStyle: NowPlayingStyle = NowPlayingStyle.CLASSIC,
    /** A soft glow in the cover's cached colour behind Now Playing artwork (never a live blur). */
    val artworkAura: Boolean = true,
    val swipeToSkip: Boolean = true,
    val doubleTap: DoubleTapAction = DoubleTapAction.PLAY_PAUSE,
    /** Percent, 85..130, multiplied with the system font scale. */
    val textScale: Int = 100,
    val density: MetadataDensity = MetadataDensity.COMFORTABLE,
    val corner: ArtworkCorner = ArtworkCorner.ROUNDED,
    /** null = the preset's default. */
    val playerBlur: Boolean? = null,
    /** Higher is faster; 0.5..2.0. */
    val transitionSpeed: Float = 1f,
    val intensity: Int = 100,
    val pageTransitions: Boolean = true,
    val scrollEffects: Boolean = true,
    val songChangeAnimation: Boolean = true,
    /** Lets the motion governor step animations down when frames are dropped. */
    val smartSmoothing: Boolean = true,
    val recapAnimations: Boolean = true,
    val liveWave: Boolean = true,
    val waveStrength: WaveStrength = WaveStrength.NORMAL,
    /** Master switch for the animation extras. */
    val animationExtras: Boolean = true,
    /** Extras the user turned off one by one (Vinyl starts off). */
    val extrasOff: Set<AnimationExtra> = ExtrasPolicy.DEFAULT_OFF,
    val seekIncrementSec: Int = 10,
    val haptics: Boolean = true,
    /** Asks for the display's highest refresh rate at the current resolution; off lets the system choose. */
    val smoothDisplay: Boolean = true,
    /** null = the preset's default tier. */
    val performanceMode: PerformanceMode? = null,
    val brightness: Brightness = Brightness.DARK,
    val visual: VisualSettings = VisualSettings(),
) {
    val visibleTabs: List<NavTab> get() = navOrder.filter { it !in navHidden }

    companion object {
        val DEFAULT_NAV_ORDER = listOf(NavTab.HOME, NavTab.SONGS, NavTab.ALBUMS, NavTab.ARTISTS, NavTab.FOLDERS, NavTab.PLAYLISTS, NavTab.SETTINGS)
        val DEFAULT_NAV_HIDDEN = setOf(NavTab.ARTISTS, NavTab.FOLDERS)
        val DEFAULT_CONTROL_ORDER = listOf(ControlId.SHUFFLE, ControlId.PREVIOUS, ControlId.NEXT, ControlId.REPEAT)

        /** The bottom bar holds at most this many destinations; more are reachable from Settings. */
        const val MAX_VISIBLE_TABS = 5
        val TEXT_SCALE_RANGE = 85..130
        val SEEK_INCREMENTS = listOf(5, 10, 15, 30)
        val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.5f, 2f)
    }
}

/** Decodes stored overrides; anything unknown or corrupt falls back to the default. */
object CustomizationCodec {

    fun decode(o: Map<CustomField, String>): Customization {
        val d = Customization()
        return Customization(
            navOrder = decodeOrder(o[CustomField.NAV_ORDER]),
            navHidden = decodeHidden(o[CustomField.NAV_HIDDEN]),
            controlOrder = decodeControls(o[CustomField.CONTROL_ORDER]),
            progressStyle = ThemeResolver.enumOr(o[CustomField.PROGRESS_STYLE], d.progressStyle),
            lyricsPosition = ThemeResolver.enumOr(o[CustomField.LYRICS_POSITION], d.lyricsPosition),
            queueStyle = ThemeResolver.enumOr(o[CustomField.QUEUE_STYLE], d.queueStyle),
            nowPlayingStyle = ThemeResolver.enumOr(o[CustomField.NOW_PLAYING_STYLE], d.nowPlayingStyle),
            artworkAura = o[CustomField.ARTWORK_AURA]?.toBooleanStrictOrNull() ?: d.artworkAura,
            swipeToSkip = o[CustomField.SWIPE_SKIP]?.toBooleanStrictOrNull() ?: d.swipeToSkip,
            doubleTap = ThemeResolver.enumOr(o[CustomField.DOUBLE_TAP], d.doubleTap),
            textScale = textScale(o[CustomField.TEXT_SCALE]),
            density = ThemeResolver.enumOr(o[CustomField.DENSITY], d.density),
            corner = ThemeResolver.enumOr(o[CustomField.CORNER], d.corner),
            playerBlur = o[CustomField.PLAYER_BLUR]?.toBooleanStrictOrNull(),
            transitionSpeed = o[CustomField.TRANSITION_SPEED]?.toFloatOrNull()?.takeIf { it in 0.5f..2f } ?: d.transitionSpeed,
            intensity = o[CustomField.INTENSITY]?.toIntOrNull()?.coerceIn(0, 100) ?: d.intensity,
            pageTransitions = o[CustomField.PAGE_TRANSITIONS]?.toBooleanStrictOrNull() ?: d.pageTransitions,
            scrollEffects = o[CustomField.SCROLL_EFFECTS]?.toBooleanStrictOrNull() ?: d.scrollEffects,
            songChangeAnimation = o[CustomField.SONG_CHANGE_ANIMATION]?.toBooleanStrictOrNull() ?: d.songChangeAnimation,
            smartSmoothing = o[CustomField.SMART_SMOOTHING]?.toBooleanStrictOrNull() ?: d.smartSmoothing,
            recapAnimations = o[CustomField.RECAP_ANIMATIONS]?.toBooleanStrictOrNull() ?: d.recapAnimations,
            liveWave = o[CustomField.LIVE_WAVE]?.toBooleanStrictOrNull() ?: d.liveWave,
            waveStrength = ThemeResolver.enumOr(o[CustomField.WAVE_STRENGTH], d.waveStrength),
            animationExtras = o[CustomField.EXTRAS]?.toBooleanStrictOrNull() ?: d.animationExtras,
            extrasOff = o[CustomField.EXTRAS_OFF]?.let { raw -> raw.split(',').mapNotNull { n -> AnimationExtra.entries.firstOrNull { it.name == n } }.toSet() } ?: d.extrasOff,
            seekIncrementSec = seekIncrement(o[CustomField.SEEK_INCREMENT]),
            haptics = o[CustomField.HAPTICS]?.toBooleanStrictOrNull() ?: d.haptics,
            performanceMode = PerformanceMode.entries.firstOrNull { it.name == o[CustomField.PERFORMANCE_MODE] },
            smoothDisplay = o[CustomField.SMOOTH_DISPLAY]?.toBooleanStrictOrNull() ?: d.smoothDisplay,
            brightness = ThemeResolver.enumOr(o[CustomField.BRIGHTNESS], d.brightness),
            visual = VisualSettings(
                style = ThemeResolver.enumOr(o[CustomField.VIS_STYLE], d.visual.style),
                intensity = o[CustomField.VIS_INTENSITY]?.toIntOrNull()?.coerceIn(0, 100) ?: d.visual.intensity,
                bars = o[CustomField.VIS_BARS]?.toIntOrNull()?.coerceIn(VisualSettings.BAR_RANGE) ?: d.visual.bars,
                glow = o[CustomField.VIS_GLOW]?.toBooleanStrictOrNull() ?: d.visual.glow,
                backgroundOpacity = o[CustomField.VIS_BACKGROUND]?.toIntOrNull()?.coerceIn(0, 100) ?: d.visual.backgroundOpacity,
                colour = ThemeResolver.enumOr(o[CustomField.VIS_COLOUR], d.visual.colour),
                showArtwork = o[CustomField.VIS_ARTWORK]?.toBooleanStrictOrNull() ?: d.visual.showArtwork,
                sensitivity = o[CustomField.VIS_SENSITIVITY]?.toIntOrNull()?.coerceIn(0, 100) ?: d.visual.sensitivity,
            ),
        )
    }

    /** Values outside 85–130 % are clamped; garbage means 100 %. */
    fun textScale(raw: String?): Int = raw?.toIntOrNull()?.coerceIn(Customization.TEXT_SCALE_RANGE) ?: 100

    /** Only 5, 10 or 15 seconds; anything else means 10. */
    fun seekIncrement(raw: String?): Int = raw?.toIntOrNull()?.takeIf { it in Customization.SEEK_INCREMENTS } ?: 10

    /** Keeps the saved order, drops unknown names and duplicates, and appends anything missing. */
    fun decodeOrder(raw: String?): List<NavTab> {
        if (raw.isNullOrBlank()) return Customization.DEFAULT_NAV_ORDER
        val saved = raw.split(',').mapNotNull { n -> NavTab.ANAHATA.firstOrNull { it.name == n } }.distinct()
        return saved + NavTab.ANAHATA.filter { it !in saved }
    }

    /** At least one tab stays visible and no more than [Customization.MAX_VISIBLE_TABS]. */
    fun decodeHidden(raw: String?): Set<NavTab> {
        if (raw == null) return Customization.DEFAULT_NAV_HIDDEN
        val hidden = raw.split(',').mapNotNull { n -> NavTab.entries.firstOrNull { it.name == n } }.toSet()
        // Hidden layout-preset tabs (Search, Library) don't count toward ETHEREAL's bar.
        val visible = NavTab.ANAHATA.count { it !in hidden }
        return if (visible < 1 || visible > Customization.MAX_VISIBLE_TABS) Customization.DEFAULT_NAV_HIDDEN else hidden
    }

    /** Must be a permutation of the four movable controls; play/pause is never stored. */
    fun decodeControls(raw: String?): List<ControlId> {
        if (raw.isNullOrBlank()) return Customization.DEFAULT_CONTROL_ORDER
        val ids = raw.split(',').mapNotNull { n -> ControlId.entries.firstOrNull { it.name == n } }
        return if (ids.size == ControlId.entries.size && ids.toSet() == ControlId.entries.toSet()) ids else Customization.DEFAULT_CONTROL_ORDER
    }

    fun encodeList(items: List<Enum<*>>): String = items.joinToString(",") { it.name }

    /**
     * Whether hiding [tab] is allowed. Returns null when it is, or the reason when it isn't.
     */
    fun canHide(c: Customization, tab: NavTab): String? =
        if (c.visibleTabs.size <= 1 && tab in c.visibleTabs) "At least one tab must stay visible." else null

    fun canShow(c: Customization, tab: NavTab): String? =
        if (tab !in c.visibleTabs && c.visibleTabs.size >= Customization.MAX_VISIBLE_TABS)
            "The bar holds up to ${Customization.MAX_VISIBLE_TABS} tabs. Hide one first; hidden sections stay reachable from Settings."
        else null
}
