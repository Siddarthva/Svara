package com.svara.app.ui.presets.tala

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svara.app.appearance.tokens.TalaSpec
import com.svara.app.domain.model.LibraryTab
import com.svara.app.library.PresetLibraryViewModel
import com.svara.app.ui.home.CoverItem
import com.svara.app.ui.home.PresetHomeViewModel
import com.svara.app.ui.home.openCover
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.presets.common.Carousel
import com.svara.app.ui.presets.common.Cover
import com.svara.app.ui.presets.common.PresetEmptyLibrary
import com.svara.app.ui.presets.common.PresetSearch
import com.svara.app.ui.presets.common.SearchFieldStyle
import com.svara.app.ui.presets.common.presetClickable
import com.svara.app.ui.theme.LocalPresetUi

/** True once the list has scrolled past the collapse distance; read in a derived state. */
@Composable
private fun rememberCollapsed(list: LazyListState): Boolean {
    val threshold = with(LocalDensity.current) { TalaSpec.LARGE_TITLE_COLLAPSE_DP.dp.roundToPx() }
    val collapsed by remember(list, threshold) {
        derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > threshold }
    }
    return collapsed
}

/**
 * Large 34 sp title that gives way to an inline 17 sp semibold title (with a hairline) once the
 * list scrolls past 24 dp. The swap is instant: no animation is spent on it.
 */
@Composable
fun TalaLargeTitleScaffold(title: String, list: LazyListState, content: @Composable () -> Unit) {
    val p = LocalPresetUi.current
    val collapsed = rememberCollapsed(list)
    Box(Modifier.fillMaxSize().background(p.colors.background).ambientWash(LocalAmbient.current).sectionFadeIn()) {
        content()
        if (collapsed) {
            Column(Modifier.fillMaxWidth().background(p.colors.background).statusBarsPadding()) {
                Text(
                    title,
                    style = p.type.bodyEmphasis,
                    color = p.colors.primaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().heightIn(min = p.spacing.touch).padding(top = p.spacing.m).semantics { heading() },
                )
                Box(Modifier.fillMaxWidth().height(p.spacing.hairline).background(p.colors.divider))
            }
        }
    }
}

@Composable
private fun LargeTitle(title: String, onSettings: (() -> Unit)? = null) {
    val p = LocalPresetUi.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = p.spacing.screenMargin, end = p.spacing.xs, top = p.spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = p.type.display, color = p.colors.primaryText, modifier = Modifier.weight(1f).semantics { heading() })
        // Not in the source; Settings isn't one of SOLACE's tabs, so it needs a way in.
        if (onSettings != null) IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings", tint = p.colors.accent) }
    }
}

/** SOLACE home: large title, a pager of up to five recent albums, then three carousels. */
@Composable
fun TalaHome(navigator: Navigator, hideHistory: Boolean, vm: PresetHomeViewModel = hiltViewModel()) {
    val home by vm.home.collectAsStateWithLifecycle()
    val p = LocalPresetUi.current
    val list = rememberLazyListState()
    val open: (CoverItem) -> Unit = { navigator.openCover(it) }
    TalaLargeTitleScaffold("Home", list) {
        when {
            home.loading -> SkeletonList(Modifier.fillMaxSize())
            home.songCount == 0 -> Column { LargeTitle("Home", navigator::settings); PresetEmptyLibrary() }
            else -> LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = p.spacing.sectionGap)) {
                item(key = "title") { LargeTitle("Home", navigator::settings) }
                item(key = "recap") { com.svara.app.recap.RecapHomeCard(navigator) }
                val hero = if (hideHistory) home.recentlyAdded.take(TALA_HERO) else home.heroAlbums
                if (hero.isNotEmpty()) item(key = "hero") { HeroPager(hero, open) }
                if (!hideHistory) item(key = "recent") { Carousel("Recently played", home.recentlyPlayed, p.sizes.carouselArtwork, p.type.headline, p.type.bodyEmphasis, p.type.subhead, open) }
                if (!hideHistory) item(key = "jump") { Carousel("Jump back in", home.jumpBackIn, p.sizes.carouselArtwork, p.type.headline, p.type.bodyEmphasis, p.type.subhead, open) }
                item(key = "added") { Carousel("Recently added", home.recentlyAdded, p.sizes.carouselArtwork, p.type.headline, p.type.bodyEmphasis, p.type.subhead, open) }
            }
        }
    }
}

private const val TALA_HERO = 5

/** Featured cards, one per album: art fills the card width with 12 dp corners; 200 ms slide, no bounce. */
@Composable
private fun HeroPager(items: List<CoverItem>, onClick: (CoverItem) -> Unit) {
    val p = LocalPresetUi.current
    val pager = rememberPagerState { items.size }
    val ms = p.effects.pagerMs
    HorizontalPager(
        state = pager,
        contentPadding = PaddingValues(horizontal = p.spacing.screenMargin),
        pageSpacing = p.spacing.gridGutter,
        key = { items[it].key },
        flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = if (ms > 0) tween(ms) else snap()),
        modifier = Modifier.padding(top = p.spacing.l),
    ) { page ->
        val item = items[page]
        Column(
            Modifier
                .fillMaxWidth()
                .presetClickable { onClick(item) }
                .semantics(mergeDescendants = true) { contentDescription = "${item.title}, ${item.subtitle}" },
        ) {
            Cover(item.songId, item.albumId, p.sizes.carouselArtwork, p.shapes.hero, Modifier.fillMaxWidth().aspectRatio(1f))
            Spacer(Modifier.height(p.spacing.s))
            Text(item.title, style = p.type.title, color = p.colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, style = p.type.subhead, color = p.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * SOLACE library: large title, a list of categories with accent chevrons ("On this device" in
 * place of Downloaded, since all music is local), then a 2-column "Recently added" grid.
 */
@Composable
fun TalaLibrary(navigator: Navigator, vm: PresetLibraryViewModel = hiltViewModel()) {
    val p = LocalPresetUi.current
    val list = rememberLazyListState()
    val recent by vm.recentlyAddedAlbums.collectAsStateWithLifecycle()
    val count by vm.songCount.collectAsStateWithLifecycle()
    val rows = listOf(
        "Playlists" to { navigator.playlists() },
        "Artists" to { navigator.libraryList(LibraryTab.ARTISTS) },
        "Albums" to { navigator.libraryList(LibraryTab.ALBUMS) },
        "Songs" to { navigator.libraryList(LibraryTab.SONGS) },
        "On this device" to { navigator.libraryList(LibraryTab.FOLDERS) },
    )
    TalaLargeTitleScaffold("Library", list) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = p.spacing.sectionGap)) {
            item(key = "title") { LargeTitle("Library") }
            if (count == 0) {
                item(key = "empty") { PresetEmptyLibrary() }
                return@LazyColumn
            }
            rows.forEach { (label, action) ->
                item(key = "row-$label") {
                    Column(Modifier.padding(start = p.spacing.screenMargin)) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = p.spacing.rowHeight).presetClickable(scale = 1f) { action() }.padding(end = p.spacing.screenMargin),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, style = p.type.body, color = p.colors.primaryText, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = p.colors.accent, modifier = Modifier.size(p.sizes.chevron))
                        }
                        Box(Modifier.fillMaxWidth().height(p.spacing.hairline).background(p.colors.divider))
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item(key = "recent-title") {
                    Text("Recently added", style = p.type.headline, color = p.colors.primaryText, modifier = Modifier.padding(horizontal = p.spacing.screenMargin).padding(top = p.spacing.sectionGap, bottom = p.spacing.s).semantics { heading() })
                }
                recent.chunked(2).forEach { pair ->
                    item(key = "grid-${pair.first().key}") {
                        Row(Modifier.padding(horizontal = p.spacing.screenMargin).padding(bottom = p.spacing.gridGutter), horizontalArrangement = Arrangement.spacedBy(p.spacing.gridGutter)) {
                            pair.forEach { item ->
                                Column(Modifier.weight(1f).presetClickable { navigator.openCover(item) }.semantics(mergeDescendants = true) { contentDescription = "${item.title}, ${item.subtitle}" }) {
                                    Cover(item.songId, item.albumId, p.sizes.carouselArtwork, p.shapes.artwork, Modifier.fillMaxWidth().aspectRatio(1f).clip(p.shapes.artwork))
                                    Spacer(Modifier.height(p.spacing.s))
                                    Text(item.title, style = p.type.bodyEmphasis, color = p.colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.subtitle, style = p.type.subhead, color = p.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** SOLACE search: large title and a rounded surface field. */
@Composable
fun TalaSearch(navigator: Navigator) {
    val p = LocalPresetUi.current
    PresetSearch(
        navigator = navigator,
        title = "Search",
        placeholder = "Songs, artists, albums",
        field = SearchFieldStyle(container = p.colors.surface, text = p.colors.primaryText, placeholder = p.colors.secondaryText, shape = p.shapes.searchField),
    )
}
