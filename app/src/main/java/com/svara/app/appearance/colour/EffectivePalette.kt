package com.svara.app.appearance.colour

import com.svara.app.appearance.Argb
import com.svara.app.appearance.BackgroundTone
import com.svara.app.appearance.ColourSystem
import com.svara.app.appearance.Contrast
import com.svara.app.appearance.Palettes
import com.svara.app.appearance.ResolvedTheme
import com.svara.app.appearance.tokens.PaletteTokens
import com.svara.app.appearance.tokens.PresetTokens
import com.svara.app.appearance.tokens.ThemeTokens

/** Which accent roles had to change to stay readable (for the "Adjusted for readability" note). */
data class AccentAdjustment(val text: Boolean, val icons: Boolean, val fills: Boolean) {
    val any: Boolean get() = text || icons || fills

    /** "text", "icons and fills" …, for the note under the swatch grid. */
    fun describe(): String = listOfNotNull(if (text) "text" else null, if (icons) "icons" else null, if (fills) "buttons" else null)
        .let { if (it.size <= 1) it.joinToString() else it.dropLast(1).joinToString() + " and " + it.last() }

    companion object {
        val NONE = AccentAdjustment(text = false, icons = false, fills = false)
    }
}

data class PaletteResult(val palette: PaletteTokens, val adjustment: AccentAdjustment, val targets: ContrastTargets)

/**
 * The palette every screen uses: the preset's tokens, then the colour source, the background
 * tone and High contrast, with the contrast engine keeping every text and icon role readable.
 * With the preset's own colours, the default tone and normal contrast, the tokens pass through
 * untouched (they were checked against the design sources).
 */
object EffectivePalette {
    /** The hue slider's colour: fixed lightness and chroma inside sRGB at every hue. */
    const val HUE_LIGHTNESS = 0.70
    const val HUE_CHROMA = 0.14

    private val BLACK = Argb(0xFF000000)
    private val WHITE = Argb(0xFFFFFFFF)
    private const val TINT_BACKGROUND = 0.08
    private const val TINT_SURFACE = 0.11
    private const val TINT_HIGHLIGHT = 0.14
    private const val PURE_SURFACE = 0.6
    private const val PURE_HIGHLIGHT = 0.8
    private const val HC_SURFACE = 0.5
    /** Divider alpha under High contrast: a visible line in the text colour. */
    private const val HC_DIVIDER_ALPHA = 0x99L

    fun hueColour(hue: Float): Argb = ContrastEngine.fromHue(hue.toDouble(), HUE_LIGHTNESS, HUE_CHROMA)

    /** The custom accent before any contrast fitting; null when the source isn't Custom. */
    fun customColour(theme: ResolvedTheme): Argb? = theme.customHue?.let(::hueColour)
        ?: theme.customSwatch?.colour
        ?: Palettes.ACCENTS.getOrNull(theme.customAccent)?.colour

    /** The colour the user chose (or the album gave), before fitting. Null means "the preset's own". */
    fun chosenAccent(theme: ResolvedTheme, albumAccent: Argb?, dark: Boolean): Argb? = when (theme.colourSystem) {
        ColourSystem.PRESET -> null
        ColourSystem.ALBUM -> albumAccent
        ColourSystem.CUSTOM -> customColour(theme)
        ColourSystem.MONOCHROME -> if (dark) Palettes.MONO_DARK else Palettes.MONO_LIGHT
        ColourSystem.WARM -> Palettes.tokens(theme.spec.palette, dark, ColourSystem.WARM, 0, null).accent
        ColourSystem.COOL -> Palettes.tokens(theme.spec.palette, dark, ColourSystem.COOL, 0, null).accent
    }

    fun build(tokens: PresetTokens, theme: ResolvedTheme, dark: Boolean, highContrast: Boolean, albumAccent: Argb?): PaletteResult {
        val targets = if (highContrast) ContrastTargets.HIGH else ContrastTargets.NORMAL
        var p = tokens.palette.pick(dark)
        val chosen = chosenAccent(theme, albumAccent, dark)
        val tone = theme.backgroundTone
        val untouched = chosen == null && tone == BackgroundTone.SOFT_DARK && !highContrast &&
            theme.colourSystem != ColourSystem.MONOCHROME && !theme.frosted
        if (untouched) return PaletteResult(p, AccentAdjustment.NONE, targets)

        val tint = chosen ?: p.accent
        if (theme.colourSystem == ColourSystem.MONOCHROME) p = monochrome(p)
        p = tone(p, tone, dark, tint)
        // Frosted surfaces: a static frost toward the text colour (no live blur).
        if (theme.frosted && !highContrast) p = p.copy(
            surface = Palettes.mix(p.surface, p.primaryText, ThemeTokens.FROST_AMOUNT),
            surfaceHighlight = Palettes.mix(p.surfaceHighlight, p.primaryText, ThemeTokens.FROST_AMOUNT),
        )
        if (highContrast) p = strengthen(p, dark)
        p = readableText(p, targets)

        // The accent: the chosen colour, or the preset's own (re-fitted for tone or contrast).
        val base = chosen ?: tokens.palette.pick(dark).accent
        val set = ContrastEngine.accentSet(base, p.background, p.surface, targets)
        val result = p.copy(
            accent = set.accent.colour,
            accentText = set.accentText.colour,
            accentFill = set.accentFill.colour,
            onAccent = set.onAccent,
        )
        // Only report changes to a colour the user picked; preset accents are already intended.
        val adjustment = if (chosen == null) AccentAdjustment.NONE
        else AccentAdjustment(text = set.accentText.adjusted, icons = set.accent.adjusted, fills = set.accentFill.adjusted)
        return PaletteResult(result, adjustment, targets)
    }

    private fun monochrome(p: PaletteTokens) = p.copy(
        background = ContrastEngine.desaturate(p.background),
        surface = ContrastEngine.desaturate(p.surface),
        surfaceHighlight = ContrastEngine.desaturate(p.surfaceHighlight),
        divider = ContrastEngine.desaturate(p.divider),
    )

    private fun tone(p: PaletteTokens, tone: BackgroundTone, dark: Boolean, tint: Argb): PaletteTokens = when (tone) {
        BackgroundTone.SOFT_DARK -> p
        // Pure black is for OLED dark themes; light themes keep their own background.
        BackgroundTone.PURE_BLACK -> if (!dark) p else p.copy(
            background = BLACK,
            surface = Palettes.mix(BLACK, p.surface, PURE_SURFACE),
            surfaceHighlight = Palettes.mix(BLACK, p.surfaceHighlight, PURE_HIGHLIGHT),
        )
        BackgroundTone.TINTED -> p.copy(
            background = Palettes.mix(p.background, tint, TINT_BACKGROUND),
            surface = Palettes.mix(p.surface, tint, TINT_SURFACE),
            surfaceHighlight = Palettes.mix(p.surfaceHighlight, tint, TINT_HIGHLIGHT),
        )
    }

    /** High contrast: pure black or white ground, full-strength text, a visible divider. Hue untouched. */
    private fun strengthen(p: PaletteTokens, dark: Boolean): PaletteTokens {
        val ground = if (dark) BLACK else WHITE
        val ink = if (dark) WHITE else BLACK
        return p.copy(
            background = ground,
            surface = Palettes.mix(ground, p.surface, HC_SURFACE),
            surfaceHighlight = Palettes.mix(ground, p.surfaceHighlight, HC_SURFACE),
            primaryText = ink,
            divider = Argb((HC_DIVIDER_ALPHA shl ALPHA_SHIFT) or (ink.value and RGB_MASK)),
        )
    }

    /** Primary and secondary text meet the text target on every surface (lightness only). */
    private fun readableText(p: PaletteTokens, targets: ContrastTargets): PaletteTokens {
        val grounds = listOf(p.background, p.surface, p.surfaceHighlight)
        fun fit(c: Argb) = ContrastEngine.fit(Contrast.composite(c, p.background), grounds, targets.text).colour
        return p.copy(primaryText = fit(p.primaryText), secondaryText = fit(p.secondaryText))
    }

    private const val ALPHA_SHIFT = 24
    private const val RGB_MASK = 0x00FFFFFFL
}
