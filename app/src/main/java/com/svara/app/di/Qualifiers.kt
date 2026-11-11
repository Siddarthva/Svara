package com.svara.app.di

import javax.inject.Qualifier

/** Scope that lives as long as the process; used for work that must outlive any screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Single-threaded dispatcher dedicated to library scanning. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ScanDispatcher

/** Single-threaded IO dispatcher for smart playlist evaluation (it is database-bound). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RulesDispatcher

/** Single background thread for palette extraction. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PaletteDispatcher

/** Single background thread for audio analysis (FFT). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AnalysisDispatcher
