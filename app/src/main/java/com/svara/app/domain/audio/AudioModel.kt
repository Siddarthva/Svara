package com.svara.app.domain.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin

/** Built-in equalizer presets. Levels are computed for whatever bands the device reports. */
enum class EqPreset(val label: String) {
    FLAT("Flat"), BASS_BOOST("Bass Boost"), TREBLE("Treble"), VOCAL("Vocal"), CLASSICAL("Classical");

    /** Gain in decibels this preset wants at [hz]. */
    fun gainDb(hz: Double): Double {
        val octave = log10(hz.coerceAtLeast(1.0) / REF_HZ) / log10(2.0) // 0 at 1 kHz
        return when (this) {
            FLAT -> 0.0
            BASS_BOOST -> when { hz <= 100 -> 6.0; hz <= 250 -> 4.0; hz <= 500 -> 1.5; else -> 0.0 }
            TREBLE -> when { hz >= 8_000 -> 6.0; hz >= 4_000 -> 4.0; hz >= 2_000 -> 1.5; else -> 0.0 }
            VOCAL -> (4.0 * bell(octave, centre = 0.6, width = 1.6)) - (if (hz < 150) 2.0 else 0.0)
            CLASSICAL -> when { hz <= 150 -> 3.0; hz >= 6_000 -> 3.0; abs(octave) < 1.0 -> -1.0; else -> 0.0 }
        }
    }

    private fun bell(x: Double, centre: Double, width: Double): Double {
        val t = ((x - centre) / width).coerceIn(-1.0, 1.0)
        return (cos(t * PI) + 1) / 2
    }

    private companion object {
        const val REF_HZ = 1_000.0
    }
}

/**
 * Equalizer and effect settings, stored locally. [bandLevelsMb] are millibels, one per band the
 * device reported last time (re-fitted if the device reports a different set). Strengths are 0..1000.
 */
data class EqSettings(
    val enabled: Boolean = false,
    /** A built-in preset name, a user preset name, or null for "Custom". */
    val preset: String? = EqPreset.FLAT.name,
    val bandLevelsMb: List<Int> = emptyList(),
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    /** Loudness enhancer target gain, millibels 0..[MAX_LOUDNESS_MB]. */
    val loudnessMb: Int = 0,
    /** User presets: name → band levels. */
    val userPresets: Map<String, List<Int>> = emptyMap(),
) {
    companion object {
        const val MAX_STRENGTH = 1000
        const val MAX_LOUDNESS_MB = 1000
        const val MAX_USER_PRESETS = 12
    }
}

/** What the device's audio effects support for the current audio session. */
data class EffectsCapabilities(
    val equalizer: Boolean = false,
    /** Centre frequency of each band in Hz. */
    val bandCentersHz: List<Int> = emptyList(),
    val minLevelMb: Int = 0,
    val maxLevelMb: Int = 0,
    val bassBoost: Boolean = false,
    val virtualizer: Boolean = false,
    val loudness: Boolean = false,
    /** Effects that failed and were turned off, with a short reason (never affects playback). */
    val failures: Map<String, String> = emptyMap(),
    /** False until a player session exists. */
    val attached: Boolean = false,
) {
    val anySupported: Boolean get() = equalizer || bassBoost || virtualizer || loudness
}

object EqMath {
    /** A preset's levels for these bands, clamped to the device's range. */
    fun levelsFor(preset: EqPreset, centersHz: List<Int>, minMb: Int, maxMb: Int): List<Int> =
        centersHz.map { hz -> (preset.gainDb(hz.toDouble()) * MB_PER_DB).roundToInt().coerceIn(minMb, maxMb) }

    /** Keeps stored levels when the band count matches; otherwise starts flat. Always clamped. */
    fun fit(levels: List<Int>, bands: Int, minMb: Int, maxMb: Int): List<Int> =
        if (levels.size == bands) levels.map { it.coerceIn(minMb, maxMb) } else List(bands) { 0.coerceIn(minMb, maxMb) }

    /** Band levels the settings ask for on this device. */
    fun resolve(settings: EqSettings, caps: EffectsCapabilities): List<Int> {
        val n = caps.bandCentersHz.size
        val builtIn = EqPreset.entries.firstOrNull { it.name == settings.preset }
        return when {
            n == 0 -> emptyList()
            builtIn != null -> levelsFor(builtIn, caps.bandCentersHz, caps.minLevelMb, caps.maxLevelMb)
            settings.preset != null && settings.preset in settings.userPresets ->
                fit(settings.userPresets.getValue(settings.preset), n, caps.minLevelMb, caps.maxLevelMb)
            else -> fit(settings.bandLevelsMb, n, caps.minLevelMb, caps.maxLevelMb)
        }
    }

    fun formatHz(hz: Int): String = if (hz >= 1000) "${"%.1f".format(hz / 1000.0).removeSuffix(".0")} kHz" else "$hz Hz"

    const val MB_PER_DB = 100
}

/** Shape of the crossfade ramp between tracks. */
enum class FadeCurve(val label: String) {
    LINEAR("Linear"), EQUAL_POWER("Equal power"), S_CURVE("Smooth (S-curve)");

    /** Gain for fade progress [t] in 0..1 going from silent (0) to full (1). */
    fun gain(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return when (this) {
            LINEAR -> x
            EQUAL_POWER -> sin(x * PI / 2).toFloat()
            S_CURVE -> (0.5 - 0.5 * cos(x * PI)).toFloat()
        }
    }
}
