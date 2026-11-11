package com.svara.app.ui.states

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.svara.app.ui.components.EmptyState
import com.svara.app.ui.theme.Radii
import com.svara.app.ui.theme.Spacing
import com.svara.app.ui.theme.StateTokens

/**
 * Static skeleton rows shown while a list loads. Nothing animates (no shimmer), so it costs one
 * frame and behaves the same with reduced motion. TalkBack hears "Loading" once.
 */
@Composable
fun SkeletonList(modifier: Modifier = Modifier, rows: Int = StateTokens.SKELETON_ROWS, artwork: Boolean = true) {
    Column(modifier.fillMaxWidth().semantics { contentDescription = "Loading"; liveRegion = LiveRegionMode.Polite }) {
        repeat(rows) { SkeletonRow(artwork) }
    }
}

/** One skeleton row at the height of a real song row, so nothing jumps when content arrives. */
@Composable
fun SkeletonRow(artwork: Boolean = true) {
    val block = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = StateTokens.SKELETON_ALPHA)
    val shape = RoundedCornerShape(Radii.small)
    Row(
        Modifier.fillMaxWidth().height(StateTokens.rowHeight).padding(horizontal = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (artwork) {
            Box(Modifier.size(StateTokens.rowArtwork).background(block, shape))
            Spacer(Modifier.width(Spacing.l))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(StateTokens.lineGap)) {
            Box(Modifier.fillMaxWidth(StateTokens.TITLE_LINE_FRACTION).height(StateTokens.lineHeight).background(block, shape))
            Box(Modifier.fillMaxWidth(StateTokens.SUBTITLE_LINE_FRACTION).height(StateTokens.lineHeight).background(block, shape))
        }
    }
}

/** Something went wrong loading this screen: what happened, and a way to try again. */
@Composable
fun ErrorState(title: String, message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Rounded.ErrorOutline,
        title = title,
        message = message,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        action = onRetry?.let { retry -> { Button(onClick = retry, modifier = Modifier.heightIn(min = Spacing.touch)) { Text("Try again") } } },
    )
}

/** Full-screen loading for screens without a list shape (fills the space, skeleton rows on top). */
@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) { SkeletonList() }
}
