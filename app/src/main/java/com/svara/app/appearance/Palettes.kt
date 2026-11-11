package com.svara.app.appearance

import com.svara.app.appearance.tokens.AnahataSpec
import com.svara.app.appearance.tokens.PalettePair
import com.svara.app.appearance.tokens.PaletteTokens
import com.svara.app.appearance.tokens.RagaSpec
import com.svara.app.appearance.tokens.ShrutiSpec
import com.svara.app.appearance.tokens.SpandaSpec
import com.svara.app.appearance.tokens.TalaSpec
import kotlin.math.pow

/** A colour as packed ARGB, kept free of Compose so contrast rules are unit tested on the JVM. */
@JvmInline
value class Argb(val value: Long) {
    val r: Int get() = ((value shr 16) and 0xFF).toInt()
    val g: Int get() = ((value shr 8) and 0xFF).toInt()
    val b: Int get() = (value and 0xFF).toInt()
    val alpha: Int get() = ((value shr 24) and 0xFF).toInt()

    companion object {
        fun rgb(r: Int, g: Int, b: Int) = Argb(0xFF000000 or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
    }
}

/** Semantic colour tokens. Every screen reads these (through MaterialTheme), never raw colours. */
data class ColourTokens(
    val background: Argb,
    val surface: Argb,
    val surfaceVariant: Argb,
    val onSurface: Argb,
    val onSurfaceVariant: Argb,
    val accent: Argb,
    val onAccent: Argb,
    val scrim: Argb,
)

data class PresetPalette(val dark: ColourTokens, val light: ColourTokens, val tiles: List<Argb>)

data class NamedColour(val name: String, val colour: Argb)

object Contrast {
    private fun channel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(c: Argb): Double = 0.2126 * channel(c.r) + 0.7152 * channel(c.g) + 0.0722 * channel(c.b)

    fun ratio(a: Argb, b: Argb): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** [fg] drawn over an opaque [bg]: what the eye sees, for contrast checks of translucent text. */
    fun composite(fg: Argb, bg: Argb): Argb {
        val a = fg.alpha / 255.0
        fun mix(f: Int, b: Int) = (f * a + b * (1 - a)).let { Math.round(it).toInt() }.coerceIn(0, 255)
        return Argb.rgb(mix(fg.r, bg.r), mix(fg.g, bg.g), mix(fg.b, bg.b))
    }

    /** WCAG AA for normal text. */
    const val AA_TEXT = 4.5

    /** WCAG 1.4.11 for icons and other non-text indicators. */
    const val AA_NON_TEXT = 3.0

    fun passesAA(fg: Argb, bg: Argb) = ratio(fg, bg) >= AA_TEXT

    /** Black or white, whichever reads better on [bg]. */
    fun onColour(bg: Argb): Argb = if (ratio(Argb(0xFF000000), bg) >= ratio(Argb(0xFFFFFFFF), bg)) Argb(0xFF000000) else Argb(0xFFFFFFFF)
}

object Palettes {
    private fun dark(bg: Long, surface: Long, variant: Long, on: Long, onVariant: Long, accent: Long) = ColourTokens(
        background = Argb(bg), surface = Argb(surface), surfaceVariant = Argb(variant),
        onSurface = Argb(on), onSurfaceVariant = Argb(onVariant),
        accent = Argb(accent), onAccent = Contrast.onColour(Argb(accent)), scrim = Argb(0xCC000000),
    )

    /**
     * Material bridge for screens shared across presets. surfaceVariant is the highlight surface,
     * onSurfaceVariant the secondary text (composited if translucent), and the accent is the
     * text-safe [PaletteTokens.accentText], since Material draws text buttons in primary.
     */
    fun fromTokens(pair: PalettePair): PresetPalette = PresetPalette(bridge(pair.dark), bridge(pair.light), pair.tiles)

    /** One palette as Material bridge tokens (see [fromTokens]). */
    fun bridge(t: PaletteTokens) = ColourTokens(
            background = t.background,
            surface = t.surface,
            surfaceVariant = t.surfaceHighlight,
            onSurface = t.primaryText,
            onSurfaceVariant = Contrast.composite(t.secondaryText, t.background),
            accent = t.accentText,
            onAccent = Contrast.onColour(t.accentText),
            scrim = t.scrim,
        )

    val ANAHATA = fromTokens(AnahataSpec.palette)
    val SHRUTI = fromTokens(ShrutiSpec.palette)
    val TALA = fromTokens(TalaSpec.palette)
    val RAGA = fromTokens(RagaSpec.palette)
    val SPANDA = fromTokens(SpandaSpec.palette)

    /** Theme Studio's fixed custom-accent palette. Each entry is still checked at runtime against the background. */
    val ACCENTS = listOf(
        NamedColour("Lavender", Argb(0xFFB9B4FF)),
        NamedColour("Sky", Argb(0xFF7CC8FF)),
        NamedColour("Aqua", Argb(0xFF5EE0D0)),
        NamedColour("Mint", Argb(0xFF7EE2A0)),
        NamedColour("Lime", Argb(0xFFC6E86A)),
        NamedColour("Sun", Argb(0xFFFFD54F)),
        NamedColour("Apricot", Argb(0xFFFFB074)),
        NamedColour("Coral", Argb(0xFFFF8F80)),
        NamedColour("Rose", Argb(0xFFFF8FB8)),
        NamedColour("Orchid", Argb(0xFFE29CF0)),
        NamedColour("Sand", Argb(0xFFE6D3A8)),
        NamedColour("Silver", Argb(0xFFCFD3DC)),
    )

    /** Accents that pass WCAG AA against [background]; the rest are excluded from the picker. */
    fun availableAccents(background: Argb): List<IndexedValue<NamedColour>> =
        ACCENTS.withIndex().filter { Contrast.passesAA(it.value.colour, background) }

    /** Monochrome source: a restrained grey accent for dark and light themes. */
    val MONO_DARK = Argb(0xFFBDBDBD)
    val MONO_LIGHT = Argb(0xFF4A4A4A)

    private val WARM_DARK = Argb(0xFFFFB38A)
    private val WARM_LIGHT = Argb(0xFF8A3A10)
    private val COOL_DARK = Argb(0xFF9CC9FF)
    private val COOL_LIGHT = Argb(0xFF1F5A99)

    /**
     * Applies a colour system to a preset's tokens. Any accent that fails AA against the
     * background (text) or that doesn't let [ColourTokens.onAccent] read on it falls back to the
     * preset default.
     */
    fun tokens(
        palette: PresetPalette,
        dark: Boolean,
        system: ColourSystem,
        customAccent: Int,
        albumAccent: Argb?,
    ): ColourTokens {
        val base = if (dark) palette.dark else palette.light
        val candidate: Argb? = when (system) {
            ColourSystem.PRESET -> null
            ColourSystem.ALBUM -> albumAccent?.let { adjustForBackground(it, base.background, dark) }
            ColourSystem.MONOCHROME -> base.onSurface
            ColourSystem.WARM -> if (dark) WARM_DARK else WARM_LIGHT
            ColourSystem.COOL -> if (dark) COOL_DARK else COOL_LIGHT
            ColourSystem.CUSTOM -> ACCENTS.getOrNull(customAccent)?.colour?.let { if (dark) it else adjustForBackground(it, base.background, false) }
        }
        val accent = candidate?.takeIf { Contrast.passesAA(it, base.background) && Contrast.passesAA(it, base.surface) } ?: base.accent
        return base.copy(accent = accent, onAccent = Contrast.onColour(accent))
    }

    /** Lightens (dark theme) or darkens (light theme) an album colour until it reads as text. */
    fun adjustForBackground(colour: Argb, background: Argb, dark: Boolean): Argb {
        var c = colour
        repeat(12) {
            if (Contrast.passesAA(c, background)) return c
            c = mix(c, if (dark) Argb(0xFFFFFFFF) else Argb(0xFF000000), 0.15)
        }
        return c
    }

    fun mix(a: Argb, b: Argb, t: Double): Argb = Argb.rgb(
        (a.r + (b.r - a.r) * t).toInt().coerceIn(0, 255),
        (a.g + (b.g - a.g) * t).toInt().coerceIn(0, 255),
        (a.b + (b.b - a.b) * t).toInt().coerceIn(0, 255),
    )

    /** Deterministic tile colour for a name, so a collection keeps its colour. */
    fun tileFor(palette: PresetPalette, key: String): Argb = palette.tiles[Math.floorMod(key.hashCode(), palette.tiles.size)]
}
