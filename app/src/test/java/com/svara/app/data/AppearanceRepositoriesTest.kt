package com.svara.app.data

import com.svara.app.appearance.colour.Swatches
import com.svara.app.appearance.ColourSystem
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.svara.app.appearance.AnimationStyle
import com.svara.app.appearance.ArtworkSize
import com.svara.app.appearance.CustomField
import com.svara.app.appearance.HomeLayout
import com.svara.app.appearance.NavTab
import com.svara.app.appearance.PerformanceMode
import com.svara.app.appearance.PresetId
import com.svara.app.appearance.Presets
import com.svara.app.appearance.StudioSection
import com.svara.app.appearance.ThemeField
import com.svara.app.appearance.ThemeSection
import com.svara.app.data.appearance.CustomizationRepository
import com.svara.app.data.appearance.DeviceConditions
import com.svara.app.data.appearance.EffectsRepository
import com.svara.app.data.appearance.SavedLookRepository
import com.svara.app.data.appearance.ThemeRepository
import com.svara.app.data.db.SongEntity
import com.svara.app.data.db.SvaraDatabase
import com.svara.app.data.mediastore.AudioSource
import com.svara.app.data.repository.LibraryRepositoryImpl
import com.svara.app.domain.model.FolderKey
import com.svara.app.playback.UserMessages
import com.svara.app.playback.analysis.AudioAnalysis
import com.svara.app.settings.AppearanceSettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppearanceRepositoriesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var db: SvaraDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File(context.filesDir, "appearance-test-${System.nanoTime()}.preferences_pb")
        store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        db = inMemoryDb()
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
        file.delete()
        Dispatchers.resetMain()
    }

    private suspend fun <T> Flow<T>.awaitValue(predicate: (T) -> Boolean): T = withTimeout(5_000) { first(predicate) }

    private fun raw(): Map<String, Any> = runBlocking { store.data.first().asMap().mapKeys { it.key.name } }

    @Test
    fun `theme overrides persist and reset by section and in full`() = runBlocking {
        val themes = ThemeRepository(store, scope)
        themes.setPreset(PresetId.SHRUTI)
        themes.setOverride(ThemeField.GLOW, "true")
        themes.setOverride(ThemeField.ARTWORK_SIZE, ArtworkSize.SMALL.name)
        themes.setOverride(ThemeField.ANIMATION_STYLE, AnimationStyle.PLAYFUL.name)
        themes.config.awaitValue { it.overrides.size == 3 }

        // A fresh repository (an app restart) reads the same configuration.
        val restarted = ThemeRepository(store, scope)
        val c = restarted.config.awaitValue { it.preset == PresetId.SHRUTI && it.overrides.size == 3 }
        assertEquals("true", c.overrides[ThemeField.GLOW])

        themes.resetSection(ThemeSection.ARTWORK)
        assertEquals(setOf(ThemeField.ANIMATION_STYLE), themes.config.awaitValue { it.overrides.size == 1 }.overrides.keys)

        themes.resetAll()
        val cleared = themes.config.awaitValue { it.overrides.isEmpty() }
        assertEquals(PresetId.SHRUTI, cleared.preset)
        assertFalse("no overrides key once everything is reset", raw().containsKey("theme.overrides"))
    }

    @Test
    fun `only overrides are stored, never preset defaults`() = runBlocking {
        val themes = ThemeRepository(store, scope)
        val spec = Presets.spec(PresetId.ANAHATA)
        themes.setOverride(ThemeField.HOME_LAYOUT, spec.homeLayout.name)
        themes.setOverride(ThemeField.GLASS, spec.glass.toString())
        themes.setOverride(ThemeField.HOME_LAYOUT, HomeLayout.RECENT_FIRST.name)
        themes.config.awaitValue { it.overrides.isNotEmpty() }
        assertEquals("HOME_LAYOUT=RECENT_FIRST", raw()["theme.overrides"])
    }

    @Test
    fun `corrupt stored values fall back to defaults`() = runBlocking {
        store.edit {
            it[stringPreferencesKey("theme.preset")] = "BOGUS"
            it[stringPreferencesKey("theme.overrides")] = "GLASS=perhaps;;HOME_LAYOUT"
            it[stringPreferencesKey("custom.TEXT_SCALE")] = "9000"
            it[stringPreferencesKey("custom.NAV_HIDDEN")] = NavTab.entries.joinToString(",") { t -> t.name }
        }
        val themes = ThemeRepository(store, scope)
        val custom = CustomizationRepository(store, scope)
        val t = themes.resolved.awaitValue { true }
        themes.loaded.awaitValue { it }
        assertEquals(PresetId.ANAHATA, themes.resolved.value.preset)
        assertEquals(Presets.spec(PresetId.ANAHATA).glass, t.glass)
        custom.loaded.awaitValue { it }
        val c = custom.customization.awaitValue { it.textScale == 130 }
        assertTrue(c.visibleTabs.isNotEmpty())
    }

    @Test
    fun `studio settings store only differences and reset by section`() = runBlocking {
        val custom = CustomizationRepository(store, scope)
        custom.set(CustomField.SEEK_INCREMENT, "15")
        custom.set(CustomField.HAPTICS, "false")
        custom.set(CustomField.TEXT_SCALE, "100") // default: not stored
        custom.customization.awaitValue { it.seekIncrementSec == 15 && !it.haptics }
        assertNull(raw()["custom.TEXT_SCALE"])

        custom.resetSection(StudioSection.CONTROL)
        val reset = custom.customization.awaitValue { it.seekIncrementSec == 10 && it.haptics }
        assertTrue(reset.haptics)
        assertTrue(raw().keys.none { it.startsWith("custom.") })
    }

    @Test
    fun `saved looks can be created, renamed, applied and deleted`() = runBlocking {
        val themes = ThemeRepository(store, scope)
        val looks = SavedLookRepository(db.savedLookDao(), themes) { 1_000L }
        themes.setPreset(PresetId.RAGA)
        themes.setOverride(ThemeField.GLASS, "true")
        themes.config.awaitValue { it.preset == PresetId.RAGA && it.overrides.isNotEmpty() }

        val id = looks.saveCurrent("Evening")
        val saved = looks.looks.awaitValue { it.size == 1 }.single()
        assertEquals("Evening", saved.name)
        assertEquals(1_000L, saved.createdAt)
        assertEquals(PresetId.RAGA, saved.config.preset)
        assertEquals(mapOf(ThemeField.GLASS to "true"), saved.config.overrides)

        looks.rename(id, "  Late night  ")
        assertEquals("Late night", looks.looks.awaitValue { it.single().name != "Evening" }.single().name)

        // Applying replaces the current overrides.
        themes.setPreset(PresetId.TALA)
        themes.resetAll()
        themes.setOverride(ThemeField.HOME_LAYOUT, HomeLayout.PLAYLIST_CARDS.name)
        assertTrue(looks.apply(id))
        val applied = themes.config.awaitValue { it.preset == PresetId.RAGA }
        assertEquals(mapOf(ThemeField.GLASS to "true"), applied.overrides)

        looks.delete(id)
        assertTrue(looks.looks.awaitValue { it.isEmpty() }.isEmpty())
        assertFalse(looks.apply(id))
    }

    @Test
    fun `saved looks keep V3 settings, duplicate them and restore them`() = runBlocking {
        val themes = ThemeRepository(store, scope)
        val custom = CustomizationRepository(store, scope)
        val looks = SavedLookRepository(db.savedLookDao(), themes, custom) { 2_000L }
        themes.setPreset(PresetId.SPANDA)
        custom.set(com.svara.app.appearance.CustomField.NOW_PLAYING_STYLE, com.svara.app.appearance.NowPlayingStyle.CINEMA.name)
        custom.set(com.svara.app.appearance.CustomField.VIS_STYLE, com.svara.app.appearance.VisualStyle.CIRCULAR.name)
        custom.customization.awaitValue { it.visual.style == com.svara.app.appearance.VisualStyle.CIRCULAR }

        val id = looks.saveCurrent("Night")
        val copyId = looks.duplicate(id, "Night (copy)")!!
        val all = looks.looks.awaitValue { it.size == 2 }
        val copy = all.first { it.id == copyId }
        assertEquals(com.svara.app.appearance.NowPlayingStyle.CINEMA.name, copy.custom[com.svara.app.appearance.CustomField.NOW_PLAYING_STYLE])
        assertEquals(PresetId.SPANDA, copy.config.preset)

        // Change everything, then restore: preset and V3 settings come back.
        themes.setPreset(PresetId.TALA)
        custom.replaceAll(emptyMap())
        custom.customization.awaitValue { it.nowPlayingStyle == com.svara.app.appearance.NowPlayingStyle.CLASSIC }
        assertTrue(looks.apply(copyId))
        val restored = custom.customization.awaitValue { it.nowPlayingStyle == com.svara.app.appearance.NowPlayingStyle.CINEMA }
        assertEquals(com.svara.app.appearance.VisualStyle.CIRCULAR, restored.visual.style)
        assertEquals(PresetId.SPANDA, themes.config.awaitValue { it.preset == PresetId.SPANDA }.preset)
    }

    /** Fails the test if anything asks the music library to scan. */
    private class FailingAudioSource : AudioSource {
        override suspend fun fingerprint(): String = throw AssertionError("appearance code must not touch the scanner")
        override suspend fun scan(folders: List<FolderKey>?, onProgress: (Int, Int) -> Unit): List<SongEntity> =
            throw AssertionError("appearance code must not trigger a library scan")
    }

    @Test
    fun `studio and theme changes never reach the scanner`() = runBlocking {
        val settings = FakeSettings()
        // The real library repository, wired to a source that fails on any scan.
        LibraryRepositoryImpl(
            FailingAudioSource(), db.songDao(), db.folderRuleDao(), db.settingsDao(), settings, db.tx(),
            scope = scope, scanDispatcher = Dispatchers.IO, compute = Dispatchers.Default, metadataOverrides = db.metadataOverrideDao(),
        )
        val themes = ThemeRepository(store, scope)
        val custom = CustomizationRepository(store, scope)
        val effects = EffectsRepository(themes, custom, DeviceConditions(context), scope)
        val vm = AppearanceSettingsViewModel(
            themes, custom, SavedLookRepository(db.savedLookDao(), themes) { 0L }, effects, settings, com.svara.app.data.prefs.FlagsStore(context), AudioAnalysis(), UserMessages(),
        )
        PresetId.entries.forEach(vm::applyPreset)
        ThemeField.entries.forEach { vm.setTheme(it, "1") }
        ThemeSection.entries.forEach(vm::resetThemeSection)
        vm.resetTheme()
        vm.duplicate("Copy")
        CustomField.entries.forEach { vm.set(it, "5") }
        StudioSection.entries.forEach(vm::resetStudio)
        PerformanceMode.entries.forEach(vm::setPerformance)
        vm.setTabVisible(NavTab.ARTISTS, true)
        vm.moveTab(0, 3)
        vm.moveControl(0, 2)
        // v2.1 Appearance page actions.
        vm.selectSwatch(Swatches.chromatic.first().id)
        vm.setHue(200f)
        ColourSystem.entries.forEach(vm::setColourSource)
        vm.restorePresetColours()
        vm.setHighContrast(true)
        vm.toggleSection("blur")
        vm.resetMotion()
        vm.resetEverything()
        themes.config.awaitValue { it.preset == PresetId.SPANDA }
        // Reaching here without an AssertionError from the source means no scan was requested.
        assertEquals(PresetId.SPANDA, themes.config.value.preset)
    }

    /** The colour source in effect: the override, or the preset's own default. */
    private fun source(c: com.svara.app.appearance.ThemeConfig) =
        ColourSystem.valueOf(c.overrides[ThemeField.COLOUR_SYSTEM] ?: Presets.spec(c.preset).colourSystem.name)

    private fun viewModel(themes: ThemeRepository, custom: CustomizationRepository, settings: FakeSettings = FakeSettings()) =
        AppearanceSettingsViewModel(
            themes, custom, SavedLookRepository(db.savedLookDao(), themes) { 0L },
            EffectsRepository(themes, custom, DeviceConditions(context), scope), settings,
            com.svara.app.data.prefs.FlagsStore(context), AudioAnalysis(), UserMessages(),
        )

    @Test
    fun `colour source switching, swatch and hue replace each other`() = runBlocking {
        val themes = ThemeRepository(store, scope)
        val vm = viewModel(themes, CustomizationRepository(store, scope))
        val swatch = Swatches.chromatic[5].id

        vm.selectSwatch(swatch)
        var c = themes.config.awaitValue { it.overrides[ThemeField.CUSTOM_SWATCH] == swatch }
        assertEquals(ColourSystem.CUSTOM.name, c.overrides[ThemeField.COLOUR_SYSTEM])

        vm.setHue(200f)
        c = themes.config.awaitValue { ThemeField.CUSTOM_HUE in it.overrides }
        assertNull("moving the hue slider deselects the swatch", c.overrides[ThemeField.CUSTOM_SWATCH])

        vm.selectSwatch(swatch)
        c = themes.config.awaitValue { ThemeField.CUSTOM_SWATCH in it.overrides }
        assertNull("choosing a swatch clears the custom hue", c.overrides[ThemeField.CUSTOM_HUE])

        listOf(ColourSystem.ALBUM, ColourSystem.MONOCHROME, ColourSystem.CUSTOM).forEach { source ->
            vm.setColourSource(source)
            themes.config.awaitValue { source(it) == source }
        }
        // Restore preset colours: the source goes back, the custom choice waits for later.
        vm.restorePresetColours()
        c = themes.config.awaitValue { ThemeField.COLOUR_SYSTEM !in it.overrides }
        assertEquals(Presets.spec(c.preset).colourSystem, source(c))
        assertEquals(swatch, c.overrides[ThemeField.CUSTOM_SWATCH])
    }

    @Test
    fun `custom accent survives a preset switch`() = runBlocking {
        val themes = ThemeRepository(store, scope)
        val vm = viewModel(themes, CustomizationRepository(store, scope))
        val swatch = Swatches.chromatic[10].id
        vm.selectSwatch(swatch)
        themes.config.awaitValue { it.overrides[ThemeField.CUSTOM_SWATCH] == swatch }
        PresetId.entries.forEach { id ->
            vm.applyPreset(id)
            val c = themes.config.awaitValue { it.preset == id }
            assertEquals("${id.name} keeps the swatch", swatch, c.overrides[ThemeField.CUSTOM_SWATCH])
            assertEquals(ColourSystem.CUSTOM, source(c))
        }
    }

    @Test
    fun `reset this section and reset everything`() = runBlocking {
        val themes = ThemeRepository(store, scope)
        val custom = CustomizationRepository(store, scope)
        val settings = FakeSettings()
        val vm = viewModel(themes, custom, settings)
        vm.applyPreset(PresetId.RAGA)
        vm.setHue(120f)
        vm.setTheme(ThemeField.AMBIENT_INTENSITY, "30")
        vm.setTheme(ThemeField.BLUR_STRENGTH, "5")
        vm.setTheme(ThemeField.ANIMATION_STYLE, AnimationStyle.PLAYFUL.name)
        vm.set(CustomField.HAPTICS, "false")
        vm.setPerformance(PerformanceMode.EFFICIENCY)
        themes.config.awaitValue { it.overrides.size == 5 }

        vm.resetThemeSection(ThemeSection.AMBIENT)
        var c = themes.config.awaitValue { ThemeField.AMBIENT_INTENSITY !in it.overrides }
        assertEquals("other sections are untouched", "5", c.overrides[ThemeField.BLUR_STRENGTH])

        vm.resetMotion()
        themes.config.awaitValue { ThemeField.ANIMATION_STYLE !in it.overrides }
        custom.customization.awaitValue { it.haptics }

        vm.resetEverything()
        c = themes.config.awaitValue { it.overrides.isEmpty() }
        assertEquals("the preset stays", PresetId.RAGA, c.preset)
        assertNull(custom.customization.awaitValue { it.performanceMode == null }.performanceMode)
    }
}
