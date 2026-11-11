package com.svara.app.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.svara.app.MainActivity
import com.svara.app.appearance.PresetId
import com.svara.app.appearance.StudioSection
import com.svara.app.di.AppearanceEntryPoint
import com.svara.app.nowplaying.TrackInfo
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Layout regressions from the UI-fix pass: Library's first row, Now Playing's title area. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@LargeTest
class UiLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun nowPlayingTitleAreaKeepsItsHeightForShortAndVeryLongTitles() {
        var title by mutableStateOf("Track 1")
        compose.setContent {
            MaterialTheme {
                Column {
                    TrackInfo(title, "Artist", "Album", isFavorite = false, leftHanded = false, emphasis = false, onToggleFavorite = {}, modifier = Modifier.fillMaxWidth())
                    Box(Modifier.fillMaxWidth().height(4.dp).testTag("below"))
                }
            }
        }
        val shortTop = compose.onNodeWithTag("below").getBoundsInRoot().top
        title = "An Extraordinarily Long Song Title That Keeps Going Well Past Two Lines On Any Phone Screen And Then Some More"
        compose.waitForIdle()
        val longTop = compose.onNodeWithTag("below").getBoundsInRoot().top
        assertEquals("seek bar and controls moved with the title", shortTop, longTop)
        title = "A medium title that fits on one line"
        compose.waitForIdle()
        assertEquals(shortTop, compose.onNodeWithTag("below").getBoundsInRoot().top)
    }
}

/** Library's first row starts below the pinned filter chips; recreation keeps the Library tab. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@LargeTest
class LibraryLayoutTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val entry = EntryPointAccessors.fromApplication(context, AppearanceEntryPoint::class.java)

    @After
    fun tearDown() = runBlocking {
        entry.themes().apply(com.svara.app.appearance.ThemeConfig())
        StudioSection.entries.forEach { entry.customization().resetSection(it) }
    }

    private fun open(): ActivityScenario<MainActivity> = runBlocking {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val audio = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        automation.grantRuntimePermission(context.packageName, audio)
        entry.flags().setOnboardingComplete()
        entry.themes().setPreset(PresetId.ANAHATA)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val library = entry.library()
        withTimeout(60_000) { while (library.scanState.value.isScanning) delay(100) }
        assumeTrue("needs music on the device", library.songCount.first() > 0)
        compose.waitUntilAtLeastOneExists(hasText("Songs"), 15_000)
        compose.onAllNodes(hasText("Songs")).onFirst().performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("song_row"), 15_000)
        scenario
    }

    @Test
    fun firstSongRowIsFullyVisibleBelowTheFilterChips() {
        val scenario = open()
        val chips = compose.onNodeWithTag("song_filters").getBoundsInRoot()
        val list = compose.onNodeWithTag("songs_list").getBoundsInRoot()
        val first = compose.onAllNodesWithTag("song_row").onFirst().getBoundsInRoot()
        assertTrue("first row ${first.top} starts under the chips ${chips.bottom}", first.top >= chips.bottom)
        assertTrue("first row runs past the list", first.bottom <= list.bottom)
        scenario.close()
    }

    @Test
    fun recreationKeepsTheLibraryTabAndScreen() {
        val scenario = open()
        // Artists is a Library tab, not a bottom-bar entry, so this text is unique.
        compose.onAllNodes(hasText("Artists")).onFirst().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("songs_list").fetchSemanticsNodes().isEmpty() }
        scenario.recreate()
        compose.waitUntilAtLeastOneExists(hasText("Artists"), 15_000)
        compose.waitForIdle()
        // Before the fix the start-tab jump ran again and reset Library to its Songs tab.
        assertTrue("recreation reset the Library tab", compose.onAllNodesWithTag("songs_list").fetchSemanticsNodes().isEmpty())
        assertTrue("recreation left Library", compose.onAllNodes(hasText("Library")).fetchSemanticsNodes().isNotEmpty())
        scenario.close()
    }
}
