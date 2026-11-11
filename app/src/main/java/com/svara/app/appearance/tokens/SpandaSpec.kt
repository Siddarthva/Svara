package com.svara.app.appearance.tokens

import com.svara.app.appearance.NavTab
import com.svara.app.appearance.tokens.ThemeTokens.BOLD
import com.svara.app.appearance.tokens.ThemeTokens.MEDIUM
import com.svara.app.appearance.tokens.ThemeTokens.REGULAR
import com.svara.app.appearance.tokens.ThemeTokens.argb
import com.svara.app.appearance.tokens.ThemeTokens.body
import com.svara.app.appearance.tokens.ThemeTokens.heading
import com.svara.app.appearance.tokens.ThemeTokens.small

/** SPANDA. Values and their sources: docs/design-source-map.md, SPANDA section. */
object SpandaSpec {
    /** Audio-reactive glow frame cap (Reactive); spectrum bars use the policy's cap (Full Spectrum). */
    const val GLOW_MAX_FPS = 20
    const val SPECTRUM_MAX_FPS = 30
    const val SPECTRUM_BARS = 24
    const val GLOW_ALPHA = 0.35f
    /** How far bass energy grows the glow behind the artwork. */
    const val GLOW_BASS_GAIN = 0.25f

    val palette = PalettePair(
        dark = PaletteTokens(
            background = argb(0xFF000000),
            surface = argb(0xFF191922),
            surfaceHighlight = argb(0xFF22222B),
            primaryText = argb(0xFFFFFFFF),
            secondaryText = argb(0xFF8A8A8F),
            accent = argb(0xFF00A8E1),
            onAccent = argb(0xFF000000),
            scrim = argb(0x73000000),
            divider = argb(0xFF22222B),
        ),
        light = PaletteTokens(
            background = argb(0xFFFFFFFF),
            surface = argb(0xFFF2F2F5),
            surfaceHighlight = argb(0xFFE6E6EB),
            primaryText = argb(0xFF000000),
            secondaryText = argb(0xFF5F5F66),
            accent = argb(0xFF0094C6),
            onAccent = argb(0xFF000000),
            scrim = argb(0x73000000),
            divider = argb(0xFFDCDCE2),
            accentText = argb(0xFF00769E),
        ),
        tiles = listOf(argb(0xFF1B2440), argb(0xFF12333A), argb(0xFF2A1B40), argb(0xFF0E2A2A)),
    )

    val typeScale = TypeScaleTokens(
        display = heading(22f, BOLD),
        headline = heading(22f, BOLD),
        title = heading(17f, BOLD),
        body = body(14.5f, REGULAR),
        bodyEmphasis = body(14.5f, MEDIUM),
        subhead = small(12.5f, REGULAR),
        caption = small(12.5f, REGULAR),
        micro = small(11f, REGULAR),
    )

    val tokens = PresetTokens(
        palette = palette,
        typeScale = typeScale,
        shape = ShapeTokens(
            artworkRadius = 6f,
            cardRadius = 6f,
            sheetTopRadius = 16f,
            miniPlayerRadius = 6f,
            lyricsCardRadius = 6f,
            searchFieldRadius = 8f,
        ),
        spacing = SpacingTokens(screenMargin = 16f, rowHeight = 64f, gridGutter = 12f, sectionGap = 24f),
        motion = MotionTokens(
            pressScale = 0.95f,
            pressDurationMs = 100,
            transitionMs = 150,
            springStiffness = ThemeTokens.STIFFNESS_MEDIUM,
            springDamping = ThemeTokens.DAMPING_NO_BOUNCE,
            enableArtworkMotion = true,
            enableBlur = false,
            enableAmbientBackground = true,
            swipeSnapMs = 200,
            swipeThreshold = 0.35f,
            heartPopScale = ThemeTokens.HEART_POP_SCALE,
            heartPopMs = 200,
        ),
        navigation = NavigationTokens(
            visibleTabs = listOf(NavTab.HOME, NavTab.SEARCH, NavTab.LIBRARY),
            tabStyle = TabStyle.OUTLINED_TO_FILLED,
            activeTint = TabTint.ACCENT,
            labels = mapOf(NavTab.SEARCH to "Find"),
        ),
        sizes = SizeTokens(
            miniArtwork = 48f,
            miniPlayerHeight = 64f,
            carouselArtwork = 128f,
            rowArtwork = 48f,
            artistArtwork = 96f,
            playButton = 72f,
            playIcon = 40f,
            transportIcon = 32f,
            scrubberTrack = 3f,
            scrubberHandle = 10f,
            handleOnlyWhileDragging = true,
            artworkElevation = 16f,
            playerArtworkFraction = 0.45f,
            otherLineAlpha = 0.5f,
            playerScrimAlpha = 0.45f,
        ),
        signature = SignatureTokens(auraAlpha = 0.40f, auraSpreadDp = 64f, cardLiftDp = 0f, cinemaBackdrop = CinemaBackdrop.BLURRED_COVER),
    )
}
