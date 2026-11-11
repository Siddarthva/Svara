package com.svara.app.ui.presets.tala

import com.svara.app.ui.presets.common.AlbumHeaderBackdrop
import androidx.compose.material.icons.rounded.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.svara.app.domain.model.Song
import com.svara.app.ui.components.SongMenu
import com.svara.app.ui.components.WithSongActions
import com.svara.app.ui.components.formatDuration
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.presets.common.Cover
import com.svara.app.ui.presets.common.presetClickable
import com.svara.app.ui.theme.LocalPresetUi

/**
 * SOLACE album page: large centred art (12 dp corners), album name in title2, artist in subhead,
 * Play (accent pill) and Shuffle (outlined pill), then numbered track rows with dividers, the
 * duration on the right and a menu for actions.
 */
@Composable
fun TalaAlbum(
    navigator: Navigator,
    title: String,
    subtitle: String,
    songs: List<Song>,
    coverSongId: Long,
    coverAlbumId: Long,
    currentSongId: Long?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onPlayFrom: (Int) -> Unit,
) {
    val p = LocalPresetUi.current
    val artist = songs.firstOrNull()?.let { it.albumArtist?.takeIf { a -> a.isNotBlank() } ?: it.displayArtist } ?: subtitle
    WithSongActions(navigator) { actions ->
        LazyColumn(Modifier.fillMaxSize().background(p.colors.background), contentPadding = PaddingValues(bottom = p.spacing.sectionGap)) {
            item(key = "back") {
                Row(Modifier.fillMaxWidth().statusBarsPadding()) {
                    IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = p.colors.accent) }
                    Spacer(Modifier.weight(1f))
                    songs.firstOrNull()?.let { first ->
                        IconButton(onClick = { navigator.changeCover(first.id, first.albumId) }) {
                            Icon(Icons.Rounded.Image, "Change album cover", tint = p.colors.accent)
                        }
                    }
                }
            }
            item(key = "hero") {
                Box {
                AlbumHeaderBackdrop(coverSongId, coverAlbumId, fadeTo = p.colors.background, text = p.colors.primaryText, modifier = Modifier.matchParentSize())
                Column(Modifier.fillMaxWidth().padding(horizontal = p.spacing.screenMargin), horizontalAlignment = Alignment.CenterHorizontally) {
                    Cover(coverSongId, coverAlbumId, p.sizes.carouselArtwork * 2, p.shapes.hero, Modifier.shadow(p.sizes.smallArtworkElevation, p.shapes.hero))
                    Spacer(Modifier.height(p.spacing.l))
                    Text(title, style = p.type.title, color = p.colors.primaryText, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(artist, style = p.type.subhead, color = p.colors.accentText, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(p.spacing.l))
                    Row(horizontalArrangement = Arrangement.spacedBy(p.spacing.m)) {
                        Pill("Play", Icons.Rounded.PlayArrow, filled = true, enabled = songs.isNotEmpty(), onClick = onPlay, modifier = Modifier.weight(1f))
                        Pill("Shuffle", Icons.Rounded.Shuffle, filled = false, enabled = songs.isNotEmpty(), onClick = onShuffle, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(p.spacing.l))
                }
                }
            }
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                Column(Modifier.padding(start = p.spacing.screenMargin)) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = p.spacing.rowHeight).presetClickable(scale = 1f) { onPlayFrom(index) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (song.trackNumber > 0) song.trackNumber.toString() else "–",
                            style = p.type.body, color = p.colors.secondaryText,
                            modifier = Modifier.width(p.spacing.xxl),
                        )
                        Text(
                            song.title, style = p.type.body,
                            color = if (song.id == currentSongId) p.colors.accentText else p.colors.primaryText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Text(formatDuration(song.durationMs), style = p.type.caption, color = p.colors.secondaryText)
                        SongMenu(song, actions)
                    }
                    Box(Modifier.fillMaxWidth().height(p.spacing.hairline).background(p.colors.divider))
                }
            }
        }
    }
}

@Composable
private fun Pill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val p = LocalPresetUi.current
    Row(
        modifier
            .heightIn(min = p.spacing.touch)
            .clip(p.shapes.pill)
            .then(if (filled) Modifier.background(p.colors.accentFill) else Modifier.border(p.spacing.hairline, p.colors.accentText, p.shapes.pill))
            .presetClickable(role = Role.Button) { if (enabled) onClick() }
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(horizontal = p.spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (filled) p.colors.onAccent else p.colors.accentText
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(p.spacing.s))
        Text(label, style = p.type.bodyEmphasis, color = tint)
    }
}
