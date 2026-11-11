package com.svara.app.settings

import com.svara.app.domain.audio.FadeCurve
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.History
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsOff
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.svara.app.ui.notificationsAllowed
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svara.app.BuildConfig
import com.svara.app.domain.model.AppSettings
import com.svara.app.domain.model.FolderRule
import com.svara.app.domain.model.FolderRuleType
import com.svara.app.domain.model.SleepTimerOption
import com.svara.app.domain.model.SystemFolders
import com.svara.app.ui.components.BackButton
import com.svara.app.ui.components.EmptyState
import com.svara.app.ui.components.SectionHeader
import com.svara.app.ui.components.formatBytes
import com.svara.app.ui.components.formatTotalDuration
import com.svara.app.ui.navigation.Navigator
import com.svara.app.appearance.NavTab
import com.svara.app.ui.theme.LocalAppearance
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navigator: Navigator, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lastScan by viewModel.lastScanAt.collectAsStateWithLifecycle()
    val scanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var notificationsOn by remember { mutableStateOf(true) }
    // Re-checked on every resume, so returning from system settings updates the row.
    LifecycleResumeEffect(Unit) {
        notificationsOn = context.notificationsAllowed()
        onPauseOrDispose { }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(viewModel::exportBackup) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importBackup) }
    val crashExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let(viewModel::exportCrashLog) }
    fun set(transform: (AppSettings) -> AppSettings) = viewModel.update(transform)

    var section by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = section != null) { section = null }
    val appearance = LocalAppearance.current
    val revoked = rules.filter { it.treeUri != null && !it.accessGranted }

    // Every reachable setting, searchable by title and common words. Each hit opens its screen.
    fun open(key: String) {
        if (key == "about") navigator.about() else section = key
    }

    if (section == null) {
        val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
            topBar = { LargeTopAppBar(title = { Text("Settings") }, scrollBehavior = scroll) },
        ) { padding ->
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search settings") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                val q = query.trim().lowercase()
                if (q.isNotEmpty()) {
                    val hits = searchSettings(q)
                    item {
                        SettingsCard {
                            if (hits.isEmpty()) Note("No settings match “$query”.")
                            hits.forEachIndexed { i, e ->
                                if (i > 0) HorizontalDivider()
                                ClickRow(Icons.Rounded.Search, e.title, sectionTitle(e.section), onClick = { open(e.section) })
                            }
                        }
                    }
                } else {
                    item {
                        SettingsCard {
                            CategoryRow(Icons.Rounded.Palette, MaterialTheme.colorScheme.primary, "Appearance", "${appearance.preset.displayName} · ${appearance.limits.mode.label}") { open("appearance") }
                            HorizontalDivider()
                            CategoryRow(
                                Icons.Rounded.PlayCircle, MaterialTheme.colorScheme.secondary, "Playback",
                                (if (settings.crossfadeSec == 0) "Crossfade off" else "Crossfade ${settings.crossfadeSec} s") + " · sleep ${settings.defaultSleepTimer.label}",
                            ) { open("playback") }
                            HorizontalDivider()
                            CategoryRow(
                                Icons.Rounded.Equalizer, MaterialTheme.colorScheme.tertiary, "Audio",
                                "Equalizer ${if (settings.eq.enabled) "on" else "off"} · levelling ${if (settings.volumeLeveling) "on" else "off"}",
                            ) { open("audio") }
                        }
                    }
                    item {
                        SettingsCard {
                            CategoryRow(
                                Icons.Rounded.Folder, MaterialTheme.colorScheme.primary, "Library",
                                when {
                                    revoked.isNotEmpty() -> "A folder lost access"
                                    scanning -> "Scanning…"
                                    rules.isEmpty() -> "All folders on this device"
                                    else -> "${rules.size} folder rule${if (rules.size == 1) "" else "s"}"
                                },
                            ) { open("library") }
                            HorizontalDivider()
                            CategoryRow(
                                Icons.Rounded.Storage, MaterialTheme.colorScheme.secondary, "Backup and data",
                                if (settings.historyEnabled) "History on this device only" else "History paused",
                            ) { open("data") }
                            HorizontalDivider()
                            CategoryRow(Icons.Rounded.Info, MaterialTheme.colorScheme.tertiary, "About and help", "Version ${BuildConfig.VERSION_NAME}") { open("about") }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    } else {
        val key = section.orEmpty()
        SettingsSubScreen(sectionTitle(key), onBack = { section = null }) {
            when (key) {
                "appearance" -> {
                    val labels = appearance.theme.spec.tokens.navigation
                    val hidden = appearance.tabUniverse.filter { it !in appearance.visibleTabs && it != NavTab.SETTINGS }
                    val startLabel = if (settings.startScreen == AppSettings.START_LAST) "Where I left off" else NavTab.entries.firstOrNull { it.name == settings.startScreen }?.label ?: "Where I left off"
                    val rows: List<@Composable () -> Unit> = listOf<@Composable () -> Unit>(
                        { ClickRow(Icons.Rounded.Palette, "Theme, colours and type", "${appearance.preset.displayName} · ${appearance.limits.mode.label}", onClick = navigator::appearance) },
                        { SwitchRow("High contrast", "Stronger colours and outlines", settings.highContrast) { on -> set { it.copy(highContrast = on) } } },
                        { SwitchRow("Reduce motion", "Turn off sliding and fading transitions", settings.reducedMotion) { on -> set { it.copy(reducedMotion = on) } } },
                        { ClickRow(Icons.Rounded.Home, "Start screen", startLabel, onClick = { dialog = "start" }) },
                        { SwitchRow("Left-handed layout", "Mirror the player's secondary controls", settings.leftHanded) { on -> set { it.copy(leftHanded = on) } } },
                        { SwitchRow("One-handed search", "A search button within thumb reach at the bottom of the main screens", settings.oneHandedSearch) { on -> set { it.copy(oneHandedSearch = on) } } },
                    ) + hidden.map { tab ->
                        @Composable { ClickRow(Icons.AutoMirrored.Rounded.ArrowForward, labels.label(tab), "Hidden from the bottom bar", onClick = { navigator.open(tab) }) }
                    }
                    Group(rows)
                    Note("Text follows your system font size (adjust further in Theme › Typography). All controls work with TalkBack.")
                }
                "playback" -> {
                    Group(
                        listOf(
                            { SwitchRow("Resume where you left off", "Restore the queue (paused) when SVARĀ starts", settings.restoreQueueOnLaunch) { on -> set { it.copy(restoreQueueOnLaunch = on) } } },
                            {
                                SliderRow(
                                    title = "Crossfade",
                                    valueLabel = if (settings.crossfadeSec == 0) "Off" else "${settings.crossfadeSec} s",
                                    value = settings.crossfadeSec.toFloat(),
                                    range = 0f..12f,
                                    steps = 11,
                                    onChange = { v -> set { it.copy(crossfadeSec = v.toInt()) } },
                                    note = "Fades the end of one song out and the next one in.",
                                )
                            },
                            { ClickRow(Icons.Rounded.Bedtime, "Default sleep timer", settings.defaultSleepTimer.label, onClick = { dialog = "sleep" }) },
                            { SwitchRow("Fade out before sleep", "Lower the volume over the last 30 seconds", settings.sleepFadeOut) { on -> set { it.copy(sleepFadeOut = on) } } },
                            { SwitchRow("Keep albums gapless", "Never fade between songs of the same album", settings.gaplessAlbums) { on -> set { it.copy(gaplessAlbums = on) } } },
                            { SwitchRow("Preserve pitch when changing speed", "Best for podcasts and audiobooks", settings.preservePitch) { on -> set { it.copy(preservePitch = on) } } },
                            {
                                ClickRow(
                                    Icons.Rounded.Timer, "Pause after a long session",
                                    if (settings.autoPauseMinutes == 0) "Off" else "After ${settings.autoPauseMinutes} minutes of continuous playback",
                                    onClick = { dialog = "autoPause" },
                                )
                            },
                            { ClickRow(Icons.Rounded.Tune, "Crossfade curve", if (settings.crossfadeSec > 0) settings.crossfadeCurve.label else "${settings.crossfadeCurve.label} · crossfade is off", onClick = { dialog = "curve" }) },
                            { SwitchRow("Resume when headphones reconnect", "Only after they were unplugged during playback", settings.resumeOnHeadphones) { on -> set { it.copy(resumeOnHeadphones = on) } } },
                            { SwitchRow("Stop when Bluetooth disconnects", "Stops playback and clears the notification", settings.stopOnBluetoothDisconnect) { on -> set { it.copy(stopOnBluetoothDisconnect = on) } } },
                            { SwitchRow("Headset button presses", "Press twice for the next song, three times for the previous one", settings.headsetMultiPress) { on -> set { it.copy(headsetMultiPress = on) } } },
                        ),
                    )
                    Note("Rituals never override calls, alarms or other apps playing audio.")
                }
                "audio" -> Group(
                    listOf(
                        { ClickRow(Icons.Rounded.Equalizer, "Equalizer and effects", if (settings.eq.enabled) "On" else "Off", onClick = navigator::equalizer) },
                        { SwitchRow("Volume levelling", "Use ReplayGain tags when a file has them", settings.volumeLeveling) { on -> set { it.copy(volumeLeveling = on) } } },
                        { SwitchRow("Skip silence", "Shorten silent gaps in recordings", settings.skipSilence) { on -> set { it.copy(skipSilence = on) } } },
                        { ClickRow(Icons.Rounded.GraphicEq, "ZENITH visualiser", "Waveform, spectrum, circular, pulse, aura and full screen", onClick = navigator::visualiser) },
                    ),
                )
                "library" -> Group(
                    listOf(
                        {
                            ClickRow(
                                Icons.Rounded.Folder, "Music folders",
                                when {
                                    revoked.isNotEmpty() -> "“${revoked.first().key.displayName}” lost access — tap to restore"
                                    rules.isEmpty() -> "All folders on this device"
                                    else -> "${rules.size} rule${if (rules.size == 1) "" else "s"}"
                                },
                                onClick = navigator::folders,
                                warning = revoked.isNotEmpty(),
                            )
                        },
                        {
                            ClickRow(
                                Icons.Rounded.Refresh, "Scan for new music",
                                if (scanning) "Scanning…" else if (lastScan > 0) "Last scanned ${DateUtils.getRelativeTimeSpanString(lastScan)}" else "Not scanned yet",
                                onClick = viewModel::rescan,
                            )
                        },
                        { SwitchRow("Hide system sounds", "Ringtones, notifications, alarms and recordings folders", settings.excludeSystemFolders, onChange = viewModel::setExcludeSystemFolders) },
                        {
                            ClickRow(
                                Icons.Rounded.Timer, "Hide short audio",
                                if (settings.minTrackDurationSec == 0) "Show everything" else "Hide files shorter than ${settings.minTrackDurationSec} s",
                                onClick = { dialog = "minDuration" },
                            )
                        },
                        { ClickRow(Icons.Rounded.History, "Listening history", if (settings.historyEnabled) "On this device only" else "Paused", onClick = navigator::history) },
                        { ClickRow(Icons.Rounded.BarChart, "Statistics", "Top songs and artists, listening time per week", onClick = navigator::stats) },
                        { ClickRow(Icons.AutoMirrored.Rounded.FactCheck, "Library Doctor", "Fix names and titles, find missing artwork, duplicates and missing files", onClick = navigator::libraryCheck) },
                        { ClickRow(Icons.Rounded.RestoreFromTrash, "Recently deleted playlists", "Kept for 30 days", onClick = navigator::recentlyDeleted) },
                        { ClickRow(Icons.Rounded.BarChart, "Recap", "Your week, month and year in music", onClick = navigator::recap) },
                        { SwitchRow("Recap: auto-advance", "Move to the next card every few seconds", settings.recapAutoAdvance) { on -> set { it.copy(recapAutoAdvance = on) } } },
                        { SwitchRow("Recap: show card on Home", "When a recap has enough plays", settings.recapHomeCard) { on -> set { it.copy(recapHomeCard = on) } } },
                    ),
                )
                "data" -> Group(
                    listOf(
                        { ClickRow(Icons.Rounded.Lock, "Privacy", "History, app lock and what's stored", onClick = navigator::privacy) },
                        { ClickRow(Icons.Rounded.Storage, "Storage", "Database and caches", onClick = navigator::storage) },
                        { ClickRow(Icons.Rounded.FileUpload, "Export backup", "Settings, playlists, favourites and history in one file. Audio and cover images are never exported.", onClick = { exporter.launch("svara-backup.json") }) },
                        { ClickRow(Icons.Rounded.RestoreFromTrash, "Restore from backup", "Choose a SVARĀ backup file", onClick = { dialog = "restore" }) },
                        { ClickRow(Icons.Rounded.Warning, "Crash log", "Kept on this device only", onClick = { dialog = "crash" }) },
                        {
                            ClickRow(
                                if (notificationsOn) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff,
                                "Notifications",
                                if (notificationsOn) "On" else "Off — playback still works, but nothing shows in the notification shade",
                                onClick = {
                                    context.startActivity(
                                        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                },
                                warning = !notificationsOn,
                            )
                        },
                    ),
                )
            }
        }
    }

    when (dialog) {
        "sleep" -> ChoiceDialog("Default sleep timer", SleepTimerOption.entries, settings.defaultSleepTimer, { it.label },
            onSelect = { viewModel.setDefaultSleepTimer(it); dialog = null }, onDismiss = { dialog = null })
        "minDuration" -> ChoiceDialog("Hide short audio", listOf(0, 10, 30, 60), settings.minTrackDurationSec,
            { if (it == 0) "Show everything" else "Shorter than $it seconds" },
            onSelect = { sec -> set { it.copy(minTrackDurationSec = sec) }; dialog = null }, onDismiss = { dialog = null })
        "curve" -> ChoiceDialog("Crossfade curve", FadeCurve.entries, settings.crossfadeCurve, { it.label },
            onSelect = { c -> set { it.copy(crossfadeCurve = c) }; dialog = null }, onDismiss = { dialog = null })
        "start" -> {
            val tabs = LocalAppearance.current.tabUniverse
            ChoiceDialog("Open SVARĀ on", listOf(AppSettings.START_LAST) + tabs.map { it.name }, settings.startScreen,
                { name -> if (name == AppSettings.START_LAST) "Where I left off" else NavTab.entries.first { it.name == name }.label },
                onSelect = { v -> set { it.copy(startScreen = v) }; dialog = null }, onDismiss = { dialog = null })
        }
        "restore" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Restore from backup?") },
            text = { Text("Your current settings are replaced by the backup's. Playlists, smart playlists, favourites and listening history from the backup are added to what you have. Folder access and cover images are not restored. Nothing is deleted.") },
            confirmButton = { TextButton(onClick = { dialog = null; importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Choose file") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        "crash" -> {
            val log = remember { viewModel.crashLog() }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Crash log") },
                text = {
                    Text(
                        log.ifBlank { "No crashes recorded." },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    if (log.isNotBlank()) TextButton(onClick = { dialog = null; crashExporter.launch("svara-crash-log.txt") }) { Text("Export") }
                    else TextButton(onClick = { dialog = null }) { Text("Close") }
                },
                dismissButton = {
                    if (log.isNotBlank()) TextButton(onClick = { dialog = "crashClear" }) { Text("Clear") }
                },
            )
        }
        "crashClear" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Clear the crash log?") },
            text = { Text("This deletes every saved crash summary on this device. Your music, playlists and settings are not affected.") },
            confirmButton = { TextButton(onClick = { viewModel.clearCrashLog(); dialog = null }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        "autoPause" -> ChoiceDialog("Pause after", listOf(0, 30, 60, 90, 120, 180), settings.autoPauseMinutes,
            { if (it == 0) "Off" else "$it minutes" },
            onSelect = { m -> set { it.copy(autoPauseMinutes = m) }; dialog = null }, onDismiss = { dialog = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(navigator: Navigator, viewModel: SettingsViewModel = hiltViewModel()) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    var confirmClearDb by rememberSaveable { mutableStateOf(false) }
    var confirmClearCovers by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshStorage() }
    if (confirmClearCovers) {
        AlertDialog(
            onDismissRequest = { confirmClearCovers = false },
            title = { Text("Remove all custom covers?") },
            text = { Text("Every song and album shows its original artwork again. Your audio files were never changed.") },
            confirmButton = { TextButton(onClick = { viewModel.clearCustomCovers(); confirmClearCovers = false }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmClearCovers = false }) { Text("Cancel") } },
        )
    }
    if (confirmClearDb) {
        AlertDialog(
            onDismissRequest = { confirmClearDb = false },
            title = { Text("Clear the library cache?") },
            text = { Text("SVARĀ will forget the cached song tags and scan your music again. Your music files, playlists, favorites and history are not affected.") },
            confirmButton = { TextButton(onClick = { viewModel.clearLibraryCache(); confirmClearDb = false }) { Text("Clear and rescan") } },
            dismissButton = { TextButton(onClick = { confirmClearDb = false }) { Text("Cancel") } },
        )
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Storage") }, navigationIcon = { BackButton(navigator::back) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Note("SVARĀ stores references and metadata only. Your music files stay where they are and are never copied.")
            val s = stats
            InfoRow("Your music", if (s == null) "…" else "${s.songCount} songs • ${formatBytes(s.totalBytes)} • ${formatTotalDuration(s.totalDurationMs)}")
            ListItem(
                headlineContent = { Text("Database") },
                supportingContent = {
                    Text("${formatBytes(storage.databaseBytes)} — cached song tags, plus your playlists, favorites, history and settings. " +
                        "Clear empties the cached tags and rescans; your playlists, favorites and history are kept (clear history in Privacy).")
                },
                trailingContent = { TextButton(onClick = { confirmClearDb = true }) { Text("Clear") } },
            )
            ListItem(
                headlineContent = { Text("Artwork cache") },
                supportingContent = { Text("${formatBytes(storage.artworkCacheBytes)} — small copies of album covers for fast scrolling") },
                trailingContent = { TextButton(onClick = viewModel::clearArtworkCache) { Text("Clear") } },
            )
            ListItem(
                headlineContent = { Text("Custom covers") },
                supportingContent = { Text("${formatBytes(storage.coverBytes)} — covers you chose, stored only inside SVARĀ. Clear restores the original artwork everywhere.") },
                trailingContent = { TextButton(onClick = { confirmClearCovers = true }, enabled = storage.coverBytes > 0) { Text("Clear") } },
            )
            InfoRow("Cover colours", "${storage.paletteEntries} covers — colours taken once from each cover, shared by every theme")
            InfoRow("Waveform and analysis data", "Not used yet — arrives in Phase 2B")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(navigator: Navigator, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val events by viewModel.historyEvents.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Privacy") }, navigationIcon = { BackButton(navigator::back) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text("Your music stays yours.", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
            Note("Custom covers stay on this device, inside SVARĀ's private storage. Your audio files are never changed, and covers are never uploaded or included in exports.")
            SectionHeader("Listening history")
            SwitchRow("Track listening history", "Powers recently played, most played and smart playlists", settings.historyEnabled) { on ->
                viewModel.update { it.copy(historyEnabled = on) }
            }
            SwitchRow("Hide history in the app", "Hides recently and most played from Home and Playlists", settings.hideHistory) { on ->
                viewModel.update { it.copy(hideHistory = on) }
            }
            ListItem(
                headlineContent = { Text("Clear history") },
                supportingContent = { Text("Deletes all $events listening events: recently played, most played and history-based smart playlists start over") },
                trailingContent = { TextButton(onClick = { confirmClear = true }, enabled = events > 0) { Text("Clear") } },
            )
            ListItem(
                headlineContent = { Text("Reset statistics") },
                supportingContent = {
                    Text(
                        "Play counts start from zero; recently played is kept" +
                            if (settings.statsSince > 0) ". Last reset ${android.text.format.DateUtils.getRelativeTimeSpanString(settings.statsSince)}" else ""
                    )
                },
                trailingContent = { TextButton(onClick = viewModel::resetStatistics) { Text("Reset") } },
            )
            SectionHeader("App lock")
            SwitchRow(
                "Lock library and history",
                if (viewModel.appLockAvailable) "Use your fingerprint, face or screen lock. Playback controls stay available."
                else "Set up a screen lock on this device to use the app lock.",
                settings.appLock && viewModel.appLockAvailable,
                enabled = viewModel.appLockAvailable,
            ) { on -> viewModel.update { it.copy(appLock = on) } }
            SectionHeader("What SVARĀ stores")
            listOf(
                "No internet permission. Nothing is sent anywhere, and there is no account or analytics.",
                "Reads your audio files and their tags. It never copies, changes or deletes them.",
                "Stores on this device only: playlists, favorites, listening history, the queue, settings, cached tags and artwork thumbnails.",
                "Settings → Storage shows how much space each part uses and lets you clear caches.",
                "Folder access you grant can be removed in Settings → Music folders or in system settings.",
                "Uninstalling SVARĀ deletes everything it stored. Your music files are untouched.",
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear listening history?") },
            text = { Text("All listening history is deleted: recently played, most played, and history-based smart playlists start over. Playlists and favorites are kept.") },
            confirmButton = { TextButton(onClick = { viewModel.clearHistory(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(navigator: Navigator, viewModel: SettingsViewModel = hiltViewModel()) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var pendingType by rememberSaveable { mutableStateOf(FolderRuleType.INCLUDE) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) viewModel.addFolder(uri.toString(), pendingType)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Music folders") }, navigationIcon = { BackButton(navigator::back) }) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Note(
                    "By default SVARĀ shows music from every folder. Include folders to show only those, " +
                        "or exclude folders to hide them. The most specific rule wins."
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pendingType = FolderRuleType.INCLUDE; picker.launch(null) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text("Include folder")
                    }
                    OutlinedButton(onClick = { pendingType = FolderRuleType.EXCLUDE; picker.launch(null) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text("Exclude folder")
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
            }
            if (rules.isEmpty()) {
                item { EmptyState(Icons.Rounded.Folder, "No folder rules", "All music on this device is shown.") }
            }
            items(rules, key = { it.key.id }) { rule ->
                RuleRow(rule, onReauthorize = { pendingType = rule.type; picker.launch(rule.treeUri?.let(Uri::parse)) }, onRemove = { viewModel.removeRule(rule.key) })
            }
            if (settings.excludeSystemFolders) {
                item {
                    SectionHeader("Hidden by default")
                    Note("System sound folders on every storage: ${SystemFolders.paths.joinToString { it.trimEnd('/') }}. " +
                        "Turn off “Hide system sounds” in Settings, or include one of these folders here, to show them.")
                }
            }
        }
    }
}

@Composable
private fun RuleRow(rule: FolderRule, onReauthorize: () -> Unit, onRemove: () -> Unit) {
    val revoked = rule.treeUri != null && !rule.accessGranted
    ListItem(
        leadingContent = {
            Icon(
                if (revoked) Icons.Rounded.Warning else if (rule.type == FolderRuleType.INCLUDE) Icons.Rounded.Folder else Icons.Rounded.FolderOff,
                contentDescription = null,
                tint = if (revoked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = { Text(rule.key.displayName) },
        supportingContent = {
            Column {
                Text("${if (rule.type == FolderRuleType.INCLUDE) "Included" else "Excluded"} • ${rule.key.volume}/${rule.key.path}")
                if (revoked) {
                    Text(
                        "SVARĀ lost access to this folder (it may have been revoked, or the SD card removed). " +
                            "The rule still applies, but lyrics files here can't be read. Tap below and pick the same folder to restore access.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onReauthorize) { Text("Restore access") }
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, contentDescription = "Remove rule for ${rule.key.displayName}") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navigator: Navigator) {
    Scaffold(topBar = { TopAppBar(title = { Text("About SVARĀ") }, navigationIcon = { BackButton(navigator::back) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Icon(
                painterResource(com.svara.app.R.drawable.ic_svara_mark),
                contentDescription = "SVARĀ logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Text("SVARĀ", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text("Your music. Your world.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                "SVARĀ is an offline music player. It organizes and plays the audio files already on your device. " +
                    "There is no streaming catalog, no account and no cloud library.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Theme names: ETHEREAL, Halo, SOLACE, OCTAVE and ZENITH.",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Built with Jetpack Compose, AndroidX Media3, Room, Paging, Hilt and Coil.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
internal fun ClickRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, warning: Boolean = false) {
    ListItem(
        leadingContent = { Icon(icon, null, tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.heightIn(min = 56.dp).clickable(onClick = onClick),
    )
}

@Composable
private fun InfoRow(title: String, value: String) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(value) })
}

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.heightIn(min = 56.dp).toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
internal fun SliderRow(title: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit, note: String) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local) },
            valueRange = range,
            steps = steps,
            modifier = Modifier.semantics { stateDescription = valueLabel },
        )
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(label(option), Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

internal data class SettingEntry(val title: String, val keywords: String, val section: String)

/** Every reachable setting, searchable by title and common words. Each hit opens its screen. */
internal val SETTINGS_INDEX = listOf(
        SettingEntry("Theme and colours", "appearance theme dark light colour color accent wallpaper look preset", "appearance"),
        SettingEntry("Font size and typography", "font size text typography large", "appearance"),
        SettingEntry("Blur and effects", "blur glass glow effects motion performance battery", "appearance"),
        SettingEntry("High contrast", "contrast accessibility outline", "appearance"),
        SettingEntry("Reduce motion", "motion animation accessibility battery", "appearance"),
        SettingEntry("Left-handed layout", "left hand mirror accessibility", "appearance"),
        SettingEntry("One-handed search", "search thumb reach accessibility", "appearance"),
        SettingEntry("Start screen", "start open launch home tab", "appearance"),
        SettingEntry("Resume where you left off", "resume restore queue launch", "playback"),
        SettingEntry("Crossfade", "crossfade fade transition curve", "playback"),
        SettingEntry("Keep albums gapless", "gapless album", "playback"),
        SettingEntry("Preserve pitch", "pitch speed podcast audiobook", "playback"),
        SettingEntry("Sleep timer", "sleep timer bedtime fade out", "playback"),
        SettingEntry("Pause after a long session", "pause auto session timer", "playback"),
        SettingEntry("Headphones and Bluetooth", "headphones headset bluetooth button reconnect disconnect", "playback"),
        SettingEntry("Equalizer and effects", "equalizer eq bass virtualizer loudness sound", "audio"),
        SettingEntry("Visualiser", "visualiser visualizer spectrum waveform", "audio"),
        SettingEntry("Volume levelling", "volume levelling leveling replaygain loudness", "audio"),
        SettingEntry("Skip silence", "silence skip gaps", "audio"),
        SettingEntry("Music folders", "folders library storage include exclude", "library"),
        SettingEntry("Scan for new music", "scan rescan refresh library", "library"),
        SettingEntry("Hide system sounds", "ringtones notifications alarms recordings system", "library"),
        SettingEntry("Hide short audio", "short duration clips", "library"),
        SettingEntry("Library Doctor", "doctor duplicates missing artwork fix tags", "library"),
        SettingEntry("Listening history", "history recent played", "library"),
        SettingEntry("Statistics", "statistics stats top listening time", "library"),
        SettingEntry("Recently deleted playlists", "deleted trash restore playlists", "library"),
        SettingEntry("Recap", "recap week month year summary statistics", "library"),
        SettingEntry("Recap: auto-advance", "recap auto advance story cards", "library"),
        SettingEntry("Recap: show card on Home", "recap home card", "library"),
        SettingEntry("Recap animations", "recap animations motion", "appearance"),
        SettingEntry("Privacy and app lock", "privacy lock history data", "data"),
        SettingEntry("Storage and caches", "storage cache database battery space", "data"),
        SettingEntry("Export backup", "backup export json playlists favourites history", "data"),
    SettingEntry("Restore from backup", "restore import backup", "data"),
    SettingEntry("Crash log", "crash log errors problems", "data"),
        SettingEntry("Notifications", "notifications notification shade", "data"),
        SettingEntry("About SVARĀ", "about version help licence", "about"),
)

/** Live filter used by the search bar: every word must appear in the title or keywords. */
internal fun searchSettings(query: String): List<SettingEntry> {
    val words = query.trim().lowercase().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return SETTINGS_INDEX.filter { e -> words.all { w -> e.title.lowercase().contains(w) || e.keywords.contains(w) } }
}

internal fun sectionTitle(key: String) = when (key) {
    "appearance" -> "Appearance"
    "playback" -> "Playback"
    "audio" -> "Audio"
    "library" -> "Library"
    "data" -> "Backup and data"
    else -> "About and help"
}

/** A rounded group of rows on the theme's container colour. */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) { Column { content() } }
}

/** Top-level row: coloured circular icon, title, live one-line summary and a chevron. */
@Composable
private fun CategoryRow(icon: ImageVector, tint: Color, title: String, summary: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = {
            Box(Modifier.size(40.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = contentColorFor(tint), modifier = Modifier.size(22.dp))
            }
        },
        headlineContent = { Text(title) },
        // Two lines, so the summary still says something at large font sizes.
        supportingContent = { Text(summary, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = "Open $title", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $summary" },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) { content() }
    }
}

/** Shows up to seven rows in a card; past that, the rest sit behind an "Advanced" row. */
@Composable
private fun Group(rows: List<@Composable () -> Unit>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val collapsible = rows.size > 7
    val visible = if (expanded || !collapsible) rows else rows.take(6)
    SettingsCard {
        visible.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider()
            row()
        }
        if (collapsible) {
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Advanced") },
                supportingContent = { Text(if (expanded) "Showing all settings" else "${rows.size - 6} more settings") },
                trailingContent = { Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .heightIn(min = 56.dp)
                    .clickable(onClickLabel = if (expanded) "Hide advanced settings" else "Show advanced settings") { expanded = !expanded },
            )
        }
    }
}
