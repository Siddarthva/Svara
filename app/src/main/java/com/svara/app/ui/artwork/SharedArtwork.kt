package com.svara.app.ui.artwork

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.svara.app.appearance.PerformanceMode
import com.svara.app.ui.theme.LocalAppearance

/**
 * Shared-element artwork between the mini-player (or an album card) and the screen it opens.
 * The scaffold provides the transition scope; each destination and the mini-player's
 * show/hide animation provide their visibility scope. Without both, or in Efficiency or with
 * reduced motion, artwork simply swaps with the screen.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalArtworkVisibility = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Keys that pair artwork across screens. */
object ArtworkKeys {
    /** The playing song's art: mini-player and Now Playing. */
    fun nowPlaying(songId: Long): Any = "np-art-$songId"

    /** An album card and that album's page header. */
    fun album(albumId: Long): Any = "album-art-$albumId"
}

@Composable
fun ProvideArtworkVisibility(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalArtworkVisibility provides scope, content = content)
}

/** True when artwork should move between screens instead of swapping instantly. */
@Composable
fun sharedArtworkEnabled(): Boolean {
    val a = LocalAppearance.current
    return a.motion.enabled && a.limits.mode != PerformanceMode.EFFICIENCY && com.svara.app.ui.motion.LocalMotionGroups.current.sharedElements
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArtwork(key: Any): Modifier {
    val transition = LocalSharedTransition.current ?: return this
    val visibility = LocalArtworkVisibility.current ?: return this
    if (!sharedArtworkEnabled()) return this
    return with(transition) {
        this@sharedArtwork.sharedElement(rememberSharedContentState(key), visibility)
    }
}
