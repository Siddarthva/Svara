package com.svara.app.ui.motion

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.metrics.performance.JankStats
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import com.svara.app.appearance.MotionGovernor
import com.svara.app.appearance.MotionGroups
import com.svara.app.appearance.MotionLevel
import com.svara.app.appearance.PresetId
import com.svara.app.appearance.tokens.PageMotion
import com.svara.app.appearance.tokens.TransitionKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The governor's current verdict, shared with Appearance for its one-line hint. */
object MotionHealth {
    private val _level = MutableStateFlow<MotionLevel?>(null)
    val level: StateFlow<MotionLevel?> = _level.asStateFlow()
    private val _reduced = MutableStateFlow(false)
    val reduced: StateFlow<Boolean> = _reduced.asStateFlow()

    internal fun publish(level: MotionLevel?, reduced: Boolean) {
        _level.value = level
        _reduced.value = reduced
    }
}

val LocalMotionGroups = staticCompositionLocalOf { MotionGroups.NONE }

/**
 * Feeds drawn frames from JankStats to [MotionGovernor] while the app is started. Nothing runs in
 * the background, with the screen off or while nothing is drawn; no timers or wake locks.
 */
@Composable
fun MotionGovernorEffect(ceiling: MotionLevel, enabled: Boolean) {
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(ceiling, enabled, lifecycle) {
        if (!enabled || ceiling <= MotionLevel.LIGHT) {
            MotionHealth.publish(null, false)
            return@DisposableEffect onDispose { }
        }
        val governor = MotionGovernor(ceiling)
        // JankStats reports only frames that were actually drawn (frame metrics), so a still
        // screen costs nothing: no per-vsync callback keeps the app awake.
        val window = view.context.findActivity()?.window
        val stats = window?.let { w ->
            JankStats.createAndTrack(w) { frame ->
                if (governor.onFrame(frame.frameStartNanos / NANOS_PER_MS, janky = frame.isJank)) {
                    MotionHealth.publish(governor.level, governor.reduced)
                }
            }.apply {
                jankHeuristicMultiplier = JANK_MULTIPLIER
                isTrackingEnabled = false
            }
        }
        fun start() { stats?.isTrackingEnabled = true }
        fun stop() { stats?.isTrackingEnabled = false; governor.pause() }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
        }
    }
}

@Composable
fun governedLevel(): MotionLevel? = MotionHealth.level.collectAsState().value

/** One set of page transitions per theme, built from its transition tokens. */
class ThemeTransitions(private val preset: PresetId, private val groups: MotionGroups, private val density: Float, private val tabRoutes: Set<String>) {
    private val tokens = PageMotion.transitions(preset)
    private fun isTab(entry: NavBackStackEntry) = entry.destination.route in tabRoutes
    private fun tabSwitch(scope: AnimatedContentTransitionScope<NavBackStackEntry>) = isTab(scope.initialState) && isTab(scope.targetState)
    private val ms get() = groups.pageMs
    private val slidePx get() = (tokens.slideDp * density).toInt()
    private fun <T> spec() = if (tokens.springDamping < 1f || groups.level == MotionLevel.IMMERSIVE)
        spring<T>(dampingRatio = tokens.springDamping, stiffness = tokens.springStiffness)
    else tween(ms, easing = FastOutSlowInEasing)

    private fun minimal() = !groups.pageTransitions || groups.minimal || groups.level == MotionLevel.LIGHT

    fun enter(scope: AnimatedContentTransitionScope<NavBackStackEntry>, pop: Boolean): EnterTransition = when {
        ms == 0 -> EnterTransition.None
        tabSwitch(scope) -> fadeIn(tween(PageMotion.TAB_FADE_IN_MS, delayMillis = PageMotion.TAB_FADE_OUT_MS))
        minimal() -> fadeIn(tween(ms))
        else -> when (tokens.kind) {
            TransitionKind.FADE_THROUGH_UP -> fadeIn(tween(ms, easing = LinearOutSlowInEasing)) + slideInVertically(spec()) { if (pop) -slidePx else slidePx }
            TransitionKind.CRISP_HORIZONTAL -> fadeIn(tween(ms)) + slideInHorizontally(tween(ms, easing = FastOutSlowInEasing)) { if (pop) -slidePx else slidePx }
            TransitionKind.PARALLAX_SLIDE -> slideInHorizontally(tween(ms, easing = LinearOutSlowInEasing)) { w -> if (pop) -(w * tokens.outgoingParallax).toInt() else w }
            TransitionKind.SPRING_SLIDE -> fadeIn(tween(ms)) + slideInHorizontally(spec()) { if (pop) -slidePx else slidePx }
            TransitionKind.FADE_SCALE -> fadeIn(tween(ms)) + scaleIn(tween(ms, easing = FastOutSlowInEasing), initialScale = tokens.initialScale)
        }
    }

    fun exit(scope: AnimatedContentTransitionScope<NavBackStackEntry>, pop: Boolean): ExitTransition = when {
        ms == 0 -> ExitTransition.None
        tabSwitch(scope) -> fadeOut(tween(PageMotion.TAB_FADE_OUT_MS))
        minimal() -> fadeOut(tween(ms))
        else -> when (tokens.kind) {
            TransitionKind.FADE_THROUGH_UP -> fadeOut(tween(ms / 2))
            TransitionKind.CRISP_HORIZONTAL -> fadeOut(tween(ms / 2)) + slideOutHorizontally(tween(ms)) { if (pop) slidePx else -slidePx }
            TransitionKind.PARALLAX_SLIDE -> slideOutHorizontally(tween(ms, easing = LinearOutSlowInEasing)) { w -> if (pop) w else -(w * tokens.outgoingParallax).toInt() }
            TransitionKind.SPRING_SLIDE -> fadeOut(tween(ms / 2)) + slideOutHorizontally(spec()) { if (pop) slidePx else -slidePx }
            TransitionKind.FADE_SCALE -> fadeOut(tween(ms / 2))
        }
    }
}

/**
 * Song-change swap for a title, artist line or artwork: old content leaves upward, new content
 * arrives from below (or follows [direction] for artwork). Instant when song-change motion is off.
 */
@Composable
fun <K> SongChange(
    key: K,
    modifier: Modifier = Modifier,
    durationMs: Int = PageMotion.SONG_TEXT_MS,
    horizontal: Boolean = false,
    /** +1 next, -1 previous. */
    direction: () -> Int = { 1 },
    content: @Composable (K) -> Unit,
) {
    val groups = LocalMotionGroups.current
    val on = groups.songChange && !groups.minimal
    AnimatedContent(
        targetState = key,
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
        transitionSpec = {
            if (!on) EnterTransition.None togetherWith ExitTransition.None
            else if (horizontal) {
                val d = direction()
                (fadeIn(tween(durationMs)) + slideInHorizontally(tween(durationMs)) { w -> w * d } + scaleIn(tween(durationMs), initialScale = PageMotion.SONG_ARTWORK_SETTLE_SCALE)) togetherWith
                    (fadeOut(tween(durationMs)) + slideOutHorizontally(tween(durationMs)) { w -> -w * d })
            } else {
                (fadeIn(tween(durationMs)) + slideInVertically(tween(durationMs)) { h -> h / 2 }) togetherWith
                    (fadeOut(tween(durationMs)) + slideOutVertically(tween(durationMs)) { h -> -h / 2 })
            }
        },
        label = "songChange",
    ) { k -> content(k) }
}

/** Provides [MotionGroups] to the tree. */
@Composable
fun ProvideMotionGroups(groups: MotionGroups, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMotionGroups provides groups, content = content)
}

@Composable
fun rememberThemeTransitions(preset: PresetId, groups: MotionGroups, tabRoutes: Set<String>): ThemeTransitions {
    val density = LocalDensity.current.density
    return remember(preset, groups, density, tabRoutes) { ThemeTransitions(preset, groups, density, tabRoutes) }
}

/** A frame counts as janky past one and a half frame budgets (as before the move to JankStats). */
private const val JANK_MULTIPLIER = 1.5f
private const val NANOS_PER_MS = 1_000_000L

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
