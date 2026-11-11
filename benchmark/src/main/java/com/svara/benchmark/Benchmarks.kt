package com.svara.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Generates the Baseline Profile: run `./gradlew :app:generateBaselineProfile`. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        prepare()
        openLibrary()
        scrollSongs(flings = 2)
        startPlaybackFromLibrary()
        openAndCloseNowPlaying()
    }
}

@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Before fun onboard() = passOnboardingOnce()

    @Test fun coldStartupNoCompilation() = startup(CompilationMode.None())

    @Test fun coldStartupBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = {
            pressHome()
            grantAudioPermission()
        },
    ) {
        startActivityAndWait()
    }
}

/** Songs scroll (about 100 rows: six flings of roughly 15–20 rows) and Now Playing in each preset. */
@OptIn(ExperimentalMetricApi::class)
@RunWith(Parameterized::class)
@LargeTest
class PresetFramesBenchmark(private val preset: String) {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test
    fun scrollSongs() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            prepare()
            choosePreset(preset)
            openLibrary()
        },
    ) {
        scrollSongs(flings = 6)
    }

    @Test
    fun openNowPlaying() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric(), TraceSectionMetric("NowPlayingOpen", TraceSectionMetric.Mode.First)),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            prepare()
            choosePreset(preset)
            openLibrary()
            startPlaybackFromLibrary()
        },
    ) {
        openAndCloseNowPlaying()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun presets() = PRESETS
    }
}

/** Tap a preset in the theme chooser until the first frame drawn with it. */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
@LargeTest
class PresetSwitchBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()
    private var switches = 0

    @Test
    fun switchPreset() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(TraceSectionMetric("PresetSwitch", TraceSectionMetric.Mode.First), FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 10,
        setupBlock = {
            prepare()
            openThemeChooser()
            // Start from ETHEREAL, so every measured tap changes the preset.
            if (switches == 0) tapPreset(PRESETS[0])
        },
    ) {
        // Halo, SOLACE, OCTAVE, ZENITH, ETHEREAL, Halo, …
        tapPreset(PRESETS[(switches + 1) % PRESETS.size])
        switches++
    }
}
