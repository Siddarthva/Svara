package com.svara.app.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svara.app.playback.QueueEntry
import com.svara.app.ui.components.ArtworkImage
import com.svara.app.ui.components.BackButton
import com.svara.app.ui.components.DragHandle
import com.svara.app.ui.components.EmptyState
import com.svara.app.ui.components.draggedItem
import com.svara.app.ui.components.formatDuration
import com.svara.app.ui.components.rememberReorderableList
import com.svara.app.ui.components.reorderAccessibility
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.theme.LocalReducedMotion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(navigator: Navigator, viewModel: QueueViewModel = hiltViewModel(), onClose: () -> Unit = navigator::back) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val undoLabel by viewModel.undoLabel.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val reducedMotion = LocalReducedMotion.current
    val reorder = rememberReorderableList(state.queue, listState, key = QueueEntry::key, onMove = viewModel::move)

    LaunchedEffect(Unit) {
        if (state.currentPosition > 0) listState.scrollToItem(state.currentPosition)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Queue")
                        if (state.queue.isNotEmpty()) {
                            Text(
                                "${state.currentPosition + 1} of ${state.queue.size}" + if (state.shuffleEnabled) " • shuffled" else "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { BackButton(onClose) },
                actions = {
                    if (undoLabel != null) {
                        IconButton(onClick = viewModel::undo) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo: $undoLabel")
                        }
                    }
                    if (state.queue.isNotEmpty()) {
                        IconButton(onClick = { saving = true }) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Save queue as playlist")
                        }
                    }
                    if (state.queue.size > 1) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Rounded.ClearAll, contentDescription = "Clear queue")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.queue.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                title = "The queue is empty",
                message = "Songs you play or add to the queue appear here.",
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            return@Scaffold
        }
        LazyColumn(state = listState, modifier = Modifier.padding(padding).fillMaxSize()) {
            itemsIndexed(reorder.items, key = { _, entry -> entry.key }) { index, entry ->
                val isCurrent = entry.key == state.current?.key
                val rowModifier = if (reorder.isDragging(entry.key) || reducedMotion) Modifier else Modifier.animateItem()
                QueueRow(
                    entry = entry,
                    isCurrent = isCurrent,
                    onClick = { viewModel.skipTo(index) },
                    onRemove = { viewModel.remove(index) },
                    dragHandle = { DragHandle(reorder, entry.key, Modifier.padding(12.dp)) },
                    modifier = rowModifier
                        .draggedItem(reorder, entry.key)
                        .background(MaterialTheme.colorScheme.surface)
                        .reorderAccessibility(index, reorder.items.lastIndex, viewModel::move),
                )
            }
        }
    }

    if (saving) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text("Save queue as playlist") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Playlist name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { viewModel.saveAsPlaylist(name); saving = false }, enabled = name.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { saving = false }) { Text("Cancel") } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear queue?") },
            text = { Text((if (state.isPlaying) "Everything except the current song will be removed." else "All songs will be removed from the queue.") + " Saved playlists aren't affected, and you can undo this.") },
            confirmButton = { TextButton(onClick = { viewModel.clear(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun QueueRow(
    entry: QueueEntry,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = "Play", onClick = onClick)
            .semantics { if (isCurrent) stateDescription = "Now playing" }
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dragHandle()
        ArtworkImage(entry.songId, entry.albumId, Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else null,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${entry.artist} • ${formatDuration(entry.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp))
        } else {
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove ${entry.title} from queue")
            }
        }
    }
}
