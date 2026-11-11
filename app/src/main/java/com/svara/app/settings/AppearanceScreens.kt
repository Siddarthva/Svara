package com.svara.app.settings

import com.svara.app.ui.theme.Spacing
import android.os.Build
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svara.app.appearance.AnimationStyle
import com.svara.app.appearance.ArtworkCorner
import com.svara.app.appearance.ArtworkSize
import com.svara.app.appearance.Brightness
import com.svara.app.appearance.ColourSystem
import com.svara.app.appearance.CustomField
import com.svara.app.appearance.WaveStrength
import com.svara.app.appearance.Customization
import com.svara.app.appearance.DoubleTapAction
import com.svara.app.appearance.HomeLayout
import com.svara.app.appearance.LyricsPosition
import com.svara.app.appearance.MetadataDensity
import com.svara.app.appearance.Palettes
import com.svara.app.appearance.PerformanceMode
import com.svara.app.appearance.PolicyOverride
import com.svara.app.appearance.PresetId
import com.svara.app.appearance.ProgressStyle
import com.svara.app.appearance.QueueStyle
import com.svara.app.appearance.StudioSection
import com.svara.app.appearance.ThemeField
import com.svara.app.appearance.ThemeResolver
import com.svara.app.appearance.ThemeSection
import com.svara.app.appearance.VisualiserMode
import com.svara.app.data.appearance.SavedLook
import com.svara.app.ui.components.BackButton
import com.svara.app.ui.components.DragHandle
import com.svara.app.ui.components.SectionHeader
import com.svara.app.ui.components.draggedItem
import com.svara.app.ui.components.rememberReorderableList
import com.svara.app.ui.components.reorderAccessibility
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.presets.PresetPreview
import com.svara.app.ui.presets.accentSwatch
import com.svara.app.ui.theme.LocalAppearance
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearancePage(
    title: String,
    navigator: Navigator,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { BackButton(navigator::back) }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), state = listState) {
            content()
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
internal fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true, note: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.padding(start = 12.dp)) {
            Text(label, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun <T> RadioGroup(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, enabled: (T) -> Boolean = { true }, note: (T) -> String? = { null }) {
    Column {
        options.forEach { option -> RadioRow(label(option), option == selected, { onSelect(option) }, enabled(option), note(option)) }
    }
}

@Composable
internal fun ResetHeader(title: String, onReset: (() -> Unit)?) {
    SectionHeader(title) {
        if (onReset != null) TextButton(onClick = onReset, modifier = Modifier.semantics { contentDescription = "Reset $title section" }) { Text("Reset section") }
    }
}

// ---------------------------------------------------------------------------------------------
// Theme Studio

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeStudioScreen(navigator: Navigator, viewModel: AppearanceSettingsViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val limits by viewModel.limits.collectAsStateWithLifecycle()
    val analysisSupported by viewModel.analysisSupported.collectAsStateWithLifecycle()
    val reducedMotion by viewModel.reducedMotion.collectAsStateWithLifecycle()
    val appearance = LocalAppearance.current
    var duplicating by rememberSaveable { mutableStateOf(false) }
    fun overridden(section: ThemeSection) = config.overrides.keys.any { it.section == section }

    AppearancePage("Customise", navigator) {
        item {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    ThemeResolver.summary(theme),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp).semantics { contentDescription = "Current look. ${ThemeResolver.summary(theme)}" },
                )
            }
        }
        if (theme.preset != PresetId.ANAHATA) item {
            SectionHeader("${theme.preset.displayName}")
            Note("Layout, colours, type and artwork come from this preset's design. Choose ETHEREAL to customise them; motion, performance and the studios below still apply.")
            PresetNotes(theme.preset)
        }
        if (theme.preset == PresetId.ANAHATA) item {
            ResetHeader("Layout", if (overridden(ThemeSection.LAYOUT)) ({ viewModel.resetThemeSection(ThemeSection.LAYOUT) }) else null)
            Text("Home layout", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
            RadioGroup(HomeLayout.entries, theme.homeLayout, { it.label }, { viewModel.setTheme(ThemeField.HOME_LAYOUT, it.name) })
        }
        if (theme.preset == PresetId.ANAHATA) item {
            ResetHeader("Artwork", if (overridden(ThemeSection.ARTWORK)) ({ viewModel.resetThemeSection(ThemeSection.ARTWORK) }) else null)
            Text("Album artwork size", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
            RadioGroup(ArtworkSize.entries, theme.artworkSize, { it.label }, { viewModel.setTheme(ThemeField.ARTWORK_SIZE, it.name) })
            SwitchRow(
                "Translucent surfaces",
                if (limits.glassAllowed) "See-through mini-player and panels" else "Not available in ${limits.mode.label} mode",
                theme.glass,
            ) { viewModel.setTheme(ThemeField.GLASS, it.toString()) }
            SwitchRow(
                "Artwork glow",
                if (limits.glowAllowed) "A soft light from the album's colour" else "Not available in ${limits.mode.label} mode",
                theme.glow,
            ) { viewModel.setTheme(ThemeField.GLOW, it.toString()) }
        }
        if (theme.preset == PresetId.SPANDA) item {
            ResetHeader("Audio-reactive layer", if (overridden(ThemeSection.VISUALISER)) ({ viewModel.resetThemeSection(ThemeSection.VISUALISER) }) else null)
            RadioGroup(
                VisualiserMode.entries, theme.visualiser, { it.label }, { viewModel.setTheme(ThemeField.VISUALISER, it.name) },
                enabled = { !it.needsAnalysis || analysisSupported },
                note = { mode ->
                    when (mode) {
                        VisualiserMode.OFF -> "No analysis; a static background"
                        VisualiserMode.AMBIENT -> "A glow in the artwork's colour, updated on track change; no analysis"
                        VisualiserMode.REACTIVE -> "Bass moves a soft glow behind the artwork, up to 20 fps"
                        VisualiserMode.FULL_SPECTRUM -> "Also draws spectrum bars under the seek bar, up to 30 fps"
                    }
                },
            )
            when {
                limits.mode == PerformanceMode.EFFICIENCY -> Note("Efficiency mode turns the audio-reactive layer off.")
                !analysisSupported -> Note("Audio analysis is unavailable on this device.")
                theme.visualiser.needsAnalysis && limits.mode != PerformanceMode.IMMERSIVE -> Note("Reactive and Full Spectrum move only in Immersive mode; until then the glow stays still.")
                theme.visualiser.needsAnalysis -> Note("Analysis runs only while Now Playing is on screen and music is playing.")
            }
        }
        item {
            SectionHeader("Actions")
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::resetTheme, enabled = config.overrides.isNotEmpty()) { Text("Reset everything") }
                OutlinedButton(onClick = { duplicating = true }) { Text("Duplicate preset") }
            }
            TextButton(onClick = navigator::savedLooks, modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp)) { Text("Saved looks") }
        }
    }

    if (duplicating) {
        NameDialog(
            title = "Save this look",
            initial = "My ${theme.preset.displayName}",
            confirm = "Save",
            onConfirm = { viewModel.duplicate(it); duplicating = false },
            onDismiss = { duplicating = false },
        )
    }
}

/** Things a preset leaves out or defers, stated where its settings live. */
@Composable
private fun PresetNotes(preset: PresetId) {
    when (preset) {
        PresetId.TALA -> Note("Audio output picker on Now Playing: Coming in a later update. Volume follows the system.")
        PresetId.RAGA -> {
            Note("All music is on this device, so My Music shows no download marks.")
            Note("Language tags and the language filter: Coming in a later update.")
        }
        PresetId.SPANDA -> Note("Share sends the song's title and artist as text. The file is never shared.")
        PresetId.SHRUTI -> Note("“Playing from” shows the album; SVARĀ doesn't record where a queue was started.")
        PresetId.ANAHATA -> Unit
    }
}

@Composable
internal fun NameDialog(title: String, initial: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name) }),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------------------------
// Saved looks

@Composable
fun SavedLooksScreen(navigator: Navigator, viewModel: AppearanceSettingsViewModel = hiltViewModel()) {
    val looks by viewModel.savedLooks.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<SavedLook?>(null) }
    var deleting by remember { mutableStateOf<SavedLook?>(null) }
    var previewing by remember { mutableStateOf<SavedLook?>(null) }
    AppearancePage("Saved looks", navigator) {
        if (looks.isEmpty()) item { Note("No saved looks yet. In Customise, choose “Duplicate preset” to save the current look under a name.") }
        looks.forEach { look ->
            item(key = look.id) {
                var menu by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(look.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        val changes = look.config.overrides.size + look.custom.size
                        Text("${look.config.preset.displayName} · ${if (changes == 1) "1 change" else "$changes changes"} · ${DateUtils.getRelativeTimeSpanString(look.createdAt)}")
                    },
                    trailingContent = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More options for ${look.name}") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Restore") }, onClick = { menu = false; viewModel.applyLook(look) })
                                DropdownMenuItem(text = { Text("Preview") }, onClick = { menu = false; previewing = look })
                                DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; viewModel.duplicateLook(look) })
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = look })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleting = look })
                            }
                        }
                    },
                    modifier = Modifier.clickable(onClickLabel = "Preview") { previewing = look },
                )
            }
        }
    }
    previewing?.let { look ->
        AlertDialog(
            onDismissRequest = { previewing = null },
            title = { Text(look.name) },
            text = {
                Column {
                    PresetPreview(look.config.preset, live = false, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Spacing.s))
                    val details = look.config.overrides.keys.map { it.name.lowercase().replace('_', ' ') } +
                        look.custom.keys.map { it.name.lowercase().replace('_', ' ') }
                    Text(
                        if (details.isEmpty()) "${look.config.preset.displayName} as designed." else "${look.config.preset.displayName} with: ${details.joinToString(", ")}.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (look.custom.isEmpty()) Text("Saved before V3: restoring keeps your current Now Playing and visualiser settings.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.applyLook(look); previewing = null }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { previewing = null }) { Text("Close") } },
        )
    }
    renaming?.let { look ->
        NameDialog("Rename look", look.name, "Rename", onConfirm = { viewModel.renameLook(look, it); renaming = null }, onDismiss = { renaming = null })
    }
    deleting?.let { look ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${look.name}”?") },
            text = { Text("Your current theme isn't affected.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteLook(look); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Performance mode

internal fun PerformanceMode.description(): String = when (this) {
    PerformanceMode.EFFICIENCY -> "No animation except progress and 100 ms tap feedback. Static backgrounds, no artwork spring, no audio-reactive layer. Easiest on the battery."
    PerformanceMode.BALANCED -> "Static backgrounds (cached, never live blur), 150–250 ms transitions, tap scale, heart pop, tab tint cross-fade and SOLACE's artwork spring."
    PerformanceMode.IMMERSIVE -> "Transitions up to 400 ms, OCTAVE's breathing overlay at up to 10 fps, ZENITH's audio-reactive layer up to 30 fps while it's on screen, and SOLACE's blurred mini-player on Android 12+."
}

// ---------------------------------------------------------------------------------------------
// Studios

@Composable
fun StudioScreen(navigator: Navigator, section: StudioSection, viewModel: AppearanceSettingsViewModel = hiltViewModel()) {
    val custom by viewModel.custom.collectAsStateWithLifecycle()
    val overrides by viewModel.customOverrides.collectAsStateWithLifecycle()
    val hasOverrides = overrides.keys.any { it.section == section }
    val listState = rememberLazyListState()
    val title = when (section) {
        StudioSection.CONTROL -> "Controls"
        else -> section.label
    }
    when (section) {
        StudioSection.NAVIGATION -> NavigationStudio(navigator, viewModel, custom, hasOverrides)
        StudioSection.NOW_PLAYING -> NowPlayingStudio(navigator, viewModel, custom, hasOverrides)
        else -> AppearancePage(title, navigator, listState) {
            item {
                ResetHeader(title, if (hasOverrides) ({ viewModel.resetStudio(section) }) else null)
                when (section) {
                    StudioSection.GESTURES -> GesturesStudio(viewModel, custom)
                    StudioSection.TYPOGRAPHY -> TypographyStudio(viewModel, custom)
                    StudioSection.ARTWORK -> ArtworkStudio(viewModel, custom)
                    StudioSection.MOTION -> MotionStudio(viewModel, custom)
                    StudioSection.CONTROL -> ControlStudio(viewModel, custom)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun NavigationStudio(navigator: Navigator, viewModel: AppearanceSettingsViewModel, custom: Customization, hasOverrides: Boolean) {
    val listState = rememberLazyListState()
    var message by remember { mutableStateOf<String?>(null) }
    val reorder = rememberReorderableList(custom.navOrder, listState, key = { it.name }, headerCount = 1, onMove = viewModel::moveTab)
    AppearancePage("Navigation", navigator, listState) {
        item(key = "header") {
            ResetHeader("Navigation", if (hasOverrides) ({ viewModel.resetStudio(StudioSection.NAVIGATION) }) else null)
            Note("Choose which tabs sit in the bottom bar and drag to reorder. Up to ${Customization.MAX_VISIBLE_TABS} tabs; at least one stays visible. Hidden sections stay reachable from Settings.")
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        }
        itemsIndexed(reorder.items, key = { _, tab -> tab.name }) { index, tab ->
            val visible = tab !in custom.navHidden
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .draggedItem(reorder, tab.name)
                    .background(MaterialTheme.colorScheme.surface)
                    .reorderAccessibility(index, reorder.items.lastIndex, viewModel::moveTab)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DragHandle(reorder, tab.name, Modifier.padding(12.dp))
                Text(tab.label, Modifier.weight(1f))
                Switch(
                    checked = visible,
                    onCheckedChange = { on -> message = viewModel.setTabVisible(tab, on) },
                    modifier = Modifier.padding(end = 12.dp).semantics { contentDescription = "Show ${tab.label} tab" },
                )
            }
        }
    }
}

@Composable
private fun NowPlayingStudio(navigator: Navigator, viewModel: AppearanceSettingsViewModel, custom: Customization, hasOverrides: Boolean) {
    val listState = rememberLazyListState()
    val reorder = rememberReorderableList(custom.controlOrder, listState, key = { it.name }, headerCount = 1, onMove = viewModel::moveControl)
    AppearancePage("Now Playing", navigator, listState) {
        item(key = "header") {
            ResetHeader("Now Playing", if (hasOverrides) ({ viewModel.resetStudio(StudioSection.NOW_PLAYING) }) else null)
            Text("Transport controls", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() })
            Note("Drag to reorder. Play/pause always stays in the centre, between the second and third control.")
        }
        itemsIndexed(reorder.items, key = { _, id -> id.name }) { index, id ->
            val position = when (index) {
                0 -> "Far left"
                1 -> "Left of play/pause"
                2 -> "Right of play/pause"
                else -> "Far right"
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .draggedItem(reorder, id.name)
                    .background(MaterialTheme.colorScheme.surface)
                    .reorderAccessibility(index, reorder.items.lastIndex, viewModel::moveControl)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DragHandle(reorder, id.name, Modifier.padding(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(id.label)
                    Text(position, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text(
                "Order: " + custom.controlOrder.take(2).joinToString(" · ") { it.label } + " · Play/pause · " + custom.controlOrder.drop(2).joinToString(" · ") { it.label },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            SectionHeader("Progress bar")
            RadioGroup(ProgressStyle.entries, custom.progressStyle, { it.label }, { viewModel.set(CustomField.PROGRESS_STYLE, it.name) })
            SectionHeader("Lyrics")
            RadioGroup(LyricsPosition.entries, custom.lyricsPosition, { it.label }, { viewModel.set(CustomField.LYRICS_POSITION, it.name) })
            SectionHeader("Queue")
            RadioGroup(QueueStyle.entries, custom.queueStyle, { it.label }, { viewModel.set(CustomField.QUEUE_STYLE, it.name) })
        }
    }
}

@Composable
private fun GesturesStudio(viewModel: AppearanceSettingsViewModel, custom: Customization) {
    SwitchRow("Swipe artwork to skip", "Swipe left for next, right for previous", custom.swipeToSkip) { viewModel.set(CustomField.SWIPE_SKIP, it.toString()) }
    SectionHeader("Double-tap artwork")
    RadioGroup(DoubleTapAction.entries, custom.doubleTap, { it.label }, { viewModel.set(CustomField.DOUBLE_TAP, it.name) })
    Note("TalkBack users get the same actions from the artwork's actions menu.")
}

@Composable
private fun TypographyStudio(viewModel: AppearanceSettingsViewModel, custom: Customization) {
    SliderRow(
        title = "Text size",
        valueLabel = "${custom.textScale}%",
        value = custom.textScale.toFloat(),
        range = Customization.TEXT_SCALE_RANGE.first.toFloat()..Customization.TEXT_SCALE_RANGE.last.toFloat(),
        steps = (Customization.TEXT_SCALE_RANGE.last - Customization.TEXT_SCALE_RANGE.first) / 5 - 1,
        onChange = { v -> viewModel.set(CustomField.TEXT_SCALE, ((Math.round(v / 5f) * 5).coerceIn(Customization.TEXT_SCALE_RANGE)).toString()) },
        note = "Multiplies your system font size. Rows grow to fit; nothing is cut off.",
    )
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Sample song title", style = MaterialTheme.typography.bodyLarge)
            Text("Artist • Album", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    SectionHeader("Metadata density")
    RadioGroup(
        MetadataDensity.entries, custom.density, { it.label }, { viewModel.set(CustomField.DENSITY, it.name) },
        note = { if (it == MetadataDensity.COMPACT) "Tighter rows, artist only" else "Artist and album on every row" },
    )
}

@Composable
private fun ArtworkStudio(viewModel: AppearanceSettingsViewModel, custom: Customization) {
    val limits by viewModel.limits.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    SectionHeader("Corners")
    RadioGroup(ArtworkCorner.entries, custom.corner, { it.label }, { viewModel.set(CustomField.CORNER, it.name) })
    val allowed = limits.playerBlurDp > 0
    SwitchRow(
        "Blur behind the player",
        when {
            !allowed -> "Not available in ${limits.mode.label} mode"
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> "Your Android version shows a soft colour gradient instead of blur"
            else -> "Blurred artwork behind the full-screen player"
        },
        custom.playerBlur ?: theme.spec.playerBlur,
        enabled = allowed,
    ) { viewModel.set(CustomField.PLAYER_BLUR, it.toString()) }
    Note("Scrolling lists are never blurred.")
}

@Composable
private fun MotionStudio(viewModel: AppearanceSettingsViewModel, custom: Customization) {
    val reducedMotion by viewModel.reducedMotion.collectAsStateWithLifecycle()
    if (reducedMotion) Note("Reduce motion is on in Accessibility. It overrides these settings.")
    SectionHeader("Transition speed")
    RadioGroup(
        Customization.SPEEDS, custom.transitionSpeed,
        { when (it) { 0.5f -> "Slowest"; 0.75f -> "Slower"; 1f -> "Normal"; 1.5f -> "Faster"; else -> "Fastest" } },
        { viewModel.set(CustomField.TRANSITION_SPEED, it.toString()) },
    )
    SliderRow(
        title = "Intensity",
        valueLabel = if (custom.intensity == 0) "Off" else "${custom.intensity}%",
        value = custom.intensity.toFloat(),
        range = 0f..100f,
        steps = 9,
        onChange = { v -> viewModel.set(CustomField.INTENSITY, (Math.round(v / 10f) * 10).toString()) },
        note = "0 turns off all animation except progress indicators.",
    )
    SwitchRow("Page transitions", "Animate moving between screens", custom.pageTransitions) { viewModel.set(CustomField.PAGE_TRANSITIONS, it.toString()) }
    SwitchRow("Scroll effects", "Collapsing titles, parallax and edge fades", custom.scrollEffects) { viewModel.set(CustomField.SCROLL_EFFECTS, it.toString()) }
    SwitchRow("Song change animation", "Artwork and titles glide to the next song", custom.songChangeAnimation) { viewModel.set(CustomField.SONG_CHANGE_ANIMATION, it.toString()) }
    SwitchRow("Smart smoothing", "Lightens animations for a while if frames start dropping", custom.smartSmoothing) { viewModel.set(CustomField.SMART_SMOOTHING, it.toString()) }
    SwitchRow("Recap animations", "Count-ups, reveals and moving shapes in your recaps", custom.recapAnimations) { viewModel.set(CustomField.RECAP_ANIMATIONS, it.toString()) }
    SwitchRow("Live wave", "A slow wave in the accent colour while music plays. Off in Efficiency and with Reduce motion", custom.liveWave) { viewModel.set(CustomField.LIVE_WAVE, it.toString()) }
    if (custom.liveWave) RadioGroup(WaveStrength.entries, custom.waveStrength, { it.label }, { viewModel.set(CustomField.WAVE_STRENGTH, it.name) })
    AnimationExtrasSection(custom) { field, value -> viewModel.set(field, value) }
    val smoothed by com.svara.app.ui.motion.MotionHealth.reduced.collectAsStateWithLifecycle()
    if (smoothed && custom.smartSmoothing) Note("Animations were reduced to keep things smooth.")
}

@Composable
private fun ControlStudio(viewModel: AppearanceSettingsViewModel, custom: Customization) {
    SectionHeader("Seek increment")
    RadioGroup(Customization.SEEK_INCREMENTS, custom.seekIncrementSec, { "$it seconds" }, { viewModel.set(CustomField.SEEK_INCREMENT, it.toString()) })
    Note("Used for rewind and forward in the player (TalkBack actions on the progress bar), headset buttons and the lock screen where the system offers them.")
    SwitchRow("Haptic feedback", "A light tap when you press player controls", custom.haptics) { viewModel.set(CustomField.HAPTICS, it.toString()) }
}
