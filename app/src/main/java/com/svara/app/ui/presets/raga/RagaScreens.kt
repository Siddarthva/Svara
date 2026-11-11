package com.svara.app.ui.presets.raga

import com.svara.app.ui.states.SkeletonList
import com.svara.app.ui.presets.common.sectionFadeIn
import com.svara.app.ui.presets.common.ambientWash
import com.svara.app.ui.theme.LocalAmbient
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svara.app.domain.model.LibraryTab
import com.svara.app.ui.home.CoverItem
import com.svara.app.ui.home.PresetHome
import com.svara.app.ui.home.PresetHomeViewModel
import com.svara.app.ui.home.openCover
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.presets.common.BrowseBlock
import com.svara.app.ui.presets.common.Carousel
import com.svara.app.ui.presets.common.Cover
import com.svara.app.ui.presets.common.LibFilter
import com.svara.app.ui.presets.common.LibFilterSpec
import com.svara.app.ui.presets.common.PillFilterLibrary
import com.svara.app.ui.presets.common.PresetEmptyLibrary
import com.svara.app.ui.presets.common.PresetPill
import com.svara.app.ui.presets.common.PresetSearch
import com.svara.app.ui.presets.common.SearchFieldStyle
import com.svara.app.ui.presets.common.presetClickable
import com.svara.app.ui.theme.LocalPresetUi

/**
 * The language filter. SVARĀ doesn't read language tags yet and has no tag editor, so with no
 * languages found the pill reads "Add language tags" and explains that this is coming later.
 */
@Composable
fun LanguagesNotice(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Language tags") },
        text = { Text("SVARĀ found no language tags in your music. Filtering by language and adding language tags are coming in a later update.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/**
 * OCTAVE home: wordmark, language selector pill, search and settings; a 16:9 banner carousel of
 * the three most-played albums (title on a gradient scrim, no promotions); then carousels with
 * h2 headers and genre chips from the files' own tags. Radio is omitted.
 */
@Composable
fun RagaHome(navigator: Navigator, hideHistory: Boolean, vm: PresetHomeViewModel = hiltViewModel()) {
    val home by vm.home.collectAsStateWithLifecycle()
    val p = LocalPresetUi.current
    var languages by rememberSaveable { mutableStateOf(false) }
    val open: (CoverItem) -> Unit = { navigator.openCover(it, home.topSongs, vm::play) }
    Column(Modifier.fillMaxSize().background(p.colors.background).ambientWash(LocalAmbient.current).sectionFadeIn().statusBarsPadding()) {
        // Wordmark and actions on one row, the language pill on its own row: both fit at 130 % text.
        Row(Modifier.fillMaxWidth().padding(start = p.spacing.screenMargin, end = p.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "SVARĀ", style = p.type.display, color = p.colors.primaryText, maxLines = 1,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = navigator::search) { Icon(Icons.Rounded.Search, "Search", tint = p.colors.primaryText) }
            IconButton(onClick = navigator::settings) { Icon(Icons.Rounded.Settings, "Settings", tint = p.colors.primaryText) }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = p.spacing.screenMargin)) {
            PresetPill("Add language tags", selected = true, onClick = { languages = true })
        }
        when {
            home.loading -> SkeletonList(Modifier.fillMaxSize())
            home.songCount == 0 -> PresetEmptyLibrary()
            else -> RagaFeed(home, hideHistory, open, navigator)
        }
    }
    if (languages) LanguagesNotice { languages = false }
}

@Composable
private fun RagaFeed(home: PresetHome, hideHistory: Boolean, open: (CoverItem) -> Unit, navigator: Navigator) {
    val p = LocalPresetUi.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = p.spacing.sectionGap)) {
        item(key = "recap") { com.svara.app.recap.RecapHomeCard(navigator) }
        if (home.topAlbums.isNotEmpty()) item(key = "banner") { Banner(home.topAlbums, open) }
        if (!hideHistory) item(key = "recent") { Carousel("Recently played", home.recentlyPlayed, p.sizes.carouselArtwork, p.type.title, p.type.bodyEmphasis, p.type.subhead, open, titleLines = 2) }
        if (!hideHistory) item(key = "charts") { Carousel("Top charts", home.topCharts, p.sizes.carouselArtwork, p.type.title, p.type.bodyEmphasis, p.type.subhead, open, titleLines = 2) }
        item(key = "new") { Carousel("New releases", home.recentlyAdded, p.sizes.carouselArtwork, p.type.title, p.type.bodyEmphasis, p.type.subhead, open, titleLines = 2) }
        if (home.genres.isNotEmpty()) item(key = "moods") {
            Column(Modifier.padding(top = p.spacing.sectionGap)) {
                Text("Moods and genres", style = p.type.title, color = p.colors.primaryText, modifier = Modifier.padding(horizontal = p.spacing.screenMargin).semantics { heading() })
                LazyRow(contentPadding = PaddingValues(horizontal = p.spacing.screenMargin), horizontalArrangement = Arrangement.spacedBy(p.spacing.s)) {
                    items(home.genres, key = { it.name }) { g -> PresetPill(g.name, selected = false, onClick = { navigator.genre(g.name) }) }
                }
            }
        }
    }
}

/** Wide 16:9 cards: the album art with its title on a gradient scrim. */
@Composable
private fun Banner(items: List<CoverItem>, onClick: (CoverItem) -> Unit) {
    val p = LocalPresetUi.current
    val pager = rememberPagerState { items.size }
    val ms = p.effects.pagerMs
    HorizontalPager(
        state = pager,
        contentPadding = PaddingValues(horizontal = p.spacing.screenMargin),
        pageSpacing = p.spacing.gridGutter,
        key = { items[it].key },
        flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = if (ms > 0) tween(ms) else snap()),
        modifier = Modifier.padding(top = p.spacing.s),
    ) { page ->
        val item = items[page]
        val shade = p.colors.shade
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(p.sizes.bannerAspect)
                .clip(p.shapes.hero)
                .presetClickable { onClick(item) }
                .semantics(mergeDescendants = true) { contentDescription = "${item.title}, ${item.subtitle}" },
            contentAlignment = Alignment.BottomStart,
        ) {
            Cover(
                item.songId, item.albumId, p.sizes.carouselArtwork, p.shapes.hero,
                Modifier.fillMaxSize().drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(listOf(shade.copy(alpha = 0f), shade.copy(alpha = p.sizes.playerScrimAlpha)), startY = size.height / 3f))
                },
            )
            Column(Modifier.padding(p.spacing.m)) {
                Text(item.title, style = p.type.headline, color = p.colors.onShade, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.subtitle, style = p.type.subhead, color = p.colors.onShade, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** My Music: pill tabs. All music is on the device, so no download marks are drawn on rows. */
@Composable
fun RagaMyMusic(navigator: Navigator) {
    PillFilterLibrary(
        navigator = navigator,
        title = "My Music",
        filters = listOf(
            LibFilterSpec(LibFilter.SONGS, "On device"),
            LibFilterSpec(LibFilter.PLAYLISTS, "Playlists"),
            LibFilterSpec(LibFilter.LIKED, "Liked songs"),
            LibFilterSpec(LibFilter.FOLDERS, "Local files"),
        ),
        showSort = false,
    )
}

/**
 * OCTAVE search: bar at the top (no microphone), results grouped by type, and a browse grid of
 * Genres and Languages (Moods appear only when mood tags exist, which SVARĀ doesn't read yet).
 */
@Composable
fun RagaSearch(navigator: Navigator, vm: PresetHomeViewModel = hiltViewModel()) {
    val p = LocalPresetUi.current
    val home by vm.home.collectAsStateWithLifecycle()
    var languages by rememberSaveable { mutableStateOf(false) }
    val genre = home.genres.firstOrNull()
    val blocks = listOf(
        BrowseBlock("Genres", genre?.coverSongId ?: -1, genre?.coverAlbumId ?: -1) { navigator.libraryList(LibraryTab.GENRES) },
        BrowseBlock("Languages", home.topCharts.firstOrNull()?.songId ?: -1, home.topCharts.firstOrNull()?.albumId ?: -1) { languages = true },
    )
    PresetSearch(
        navigator = navigator,
        title = null,
        placeholder = "Search songs, artists, albums, playlists",
        field = SearchFieldStyle(container = p.colors.surface, text = p.colors.primaryText, placeholder = p.colors.secondaryText, shape = p.shapes.searchField),
        browse = blocks,
        browseLabelCentred = true,
    )
    if (languages) LanguagesNotice { languages = false }
}
