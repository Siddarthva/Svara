package com.svara.app.settings

import com.svara.app.appearance.ColourSystem
import com.svara.app.data.prefs.FlagsStore
import com.svara.app.ui.PerfTrace
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svara.app.appearance.ControlId
import com.svara.app.appearance.CustomField
import com.svara.app.appearance.Customization
import com.svara.app.appearance.CustomizationCodec
import com.svara.app.appearance.EffectLimits
import com.svara.app.appearance.NavTab
import com.svara.app.appearance.PerformanceMode
import com.svara.app.appearance.PresetId
import com.svara.app.appearance.ResolvedTheme
import com.svara.app.appearance.StudioSection
import com.svara.app.appearance.ThemeConfig
import com.svara.app.appearance.ThemeField
import com.svara.app.appearance.ThemeSection
import com.svara.app.data.appearance.CustomizationRepository
import com.svara.app.data.appearance.EffectsRepository
import com.svara.app.data.appearance.SavedLook
import com.svara.app.data.appearance.SavedLookRepository
import com.svara.app.data.appearance.ThemeRepository
import com.svara.app.domain.repository.SettingsRepository
import com.svara.app.playback.UserMessages
import com.svara.app.playback.analysis.AudioAnalysis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Appearance settings. Every action writes one appearance override (or removes overrides);
 * none of them reaches the player, the queue or the scanner.
 */
@HiltViewModel
class AppearanceSettingsViewModel @Inject constructor(
    private val themes: ThemeRepository,
    private val customization: CustomizationRepository,
    private val looks: SavedLookRepository,
    effects: EffectsRepository,
    private val settings: SettingsRepository,
    private val flags: FlagsStore,
    analysis: AudioAnalysis,
    private val messages: UserMessages,
) : ViewModel() {

    val theme: StateFlow<ResolvedTheme> = themes.resolved
    val config: StateFlow<ThemeConfig> = themes.config
    val custom: StateFlow<Customization> = customization.customization
    val customOverrides: StateFlow<Map<CustomField, String>> = customization.overrides
    val limits: StateFlow<EffectLimits> = effects.limits
    val analysisSupported: StateFlow<Boolean> = analysis.supported
    val reducedMotion: StateFlow<Boolean> = settings.settings.map { it.reducedMotion }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val highContrast: StateFlow<Boolean> = settings.settings.map { it.highContrast }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val sections: StateFlow<Set<String>> = flags.appearanceSections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), setOf("theme", "colour"))
    val savedLooks: StateFlow<List<SavedLook>> = looks.looks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun applyPreset(id: PresetId) {
        PerfTrace.begin(PerfTrace.PRESET_SWITCH)
        viewModelScope.launch {
            themes.setPreset(id)
            messages.emit("Theme applied. Playback unchanged.")
        }
    }

    fun toggleSection(id: String) {
        val now = sections.value
        viewModelScope.launch { flags.setAppearanceSections(if (id in now) now - id else now + id) }
    }

    fun setColourSource(source: ColourSystem) {
        viewModelScope.launch { themes.setOverride(ThemeField.COLOUR_SYSTEM, source.name) }
    }

    /** A swatch: Custom accent with this swatch (the hue slider's value is cleared). */
    fun selectSwatch(id: String) {
        viewModelScope.launch {
            themes.edit(mapOf(ThemeField.COLOUR_SYSTEM to ColourSystem.CUSTOM.name, ThemeField.CUSTOM_SWATCH to id), remove = setOf(ThemeField.CUSTOM_HUE))
        }
    }

    /** The hue slider: Custom accent at this hue ("Custom hue"; no swatch selected). */
    fun setHue(hue: Float) {
        viewModelScope.launch {
            themes.edit(mapOf(ThemeField.COLOUR_SYSTEM to ColourSystem.CUSTOM.name, ThemeField.CUSTOM_HUE to hue.toString()), remove = setOf(ThemeField.CUSTOM_SWATCH))
        }
    }

    /** Back to the preset's own colour source (the custom swatch or hue is kept for later). */
    fun restorePresetColours() {
        viewModelScope.launch { themes.resetField(ThemeField.COLOUR_SYSTEM) }
    }

    fun setHighContrast(on: Boolean) {
        viewModelScope.launch { settings.update { it.copy(highContrast = on) } }
    }

    /** Motion section: animation style, transition speed, intensity and haptics. */
    fun resetMotion() {
        viewModelScope.launch {
            themes.resetSection(ThemeSection.MOTION)
            customization.resetSection(StudioSection.MOTION)
            customization.reset(CustomField.HAPTICS)
        }
    }

    /** Reset everything on the Appearance page: every theme override, motion, performance, brightness. */
    fun resetEverything() {
        viewModelScope.launch {
            themes.resetAll()
            customization.resetSection(StudioSection.MOTION)
            customization.reset(CustomField.HAPTICS)
            customization.reset(CustomField.PERFORMANCE_MODE)
            customization.reset(CustomField.BRIGHTNESS)
            messages.emit("Appearance reset to ${themes.config.value.preset.displayName} defaults. Playback unchanged.")
        }
    }

    fun setTheme(field: ThemeField, value: String) {
        viewModelScope.launch { themes.setOverride(field, value) }
    }

    fun resetThemeSection(section: ThemeSection) {
        viewModelScope.launch { themes.resetSection(section) }
    }

    fun resetTheme() {
        viewModelScope.launch {
            themes.resetAll()
            messages.emit("Theme reset to ${themes.config.value.preset.displayName} defaults")
        }
    }

    fun duplicate(name: String) {
        viewModelScope.launch {
            looks.saveCurrent(name)
            messages.emit("Saved “${name.trim().ifEmpty { "My look" }}”")
        }
    }

    fun applyLook(look: SavedLook) {
        viewModelScope.launch {
            if (looks.apply(look.id)) messages.emit("Applied “${look.name}”. Playback unchanged.")
        }
    }

    fun duplicateLook(look: SavedLook) {
        viewModelScope.launch {
            looks.duplicate(look.id, "${look.name} (copy)")
            messages.emit("Made a copy of “${look.name}”")
        }
    }

    fun renameLook(look: SavedLook, name: String) {
        viewModelScope.launch { looks.rename(look.id, name) }
    }

    fun deleteLook(look: SavedLook) {
        viewModelScope.launch { looks.delete(look.id) }
    }

    fun set(field: CustomField, value: String) {
        viewModelScope.launch { customization.set(field, value) }
    }

    fun resetStudio(section: StudioSection) {
        viewModelScope.launch { customization.resetSection(section) }
    }

    /** Performance section: performance mode and Smooth display back to their defaults. */
    fun resetPerformance() {
        viewModelScope.launch { customization.resetSection(StudioSection.PERFORMANCE) }
    }

    fun setPerformance(mode: PerformanceMode?) {
        viewModelScope.launch {
            if (mode == null) customization.reset(CustomField.PERFORMANCE_MODE) else customization.set(CustomField.PERFORMANCE_MODE, mode.name)
        }
    }

    /** Returns the reason when the change isn't allowed (last visible tab, or a full bar). */
    fun setTabVisible(tab: NavTab, visible: Boolean): String? {
        val c = custom.value
        val reason = if (visible) CustomizationCodec.canShow(c, tab) else CustomizationCodec.canHide(c, tab)
        if (reason != null) return reason
        val hidden = if (visible) c.navHidden - tab else c.navHidden + tab
        set(CustomField.NAV_HIDDEN, CustomizationCodec.encodeList(hidden.sortedBy { it.ordinal }))
        return null
    }

    fun moveTab(from: Int, to: Int) {
        val order = custom.value.navOrder.toMutableList()
        if (from !in order.indices || to !in order.indices) return
        order.add(to, order.removeAt(from))
        set(CustomField.NAV_ORDER, CustomizationCodec.encodeList(order))
    }

    fun moveControl(from: Int, to: Int) {
        val order: MutableList<ControlId> = custom.value.controlOrder.toMutableList()
        if (from !in order.indices || to !in order.indices) return
        order.add(to, order.removeAt(from))
        set(CustomField.CONTROL_ORDER, CustomizationCodec.encodeList(order))
    }
}
