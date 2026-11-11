package com.svara.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.runtime.staticCompositionLocalOf

/** The app's scroll tracker, for artwork that skips its crossfade while a list flings. */
val LocalScrollTracker = staticCompositionLocalOf<ScrollDirectionTracker?> { null }

/**
 * Follows the scroll direction of any list below it (through nested scroll) and says whether a
 * floating button should show: hidden once the content has moved [thresholdPx] down, shown again
 * once it has moved that far up. Consumes nothing, so lists scroll exactly as before.
 */
class ScrollDirectionTracker(private val thresholdPx: Float = DEFAULT_THRESHOLD_PX) : NestedScrollConnection {
    var visible by mutableStateOf(true)
    private var travel = 0f

    /**
     * True while a list below is flinging. Plain field, not state: artwork reads it once when it
     * builds a request (no crossfade mid-fling), and nothing recomposes when it flips.
     */
    @Volatile
    var flinging = false
        private set

    /** [dy] as nested scroll reports it: negative while the content moves up (scrolling down the list). */
    fun onDelta(dy: Float) {
        if (dy == 0f) return
        // A change of direction starts counting afresh.
        if ((dy < 0f) != (travel < 0f)) travel = 0f
        travel += dy
        if (travel <= -thresholdPx && visible) visible = false
        if (travel >= thresholdPx && !visible) visible = true
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        onDelta(consumed.y)
        flinging = source != NestedScrollSource.UserInput
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        flinging = false
        return Velocity.Zero
    }

    companion object {
        const val DEFAULT_THRESHOLD_PX = 24f
    }
}

/**
 * A header pinned over a list. The header is measured first and the list gets exactly its
 * height as top content padding in the same layout pass, so the first row is never hidden under
 * the header at any font size, and there is no frame where the gap is wrong.
 */
@Composable
fun PinnedHeaderLayout(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minHeight = 0)
        val headerPlaceables = subcompose(Slot.HEADER, header).map { it.measure(loose) }
        val headerHeight = headerPlaceables.maxOfOrNull { it.height } ?: 0
        val padding = PaddingValues(top = headerHeight.toDp())
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else headerHeight
        val body = subcompose(Slot.CONTENT) { content(padding) }.map {
            it.measure(Constraints.fixed(constraints.maxWidth, height))
        }
        layout(constraints.maxWidth, height) {
            body.forEach { it.place(0, 0) }
            headerPlaceables.forEach { it.place(0, 0) }
        }
    }
}

private enum class Slot { HEADER, CONTENT }
