package com.svara.app.di

import com.svara.app.data.artwork.ArtworkLoader
import com.svara.app.data.covers.CoverOverrides
import com.svara.app.data.covers.CoverRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Custom covers for composables that aren't tied to a ViewModel (artwork images, editors). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CoverEntryPoint {
    fun covers(): CoverOverrides
    fun repository(): CoverRepository
    fun artwork(): ArtworkLoader
}
