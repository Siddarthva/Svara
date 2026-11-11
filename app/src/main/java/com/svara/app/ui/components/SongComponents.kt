package com.svara.app.ui.components

import androidx.compose.material.icons.rounded.Info
import com.svara.app.ui.theme.LocalPresetUi
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.rounded.Check
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.rounded.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svara.app.domain.model.Playlist
import com.svara.app.domain.model.Song
import com.svara.app.ui.SongActionsViewModel
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.presets.PresetMenuItems
import com.svara.app.ui.presets.PresetRowActions
import com.svara.app.ui.theme.LocalAppearance
import com.svara.app.ui.theme.rememberArtworkShape

/** Everything a song row's menu can do. Built once per screen by [WithSongActions]. */
@Stable
class SongActions internal constructor(
    val favoriteIds: Set<Long>,
    private val vm: SongActionsViewModel,
    private val navigator: Navigator,
    private val openPlaylistPicker: (List<Long>, Boolean) -> Unit,
) {
    fun playNext(song: Song) = vm.playNext(listOf(song.id), song.title)
    fun playNext(ids: List<Long>) = vm.playNext(ids, null)
    fun addToQueue(song: Song) = vm.addToQueue(listOf(song.id), song.title)
    fun addToQueue(ids: List<Long>) = vm.addToQueue(ids, null)
    fun addToPlaylist(ids: List<Long>) = openPlaylistPicker(ids, false)
    /** Skips the picker and asks for a name straight away. */
    fun newPlaylistWith(ids: List<Long>) = openPlaylistPicker(ids, true)
    fun toggleFavorite(songId: Long) = vm.toggleFavorite(songId)
    /** Bulk: heart all when any isn't a favourite yet, otherwise un-heart all. */
    fun toggleFavorites(ids: List<Long>) = vm.setFavorites(ids, favourite = ids.any { it !in favoriteIds })
    fun goToAlbum(song: Song) = navigator.album(song.albumId)
    fun goToArtist(song: Song) = navigator.artist(song.displayArtist)
    fun changeCover(song: Song) = navigator.changeCover(song.id, song.albumId)
}

/**
 * Provides [SongActions] for a screen and hosts the "Add to playlist" dialog they open.
 */
@Composable
fun WithSongActions(navigator: Navigator, content: @Composable (SongActions) -> Unit) {
    val vm: SongActionsViewModel = hiltViewModel()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val playlists by vm.allPlaylists.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<Pair<List<Long>, Boolean>?>(null) }

    val actions = remember(favoriteIds, vm, navigator) {
        SongActions(favoriteIds, vm, navigator, openPlaylistPicker = { ids, create -> pending = ids to create })
    }
    content(actions)

    pending?.let { (ids, create) ->
        AddToPlaylistDialog(
            songCount = ids.size,
            playlists = playlists,
            startCreating = create,
            onPick = { vm.addToPlaylist(it, ids); pending = null },
            onCreate = { vm.createPlaylistAndAdd(it, ids); pending = null },
            onDismiss = { pending = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: SongActions? = null,
    isCurrent: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    showArtwork: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    extraMenuItems: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    val appearance = LocalAppearance.current
    // Typography Studio density: compact rows drop the album and tighten spacing, never below 48 dp.
    val compact = appearance.compact
    val subtitle = if (compact) song.displayArtist else "${song.displayArtist} • ${song.displayAlbum}"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 48.dp else 64.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = if (onLongClick != null) "Select" else null,
            )
            .semantics {
                if (selectionMode) stateDescription = if (selected) "Selected" else "Not selected"
                else if (isCurrent) stateDescription = "Now playing"
            }
            .padding(start = 16.dp, end = 4.dp, top = if (compact) 2.dp else 6.dp, bottom = if (compact) 2.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        leading?.invoke()
        if (showArtwork) {
            Box {
                ArtworkImage(song.id, song.albumId, Modifier.size(if (compact) 40.dp else 48.dp), shape = rememberArtworkShape())
                if (isCurrent) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.BottomEnd).size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else null,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (song.missingTags) {
            // Library Doctor can fix these; the icon only says something is missing.
            Icon(
                Icons.Rounded.Info,
                contentDescription = "Missing tags",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(16.dp),
            )
        }
        Text(
            formatDuration(song.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (actions != null && !selectionMode) {
            PresetRowActions(song, actions)
            SongMenu(song, actions, extraMenuItems)
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
fun SongMenu(song: Song, actions: SongActions, extraMenuItems: (@Composable (dismiss: () -> Unit) -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    val isFavorite = song.id in actions.favoriteIds
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More options for ${song.title}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val dismiss = { open = false }
            DropdownMenuItem(
                text = { Text("Play next") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null) },
                onClick = { actions.playNext(song); dismiss() },
            )
            DropdownMenuItem(
                text = { Text("Add to queue") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                onClick = { actions.addToQueue(song); dismiss() },
            )
            DropdownMenuItem(
                text = { Text("Add to playlist…") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                onClick = { actions.addToPlaylist(listOf(song.id)); dismiss() },
            )
            PresetMenuItems(song, actions, dismiss)
            DropdownMenuItem(
                text = { Text(if (isFavorite) "Remove from favorites" else "Add to favorites") },
                leadingIcon = { Icon(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) },
                onClick = { actions.toggleFavorite(song.id); dismiss() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Go to album") },
                leadingIcon = { Icon(Icons.Rounded.Album, null) },
                onClick = { actions.goToAlbum(song); dismiss() },
            )
            DropdownMenuItem(
                text = { Text("Go to artist") },
                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                onClick = { actions.goToArtist(song); dismiss() },
            )
            DropdownMenuItem(
                text = { Text("Change cover") },
                leadingIcon = { Icon(Icons.Rounded.Image, null) },
                onClick = { actions.changeCover(song); dismiss() },
            )
            extraMenuItems?.invoke(dismiss)
        }
    }
}

@Composable
fun AddToPlaylistDialog(
    songCount: Int,
    playlists: List<Playlist>,
    startCreating: Boolean = false,
    onPick: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(startCreating || playlists.isEmpty()) }
    var name by rememberSaveable { mutableStateOf("") }
    // "Added": the plus becomes a checkmark for a moment before the dialog closes.
    var added by remember { mutableStateOf<Long?>(null) }
    val checkMs = LocalPresetUi.current.effects.checkmarkMs
    LaunchedEffect(added) {
        val id = added ?: return@LaunchedEffect
        if (checkMs > 0) delay(checkMs.toLong())
        playlists.firstOrNull { it.id == id }?.let(onPick)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true),
        title = { Text(if (songCount == 1) "Add to playlist" else "Add ${songCountLabel(songCount)} to playlist") },
        text = {
            if (creating) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onCreate(name) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        ListItem(
                            headlineContent = { Text("New playlist") },
                            leadingContent = { Icon(Icons.Rounded.Add, null) },
                            modifier = Modifier.combinedClickableCompat { creating = true },
                        )
                    }
                    items(playlists, key = { it.id }) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(songCountLabel(playlist.songCount)) },
                            leadingContent = {
                                ArtworkImage(playlist.coverSongId ?: -1, playlist.coverAlbumId ?: -1, Modifier.size(40.dp), placeholderIcon = Icons.AutoMirrored.Rounded.QueueMusic)
                            },
                            trailingContent = {
                                Crossfade(added == playlist.id, animationSpec = tween(checkMs), label = "added") { done ->
                                    Icon(if (done) Icons.Rounded.Check else Icons.Rounded.Add, contentDescription = if (done) "Added" else null)
                                }
                            },
                            modifier = Modifier.combinedClickableCompat { if (added == null) added = playlist.id },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = combinedClickable(onClick = onClick)

/** Accessibility actions that replace drag-to-reorder for TalkBack users. */
fun Modifier.reorderAccessibility(index: Int, lastIndex: Int, onMove: (from: Int, to: Int) -> Unit): Modifier =
    semantics {
        customActions = buildList {
            if (index > 0) add(CustomAccessibilityAction("Move up") { onMove(index, index - 1); true })
            if (index < lastIndex) add(CustomAccessibilityAction("Move down") { onMove(index, index + 1); true })
            if (index > 0) add(CustomAccessibilityAction("Move to top") { onMove(index, 0); true })
        }
    }
