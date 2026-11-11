package com.svara.app.appearance.colour

import com.svara.app.appearance.Argb
import com.svara.app.appearance.Contrast
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A colour in OKLCH (perceptual lightness 0..1, chroma, hue in degrees). */
data class Oklch(val l: Double, val c: Double, val h: Double)

/** Contrast targets: text and non-text (icons, fills, selected states). */
data class ContrastTargets(val text: Double, val largeText: Double, val nonText: Double) {
    companion object {
        val NORMAL = ContrastTargets(text = Contrast.AA_TEXT, largeText = Contrast.AA_NON_TEXT, nonText = Contrast.AA_NON_TEXT)
        /** High contrast: 7:1 for text (WCAG AAA), 4.5:1 for everything else. */
        val HIGH = ContrastTargets(text = 7.0, largeText = Contrast.AA_TEXT, nonText = Contrast.AA_TEXT)
    }
}

/** A colour after fitting, and whether it had to change to meet its target. */
data class Fitted(val colour: Argb, val adjusted: Boolean)

/** The accent roles derived from one chosen colour. */
data class AccentSet(
    /** Icons, progress, fills behind icons. */
    val accent: Fitted,
    /** The accent as text. */
    val accentText: Fitted,
    /** Fill behind text labels, so [onAccent] text meets the text target. */
    val accentFill: Fitted,
    val onAccent: Argb,
) {
    val anyAdjusted: Boolean get() = accent.adjusted || accentText.adjusted || accentFill.adjusted
}

/**
 * The one place colours are made readable. A colour that fails its contrast target against the
 * given backgrounds is moved in lightness only (OKLCH), keeping its hue and as much chroma as
 * sRGB allows, until it passes. Pure Kotlin; unit tested.
 */
object ContrastEngine {
    private val BLACK = Argb(0xFF000000)
    private val WHITE = Argb(0xFFFFFFFF)
    private const val STEPS = 40

    fun passes(fg: Argb, backgrounds: List<Argb>, target: Double): Boolean =
        backgrounds.all { Contrast.ratio(Contrast.composite(fg, it), it) >= target }

    /**
     * Returns [colour] if it already meets [target] against every background; otherwise the
     * nearest lightness (toward white on dark backgrounds, toward black on light ones) that does.
     */
    fun fit(colour: Argb, backgrounds: List<Argb>, target: Double): Fitted {
        val opaque = Argb(colour.value or 0xFF000000)
        if (passes(opaque, backgrounds, target)) return Fitted(opaque, false)
        val lch = toOklch(opaque)
        val darkBackground = backgrounds.map(Contrast::luminance).average() < LIGHT_BACKGROUND
        val end = if (darkBackground) 1.0 else 0.0
        // The extreme in that direction (white or black, at this hue) is the last resort.
        val extreme = fromOklch(Oklch(end, lch.c, lch.h))
        if (!passes(extreme, backgrounds, target)) return Fitted(if (darkBackground) WHITE else BLACK, true)
        var lo = lch.l
        var hi = end
        repeat(STEPS) {
            val mid = (lo + hi) / 2
            if (passes(fromOklch(Oklch(mid, lch.c, lch.h)), backgrounds, target)) hi = mid else lo = mid
        }
        return Fitted(fromOklch(Oklch(hi, lch.c, lch.h)), true)
    }

    /** Black or white, whichever reads better on [fill]. */
    fun onColour(fill: Argb): Argb = if (Contrast.ratio(BLACK, fill) >= Contrast.ratio(WHITE, fill)) BLACK else WHITE

    /** Every accent role for [base] on this background and surface. */
    fun accentSet(base: Argb, background: Argb, surface: Argb, targets: ContrastTargets): AccentSet {
        val grounds = listOf(background, surface)
        val accent = fit(base, grounds, targets.nonText)
        val accentText = fit(base, grounds, targets.text)
        val on = onColour(accent.colour)
        // The fill under labels moves away from its label colour until the label reads.
        val fill = fit(accent.colour, listOf(on), targets.text).let { f ->
            Fitted(f.colour, accent.adjusted || f.adjusted)
        }
        return AccentSet(accent, accentText, fill, on)
    }

    /** Hue difference in degrees (0..180). */
    fun hueDistance(a: Argb, b: Argb): Double {
        val d = abs(toOklch(a).h - toOklch(b).h) % 360
        return if (d > 180) 360 - d else d
    }

    /** A colour at [hue] with the given lightness and chroma, clipped into sRGB. */
    fun fromHue(hue: Double, lightness: Double, chroma: Double): Argb = fromOklch(Oklch(lightness, chroma, hue))

    /** The same colour with no chroma (for the monochrome source). */
    fun desaturate(colour: Argb): Argb = toOklch(colour).let { fromOklch(Oklch(it.l, 0.0, it.h)) }

    /** A very dark shade at the same hue (scrims tinted by a cover colour; white text reads on it). */
    fun deepShade(colour: Argb): Argb = toOklch(colour).let { fromOklch(Oklch(DEEP_L, minOf(it.c, DEEP_C), it.h)) }

    /**
     * The largest alpha, up to [desired], at which [tint] laid over [ground] keeps every colour
     * in [texts] at [target]. Ambient tints use this, so they never cost text contrast.
     */
    fun maxTintAlpha(tint: Argb, ground: Argb, texts: List<Argb>, target: Double, desired: Double): Double {
        var a = desired
        while (a > 0.0) {
            val under = com.svara.app.appearance.Palettes.mix(ground, tint, a)
            if (texts.all { Contrast.ratio(Contrast.composite(it, under), under) >= target }) return a
            a -= TINT_STEP
        }
        return 0.0
    }

    fun hex(colour: Argb): String = "#%06X".format(colour.value and 0xFFFFFF)

    // ---- OKLab / OKLCH (Björn Ottosson's reference matrices) ------------------------------

    private fun toLinear(c: Int): Double { val s = c / 255.0; return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
    private fun toGamma(v: Double): Double = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055

    fun toOklch(c: Argb): Oklch {
        val r = toLinear(c.r); val g = toLinear(c.g); val b = toLinear(c.b)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        val okL = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
        val okA = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        val okB = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        val h = Math.toDegrees(atan2(okB, okA)).let { if (it < 0) it + 360 else it }
        return Oklch(okL, sqrt(okA * okA + okB * okB), h)
    }

    private fun rawRgb(o: Oklch): DoubleArray {
        val a = o.c * cos(Math.toRadians(o.h))
        val b = o.c * sin(Math.toRadians(o.h))
        val l = (o.l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m = (o.l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s = (o.l - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        return doubleArrayOf(
            4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        )
    }

    private fun inGamut(rgb: DoubleArray) = rgb.all { it in -GAMUT_EPS..1 + GAMUT_EPS }

    /** OKLCH to sRGB; out-of-gamut colours lose chroma (never lightness or hue) until they fit. */
    fun fromOklch(o: Oklch): Argb {
        val l = o.l.coerceIn(0.0, 1.0)
        var rgb = rawRgb(Oklch(l, o.c, o.h))
        if (!inGamut(rgb)) {
            var lo = 0.0
            var hi = o.c
            repeat(STEPS) {
                val mid = (lo + hi) / 2
                if (inGamut(rawRgb(Oklch(l, mid, o.h)))) lo = mid else hi = mid
            }
            rgb = rawRgb(Oklch(l, lo, o.h))
        }
        fun ch(v: Double) = (toGamma(v.coerceIn(0.0, 1.0)) * 255).roundToInt().coerceIn(0, 255)
        return Argb.rgb(ch(rgb[0]), ch(rgb[1]), ch(rgb[2]))
    }

    private const val GAMUT_EPS = 1e-4
    private const val DEEP_L = 0.2
    private const val DEEP_C = 0.06
    private const val TINT_STEP = 0.02
    /** Below this average relative luminance a background counts as dark. */
    private const val LIGHT_BACKGROUND = 0.18
}
