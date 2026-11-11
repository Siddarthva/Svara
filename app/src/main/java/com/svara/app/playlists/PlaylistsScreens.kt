package com.svara.app.playlists

import com.svara.app.ui.components.ArtworkImage
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.automirrored.rounded.Notes
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.svara.app.ui.theme.rememberArtworkShape
import com.svara.app.ui.theme.ArtworkTokens
import com.svara.app.ui.artwork.LayeredArtwork
import com.svara.app.ui.artwork.ArtRef
import com.svara.app.ui.states.SkeletonList
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.svara.app.data.portability.Portability
import com.svara.app.di.IoDispatcher
import com.svara.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import com.svara.app.domain.model.Playlist
import com.svara.app.domain.model.PlaylistItem
import com.svara.app.domain.model.SmartPlaylist
import com.svara.app.domain.model.SmartRule
import com.svara.app.domain.model.Song
import com.svara.app.domain.repository.PlaylistRepository
import com.svara.app.domain.repository.SmartPlaylistRepository
import com.svara.app.library.CollectionHeader
import com.svara.app.library.CollectionRow
import com.svara.app.playback.PlaybackController
import com.svara.app.playback.UserMessages
import com.svara.app.ui.components.BackButton
import com.svara.app.ui.components.DragHandle
import com.svara.app.ui.components.EmptyState
import com.svara.app.ui.components.SectionHeader
import com.svara.app.ui.components.SongRow
import com.svara.app.ui.components.WithSongActions
import com.svara.app.ui.components.draggedItem
import com.svara.app.ui.components.formatTotalDuration
import com.svara.app.ui.components.rememberReorderableList
import com.svara.app.ui.components.reorderAccessibility
import com.svara.app.ui.components.songCountLabel
import com.svara.app.ui.navigation.CollectionType
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.theme.LocalReducedMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ImportReport(val name: String, val matched: Int, val unmatched: List<String>)

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PlaylistRepository,
    private val smart: SmartPlaylistRepository,
    private val portability: Portability,
    private val messages: UserMessages,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    val playlists: StateFlow<List<Playlist>?> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val smartPlaylists: StateFlow<List<SmartPlaylist>> = smart.smartPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Each playlist's cover composition: up to four covers, one per album, read off the main thread. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val covers: StateFlow<Map<Long, List<ArtRef>>> = repository.playlists
        .mapLatest { list ->
            list.associate { pl -> pl.id to repository.coverArt(pl.id, ArtworkTokens.MOSAIC_COUNT).map { (song, album) -> ArtRef(song, album) } }
        }
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _report = MutableStateFlow<ImportReport?>(null)
    val report: StateFlow<ImportReport?> = _report.asStateFlow()

    fun create(name: String) {
        if (name.isNotBlank()) viewModelScope.launch { repository.create(name) }
    }

    fun createSmart(name: String, rule: SmartRule, limit: Int) {
        if (name.isNotBlank()) viewModelScope.launch { smart.create(name, rule, limit) }
    }

    fun importM3u(uri: Uri) {
        viewModelScope.launch {
            val file = withContext(io) {
                try {
                    val display = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Imported playlist"
                    val content = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    content?.let { display.substringBeforeLast('.') to it }
                } catch (e: Exception) {
                    // FileNotFound, SecurityException (revoked grant) or an unreadable provider.
                    null
                }
            }
            if (file == null) {
                messages.emit("Couldn't read that playlist file")
                return@launch
            }
            val result = portability.importM3u(file.first, file.second)
            _report.value = ImportReport(file.first, result.matched, result.unmatched)
        }
    }

    fun dismissReport() {
        _report.value = null
    }
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: PlaylistRepository,
    private val playback: PlaybackController,
    private val portability: Portability,
    private val messages: UserMessages,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    /** -1 (no such playlist) shows an empty screen instead of crashing on a bad deep link. */
    private val id: Long = savedState.get<Long>("id") ?: -1L

    val playlist: StateFlow<Playlist?> = repository.observePlaylist(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val items: StateFlow<List<PlaylistItem>?> = repository.observeItems(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun songs() = items.value.orEmpty().map { it.song }

    fun play() = playback.playSongs(songs(), 0, shuffle = false)
    fun playFrom(index: Int) = playback.playSongs(songs(), index)
    fun shuffle() = playback.shuffleAll(songs().map { it.id })
    fun rename(name: String) {
        if (name.isNotBlank()) viewModelScope.launch { repository.rename(id, name) }
    }
    /** Moves the playlist to Recently deleted and offers Undo. */
    fun delete() {
        val name = playlist.value?.name.orEmpty()
        viewModelScope.launch {
            repository.delete(id)
            messages.emit("Moved “$name” to Recently deleted", "Undo") { appScope.launch { repository.restore(id) } }
        }
    }

    /** Renames with Undo back to the old name. */
    fun renameWithUndo(name: String) {
        val old = playlist.value?.name ?: return
        if (name.isBlank() || name.trim() == old) return
        viewModelScope.launch {
            repository.rename(id, name)
            messages.emit("Renamed to “${name.trim()}”", "Undo") { appScope.launch { repository.rename(id, old) } }
        }
    }

    fun setDescription(text: String) {
        viewModelScope.launch { repository.setDescription(id, text) }
    }

    fun setCover(songId: Long?) {
        viewModelScope.launch { repository.setCover(id, songId) }
    }

    fun duplicate(onCreated: (Long) -> Unit) {
        val name = playlist.value?.name ?: return
        viewModelScope.launch {
            val copy = repository.duplicate(id, "$name (copy)")
            if (copy > 0) {
                messages.emit("Made a copy of “$name”")
                onCreated(copy)
            }
        }
    }

    /** Removes a song and offers Undo, which puts it back at the same position. */
    fun remove(item: PlaylistItem) {
        viewModelScope.launch {
            repository.removeEntry(id, item.entryId)
            messages.emit("Removed “${item.song.title}”", "Undo") {
                // App scope: the snackbar may outlive this screen.
                appScope.launch { repository.insertAt(id, item.song.id, item.position) }
            }
        }
    }

    /** [from]/[to] are visible positions; they map to absolute entry positions, which include hidden songs. */
    fun move(from: Int, to: Int) {
        val list = items.value ?: return
        val a = list.getOrNull(from)?.position ?: return
        val b = list.getOrNull(to)?.position ?: return
        viewModelScope.launch { repository.move(id, a, b) }
    }

    fun exportM3u(uri: Uri) {
        viewModelScope.launch {
            val text = portability.exportM3u(id)
            val written = withContext(io) {
                try {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
                } catch (e: Exception) {
                    false
                }
            }
            messages.emit(if (written) "Exported “${playlist.value?.name}”" else "Couldn't save the playlist file")
        }
    }
}

@HiltViewModel
class SmartPlaylistViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: SmartPlaylistRepository,
    private val playback: PlaybackController,
) : ViewModel() {
    /** -1 (no such playlist) shows an empty screen instead of crashing on a bad deep link. */
    private val id: Long = savedState.get<Long>("id") ?: -1L
    val playlist: StateFlow<SmartPlaylist?> = repository.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val songs: StateFlow<List<Song>?> = repository.observeSongs(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun play() = playback.playSongs(songs.value.orEmpty(), 0, shuffle = false)
    fun playFrom(index: Int) = playback.playSongs(songs.value.orEmpty(), index)
    fun shuffle() = playback.shuffleAll(songs.value.orEmpty().map { it.id })
    fun setPinned(pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(id, pinned) }
    }
    fun update(name: String, rule: SmartRule, limit: Int) {
        viewModelScope.launch { repository.update(id, name, rule, limit) }
    }
    fun delete() {
        viewModelScope.launch { repository.delete(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(navigator: Navigator, hideHistory: Boolean, viewModel: PlaylistsViewModel = hiltViewModel()) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val smartPlaylists by viewModel.smartPlaylists.collectAsStateWithLifecycle()
    val covers by viewModel.covers.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var creatingSmart by rememberSaveable { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importM3u) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playlists") },
                actions = {
                    IconButton(onClick = { importer.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "application/x-mpegurl", "*/*")) }) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = "Import M3U playlist")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New playlist") })
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item(key = "built-in") {
                CollectionRow("Favorites", "Songs you've hearted", -1, -1, { navigator.collection(CollectionType.FAVORITES) }, icon = Icons.Rounded.Favorite)
                if (!hideHistory) {
                    CollectionRow("Recently played", "Your listening history", -1, -1, { navigator.collection(CollectionType.RECENTLY_PLAYED) }, icon = Icons.Rounded.History)
                    CollectionRow("Most played", "Your top songs", -1, -1, { navigator.collection(CollectionType.MOST_PLAYED) }, icon = Icons.AutoMirrored.Rounded.TrendingUp)
                }
                CollectionRow("Recently added", "New on this device", -1, -1, { navigator.collection(CollectionType.RECENTLY_ADDED) }, icon = Icons.Rounded.NewReleases)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionHeader("Smart playlists") {
                    var newOpen by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { newOpen = true }) { Text("New") }
                        DropdownMenu(newOpen, onDismissRequest = { newOpen = false }) {
                            DropdownMenuItem(text = { Text("Build with rules") }, onClick = { newOpen = false; navigator.ruleBuilder(0) })
                            DropdownMenuItem(text = { Text("From a ready-made rule") }, onClick = { newOpen = false; creatingSmart = true })
                        }
                    }
                }
            }
            if (smartPlaylists.isEmpty()) {
                item(key = "smart-empty") {
                    Text(
                        "Smart playlists fill themselves from a rule, like “Not played recently”, and update as you listen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            items(smartPlaylists, key = { "s${it.id}" }) { sp ->
                CollectionRow(
                    sp.name,
                    (sp.rules?.let { "Rules" } ?: sp.rule.label) + " • up to ${sp.limit}" + if (sp.pinned) " • pinned" else "",
                    -1, -1, { navigator.smartPlaylist(sp.id) },
                    icon = if (sp.pinned) Icons.Rounded.PushPin else Icons.Rounded.AutoAwesome,
                )
            }
            item(key = "mine") {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionHeader("Your playlists")
            }
            val list = playlists
            when {
                list == null -> item { SkeletonList(rows = 4) }
                list.isEmpty() -> item {
                    EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, "No playlists yet", "Create one here, import an M3U file, or use “Add to playlist” on any song.")
                }
                else -> items(list, key = { it.id }) { playlist ->
                    val art = covers[playlist.id]
                    CollectionRow(
                        title = playlist.name,
                        subtitle = songCountLabel(playlist.songCount),
                        coverSongId = playlist.coverSongId ?: -1,
                        coverAlbumId = playlist.coverAlbumId ?: -1,
                        onClick = { navigator.playlist(playlist.id) },
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        artwork = art?.let { { LayeredArtwork(it, rememberArtworkShape(), Modifier.fillMaxSize()) } },
                    )
                }
            }
            item(key = "deleted") {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                CollectionRow("Recently deleted", "Restore playlists deleted in the last 30 days", -1, -1, { navigator.recentlyDeleted() }, icon = Icons.Rounded.RestoreFromTrash)
            }
            item(key = "fab-space") { Box(Modifier.padding(40.dp)) }
        }
    }

    if (creating) {
        NameDialog("New playlist", "", "Create", onConfirm = { viewModel.create(it); creating = false }, onDismiss = { creating = false })
    }
    if (creatingSmart) {
        SmartPlaylistDialog(
            title = "New smart playlist",
            initialName = "",
            initialRule = SmartRule.NOT_PLAYED_RECENTLY,
            initialLimit = 50,
            hideHistoryRules = hideHistory,
            onConfirm = { name, rule, limit -> viewModel.createSmart(name, rule, limit); creatingSmart = false },
            onDismiss = { creatingSmart = false },
        )
    }
    report?.let { r ->
        AlertDialog(
            onDismissRequest = viewModel::dismissReport,
            title = { Text("Imported “${r.name}”") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("${songCountLabel(r.matched)} matched in your library.")
                    if (r.unmatched.isNotEmpty()) {
                        Text("${r.unmatched.size} not found on this device:", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall)
                        r.unmatched.take(50).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        if (r.unmatched.size > 50) Text("…and ${r.unmatched.size - 50} more", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissReport) { Text("OK") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(navigator: Navigator, viewModel: PlaylistDetailViewModel = hiltViewModel()) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var describing by rememberSaveable { mutableStateOf(false) }
    var choosingCover by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableList(items.orEmpty(), listState, key = PlaylistItem::entryId, headerCount = 1, onMove = viewModel::move)
    val reducedMotion = LocalReducedMotion.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri -> uri?.let(viewModel::exportM3u) }

    WithSongActions(navigator) { actions ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(playlist?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { BackButton(navigator::back) },
                    actions = {
                        var open by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist options") }
                            DropdownMenu(open, onDismissRequest = { open = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { open = false; renaming = true })
                                DropdownMenuItem(text = { Text("Description") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, null) }, onClick = { open = false; describing = true })
                                DropdownMenuItem(text = { Text("Cover") }, leadingIcon = { Icon(Icons.Rounded.Image, null) }, onClick = { open = false; choosingCover = true })
                                DropdownMenuItem(
                                    text = { Text("Duplicate") },
                                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                    onClick = { open = false; viewModel.duplicate { navigator.playlist(it) } },
                                )
                                DropdownMenuItem(
                                    text = { Text("Export as M3U") },
                                    leadingIcon = { Icon(Icons.Rounded.FileUpload, null) },
                                    onClick = { open = false; exporter.launch("${playlist?.name ?: "playlist"}.m3u8") },
                                )
                                DropdownMenuItem(text = { Text("Delete playlist") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { open = false; confirmDelete = true })
                            }
                        }
                    },
                )
            },
        ) { padding ->
            val list = items
            if (list == null) {
                SkeletonList(Modifier.padding(padding))
                return@Scaffold
            }
            LazyColumn(state = listState, modifier = Modifier.padding(padding).fillMaxSize()) {
                item(key = "header") {
                    CollectionHeader(
                        title = playlist?.name.orEmpty(),
                        subtitle = "${songCountLabel(list.size)} • ${formatTotalDuration(list.sumOf { it.song.durationMs })}",
                        coverSongId = list.firstOrNull()?.song?.id ?: -1,
                        coverAlbumId = list.firstOrNull()?.song?.albumId ?: -1,
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        showCover = true,
                        artwork = {
                            val p = playlist
                            if (p != null && p.chosenCover && p.coverSongId != null) {
                                ArtworkImage(p.coverSongId, p.coverAlbumId ?: -1, Modifier.fillMaxSize(), shape = rememberArtworkShape(), fallbackLabel = p.name)
                            } else {
                                val art = remember(list) { list.asSequence().map { ArtRef(it.song.id, it.song.albumId) }.distinctBy { it.albumId }.take(ArtworkTokens.MOSAIC_COUNT).toList() }
                                LayeredArtwork(art, rememberArtworkShape(), Modifier.fillMaxSize(), label = p?.name)
                            }
                        },
                        canPlay = list.isNotEmpty(),
                        onPlay = viewModel::play,
                        onShuffle = viewModel::shuffle,
                    )
                    playlist?.description?.let { d ->
                        Text(d, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                    if (list.isEmpty()) {
                        EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, "This playlist is empty", "Use “Add to playlist” on any song or selection.")
                    }
                }
                itemsIndexed(reorder.items, key = { _, item -> item.entryId }) { index, item ->
                    SongRow(
                        song = item.song,
                        actions = actions,
                        onClick = { viewModel.playFrom(index) },
                        leading = { DragHandle(reorder, item.entryId, Modifier.padding(end = 8.dp)) },
                        modifier = (if (reorder.isDragging(item.entryId) || reducedMotion) Modifier else Modifier.animateItem())
                            .draggedItem(reorder, item.entryId)
                            .background(MaterialTheme.colorScheme.surface)
                            .reorderAccessibility(index, reorder.items.lastIndex, viewModel::move),
                        extraMenuItems = { dismiss ->
                            DropdownMenuItem(
                                text = { Text("Remove from playlist") },
                                leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) },
                                onClick = { viewModel.remove(item); dismiss() },
                            )
                        },
                    )
                }
            }
        }
    }

    if (renaming) {
        NameDialog("Rename playlist", playlist?.name.orEmpty(), "Save", onConfirm = { viewModel.renameWithUndo(it); renaming = false }, onDismiss = { renaming = false })
    }
    if (describing) {
        DescriptionDialog(playlist?.description.orEmpty(), onConfirm = { viewModel.setDescription(it); describing = false }, onDismiss = { describing = false })
    }
    if (choosingCover) {
        CoverPickerDialog(
            items.orEmpty(), chosen = playlist?.takeIf { it.chosenCover }?.coverSongId,
            onPick = { viewModel.setCover(it); choosingCover = false }, onDismiss = { choosingCover = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${playlist?.name}”?") },
            text = { Text("It moves to Recently deleted for 30 days, so you can restore it. Your songs stay on the device.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(); confirmDelete = false; navigator.back() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartPlaylistScreen(navigator: Navigator, viewModel: SmartPlaylistViewModel = hiltViewModel()) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val sp = playlist

    WithSongActions(navigator) { actions ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(sp?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { BackButton(navigator::back) },
                    actions = {
                        if (sp != null) {
                            IconButton(onClick = { viewModel.setPinned(!sp.pinned) }) {
                                Icon(Icons.Rounded.PushPin, contentDescription = if (sp.pinned) "Unpin: resume live updates" else "Pin: freeze current songs",
                                    tint = if (sp.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { if (sp.rules != null) navigator.ruleBuilder(sp.id) else editing = true }) { Icon(Icons.Rounded.Edit, contentDescription = "Edit rule") }
                            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete smart playlist") }
                        }
                    },
                )
            },
        ) { padding ->
            val list = songs
            if (sp == null || list == null) {
                SkeletonList(Modifier.padding(padding))
                return@Scaffold
            }
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item(key = "header") {
                    CollectionHeader(
                        title = sp.name,
                        subtitle = "${songCountLabel(list.size)} • ${formatTotalDuration(list.sumOf { it.durationMs })}",
                        coverSongId = list.firstOrNull()?.id ?: -1,
                        coverAlbumId = list.firstOrNull()?.albumId ?: -1,
                        icon = Icons.Rounded.AutoAwesome,
                        showCover = true,
                        canPlay = list.isNotEmpty(),
                        onPlay = viewModel::play,
                        onShuffle = viewModel::shuffle,
                    )
                    Text(
                        "Rule: ${sp.rules?.summary() ?: sp.rule.description} Up to ${sp.limit} songs. " +
                            if (sp.pinned) "Pinned — the list is frozen until you unpin it." else "Updates automatically as your library and listening change.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    if (list.isEmpty()) EmptyState(Icons.Rounded.AutoAwesome, "Nothing matches yet", "Keep listening — this list fills itself as the rule starts to match.")
                }
                itemsIndexed(list, key = { _, s -> s.id }) { index, song ->
                    SongRow(song = song, actions = actions, onClick = { viewModel.playFrom(index) })
                }
            }
        }
    }
    if (editing && sp != null) {
        SmartPlaylistDialog(
            title = "Edit smart playlist", initialName = sp.name, initialRule = sp.rule, initialLimit = sp.limit, hideHistoryRules = false,
            onConfirm = { name, rule, limit -> viewModel.update(name, rule, limit); editing = false },
            onDismiss = { editing = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${sp?.name}”?") },
            text = { Text("Only the rule is removed. Your songs stay on the device.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(); confirmDelete = false; navigator.back() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SmartPlaylistDialog(
    title: String,
    initialName: String,
    initialRule: SmartRule,
    initialLimit: Int,
    hideHistoryRules: Boolean,
    onConfirm: (String, SmartRule, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var rule by rememberSaveable { mutableStateOf(initialRule) }
    var limit by rememberSaveable { mutableIntStateOf(initialLimit) }
    val rules = SmartRule.entries.filter { !hideHistoryRules || (it != SmartRule.RECENTLY_PLAYED && it != SmartRule.MOST_PLAYED) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Rule", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                rules.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = r == rule, role = Role.RadioButton, onClick = { rule = r }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = r == rule, onClick = null)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(r.label)
                            Text(r.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("Maximum songs", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                Row {
                    SmartPlaylist.LIMITS.forEach { l ->
                        FilterChip(selected = limit == l, onClick = { limit = l }, label = { Text("$l") }, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, rule, limit) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name) }),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
