package com.svara.app.settings

import com.svara.app.appearance.NowPlayingStyle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svara.app.appearance.AnimationStyle
import com.svara.app.appearance.AppearanceDefaults
import com.svara.app.appearance.BackgroundTone
import com.svara.app.appearance.Brightness
import com.svara.app.appearance.ColourSystem
import com.svara.app.appearance.CustomField
import com.svara.app.appearance.WaveStrength
import com.svara.app.appearance.Customization
import com.svara.app.appearance.PerformanceMode
import com.svara.app.appearance.PolicyOverride
import com.svara.app.appearance.PresetId
import com.svara.app.appearance.StudioSection
import com.svara.app.appearance.ThemeField
import com.svara.app.appearance.ThemeSection
import com.svara.app.appearance.colour.ContrastEngine
import com.svara.app.appearance.colour.EffectivePalette
import com.svara.app.appearance.colour.Swatch
import com.svara.app.appearance.colour.Swatches
import com.svara.app.appearance.tokens.ThemeTokens
import com.svara.app.ui.components.BackButton
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.presets.PresetPreview
import com.svara.app.ui.theme.LocalAppearance
import com.svara.app.ui.theme.LocalPresetUi
import com.svara.app.ui.theme.Spacing
import com.svara.app.ui.theme.toColor

/*
 * Settings > Appearance: one page for every visual control. Every control writes ThemeConfig or
 * studio overrides only (never full copies), and none of them touches playback, the queue or
 * the scanner. Sections collapse; which ones are open is remembered.
 */

private const val PREVIEW_PROGRESS = 0.6f
private const val HUE_MAX = 360f
private const val PERCENT = 100f
private const val SWATCH_PRESSED = 0.85f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(navigator: Navigator, viewModel: AppearanceSettingsViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val custom by viewModel.custom.collectAsStateWithLifecycle()
    val customOverrides by viewModel.customOverrides.collectAsStateWithLifecycle()
    val limits by viewModel.limits.collectAsStateWithLifecycle()
    val reducedMotion by viewModel.reducedMotion.collectAsStateWithLifecycle()
    val highContrast by viewModel.highContrast.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val appearance = LocalAppearance.current
    var saving by rememberSaveable { mutableStateOf(false) }
    fun overridden(vararg s: ThemeSection) = config.overrides.keys.any { it.section in s }

    Scaffold(topBar = { TopAppBar(title = { Text("Appearance") }, navigationIcon = { BackButton(navigator::back) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PreviewStrip()
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f)) {
                section("theme", "Theme", sections, viewModel::toggleSection, onReset = null) {
                    PresetId.entries.forEach { id -> PresetCard(id, id == theme.preset) { viewModel.applyPreset(id) } }
                }
                section(
                    "colour", "Colour", sections, viewModel::toggleSection,
                    onReset = if (overridden(ThemeSection.COLOUR) || custom.brightness != Brightness.DARK || highContrast) ({
                        viewModel.resetThemeSection(ThemeSection.COLOUR)
                        viewModel.set(CustomField.BRIGHTNESS, Brightness.DARK.name)
                        viewModel.setHighContrast(false)
                    }) else null,
                ) { ColourSection(viewModel, appearance.dark, highContrast) }
                section(
                    "ambient", "Ambient", sections, viewModel::toggleSection,
                    onReset = if (overridden(ThemeSection.AMBIENT)) ({ viewModel.resetThemeSection(ThemeSection.AMBIENT) }) else null,
                ) {
                    Note("Colours from the playing song's cover tint the home header, Now Playing, the mini-player and album tiles when Colour source is Album artwork. Text never takes its colour from the tint.")
                    PercentSlider(
                        "Intensity", theme.ambientIntensity,
                        onDone = { viewModel.setTheme(ThemeField.AMBIENT_INTENSITY, it.toString()) },
                        note = if (theme.colourSystem != ColourSystem.ALBUM) "Choose Album artwork as the colour source to see the tint." else "0 turns the tint off.",
                    )
                    if (limits.mode == PerformanceMode.EFFICIENCY) Note("Efficiency mode switches colours instantly instead of cross-fading.")
                }
                section(
                    "blur", "Cinematic blur", sections, viewModel::toggleSection,
                    onReset = if (overridden(ThemeSection.BLUR)) ({ viewModel.resetThemeSection(ThemeSection.BLUR) }) else null,
                ) {
                    Note("A softened cover behind Now Playing and album headers, made once per cover and kept, never a live blur.")
                    BlurStrength(theme.blurStrength) { viewModel.setTheme(ThemeField.BLUR_STRENGTH, it.toString()) }
                    SwitchRow("Frosted surfaces", "A light, static frost on cards and bars", theme.frosted) { viewModel.setTheme(ThemeField.FROSTED, it.toString()) }
                    SwitchRow("Tint", "Colour the blur's shade with the cover", theme.blurTint) { viewModel.setTheme(ThemeField.BLUR_TINT, it.toString()) }
                    if (limits.mode == PerformanceMode.EFFICIENCY) Note("Efficiency mode turns blur off and uses a plain shade.")
                }
                section(
                    "nowplaying", "Now Playing", sections, viewModel::toggleSection,
                    onReset = if (customOverrides.keys.any { it == CustomField.NOW_PLAYING_STYLE || it == CustomField.ARTWORK_AURA }) ({
                        viewModel.set(CustomField.NOW_PLAYING_STYLE, NowPlayingStyle.CLASSIC.name)
                        viewModel.set(CustomField.ARTWORK_AURA, true.toString())
                    }) else null,
                ) {
                    Note("One player, three arrangements. Switching keeps the song, the queue and the position.")
                    NowPlayingStyle.entries.forEach { style ->
                        RadioRow(style.label, custom.nowPlayingStyle == style, { viewModel.set(CustomField.NOW_PLAYING_STYLE, style.name) }, note = style.description)
                    }
                    SwitchRow(
                        "Artwork aura",
                        "A soft glow in the cover's colour behind the artwork. Off in Efficiency mode and with High contrast.",
                        custom.artworkAura,
                    ) { viewModel.set(CustomField.ARTWORK_AURA, it.toString()) }
                }
                section(
                    "motion", "Motion", sections, viewModel::toggleSection,
                    onReset = if (overridden(ThemeSection.MOTION) || customOverrides.keys.any { it.section == StudioSection.MOTION } || !custom.haptics) ({ viewModel.resetMotion() }) else null,
                ) {
                    if (reducedMotion) Note("Reduce motion is on in Accessibility. It overrides everything here: only progress indicators move.")
                    RadioGroup(AnimationStyle.entries, theme.animationStyle, { it.label }, { viewModel.setTheme(ThemeField.ANIMATION_STYLE, it.name) })
                    Text("Transition speed", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s))
                    RadioGroup(
                        Customization.SPEEDS, custom.transitionSpeed,
                        { when (it) { 0.5f -> "Slowest"; 0.75f -> "Slower"; 1f -> "Normal"; 1.5f -> "Faster"; else -> "Fastest" } },
                        { viewModel.set(CustomField.TRANSITION_SPEED, it.toString()) },
                    )
                    PercentSlider("Intensity", custom.intensity, onDone = { viewModel.set(CustomField.INTENSITY, it.toString()) }, note = "0 turns off all animation except progress indicators.")
                    SwitchRow("Haptics", "A light tap on player controls and while scrubbing", custom.haptics) { viewModel.set(CustomField.HAPTICS, it.toString()) }
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
                section(
                    "performance", "Performance mode", sections, viewModel::toggleSection,
                    onReset = if (custom.performanceMode != null || !custom.smoothDisplay) viewModel::resetPerformance else null,
                ) {
                    if (limits.override != PolicyOverride.NONE) limits.notice?.let { Note("$it Your choice returns when it clears.") }
                    RadioRow("Theme default (${theme.spec.defaultTier.label})", custom.performanceMode == null, { viewModel.setPerformance(null) })
                    PerformanceMode.entries.forEach { mode ->
                        RadioRow(mode.label, custom.performanceMode == mode, { viewModel.setPerformance(mode) }, note = mode.description())
                    }
                    SwitchRow(
                        "Smooth display",
                        "Asks for the highest refresh rate your screen offers. Battery saver, low battery or heat can still lower it; off lets the system choose",
                        custom.smoothDisplay,
                    ) { viewModel.set(CustomField.SMOOTH_DISPLAY, it.toString()) }
                }
                section("advanced", "Advanced", sections, viewModel::toggleSection, onReset = null) {
                    ClickRow(Icons.Rounded.Tune, "Theme Studio", "Layout, artwork and the audio-reactive layer", onClick = navigator::themeStudio)
                    ClickRow(Icons.Rounded.Navigation, "Navigation", "Bottom-bar tabs", onClick = { navigator.studio(StudioSection.NAVIGATION) })
                    ClickRow(Icons.Rounded.PlayCircle, "Now Playing", "Controls, progress bar, lyrics and queue", onClick = { navigator.studio(StudioSection.NOW_PLAYING) })
                    ClickRow(Icons.Rounded.Gesture, "Gestures", "Swipe and double-tap", onClick = { navigator.studio(StudioSection.GESTURES) })
                    ClickRow(Icons.Rounded.TextFields, "Typography", "Text size and density", onClick = { navigator.studio(StudioSection.TYPOGRAPHY) })
                    ClickRow(Icons.Rounded.Image, "Artwork", "Corner style", onClick = { navigator.studio(StudioSection.ARTWORK) })
                    ClickRow(Icons.Rounded.Animation, "Motion", "Speed and intensity", onClick = { navigator.studio(StudioSection.MOTION) })
                    ClickRow(Icons.Rounded.Speed, "Controls", "Seek increment and haptics", onClick = { navigator.studio(StudioSection.CONTROL) })
                }
                item(key = "footer") {
                    Row(Modifier.fillMaxWidth().padding(Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        OutlinedButton(onClick = viewModel::resetEverything, modifier = Modifier.heightIn(min = Spacing.touch)) { Text("Reset everything") }
                        OutlinedButton(onClick = { saving = true }, modifier = Modifier.heightIn(min = Spacing.touch)) { Text("Save as look") }
                    }
                    ClickRow(Icons.Rounded.Bookmarks, "Saved looks", "Apply, rename or delete saved looks", onClick = navigator::savedLooks)
                    Note("Changing appearance never stops the music, changes the queue or rescans your library.")
                    Spacer(Modifier.height(Spacing.xl))
                }
            }
        }
    }
    if (saving) {
        NameDialog(
            title = "Save this look", initial = "My ${theme.preset.displayName}", confirm = "Save",
            onConfirm = { viewModel.duplicate(it); saving = false }, onDismiss = { saving = false },
        )
    }
}

/** A collapsible section: header (tap to expand or collapse) with an optional reset. */
private fun LazyListScope.section(
    id: String,
    title: String,
    open: Set<String>,
    onToggle: (String) -> Unit,
    onReset: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val expanded = id in open
    item(key = "h-$id") {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.touch)
                .clickable(onClickLabel = if (expanded) "Collapse" else "Expand") { onToggle(id) }
                .padding(start = Spacing.l, end = Spacing.xs)
                .semantics { heading(); stateDescription = if (expanded) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (onReset != null) TextButton(onClick = onReset, modifier = Modifier.semantics { contentDescription = "Reset $title section" }) { Text("Reset this section") }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
        }
    }
    if (expanded) item(key = "b-$id") { Column { content() } }
    item(key = "d-$id") { HorizontalDivider() }
}

/**
 * Live preview in the effective theme: mini-player, card, button, toggle and progress bar.
 * Static (no blur, nothing moving), so it costs one cheap recomposition per change.
 */
@Composable
private fun PreviewStrip() {
    val p = LocalPresetUi.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(p.colors.background)
            .padding(Spacing.m)
            .clearAndSetSemantics { contentDescription = "Preview of the current appearance" },
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(p.shapes.miniPlayer).background(p.colors.surface).padding(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(p.sizes.miniArtwork).clip(p.shapes.artwork).background(p.colors.surfaceHighlight), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = p.colors.secondaryText)
            }
            Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
                Text("Song title", style = p.type.bodyEmphasis, color = p.colors.primaryText, maxLines = 1)
                Text("Artist", style = p.type.subhead, color = p.colors.secondaryText, maxLines = 1)
            }
            Box(Modifier.size(Spacing.touch).clip(p.shapes.circle).background(p.colors.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = p.colors.onAccent)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clip(p.shapes.card).background(p.colors.surfaceHighlight).padding(Spacing.s)) {
                Text("Card", style = p.type.bodyEmphasis, color = p.colors.primaryText)
                Text("Accent text", style = p.type.subhead, color = p.colors.accentText)
            }
            Text(
                "Button", style = p.type.bodyEmphasis, color = p.colors.onAccent,
                modifier = Modifier.clip(p.shapes.pill).background(p.colors.accentFill).padding(horizontal = Spacing.l, vertical = Spacing.s),
            )
            Switch(checked = true, onCheckedChange = null)
        }
        LinearProgressIndicator(progress = { PREVIEW_PROGRESS }, modifier = Modifier.fillMaxWidth(), color = p.colors.accent, trackColor = p.colors.surfaceHighlight, drawStopIndicator = {})
    }
}

@Composable
private fun PresetCard(id: PresetId, selected: Boolean, onApply: () -> Unit) {
    Card(
        onClick = { if (!selected) onApply() },
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = Spacing.xs)
            .semantics { this.selected = selected; stateDescription = if (selected) "Selected" else "Not selected" },
    ) {
        Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            // Static preview art for every card.
            PresetPreview(id, live = false, modifier = Modifier.size(width = Spacing.touch + Spacing.xxl, height = Spacing.touch * 2).clip(MaterialTheme.shapes.small))
            Column(Modifier.weight(1f).padding(start = Spacing.l)) {
                Text(id.displayName, style = MaterialTheme.typography.titleLarge)
                Text(id.personality, style = MaterialTheme.typography.bodyMedium)
            }
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColourSection(viewModel: AppearanceSettingsViewModel, dark: Boolean, highContrast: Boolean) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val custom by viewModel.custom.collectAsStateWithLifecycle()
    val appearance = LocalAppearance.current
    Text("Colour source", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s))
    val sources = ColourSystem.entries.filter { it.selectable }
    RadioGroup(sources, theme.colourSystem.takeIf { it.selectable } ?: ColourSystem.PRESET, { it.label }, viewModel::setColourSource, note = { s ->
        when (s) {
            ColourSystem.PRESET -> "${theme.preset.displayName}'s signature palette"
            ColourSystem.ALBUM -> if (theme.spec.colourSystem == ColourSystem.ALBUM) "Colours from the playing song's cover (${theme.preset.displayName}'s default)" else "Colours from the playing song's cover"
            ColourSystem.CUSTOM -> "Pick from 64 colours or any hue"
            ColourSystem.MONOCHROME -> "A restrained greyscale palette"
            else -> null
        }
    })
    if (theme.colourSystem != theme.spec.colourSystem) {
        TextButton(onClick = viewModel::restorePresetColours, modifier = Modifier.padding(horizontal = Spacing.s).heightIn(min = Spacing.touch)) { Text("Restore preset colours") }
    }

    Text("Custom accent", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s))
    val selectedId = if (theme.colourSystem == ColourSystem.CUSTOM && theme.customHue == null) theme.customSwatch?.id else null
    Swatches.grouped().forEach { (group, swatches) ->
        Text(group.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Spacing.l, top = Spacing.s))
        FlowRow(Modifier.padding(horizontal = Spacing.m)) {
            swatches.forEach { s -> SwatchCell(s, s.id == selectedId) { viewModel.selectSwatch(s.id) } }
        }
    }
    // Which accent roles had to change to stay readable on this theme.
    val adjustment = EffectivePalette.build(theme.spec.tokens, theme, dark, highContrast, appearance.albumAccent).adjustment
    if (adjustment.any) Note("Adjusted for readability: the accent's lightness was changed for ${adjustment.describe()} so it meets the contrast target. Its hue is kept.")

    HueSlider(theme.customHue, active = theme.colourSystem == ColourSystem.CUSTOM && theme.customHue != null, onDone = viewModel::setHue)

    Text("Background tone", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s))
    RadioGroup(BackgroundTone.entries, theme.backgroundTone, { it.label }, { viewModel.setTheme(ThemeField.BACKGROUND_TONE, it.name) }, note = {
        when (it) {
            BackgroundTone.PURE_BLACK -> "True black for OLED screens (dark themes)"
            BackgroundTone.SOFT_DARK -> "The preset's own surfaces"
            BackgroundTone.TINTED -> "Surfaces lightly tinted by the accent; text contrast is kept"
        }
    })
    Text("Light or dark", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s))
    RadioGroup(Brightness.entries, custom.brightness, { it.label }, { viewModel.set(CustomField.BRIGHTNESS, it.name) })
    SwitchRow("High contrast", "Stronger text, outlines and dividers. Each theme keeps its own accent.", highContrast, onChange = viewModel::setHighContrast)
}

/** One swatch: 48 dp target, outline and checkmark when selected (never colour alone). */
@Composable
private fun SwatchCell(swatch: Swatch, selected: Boolean, onSelect: () -> Unit) {
    val colour = swatch.colour.toColor()
    val ms = LocalPresetUi.current.effects.iconMorphMs
    // A single short scale on the newly selected swatch; every other swatch stays still.
    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected && ms > 0) {
            scale.snapTo(SWATCH_PRESSED)
            scale.animateTo(1f, tween(ms))
        }
    }
    val outline = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(Spacing.touch)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = swatch.name },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(Spacing.touch - Spacing.s)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .then(if (selected) Modifier.border(ThemeTokens.HAIRLINE_HIGH_CONTRAST.toDp(), outline, CircleShape) else Modifier)
                .padding(if (selected) Spacing.xs else Spacing.xs / 2)
                .clip(CircleShape)
                .background(colour),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = ContrastEngine.onColour(swatch.colour).toColor())
        }
    }
}

/** Hue only, at a fixed safe lightness and chroma: the live colour, its hex, and copy. */
@Composable
private fun HueSlider(hue: Float?, active: Boolean, onDone: (Float) -> Unit) {
    var local by remember(hue) { mutableFloatStateOf(hue ?: 0f) }
    val colour = EffectivePalette.hueColour(local)
    val hex = ContrastEngine.hex(colour)
    val clipboard = LocalClipboardManager.current
    Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (active) "Custom hue" else "Hue", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Box(Modifier.size(Spacing.xl).clip(RoundedCornerShape(Spacing.xs)).background(colour.toColor()))
            Text(hex, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = Spacing.s))
            IconButton(onClick = { clipboard.setText(AnnotatedString(hex)) }) { Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy $hex") }
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onDone(local) },
            valueRange = 0f..HUE_MAX,
            modifier = Modifier.semantics { contentDescription = "Hue"; stateDescription = "${local.toInt()} degrees, $hex" },
        )
        Text("Moving the slider replaces the selected swatch with this hue.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Five blur buckets; written when the slider is released, so only one bitmap is rebuilt. */
@Composable
private fun BlurStrength(value: Int, onDone: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        Row {
            Text("Blur strength", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${local.toInt()} of ${AppearanceDefaults.BLUR_BUCKETS}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onDone(local.toInt()) },
            valueRange = 1f..AppearanceDefaults.BLUR_BUCKETS.toFloat(),
            steps = AppearanceDefaults.BLUR_BUCKETS - 2,
            modifier = Modifier.semantics { contentDescription = "Blur strength"; stateDescription = "${local.toInt()} of ${AppearanceDefaults.BLUR_BUCKETS}" },
        )
    }
}

@Composable
private fun PercentSlider(title: String, value: Int, onDone: (Int) -> Unit, note: String) {
    SliderRow(
        title = title,
        valueLabel = if (value == 0) "Off" else "$value%",
        value = value.toFloat(),
        range = 0f..PERCENT,
        steps = 9,
        onChange = { v -> onDone((Math.round(v / 10f) * 10)) },
        note = note,
    )
}

private fun Float.toDp() = androidx.compose.ui.unit.Dp(this)
