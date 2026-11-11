package com.svara.app.ui.presets.spanda

import com.svara.app.ui.states.SkeletonList
import com.svara.app.ui.presets.common.sectionFadeIn
import com.svara.app.ui.presets.common.ambientWash
import com.svara.app.ui.theme.LocalAmbient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svara.app.domain.model.LibraryTab
import com.svara.app.ui.home.CoverItem
import com.svara.app.ui.home.PresetHomeViewModel
import com.svara.app.ui.home.openCover
import com.svara.app.ui.navigation.CollectionType
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
 * ZENITH home: text wordmark and settings (no cast icon); an edge-to-edge 16:9 banner for the
 * most recently added album with a "Play" pill and its title on a static scrim; then carousels,
 * including circular "Artists you play".
 */
@Composable
fun SpandaHome(navigator: Navigator, hideHistory: Boolean, vm: PresetHomeViewModel = hiltViewModel()) {
    val home by vm.home.collectAsStateWithLifecycle()
    val p = LocalPresetUi.current
    val open: (CoverItem) -> Unit = { navigator.openCover(it, home.topSongs, vm::play) }
    Column(Modifier.fillMaxSize().background(p.colors.background).ambientWash(LocalAmbient.current).sectionFadeIn().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = p.spacing.screenMargin, end = p.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text("SVARĀ", style = p.type.display, color = p.colors.primaryText, modifier = Modifier.weight(1f).semantics { heading() })
            IconButton(onClick = navigator::settings) { Icon(Icons.Rounded.Settings, "Settings", tint = p.colors.primaryText) }
        }
        when {
            home.loading -> SkeletonList(Modifier.fillMaxSize())
            home.songCount == 0 -> PresetEmptyLibrary()
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = p.spacing.sectionGap)) {
                item(key = "recap") { com.svara.app.recap.RecapHomeCard(navigator) }
                home.newestAlbum?.let { album -> item(key = "banner") { Banner(album, onOpen = { open(album) }, onPlay = { (album.target as? CoverItem.Target.Album)?.let { vm.playAlbum(it.id) } }) } }
                if (!hideHistory) item(key = "mix") { Carousel("My discovery mix", home.topCharts, p.sizes.carouselArtwork, p.type.headline, p.type.bodyEmphasis, p.type.subhead, open, titleLines = 2) }
                if (!hideHistory) item(key = "recent") { Carousel("Recently played", home.recentlyPlayed, p.sizes.carouselArtwork, p.type.headline, p.type.bodyEmphasis, p.type.subhead, open, titleLines = 2) }
                item(key = "added") { Carousel("Recently added", home.recentlyAdded, p.sizes.carouselArtwork, p.type.headline, p.type.bodyEmphasis, p.type.subhead, open, titleLines = 2) }
                if (!hideHistory) item(key = "artists") { Carousel("Artists you play", home.artists, p.sizes.artistArtwork, p.type.headline, p.type.bodyEmphasis, p.type.subhead, open, circular = true) }
            }
        }
    }
}

@Composable
private fun Banner(album: CoverItem, onOpen: () -> Unit, onPlay: () -> Unit) {
    val p = LocalPresetUi.current
    val shade = p.colors.shade
    Box(
        Modifier.fillMaxWidth().aspectRatio(p.sizes.bannerAspect).presetClickable(scale = 1f, onClickLabel = "Open album") { onOpen() },
        contentAlignment = Alignment.BottomStart,
    ) {
        Cover(
            album.songId, album.albumId, p.sizes.carouselArtwork, RectangleShape,
            Modifier.fillMaxSize().drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(listOf(shade.copy(alpha = 0f), shade.copy(alpha = p.sizes.playerScrimAlpha * 2f)), startY = size.height / 3f))
            },
        )
        Column(Modifier.padding(p.spacing.screenMargin)) {
            Text(album.title, style = p.type.headline, color = p.colors.onShade, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(album.subtitle, style = p.type.subhead, color = p.colors.onShade, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(p.spacing.s))
            PresetPill("Play", selected = true, onClick = onPlay)
        }
    }
}

/**
 * Find: sticky search field (surface, 8 dp radius, no microphone) and a browse grid of
 * Genres and Recently added. Moods and Languages appear only when such tags exist.
 */
@Composable
fun SpandaFind(navigator: Navigator, vm: PresetHomeViewModel = hiltViewModel()) {
    val p = LocalPresetUi.current
    val home by vm.home.collectAsStateWithLifecycle()
    val genre = home.genres.firstOrNull()
    val newest = home.newestAlbum
    val blocks = buildList {
        if (genre != null) add(BrowseBlock("Genres", genre.coverSongId, genre.coverAlbumId) { navigator.libraryList(LibraryTab.GENRES) })
        add(BrowseBlock("Recently added", newest?.songId ?: -1, newest?.albumId ?: -1) { navigator.collection(CollectionType.RECENTLY_ADDED) })
    }
    PresetSearch(
        navigator = navigator,
        title = null,
        placeholder = "Search music",
        field = SearchFieldStyle(container = p.colors.surface, text = p.colors.primaryText, placeholder = p.colors.secondaryText, shape = p.shapes.searchField),
        browse = blocks,
    )
}

/** Library: pill filters (Podcasts and Offline omitted), sort dropdown, circular artists. */
@Composable
fun SpandaLibrary(navigator: Navigator) {
    PillFilterLibrary(
        navigator = navigator,
        title = "Library",
        filters = listOf(
            LibFilterSpec(LibFilter.PLAYLISTS, "Playlists"),
            LibFilterSpec(LibFilter.SONGS, "Songs"),
            LibFilterSpec(LibFilter.ALBUMS, "Albums"),
            LibFilterSpec(LibFilter.ARTISTS, "Artists"),
        ),
    )
}
