package com.svara.app.ui.navigation

import com.svara.app.ui.PerfTrace
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.svara.app.appearance.NavTab
import com.svara.app.appearance.StudioSection
import com.svara.app.domain.model.LibraryTab

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library?tab={tab}"
    const val LIBRARY_BASE = "library"
    const val PLAYLISTS = "playlists"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    /** The layout presets' library tab (Your Library, Library, My Music). */
    const val MY_LIBRARY = "mylibrary"
    const val COLLECTION = "collection/{type}/{id}"
    const val PLAYLIST = "playlist/{id}"
    const val SMART_PLAYLIST = "smart/{id}"
    const val NOW_PLAYING = "nowplaying"
    const val QUEUE = "queue"
    const val FOLDERS = "settings/folders"
    const val STORAGE = "settings/storage"
    const val PRIVACY = "settings/privacy"
    const val ABOUT = "settings/about"
    const val LIBRARY_CHECK = "library/check"
    const val APPEARANCE = "settings/appearance"
    const val THEME_STUDIO = "settings/appearance/customise"
    const val SAVED_LOOKS = "settings/appearance/looks"
    const val STUDIO = "settings/appearance/studio/{section}"
    const val COVER = "cover/{songId}/{albumId}"
    const val RULE_BUILDER = "smart/edit/{id}"
    const val RECENTLY_DELETED = "playlists/deleted"
    const val LYRICS = "lyrics/{songId}"
    const val HISTORY = "history"
    const val STATS = "stats"
    const val RECAP = "recap"
    const val RECAP_STORY = "recap/story/{period}"
    const val EQUALIZER = "audio/equalizer"
    const val VISUALISER = "visualiser"
    const val VISUALISER_FULL = "visualiser/full"

    /** Routes that take over the whole screen, hiding the mini-player and bottom bar. */
    val fullScreen = setOf(NOW_PLAYING, QUEUE, LYRICS, VISUALISER_FULL)

    /** Areas covered by the optional app lock. */
    val locked = setOf(LIBRARY, MY_LIBRARY, PLAYLISTS, SEARCH, COLLECTION, PLAYLIST, SMART_PLAYLIST, LIBRARY_CHECK, RULE_BUILDER, RECENTLY_DELETED, HISTORY, STATS)

    fun collection(type: CollectionType, id: String = "_") = "collection/${type.name}/${Uri.encode(id)}"
    fun playlist(id: Long) = "playlist/$id"
}

enum class CollectionType { ALBUM, ARTIST, FOLDER, GENRE, FAVORITES, RECENTLY_PLAYED, MOST_PLAYED, RECENTLY_ADDED }

/** Navigation entry points handed to screens, so screens don't depend on NavController. */
@Immutable
class Navigator(private val nav: NavController) {
    /**
     * Only the screen that is fully on (resumed) may navigate: a second tap during a transition,
     * or on a screen already on its way out, is ignored, so nothing opens twice and back never
     * pops two screens. Bottom-bar tabs are exempt; they are single-top and restore state.
     */
    private fun settled(): Boolean =
        nav.currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true

    private fun go(route: String, options: NavOptionsBuilder.() -> Unit = {}) {
        if (settled()) nav.navigate(route, options)
    }

    fun back() {
        // Never pops the last screen, which would leave an empty host.
        if (settled() && nav.previousBackStackEntry != null) nav.popBackStack()
    }

    fun topLevel(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun library(tab: LibraryTab) = nav.navigate("${Routes.LIBRARY_BASE}?tab=${tab.name}") {
        popUpTo(nav.graph.findStartDestination().id) { saveState = false }
        launchSingleTop = true
    }

    /** Pushes a library list on top of the current screen (SOLACE's Library rows). */
    fun libraryList(tab: LibraryTab) = go("${Routes.LIBRARY_BASE}?tab=${tab.name}")
    fun playlists() = go(Routes.PLAYLISTS)

    fun album(albumId: Long) = go(Routes.collection(CollectionType.ALBUM, albumId.toString()))
    fun artist(name: String) = go(Routes.collection(CollectionType.ARTIST, name))
    fun folder(folderId: String) = go(Routes.collection(CollectionType.FOLDER, folderId))
    fun genre(name: String) = go(Routes.collection(CollectionType.GENRE, name))
    fun collection(type: CollectionType) = go(Routes.collection(type))
    fun playlist(id: Long) = go(Routes.playlist(id))
    fun smartPlaylist(id: Long) = go("smart/$id")
    fun search() = go(Routes.SEARCH) { launchSingleTop = true }
    fun nowPlaying() {
        PerfTrace.begin(PerfTrace.NOW_PLAYING_OPEN)
        go(Routes.NOW_PLAYING) { launchSingleTop = true }
    }
    fun queue() = go(Routes.QUEUE) { launchSingleTop = true }
    fun folders() = go(Routes.FOLDERS)
    fun storage() = go(Routes.STORAGE)
    fun privacy() = go(Routes.PRIVACY)
    fun about() = go(Routes.ABOUT)
    fun settings() = go(Routes.SETTINGS) { launchSingleTop = true }
    fun appearance() = go(Routes.APPEARANCE)
    fun themeStudio() = go(Routes.THEME_STUDIO)
    fun savedLooks() = go(Routes.SAVED_LOOKS)
    fun studio(section: StudioSection) = go("settings/appearance/studio/${section.name}")
    fun libraryCheck() = go(Routes.LIBRARY_CHECK)
    /** 0 starts a new rule playlist. */
    fun ruleBuilder(id: Long) = go("smart/edit/$id")
    fun recentlyDeleted() = go(Routes.RECENTLY_DELETED)
    fun lyrics(songId: Long) = go("lyrics/$songId") { launchSingleTop = true }
    fun history() = go(Routes.HISTORY)
    fun stats() = go(Routes.STATS)
    fun recap() = go(Routes.RECAP)

    private val storyGuard = com.svara.app.appearance.RepeatGuard()
    /** Opens a recap's story; a second tap on the same recap while it opens is ignored. */
    fun recapStory(periodKey: String) {
        if (storyGuard.accept(periodKey, android.os.SystemClock.uptimeMillis())) go("recap/story/" + android.net.Uri.encode(periodKey))
    }
    fun equalizer() = go(Routes.EQUALIZER) { launchSingleTop = true }
    fun visualiser() = go(Routes.VISUALISER) { launchSingleTop = true }
    fun visualiserFull() = go(Routes.VISUALISER_FULL) { launchSingleTop = true }

    /** Change cover for a song (and, optionally, its whole album). */
    fun changeCover(songId: Long, albumId: Long) = go("cover/$songId/$albumId")

    /** Opens a bottom-bar destination the way the bar does (single top, state restored). */
    fun open(tab: NavTab) {
        val library = tab.libraryTab()
        when {
            library != null -> library(library)
            // Home is also the start destination: restoring its saved state would bring back
            // the screens stacked above it, so pop to it instead.
            tab == NavTab.HOME -> if (!nav.popBackStack(Routes.HOME, inclusive = false)) topLevel(Routes.HOME)
            else -> topLevel(tab.route())
        }
    }
}

fun NavTab.libraryTab(): LibraryTab? = when (this) {
    NavTab.SONGS -> LibraryTab.SONGS
    NavTab.ALBUMS -> LibraryTab.ALBUMS
    NavTab.ARTISTS -> LibraryTab.ARTISTS
    NavTab.FOLDERS -> LibraryTab.FOLDERS
    else -> null
}

/** The route a tab lives at; library tabs share [Routes.LIBRARY] and differ by argument. */
fun NavTab.route(): String = when (this) {
    NavTab.HOME -> Routes.HOME
    NavTab.PLAYLISTS -> Routes.PLAYLISTS
    NavTab.SETTINGS -> Routes.SETTINGS
    NavTab.SEARCH -> Routes.SEARCH
    NavTab.LIBRARY -> Routes.MY_LIBRARY
    NavTab.SONGS, NavTab.ALBUMS, NavTab.ARTISTS, NavTab.FOLDERS -> Routes.LIBRARY
}
