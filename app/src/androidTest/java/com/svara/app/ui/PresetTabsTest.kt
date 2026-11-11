package com.svara.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.svara.app.appearance.Customization
import com.svara.app.appearance.EffectsPolicy
import com.svara.app.appearance.NavTab
import com.svara.app.appearance.PerformanceMode
import com.svara.app.appearance.PresetId
import com.svara.app.appearance.Presets
import com.svara.app.appearance.TabVisibility
import com.svara.app.appearance.VisualiserMode
import com.svara.app.ui.presets.common.PresetNavBar
import com.svara.app.ui.theme.LocalPresetUi
import com.svara.app.ui.theme.PresetUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Each preset's bottom bar shows its own tabs, honours hidden tabs, and never ends up empty. */
@RunWith(AndroidJUnit4::class)
class PresetTabsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun ui(id: PresetId) = PresetUi.build(
        id, Presets.tokens(id), Presets.tokens(id).palette.dark, dark = true, highContrast = false,
        limits = EffectsPolicy.limitsFor(PerformanceMode.BALANCED), motionEnabled = true, visualiser = VisualiserMode.OFF,
    )

    private val layoutPresets = PresetId.entries - PresetId.ANAHATA

    private val isTab = SemanticsMatcher("is a bottom-bar tab") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("tab_") == true
    }

    @Test
    fun tabsFollowThePresetAndHiddenTabsAndNeverEmpty() {
        var preset by mutableStateOf(PresetId.SHRUTI)
        var custom by mutableStateOf(Customization())
        var selected by mutableStateOf<NavTab?>(NavTab.HOME)
        compose.setContent {
            val p = ui(preset)
            CompositionLocalProvider(LocalPresetUi provides p) {
                val tabs = TabVisibility.visible(p.navigation.visibleTabs, custom)
                PresetNavBar(tabs, selected, onSelect = { selected = it })
            }
        }
        layoutPresets.forEach { id ->
            preset = id
            custom = Customization()
            compose.waitForIdle()
            val expected = Presets.tokens(id).navigation.visibleTabs
            compose.onAllNodes(isTab).assertCountEquals(expected.size)
            expected.forEach { compose.onNodeWithTag("tab_${it.name}").assertIsDisplayed() }

            // Navigation Studio hides one tab within the preset.
            custom = Customization(navHidden = setOf(NavTab.SEARCH))
            compose.waitForIdle()
            compose.onAllNodes(isTab).assertCountEquals(expected.size - 1)

            // Hiding everything still leaves one tab.
            custom = Customization(navHidden = NavTab.entries.toSet())
            compose.waitForIdle()
            compose.onAllNodes(isTab).assertCountEquals(1)
            compose.onNodeWithTag("tab_${expected.first().name}").assertIsDisplayed()
        }
    }

    @Test
    fun tappingATabSelectsIt() {
        var selected by mutableStateOf<NavTab?>(NavTab.HOME)
        compose.setContent {
            val p = ui(PresetId.TALA)
            CompositionLocalProvider(LocalPresetUi provides p) {
                PresetNavBar(p.navigation.visibleTabs, selected, onSelect = { selected = it })
            }
        }
        compose.onNodeWithTag("tab_LIBRARY").performClick()
        compose.waitForIdle()
        assertEquals(NavTab.LIBRARY, selected)
        compose.onNodeWithTag("tab_LIBRARY").assertIsSelected()
    }
}
