package com.svara.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svara.app.appearance.colour.AmbientColours
import com.svara.app.data.covers.CoverOverrides
import com.svara.app.appearance.ColourSystem
import com.svara.app.appearance.Customization
import com.svara.app.appearance.EffectLimits
import com.svara.app.appearance.MotionSpec
import com.svara.app.appearance.ResolvedTheme
import com.svara.app.data.appearance.CustomizationRepository
import com.svara.app.data.appearance.EffectsRepository
import com.svara.app.data.appearance.PaletteRepository
import com.svara.app.data.appearance.ThemeRepository
import com.svara.app.domain.repository.LibraryRepository
import com.svara.app.domain.repository.SettingsRepository
import com.svara.app.playback.PlaybackController
import com.svara.app.ui.theme.Appearance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Resolves the theme, studio settings, effect limits and motion into one [Appearance] for the
 * root of the UI. Reads only; it never touches the player, the queue or the scanner.
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    themes: ThemeRepository,
    customization: CustomizationRepository,
    effects: EffectsRepository,
    settings: SettingsRepository,
    playback: PlaybackController,
    library: LibraryRepository,
    palettes: PaletteRepository,
    covers: CoverOverrides,
) : ViewModel() {

    private data class Inputs(
        val theme: ResolvedTheme,
        val custom: Customization,
        val limits: EffectLimits,
        val highContrast: Boolean,
        val reducedMotion: Boolean,
    )

    private val inputs: Flow<Inputs> = combine(
        themes.resolved,
        customization.customization,
        effects.limits,
        settings.settings.map { it.highContrast to it.reducedMotion }.distinctUntilChanged(),
    ) { theme, custom, limits, (hc, rm) -> Inputs(theme, custom, limits, hc, rm) }

    /**
     * The current song's cover colours, extracted only when something uses them (album-artwork
     * source or glow), on the palette dispatcher. Re-read when that song's cover changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val coverColours: Flow<AmbientColours?> = combine(
        themes.resolved.map { it.colourSystem == ColourSystem.ALBUM || it.glow }.distinctUntilChanged(),
        playback.state.map { it.current?.let { c -> c.songId to c.albumId } }.distinctUntilChanged(),
    ) { needed, current -> if (needed) current else null }
        .distinctUntilChanged()
        .flatMapLatest { current ->
            if (current == null) flowOf<AmbientColours?>(null)
            else covers.versionOf(current.first, current.second).flatMapLatest {
                flow<AmbientColours?> {
                    val (songId, albumId) = current
                    val song = library.songById(songId)
                    val c = runCatching { palettes.coloursFor(songId, albumId, song?.dateModifiedSec ?: 0L, song?.album, song?.fileName.orEmpty()) }.getOrNull()
                    emit(c?.let { AmbientColours.from(it.dominant, it.accent, it.muted) })
                }
            }
        }
        // Never hold up the first frame for artwork: start with the theme's own accent.
        .onStart { emit(null) }

    private val ready: Flow<Boolean> = combine(themes.loaded, customization.loaded) { a, b -> a && b }

    /** null until stored appearance has loaded. [Appearance.dark] is set by the UI from the system setting. */
    val appearance: StateFlow<Appearance?> = combine(inputs, coverColours, ready) { i, colours, isReady ->
        if (!isReady) return@combine null
        Appearance(
            theme = i.theme,
            custom = i.custom,
            limits = i.limits,
            motion = MotionSpec.from(
                limits = i.limits,
                style = i.theme.animationStyle,
                reducedMotion = i.reducedMotion,
                speed = i.custom.transitionSpeed,
                intensityPercent = i.custom.intensity,
                presetFadesOnly = i.theme.spec.fadesOnly,
            ),
            highContrast = i.highContrast,
            albumAccent = colours?.accent,
            ambient = colours,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
