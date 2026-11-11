package com.svara.app.appearance.tokens

import com.svara.app.appearance.tokens.ThemeTokens.MEDIUM
import com.svara.app.appearance.tokens.ThemeTokens.REGULAR
import com.svara.app.appearance.tokens.ThemeTokens.SEMIBOLD
import com.svara.app.appearance.tokens.ThemeTokens.argb
import com.svara.app.appearance.tokens.ThemeTokens.body
import com.svara.app.appearance.tokens.ThemeTokens.heading
import com.svara.app.appearance.tokens.ThemeTokens.small

/**
 * ANĀHATA, SVARĀ's own design. These are the values it already used before the token model, so
 * moving it onto tokens changes nothing on screen. Its tabs come from Navigation Studio.
 */
object AnahataSpec {
    val palette = PalettePair(
        dark = PaletteTokens(
            background = argb(0xFF101114),
            surface = argb(0xFF16171C),
            surfaceHighlight = argb(0xFF24262E),
            primaryText = argb(0xFFECEDF2),
            secondaryText = argb(0xFFB7BAC6),
            accent = argb(0xFFB9B4FF),
            onAccent = argb(0xFF000000),
            scrim = argb(0xCC000000),
            divider = argb(0xFF24262E),
        ),
        light = PaletteTokens(
            background = argb(0xFFFAFAFD),
            surface = argb(0xFFFFFFFF),
            surfaceHighlight = argb(0xFFE6E7EF),
            primaryText = argb(0xFF15161B),
            secondaryText = argb(0xFF4A4C57),
            accent = argb(0xFF4D45B5),
            onAccent = argb(0xFFFFFFFF),
            scrim = argb(0xCC000000),
            divider = argb(0xFFE6E7EF),
        ),
        tiles = listOf(argb(0xFF3C3A6E), argb(0xFF2E4B5E), argb(0xFF5A3E5E), argb(0xFF3E5A4A)),
    )

    /** The original 5-step scale: 12 / 15 / 18 / 26 / 36 sp. */
    val typeScale = TypeScaleTokens(
        display = heading(36f, SEMIBOLD),
        headline = heading(26f, SEMIBOLD),
        title = heading(18f, SEMIBOLD),
        body = body(15f, REGULAR),
        bodyEmphasis = body(15f, SEMIBOLD),
        subhead = small(12f, REGULAR),
        caption = small(12f, MEDIUM),
        micro = small(12f, MEDIUM),
    )

    val tokens = PresetTokens(
        palette = palette,
        typeScale = typeScale,
        shape = ShapeTokens(
            artworkRadius = 8f,
            cardRadius = 16f,
            sheetTopRadius = 28f,
            miniPlayerRadius = 0f,
            heroRadius = 16f,
            lyricsCardRadius = 16f,
        ),
        spacing = SpacingTokens(screenMargin = 16f, rowHeight = 56f, gridGutter = 8f, sectionGap = 24f),
        motion = MotionTokens(
            pressScale = 1f,
            pressDurationMs = 0,
            transitionMs = 300,
            springStiffness = ThemeTokens.STIFFNESS_MEDIUM,
            springDamping = ThemeTokens.DAMPING_NO_BOUNCE,
            enableArtworkMotion = false,
            enableBlur = false,
            enableAmbientBackground = false,
        ),
        navigation = NavigationTokens(visibleTabs = emptyList(), tabStyle = TabStyle.TINTED_PILL, activeTint = TabTint.ACCENT),
        sizes = SizeTokens(
            miniArtwork = 44f,
            miniPlayerHeight = 56f,
            carouselArtwork = 168f,
            playButton = 80f,
            playIcon = 44f,
            transportButton = 64f,
            transportIcon = 40f,
            scrubberTrack = 4f,
            scrubberHandle = 20f,
            handleOnlyWhileDragging = false,
            playerArtworkFraction = 0.8f,
        ),
        signature = SignatureTokens(auraAlpha = 0.28f, auraSpreadDp = 48f, cardLiftDp = 2f, cinemaBackdrop = CinemaBackdrop.BLURRED_COVER),
    )
}
