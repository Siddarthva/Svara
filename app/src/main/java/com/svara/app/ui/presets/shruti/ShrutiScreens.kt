package com.svara.app.ui.presets.shruti

import com.svara.app.ui.states.SkeletonList
import com.svara.app.ui.presets.common.sectionFadeIn
import com.svara.app.ui.presets.common.ambientWash
import com.svara.app.ui.theme.LocalAmbient
import com.svara.app.ui.presets.common.BrowseBlock
import com.svara.app.domain.model.LibraryTab
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svara.app.appearance.tokens.ShrutiSpec
import com.svara.app.library.PresetSort
import com.svara.app.ui.home.CoverItem
import com.svara.app.ui.home.PresetHomeViewModel
import com.svara.app.ui.home.greetingFor
import com.svara.app.ui.home.openCover
import com.svara.app.ui.navigation.CollectionType
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.presets.common.Carousel
import com.svara.app.ui.presets.common.Cover
import com.svara.app.ui.presets.common.LibFilter
import com.svara.app.ui.presets.common.LibFilterSpec
import com.svara.app.ui.presets.common.PillFilterLibrary
import com.svara.app.ui.presets.common.PresetEmptyLibrary
import com.svara.app.ui.presets.common.PresetSearch
import com.svara.app.ui.presets.common.SearchFieldStyle
import com.svara.app.ui.presets.common.presetClickable
import com.svara.app.ui.presets.common.readableBackground
import com.svara.app.ui.presets.common.rememberAlbumColour
import com.svara.app.ui.theme.LocalPresetUi
import java.time.LocalTime

/**
 * Halo home: greeting from local time with history and settings icons, a 2-column grid of six
 * recent items, then carousels. A static radial glow from the top-left uses the cached colour of
 * the most recent artwork; it never animates.
 */
@Composable
fun ShrutiHome(navigator: Navigator, hideHistory: Boolean, vm: PresetHomeViewModel = hiltViewModel()) {
    val home by vm.home.collectAsStateWithLifecycle()
    val p = LocalPresetUi.current
    val open: (CoverItem) -> Unit = { navigator.openCover(it) }
    when {
        home.loading -> SkeletonList(Modifier.fillMaxSize().background(p.colors.background))
        home.songCount == 0 -> PresetEmptyLibrary(Modifier.fillMaxSize().background(p.colors.background))
        else -> LazyColumn(Modifier.fillMaxSize().background(p.colors.background).ambientWash(LocalAmbient.current).sectionFadeIn(), contentPadding = PaddingValues(bottom = p.spacing.sectionGap)) {
            item(key = "recap") { com.svara.app.recap.RecapHomeCard(navigator) }
            item(key = "header") {
                val lead = (if (hideHistory) home.recentlyAdded else home.recentlyPlayed.ifEmpty { home.recentlyAdded }).firstOrNull()
                val glow = lead?.let { rememberAlbumColour(it.songId, it.albumId) }?.let { readableBackground(it, p.colors.primaryText, p.colors.background) }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            if (glow != null) drawRect(
                                Brush.radialGradient(
                                    listOf(glow.copy(alpha = ShrutiSpec.HOME_GLOW_ALPHA), glow.copy(alpha = 0f)),
                                    center = Offset.Zero,
                                    // Fades out by the header's bottom edge, so there is no seam.
                                    radius = size.height,
                                ),
                            )
                        }
                        .statusBarsPadding(),
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = p.spacing.screenMargin, end = p.spacing.xs, top = p.spacing.s), verticalAlignment = Alignment.CenterVertically) {
                        val greeting = remember { greetingFor(LocalTime.now().hour) }
                        Text(greeting, style = p.type.display, color = p.colors.primaryText, modifier = Modifier.weight(1f).semantics { heading() })
                        if (!hideHistory) IconButton(onClick = { navigator.collection(CollectionType.RECENTLY_PLAYED) }) {
                            Icon(Icons.Rounded.History, contentDescription = "Recently played", tint = p.colors.primaryText)
                        }
                        IconButton(onClick = navigator::settings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = p.colors.primaryText) }
                    }
                    val grid = if (hideHistory) home.recentlyAdded.take(QUICK) else home.quickGrid
                    QuickGrid(grid, open)
                }
            }
            if (!hideHistory) item(key = "jump") { Carousel("Jump back in", home.jumpBackIn, p.sizes.carouselArtwork, p.type.title, p.type.bodyEmphasis, p.type.subhead, open, titleLines = 2) }
            item(key = "made") { Carousel("Made for you", home.madeForYou, p.sizes.carouselArtwork, p.type.title, p.type.bodyEmphasis, p.type.subhead, open, titleLines = 2) }
            item(key = "added") { Carousel("Recently added", home.recentlyAdded, p.sizes.carouselArtwork, p.type.title, p.type.bodyEmphasis, p.type.subhead, open, titleLines = 2) }
        }
    }
}

private const val QUICK = 6

/** Dense 2-column grid: 48 dp art on the left, highlight background, 4 dp corners. */
@Composable
private fun QuickGrid(items: List<CoverItem>, onClick: (CoverItem) -> Unit) {
    val p = LocalPresetUi.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = p.spacing.screenMargin, vertical = p.spacing.s),
        verticalArrangement = Arrangement.spacedBy(p.spacing.gridGutter),
    ) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(p.spacing.gridGutter)) {
                row.forEach { item ->
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(p.shapes.card)
                            .background(p.colors.surfaceHighlight)
                            .presetClickable { onClick(item) }
                            .semantics(mergeDescendants = true) { contentDescription = item.title },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(item.songId, item.albumId, p.sizes.quickTileArtwork, p.shapes.card)
                        Spacer(Modifier.width(p.spacing.s))
                        Text(item.title, style = p.type.bodyEmphasis, color = p.colors.primaryText, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = p.spacing.s))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(p.spacing.xs))
    }
}

/** "Your Library": pill filters (Podcasts and Downloaded omitted), sort dropdown, circular artists. */
@Composable
fun ShrutiLibrary(navigator: Navigator) {
    PillFilterLibrary(
        navigator = navigator,
        title = "Your Library",
        gridToggle = true,
        filters = listOf(
            LibFilterSpec(LibFilter.PLAYLISTS, "Playlists"),
            LibFilterSpec(LibFilter.ALBUMS, "Albums"),
            LibFilterSpec(LibFilter.ARTISTS, "Artists"),
            // Not in the source; kept so the full song list stays one tap away.
            LibFilterSpec(LibFilter.SONGS, "Songs"),
        ),
        sortLabel = {
            when (it) {
                PresetSort.RECENTLY_ADDED -> "Recently added"
                PresetSort.TITLE -> "Alphabetical"
                PresetSort.ARTIST -> "Creator"
                PresetSort.DURATION -> "Duration"
            }
        },
    )
}

/**
 * Halo search: a sticky, pill-shaped light field (inverted colours) with the icon on the left,
 * and, while empty, the browse-all grid: solid cards in each category's artwork colour, bold
 * white label top-left, cover tilted out of the bottom-right corner.
 */
@Composable
fun ShrutiSearch(navigator: Navigator, vm: PresetHomeViewModel = hiltViewModel()) {
    val p = LocalPresetUi.current
    val home by vm.home.collectAsStateWithLifecycle()
    val genre = home.genres.firstOrNull()
    val newest = home.newestAlbum
    val top = home.topCharts.firstOrNull()
    val recent = home.recentlyPlayed.firstOrNull()
    val blocks = buildList {
        if (genre != null) add(BrowseBlock("Genres", genre.coverSongId, genre.coverAlbumId) { navigator.libraryList(LibraryTab.GENRES) })
        add(BrowseBlock("Recently added", newest?.songId ?: -1, newest?.albumId ?: -1) { navigator.collection(CollectionType.RECENTLY_ADDED) })
        add(BrowseBlock("Most played", top?.songId ?: -1, top?.albumId ?: -1) { navigator.collection(CollectionType.MOST_PLAYED) })
        add(BrowseBlock("Liked songs", recent?.songId ?: -1, recent?.albumId ?: -1) { navigator.collection(CollectionType.FAVORITES) })
    }
    PresetSearch(
        navigator = navigator,
        title = "Search",
        placeholder = "Songs, artists, albums",
        field = SearchFieldStyle(container = p.colors.primaryText, text = p.colors.background, placeholder = p.colors.background, shape = p.shapes.pill),
        browse = blocks,
        tiltedCovers = ShrutiSpec.BROWSE_TILT_DEGREES,
    )
}
