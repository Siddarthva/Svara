package com.svara.app.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.svara.app.data.repository.RECENTLY_DELETED_MS
import com.svara.app.domain.model.Playlist
import com.svara.app.domain.model.PlaylistItem
import com.svara.app.domain.model.SmartPlaylist
import com.svara.app.domain.model.Song
import com.svara.app.domain.playlist.SmartCondition
import com.svara.app.domain.playlist.SmartField
import com.svara.app.domain.playlist.SmartMatch
import com.svara.app.domain.playlist.SmartRuleEngine
import com.svara.app.domain.playlist.SmartRules
import com.svara.app.domain.playlist.SmartSort
import com.svara.app.domain.repository.PlaylistRepository
import com.svara.app.domain.repository.SmartPlaylistRepository
import com.svara.app.playback.UserMessages
import com.svara.app.ui.components.ArtworkImage
import com.svara.app.ui.components.BackButton
import com.svara.app.ui.components.EmptyState
import com.svara.app.ui.components.songCountLabel
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.states.SkeletonList
import com.svara.app.ui.theme.Spacing
import com.svara.app.ui.theme.StateTokens
import com.svara.app.ui.theme.rememberArtworkShape
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

// region Rule builder

/** Editable state of a rule-builder playlist. */
data class RuleDraft(
    val name: String = "",
    val rules: SmartRules = SmartRules(conditions = listOf(SmartCondition(SmartField.ADDED_WITHIN_DAYS, "30"))),
    val limit: Int = SmartPlaylist.LIMITS[2],
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RuleBuilderViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: SmartPlaylistRepository,
    private val messages: UserMessages,
) : ViewModel() {
    /** 0 creates a new playlist. */
    private val id: Long = savedState.get<Long>("id") ?: 0L
    val isNew: Boolean get() = id <= 0

    private val _draft = MutableStateFlow(RuleDraft())
    val draft: StateFlow<RuleDraft> = _draft.asStateFlow()
    private var loaded = false

    /** Live preview: re-evaluated off the main thread (debounced) whenever the draft or the library changes. */
    val preview: StateFlow<List<Song>?> = _draft.flatMapLatest { repository.preview(it.rules, it.limit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _saved = MutableStateFlow<Long?>(null)
    val saved: StateFlow<Long?> = _saved.asStateFlow()

    init {
        if (!isNew) viewModelScope.launch {
            repository.observe(id).collect { sp ->
                if (sp != null && !loaded) {
                    loaded = true
                    _draft.value = RuleDraft(sp.name, sp.rules ?: SmartRules(), sp.limit)
                }
            }
        }
    }

    fun setName(name: String) = _draft.update { it.copy(name = name) }
    fun setMatch(match: SmartMatch) = _draft.update { it.copy(rules = it.rules.copy(match = match)) }
    fun setSort(sort: SmartSort) = _draft.update { it.copy(rules = it.rules.copy(sort = sort)) }
    fun setLimit(limit: Int) = _draft.update { it.copy(limit = limit) }
    fun addCondition(field: SmartField) = _draft.update {
        val default = when (field.kind) { SmartField.Kind.NUMBER -> "1"; else -> "" }
        it.copy(rules = it.rules.copy(conditions = it.rules.conditions + SmartCondition(field, default)))
    }
    fun setValue(index: Int, value: String) = _draft.update {
        it.copy(rules = it.rules.copy(conditions = it.rules.conditions.mapIndexed { i, c -> if (i == index) c.copy(value = value) else c }))
    }
    fun removeCondition(index: Int) = _draft.update {
        it.copy(rules = it.rules.copy(conditions = it.rules.conditions.filterIndexed { i, _ -> i != index }))
    }

    fun save() {
        val d = _draft.value
        if (d.name.isBlank()) return
        viewModelScope.launch {
            val savedId = if (isNew) repository.createWithRules(d.name, d.rules, d.limit) else { repository.updateRules(id, d.name, d.rules, d.limit); id }
            messages.emit(if (isNew) "Created “${d.name.trim()}”" else "Saved “${d.name.trim()}”")
            _saved.value = savedId
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleBuilderScreen(navigator: Navigator, viewModel: RuleBuilderViewModel = hiltViewModel()) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    if (saved != null) {
        androidx.compose.runtime.LaunchedEffect(saved) { navigator.back() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "New rule playlist" else "Edit rules") },
                navigationIcon = { BackButton(navigator::back) },
                actions = { TextButton(onClick = viewModel::save, enabled = draft.name.isNotBlank()) { Text("Save") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item(key = "name") {
                OutlinedTextField(
                    value = draft.name, onValueChange = viewModel::setName, label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
                )
                Row(Modifier.padding(horizontal = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    SmartMatch.entries.forEach { m -> FilterChip(selected = draft.rules.match == m, onClick = { viewModel.setMatch(m) }, label = { Text(m.label) }) }
                }
            }
            itemsIndexed(draft.rules.conditions, key = { i, c -> "$i-${c.field}" }) { index, condition ->
                ConditionRow(condition, onValue = { viewModel.setValue(index, it) }, onRemove = { viewModel.removeCondition(index) })
            }
            item(key = "add") {
                var open by remember { mutableStateOf(false) }
                Row(Modifier.padding(horizontal = Spacing.l)) {
                    OutlinedButton(onClick = { open = true }, modifier = Modifier.heightIn(min = Spacing.touch)) {
                        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(Spacing.s)); Text("Add rule")
                    }
                    DropdownMenu(open, onDismissRequest = { open = false }) {
                        SmartField.entries.forEach { f -> DropdownMenuItem(text = { Text(f.label) }, onClick = { open = false; viewModel.addCondition(f) }) }
                    }
                }
                if (draft.rules.conditions.isEmpty()) Note("No rules: every song matches, in the order below.")
                SectionTitle("Order")
                Row(Modifier.padding(horizontal = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    var sortOpen by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { sortOpen = true }, modifier = Modifier.heightIn(min = Spacing.touch)) { Text(draft.rules.sort.label) }
                    DropdownMenu(sortOpen, onDismissRequest = { sortOpen = false }) {
                        SmartSort.entries.forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { sortOpen = false; viewModel.setSort(s) }) }
                    }
                }
                SectionTitle("Maximum songs")
                Row(Modifier.padding(horizontal = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    SmartPlaylist.LIMITS.forEach { n -> FilterChip(selected = draft.limit == n, onClick = { viewModel.setLimit(n) }, label = { Text("$n") }) }
                }
                HorizontalDivider(Modifier.padding(top = Spacing.l))
                val list = preview
                SectionTitle(if (list == null) "Preview" else "Preview: ${songCountLabel(list.size)}")
            }
            val list = preview
            when {
                list == null -> item(key = "loading") { SkeletonList(rows = 3) }
                list.isEmpty() -> item(key = "none") { Note("Nothing matches these rules yet.") }
                else -> items(list.take(PREVIEW_ROWS), key = { it.id }) { song -> PreviewRow(song) }
            }
            if ((list?.size ?: 0) > PREVIEW_ROWS) item(key = "more") { Note("…and ${list!!.size - PREVIEW_ROWS} more.") }
        }
    }
}

@Composable
private fun ConditionRow(condition: SmartCondition, onValue: (String) -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.xs, top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        when (condition.field.kind) {
            SmartField.Kind.FLAG -> Text(condition.field.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            else -> OutlinedTextField(
                value = condition.value,
                onValueChange = onValue,
                label = { Text(condition.field.label) },
                singleLine = true,
                isError = !SmartRuleEngine.usable(condition),
                keyboardOptions = KeyboardOptions(keyboardType = if (condition.field.kind == SmartField.Kind.NUMBER) KeyboardType.Number else KeyboardType.Text),
                modifier = Modifier.weight(1f),
            )
        }
        IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = "Remove rule: ${condition.field.label}") }
    }
}

@Composable
private fun PreviewRow(song: Song) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.touch).padding(horizontal = Spacing.l, vertical = Spacing.xs)
            .semantics(mergeDescendants = true) { contentDescription = "${song.title}, ${song.displayArtist}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(song.id, song.albumId, Modifier.size(StateTokens.rowArtwork), shape = rememberArtworkShape())
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.displayArtist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s).semantics { heading() })
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs))
}

/** One-line summary of rules for list rows and the playlist page. */
fun SmartRules.summary(): String {
    val usable = conditions.filter { SmartRuleEngine.usable(it) }
    val parts = usable.map { c ->
        when (c.field.kind) {
            SmartField.Kind.FLAG -> c.field.label.lowercase()
            else -> "${c.field.label.lowercase()} ${c.value.trim()}"
        }
    }
    val joined = if (parts.isEmpty()) "All songs" else parts.joinToString(if (match == SmartMatch.ALL) ", and " else ", or ").replaceFirstChar { it.uppercase() }
    return "$joined. Order: ${sort.label.lowercase()}."
}

private const val PREVIEW_ROWS = 25

// endregion

// region Recently deleted

@HiltViewModel
class RecentlyDeletedViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val messages: UserMessages,
) : ViewModel() {
    val deleted: StateFlow<List<Playlist>?> = repository.deleted.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun restore(p: Playlist) {
        viewModelScope.launch { repository.restore(p.id); messages.emit("Restored “${p.name}”") }
    }

    fun purge(p: Playlist) {
        viewModelScope.launch { repository.purge(p.id); messages.emit("Deleted “${p.name}” for good") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyDeletedScreen(navigator: Navigator, viewModel: RecentlyDeletedViewModel = hiltViewModel()) {
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Playlist?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("Recently deleted") }, navigationIcon = { BackButton(navigator::back) }) }) { padding ->
        val list = deleted
        when {
            list == null -> SkeletonList(Modifier.padding(padding))
            list.isEmpty() -> EmptyState(
                Icons.Rounded.RestoreFromTrash, "Nothing here", "Deleted playlists stay here for 30 days, then they're removed for good.",
                Modifier.padding(padding),
            )
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item(key = "note") { Note("Playlists stay here for 30 days. Your music files are never affected.") }
                items(list, key = { it.id }) { p ->
                    val daysLeft = ((p.deletedAt ?: 0) + RECENTLY_DELETED_MS - System.currentTimeMillis()).coerceAtLeast(0) / DAY_MS
                    Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touch).padding(start = Spacing.l, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null)
                        Spacer(Modifier.width(Spacing.l))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${songCountLabel(p.songCount)} • deleted ${p.deletedAt?.let { DateFormat.getDateInstance().format(Date(it)) }.orEmpty()} • $daysLeft days left",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.restore(p) }) { Icon(Icons.Rounded.RestoreFromTrash, contentDescription = "Restore ${p.name}") }
                        IconButton(onClick = { confirm = p }) { Icon(Icons.Rounded.DeleteForever, contentDescription = "Delete ${p.name} for good") }
                    }
                }
            }
        }
    }
    confirm?.let { p ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Delete “${p.name}” for good?") },
            text = { Text("This can't be undone. The songs stay in your library.") },
            confirmButton = { TextButton(onClick = { viewModel.purge(p); confirm = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

private const val DAY_MS = 86_400_000L

// endregion

// region Playlist dialogs

@Composable
fun DescriptionDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Description") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it.take(MAX_DESCRIPTION) }, minLines = 2, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val MAX_DESCRIPTION = 500

/** Pick any song in the playlist for its cover, or go back to the automatic composition. */
@Composable
fun CoverPickerDialog(items: List<PlaylistItem>, chosen: Long?, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    val songs = remember(items) { items.map { it.song }.distinctBy { it.albumId } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playlist cover") },
        text = {
            LazyColumn {
                item(key = "auto") {
                    Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touch).clickable { onPick(null) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = chosen == null, onClick = { onPick(null) })
                        Text("Automatic (from the songs)")
                    }
                }
                items(songs, key = { it.id }) { song ->
                    Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touch).clickable { onPick(song.id) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = chosen == song.id, onClick = { onPick(song.id) })
                        ArtworkImage(song.id, song.albumId, Modifier.size(StateTokens.rowArtwork), shape = rememberArtworkShape())
                        Spacer(Modifier.width(Spacing.m))
                        Text(song.displayAlbum, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

// endregion
