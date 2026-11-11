package com.svara.app.ui

import com.svara.app.nowplaying.VisualiserFullScreen
import com.svara.app.nowplaying.VisualiserScreen
import com.svara.app.nowplaying.EqualizerScreen
import com.svara.app.library.StatsScreen
import com.svara.app.library.HistoryScreen
import com.svara.app.playlists.RecentlyDeletedScreen
import com.svara.app.playlists.RuleBuilderScreen
import com.svara.app.nowplaying.LyricsStudioScreen
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import com.svara.app.ui.artwork.LocalSharedTransition
import com.svara.app.appearance.MotionGroups
import com.svara.app.ui.motion.LocalMotionGroups
import com.svara.app.ui.motion.MotionGovernorEffect
import com.svara.app.ui.motion.governedLevel
import com.svara.app.ui.motion.rememberThemeTransitions
import com.svara.app.ui.artwork.ProvideArtworkVisibility

import com.svara.app.settings.CoverEditorScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.svara.app.ui.components.ScrollDirectionTracker
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.svara.app.library.CollectionScreen
import com.svara.app.library.LibraryCheckScreen
import com.svara.app.library.LibraryScreen
import com.svara.app.library.SearchScreen
import com.svara.app.nowplaying.MiniPlayer
import com.svara.app.nowplaying.NowPlayingScreen
import com.svara.app.nowplaying.QueueScreen
import com.svara.app.playlists.PlaylistDetailScreen
import com.svara.app.playlists.PlaylistsScreen
import com.svara.app.playlists.SmartPlaylistScreen
import com.svara.app.settings.AboutScreen
import com.svara.app.settings.FoldersScreen
import com.svara.app.settings.PrivacyScreen
import com.svara.app.settings.SettingsScreen
import com.svara.app.settings.StorageScreen
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.navigation.Routes
import com.svara.app.ui.navigation.libraryTab
import com.svara.app.appearance.TabVisibility
import com.svara.app.ui.presets.PresetHomeScreen
import com.svara.app.ui.presets.PresetLibraryScreen
import com.svara.app.ui.presets.PresetSearchScreen
import com.svara.app.ui.presets.common.PresetNavBar
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.svara.app.ui.navigation.route
import com.svara.app.appearance.NavStyle
import com.svara.app.appearance.NavTab
import com.svara.app.appearance.StudioSection
import com.svara.app.domain.model.LibraryTab
import com.svara.app.settings.AppearanceScreen
import com.svara.app.settings.SavedLooksScreen
import com.svara.app.settings.StudioScreen
import com.svara.app.settings.ThemeStudioScreen
import com.svara.app.ui.theme.LocalAppearance
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person

private fun NavTab.icon(): ImageVector = when (this) {
    NavTab.HOME -> Icons.Rounded.Home
    NavTab.SONGS -> Icons.Rounded.MusicNote
    NavTab.ALBUMS -> Icons.Rounded.Album
    NavTab.ARTISTS -> Icons.Rounded.Person
    NavTab.FOLDERS -> Icons.Rounded.Folder
    NavTab.PLAYLISTS -> Icons.AutoMirrored.Rounded.QueueMusic
    NavTab.SETTINGS -> Icons.Rounded.Settings
    NavTab.SEARCH -> Icons.Rounded.Search
    NavTab.LIBRARY -> Icons.Rounded.LibraryMusic
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SvaraAppScaffold(mainViewModel: MainViewModel) {
    val nav = rememberNavController()
    val navigator = remember(nav) { Navigator(nav) }
    val backStackEntry by nav.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showChrome = destination?.route !in Routes.fullScreen
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by mainViewModel.settings.collectAsStateWithLifecycle()
    val unlocked by mainViewModel.unlocked.collectAsStateWithLifecycle()
    val appearance = LocalAppearance.current
    val motion = appearance.motion
    val tabs = appearance.visibleTabs
    val layoutPreset = appearance.layoutPreset

    // Motion groups: the policy's ceiling, stepped down by the frame governor when frames drop.
    val ceiling = MotionGroups.ceiling(appearance.limits, motion)
    MotionGovernorEffect(ceiling, enabled = appearance.custom.smartSmoothing)
    val governed = governedLevel()
    val groups = remember(appearance.limits, motion, appearance.custom, governed) {
        MotionGroups.resolve(appearance.limits, motion, appearance.custom, governed)
    }
    val tabRoutes = remember(appearance.tabUniverse) { appearance.tabUniverse.map { it.route() }.toSet() }
    val transitions = rememberThemeTransitions(appearance.preset, groups, tabRoutes)

    // The tab the user is on: the bar route they're at, or the last one while deeper in a stack.
    var lastTab by rememberSaveable { mutableStateOf<NavTab?>(null) }
    val routeTab = appearance.tabUniverse.firstOrNull { tab ->
        destination?.hierarchy?.any { it.route == tab.route() } == true &&
            (tab.libraryTab() == null || tab.libraryTab()?.name == (backStackEntry?.arguments?.getString("tab") ?: LibraryTab.SONGS.name))
    }
    LaunchedEffect(routeTab) {
        if (routeTab != null && routeTab != lastTab) {
            lastTab = routeTab
            mainViewModel.rememberTab(routeTab)
        }
    }

    suspend fun awaitGraph() {
        if (nav.currentBackStackEntry == null) snapshotFlow { backStackEntry }.filterNotNull().first()
    }

    // Start on the saved tab when it still exists; Navigation Studio may also have hidden Home.
    // Once per launch: after rotation, a font or locale change or process recreation the restored
    // back stack already holds the right screen, and navigating again would drop its state.
    var startApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (startApplied) return@LaunchedEffect
        startApplied = true
        val saved = mainViewModel.startTab()
        // When settings are already in memory this runs before NavHost has set its graph, and
        // navigating then crashes: wait for the first screen.
        awaitGraph()
        val start = if (saved != null && saved in tabs) saved else tabs.first()
        if (start != NavTab.HOME) {
            val library = start.libraryTab()
            nav.navigate(if (library != null) "${Routes.LIBRARY_BASE}?tab=${library.name}" else start.route()) {
                popUpTo(Routes.HOME) { inclusive = NavTab.HOME !in tabs }
            }
        }
    }

    // Widgets, the quick settings tile and app shortcuts can ask for a screen.
    val launchRoute by mainViewModel.launchRoute.collectAsStateWithLifecycle()
    LaunchedEffect(launchRoute) {
        val route = launchRoute ?: return@LaunchedEffect
        awaitGraph()
        // Directly on the controller: at launch the first screen may not be resumed yet, and the
        // Navigator ignores taps until it is.
        nav.navigate(route) { if (route == Routes.NOW_PLAYING || route == Routes.QUEUE) launchSingleTop = true }
        mainViewModel.consumeLaunchRoute()
    }

    // A preset change keeps the current tab when the new preset has it; otherwise its first tab.
    LaunchedEffect(tabs) {
        val current = lastTab ?: return@LaunchedEffect
        if (current !in tabs) navigator.open(TabVisibility.keep(current, tabs))
    }

    // Battery saver or heat forced Efficiency: say so once, in one line.
    val notice = appearance.limits.notice
    LaunchedEffect(notice) {
        if (notice != null) snackbarHostState.showSnackbar(notice)
    }

    // Any list on any screen reports its scroll direction here; a new screen shows the button again.
    val fabScroll = remember { ScrollDirectionTracker() }
    LaunchedEffect(destination?.route) { fabScroll.visible = true }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        // Only while visible: nothing collects when the UI is hidden.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            mainViewModel.messages.collect { msg ->
            val result = snackbarHostState.showSnackbar(
                message = msg.text,
                actionLabel = msg.actionLabel,
                duration = if (msg.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) msg.action?.invoke()
            }
        }
    }

    /** Wraps library and history screens in the optional app lock. */
    fun NavGraphBuilder.screen(
        route: String,
        arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
        content: @Composable (NavBackStackEntry) -> Unit,
    ) = composable(route, arguments) { entry ->
        ProvideArtworkVisibility(this) {
            LockGate(locked = settings.appLock && !unlocked && route in Routes.locked, onUnlocked = mainViewModel::onUnlocked) { content(entry) }
        }
    }

    // Artwork moves between the mini-player or an album card and the screen it opens.
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransition provides this, LocalMotionGroups provides groups) {
            Scaffold(
                // Exposes test tags as resource ids for UiAutomator (benchmarks).
                modifier = Modifier.semantics { testTagsAsResourceId = true },
                contentWindowInsets = WindowInsets(0),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                // One-handed search: a button within thumb reach on the main screens.
                // It shrinks away while a list scrolls down (so it never sits on titles or menus)
                // and comes back on the first scroll up.
                floatingActionButton = {
                    if (settings.oneHandedSearch && showChrome && routeTab != null && routeTab != NavTab.SEARCH) {
                        AnimatedVisibility(
                            visible = fabScroll.visible,
                            enter = when {
                                !motion.enabled -> EnterTransition.None
                                motion.fadesOnly -> fadeIn(tween(motion.feedback))
                                else -> scaleIn(tween(motion.feedback)) + fadeIn(tween(motion.feedback))
                            },
                            exit = when {
                                !motion.enabled -> ExitTransition.None
                                motion.fadesOnly -> fadeOut(tween(motion.feedback))
                                else -> scaleOut(tween(motion.feedback)) + fadeOut(tween(motion.feedback))
                            },
                        ) {
                            androidx.compose.material3.FloatingActionButton(onClick = navigator::search) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search")
                            }
                        }
                    }
                },
                floatingActionButtonPosition = if (settings.leftHanded) androidx.compose.material3.FabPosition.Start else androidx.compose.material3.FabPosition.End,
                bottomBar = {
                    AnimatedVisibility(
                        visible = showChrome,
                        enter = when {
                            !motion.enabled -> EnterTransition.None
                            motion.fadesOnly -> fadeIn(tween(motion.feedback))
                            else -> slideInVertically(tween(motion.feedback)) { it } + fadeIn(tween(motion.feedback))
                        },
                        exit = when {
                            !motion.enabled -> ExitTransition.None
                            motion.fadesOnly -> fadeOut(tween(motion.feedback))
                            else -> slideOutVertically(tween(motion.feedback)) { it } + fadeOut(tween(motion.feedback))
                        },
                    ) {
                        ProvideArtworkVisibility(this) {
                            if (layoutPreset) Column {
                                MiniPlayer(onOpen = navigator::nowPlaying, navigator = navigator)
                                PresetNavBar(
                                    tabs = tabs,
                                    selected = routeTab ?: lastTab,
                                    onSelect = { tab ->
                                        // Reselecting the current tab returns to its first screen.
                                        if (tab == (routeTab ?: lastTab) && tab.libraryTab() == null) nav.popBackStack(tab.route(), inclusive = false)
                                        else navigator.open(tab)
                                    },
                                )
                            } else Column {
                                MiniPlayer(onOpen = navigator::nowPlaying, navigator = navigator)
                                if (tabs.size > 1 || tabs.firstOrNull() != null) NavigationBar {
                                    val currentTab = backStackEntry?.arguments?.getString("tab") ?: LibraryTab.SONGS.name
                                    tabs.forEach { tab ->
                                        val inRoute = destination?.hierarchy?.any { it.route == tab.route() } == true
                                        val library = tab.libraryTab()
                                        val selected = inRoute && (library == null || library.name == currentTab)
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { navigator.open(tab) },
                                            icon = { Icon(tab.icon(), contentDescription = null) },
                                            // One line: at large font sizes a label must not break mid-word.
                                            label = { Text(tab.label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                                            alwaysShowLabel = appearance.theme.spec.navStyle != NavStyle.SELECTED_LABEL_ONLY,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
                CompositionLocalProvider(com.svara.app.ui.components.LocalScrollTracker provides fabScroll) {
                NavHost(
                    navController = nav,
                    startDestination = Routes.HOME,
                    modifier = Modifier
                        .padding(bottom)
                        .consumeWindowInsets(bottom)
                        .nestedScroll(fabScroll),
                    enterTransition = { transitions.enter(this, pop = false) },
                    exitTransition = { transitions.exit(this, pop = false) },
                    popEnterTransition = { transitions.enter(this, pop = true) },
                    popExitTransition = { transitions.exit(this, pop = true) },
                ) {
                    composable(Routes.HOME) { ProvideArtworkVisibility(this) { PresetHomeScreen(navigator, hideHistory = settings.hideHistory) } }
                    screen(Routes.RECAP) { com.svara.app.recap.RecapScreen(navigator) }
                    composable(Routes.RECAP_STORY, listOf(navArgument("period") { type = NavType.StringType })) { com.svara.app.recap.RecapStoryScreen(navigator) }
                    screen(Routes.LIBRARY, listOf(navArgument("tab") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                        LibraryScreen(navigator)
                    }
                    screen(Routes.PLAYLISTS) { PlaylistsScreen(navigator, hideHistory = settings.hideHistory) }
                    composable(Routes.SETTINGS) { SettingsScreen(navigator) }
                    screen(Routes.SEARCH) { if (layoutPreset) PresetSearchScreen(navigator) else SearchScreen(navigator) }
                    screen(Routes.MY_LIBRARY) { PresetLibraryScreen(navigator) }
                    screen(
                        Routes.COLLECTION,
                        listOf(navArgument("type") { type = NavType.StringType }, navArgument("id") { type = NavType.StringType }),
                    ) { CollectionScreen(navigator) }
                    screen(Routes.PLAYLIST, listOf(navArgument("id") { type = NavType.LongType })) { PlaylistDetailScreen(navigator) }
                    screen(Routes.SMART_PLAYLIST, listOf(navArgument("id") { type = NavType.LongType })) { SmartPlaylistScreen(navigator) }
                    screen(Routes.LIBRARY_CHECK) { LibraryCheckScreen(navigator) }
                    composable(
                        Routes.NOW_PLAYING,
                        enterTransition = {
                            when {
                                !motion.enabled -> EnterTransition.None
                                motion.fadesOnly -> fadeIn(tween(motion.screen))
                                else -> slideInVertically(tween(motion.screen)) { it }
                            }
                        },
                        exitTransition = { if (!motion.enabled) ExitTransition.None else fadeOut(tween(motion.feedback)) },
                        popEnterTransition = { if (!motion.enabled) EnterTransition.None else fadeIn(tween(motion.feedback)) },
                        popExitTransition = {
                            when {
                                !motion.enabled -> ExitTransition.None
                                motion.fadesOnly -> fadeOut(tween(motion.screen))
                                else -> slideOutVertically(tween(motion.screen)) { it }
                            }
                        },
                    ) { ProvideArtworkVisibility(this) { NowPlayingScreen(navigator) } }
                    composable(Routes.QUEUE) { QueueScreen(navigator) }
            composable(Routes.LYRICS, listOf(navArgument("songId") { type = NavType.LongType })) { LyricsStudioScreen(navigator) }
            screen(Routes.RULE_BUILDER, listOf(navArgument("id") { type = NavType.LongType })) { RuleBuilderScreen(navigator) }
            screen(Routes.RECENTLY_DELETED) { RecentlyDeletedScreen(navigator) }
            screen(Routes.HISTORY) { HistoryScreen(navigator) }
            screen(Routes.STATS) { StatsScreen(navigator) }
            composable(Routes.EQUALIZER) { EqualizerScreen(navigator) }
            composable(Routes.VISUALISER) { VisualiserScreen(navigator) }
            composable(Routes.VISUALISER_FULL) { VisualiserFullScreen(navigator) }
                    composable(
                        Routes.COVER,
                        listOf(navArgument("songId") { type = NavType.LongType }, navArgument("albumId") { type = NavType.LongType }),
                    ) { CoverEditorScreen(navigator) }
                    composable(Routes.FOLDERS) { FoldersScreen(navigator) }
                    composable(Routes.STORAGE) { StorageScreen(navigator) }
                    composable(Routes.PRIVACY) { PrivacyScreen(navigator) }
                    composable(Routes.ABOUT) { AboutScreen(navigator) }
                    composable(Routes.APPEARANCE) { AppearanceScreen(navigator) }
                    composable(Routes.THEME_STUDIO) { ThemeStudioScreen(navigator) }
                    composable(Routes.SAVED_LOOKS) { SavedLooksScreen(navigator) }
                    composable(Routes.STUDIO, listOf(navArgument("section") { type = NavType.StringType })) { entry ->
                        StudioScreen(navigator, StudioSection.entries.firstOrNull { it.name == entry.arguments?.getString("section") } ?: StudioSection.NAVIGATION)
                    }
                }
                }
            }
        }
    }
}
