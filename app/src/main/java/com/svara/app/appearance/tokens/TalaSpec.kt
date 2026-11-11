package com.svara.app.appearance.tokens

import com.svara.app.appearance.NavTab
import com.svara.app.appearance.tokens.ThemeTokens.BOLD
import com.svara.app.appearance.tokens.ThemeTokens.REGULAR
import com.svara.app.appearance.tokens.ThemeTokens.SEMIBOLD
import com.svara.app.appearance.tokens.ThemeTokens.argb
import com.svara.app.appearance.tokens.ThemeTokens.body
import com.svara.app.appearance.tokens.ThemeTokens.heading
import com.svara.app.appearance.tokens.ThemeTokens.small

/** TĀLA. Values and their sources: docs/design-source-map.md, TĀLA section. */
object TalaSpec {
    /** Large title collapses to the inline title after this much scroll, dp. */
    const val LARGE_TITLE_COLLAPSE_DP = 24f

    val palette = PalettePair(
        dark = PaletteTokens(
            background = argb(0xFF000000),
            surface = argb(0xFF1C1C1E),
            surfaceHighlight = argb(0xFF242426),
            primaryText = argb(0xFFFFFFFF),
            secondaryText = argb(0xFF8E8E93),
            accent = argb(0xFFFA243C),
            onAccent = argb(0xFFFFFFFF),
            scrim = argb(0x99000000),
            divider = argb(0xFF38383A),
            accentText = argb(0xFFFA364C),
            accentFill = argb(0xFFE22136),
        ),
        light = PaletteTokens(
            background = argb(0xFFFFFFFF),
            surface = argb(0xFFF2F2F7),
            surfaceHighlight = argb(0xFFE5E5EA),
            primaryText = argb(0xFF000000),
            // #3C3C43 at 76 % (the brief's 60 % fails AA; see the source map, C3).
            secondaryText = argb(0xC23C3C43),
            accent = argb(0xFFFA243C),
            onAccent = argb(0xFFFFFFFF),
            scrim = argb(0x99000000),
            divider = argb(0xFFC6C6C8),
            accentText = argb(0xFFD41F33),
            accentFill = argb(0xFFE22136),
        ),
        tiles = listOf(argb(0xFF2C2C2E), argb(0xFF3A3A3C), argb(0xFF48484A), argb(0xFF1C1C1E)),
    )

    val typeScale = TypeScaleTokens(
        display = heading(34f, BOLD),
        headline = heading(28f, BOLD),
        title = heading(22f, SEMIBOLD),
        body = body(17f, REGULAR),
        bodyEmphasis = body(17f, SEMIBOLD),
        subhead = body(15f, REGULAR),
        caption = small(12f, REGULAR),
        micro = small(11f, REGULAR),
    )

    val tokens = PresetTokens(
        palette = palette,
        typeScale = typeScale,
        shape = ShapeTokens(
            artworkRadius = 8f,
            cardRadius = 12f,
            sheetTopRadius = 28f,
            miniPlayerRadius = 12f,
            heroRadius = 12f,
            lyricsCardRadius = 12f,
            searchFieldRadius = 12f,
        ),
        spacing = SpacingTokens(screenMargin = 20f, rowHeight = 56f, gridGutter = 16f, sectionGap = 32f),
        motion = MotionTokens(
            pressScale = 0.95f,
            pressDurationMs = 100,
            transitionMs = 200,
            springStiffness = ThemeTokens.STIFFNESS_MEDIUM,
            springDamping = 0.8f,
            enableArtworkMotion = true,
            enableBlur = true,
            enableAmbientBackground = false,
            tabCrossfadeMs = 150,
            pagerMs = 200,
            lyricsScrollMs = 250,
            pausedArtworkScale = 0.9f,
        ),
        navigation = NavigationTokens(
            visibleTabs = listOf(NavTab.HOME, NavTab.LIBRARY, NavTab.SEARCH),
            tabStyle = TabStyle.OUTLINED_TO_FILLED,
            activeTint = TabTint.ACCENT,
        ),
        sizes = SizeTokens(
            miniArtwork = 48f,
            miniPlayerHeight = 64f,
            carouselArtwork = 150f,
            rowArtwork = 48f,
            artistArtwork = 150f,
            playButton = 80f,
            playIcon = 56f,
            transportButton = 72f,
            transportIcon = 44f,
            scrubberTrack = 6f,
            scrubberTrackDragging = 10f,
            scrubberHandle = 0f,
            handleOnlyWhileDragging = true,
            artworkElevation = 16f,
            playerArtworkFraction = 0.5f,
            otherLineAlpha = 0.4f,
            pastLineAlpha = 0.4f,
            playerScrimAlpha = 0.6f,
            lyricsActiveSp = 28f,
            miniPlayerAlpha = 0.88f,
            liveBlurRadius = 24f,
            smallArtworkElevation = 4f,
        ),
        signature = SignatureTokens(auraAlpha = 0.18f, auraSpreadDp = 56f, cardLiftDp = 6f, cinemaBackdrop = CinemaBackdrop.BLURRED_COVER),
    )
}
