package com.svara.app.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.svara.app.data.artwork.Artwork
import com.svara.app.data.artwork.ArtworkLoader
import com.svara.app.di.IoDispatcher
import com.svara.app.domain.library.DoctorIssue
import com.svara.app.domain.library.LibraryDoctor
import com.svara.app.domain.library.MetadataField
import com.svara.app.domain.model.DuplicateGroup
import com.svara.app.domain.model.Song
import com.svara.app.domain.repository.LibraryRepository
import com.svara.app.domain.repository.MetadataFix
import com.svara.app.playback.UserMessages
import com.svara.app.ui.components.ArtworkImage
import com.svara.app.ui.components.BackButton
import com.svara.app.ui.components.EmptyState
import com.svara.app.ui.components.formatBytes
import com.svara.app.ui.components.formatDuration
import com.svara.app.ui.states.ErrorState
import com.svara.app.ui.states.SkeletonList
import com.svara.app.ui.theme.rememberArtworkShape
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class LibraryCheckUi(
    val loading: Boolean = true,
    val duplicates: List<DuplicateGroup> = emptyList(),
    val missing: List<Song> = emptyList(),
    val issues: List<DoctorIssue> = emptyList(),
    /** One song per album that has no artwork at all (custom, embedded or MediaStore). */
    val noArtwork: List<Song> = emptyList(),
    val error: String? = null,
)

enum class DoctorSection(val label: String) {
    FIXES("Fixes"), ARTWORK("Missing artwork"), DUPLICATES("Duplicates"), MISSING("Missing files"), APPLIED("Applied")
}

/**
 * Library Doctor. Findings are computed off the main thread; every change is previewed (before →
 * after), approved per item, stored as a SVARĀ override (never written to the audio file) and can
 * be undone. Duplicates and missing files are review-only: nothing is deleted or moved.
 */
@HiltViewModel
class LibraryCheckViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val artwork: ArtworkLoader,
    private val messages: UserMessages,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _ui = MutableStateFlow(LibraryCheckUi())
    val ui: StateFlow<LibraryCheckUi> = _ui.asStateFlow()

    val applied: StateFlow<List<MetadataFix>> = library.metadataFixes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        load()
    }

    fun load() {
        _ui.value = LibraryCheckUi()
        viewModelScope.launch {
            _ui.value = try {
                coroutineScope {
                    val dups = async { library.duplicates() }
                    val missing = async { library.missingFiles() }
                    val songs = library.visibleSongs()
                    val issues = async(io) { LibraryDoctor.findIssues(songs) }
                    val art = async(io) {
                        songs.groupBy { it.albumId }.values.map { it.first() }
                            .filter { s -> artwork.load(Artwork(s.id, s.albumId), PROBE_PX) == null }
                    }
                    LibraryCheckUi(false, dups.await(), missing.await(), issues.await(), art.await())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LibraryCheckUi(loading = false, error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Applies one approved change; the finding disappears and Undo is offered. */
    fun apply(issue: DoctorIssue, value: String) {
        viewModelScope.launch {
            if (library.applyMetadataFix(issue.song.id, issue.field, value)) {
                _ui.update { u -> u.copy(issues = u.issues.filterNot { it.key == issue.key }) }
                messages.emit("${issue.field.label} changed to “${value.trim()}”", "Undo") { undo(issue.song.id, issue.field) }
            }
        }
    }

    fun undo(songId: Long, field: MetadataField) {
        viewModelScope.launch {
            if (library.undoMetadataFix(songId, field)) {
                messages.emit("${field.label} restored")
                load()
            }
        }
    }

    private companion object {
        const val PROBE_PX = 64
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryCheckScreen(navigator: com.svara.app.ui.navigation.Navigator, viewModel: LibraryCheckViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val applied by viewModel.applied.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf(DoctorSection.FIXES) }
    var editing by remember { mutableStateOf<DoctorIssue?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("Library Doctor") }, navigationIcon = { BackButton(navigator::back) }) }) { padding ->
        if (ui.loading) {
            SkeletonList(Modifier.padding(padding))
            return@Scaffold
        }
        ui.error?.let { error ->
            ErrorState("Couldn't check your library", "SVARĀ couldn't read the library ($error). Your music and playlists are unchanged.", viewModel::load, Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item(key = "intro") {
                Text(
                    "Corrections are saved in SVARĀ and shown everywhere in the app; your audio files are never changed. Duplicates and missing files are for review only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DoctorSection.entries) { s ->
                        val count = when (s) {
                            DoctorSection.FIXES -> ui.issues.size
                            DoctorSection.ARTWORK -> ui.noArtwork.size
                            DoctorSection.DUPLICATES -> ui.duplicates.size
                            DoctorSection.MISSING -> ui.missing.size
                            DoctorSection.APPLIED -> applied.size
                        }
                        FilterChip(selected = section == s, onClick = { section = s }, label = { Text("${s.label} ($count)") })
                    }
                }
            }
            when (section) {
                DoctorSection.FIXES -> {
                    if (ui.issues.isEmpty()) item(key = "f-empty") { EmptyState(Icons.AutoMirrored.Rounded.FactCheck, "Nothing to fix", "No inconsistent artist names, title problems or missing tags.") }
                    items(ui.issues, key = { it.key }) { issue -> IssueRow(issue, onApply = { viewModel.apply(issue, it) }, onEdit = { editing = issue }) }
                }
                DoctorSection.ARTWORK -> {
                    if (ui.noArtwork.isEmpty()) item(key = "a-empty") { EmptyState(Icons.AutoMirrored.Rounded.FactCheck, "Every album has artwork", "Albums without art show a colour and a music symbol.") }
                    items(ui.noArtwork, key = { "a${it.albumId}" }) { song ->
                        ListItem(
                            leadingContent = { ArtworkImage(song.id, song.albumId, Modifier.size(48.dp), shape = rememberArtworkShape()) },
                            headlineContent = { Text(song.displayAlbum) },
                            supportingContent = { Text(song.displayArtist) },
                            trailingContent = { TextButton(onClick = { navigator.changeCover(song.id, song.albumId) }) { Text("Add cover") } },
                        )
                    }
                }
                DoctorSection.DUPLICATES -> {
                    if (ui.duplicates.isEmpty()) item(key = "d-empty") { EmptyState(Icons.AutoMirrored.Rounded.FactCheck, "No duplicates", "No songs share a title, artist and length.") }
                    items(ui.duplicates, key = { g -> "d" + g.songs.joinToString { it.id.toString() } }) { group ->
                        val first = group.songs.first()
                        ListItem(
                            leadingContent = { Icon(if (group.identicalSize) Icons.Rounded.FileCopy else Icons.Rounded.ContentCopy, null) },
                            headlineContent = { Text("${first.title} — ${first.displayArtist}") },
                            supportingContent = {
                                Text(
                                    (if (group.identicalSize) "Same title, artist, length and file size — likely exact copies\n" else "Same title, artist and length — may be different quality\n") +
                                        group.songs.joinToString("\n") { "• ${it.relativePath}${it.fileName} (${formatBytes(it.sizeBytes)}, ${formatDuration(it.durationMs)})" }
                                )
                            },
                        )
                        HorizontalDivider()
                    }
                }
                DoctorSection.MISSING -> {
                    if (ui.missing.isEmpty()) item(key = "m-empty") { EmptyState(Icons.AutoMirrored.Rounded.FactCheck, "No missing files", "Every song in the library can be read.") }
                    items(ui.missing, key = { "m${it.id}" }) { song ->
                        ListItem(
                            leadingContent = { Icon(Icons.Rounded.LinkOff, null) },
                            headlineContent = { Text(song.title) },
                            supportingContent = { Text("${song.dataPath ?: song.relativePath + song.fileName}\nRemoved from the library on the next scan.") },
                        )
                    }
                }
                DoctorSection.APPLIED -> {
                    if (applied.isEmpty()) item(key = "ap-empty") { EmptyState(Icons.AutoMirrored.Rounded.FactCheck, "No corrections yet", "Changes you apply are listed here so you can undo them.") }
                    items(applied, key = { "ap${it.songId}${it.field}" }) { fix ->
                        ListItem(
                            headlineContent = { Text("${fix.field.label}: “${fix.value}”") },
                            supportingContent = { Text("Was “${fix.original.ifBlank { "(empty)" }}” in the file") },
                            trailingContent = { TextButton(onClick = { viewModel.undo(fix.songId, fix.field) }) { Text("Undo") } },
                        )
                    }
                }
            }
            item(key = "end") { Spacer(Modifier.height(24.dp)) }
        }
    }
    editing?.let { issue ->
        FixDialog(issue, onApply = { viewModel.apply(issue, it); editing = null }, onDismiss = { editing = null })
    }
}

@Composable
private fun IssueRow(issue: DoctorIssue, onApply: (String) -> Unit, onEdit: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(issue.kind.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            "${issue.song.relativePath}${issue.song.fileName}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        // Before → after preview.
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {
                contentDescription = "${issue.field.label}: ${issue.current.ifBlank { "empty" }} becomes ${issue.proposed ?: "a value you type"}"
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(issue.current.ifBlank { "(empty)" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                issue.proposed ?: "Type a value",
                style = MaterialTheme.typography.bodyLarge,
                color = if (issue.proposed != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            issue.proposed?.let { p -> Button(onClick = { onApply(p) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Apply") } }
            OutlinedButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (issue.proposed == null) "Enter ${issue.field.label.lowercase()}" else "Edit") }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun FixDialog(issue: DoctorIssue, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf(issue.proposed ?: issue.current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${issue.field.label} for “${issue.song.title.ifBlank { issue.song.fileName }}”") },
        text = {
            Column {
                Text("Now: ${issue.current.ifBlank { "(empty)" }}", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("New ${issue.field.label.lowercase()}") }, singleLine = true)
                Text("Saved in SVARĀ only; the audio file isn't changed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onApply(value) }, enabled = value.isNotBlank() && value.trim() != issue.current) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
