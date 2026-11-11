package com.svara.app.appearance.tokens

import com.svara.app.appearance.PresetId

/** How a theme moves between pages. Every number a transition uses lives here. */
enum class TransitionKind { FADE_THROUGH_UP, CRISP_HORIZONTAL, PARALLAX_SLIDE, SPRING_SLIDE, FADE_SCALE }

data class TransitionTokens(
    val kind: TransitionKind,
    /** Slide distance in dp; for [TransitionKind.PARALLAX_SLIDE] a fraction of the width is used instead. */
    val slideDp: Float,
    /** Share of the travel the outgoing screen moves (parallax). */
    val outgoingParallax: Float,
    /** Starting scale for [TransitionKind.FADE_SCALE]. */
    val initialScale: Float,
    val springStiffness: Float,
    val springDamping: Float,
)

object PageMotion {
    /** Bottom-tab fade-through: the old tab leaves fast, the new one arrives a little slower. */
    const val TAB_FADE_OUT_MS = 90
    const val TAB_FADE_IN_MS = 150
    /** Reduced motion, Efficiency, battery saver and heat: a plain fade, no longer than this. */
    const val MINIMAL_FADE_MS = 100
    const val BALANCED_MIN_MS = 200
    const val BALANCED_MAX_MS = 300
    const val SONG_ARTWORK_MS = 300
    const val SONG_ARTWORK_SETTLE_SCALE = 0.98f
    const val SONG_TEXT_MS = 200
    const val MINI_PLAYER_MS = 150
    const val PROGRESS_RESET_MS = 150
    const val NOW_PLAYING_HIGHLIGHT_MS = 150
    const val SKELETON_CROSSFADE_MS = 150
    const val ARTWORK_FADE_IN_MS = 120
    const val PLACEMENT_SPRING_STIFFNESS = 1500f // about 200 ms to settle
    const val PLACEMENT_SPRING_DAMPING = 1f
    const val PARALLAX_FACTOR = 0.6f
    const val CAROUSEL_NEIGHBOUR_SCALE = 0.96f
    const val EDGE_FADE_DP = 24f
    /** Faster than this (px/s), list entrance effects and artwork fades pause. */
    const val FAST_FLING_PX_PER_S = 6000f
    /** Two identical requests closer than this are treated as one. */
    const val DUPLICATE_WINDOW_MS = 400L

    fun transitions(id: PresetId): TransitionTokens = when (id) {
        PresetId.ANAHATA -> TransitionTokens(TransitionKind.FADE_THROUGH_UP, 12f, 0f, 1f, 400f, 1f)
        PresetId.SHRUTI -> TransitionTokens(TransitionKind.CRISP_HORIZONTAL, 16f, 0f, 1f, 1500f, 1f)
        PresetId.TALA -> TransitionTokens(TransitionKind.PARALLAX_SLIDE, 0f, 0.3f, 1f, 400f, 1f)
        PresetId.RAGA -> TransitionTokens(TransitionKind.SPRING_SLIDE, 24f, 0f, 1f, 500f, 0.85f)
        PresetId.SPANDA -> TransitionTokens(TransitionKind.FADE_SCALE, 0f, 0f, 0.96f, 1500f, 1f)
    }
}

/** Frame-health thresholds for the motion governor. */
object GovernorTokens {
    const val WINDOW_MS = 3_000L
    const val JANK_FRACTION = 0.10f
    const val STABLE_MS = 30_000L
    const val STEP_UP_INTERVAL_MS = 60_000L
    /** A gap this long between drawn frames means the screen was still: the window starts afresh. */
    const val IDLE_GAP_MS = 250L
    /** A window needs this many frames before it can count as janky (a few frames after a pause prove nothing). */
    const val MIN_FRAMES = 30
}

/** The live ambient wave on Now Playing and the mini-player. */
object WaveTokens {
    const val LOOP_MS = 10_000L
    const val FADE_MS = 400
    const val BALANCED_FPS = 20
    const val IMMERSIVE_FPS = 30
    const val SEGMENTS = 48
    /** Strip on the mini-player's top edge, in dp. */
    const val STRIP_DP = 24f
    /** Share of Now Playing's height the wave covers (bottom third). */
    const val PLAYER_FRACTION = 1f / 3f
    val AMPLITUDE = floatArrayOf(0.18f, 0.12f, 0.08f)
    val SPEED = floatArrayOf(1f, 2f, 3f)
    val PHASE = floatArrayOf(0f, 0.33f, 0.66f)
    val BASELINE = floatArrayOf(0.35f, 0.5f, 0.62f)
    val CYCLES = floatArrayOf(1.2f, 1.8f, 2.6f)
}

/** Animation extras. */
object ExtraTokens {
    const val HEART_SCALE = 1.3f
    const val HEART_DAMPING = 0.45f
    const val HEART_STIFFNESS = 600f
    const val BURST_DOTS = 7
    const val BURST_MS = 400
    const val BURST_REACH = 0.9f
    const val DOT_FRACTION = 0.06f
    const val NOTES = 3
    const val NOTE_DRIFT_MS = 600
    const val NOTE_SPACING = 4f
    const val NOTE_SCALE = 1.4f
    const val PRESS_SCALE = 0.92f
    const val PRESS_DAMPING = 0.6f
    const val PRESS_STIFFNESS = 800f
    const val MORPH_MS = 180
    const val MORPH_START_SCALE = 0.7f
}
