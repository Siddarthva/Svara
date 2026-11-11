package com.svara.app.di

import com.svara.app.data.appearance.CustomizationRepository
import com.svara.app.data.appearance.ThemeRepository
import com.svara.app.data.prefs.FlagsStore
import com.svara.app.domain.repository.LibraryRepository
import com.svara.app.playback.analysis.AudioAnalysis
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Lets instrumented tests reach the app's own appearance singletons. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppearanceEntryPoint {
    fun themes(): ThemeRepository
    fun customization(): CustomizationRepository
    fun analysis(): AudioAnalysis
    fun library(): LibraryRepository
    fun flags(): FlagsStore
    fun settings(): com.svara.app.domain.repository.SettingsRepository
    fun effects(): com.svara.app.playback.effects.AudioEffectsHub
    fun widgetStore(): com.svara.app.widget.WidgetStateStore
}
