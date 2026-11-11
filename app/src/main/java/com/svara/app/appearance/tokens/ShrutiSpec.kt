package com.svara.app.appearance.tokens

import com.svara.app.appearance.NavTab
import com.svara.app.appearance.tokens.ThemeTokens.BOLD
import com.svara.app.appearance.tokens.ThemeTokens.MEDIUM
import com.svara.app.appearance.tokens.ThemeTokens.REGULAR
import com.svara.app.appearance.tokens.ThemeTokens.argb
import com.svara.app.appearance.tokens.ThemeTokens.body
import com.svara.app.appearance.tokens.ThemeTokens.heading
import com.svara.app.appearance.tokens.ThemeTokens.small

/** SHRUTI. Values and their sources: docs/design-source-map.md, SHRUTI section. */
object ShrutiSpec {
    /** Static radial glow behind the home header, from the latest artwork's colour. */
    const val HOME_GLOW_ALPHA = 0.6f
    /** Now Playing gradient: artwork colour at the top, fading to black at the bottom. */
    const val PLAYER_GRADIENT_ALPHA = 0.9f
    /** Browse-all cards: cover rotated about 25 degrees (S.screens_and_views.search_screen.browse_all_grid). */
    const val BROWSE_TILT_DEGREES = 25f

    val palette = PalettePair(
        dark = PaletteTokens(
            background = argb(0xFF121212),
            surface = argb(0xFF181818),
            surfaceHighlight = argb(0xFF282828),
            primaryText = argb(0xFFFFFFFF),
            secondaryText = argb(0xFFB3B3B3),
            accent = argb(0xFF1DB954),
            onAccent = argb(0xFF000000),
            scrim = argb(0x99000000),
            divider = argb(0xFF282828),
        ),
        light = PaletteTokens(
            background = argb(0xFFFFFFFF),
            surface = argb(0xFFF5F5F5),
            surfaceHighlight = argb(0xFFEDEDED),
            primaryText = argb(0xFF000000),
            secondaryText = argb(0xFF6A6A6A),
            accent = argb(0xFF19A049),
            onAccent = argb(0xFF000000),
            scrim = argb(0x66000000),
            divider = argb(0xFFE0E0E0),
            accentText = argb(0xFF14803A),
        ),
        tiles = listOf(argb(0xFF282828), argb(0xFF333333), argb(0xFF3E3E3E), argb(0xFF2A2A2A)),
    )

    val typeScale = TypeScaleTokens(
        display = heading(24f, BOLD),
        headline = heading(22f, BOLD),
        title = heading(18f, BOLD),
        body = body(14f, REGULAR),
        bodyEmphasis = body(14f, MEDIUM),
        subhead = small(12f, REGULAR),
        caption = small(11f, REGULAR),
        micro = small(10f, REGULAR, letterSpacingEm = 0.08f),
    )

    val tokens = PresetTokens(
        palette = palette,
        typeScale = typeScale,
        shape = ShapeTokens(
            artworkRadius = 8f,
            cardRadius = 4f,
            sheetTopRadius = 20f,
            miniPlayerRadius = 8f,
            lyricsCardRadius = 12f,
            searchFieldRadius = 8f,
        ),
        spacing = SpacingTokens(screenMargin = 16f, rowHeight = 64f, gridGutter = 8f, sectionGap = 24f),
        motion = MotionTokens(
            pressScale = 0.95f,
            pressDurationMs = 100,
            transitionMs = 200,
            springStiffness = ThemeTokens.STIFFNESS_MEDIUM,
            springDamping = ThemeTokens.DAMPING_NO_BOUNCE,
            enableArtworkMotion = false,
            enableBlur = false,
            enableAmbientBackground = false,
            playPressScale = 0.92f,
            playPressMs = 80,
            heartPopScale = ThemeTokens.HEART_POP_SCALE,
            heartPopMs = 200,
        ),
        navigation = NavigationTokens(
            visibleTabs = listOf(NavTab.HOME, NavTab.SEARCH, NavTab.LIBRARY),
            tabStyle = TabStyle.OUTLINED_TO_FILLED,
            activeTint = TabTint.PRIMARY_TEXT,
            labels = mapOf(NavTab.LIBRARY to "Your Library"),
        ),
        sizes = SizeTokens(
            miniArtwork = 48f,
            miniPlayerHeight = 64f,
            carouselArtwork = 150f,
            quickTileArtwork = 48f,
            rowArtwork = 48f,
            artistArtwork = 150f,
            playButton = 64f,
            playIcon = 32f,
            transportIcon = 32f,
            scrubberTrack = 4f,
            scrubberHandle = 12f,
            handleOnlyWhileDragging = true,
            artworkElevation = 8f,
            playerArtworkFraction = 0.5f,
            pastLineAlpha = 0.5f,
        ),
        signature = SignatureTokens(auraAlpha = 0.20f, auraSpreadDp = 32f, cardLiftDp = 0f, cinemaBackdrop = CinemaBackdrop.GRADIENT),
    )
}
