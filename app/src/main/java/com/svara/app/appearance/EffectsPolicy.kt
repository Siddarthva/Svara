package com.svara.app.appearance

import com.svara.app.appearance.tokens.ThemeTokens

/** Mirrors PowerManager thermal status constants (NONE=0 … SHUTDOWN=6). */
enum class ThermalLevel { NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN;
    companion object {
        fun fromStatus(status: Int): ThermalLevel = entries.getOrElse(status) { NONE }
    }
}

enum class BackgroundKind { STATIC, CACHED_PALETTE, AMBIENT }

enum class PolicyOverride { NONE, BATTERY_SAVER, THERMAL }

/** Every visual limit the UI may use. Screens read these and never check device state themselves. */
data class EffectLimits(
    val selectedMode: PerformanceMode,
    /** The mode actually in force after battery-saver and thermal overrides. */
    val mode: PerformanceMode,
    val override: PolicyOverride,
    val backgrounds: Set<BackgroundKind>,
    /** Blur in scrolling lists is never allowed, in any mode. */
    val blurInLists: Boolean,
    /** Max blur radius on the full-screen player in dp; 0 = no blur. */
    val playerBlurDp: Int,
    val fadesOnly: Boolean,
    /** Allowed transition duration range in ms (min == max means fixed). */
    val transitionMinMs: Int,
    val transitionMaxMs: Int,
    val ambientFps: Int,
    val visualiserFps: Int,
    val glowAllowed: Boolean,
    val glassAllowed: Boolean,
    /** Heart pop, tab tint cross-fade, artwork spring: off in Efficiency (tap feedback stays). */
    val microAnimations: Boolean = !fadesOnly,
    /** Live RenderEffect blur (SOLACE's mini-player, API 31+). Everything else uses cached bitmaps. */
    val liveBlurAllowed: Boolean = false,
) {
    val notice: String?
        get() = when (override) {
            PolicyOverride.BATTERY_SAVER -> "Battery saver is on. Visual effects are reduced."
            PolicyOverride.THERMAL -> "Your device is hot. Visual effects are reduced."
            PolicyOverride.NONE -> null
        }
}

object EffectsPolicy {

    /** The table from the spec, one row per mode. */
    fun limitsFor(mode: PerformanceMode): EffectLimits = when (mode) {
        PerformanceMode.EFFICIENCY -> EffectLimits(
            selectedMode = mode, mode = mode, override = PolicyOverride.NONE,
            backgrounds = setOf(BackgroundKind.STATIC),
            blurInLists = false, playerBlurDp = 0,
            fadesOnly = true, transitionMinMs = 100, transitionMaxMs = 100,
            ambientFps = 0, visualiserFps = 0, glowAllowed = false, glassAllowed = false,
        )
        PerformanceMode.BALANCED -> EffectLimits(
            selectedMode = mode, mode = mode, override = PolicyOverride.NONE,
            // Static backgrounds: cached palettes and cached blurred bitmaps, nothing that moves.
            backgrounds = setOf(BackgroundKind.STATIC, BackgroundKind.CACHED_PALETTE),
            blurInLists = false, playerBlurDp = 0,
            fadesOnly = false, transitionMinMs = 150, transitionMaxMs = 250,
            ambientFps = 0, visualiserFps = 0, glowAllowed = true, glassAllowed = true,
        )
        PerformanceMode.IMMERSIVE -> EffectLimits(
            selectedMode = mode, mode = mode, override = PolicyOverride.NONE,
            backgrounds = setOf(BackgroundKind.STATIC, BackgroundKind.CACHED_PALETTE, BackgroundKind.AMBIENT),
            blurInLists = false, playerBlurDp = 0,
            fadesOnly = false, transitionMinMs = 150, transitionMaxMs = 400,
            ambientFps = 10, visualiserFps = 30, glowAllowed = true, glassAllowed = true,
            liveBlurAllowed = true,
        )
    }

    /**
     * Battery saver, or a thermal status of SEVERE or worse, forces Efficiency. When the
     * condition clears the selected mode applies again (this is a pure function of the inputs).
     */
    fun resolve(selected: PerformanceMode, batterySaver: Boolean, thermal: ThermalLevel): EffectLimits {
        val override = when {
            batterySaver -> PolicyOverride.BATTERY_SAVER
            thermal >= ThermalLevel.SEVERE -> PolicyOverride.THERMAL
            else -> PolicyOverride.NONE
        }
        val effective = if (override == PolicyOverride.NONE) selected else PerformanceMode.EFFICIENCY
        return limitsFor(effective).copy(selectedMode = selected, override = override)
    }
}

/**
 * Animation timing derived from the policy and the user's motion settings. A duration of 0 means
 * "change instantly" — used for reduced motion and intensity 0.
 */
data class MotionSpec(
    val enabled: Boolean,
    val fadesOnly: Boolean,
    /** 0..1, scales animation magnitudes (distances, scale amounts). */
    val intensity: Float,
    val playful: Boolean,
    private val feedbackMs: Int,
    private val screenMs: Int,
) {
    /** Small state-change feedback (150–300 ms before policy limits). */
    val feedback: Int get() = if (enabled) feedbackMs else 0
    /** Screen transitions (up to 400 ms). */
    val screen: Int get() = if (enabled) screenMs else 0

    companion object {
        val NONE = MotionSpec(false, true, 0f, false, 0, 0)

        /**
         * @param speed transition speed multiplier; higher is faster (durations divide by it).
         * @param intensityPercent 0..100; 0 disables animation except progress indicators.
         */
        fun from(
            limits: EffectLimits,
            style: AnimationStyle,
            reducedMotion: Boolean,
            speed: Float,
            intensityPercent: Int,
            presetFadesOnly: Boolean,
        ): MotionSpec {
            if (reducedMotion || style == AnimationStyle.REDUCED || intensityPercent <= 0) return NONE
            val styleFactor = when (style) {
                AnimationStyle.SUBTLE -> 0.8f
                AnimationStyle.CINEMATIC -> 1.3f
                AnimationStyle.PLAYFUL -> 1.0f
                AnimationStyle.REDUCED -> 0f
            }
            val s = speed.coerceIn(0.5f, 2f)
            fun clamp(ms: Float) = ms.toInt().coerceIn(limits.transitionMinMs, limits.transitionMaxMs)
            return MotionSpec(
                enabled = true,
                fadesOnly = limits.fadesOnly || presetFadesOnly,
                intensity = (intensityPercent.coerceIn(0, 100) / 100f),
                playful = style == AnimationStyle.PLAYFUL && !limits.fadesOnly,
                feedbackMs = clamp(200 * styleFactor / s),
                screenMs = clamp(300 * styleFactor / s),
            )
        }
    }
}

/**
 * What a layout preset may animate right now: its motion tokens after the effects policy and the
 * user's motion settings. Composables read this and never check modes themselves.
 */
data class PresetEffects(
    val pressScale: Float,
    val pressMs: Int,
    val playPressScale: Float,
    val playPressMs: Int,
    val transitionMs: Int,
    val tabCrossfadeMs: Int,
    val pagerMs: Int,
    val lyricsScrollMs: Int,
    val swipeSnapMs: Int,
    val heartPop: Boolean,
    val heartPopScale: Float,
    val heartPopMs: Int,
    /** SOLACE's paused-artwork spring. */
    val artworkSpring: Boolean,
    /** OCTAVE's breathing overlay frame cap; 0 = static. */
    val breathingFps: Int,
    val liveBlur: Boolean,
    /** ZENITH's audio-reactive layer after the policy: Off in Efficiency, at most Ambient in Balanced. */
    val visualiser: VisualiserMode,
    val glowFps: Int,
    val spectrumFps: Int,
    /** Cached blurred backdrops; off in Efficiency (and battery saver), which use a static scrim. */
    val cachedBlur: Boolean = true,
    /** Ambient colour cross-fade on track change; 0 = switch instantly. */
    val ambientCrossfadeMs: Int = 0,
    /** Play/pause icon morph and tab icon outline-to-filled. */
    val iconMorphMs: Int = 0,
    /** "Added" checkmark replacing the plus. */
    val checkmarkMs: Int = 0,
    /** First appearance of list sections, once per screen visit. */
    val sectionFadeMs: Int = 0,
) {
    companion object {
        /** Reactive glow cap; spectrum bars may use the policy's full visualiser rate. */
        const val GLOW_MAX_FPS = 20
        const val FEEDBACK_MS = 100

        fun resolve(
            motion: com.svara.app.appearance.tokens.MotionTokens,
            limits: EffectLimits,
            motionEnabled: Boolean,
            visualiser: VisualiserMode,
        ): PresetEffects {
            val mode = limits.mode
            val feedbackOnly = mode == PerformanceMode.EFFICIENCY
            fun transition(ms: Int) = when {
                !motionEnabled || feedbackOnly || ms <= 0 -> 0
                else -> ms.coerceIn(limits.transitionMinMs, limits.transitionMaxMs)
            }
            fun feedback(ms: Int) = if (!motionEnabled) 0 else ms.coerceAtMost(FEEDBACK_MS)
            val micro = motionEnabled && limits.microAnimations
            val effectiveVisualiser = when {
                feedbackOnly -> VisualiserMode.OFF
                visualiser == VisualiserMode.OFF -> VisualiserMode.OFF
                // Reactive layers need Immersive and motion; otherwise the glow stays static.
                !motionEnabled || mode != PerformanceMode.IMMERSIVE -> VisualiserMode.AMBIENT
                else -> visualiser
            }
            val analysisFps = if (effectiveVisualiser.needsAnalysis) limits.visualiserFps else 0
            return PresetEffects(
                pressScale = if (motionEnabled) motion.pressScale else 1f,
                pressMs = feedback(motion.pressDurationMs),
                playPressScale = if (motionEnabled) motion.playPressScale else 1f,
                playPressMs = feedback(motion.playPressMs),
                transitionMs = transition(motion.transitionMs),
                tabCrossfadeMs = if (micro) motion.tabCrossfadeMs else 0,
                pagerMs = transition(motion.pagerMs),
                lyricsScrollMs = transition(motion.lyricsScrollMs),
                swipeSnapMs = transition(motion.swipeSnapMs),
                heartPop = micro && motion.heartPopMs > 0,
                heartPopScale = motion.heartPopScale,
                heartPopMs = if (micro) motion.heartPopMs else 0,
                artworkSpring = micro && motion.enableArtworkMotion && motion.pausedArtworkScale < 1f,
                breathingFps = if (motionEnabled && motion.enableAmbientBackground && motion.breathingMaxFps > 0)
                    minOf(motion.breathingMaxFps, limits.ambientFps) else 0,
                liveBlur = motion.enableBlur && limits.liveBlurAllowed,
                visualiser = effectiveVisualiser,
                glowFps = minOf(GLOW_MAX_FPS, analysisFps),
                spectrumFps = if (effectiveVisualiser == VisualiserMode.FULL_SPECTRUM) analysisFps else 0,
                cachedBlur = !feedbackOnly,
                ambientCrossfadeMs = if (micro) ThemeTokens.AMBIENT_CROSSFADE_MS else 0,
                iconMorphMs = if (micro) ThemeTokens.ICON_MORPH_MS else 0,
                checkmarkMs = if (micro) ThemeTokens.CHECKMARK_MS else 0,
                sectionFadeMs = if (micro) ThemeTokens.SECTION_FADE_MS else 0,
            )
        }
    }
}
