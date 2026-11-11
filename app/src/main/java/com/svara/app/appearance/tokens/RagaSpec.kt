package com.svara.app.appearance.tokens

import com.svara.app.appearance.NavTab
import com.svara.app.appearance.tokens.ThemeTokens.BOLD
import com.svara.app.appearance.tokens.ThemeTokens.MEDIUM
import com.svara.app.appearance.tokens.ThemeTokens.REGULAR
import com.svara.app.appearance.tokens.ThemeTokens.SEMIBOLD
import com.svara.app.appearance.tokens.ThemeTokens.argb
import com.svara.app.appearance.tokens.ThemeTokens.body
import com.svara.app.appearance.tokens.ThemeTokens.heading
import com.svara.app.appearance.tokens.ThemeTokens.small

/** RĀGA. Values and their sources: docs/design-source-map.md, RĀGA section. */
object RagaSpec {
    val palette = PalettePair(
        dark = PaletteTokens(
            background = argb(0xFF121212),
            surface = argb(0xFF1E1E1E),
            surfaceHighlight = argb(0xFF2A2A2A),
            primaryText = argb(0xFFFFFFFF),
            secondaryText = argb(0xFF9E9E9E),
            accent = argb(0xFFE52D27),
            onAccent = argb(0xFFFFFFFF),
            scrim = argb(0x99000000),
            divider = argb(0xFF2A2A2A),
            accentText = argb(0xFFEA524D),
            accentFill = argb(0xFFE02C26),
        ),
        light = PaletteTokens(
            background = argb(0xFFFFFFFF),
            surface = argb(0xFFF5F5F5),
            surfaceHighlight = argb(0xFFECECEC),
            primaryText = argb(0xFF000000),
            secondaryText = argb(0xFF6B6B6B),
            accent = argb(0xFFE52D27),
            onAccent = argb(0xFFFFFFFF),
            scrim = argb(0x99000000),
            divider = argb(0xFFE0E0E0),
            accentText = argb(0xFFD52A24),
            accentFill = argb(0xFFE02C26),
        ),
        tiles = listOf(argb(0xFF8E2D4A), argb(0xFF9A5A12), argb(0xFF2F6B4F), argb(0xFF3D4D9A), argb(0xFF7A3A8A), argb(0xFF16687A)),
    )

    val typeScale = TypeScaleTokens(
        display = heading(20f, BOLD),
        headline = heading(20f, BOLD),
        title = heading(17f, SEMIBOLD),
        body = body(14f, REGULAR),
        bodyEmphasis = body(14f, MEDIUM),
        subhead = small(12f, REGULAR),
        caption = small(12f, REGULAR),
        micro = small(10f, REGULAR),
    )

    val tokens = PresetTokens(
        palette = palette,
        typeScale = typeScale,
        shape = ShapeTokens(
            artworkRadius = 8f,
            cardRadius = 8f,
            sheetTopRadius = 16f,
            miniPlayerRadius = 0f,
            heroRadius = 12f,
            lyricsCardRadius = 12f,
            searchFieldRadius = 8f,
        ),
        spacing = SpacingTokens(screenMargin = 16f, rowHeight = 64f, gridGutter = 12f, sectionGap = 24f),
        motion = MotionTokens(
            pressScale = 0.95f,
            pressDurationMs = 100,
            transitionMs = 200,
            springStiffness = ThemeTokens.STIFFNESS_MEDIUM,
            springDamping = ThemeTokens.DAMPING_NO_BOUNCE,
            enableArtworkMotion = false,
            enableBlur = false,
            enableAmbientBackground = true,
            heartPopScale = 1.25f,
            heartPopMs = 200,
            breathingMinAlpha = 0.55f,
            breathingMaxAlpha = 0.65f,
            breathingPeriodMs = 6_000,
            breathingMaxFps = 10,
        ),
        navigation = NavigationTokens(
            visibleTabs = listOf(NavTab.HOME, NavTab.SEARCH, NavTab.LIBRARY),
            tabStyle = TabStyle.OUTLINED_TO_FILLED,
            activeTint = TabTint.ACCENT,
            labels = mapOf(NavTab.LIBRARY to "My Music"),
        ),
        sizes = SizeTokens(
            miniArtwork = 40f,
            miniPlayerHeight = 56f,
            carouselArtwork = 128f,
            rowArtwork = 48f,
            artistArtwork = 128f,
            playButton = 72f,
            playIcon = 40f,
            transportIcon = 32f,
            scrubberTrack = 3f,
            scrubberHandle = 8f,
            handleOnlyWhileDragging = false,
            playerArtworkFraction = 0.45f,
            otherLineAlpha = 0.5f,
            playerScrimAlpha = 0.6f,
        ),
        signature = SignatureTokens(auraAlpha = 0.34f, auraSpreadDp = 40f, cardLiftDp = 2f, cinemaBackdrop = CinemaBackdrop.COLOUR_FIELD),
    )
}
