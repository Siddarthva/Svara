package com.svara.app.ui.presets.common

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svara.app.library.SearchViewModel
import com.svara.app.ui.components.WithSongActions
import com.svara.app.ui.components.songCountLabel
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.theme.LocalPresetUi

/** How a preset's search field looks. */
@Immutable
class SearchFieldStyle(val container: Color, val text: Color, val placeholder: Color, val shape: Shape)

/** One browse block: label, the cover whose colour tints it, and what a tap does. */
@Immutable
class BrowseBlock(val label: String, val songId: Long, val albumId: Long, val onClick: () -> Unit)

/**
 * Search for the layout presets: a sticky field (no microphone; there is no voice search),
 * results grouped by type, and, while the field is empty, an optional browse grid.
 */
@Composable
fun PresetSearch(
    navigator: Navigator,
    title: String?,
    placeholder: String,
    field: SearchFieldStyle,
    browse: List<BrowseBlock> = emptyList(),
    browseLabelCentred: Boolean = false,
    /** Halo: solid blocks with the cover tilted out of the bottom-right corner. */
    tiltedCovers: Float = 0f,
    vm: SearchViewModel = hiltViewModel(),
) {
    val p = LocalPresetUi.current
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(p.colors.background).statusBarsPadding()) {
        if (title != null) {
            Text(title, style = p.type.display, color = p.colors.primaryText, modifier = Modifier.padding(horizontal = p.spacing.screenMargin, vertical = p.spacing.s))
        }
        TextField(
            value = query,
            onValueChange = vm::setQuery,
            placeholder = { Text(placeholder, style = p.type.body, color = field.placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            textStyle = p.type.body,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = field.placeholder) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Rounded.Close, "Clear search", tint = field.placeholder) }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = field.shape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = field.container,
                unfocusedContainerColor = field.container,
                focusedTextColor = field.text,
                unfocusedTextColor = field.text,
                cursorColor = field.text,
                focusedIndicatorColor = p.colors.transparent,
                unfocusedIndicatorColor = p.colors.transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = p.spacing.screenMargin, vertical = p.spacing.s),
        )
        val r = results
        when {
            query.isBlank() || r == null -> if (browse.isNotEmpty()) BrowseGrid(browse, browseLabelCentred, tiltedCovers) else Text(
                "Search your songs, artists, albums, playlists and folders.",
                style = p.type.body,
                color = p.colors.secondaryText,
                modifier = Modifier.padding(p.spacing.screenMargin),
            )
            r.isEmpty -> Text("Nothing in your library matches “$query”.", style = p.type.body, color = p.colors.secondaryText, modifier = Modifier.padding(p.spacing.screenMargin))
            else -> WithSongActions(navigator) { actions ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = p.spacing.l)) {
                    if (r.songs.isNotEmpty()) {
                        item(key = "songs-h") { SectionTitle("Songs", p.type.title) }
                        itemsIndexed(r.songs, key = { _, s -> "song-${s.id}" }) { i, s -> PresetSongRow(s, actions, false, onClick = { vm.play(i) }) }
                    }
                    if (r.artists.isNotEmpty()) {
                        item(key = "artists-h") { SectionTitle("Artists", p.type.title) }
                        items(r.artists.take(ARTIST_LIMIT), key = { "artist-${it.sortKey}" }) { a ->
                            PresetRow(a.name, songCountLabel(a.songCount), a.coverSongId, a.coverAlbumId, onClick = { navigator.artist(a.name) }, circular = true)
                        }
                    }
                    if (r.albums.isNotEmpty()) {
                        item(key = "albums-h") { SectionTitle("Albums", p.type.title) }
                        items(r.albums, key = { "album-${it.id}" }) { a -> PresetRow(a.title, a.artist, a.coverSongId, a.id, onClick = { navigator.album(a.id) }) }
                    }
                    if (r.playlists.isNotEmpty()) {
                        item(key = "playlists-h") { SectionTitle("Playlists", p.type.title) }
                        items(r.playlists, key = { "pl-${it.id}" }) { pl ->
                            PresetRow(pl.name, songCountLabel(pl.songCount), pl.coverSongId ?: -1, pl.coverAlbumId ?: -1, onClick = { navigator.playlist(pl.id) })
                        }
                    }
                    if (r.folders.isNotEmpty()) {
                        item(key = "folders-h") { SectionTitle("Folders", p.type.title) }
                        items(r.folders.take(ARTIST_LIMIT), key = { "f-${it.key.id.lowercase()}" }) { f ->
                            PresetRow(f.key.displayName, songCountLabel(f.songCount), f.coverSongId, f.coverAlbumId, onClick = { navigator.folder(f.key.id) })
                        }
                    }
                }
            }
        }
    }
}

private const val ARTIST_LIMIT = 5

/**
 * Two-column grid of rounded blocks, each a gradient from its cover's cached colour (made dark
 * enough for white text at 4.5:1). Labels are bold, top-left or centred.
 */
@Composable
fun BrowseGrid(blocks: List<BrowseBlock>, centred: Boolean, tiltDegrees: Float = 0f) {
    val p = LocalPresetUi.current
    LazyColumn(contentPadding = PaddingValues(horizontal = p.spacing.screenMargin, vertical = p.spacing.s)) {
        item(key = "browse-title") { Text("Browse", style = p.type.title, color = p.colors.primaryText, modifier = Modifier.padding(vertical = p.spacing.s)) }
        items(blocks.chunked(2), key = { row -> row.joinToString { it.label } }) { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = p.spacing.gridGutter), horizontalArrangement = Arrangement.spacedBy(p.spacing.gridGutter)) {
                row.forEach { b -> BrowseTile(b, centred, tiltDegrees, Modifier.weight(1f)) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BrowseTile(block: BrowseBlock, centred: Boolean, tiltDegrees: Float, modifier: Modifier) {
    val p = LocalPresetUi.current
    val art = rememberAlbumColour(block.songId, block.albumId) ?: p.colors.surfaceHighlight
    val top = readableBackground(art, p.colors.onShade, p.colors.shade)
    val bottom = readableBackground(top, p.colors.onShade, p.colors.shade).let { c -> lerpTo(c, p.colors.shade) }
    val tilted = tiltDegrees != 0f
    Box(
        modifier
            .aspectRatio(p.sizes.bannerAspect)
            .clip(p.shapes.hero)
            // Solid for the tilted style (the source's "solid, vibrant colors"), a gradient otherwise.
            .then(if (tilted) Modifier.background(top) else Modifier.background(Brush.linearGradient(listOf(top, bottom))))
            .presetClickable(role = Role.Button) { block.onClick() }
            .semantics(mergeDescendants = true) { contentDescription = block.label }
            .padding(p.spacing.m),
        contentAlignment = if (centred) Alignment.Center else Alignment.TopStart,
    ) {
        if (tilted) {
            // The cover leans out of the bottom-right corner; the tile's clip trims the overflow.
            val cover = p.sizes.miniArtwork + p.spacing.xl
            Cover(
                block.songId, block.albumId, cover, p.shapes.card,
                Modifier
                    .align(Alignment.BottomEnd)
                    .graphicsLayer {
                        rotationZ = tiltDegrees
                        translationX = cover.toPx() * TILT_OVERHANG
                        translationY = cover.toPx() * TILT_OVERHANG
                    },
            )
        }
        Text(block.label, style = p.type.title, color = p.colors.onShade, textAlign = if (centred) TextAlign.Center else TextAlign.Start)
    }
}

/** Halfway toward [to]; keeps the gradient's dark end readable as well. */
private fun lerpTo(from: Color, to: Color): Color = androidx.compose.ui.graphics.lerp(from, to, HALF)

private const val HALF = 0.5f
/** How far a tilted cover hangs past the tile edge, as a fraction of its size. */
private const val TILT_OVERHANG = 0.25f
