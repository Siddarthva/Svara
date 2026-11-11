package com.svara.app.nowplaying

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.svara.app.appearance.Argb
import com.svara.app.appearance.BackgroundKind
import com.svara.app.appearance.Contrast
import com.svara.app.appearance.colour.ContrastEngine
import com.svara.app.data.lyrics.LyricsText
import com.svara.app.di.IoDispatcher
import com.svara.app.domain.model.AppSettings
import com.svara.app.domain.model.Lyrics
import com.svara.app.domain.model.LyricsSource
import com.svara.app.domain.model.Song
import com.svara.app.domain.repository.LibraryRepository
import com.svara.app.domain.repository.LyricsOverrideRepository
import com.svara.app.domain.repository.LyricsRepository
import com.svara.app.domain.repository.SettingsRepository
import com.svara.app.playback.PlaybackController
import com.svara.app.playback.PlaybackProgress
import com.svara.app.playback.UserMessages
import com.svara.app.ui.components.BackButton
import com.svara.app.ui.components.EmptyState
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.presets.common.rememberAlbumColour
import com.svara.app.ui.states.SkeletonList
import com.svara.app.ui.theme.LocalAppearance
import com.svara.app.ui.theme.LocalReducedMotion
import com.svara.app.ui.theme.LyricsTokens
import com.svara.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** What Lyrics Studio shows. [lyrics] null = none found (an Import action is offered, never a fake fetch). */
sealed interface LyricsStudioUi {
    data object Loading : LyricsStudioUi
    data class Ready(val song: Song, val lyrics: Lyrics?) : LyricsStudioUi
    data object NoSong : LyricsStudioUi
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LyricsStudioViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val library: LibraryRepository,
    private val lyrics: LyricsRepository,
    private val overrides: LyricsOverrideRepository,
    private val playback: PlaybackController,
    private val settings: SettingsRepository,
    private val messages: UserMessages,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val songId: Long = savedState.get<Long>("songId") ?: -1L

    /** Reloads whenever the user's lyrics or offset for this song change. Parsing and reads run off the main thread. */
    val ui: StateFlow<LyricsStudioUi> = overrides.observe(songId)
        .flatMapLatest {
            flow {
                val song = library.songById(songId)
                if (song == null) emit(LyricsStudioUi.NoSong)
                else emit(LyricsStudioUi.Ready(song, withContext(io) { lyrics.lyricsFor(song) }))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsStudioUi.Loading)

    /** Whether this song is the one playing, so the highlight follows playback. */
    val isCurrent: StateFlow<Boolean> = playback.state.map { it.current?.songId == songId }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Position for the highlight; sampled only while this screen is visible and this song plays. */
    val progress: StateFlow<PlaybackProgress> = isCurrent
        .flatMapLatest { current -> if (current) playback.progress(PROGRESS_MS) else flowOf(PlaybackProgress()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), PlaybackProgress())

    val textScale: StateFlow<Int> = settings.settings.map { it.lyricsTextScale }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings().lyricsTextScale)
    val lineSpacing: StateFlow<Int> = settings.settings.map { it.lyricsLineSpacing }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings().lyricsLineSpacing)

    fun seekTo(ms: Long) = playback.seekTo(ms.coerceAtLeast(0))

    fun play() {
        val song = (ui.value as? LyricsStudioUi.Ready)?.song ?: return
        playback.playSongs(listOf(song), 0, shuffle = false)
    }

    /** Reads an .lrc or .txt the user picked (Storage Access Framework), capped in size. */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(io) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(BUFFER)
                        while (out.size() <= MAX_IMPORT_BYTES) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                        }
                        if (out.size() > MAX_IMPORT_BYTES) null else LyricsText.decode(out.toByteArray())
                    }
                } catch (e: Exception) {
                    null
                }
            }
            if (text.isNullOrBlank()) {
                messages.emit("Couldn't read lyrics from that file")
                return@launch
            }
            overrides.setText(songId, text)
            messages.emit("Lyrics imported. The audio file isn't changed.")
        }
    }

    fun saveText(text: String) {
        viewModelScope.launch { overrides.setText(songId, text) }
    }

    fun removeImported() {
        viewModelScope.launch {
            overrides.setText(songId, null)
            messages.emit("Showing the file's own lyrics again")
        }
    }

    fun nudgeOffset(deltaMs: Long) {
        val current = ((ui.value as? LyricsStudioUi.Ready)?.lyrics?.offsetMs) ?: 0L
        viewModelScope.launch { overrides.setOffset(songId, current + deltaMs) }
    }

    fun resetOffset() {
        viewModelScope.launch { overrides.setOffset(songId, 0) }
    }

    fun setTextScale(percent: Int) {
        viewModelScope.launch { settings.update { it.copy(lyricsTextScale = percent.coerceIn(AppSettings.LYRICS_TEXT_SCALES)) } }
    }

    fun setLineSpacing(tenths: Int) {
        viewModelScope.launch { settings.update { it.copy(lyricsLineSpacing = tenths.coerceIn(AppSettings.LYRICS_SPACINGS)) } }
    }

    private companion object {
        const val PROGRESS_MS = 200L
        const val MAX_IMPORT_BYTES = 256 * 1024
        const val BUFFER = 8 * 1024
    }
}

/**
 * Lyrics Studio: full-screen lyrics with the playing line highlighted and scrolled into view,
 * import (.lrc or .txt), edit, per-song timing offset, text size and line spacing, over a
 * background in the cover's cached colour (kept readable). The highlight recomposes only when
 * the active line changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsStudioScreen(navigator: Navigator, viewModel: LyricsStudioViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val isCurrent by viewModel.isCurrent.collectAsStateWithLifecycle()
    val progress = viewModel.progress.collectAsStateWithLifecycle()
    val textScale by viewModel.textScale.collectAsStateWithLifecycle()
    val spacing by viewModel.lineSpacing.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var showText by rememberSaveable { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importFrom) }
    // .lrc has no registered type on most devices, so any file is offered; the import checks size and decodes text itself.
    val pick = { importer.launch(arrayOf("*/*")) }

    val ready = ui as? LyricsStudioUi.Ready
    val background = ready?.let { lyricsBackground(it.song) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(ready?.song?.title ?: "Lyrics", maxLines = 1) },
                navigationIcon = { BackButton(navigator::back) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    if (ready != null) {
                        IconButton(onClick = pick) { Icon(Icons.Rounded.FileOpen, contentDescription = "Import lyrics file") }
                        IconButton(onClick = { showText = true }) { Icon(Icons.Rounded.TextFields, contentDescription = "Text size and spacing") }
                        var open by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More lyrics options") }
                            DropdownMenu(open, onDismissRequest = { open = false }) {
                                DropdownMenuItem(text = { Text(if (ready.lyrics == null) "Write lyrics" else "Edit lyrics") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { open = false; editing = true })
                                if (ready.lyrics?.source == LyricsSource.IMPORTED) {
                                    DropdownMenuItem(text = { Text("Remove imported lyrics") }, onClick = { open = false; viewModel.removeImported() })
                                }
                                if ((ready.lyrics?.offsetMs ?: 0L) != 0L) {
                                    DropdownMenuItem(text = { Text("Reset timing") }, onClick = { open = false; viewModel.resetOffset() })
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().then(if (background != null) Modifier.background(background) else Modifier).padding(padding)) {
            when (val state = ui) {
                LyricsStudioUi.Loading -> SkeletonList(artwork = false)
                LyricsStudioUi.NoSong -> EmptyState(Icons.Rounded.Lyrics, "Song not found", "It may have been moved or deleted.")
                is LyricsStudioUi.Ready -> {
                    val l = state.lyrics
                    if (l == null) {
                        EmptyState(
                            Icons.Rounded.Lyrics, "No lyrics for this song",
                            "Import an .lrc or .txt file, or write them yourself. SVARĀ never looks lyrics up online, and your audio file isn't changed.",
                            action = {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                    Button(onClick = pick, modifier = Modifier.heightIn(min = Spacing.touch)) { Text("Import") }
                                    OutlinedButton(onClick = { editing = true }, modifier = Modifier.heightIn(min = Spacing.touch)) { Text("Write") }
                                }
                            },
                        )
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            LyricsBody(l, isCurrent, progress, textScale, spacing, onSeek = viewModel::seekTo, modifier = Modifier.weight(1f))
                            if (l.timed.isNotEmpty()) OffsetBar(l.offsetMs, isCurrent, onNudge = viewModel::nudgeOffset, onPlay = viewModel::play)
                        }
                    }
                }
            }
        }
    }
    if (editing && ready != null) {
        LyricsEditor(ready.lyrics?.raw.orEmpty(), onSave = { viewModel.saveText(it); editing = false }, onDismiss = { editing = false })
    }
    if (showText) {
        TextSettingsDialog(textScale, spacing, viewModel::setTextScale, viewModel::setLineSpacing) { showText = false }
    }
}

@Composable
private fun LyricsBody(
    lyrics: Lyrics,
    isCurrent: Boolean,
    progress: androidx.compose.runtime.State<PlaybackProgress>,
    textScale: Int,
    spacing: Int,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
) {
    val base = MaterialTheme.typography.headlineSmall
    val style = base.copy(fontSize = base.fontSize * (textScale / PERCENT), lineHeight = (spacing / TENTHS).em)
    val scheme = MaterialTheme.colorScheme
    val timed = lyrics.timed
    val listState = rememberLazyListState()
    if (timed.isEmpty()) {
        LazyColumn(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.xl)) {
            item { Text(lyrics.text, style = style, color = scheme.onSurface) }
            item { SourceNote(lyrics) }
        }
        return
    }
    // Only a change of line recomposes the list; position ticks alone don't.
    val active by remember(timed, lyrics.offsetMs, isCurrent) {
        derivedStateOf { if (!isCurrent) -1 else LyricsText.activeIndex(timed, progress.value.positionMs - lyrics.offsetMs) }
    }
    val reduced = LocalReducedMotion.current || !LocalAppearance.current.motion.enabled
    LaunchedEffect(active) {
        if (active >= 0) {
            val target = (active - LyricsTokens.LINES_ABOVE_ACTIVE).coerceAtLeast(0)
            if (reduced) listState.scrollToItem(target) else listState.animateScrollToItem(target)
        }
    }
    LazyColumn(modifier.fillMaxWidth(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.xl, vertical = Spacing.l)) {
        itemsIndexed(timed, key = { i, line -> "$i-${line.timeMs}" }) { i, line ->
            val isActive = i == active
            Text(
                line.text.ifBlank { "♪" },
                style = if (isActive) style.copy(fontWeight = FontWeight.Bold) else style,
                color = when {
                    isActive -> scheme.primary
                    i < active -> scheme.onSurfaceVariant.copy(alpha = LyricsTokens.PAST_ALPHA)
                    else -> scheme.onSurface
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isCurrent, onClickLabel = "Play from this line") { onSeek(line.timeMs + lyrics.offsetMs) }
                    .padding(vertical = Spacing.xs)
                    .semantics { if (isActive) { stateDescription = "Now singing"; liveRegion = LiveRegionMode.Polite } },
            )
        }
        item { SourceNote(lyrics) }
    }
}

@Composable
private fun SourceNote(lyrics: Lyrics) {
    Text(
        when (lyrics.source) {
            LyricsSource.EMBEDDED -> "From the file's tags"
            LyricsSource.SIDECAR_FILE -> "From a lyrics file next to the song"
            LyricsSource.IMPORTED -> "Imported in SVARĀ (the audio file is unchanged)"
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.l),
    )
}

/** Timing offset: nudge lines earlier or later in 0.1 s and 0.5 s steps; saved per song. */
@Composable
private fun OffsetBar(offsetMs: Long, isCurrent: Boolean, onNudge: (Long) -> Unit, onPlay: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        if (!isCurrent) {
            TextButton(onClick = onPlay, modifier = Modifier.heightIn(min = Spacing.touch)) { Text("Play this song to follow along") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(-LyricsTokens.BIG_STEP_MS, -LyricsTokens.SMALL_STEP_MS).forEach { d ->
                TextButton(onClick = { onNudge(d) }, modifier = Modifier.heightIn(min = Spacing.touch)) { Text(stepLabel(d)) }
            }
            Text(
                "Timing ${if (offsetMs >= 0) "+" else "−"}${"%.1f".format(kotlin.math.abs(offsetMs) / MS_PER_S)} s",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(top = Spacing.m).semantics { heading() },
            )
            listOf(LyricsTokens.SMALL_STEP_MS, LyricsTokens.BIG_STEP_MS).forEach { d ->
                TextButton(onClick = { onNudge(d) }, modifier = Modifier.heightIn(min = Spacing.touch)) { Text(stepLabel(d)) }
            }
        }
    }
}

private fun stepLabel(d: Long) = (if (d > 0) "+" else "−") + "%.1f".format(kotlin.math.abs(d) / MS_PER_S)

@Composable
private fun LyricsEditor(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lyrics") },
        text = {
            Column {
                Text(
                    "Plain text, or LRC with times like [01:23.45]. Saved in SVARĀ only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = LyricsTokens.EDITOR_LINES, maxLines = LyricsTokens.EDITOR_LINES * 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TextSettingsDialog(scale: Int, spacing: Int, onScale: (Int) -> Unit, onSpacing: (Int) -> Unit, onDismiss: () -> Unit) {
    var s by remember { mutableFloatStateOf(scale.toFloat()) }
    var sp by remember { mutableFloatStateOf(spacing.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lyrics text") },
        text = {
            Column {
                Text("Size: ${s.toInt()} %")
                Slider(
                    value = s, onValueChange = { s = it }, onValueChangeFinished = { onScale(s.toInt()) },
                    valueRange = AppSettings.LYRICS_TEXT_SCALES.first.toFloat()..AppSettings.LYRICS_TEXT_SCALES.last.toFloat(),
                )
                Text("Line spacing: ${"%.1f".format(sp / TENTHS)}×")
                Slider(
                    value = sp, onValueChange = { sp = it }, onValueChangeFinished = { onSpacing(sp.toInt()) },
                    valueRange = AppSettings.LYRICS_SPACINGS.first.toFloat()..AppSettings.LYRICS_SPACINGS.last.toFloat(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/**
 * A soft wash in the cover's cached colour from the top, at most as strong as text allows
 * (computed against the theme's text colours). None with high contrast or without cached palettes.
 */
@Composable
private fun lyricsBackground(song: Song): Brush? {
    val a = LocalAppearance.current
    if (a.highContrast || BackgroundKind.CACHED_PALETTE !in a.limits.backgrounds) return null
    val colour = rememberAlbumColour(song.id, song.albumId) ?: return null
    val scheme = MaterialTheme.colorScheme
    val alpha = remember(colour, scheme.background, scheme.onSurface, scheme.onSurfaceVariant, scheme.primary) {
        ContrastEngine.maxTintAlpha(
            colour.argb(), scheme.background.argb(),
            listOf(scheme.onSurface.argb(), scheme.onSurfaceVariant.argb(), scheme.primary.argb()),
            Contrast.AA_TEXT, LyricsTokens.BACKGROUND_ALPHA.toDouble(),
        ).toFloat()
    }
    return Brush.verticalGradient(listOf(colour.copy(alpha = alpha), colour.copy(alpha = alpha * LyricsTokens.BACKGROUND_FADE)))
}

private fun Color.argb() = Argb(toArgb().toLong() and ARGB_MASK)

private const val ARGB_MASK = 0xFFFFFFFFL
private const val PERCENT = 100f
private const val TENTHS = 10f
private const val MS_PER_S = 1000.0
