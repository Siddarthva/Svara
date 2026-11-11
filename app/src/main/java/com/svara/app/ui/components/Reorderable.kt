package com.svara.app.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Minimal drag-to-reorder for a LazyColumn. The list shows a local copy that is rearranged
 * while dragging; [onMove] fires once on drop with the original and final positions.
 */
@Stable
class ReorderableListState<T>(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    private val keyOf: (T) -> Any,
    private val headerCount: Int,
) {
    val items = mutableStateListOf<T>()
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set
    private var startIndex = -1
    internal var onMove: (Int, Int) -> Unit = { _, _ -> }

    fun sync(source: List<T>) {
        if (draggingKey == null) {
            items.clear()
            items.addAll(source)
        }
    }

    fun start(key: Any) {
        draggingKey = key
        startIndex = items.indexOfFirst { keyOf(it) == key }
        dragOffset = 0f
    }

    fun drag(dy: Float) {
        val key = draggingKey ?: return
        dragOffset += dy
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val center = info.offset + info.size / 2 + dragOffset
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { other ->
            other.key != key && other.index >= headerCount && other.index < headerCount + items.size &&
                center.toInt() in other.offset..(other.offset + other.size)
        }
        if (target != null) {
            val from = info.index - headerCount
            val to = target.index - headerCount
            if (from in items.indices && to in items.indices) {
                items.add(to, items.removeAt(from))
                dragOffset -= (target.offset - info.offset)
            }
        }
        autoScroll(info)
    }

    private fun autoScroll(info: LazyListItemInfo) {
        val viewportStart = listState.layoutInfo.viewportStartOffset
        val viewportEnd = listState.layoutInfo.viewportEndOffset
        val top = info.offset + dragOffset
        val bottom = top + info.size
        val edge = 48f
        val delta = when {
            bottom > viewportEnd - edge -> (bottom - (viewportEnd - edge)).coerceAtMost(40f)
            top < viewportStart + edge -> (top - (viewportStart + edge)).coerceAtLeast(-40f)
            else -> 0f
        }
        if (delta != 0f) {
            scope.launch {
                val consumed = listState.scrollBy(delta)
                dragOffset += consumed
            }
        }
    }

    fun end() {
        val key = draggingKey ?: return
        val end = items.indexOfFirst { keyOf(it) == key }
        draggingKey = null
        dragOffset = 0f
        if (startIndex >= 0 && end >= 0 && startIndex != end) onMove(startIndex, end)
        startIndex = -1
    }

    fun isDragging(key: Any) = draggingKey == key
}

@Composable
fun <T> rememberReorderableList(
    source: List<T>,
    listState: LazyListState,
    key: (T) -> Any,
    headerCount: Int = 0,
    onMove: (from: Int, to: Int) -> Unit,
): ReorderableListState<T> {
    val scope = rememberCoroutineScope()
    val state = remember(listState) { ReorderableListState(listState, scope, key, headerCount).also { it.items.addAll(source) } }
    val currentOnMove by rememberUpdatedState(onMove)
    state.onMove = { from, to -> currentOnMove(from, to) }
    LaunchedEffect(source) { state.sync(source) }
    return state
}

/** Applied to the dragged row so it floats above its neighbours. */
fun Modifier.draggedItem(state: ReorderableListState<*>, key: Any): Modifier =
    if (state.isDragging(key)) {
        zIndex(1f)
            .graphicsLayer { translationY = state.dragOffset }
            .shadow(8.dp)
    } else this

@Composable
fun DragHandle(state: ReorderableListState<*>, key: Any, modifier: Modifier = Modifier) {
    Icon(
        Icons.Rounded.DragHandle,
        contentDescription = null, // reordering is exposed through custom accessibility actions
        modifier = modifier.pointerInput(key) {
            detectDragGestures(
                onDragStart = { state.start(key) },
                onDrag = { change, amount ->
                    change.consume()
                    state.drag(amount.y)
                },
                onDragEnd = { state.end() },
                onDragCancel = { state.end() },
            )
        },
    )
}
