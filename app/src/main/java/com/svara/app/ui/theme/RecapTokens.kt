package com.svara.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.svara.app.appearance.PresetId

/** How each theme paints a recap card's background; layouts are shared. */
enum class RecapGradient { SOFT_RADIAL, CRISP_LINEAR, SMOOTH_DIAGONAL, MULTI_STOP, DARK_GLOW }

/** Recap story cards. Every size, duration and alpha the recap screens use. */
object RecapTokens {
    const val CARD_MS = 6_000
    const val COUNT_UP_MS = 1_200
    const val STAGGER_MS = 30
    const val STAGGER_ITEMS = 8
    const val ENTRANCE_MS = 300
    const val SPARKLE_MS = 600
    const val PULSE_MS = 900
    const val BALANCED_FPS = 20
    const val IMMERSIVE_FPS = 30
    const val SCRIM_ALPHA = 0.55f
    const val SHAPE_ALPHA = 0.18f
    const val GLOW_ALPHA = 0.35f
    const val TRACK_ALPHA = 0.3f
    const val ENTRANCE_SCALE = 0.9f
    const val PULSE_SCALE = 1.08f
    const val STACK_ROTATION = 6f
    const val PAGE_TURN_DEGREES = 12f
    /** Fraction of the width a tap must be within to go back. */
    const val BACK_ZONE = 0.3f
    /** Average album length used for the plain comparison line, in minutes. */
    const val AVERAGE_ALBUM_MINUTES = 45
    const val SHARE_WIDTH = 1080
    const val SHARE_HEIGHT = 1920
    /** Shape wipe between cards; the minimal fade under the effects policy. */
    const val WIPE_MS = 300
    const val MINIMAL_FADE_MS = 100
    /** WCAG AA for body text, and the step used when raising the scrim to reach it. */
    const val MIN_CONTRAST = 4.5
    const val SCRIM_STEP = 0.05f

    fun gradient(preset: PresetId): RecapGradient = when (preset) {
        PresetId.ANAHATA -> RecapGradient.SOFT_RADIAL
        PresetId.SHRUTI -> RecapGradient.CRISP_LINEAR
        PresetId.TALA -> RecapGradient.SMOOTH_DIAGONAL
        PresetId.RAGA -> RecapGradient.MULTI_STOP
        PresetId.SPANDA -> RecapGradient.DARK_GLOW
    }

    val progressHeight: Dp = 3.dp
    val progressGap: Dp = 4.dp
    val heroArtwork: Dp = 240.dp
    val thumb: Dp = 48.dp
    val barHeight: Dp = 14.dp
    val heatHeight: Dp = 56.dp
    val closeDragDistance: Dp = 120.dp
    val homeCardHeight: Dp = 88.dp
    val previewDot: Dp = 10.dp
    val sparkleDot: Dp = 4.dp
    val stackOffset: Dp = 14.dp
}
