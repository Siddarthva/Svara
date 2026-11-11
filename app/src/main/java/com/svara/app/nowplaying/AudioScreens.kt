package com.svara.app.nowplaying

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.svara.app.appearance.CustomField
import com.svara.app.appearance.PerformanceMode
import com.svara.app.appearance.VisualColour
import com.svara.app.appearance.VisualSettings
import com.svara.app.appearance.VisualStyle
import com.svara.app.data.appearance.CustomizationRepository
import com.svara.app.domain.audio.EffectsCapabilities
import com.svara.app.domain.audio.EqMath
import com.svara.app.domain.audio.EqPreset
import com.svara.app.domain.audio.EqSettings
import com.svara.app.domain.repository.SettingsRepository
import com.svara.app.playback.PlaybackController
import com.svara.app.playback.analysis.AudioBands
import com.svara.app.playback.analysis.SpectrumMath
import com.svara.app.playback.effects.AudioEffectsHub
import com.svara.app.ui.components.ArtworkImage
import com.svara.app.ui.components.BackButton
import com.svara.app.ui.components.EmptyState
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.presets.common.rememberAlbumColour
import com.svara.app.ui.theme.LocalAppearance
import com.svara.app.ui.theme.Spacing
import com.svara.app.ui.theme.VisualTokens
import com.svara.app.ui.theme.rememberArtworkShape
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// region Equalizer

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val settings: SettingsRepository,
    hub: AudioEffectsHub,
) : ViewModel() {
    val caps: StateFlow<EffectsCapabilities> = hub.capabilities
    val eq: StateFlow<EqSettings> = settings.settings.map { it.eq }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EqSettings())

    private fun update(transform: (EqSettings) -> EqSettings) {
        viewModelScope.launch { settings.update { it.copy(eq = transform(it.eq)) } }
    }

    fun setEnabled(on: Boolean) = update { it.copy(enabled = on) }
    fun selectPreset(name: String) = update { it.copy(preset = name, enabled = true) }

    /** Moving a band makes the curve "Custom", starting from what was playing. */
    fun setBand(band: Int, levelMb: Int) = update { e ->
        val levels = EqMath.resolve(e, caps.value).toMutableList()
        if (band in levels.indices) levels[band] = levelMb
        e.copy(preset = null, bandLevelsMb = levels, enabled = true)
    }

    fun setBass(v: Int) = update { it.copy(bassBoost = v.coerceIn(0, EqSettings.MAX_STRENGTH)) }
    fun setVirtualizer(v: Int) = update { it.copy(virtualizer = v.coerceIn(0, EqSettings.MAX_STRENGTH)) }
    fun setLoudness(v: Int) = update { it.copy(loudnessMb = v.coerceIn(0, EqSettings.MAX_LOUDNESS_MB)) }

    fun savePreset(name: String) = update { e ->
        val n = name.trim()
        if (n.isEmpty() || EqPreset.entries.any { it.label.equals(n, true) || it.name == n }) return@update e
        val levels = EqMath.resolve(e, caps.value)
        e.copy(userPresets = (e.userPresets + (n to levels)).entries.toList().takeLast(EqSettings.MAX_USER_PRESETS).associate { it.key to it.value }, preset = n)
    }

    fun deletePreset(name: String) = update { e -> e.copy(userPresets = e.userPresets - name, preset = if (e.preset == name) EqPreset.FLAT.name else e.preset) }

    /** Flat, effects at zero; user presets are kept. */
    fun reset() = update { it.copy(preset = EqPreset.FLAT.name, bandLevelsMb = emptyList(), bassBoost = 0, virtualizer = 0, loudnessMb = 0) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(navigator: Navigator, viewModel: EqualizerViewModel = hiltViewModel()) {
    val caps by viewModel.caps.collectAsStateWithLifecycle()
    val eq by viewModel.eq.collectAsStateWithLifecycle()
    var saving by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Equalizer") },
                navigationIcon = { BackButton(navigator::back) },
                actions = { if (caps.anySupported) TextButton(onClick = viewModel::reset) { Text("Reset") } },
            )
        },
    ) { padding ->
        if (!caps.attached) {
            EmptyState(Icons.Rounded.Equalizer, "Start playing to adjust", "The equalizer attaches to the player when it starts. Play a song, then come back.", Modifier.padding(padding))
            return@Scaffold
        }
        if (!caps.anySupported) {
            EmptyState(
                Icons.Rounded.Equalizer, "Not supported on this device",
                "This device doesn't offer an equalizer or audio effects to apps." +
                    caps.failures.entries.joinToString("") { "\n${it.key}: ${it.value}" },
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item(key = "on") {
                Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touch).padding(horizontal = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Audio effects", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Uses this device's own effects. Results depend on the device and output; SVARĀ doesn't promise bit-perfect output.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = eq.enabled, onCheckedChange = viewModel::setEnabled)
                }
                caps.failures.forEach { (name, why) ->
                    Text("$name was turned off after an error ($why).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = Spacing.l))
                }
            }
            if (caps.equalizer) {
                item(key = "presets") {
                    Text("Presets", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(Spacing.l).semantics { heading() })
                    LazyRow(contentPadding = PaddingValues(horizontal = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        items(EqPreset.entries) { p -> FilterChip(selected = eq.preset == p.name, onClick = { viewModel.selectPreset(p.name) }, label = { Text(p.label) }) }
                        items(eq.userPresets.keys.toList()) { name ->
                            FilterChip(selected = eq.preset == name, onClick = { viewModel.selectPreset(name) }, label = { Text(name) })
                        }
                        if (eq.preset == null) item { FilterChip(selected = true, onClick = {}, label = { Text("Custom") }) }
                    }
                    Row(Modifier.padding(horizontal = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        OutlinedButton(onClick = { saving = true }, modifier = Modifier.heightIn(min = Spacing.touch)) { Text("Save as preset") }
                        eq.preset?.takeIf { it in eq.userPresets }?.let { name ->
                            OutlinedButton(onClick = { viewModel.deletePreset(name) }, modifier = Modifier.heightIn(min = Spacing.touch)) { Text("Delete “$name”") }
                        }
                    }
                }
                val levels = EqMath.resolve(eq, caps)
                items(caps.bandCentersHz.indices.toList(), key = { "b$it" }) { band ->
                    BandSlider(
                        label = EqMath.formatHz(caps.bandCentersHz[band]),
                        levelMb = levels.getOrElse(band) { 0 },
                        min = caps.minLevelMb, max = caps.maxLevelMb,
                        enabled = eq.enabled,
                        onDone = { viewModel.setBand(band, it) },
                    )
                }
            } else {
                item(key = "no-eq") { Note("This device has no equalizer for apps; the effects below still work.") }
            }
            item(key = "effects") {
                HorizontalDivider(Modifier.padding(vertical = Spacing.s))
                if (caps.bassBoost) StrengthSlider("Bass boost", eq.bassBoost, EqSettings.MAX_STRENGTH, eq.enabled, viewModel::setBass)
                else Note("Bass boost: not supported on this device.")
                if (caps.virtualizer) StrengthSlider("Virtualizer (headphones)", eq.virtualizer, EqSettings.MAX_STRENGTH, eq.enabled, viewModel::setVirtualizer)
                else Note("Virtualizer: not supported on this device.")
                if (caps.loudness) StrengthSlider("Loudness", eq.loudnessMb, EqSettings.MAX_LOUDNESS_MB, eq.enabled, viewModel::setLoudness)
                else Note("Loudness: not supported on this device.")
                Note("If an effect fails, SVARĀ turns that effect off and keeps playing.")
            }
        }
    }
    if (saving) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text("Save preset") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(MAX_PRESET_NAME) }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { viewModel.savePreset(name); saving = false }, enabled = name.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { saving = false }) { Text("Cancel") } },
        )
    }
}

private const val MAX_PRESET_NAME = 24

@Composable
private fun BandSlider(label: String, levelMb: Int, min: Int, max: Int, enabled: Boolean, onDone: (Int) -> Unit) {
    var value by remember(levelMb) { mutableFloatStateOf(levelMb.toFloat()) }
    val db = value / EqMath.MB_PER_DB
    Column(Modifier.padding(horizontal = Spacing.l)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("${if (db >= 0) "+" else ""}${"%.1f".format(db)} dB", style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value, onValueChange = { value = it }, onValueChangeFinished = { onDone(value.toInt()) },
            valueRange = min.toFloat()..max.toFloat(), enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "$label band"; stateDescription = "${"%.1f".format(db)} decibels" },
        )
    }
}

@Composable
private fun StrengthSlider(label: String, value: Int, max: Int, enabled: Boolean, onDone: (Int) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.padding(horizontal = Spacing.l)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("${(v * PERCENT / max).toInt()} %", style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = v, onValueChange = { v = it }, onValueChangeFinished = { onDone(v.toInt()) }, valueRange = 0f..max.toFloat(), enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs))
}

private const val PERCENT = 100f

// endregion

// region ZENITH visualiser

@HiltViewModel
class VisualGalleryViewModel @Inject constructor(
    private val customization: CustomizationRepository,
    playback: PlaybackController,
) : ViewModel() {
    val visual: StateFlow<VisualSettings> = customization.customization.map { it.visual }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VisualSettings())
    val state = playback.state

    fun set(field: CustomField, value: String) {
        viewModelScope.launch { customization.set(field, value) }
    }
}

/**
 * Frame rate for the gallery: 30 fps (20 for pulse styles), none in Efficiency or battery saver
 * (the effective mode is Efficiency then) or with reduced motion, where one still frame is drawn.
 */
@Composable
fun galleryFps(style: VisualStyle): Int {
    val a = LocalAppearance.current
    if (!a.motion.enabled || a.limits.mode == PerformanceMode.EFFICIENCY) return 0
    return if (style.pulse) VisualTokens.PULSE_FPS else VisualTokens.MAX_FPS
}

/** Attaches analysis while this is resumed, playing and allowed; returns the bands, or null when static. */
@Composable
fun rememberGalleryBands(fps: Int, playing: Boolean): State<AudioBands>? {
    val vm: VisualiserViewModel = hiltViewModel()
    val supported by vm.supported.collectAsStateWithLifecycle()
    val live = fps > 0 && supported
    LifecycleResumeEffect(live, fps, playing) {
        if (live && playing) vm.show(fps) else vm.hide()
        onPauseOrDispose { vm.hide() }
    }
    return if (live) vm.bands.collectAsStateWithLifecycle() else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualiserScreen(navigator: Navigator, viewModel: VisualGalleryViewModel = hiltViewModel()) {
    val visual by viewModel.visual.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fps = galleryFps(visual.style)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ZENITH visualiser") },
                navigationIcon = { BackButton(navigator::back) },
                actions = { IconButton(onClick = navigator::visualiserFull) { Icon(Icons.Rounded.Fullscreen, contentDescription = "Full screen") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item(key = "preview") {
                val bands = rememberGalleryBands(fps, state.isPlaying)
                SpandaVisual(visual, bands, state.current?.songId ?: -1, state.current?.albumId ?: -1, Modifier.fillMaxWidth().aspectRatio(VisualTokens.PREVIEW_RATIO).padding(Spacing.l))
                Text(
                    when {
                        fps == 0 -> "Still frame: motion is off in Efficiency mode, battery saver or with reduced motion."
                        !state.isPlaying -> "Play a song to see it move. Nothing is analysed while paused."
                        else -> "Up to $fps frames a second, only while this screen is open."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.l),
                )
            }
            item(key = "styles") {
                LazyRow(contentPadding = PaddingValues(Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    items(VisualStyle.entries) { s -> FilterChip(selected = visual.style == s, onClick = { viewModel.set(CustomField.VIS_STYLE, s.name) }, label = { Text(s.label) }) }
                }
            }
            item(key = "controls") {
                Percent("Intensity", visual.intensity) { viewModel.set(CustomField.VIS_INTENSITY, it.toString()) }
                Percent("Sensitivity", visual.sensitivity) { viewModel.set(CustomField.VIS_SENSITIVITY, it.toString()) }
                Percent("Background", visual.backgroundOpacity) { viewModel.set(CustomField.VIS_BACKGROUND, it.toString()) }
                var bars by remember(visual.bars) { mutableFloatStateOf(visual.bars.toFloat()) }
                Column(Modifier.padding(horizontal = Spacing.l)) {
                    Text("Bars: ${bars.toInt()}", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = bars, onValueChange = { bars = it }, onValueChangeFinished = { viewModel.set(CustomField.VIS_BARS, bars.toInt().toString()) },
                        valueRange = VisualSettings.BAR_RANGE.first.toFloat()..VisualSettings.BAR_RANGE.last.toFloat(),
                        modifier = Modifier.semantics { contentDescription = "Number of bars" },
                    )
                }
                Toggle("Glow", visual.glow) { viewModel.set(CustomField.VIS_GLOW, it.toString()) }
                Toggle("Show artwork", visual.showArtwork) { viewModel.set(CustomField.VIS_ARTWORK, it.toString()) }
                Text("Colour", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s))
                Row(Modifier.padding(horizontal = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    VisualColour.entries.forEach { c -> FilterChip(selected = visual.colour == c, onClick = { viewModel.set(CustomField.VIS_COLOUR, c.name) }, label = { Text(c.label) }) }
                }
                Button(onClick = navigator::visualiserFull, modifier = Modifier.padding(Spacing.l).heightIn(min = Spacing.touch)) { Text("Full screen") }
            }
        }
    }
}

/** Full-screen immersive view; tap or Back to leave. Stops with the screen. */
@Composable
fun VisualiserFullScreen(navigator: Navigator, viewModel: VisualGalleryViewModel = hiltViewModel()) {
    val visual by viewModel.visual.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fps = galleryFps(visual.style)
    val bands = rememberGalleryBands(fps, state.isPlaying)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SpandaVisual(visual.copy(style = if (visual.style == VisualStyle.IMMERSIVE) VisualStyle.IMMERSIVE else visual.style), bands, state.current?.songId ?: -1, state.current?.albumId ?: -1, Modifier.fillMaxSize())
        IconButton(onClick = navigator::back, modifier = Modifier.safeDrawingPadding().align(Alignment.TopStart)) {
            Icon(Icons.Rounded.Close, contentDescription = "Leave full screen")
        }
    }
}

@Composable
private fun Percent(label: String, value: Int, onDone: (Int) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.padding(horizontal = Spacing.l)) {
        Text("$label: ${v.toInt()} %", style = MaterialTheme.typography.labelLarge)
        Slider(value = v, onValueChange = { v = it }, onValueChangeFinished = { onDone(v.toInt()) }, valueRange = 0f..PERCENT, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touch).padding(horizontal = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

/**
 * Draws one visual style from the smoothed bands. Bands are read in the draw phase, so updates
 * redraw without recomposition, at the analysis rate (the frame cap). If drawing throws, the
 * visual freezes on a still frame and reports it; playback, in the service, is untouched.
 */
@Composable
fun SpandaVisual(settings: VisualSettings, bands: State<AudioBands>?, songId: Long, albumId: Long, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val artColour = if (settings.colour == VisualColour.ARTWORK) rememberAlbumColour(songId, albumId) else null
    val colour = when (settings.colour) {
        VisualColour.ACCENT -> scheme.primary
        VisualColour.ARTWORK -> artColour ?: scheme.primary
        VisualColour.MONOCHROME -> scheme.onSurface
    }
    val glow = settings.glow && LocalAppearance.current.glow
    val failed = remember { mutableStateOf(false) }
    Box(modifier) {
        if (settings.showArtwork && songId > 0 && settings.style != VisualStyle.SPECTRUM_BARS && settings.style != VisualStyle.WAVEFORM) {
            ArtworkImage(songId, albumId, Modifier.fillMaxSize(VisualTokens.ARTWORK_FRACTION).align(Alignment.Center), shape = rememberArtworkShape())
        }
        Canvas(Modifier.fillMaxSize().background(scheme.scrim.copy(alpha = settings.backgroundOpacity / PERCENT * VisualTokens.MAX_SCRIM))) {
            if (failed.value) return@Canvas
            val b = bands?.value ?: AudioBands.ZERO
            VisualRenderer.safeDraw(this, settings, b, colour, glow) { failed.value = true }
        }
        if (failed.value) {
            Text("The visualiser stopped. Music keeps playing.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** Drawing for each style. Pure functions of the band values and settings. */
object VisualRenderer {
    /** Calls [draw]; any exception freezes the visual instead of crashing the app (and the music). */
    var drawHook: (DrawScope.(VisualSettings, AudioBands, Color, Boolean) -> Unit) = { s, b, c, g -> draw(s, b, c, g) }

    fun safeDraw(scope: DrawScope, settings: VisualSettings, bands: AudioBands, colour: Color, glow: Boolean, onFailure: () -> Unit) {
        try {
            drawHook(scope, settings, bands, colour, glow)
        } catch (e: RuntimeException) {
            android.util.Log.w("SpandaVisual", "Visual stopped", e)
            onFailure()
        }
    }

    /** Band values after sensitivity and intensity, 0..1. */
    fun levels(settings: VisualSettings, bands: AudioBands, count: Int): FloatArray {
        val gain = VisualTokens.MIN_GAIN + settings.sensitivity / PERCENT * VisualTokens.GAIN_SPAN
        val scale = settings.intensity / PERCENT
        return SpectrumMath.resample(bands.spectrum, count).map { (it * gain * scale).coerceIn(0f, 1f) }.toFloatArray()
    }

    fun DrawScope.draw(settings: VisualSettings, bands: AudioBands, colour: Color, glow: Boolean) {
        val minDim = size.minDimension
        val intensity = settings.intensity / PERCENT
        if (glow) {
            val r = minDim / 2f * (1f + bands.energy * intensity * VisualTokens.GLOW_GAIN)
            drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = VisualTokens.GLOW_ALPHA), colour.copy(alpha = 0f)), center, r), r)
        }
        when (settings.style) {
            VisualStyle.SPECTRUM_BARS -> {
                val lv = levels(settings, bands, settings.bars)
                val gap = size.width / lv.size * VisualTokens.BAR_GAP
                val w = size.width / lv.size - gap
                lv.forEachIndexed { i, l ->
                    val h = (size.height * l).coerceAtLeast(VisualTokens.MIN_BAR_PX)
                    drawRoundRect(colour, Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(w / 2f))
                }
            }
            VisualStyle.WAVEFORM -> {
                // A smooth line through the band values, mirrored around the centre line.
                val lv = levels(settings, bands, settings.bars)
                val path = Path()
                val mid = size.height / 2f
                lv.forEachIndexed { i, l ->
                    val x = size.width * i / (lv.size - 1).coerceAtLeast(1)
                    val y = mid - l * mid * if (i % 2 == 0) 1f else -1f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, colour, style = Stroke(width = VisualTokens.STROKE_PX))
            }
            VisualStyle.CIRCULAR, VisualStyle.IMMERSIVE -> {
                val lv = levels(settings, bands, settings.bars)
                val inner = minDim * VisualTokens.RING_INNER
                val reach = minDim * if (settings.style == VisualStyle.IMMERSIVE) VisualTokens.RING_REACH_FULL else VisualTokens.RING_REACH
                lv.forEachIndexed { i, l ->
                    val a = 2 * PI * i / lv.size - PI / 2
                    val start = Offset(center.x + (inner * cos(a)).toFloat(), center.y + (inner * sin(a)).toFloat())
                    val end = Offset(center.x + ((inner + reach * l) * cos(a)).toFloat(), center.y + ((inner + reach * l) * sin(a)).toFloat())
                    drawLine(colour, start, end, strokeWidth = VisualTokens.STROKE_PX * 2)
                }
            }
            VisualStyle.PULSE -> {
                val r = minDim * VisualTokens.PULSE_BASE * (1f + bands.bass * intensity * VisualTokens.PULSE_GAIN)
                drawCircle(colour.copy(alpha = VisualTokens.PULSE_ALPHA), r)
            }
            VisualStyle.ALBUM_AURA -> {
                val r = minDim / 2f * (VisualTokens.AURA_BASE + bands.energy * intensity * VisualTokens.AURA_GAIN)
                drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = VisualTokens.AURA_ALPHA), colour.copy(alpha = 0f)), center, r), r)
            }
        }
    }
}

// endregion
