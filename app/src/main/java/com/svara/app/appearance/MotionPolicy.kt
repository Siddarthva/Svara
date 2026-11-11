package com.svara.app.appearance

import com.svara.app.appearance.tokens.GovernorTokens
import com.svara.app.appearance.tokens.PageMotion
import com.svara.app.appearance.tokens.WaveTokens

/** How much may move. Ordered from least to most. */
enum class MotionLevel { MINIMAL, LIGHT, BALANCED, IMMERSIVE }

/**
 * Which motion groups are on right now. Screens read this and never check device state; every
 * group is off (instant swap or a [PageMotion.MINIMAL_FADE_MS] fade) at [MotionLevel.MINIMAL].
 */
data class MotionGroups(
    val level: MotionLevel,
    val pageTransitions: Boolean,
    val sharedElements: Boolean,
    val scrollEffects: Boolean,
    val parallax: Boolean,
    val staggers: Boolean,
    val songChange: Boolean,
    /** Page transition duration in ms; [PageMotion.MINIMAL_FADE_MS] or less when minimal. */
    val pageMs: Int,
) {
    val minimal: Boolean get() = level == MotionLevel.MINIMAL

    companion object {
        val NONE = MotionGroups(MotionLevel.MINIMAL, false, false, false, false, false, false, 0)

        /** The highest level the user's choices and the device allow, before the governor. */
        fun ceiling(limits: EffectLimits, motion: MotionSpec): MotionLevel = when {
            !motion.enabled || limits.mode == PerformanceMode.EFFICIENCY -> MotionLevel.MINIMAL
            motion.fadesOnly -> MotionLevel.LIGHT
            limits.mode == PerformanceMode.IMMERSIVE -> MotionLevel.IMMERSIVE
            else -> MotionLevel.BALANCED
        }

        fun resolve(limits: EffectLimits, motion: MotionSpec, custom: Customization, governed: MotionLevel?): MotionGroups {
            val ceiling = ceiling(limits, motion)
            val level = if (governed != null && custom.smartSmoothing && governed < ceiling) maxOf(governed, minOf(ceiling, MotionLevel.LIGHT)) else ceiling
            if (level == MotionLevel.MINIMAL) {
                return NONE.copy(pageMs = if (motion.enabled && custom.pageTransitions) PageMotion.MINIMAL_FADE_MS else 0)
            }
            val rich = level >= MotionLevel.BALANCED
            val pageMs = when (level) {
                MotionLevel.IMMERSIVE -> motion.screen.coerceAtLeast(PageMotion.BALANCED_MIN_MS)
                MotionLevel.BALANCED -> motion.screen.coerceIn(PageMotion.BALANCED_MIN_MS, PageMotion.BALANCED_MAX_MS)
                else -> motion.feedback.coerceAtMost(PageMotion.BALANCED_MIN_MS)
            }
            return MotionGroups(
                level = level,
                pageTransitions = custom.pageTransitions,
                sharedElements = custom.pageTransitions && rich,
                scrollEffects = custom.scrollEffects,
                parallax = custom.scrollEffects && rich,
                staggers = rich,
                songChange = custom.songChangeAnimation,
                pageMs = if (custom.pageTransitions) pageMs else 0,
            )
        }
    }
}

/**
 * Watches frame timing and steps motion down one level when more than 10 % of frames in a
 * 3-second window miss their budget, and back up one level after 30 s of stable frames (at most
 * once a minute). It never goes below [floor] — the level the user and device already allow is
 * the ceiling; the floor is the lowest the governor itself may pick. Pure: the caller feeds frames.
 */
class MotionGovernor(private val ceiling: MotionLevel, private val floor: MotionLevel = MotionLevel.LIGHT) {
    var level: MotionLevel = ceiling
        private set
    /** True once the governor has stepped down; drives the one-line hint in Appearance. */
    var reduced: Boolean = false
        private set

    private var windowStart = -1L
    private var frames = 0
    private var janky = 0
    private var stableSince = -1L
    private var lastStepUp = Long.MIN_VALUE / 2

    private var lastFrame = -1L

    /**
     * Records one drawn frame. Returns true when [level] changed. Only drawn frames arrive, so a
     * still screen sends nothing; after such a pause the window starts afresh, and a window with
     * fewer than [GovernorTokens.MIN_FRAMES] frames never counts as janky.
     */
    fun onFrame(nowMs: Long, janky: Boolean): Boolean {
        if (windowStart < 0) { windowStart = nowMs; stableSince = nowMs }
        if (lastFrame >= 0 && nowMs - lastFrame > GovernorTokens.IDLE_GAP_MS) { windowStart = nowMs; frames = 0; this.janky = 0 }
        lastFrame = nowMs
        frames++
        if (janky) this.janky++
        if (nowMs - windowStart < GovernorTokens.WINDOW_MS) return false
        val bad = frames >= GovernorTokens.MIN_FRAMES && this.janky > frames * GovernorTokens.JANK_FRACTION
        windowStart = nowMs; frames = 0; this.janky = 0
        val lowest = minOf(floor, ceiling)
        if (bad) {
            stableSince = nowMs
            if (level > lowest) { level = MotionLevel.entries[level.ordinal - 1]; reduced = true; return true }
            return false
        }
        if (level < ceiling && nowMs - stableSince >= GovernorTokens.STABLE_MS && nowMs - lastStepUp >= GovernorTokens.STEP_UP_INTERVAL_MS) {
            level = MotionLevel.entries[level.ordinal + 1]
            lastStepUp = nowMs
            stableSince = nowMs
            if (level == ceiling) reduced = false
            return true
        }
        return false
    }

    /** Called when the app goes to the background: the next window starts fresh. */
    fun pause() { windowStart = -1L; lastFrame = -1L; frames = 0; janky = 0 }
}

/** Ignores a request identical to the previous one if it arrives within [windowMs]. */
class RepeatGuard(private val windowMs: Long = PageMotion.DUPLICATE_WINDOW_MS) {
    private var lastKey: Any? = null
    private var lastAt = Long.MIN_VALUE / 2

    fun accept(key: Any?, nowMs: Long): Boolean {
        if (key == lastKey && nowMs - lastAt < windowMs) return false
        lastKey = key; lastAt = nowMs
        return true
    }
}

/** What the live wave may do right now; fps 0 means it is not drawn at all. */
data class WaveSpec(val fps: Int, val layers: Int, val alpha: Float, val amplitude: Float) {
    companion object { val OFF = WaveSpec(0, 0, 0f, 0f) }
}

object WavePolicy {
    /** Off at the minimal level (reduced motion, Efficiency, battery saver, severe heat) and when switched off. */
    fun resolve(groups: MotionGroups, custom: Customization): WaveSpec {
        if (!custom.liveWave || groups.minimal) return WaveSpec.OFF
        val immersive = groups.level == MotionLevel.IMMERSIVE
        val (alpha, amp) = when (custom.waveStrength) {
            WaveStrength.SUBTLE -> 0.12f to 0.7f
            WaveStrength.NORMAL -> 0.16f to 1f
            WaveStrength.LIVELY -> 0.20f to 1.3f
        }
        return WaveSpec(
            fps = if (immersive) WaveTokens.IMMERSIVE_FPS else WaveTokens.BALANCED_FPS,
            layers = if (immersive) 3 else 2,
            alpha = alpha,
            amplitude = amp,
        )
    }
}
