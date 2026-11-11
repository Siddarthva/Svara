package com.svara.app.data.prefs

import androidx.datastore.preferences.core.stringSetPreferencesKey
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.flagsDataStore by preferencesDataStore(name = "svara_flags")

/** Simple flags that don't belong in Room (Preferences DataStore). */
@Singleton
class FlagsStore @Inject constructor(@ApplicationContext private val context: Context) {

    val lastScanAtMillis: Flow<Long> = context.flagsDataStore.data.map { it[LAST_SCAN] ?: 0L }
    val onboardingComplete: Flow<Boolean> = context.flagsDataStore.data.map { it[ONBOARDING_DONE] ?: false }

    suspend fun setLastScanAt(millis: Long) {
        context.flagsDataStore.edit { it[LAST_SCAN] = millis }
    }

    /** Expanded sections on the Appearance page; Theme and Colour on first launch. */
    val appearanceSections: Flow<Set<String>> = context.flagsDataStore.data.map { it[APPEARANCE_SECTIONS] ?: DEFAULT_SECTIONS }

    suspend fun setAppearanceSections(sections: Set<String>) {
        context.flagsDataStore.edit { it[APPEARANCE_SECTIONS] = sections }
    }

    /** The bottom-bar tab last opened, restored on the next launch. */
    val lastTab: Flow<String?> = context.flagsDataStore.data.map { it[LAST_TAB] }

    suspend fun setLastTab(name: String) {
        context.flagsDataStore.edit { it[LAST_TAB] = name }
    }

    suspend fun setOnboardingComplete() {
        context.flagsDataStore.edit { it[ONBOARDING_DONE] = true }
    }

    private companion object {
        val LAST_SCAN = longPreferencesKey("last_scan_at")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val LAST_TAB = stringPreferencesKey("last_tab")
        val APPEARANCE_SECTIONS = stringSetPreferencesKey("appearance_sections")
        val DEFAULT_SECTIONS = setOf("theme", "colour")
    }
}
