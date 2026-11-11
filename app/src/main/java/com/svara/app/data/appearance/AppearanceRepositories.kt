package com.svara.app.data.appearance

import com.svara.app.appearance.AppearanceDefaults
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.svara.app.appearance.CustomField
import com.svara.app.appearance.Customization
import com.svara.app.appearance.CustomizationCodec
import com.svara.app.appearance.PresetId
import com.svara.app.appearance.Presets
import com.svara.app.appearance.ResolvedTheme
import com.svara.app.appearance.StudioSection
import com.svara.app.appearance.ThemeConfig
import com.svara.app.appearance.ThemeConfigCodec
import com.svara.app.appearance.ThemeField
import com.svara.app.appearance.ThemeResolver
import com.svara.app.appearance.ThemeSection
import com.svara.app.appearance.ThemeSpec
import com.svara.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppearanceStore

/**
 * Single source of truth for the theme. Stores the preset id and only the fields the user
 * changed; values equal to the preset default are never stored.
 */
@Singleton
class ThemeRepository @Inject constructor(
    @AppearanceStore private val store: DataStore<Preferences>,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _loaded = MutableStateFlow(false)
    /** False until the stored configuration has been read once, so the UI never flashes defaults. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val config: StateFlow<ThemeConfig> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> ThemeConfig(PresetId.parse(prefs[PRESET]), ThemeConfigCodec.decodeOverrides(prefs[OVERRIDES])) }
        .onEach { _loaded.value = true }
        .stateIn(scope, SharingStarted.Eagerly, ThemeConfig())

    val resolved: StateFlow<ResolvedTheme> = config.map(ThemeResolver::resolve)
        .stateIn(scope, SharingStarted.Eagerly, ThemeResolver.resolve(ThemeConfig()))

    /** Changing preset keeps the user's overrides, minus any that now equal the new defaults. */
    suspend fun setPreset(id: PresetId) = update { prune(it.copy(preset = id)) }

    suspend fun setOverride(field: ThemeField, value: String) = update { prune(it.with(field, value)) }

    suspend fun resetField(field: ThemeField) = update { it.without(field) }

    suspend fun resetSection(section: ThemeSection) = update { it.resetSection(section) }

    suspend fun resetAll() = update { it.resetAll() }

    /** Sets and removes several overrides in one write (e.g. choosing a swatch). */
    suspend fun edit(set: Map<ThemeField, String>, remove: Set<ThemeField> = emptySet()) = update { c ->
        prune(ThemeConfig(c.preset, (c.overrides - remove) + set))
    }

    /** Replaces the whole configuration (used to apply a saved look). */
    suspend fun apply(config: ThemeConfig) = update { prune(config) }

    private suspend fun update(transform: (ThemeConfig) -> ThemeConfig) {
        store.edit { prefs ->
            val current = ThemeConfig(PresetId.parse(prefs[PRESET]), ThemeConfigCodec.decodeOverrides(prefs[OVERRIDES]))
            val next = transform(current)
            prefs[PRESET] = next.preset.name
            if (next.overrides.isEmpty()) prefs.remove(OVERRIDES) else prefs[OVERRIDES] = ThemeConfigCodec.encodeOverrides(next.overrides)
        }
    }

    companion object {
        private val PRESET = stringPreferencesKey("theme.preset")
        private val OVERRIDES = stringPreferencesKey("theme.overrides")

        /** The value a field has when not overridden, in the same encoding as stored overrides. */
        fun defaultValue(spec: ThemeSpec, field: ThemeField): String = when (field) {
            ThemeField.HOME_LAYOUT -> spec.homeLayout.name
            ThemeField.COLOUR_SYSTEM -> spec.colourSystem.name
            ThemeField.CUSTOM_ACCENT -> "0"
            ThemeField.ANIMATION_STYLE -> spec.animationStyle.name
            ThemeField.ARTWORK_SIZE -> spec.artworkSize.name
            ThemeField.GLASS -> spec.glass.toString()
            ThemeField.GLOW -> spec.glow.toString()
            ThemeField.VISUALISER -> spec.visualiser.name
            ThemeField.CUSTOM_SWATCH, ThemeField.CUSTOM_HUE -> ""
            ThemeField.BACKGROUND_TONE -> AppearanceDefaults.BACKGROUND_TONE.name
            ThemeField.AMBIENT_INTENSITY -> AppearanceDefaults.AMBIENT_INTENSITY.toString()
            ThemeField.BLUR_STRENGTH -> AppearanceDefaults.BLUR_STRENGTH.toString()
            ThemeField.FROSTED -> AppearanceDefaults.FROSTED.toString()
            ThemeField.BLUR_TINT -> AppearanceDefaults.BLUR_TINT.toString()
        }

        /** Drops overrides that equal the preset's defaults, so only real changes are stored. */
        fun prune(config: ThemeConfig): ThemeConfig {
            val spec = Presets.spec(config.preset)
            return config.copy(overrides = config.overrides.filter { (field, value) -> value != defaultValue(spec, field) })
        }
    }
}

/** Studio settings (navigation, gestures, typography …). Also stores only overrides. */
@Singleton
class CustomizationRepository @Inject constructor(
    @AppearanceStore private val store: DataStore<Preferences>,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val overrides: StateFlow<Map<CustomField, String>> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map(::read)
        .onEach { _loaded.value = true }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val customization: StateFlow<Customization> = overrides.map(CustomizationCodec::decode)
        .stateIn(scope, SharingStarted.Eagerly, Customization())

    suspend fun set(field: CustomField, value: String) {
        store.edit { prefs ->
            val key = key(field)
            if (value == defaultValue(field)) prefs.remove(key) else prefs[key] = value
        }
    }

    suspend fun reset(field: CustomField) {
        store.edit { it.remove(key(field)) }
    }

    /** Replaces every customization override with [map] (restoring a saved look). */
    suspend fun replaceAll(map: Map<CustomField, String>) {
        store.edit { prefs ->
            CustomField.entries.forEach { prefs.remove(key(it)) }
            map.forEach { (f, v) -> if (v != defaultValue(f)) prefs[key(f)] = v }
        }
    }

    suspend fun resetSection(section: StudioSection) {
        store.edit { prefs -> CustomField.entries.filter { it.section == section }.forEach { prefs.remove(key(it)) } }
    }

    private fun read(prefs: Preferences): Map<CustomField, String> =
        CustomField.entries.mapNotNull { f -> prefs[key(f)]?.let { f to it } }.toMap()

    companion object {
        private fun key(field: CustomField) = stringPreferencesKey("custom.${field.name}")

        private val DEFAULTS = Customization()

        /** Defaults in stored form; setting a field to its default removes the override. */
        fun defaultValue(field: CustomField): String? = when (field) {
            CustomField.NAV_ORDER -> CustomizationCodec.encodeList(DEFAULTS.navOrder)
            CustomField.NAV_HIDDEN -> CustomizationCodec.encodeList(DEFAULTS.navHidden.sortedBy { it.ordinal })
            CustomField.CONTROL_ORDER -> CustomizationCodec.encodeList(DEFAULTS.controlOrder)
            CustomField.PROGRESS_STYLE -> DEFAULTS.progressStyle.name
            CustomField.LYRICS_POSITION -> DEFAULTS.lyricsPosition.name
            CustomField.QUEUE_STYLE -> DEFAULTS.queueStyle.name
            CustomField.NOW_PLAYING_STYLE -> DEFAULTS.nowPlayingStyle.name
            CustomField.ARTWORK_AURA -> DEFAULTS.artworkAura.toString()
            CustomField.SWIPE_SKIP -> DEFAULTS.swipeToSkip.toString()
            CustomField.DOUBLE_TAP -> DEFAULTS.doubleTap.name
            CustomField.TEXT_SCALE -> DEFAULTS.textScale.toString()
            CustomField.DENSITY -> DEFAULTS.density.name
            CustomField.CORNER -> DEFAULTS.corner.name
            CustomField.PLAYER_BLUR -> null // default depends on the preset
            CustomField.TRANSITION_SPEED -> DEFAULTS.transitionSpeed.toString()
            CustomField.INTENSITY -> DEFAULTS.intensity.toString()
            CustomField.PAGE_TRANSITIONS -> DEFAULTS.pageTransitions.toString()
            CustomField.SCROLL_EFFECTS -> DEFAULTS.scrollEffects.toString()
            CustomField.SONG_CHANGE_ANIMATION -> DEFAULTS.songChangeAnimation.toString()
            CustomField.SMART_SMOOTHING -> DEFAULTS.smartSmoothing.toString()
            CustomField.RECAP_ANIMATIONS -> DEFAULTS.recapAnimations.toString()
            CustomField.LIVE_WAVE -> DEFAULTS.liveWave.toString()
            CustomField.WAVE_STRENGTH -> DEFAULTS.waveStrength.name
            CustomField.EXTRAS -> DEFAULTS.animationExtras.toString()
            CustomField.EXTRAS_OFF -> CustomizationCodec.encodeList(DEFAULTS.extrasOff.sortedBy { it.ordinal })
            CustomField.SEEK_INCREMENT -> DEFAULTS.seekIncrementSec.toString()
            CustomField.HAPTICS -> DEFAULTS.haptics.toString()
            CustomField.PERFORMANCE_MODE -> null // default follows the preset's tier
            CustomField.SMOOTH_DISPLAY -> DEFAULTS.smoothDisplay.toString()
            CustomField.BRIGHTNESS -> DEFAULTS.brightness.name
            CustomField.VIS_STYLE -> DEFAULTS.visual.style.name
            CustomField.VIS_INTENSITY -> DEFAULTS.visual.intensity.toString()
            CustomField.VIS_BARS -> DEFAULTS.visual.bars.toString()
            CustomField.VIS_GLOW -> DEFAULTS.visual.glow.toString()
            CustomField.VIS_BACKGROUND -> DEFAULTS.visual.backgroundOpacity.toString()
            CustomField.VIS_COLOUR -> DEFAULTS.visual.colour.name
            CustomField.VIS_ARTWORK -> DEFAULTS.visual.showArtwork.toString()
            CustomField.VIS_SENSITIVITY -> DEFAULTS.visual.sensitivity.toString()
        }
    }
}
