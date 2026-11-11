package com.svara.app.playback

import com.svara.app.domain.audio.FadeCurve
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import kotlin.math.pow

/**
 * The player's volume is the product of independent gains, so features never fight over it:
 * ReplayGain levelling × track transition fade × sleep-timer fade.
 */
class VolumeMixer(private val player: Player) {
    var leveling = 1f
        set(value) { field = value.coerceIn(0f, 1f); apply() }
    /** Linear fade progress (0 silent, 1 full); [curve] shapes it into a gain. */
    var transition = 1f
        set(value) { field = value.coerceIn(0f, 1f); apply() }
    var curve: FadeCurve = FadeCurve.LINEAR
        set(value) { field = value; apply() }
    var sleep = 1f
        set(value) { field = value.coerceIn(0f, 1f); apply() }

    private fun apply() {
        player.volume = leveling * curve.gain(transition) * sleep
    }
}

/** ReplayGain tag parsing. Only attenuation is applied; positive gain would need a limiter to avoid clipping. */
object ReplayGain {
    private val KEYS = setOf("REPLAYGAIN_TRACK_GAIN", "R128_TRACK_GAIN")

    /** Parses "-6.54 dB" (ReplayGain) or a Q7.8 integer (R128, Opus). Returns dB or null. */
    fun parseDb(key: String, value: String): Double? {
        val k = key.uppercase()
        return when (k) {
            "REPLAYGAIN_TRACK_GAIN" -> value.trim().removeSuffix("dB").removeSuffix("db").trim().replace(',', '.').toDoubleOrNull()
            // R128 gains are relative to -23 LUFS; ReplayGain targets about -18 LUFS, hence +5 dB.
            "R128_TRACK_GAIN" -> value.trim().toIntOrNull()?.let { it / 256.0 + 5.0 }
            else -> null
        }
    }

    /** Linear volume for a gain in dB, never above 1.0. */
    fun linear(db: Double): Float = 10.0.pow(db / 20.0).toFloat().coerceIn(0f, 1f)

    @androidx.annotation.OptIn(UnstableApi::class)
    fun fromMetadata(metadata: Metadata): Double? {
        for (i in 0 until metadata.length()) {
            when (val e = metadata[i]) {
                is TextInformationFrame -> {
                    val description = e.description
                    if (e.id == "TXXX" && description != null && description.uppercase() in KEYS) e.values.firstOrNull()?.let { v -> parseDb(description, v) }?.let { return it }
                }
                is VorbisComment -> if (e.key.uppercase() in KEYS) parseDb(e.key, e.value)?.let { return it }
            }
        }
        return null
    }
}
