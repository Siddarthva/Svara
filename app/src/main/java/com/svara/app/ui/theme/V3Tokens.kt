package com.svara.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

/**
 * Sizes and ratios for the V3 artwork system. Composables under `ui/artwork` and the Now Playing
 * styles read only these (and the preset tokens); `V3SourceRulesTest` fails on raw values there.
 */
object ArtworkTokens {
    /** Width over height for each card format. */
    const val SQUARE_RATIO = 1f
    const val PORTRAIT_RATIO = 3f / 4f
    const val WIDE_RATIO = 16f / 9f

    /** Fallback art shows its label only from this size up; smaller tiles show the symbol alone. */
    val fallbackLabelMinSize: Dp = 96.dp
    val fallbackPadding: Dp = 12.dp
    const val FALLBACK_LABEL_LINES = 3

    /** Layered playlist composition: each cover behind the front one is inset and offset by these. */
    const val LAYER_INSET_FRACTION = 0.08f
    const val LAYER_OFFSET_FRACTION = 0.06f
    const val LAYER_BACK_ALPHA = 0.85f
    const val LAYERED_MAX = 3
    const val MOSAIC_COUNT = 4
    val mosaicGap: Dp = 2.dp

    /** The aura's centre colour fades to transparent at this fraction of its radius. */
    const val AURA_FADE_STOP = 1f
}

/** Now Playing styles (Cinema, Compact) and the shared extras row. */
object NowPlayingTokens {
    val cinemaMaxArtwork: Dp = 440.dp
    const val CINEMA_ARTWORK_FRACTION = 0.9f
    val compactArtwork: Dp = 112.dp
    val upNextArtwork: Dp = 44.dp
    const val UP_NEXT_COUNT = 5
    /** Drag distance that closes Now Playing with a swipe down. */
    val dismissDistance: Dp = 120.dp
    /** How far the screen follows the finger, as a fraction of the drag (0 with reduced motion). */
    const val DISMISS_FOLLOW = 0.5f
    /** Overlay on Cinema's backdrop so text keeps 4.5:1 over the brightest part of the cover. */
    const val COLOUR_FIELD_ALPHA = 0.45f
    const val GRADIENT_ALPHA = 0.55f
}

/** Skeleton rows and state screens. */
object StateTokens {
    val rowHeight: Dp = 64.dp
    val rowArtwork: Dp = 48.dp
    val lineHeight: Dp = 12.dp
    val lineGap: Dp = 8.dp
    const val TITLE_LINE_FRACTION = 0.6f
    const val SUBTITLE_LINE_FRACTION = 0.35f
    const val SKELETON_ROWS = 8
    const val SKELETON_ALPHA = 0.5f
    val headerArtwork: Dp = 120.dp
}

/** Lyrics Studio. */
object LyricsTokens {
    /** Lines kept above the active one when it scrolls into view. */
    const val LINES_ABOVE_ACTIVE = 2
    const val PAST_ALPHA = 0.7f
    const val SMALL_STEP_MS = 100L
    const val BIG_STEP_MS = 500L
    const val EDITOR_LINES = 8
    /** Strongest wash of the cover colour behind lyrics, before the contrast cap. */
    const val BACKGROUND_ALPHA = 0.35f
    /** The wash fades to this fraction of its strength at the bottom. */
    const val BACKGROUND_FADE = 0.2f
}

/** Statistics chart. */
object StatsTokens {
    val chartHeight: Dp = 120.dp
    val barGap: Dp = 6.dp
}

/** ZENITH visualiser gallery. */
object VisualTokens {
    const val MAX_FPS = 30
    const val PULSE_FPS = 20
    const val PREVIEW_RATIO = 1f
    const val ARTWORK_FRACTION = 0.42f
    /** Background dim at 100 % opacity setting. */
    const val MAX_SCRIM = 0.7f
    const val MIN_GAIN = 0.6f
    const val GAIN_SPAN = 1.2f
    const val GLOW_GAIN = 0.4f
    const val GLOW_ALPHA = 0.3f
    const val BAR_GAP = 0.3f
    const val MIN_BAR_PX = 3f
    const val STROKE_PX = 4f
    const val RING_INNER = 0.24f
    const val RING_REACH = 0.22f
    const val RING_REACH_FULL = 0.3f
    const val PULSE_BASE = 0.22f
    const val PULSE_GAIN = 0.5f
    const val PULSE_ALPHA = 0.35f
    const val AURA_BASE = 0.7f
    const val AURA_GAIN = 0.5f
    const val AURA_ALPHA = 0.45f
}

/** Home-screen widgets (Glance). */
object WidgetTokens {
    const val SMALL_ART_PX = 192
    const val MEDIUM_ART_PX = 320
    const val LARGE_ART_PX = 640
    const val TRANSLUCENT_ALPHA = 0.78f
    const val FALLBACK_ALPHA = 0.6f
    const val TRACK_ALPHA = 0.3f
    val corner: Dp = 20.dp
    val artCorner: Dp = 12.dp
    val padding: Dp = 12.dp
    val gap: Dp = 8.dp
    val icon: Dp = 24.dp
    val iconLarge: Dp = 32.dp
    val touch: Dp = 48.dp
    val touchLarge: Dp = 56.dp
    val smallArt: Dp = 48.dp
    val mediumArt: Dp = 88.dp
    val progress: Dp = 12.dp
    val progressPadding: Dp = 4.dp
    /** Below this height the small widget drops to one line. */
    val tallSmall: Dp = 80.dp
    /** From this width the small widget also shows previous and next. */
    val skipFitsWidth: Dp = 220.dp
    val title: TextUnit = 15.sp
    val body: TextUnit = 13.sp
    val caption: TextUnit = 12.sp
}
