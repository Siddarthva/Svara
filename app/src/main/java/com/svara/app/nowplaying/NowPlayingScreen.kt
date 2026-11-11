package com.svara.app.nowplaying

import androidx.compose.material.icons.rounded.GraphicEq
import com.svara.app.domain.model.AppSettings
import androidx.compose.material3.Button
import com.svara.app.ui.theme.LocalReducedMotion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import com.svara.app.data.lyrics.LyricsText
import com.svara.app.ui.states.SkeletonList
import androidx.compose.ui.semantics.disabled
import androidx.compose.material.icons.rounded.Equalizer
import com.svara.app.ui.components.formatRemaining
import com.svara.app.appearance.NowPlayingStyle
import com.svara.app.ui.artwork.ArtworkKeys
import androidx.compose.material.icons.rounded.Image
import com.svara.app.ui.PerfTrace
import com.svara.app.ui.EndTraceAfterFrame
import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svara.app.domain.model.AudioInfo
import com.svara.app.domain.model.LyricsSource
import com.svara.app.domain.model.OutputDeviceInfo
import com.svara.app.domain.model.SleepTimerOption
import com.svara.app.playback.PlaybackProgress
import com.svara.app.playback.PlaybackState
import com.svara.app.playback.RepeatSetting
import com.svara.app.playback.SleepTimerState
import com.svara.app.ui.components.ArtworkImage
import com.svara.app.ui.components.EmptyState
import com.svara.app.ui.components.WithSongActions
import com.svara.app.ui.components.formatBytes
import com.svara.app.ui.components.formatDuration
import com.svara.app.ui.navigation.Navigator
import com.svara.app.appearance.ControlId
import com.svara.app.appearance.DoubleTapAction
import com.svara.app.appearance.ProgressStyle
import com.svara.app.appearance.QueueStyle
import com.svara.app.ui.presets.PlayerParts
import com.svara.app.ui.presets.PresetPlayerBody
import com.svara.app.appearance.PresetId
import com.svara.app.ui.presets.common.PlayerActions
import com.svara.app.ui.presets.common.PlayerHost
import com.svara.app.ui.presets.common.shareTrack
import com.svara.app.ui.presets.raga.RagaPlayer
import com.svara.app.ui.presets.shruti.ShrutiPlayer
import com.svara.app.ui.presets.spanda.SpandaPlayer
import com.svara.app.ui.presets.tala.TalaPlayer
import androidx.compose.ui.platform.LocalContext
import com.svara.app.ui.theme.LocalAppearance
import com.svara.app.ui.theme.Radii
import com.svara.app.ui.theme.rememberArtworkShape
import com.svara.app.ui.theme.rememberHaptic
import com.svara.app.ui.motion.LocalPlayerWave
import com.svara.app.ui.motion.PlayerWaveLayer
import com.svara.app.ui.presets.common.PresetScrubber
import com.svara.app.appearance.tokens.ThemeTokens
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(navigator: Navigator, viewModel: NowPlayingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Passed down as State and read only by the seek bar, lyrics and times: playback ticks never
    // recompose this screen.
    val progressState = viewModel.progress.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimer.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val defaultSleep by viewModel.defaultSleepTimer.collectAsStateWithLifecycle()
    val leftHanded by viewModel.leftHanded.collectAsStateWithLifecycle()
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showSleepDialog by rememberSaveable { mutableStateOf(false) }
    var showSpeedDialog by rememberSaveable { mutableStateOf(false) }
    var showActions by rememberSaveable { mutableStateOf(false) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var showQueueSheet by rememberSaveable { mutableStateOf(false) }

    // Layout presets also load lyrics for their lyrics cards and overlays.
    var presetWantsLyrics by remember { mutableStateOf(false) }
    DisposableEffect(showLyrics, presetWantsLyrics) {
        viewModel.setLyricsVisible(showLyrics || presetWantsLyrics)
        onDispose { viewModel.setLyricsVisible(false) }
    }

    EndTraceAfterFrame(PerfTrace.NOW_PLAYING_OPEN, Unit)
    val current = state.current
    val queueAsSheet = LocalAppearance.current.custom.queueStyle == QueueStyle.SHEET
    val appearanceNow = LocalAppearance.current
    val style = appearanceNow.custom.nowPlayingStyle
    // The ETHEREAL Classic screen has its own top-bar menu; every other style uses the options sheet.
    val anahataClassic = !appearanceNow.layoutPreset && style == NowPlayingStyle.CLASSIC
    val custom = appearanceNow.custom
    val haptic = rememberHaptic()
    val onQueue: () -> Unit = { if (queueAsSheet) showQueueSheet = true else navigator.queue() }
    val openInfo: () -> Unit = { showInfo = true; viewModel.loadAudioInfo() }
    // One set of parts for every style: the style only decides where they go.
    val parts = current?.let { c ->
        val onDoubleTap: () -> Unit = {
            haptic()
            when (custom.doubleTap) {
                DoubleTapAction.PLAY_PAUSE -> viewModel.togglePlayPause()
                DoubleTapAction.FAVOURITE -> viewModel.toggleFavorite()
                DoubleTapAction.LYRICS -> showLyrics = !showLyrics
            }
        }
        PlayerParts(
            artwork = { mod ->
                SwipeableArtwork(
                    songId = c.songId,
                    albumId = c.albumId,
                    swipeEnabled = custom.swipeToSkip,
                    onNext = { haptic(); viewModel.skipNext() },
                    onPrevious = { haptic(); viewModel.skipPrevious() },
                    onDoubleTap = onDoubleTap,
                    doubleTapLabel = custom.doubleTap.label,
                    onLongPress = { showActions = true },
                    modifier = mod,
                )
            },
            lyrics = { mod -> LyricsPanel(lyrics, progressState, onOpenStudio = { navigator.lyrics(c.songId) }, mod) },
            info = { mod, emphasis -> TrackInfo(c.title, c.artist, c.album, isFavorite, leftHanded, emphasis, viewModel::toggleFavorite, mod) },
            progress = { mod -> SeekBar(progressState, custom.progressStyle, custom.seekIncrementSec, viewModel::seekTo, viewModel::seekBack, viewModel::seekForward, mod) },
            transport = { mod, large, backdrop -> Transport(state, custom.controlOrder, leftHanded, large, backdrop, viewModel, haptic, mod) },
            secondary = { mod ->
                SecondaryControls(
                    sleepActive = sleepTimer.isActive,
                    showLyrics = showLyrics,
                    leftHanded = leftHanded,
                    onSleep = { showSleepDialog = true },
                    onToggleLyrics = { showLyrics = !showLyrics },
                    onQueue = onQueue,
                    modifier = mod,
                )
            },
        )
    }
    val extras = ExtraActions(
        sleepActive = sleepTimer.isActive,
        showLyrics = showLyrics,
        onSleep = { showSleepDialog = true },
        onToggleLyrics = { showLyrics = !showLyrics },
        onQueue = onQueue,
        onSpeed = { showSpeedDialog = true },
        onInfo = openInfo,
        onEqualizer = navigator::equalizer,
    )
    val dismiss = Modifier.swipeDownToDismiss(navigator::back)
    WithSongActions(navigator) { actions ->
        // Every style draws, back to front: its background, the live wave (bottom third), then
        // artwork and controls, so the wave never covers the seek bar, times or text.
        CompositionLocalProvider(LocalPlayerWave provides state.isPlaying) {
        Box(Modifier.fillMaxSize()) {
        if (parts != null && style != NowPlayingStyle.CLASSIC) {
            val topBar: @Composable () -> Unit = { NowPlayingTopBar(onClose = navigator::back, onInfo = openInfo, onMore = { showActions = true }) }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            PlayerWaveLayer()
            Surface(Modifier.fillMaxSize().then(dismiss), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
                when (style) {
                    NowPlayingStyle.CINEMA -> CinemaPlayer(parts, current.songId, current.albumId, showLyrics, topBar, extras)
                    else -> CompactPlayer(parts, state, showLyrics, topBar, extras, onSkipTo = viewModel::skipToQueuePosition)
                }
            }
            }
        } else if (appearanceNow.layoutPreset && current != null) {
            val badge by viewModel.qualityBadge.collectAsStateWithLifecycle()
            val song by viewModel.currentSong.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val host = PlayerHost(
                state = state,
                progress = progressState,
                isFavourite = isFavorite,
                lyrics = lyrics,
                badge = badge,
                seekIncrementSec = appearanceNow.custom.seekIncrementSec,
                hapticsOn = appearanceNow.custom.haptics,
                actions = PlayerActions(
                    togglePlayPause = viewModel::togglePlayPause,
                    next = viewModel::skipNext,
                    previous = viewModel::skipPrevious,
                    seekTo = viewModel::seekTo,
                    seekBack = viewModel::seekBack,
                    seekForward = viewModel::seekForward,
                    toggleShuffle = viewModel::toggleShuffle,
                    cycleRepeat = viewModel::cycleRepeat,
                    toggleFavourite = viewModel::toggleFavorite,
                    close = navigator::back,
                    openQueue = onQueue,
                    openOptions = { showActions = true },
                    openAudioInfo = openInfo,
                    openArtist = { navigator.artist(current.artist) },
                    share = { shareTrack(context, current.title, current.artist) },
                    setLyricsWanted = { presetWantsLyrics = it },
                    haptic = haptic,
                ),
            )
            Box(Modifier.fillMaxSize().then(dismiss)) {
                when (appearanceNow.preset) {
                    PresetId.SHRUTI -> ShrutiPlayer(host, current)
                    PresetId.TALA -> TalaPlayer(host, current)
                    PresetId.RAGA -> RagaPlayer(host, current)
                    PresetId.SPANDA -> SpandaPlayer(host, current, song)
                    PresetId.ANAHATA -> Unit
                }
            }
        } else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            PlayerWaveLayer()
            Surface(Modifier.fillMaxSize().then(dismiss), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = navigator::back) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close now playing")
                    }
                    Text(
                        "Now playing",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    IconButton(onClick = openInfo, enabled = current != null) {
                        Icon(Icons.Rounded.Info, contentDescription = "Audio information")
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }, enabled = current != null) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Add to playlist…") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                                onClick = { menuOpen = false; current?.let { actions.addToPlaylist(listOf(it.songId)) } },
                            )
                            DropdownMenuItem(
                                text = { Text("Go to album") },
                                leadingIcon = { Icon(Icons.Rounded.Album, null) },
                                onClick = { menuOpen = false; current?.let { navigator.album(it.albumId) } },
                            )
                            DropdownMenuItem(
                                text = { Text("Go to artist") },
                                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                                onClick = { menuOpen = false; current?.let { navigator.artist(it.artist) } },
                            )
                            DropdownMenuItem(
                                text = { Text("Playback speed") },
                                leadingIcon = { Icon(Icons.Rounded.Speed, null) },
                                onClick = { menuOpen = false; showSpeedDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Equalizer") },
                                leadingIcon = { Icon(Icons.Rounded.Equalizer, null) },
                                onClick = { menuOpen = false; navigator.equalizer() },
                            )
                            DropdownMenuItem(
                                text = { Text("Lyrics Studio") },
                                leadingIcon = { Icon(Icons.Rounded.Lyrics, null) },
                                onClick = { menuOpen = false; current?.let { navigator.lyrics(it.songId) } },
                            )
                            DropdownMenuItem(
                                text = { Text("Visualiser") },
                                leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) },
                                onClick = { menuOpen = false; navigator.visualiser() },
                            )
                        }
                    }
                }

                if (current == null || parts == null) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        title = "Nothing playing",
                        message = "Pick a song from your library to start listening.",
                        modifier = Modifier.weight(1f),
                    )
                    return@Column
                }
                PresetPlayerBody(parts, current.songId, current.albumId, showLyrics, Modifier.weight(1f))
            }
            }
        }
        }
        }

        if (showActions && current != null) {
            ModalBottomSheet(onDismissRequest = { showActions = false }) {
                // Only ETHEREAL's Classic screen has a top-bar menu; for every other style its entries live here.
                if (!anahataClassic) ListItem(
                    headlineContent = { Text("Go to album") },
                    leadingContent = { Icon(Icons.Rounded.Album, null) },
                    modifier = Modifier.combinedClickable(onClick = { showActions = false; navigator.album(current.albumId) }),
                )
                if (!anahataClassic) ListItem(
                    headlineContent = { Text("Sleep timer") },
                    leadingContent = { Icon(Icons.Rounded.Bedtime, null) },
                    modifier = Modifier.combinedClickable(onClick = { showActions = false; showSleepDialog = true }),
                )
                if (!anahataClassic) ListItem(
                    headlineContent = { Text("Playback speed") },
                    leadingContent = { Icon(Icons.Rounded.Speed, null) },
                    modifier = Modifier.combinedClickable(onClick = { showActions = false; showSpeedDialog = true }),
                )
                ListItem(
                    headlineContent = { Text("Lyrics Studio") },
                    supportingContent = { Text("Full screen, import, edit and timing") },
                    leadingContent = { Icon(Icons.Rounded.Lyrics, null) },
                    modifier = Modifier.combinedClickable(onClick = { showActions = false; navigator.lyrics(current.songId) }),
                )
                ListItem(
                    headlineContent = { Text("Equalizer") },
                    supportingContent = { Text("Bands, presets and effects") },
                    leadingContent = { Icon(Icons.Rounded.Equalizer, null) },
                    modifier = Modifier.combinedClickable(onClick = { showActions = false; navigator.equalizer() }),
                )
                ListItem(
                    headlineContent = { Text("Visualiser") },
                    supportingContent = { Text("ZENITH gallery and full screen") },
                    leadingContent = { Icon(Icons.Rounded.GraphicEq, null) },
                    modifier = Modifier.combinedClickable(onClick = { showActions = false; navigator.visualiser() }),
                )
                ListItem(
                    headlineContent = { Text("Open queue") },
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                    modifier = Modifier.combinedClickable(onClick = {
                        showActions = false
                        if (queueAsSheet) showQueueSheet = true else navigator.queue()
                    }),
                )
                ListItem(
                    headlineContent = { Text(if (isFavorite) "Remove from favorites" else "Add to favorites") },
                    leadingContent = { Icon(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) },
                    modifier = Modifier.combinedClickable(onClick = { showActions = false; viewModel.toggleFavorite() }),
                )
                ListItem(
                    headlineContent = { Text("Add to playlist…") },
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                    modifier = Modifier.combinedClickable(onClick = { showActions = false; actions.addToPlaylist(listOf(current.songId)) }),
                )
                ListItem(
                    headlineContent = { Text("Change cover") },
                    leadingContent = { Icon(Icons.Rounded.Image, null) },
                    modifier = Modifier.combinedClickable(onClick = { showActions = false; navigator.changeCover(current.songId, current.albumId) }),
                )
                ListItem(
                    headlineContent = { Text("Audio information") },
                    leadingContent = { Icon(Icons.Rounded.Info, null) },
                    modifier = Modifier.combinedClickable(onClick = { showActions = false; showInfo = true; viewModel.loadAudioInfo() }),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showInfo) AudioInfoSheet(viewModel, onDismiss = { showInfo = false }, onChangeCover = current?.let { c -> { showInfo = false; navigator.changeCover(c.songId, c.albumId) } })
    if (showQueueSheet) {
        // Now Playing Studio: the same queue, presented as a sheet over the player.
        ModalBottomSheet(onDismissRequest = { showQueueSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Box(Modifier.fillMaxHeight(0.9f)) { QueueScreen(navigator, onClose = { showQueueSheet = false }) }
        }
    }
    if (showSleepDialog) {
        val customMinutes by viewModel.sleepCustomMinutes.collectAsStateWithLifecycle()
        val fadeOut by viewModel.sleepFadeOut.collectAsStateWithLifecycle()
        SleepTimerDialog(
            state = sleepTimer,
            default = defaultSleep,
            customMinutes = customMinutes,
            fadeOut = fadeOut,
            onFadeOut = viewModel::setSleepFadeOut,
            onStartCustom = { viewModel.startSleepTimerMinutes(it); showSleepDialog = false },
            onStart = { viewModel.startSleepTimer(it); showSleepDialog = false },
            onCancelTimer = { viewModel.cancelSleepTimer(); showSleepDialog = false },
            onDismiss = { showSleepDialog = false },
        )
    }
    if (showSpeedDialog) {
        val preservePitch by viewModel.preservePitch.collectAsStateWithLifecycle()
        SpeedDialog(
            speed = state.speed,
            preservePitch = preservePitch,
            onSpeed = viewModel::setSpeed,
            onPreservePitch = viewModel::setPreservePitch,
            onDismiss = { showSpeedDialog = false },
        )
    }
}

/**
 * Swipe left/right on the cover to skip (Gesture Studio can turn this off), double-tap for the
 * chosen action, long-press for more. TalkBack gets the same as custom actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableArtwork(
    songId: Long,
    albumId: Long,
    swipeEnabled: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDoubleTap: () -> Unit,
    doubleTapLabel: String,
    onLongPress: () -> Unit,
    modifier: Modifier,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val motion = LocalAppearance.current.motion
    val swipe = if (!swipeEnabled) Modifier else Modifier
        // Follow the finger only when motion is allowed; the skip itself works either way.
        .graphicsLayer { translationX = if (motion.enabled) offset * 0.4f * motion.intensity else 0f }
        .pointerInput(songId) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    when {
                        offset <= -threshold -> onNext()
                        offset >= threshold -> onPrevious()
                    }
                    offset = 0f
                },
                onDragCancel = { offset = 0f },
                onHorizontalDrag = { change, amount -> change.consume(); offset += amount },
            )
        }
    ArtworkImage(
        songId, albumId,
        modifier
            .aspectRatio(1f)
            .then(swipe)
            .combinedClickable(
                onClick = {},
                onDoubleClick = onDoubleTap,
                onLongClick = onLongPress,
                onLongClickLabel = "More actions",
            )
            .semantics {
                contentDescription = if (swipeEnabled) "Album artwork. Swipe to change track." else "Album artwork"
                customActions = listOf(
                    CustomAccessibilityAction("Next track") { onNext(); true },
                    CustomAccessibilityAction("Previous track") { onPrevious(); true },
                    CustomAccessibilityAction(doubleTapLabel) { onDoubleTap(); true },
                )
            },
        shape = rememberArtworkShape(Radii.medium),
        iconSize = 96.dp,
        sharedKey = ArtworkKeys.nowPlaying(songId),
    )
}

@Composable
internal fun TrackInfo(
    title: String,
    artist: String,
    album: String,
    isFavorite: Boolean,
    leftHanded: Boolean,
    emphasis: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier,
) {
    val favorite: @Composable () -> Unit = {
        IconToggleButton(checked = isFavorite, onCheckedChange = { onToggleFavorite() }, modifier = Modifier.size(48.dp)) {
            Icon(
                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    // The title always takes exactly two lines (ellipsised beyond that), so artist, album, the
    // heart, the seek bar and the controls stay put from song to song. The heart sits on the
    // title's first line.
    Row(modifier, verticalAlignment = Alignment.Top) {
        if (leftHanded) favorite()
        Column(Modifier.weight(1f).padding(horizontal = if (leftHanded) 8.dp else 0.dp)) {
            Text(
                title,
                style = if (emphasis) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                minLines = TITLE_LINES,
                maxLines = TITLE_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("np_title"),
            )
            Text(
                artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (emphasis && album.isNotBlank()) {
                Text(album, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!leftHanded) favorite()
    }
}

/**
 * Shuffle, previous, next and repeat in the user's order; play/pause is always in the centre.
 * Left-handed mode swaps the two outer controls, as before.
 */
@Composable
private fun Transport(
    state: PlaybackState,
    order: List<ControlId>,
    leftHanded: Boolean,
    large: Boolean,
    playBackdrop: (@Composable () -> Unit)?,
    viewModel: NowPlayingViewModel,
    haptic: () -> Unit,
    modifier: Modifier,
) {
    val arranged = if (leftHanded) listOf(order[3], order[1], order[2], order[0]) else order
    val skipSize = if (large) 72.dp else 64.dp
    val playSize = if (large) 88.dp else 80.dp
    @Composable
    fun control(id: ControlId) = when (id) {
        ControlId.SHUFFLE -> IconToggleButton(
            checked = state.shuffleEnabled,
            onCheckedChange = { haptic(); viewModel.toggleShuffle() },
            modifier = Modifier.size(48.dp).semantics { stateDescription = if (state.shuffleEnabled) "On" else "Off" },
        ) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ControlId.REPEAT -> IconButton(
            onClick = { haptic(); viewModel.cycleRepeat() },
            modifier = Modifier.size(48.dp).semantics {
                stateDescription = when (state.repeat) {
                    RepeatSetting.OFF -> "Off"
                    RepeatSetting.ALL -> "Repeat all"
                    RepeatSetting.ONE -> "Repeat one"
                }
            },
        ) {
            Icon(
                if (state.repeat == RepeatSetting.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = "Repeat",
                tint = if (state.repeat != RepeatSetting.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ControlId.PREVIOUS -> IconButton(onClick = { haptic(); viewModel.skipPrevious() }, modifier = Modifier.size(skipSize)) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(40.dp))
        }
        ControlId.NEXT -> IconButton(onClick = { haptic(); viewModel.skipNext() }, enabled = state.hasNext, modifier = Modifier.size(skipSize)) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(40.dp))
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        control(arranged[0])
        control(arranged[1])
        Box(contentAlignment = Alignment.Center) {
            playBackdrop?.invoke()
            FilledIconButton(onClick = { haptic(); viewModel.togglePlayPause() }, modifier = Modifier.size(playSize)) {
                if (state.isBuffering && !state.isPlaying) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
                } else {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
        }
        control(arranged[2])
        control(arranged[3])
    }
}

@Composable
private fun SecondaryControls(
    sleepActive: Boolean,
    showLyrics: Boolean,
    leftHanded: Boolean,
    onSleep: () -> Unit,
    onToggleLyrics: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier,
) {
    val secondary = listOf<@Composable () -> Unit>(
        {
            IconButton(onClick = onSleep, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Rounded.Bedtime,
                    contentDescription = if (sleepActive) "Sleep timer, on" else "Sleep timer",
                    tint = if (sleepActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        {
            IconToggleButton(checked = showLyrics, onCheckedChange = { onToggleLyrics() }, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Rounded.Lyrics,
                    contentDescription = if (showLyrics) "Hide lyrics" else "Show lyrics",
                    tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        {
            IconButton(onClick = onQueue, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Queue", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
        (if (leftHanded) secondary.reversed() else secondary).forEach { it() }
    }
}

/**
 * One seek bar for every progress style (Line, Thick, Dots): the shared draw-phase scrubber in
 * this theme's colours. One clean track and played portion, a thumb that grows only while
 * dragging, and the time bubble; it holds the user's position while dragging. TalkBack can jump
 * by the Control Studio increment.
 */
@Composable
private fun SeekBar(
    progress: State<PlaybackProgress>,
    style: ProgressStyle,
    incrementSec: Int,
    onSeek: (Long) -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    PresetScrubber(
        progress = progress,
        onSeek = onSeek,
        onSeekBack = onSeekBack,
        onSeekForward = onSeekForward,
        incrementSec = incrementSec,
        activeColor = scheme.primary,
        inactiveColor = scheme.surfaceVariant,
        handleColor = scheme.primary,
        timeColor = scheme.onSurfaceVariant,
        timeStyle = MaterialTheme.typography.labelMedium,
        modifier = modifier.testTag("np_seek"),
        trackHeight = if (style == ProgressStyle.THICK) ThemeTokens.SEEK_THICK_DP.dp else null,
        dots = style == ProgressStyle.DOTS,
        bubbleColor = scheme.inverseSurface,
        bubbleTextColor = scheme.inverseOnSurface,
    )
}

private const val TITLE_LINES = 2

@Composable
private fun LyricsPanel(lyrics: LyricsUi, progress: State<PlaybackProgress>, onOpenStudio: () -> Unit, modifier: Modifier) {
    Surface(modifier.fillMaxSize(), shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp) {
        when (lyrics) {
            LyricsUi.Hidden, LyricsUi.Loading -> SkeletonList(artwork = false)
            LyricsUi.NotFound -> EmptyState(
                icon = Icons.Rounded.Lyrics,
                title = "No lyrics found",
                message = "Nothing in the file's tags or in a .lrc or .txt beside it. Import a file or write them; the song itself isn't changed.",
                action = { Button(onClick = onOpenStudio, modifier = Modifier.heightIn(min = 48.dp)) { Text("Import or write") } },
            )
            is LyricsUi.Found -> {
                val l = lyrics.lyrics
                val timed = l.timed
                // Recomposes only when the line changes, not on every position tick.
                val active by remember(timed, l.offsetMs) { derivedStateOf { if (timed.isEmpty()) -1 else LyricsText.activeIndex(timed, progress.value.positionMs - l.offsetMs) } }
                val listState = rememberLazyListState()
                val reduced = LocalReducedMotion.current || !LocalAppearance.current.motion.enabled
                LaunchedEffect(active) {
                    if (active > 0) { val t = (active - 2).coerceAtLeast(0); if (reduced) listState.scrollToItem(t) else listState.animateScrollToItem(t) }
                }
                val lines = remember(l) { if (timed.isNotEmpty()) timed.map { it.text } else l.text.lines() }
                LazyColumn(state = listState, contentPadding = PaddingValues(20.dp)) {
                    itemsIndexed(lines) { i, line ->
                        Text(
                            line.ifBlank { " " },
                            style = if (i == active) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyLarge,
                            color = if (i == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = onOpenStudio, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open Lyrics Studio") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioInfoSheet(viewModel: NowPlayingViewModel, onDismiss: () -> Unit, onChangeCover: (() -> Unit)?) {
    val info by viewModel.info.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("Audio information", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            val data = info
            if (data == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            FileProperties(data.first)
            if (onChangeCover != null) TextButton(onClick = onChangeCover, modifier = Modifier.heightIn(min = 48.dp)) { Text("Change cover") }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("Device output", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(
                "What the connected output reports it can play. This describes the device, not the file.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (data.second.isEmpty()) InfoLine("Outputs", null)
            data.second.forEach { OutputDevice(it) }
        }
    }
}

@Composable
private fun FileProperties(info: AudioInfo) {
    Spacer(Modifier.height(12.dp))
    Text("File properties", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    InfoLine("Format", info.format)
    InfoLine("Codec", info.codec)
    InfoLine("Bitrate", info.bitrate?.let { "${it / 1000} kbps" })
    if (info.bitrate == null && info.calculatedBitrate != null) InfoLine("Average bitrate (calculated)", "${info.calculatedBitrate / 1000} kbps")
    InfoLine("Sample rate", info.sampleRate?.let { String.format(Locale.ROOT, "%.1f kHz", it / 1000.0) })
    InfoLine("Channels", info.channels?.let { if (it == 1) "Mono" else if (it == 2) "Stereo" else "$it" })
    InfoLine("Bit depth", info.bitDepth?.let { "$it-bit" })
    InfoLine("Duration", info.durationMs?.let(::formatDuration))
    InfoLine("File size", info.sizeBytes?.let(::formatBytes))
    InfoLine("Location", info.path)
}

@Composable
private fun OutputDevice(d: OutputDeviceInfo) {
    Spacer(Modifier.height(12.dp))
    Text(d.name + if (d.isActiveRoute) " (playing here)" else "", style = MaterialTheme.typography.titleSmall)
    InfoLine("Type", d.type)
    InfoLine("Sample rates", d.sampleRates.takeIf { it.isNotEmpty() }?.joinToString { "${it / 1000.0} kHz" } ?: "Any (not restricted)")
    InfoLine("Channels", d.channelCounts.takeIf { it.isNotEmpty() }?.joinToString() ?: "Any (not restricted)")
    InfoLine("Encodings", d.encodings.takeIf { it.isNotEmpty() }?.joinToString())
}

@Composable
private fun InfoLine(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
        Text(
            value ?: "Not reported",
            style = MaterialTheme.typography.bodyMedium,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.55f),
        )
    }
}

@Composable
private fun SleepTimerDialog(
    state: SleepTimerState,
    default: SleepTimerOption,
    customMinutes: Int,
    fadeOut: Boolean,
    onFadeOut: (Boolean) -> Unit,
    onStartCustom: (Int) -> Unit,
    onStart: (SleepTimerOption) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(default) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var minutes by rememberSaveable { mutableFloatStateOf(customMinutes.toFloat()) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.endsAtElapsedMs) {
        while (state.endsAtElapsedMs != null) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                when {
                    state.endOfTrack -> Text("Playback will pause at the end of this track.", color = MaterialTheme.colorScheme.primary)
                    state.endsAtElapsedMs != null -> Text(
                        "Pausing in ${formatDuration((state.endsAtElapsedMs - now).coerceAtLeast(0))}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                SleepTimerOption.entries.forEach { option ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !custom && selected == option, onClick = { selected = option; custom = false })
                        Text(option.label)
                    }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = custom, onClick = { custom = true })
                    Text("Custom: ${minutes.toInt()} minutes")
                }
                if (custom) {
                    Slider(
                        value = minutes, onValueChange = { minutes = it.toInt().toFloat() },
                        valueRange = AppSettings.SLEEP_CUSTOM_RANGE.first.toFloat()..AppSettings.SLEEP_CUSTOM_RANGE.last.toFloat(),
                        modifier = Modifier.semantics { stateDescription = "${minutes.toInt()} minutes" },
                    )
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Fade out at the end", modifier = Modifier.weight(1f))
                    Switch(checked = fadeOut, onCheckedChange = onFadeOut)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (custom) onStartCustom(minutes.toInt()) else onStart(selected) }) { Text(if (state.isActive) "Restart" else "Start") }
        },
        dismissButton = {
            if (state.isActive) TextButton(onClick = onCancelTimer) { Text("Turn off") }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SpeedDialog(
    speed: Float,
    preservePitch: Boolean,
    onSpeed: (Float) -> Unit,
    onPreservePitch: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var local by remember { mutableFloatStateOf(speed) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback speed") },
        text = {
            Column {
                Text(String.format(Locale.ROOT, "%.2f×", local), style = MaterialTheme.typography.headlineSmall)
                Slider(
                    value = local,
                    onValueChange = { local = Math.round(it * 20) / 20f },
                    onValueChangeFinished = { onSpeed(local) },
                    valueRange = 0.5f..2f,
                    modifier = Modifier.semantics { stateDescription = String.format(Locale.ROOT, "%.2f times", local) },
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Preserve pitch")
                        Text("Voices sound natural at any speed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = preservePitch, onCheckedChange = onPreservePitch)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = { local = 1f; onSpeed(1f) }) { Text("Reset") }
        },
    )
}
