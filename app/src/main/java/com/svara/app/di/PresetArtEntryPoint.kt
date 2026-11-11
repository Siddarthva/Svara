package com.svara.app.di

import com.svara.app.data.appearance.PaletteRepository
import com.svara.app.data.artwork.BlurredArtworkCache
import com.svara.app.data.covers.CoverOverrides
import com.svara.app.domain.repository.LibraryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Cached album colours and blurred artwork for the layout presets' backgrounds. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PresetArtEntryPoint {
    fun palettes(): PaletteRepository
    fun blurred(): BlurredArtworkCache
    fun library(): LibraryRepository
    fun covers(): CoverOverrides
}
