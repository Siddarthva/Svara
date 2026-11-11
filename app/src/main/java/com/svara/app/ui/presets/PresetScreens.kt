package com.svara.app.ui.presets

import androidx.compose.runtime.Composable
import com.svara.app.appearance.PresetId
import com.svara.app.library.LibraryScreen
import com.svara.app.library.SearchScreen
import com.svara.app.ui.home.HomeScreen
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.presets.raga.RagaHome
import com.svara.app.ui.presets.raga.RagaMyMusic
import com.svara.app.ui.presets.raga.RagaSearch
import com.svara.app.ui.presets.shruti.ShrutiHome
import com.svara.app.ui.presets.shruti.ShrutiLibrary
import com.svara.app.ui.presets.shruti.ShrutiSearch
import com.svara.app.ui.presets.spanda.SpandaFind
import com.svara.app.ui.presets.spanda.SpandaHome
import com.svara.app.ui.presets.spanda.SpandaLibrary
import com.svara.app.ui.presets.tala.TalaHome
import com.svara.app.ui.presets.tala.TalaLibrary
import com.svara.app.ui.presets.tala.TalaSearch
import com.svara.app.ui.theme.LocalAppearance

/*
 * Each preset's own screens for the bottom-bar destinations. ETHEREAL keeps SVARĀ's screens.
 * Switching preset swaps these composables only; ViewModels, the player and the queue stay.
 */

@Composable
fun PresetHomeScreen(navigator: Navigator, hideHistory: Boolean) = when (LocalAppearance.current.preset) {
    PresetId.ANAHATA -> HomeScreen(navigator, hideHistory)
    PresetId.SHRUTI -> ShrutiHome(navigator, hideHistory)
    PresetId.TALA -> TalaHome(navigator, hideHistory)
    PresetId.RAGA -> RagaHome(navigator, hideHistory)
    PresetId.SPANDA -> SpandaHome(navigator, hideHistory)
}

@Composable
fun PresetLibraryScreen(navigator: Navigator) = when (LocalAppearance.current.preset) {
    PresetId.ANAHATA -> LibraryScreen(navigator)
    PresetId.SHRUTI -> ShrutiLibrary(navigator)
    PresetId.TALA -> TalaLibrary(navigator)
    PresetId.RAGA -> RagaMyMusic(navigator)
    PresetId.SPANDA -> SpandaLibrary(navigator)
}

@Composable
fun PresetSearchScreen(navigator: Navigator) = when (LocalAppearance.current.preset) {
    PresetId.ANAHATA -> SearchScreen(navigator)
    PresetId.SHRUTI -> ShrutiSearch(navigator)
    PresetId.TALA -> TalaSearch(navigator)
    PresetId.RAGA -> RagaSearch(navigator)
    PresetId.SPANDA -> SpandaFind(navigator)
}
