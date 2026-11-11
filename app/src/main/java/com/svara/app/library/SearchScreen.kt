package com.svara.app.library

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.History
import kotlinx.coroutines.launch
import com.svara.app.domain.model.AppSettings
import com.svara.app.domain.repository.SettingsRepository
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.svara.app.domain.library.SearchResults
import com.svara.app.domain.library.CollectionSearch
import com.svara.app.domain.library.TextKeys
import com.svara.app.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.first
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Folder
import com.svara.app.domain.repository.LibraryRepository
import com.svara.app.playback.PlaybackController
import com.svara.app.ui.components.BackButton
import com.svara.app.ui.components.EmptyState
import com.svara.app.ui.components.SectionHeader
import com.svara.app.ui.components.SongRow
import com.svara.app.ui.components.WithSongActions
import com.svara.app.ui.components.songCountLabel
import com.svara.app.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val library: LibraryRepository,
    private val playlists: PlaylistRepository,
    private val playback: PlaybackController,
    private val settings: SettingsRepository,
) : ViewModel() {

    val query: StateFlow<String> = savedState.getStateFlow(KEY_QUERY, "")

    /** Recent searches, kept on this device only. */
    val recent: StateFlow<List<String>> = settings.settings.map { it.recentSearches }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Debounced so typing quickly runs one query, not one per keystroke. */
    val results: StateFlow<SearchResults?> = query
        .debounce(DEBOUNCE_MS)
        .distinctUntilChanged()
        .mapLatest { q ->
            if (q.isBlank()) null
            else {
                val terms = TextKeys.searchTerms(q)
                library.search(q).copy(
                    playlists = CollectionSearch.rank(playlists.playlists.first(), terms, 20, { it.name }, { it.name.lowercase() }),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(q: String) {
        savedState[KEY_QUERY] = q
    }

    fun play(index: Int) {
        val songs = results.value?.songs ?: return
        rememberQuery()
        playback.playSongs(songs, index)
    }

    /** Called when a result is used: the query joins the recent searches. */
    fun rememberQuery() {
        val q = query.value.trim()
        if (q.length < 2) return
        viewModelScope.launch {
            settings.update { s -> s.copy(recentSearches = (listOf(q) + s.recentSearches.filterNot { it.equals(q, ignoreCase = true) }).take(AppSettings.RECENT_SEARCH_LIMIT)) }
        }
    }

    fun clearRecent() {
        viewModelScope.launch { settings.update { it.copy(recentSearches = emptyList()) } }
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val DEBOUNCE_MS = 200L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navigator: Navigator, viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    WithSongActions(navigator) { actions ->
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = { BackButton(navigator::back) },
                    title = {
                        TextField(
                            value = query,
                            onValueChange = viewModel::setQuery,
                            placeholder = { Text("Songs, artists, albums", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    },
                )
            },
        ) { padding ->
            val r = results
            when {
                r == null -> {
                    val recent by viewModel.recent.collectAsStateWithLifecycle()
                    SearchShortcuts(navigator, recent, onRecent = viewModel::setQuery, onClearRecent = viewModel::clearRecent, modifier = Modifier.padding(padding))
                }
                r.isEmpty -> EmptyState(Icons.Rounded.SearchOff, "No matches", "Nothing in your library matches “$query”.", Modifier.padding(padding))
                else -> LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                    if (r.includesFuzzy) {
                        item(key = "fuzzy") {
                            Text(
                                "Including close matches for “$query”",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    if (r.playlists.isNotEmpty()) {
                        item(key = "playlists-h") { SectionHeader("Playlists") }
                        items(r.playlists, key = { "pl-${it.id}" }) { p ->
                            CollectionRow(p.name, songCountLabel(p.songCount), p.coverSongId ?: -1, p.coverAlbumId ?: -1, { navigator.playlist(p.id) }, icon = Icons.AutoMirrored.Rounded.QueueMusic)
                        }
                    }
                    if (r.artists.isNotEmpty()) {
                        item(key = "artists-h") { SectionHeader("Artists") }
                        items(r.artists.take(5), key = { "artist-" + it.sortKey }) { artist ->
                            CollectionRow(
                                title = artist.name,
                                subtitle = songCountLabel(artist.songCount),
                                coverSongId = artist.coverSongId,
                                coverAlbumId = artist.coverAlbumId,
                                onClick = { navigator.artist(artist.name) },
                                icon = Icons.Rounded.Person,
                                circular = true,
                            )
                        }
                    }
                    if (r.albums.isNotEmpty()) {
                        item(key = "albums") {
                            SectionHeader("Albums")
                            LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                                items(r.albums, key = { it.id }) { album ->
                                    AlbumCard(album, onClick = { navigator.album(album.id) }, modifier = Modifier.width(140.dp))
                                }
                            }
                        }
                    }
                    if (r.folders.isNotEmpty()) {
                        item(key = "folders-h") { SectionHeader("Folders") }
                        items(r.folders.take(5), key = { "f-" + it.key.id.lowercase() }) { f ->
                            CollectionRow(f.key.displayName, "${f.key.path} • ${songCountLabel(f.songCount)}", -1, -1, { navigator.folder(f.key.id) }, icon = Icons.Rounded.Folder)
                        }
                    }
                    if (r.songs.isNotEmpty()) {
                        item(key = "songs-h") { SectionHeader("Songs") }
                        itemsIndexed(r.songs, key = { _, s -> "song-${s.id}" }) { index, song ->
                            SongRow(song = song, actions = actions, onClick = { viewModel.play(index) })
                        }
                    }
                }
            }
        }
    }
}

/** Before typing: jump straight to common collections, or repeat a recent search. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SearchShortcuts(navigator: Navigator, recent: List<String>, onRecent: (String) -> Unit, onClearRecent: () -> Unit, modifier: Modifier) {
    androidx.compose.foundation.layout.Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Shortcuts", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            listOf(
                "Favourites" to { navigator.collection(com.svara.app.ui.navigation.CollectionType.FAVORITES) },
                "Recently played" to { navigator.collection(com.svara.app.ui.navigation.CollectionType.RECENTLY_PLAYED) },
                "Most played" to { navigator.collection(com.svara.app.ui.navigation.CollectionType.MOST_PLAYED) },
                "Recently added" to { navigator.collection(com.svara.app.ui.navigation.CollectionType.RECENTLY_ADDED) },
                "Playlists" to { navigator.playlists() },
                "History" to { navigator.history() },
            ).forEach { (label, open) ->
                androidx.compose.material3.AssistChip(onClick = open, label = { Text(label) })
            }
        }
        if (recent.isNotEmpty()) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Recent searches", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                androidx.compose.material3.TextButton(onClick = onClearRecent) { Text("Clear") }
            }
            recent.forEach { q ->
                androidx.compose.material3.ListItem(
                    headlineContent = { Text(q) },
                    leadingContent = { Icon(Icons.Rounded.History, null) },
                    modifier = Modifier.clickable { onRecent(q) },
                )
            }
        }
    }
}
