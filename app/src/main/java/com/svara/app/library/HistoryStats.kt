package com.svara.app.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.foundation.clickable
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.svara.app.domain.model.ArtistStat
import com.svara.app.domain.model.HistoryEntry
import com.svara.app.domain.model.PlayCount
import com.svara.app.domain.model.WeekStat
import com.svara.app.domain.repository.HistoryRepository
import com.svara.app.domain.repository.SettingsRepository
import com.svara.app.playback.PlaybackController
import com.svara.app.playback.UserMessages
import com.svara.app.ui.components.WithSongActions
import com.svara.app.ui.components.BackButton
import com.svara.app.ui.components.EmptyState
import com.svara.app.ui.components.SongRow
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.states.SkeletonList
import com.svara.app.ui.theme.Radii
import com.svara.app.ui.theme.Spacing
import com.svara.app.ui.theme.StatsTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val settings: SettingsRepository,
    private val playback: PlaybackController,
    private val messages: UserMessages,
) : ViewModel() {
    val entries: StateFlow<List<HistoryEntry>?> = history.history(HISTORY_LIMIT).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val enabled: StateFlow<Boolean> = settings.settings.map { it.historyEnabled }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setEnabled(on: Boolean) {
        viewModelScope.launch { settings.update { it.copy(historyEnabled = on) } }
    }

    fun remove(entry: HistoryEntry) {
        viewModelScope.launch { history.deleteEntry(entry.eventId); messages.emit("Removed from history") }
    }

    fun eraseAll() {
        viewModelScope.launch { history.clear(); messages.emit("Listening history erased") }
    }

    fun play(entries: List<HistoryEntry>, index: Int) = playback.playSongs(entries.map { it.song }, index)

    private companion object {
        const val HISTORY_LIMIT = 500
    }
}

/** Listening history, newest first, grouped by day. Pause and erase are right here; nothing leaves the device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navigator: Navigator, viewModel: HistoryViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    var confirmErase by rememberSaveable { mutableStateOf(false) }
    WithSongActions(navigator) { actions ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Listening history") },
                    navigationIcon = { BackButton(navigator::back) },
                    actions = {
                        IconButton(onClick = navigator::stats) { Icon(Icons.Rounded.BarChart, contentDescription = "Statistics") }
                        TextButton(onClick = { confirmErase = true }, enabled = !entries.isNullOrEmpty()) { Text("Erase") }
                    },
                )
            },
        ) { padding ->
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item(key = "toggle") {
                    Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touch).padding(horizontal = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Record listening history", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (enabled) "Stored only on this device" else "Paused: new plays aren't recorded",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = enabled, onCheckedChange = viewModel::setEnabled)
                    }
                }
                val list = entries
                when {
                    list == null -> item(key = "loading") { SkeletonList() }
                    list.isEmpty() -> item(key = "empty") {
                        EmptyState(Icons.Rounded.History, "No history yet", "Songs you listen to for 30 seconds or half their length appear here.")
                    }
                    else -> {
                        val days = list.withIndex().groupBy { dayStart(it.value.playedAt) }
                        days.forEach { (day, rows) ->
                            item(key = "d$day") {
                                Text(
                                    dayLabel(day), style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s).semantics { heading() },
                                )
                            }
                            items(rows, key = { it.value.eventId }) { (index, entry) ->
                                SongRow(
                                    song = entry.song,
                                    actions = actions,
                                    onClick = { viewModel.play(list, index) },
                                    extraMenuItems = { dismiss ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Remove from history") },
                                            leadingIcon = { Icon(Icons.Rounded.Close, null) },
                                            onClick = { viewModel.remove(entry); dismiss() },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase listening history?") },
            text = { Text("Recently played, most played, statistics and history-based smart playlists start over. Playlists and favourites aren't affected.") },
            confirmButton = { TextButton(onClick = { viewModel.eraseAll(); confirmErase = false }) { Text("Erase") } },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancel") } },
        )
    }
}

enum class StatsRange(val label: String, val days: Int) { MONTH("30 days", 30), YEAR("12 months", 365), ALL("All time", 0) }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(private val history: HistoryRepository) : ViewModel() {
    private val _range = MutableStateFlow(StatsRange.MONTH)
    val range: StateFlow<StatsRange> = _range

    private fun since(r: StatsRange) = if (r.days == 0) 0L else System.currentTimeMillis() - r.days * DAY_MS

    val topSongs: StateFlow<List<PlayCount>?> = history.mostPlayed(TOP).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val topArtists: StateFlow<List<ArtistStat>?> = _range.flatMapLatest { history.topArtists(since(it), TOP) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val weeks: StateFlow<List<WeekStat>?> = history.weeks(System.currentTimeMillis() - WEEKS * WEEK_MS, weekOffsetMs())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val timedSince: StateFlow<Long?> = history.timedSince.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setRange(r: StatsRange) {
        _range.value = r
    }

    companion object {
        const val TOP = 10
        const val WEEKS = 8
        const val DAY_MS = 86_400_000L
        const val WEEK_MS = 7 * DAY_MS

        /** Shift so weekly buckets start at local midnight on the locale's first day of the week. */
        fun weekOffsetMs(): Long {
            val c = Calendar.getInstance()
            c.set(Calendar.DAY_OF_WEEK, c.firstDayOfWeek)
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            return Math.floorMod(c.timeInMillis, WEEK_MS)
        }
    }
}

/** Local-only statistics: top songs and artists, and listening time per week. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(navigator: Navigator, viewModel: StatsViewModel = hiltViewModel()) {
    val range by viewModel.range.collectAsStateWithLifecycle()
    val songs by viewModel.topSongs.collectAsStateWithLifecycle()
    val artists by viewModel.topArtists.collectAsStateWithLifecycle()
    val weeks by viewModel.weeks.collectAsStateWithLifecycle()
    val since by viewModel.timedSince.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Statistics") }, navigationIcon = { BackButton(navigator::back) }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item(key = "recap") {
                androidx.compose.material3.ListItem(
                    leadingContent = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.AutoGraph, null) },
                    headlineContent = { Text("Recap") },
                    supportingContent = { Text("Your week, month and year in music") },
                    modifier = Modifier.heightIn(min = Spacing.touch).clickable(onClick = navigator::recap),
                )
            }
            item(key = "note") {
                Text(
                    "Worked out on this device from your listening history. Nothing is sent anywhere.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                )
                Heading("Listening time per week")
                val w = weeks
                when {
                    w == null -> SkeletonList(rows = 2, artwork = false)
                    since == null -> Note("Listening time is recorded from this version on; play some music and it appears here.")
                    else -> WeekChart(w)
                }
                since?.let { Note("Recorded since ${DateFormat.getDateInstance().format(Date(it))}.") }
                Heading("Top artists")
                Row(Modifier.padding(horizontal = Spacing.l)) {
                    StatsRange.entries.forEach { r ->
                        FilterChip(selected = range == r, onClick = { viewModel.setRange(r) }, label = { Text(r.label) }, modifier = Modifier.padding(end = Spacing.s))
                    }
                }
            }
            val a = artists
            when {
                a == null -> item(key = "a-loading") { SkeletonList(rows = 3, artwork = false) }
                a.isEmpty() -> item(key = "a-empty") { Note("No plays in this period.") }
                else -> items(a, key = { "a" + it.artist }) { s -> StatRow(s.artist, "${s.plays} plays" + listenedSuffix(s.listenedMs)) }
            }
            item(key = "songs-h") { Heading("Top songs (since statistics were last reset)") }
            val sg = songs
            when {
                sg == null -> item(key = "s-loading") { SkeletonList(rows = 3) }
                sg.isEmpty() -> item(key = "s-empty") { Note("No plays yet.") }
                else -> items(sg, key = { "s" + it.song.id }) { pc -> StatRow(pc.song.title, "${pc.song.displayArtist} • ${pc.count} plays") }
            }
        }
    }
}

@Composable
private fun WeekChart(weeks: List<WeekStat>) {
    val now = System.currentTimeMillis()
    val offset = StatsViewModel.weekOffsetMs()
    val currentWeek = Math.floorDiv(now - offset, StatsViewModel.WEEK_MS)
    val byWeek = weeks.associateBy { Math.floorDiv(it.weekStart - offset, StatsViewModel.WEEK_MS) }
    val bars = (StatsViewModel.WEEKS - 1 downTo 0).map { back -> byWeek[currentWeek - back]?.listenedMs ?: 0L }
    val max = (bars.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val colour = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val description = bars.mapIndexed { i, ms -> "${StatsViewModel.WEEKS - 1 - i} weeks ago: ${hours(ms)}" }.reversed().joinToString("; ")
    Column(Modifier.padding(horizontal = Spacing.l)) {
        Canvas(Modifier.fillMaxWidth().height(StatsTokens.chartHeight).semantics { contentDescription = "Listening per week. $description" }) {
            val gap = StatsTokens.barGap.toPx()
            val w = (size.width - gap * (bars.size - 1)) / bars.size
            bars.forEachIndexed { i, ms ->
                val x = i * (w + gap)
                drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(Radii.small.toPx()))
                val h = size.height * ms / max
                if (h > 0f) drawRoundRect(colour, Offset(x, size.height - h), Size(w, h), CornerRadius(Radii.small.toPx()))
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(Modifier.fillMaxWidth()) {
            Text("${StatsViewModel.WEEKS} weeks ago", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("This week: ${hours(bars.last())}", style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun hours(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}

private fun listenedSuffix(ms: Long) = if (ms > 0) " • ${hours(ms)}" else ""

@Composable
private fun StatRow(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().heightIn(min = Spacing.touch).padding(horizontal = Spacing.l, vertical = Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.l, bottom = Spacing.s).semantics { heading() })
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs))
}

private fun dayStart(ms: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    return c.timeInMillis
}

private fun dayLabel(day: Long): String {
    val today = dayStart(System.currentTimeMillis())
    return when (day) {
        today -> "Today"
        today - StatsViewModel.DAY_MS -> "Yesterday"
        else -> DateFormat.getDateInstance(DateFormat.FULL).format(Date(day))
    }
}

