package com.svara.app.playback.effects

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.svara.app.domain.audio.EffectsCapabilities
import com.svara.app.domain.audio.EqMath
import com.svara.app.domain.audio.EqSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A platform effect behind a small interface, so a failing effect can be contained and tests can fake one. */
interface EffectHandle {
    fun setEnabled(on: Boolean)
    fun release()
}

interface EqualizerHandle : EffectHandle {
    val bandCentersHz: List<Int>
    val minLevelMb: Int
    val maxLevelMb: Int
    fun setLevel(band: Int, levelMb: Int)
}

interface StrengthHandle : EffectHandle {
    /** 0..1000. */
    fun setStrength(strength: Int)
}

interface LoudnessHandle : EffectHandle {
    fun setTargetGainMb(gainMb: Int)
}

/** Creates effects for an audio session. Any of these may throw; the controller copes. */
interface EffectFactory {
    fun equalizer(session: Int): EqualizerHandle
    fun bassBoost(session: Int): StrengthHandle
    fun virtualizer(session: Int): StrengthHandle
    fun loudness(session: Int): LoudnessHandle
}

/** android.media.audiofx implementations. Priority 0, attached to the player's own session only. */
class PlatformEffectFactory : EffectFactory {
    override fun equalizer(session: Int): EqualizerHandle {
        val eq = Equalizer(0, session)
        val range = eq.bandLevelRange
        val centers = (0 until eq.numberOfBands).map { b -> eq.getCenterFreq(b.toShort()) / MILLIHERTZ }
        return object : EqualizerHandle {
            override val bandCentersHz = centers
            override val minLevelMb = range[0].toInt()
            override val maxLevelMb = range[1].toInt()
            override fun setLevel(band: Int, levelMb: Int) = eq.setBandLevel(band.toShort(), levelMb.toShort())
            override fun setEnabled(on: Boolean) { eq.enabled = on }
            override fun release() = eq.release()
        }
    }

    override fun bassBoost(session: Int): StrengthHandle {
        val bb = BassBoost(0, session)
        check(bb.strengthSupported) { "strength not supported" }
        return strength(bb) { bb.setStrength(it.toShort()) }
    }

    override fun virtualizer(session: Int): StrengthHandle {
        val v = Virtualizer(0, session)
        check(v.strengthSupported) { "strength not supported" }
        return strength(v) { v.setStrength(it.toShort()) }
    }

    override fun loudness(session: Int): LoudnessHandle {
        val le = LoudnessEnhancer(session)
        return object : LoudnessHandle {
            override fun setTargetGainMb(gainMb: Int) = le.setTargetGain(gainMb)
            override fun setEnabled(on: Boolean) { le.enabled = on }
            override fun release() = le.release()
        }
    }

    private fun strength(effect: AudioEffect, set: (Int) -> Unit) = object : StrengthHandle {
        override fun setStrength(strength: Int) = set(strength)
        override fun setEnabled(on: Boolean) { effect.enabled = on }
        override fun release() = effect.release()
    }

    private companion object {
        const val MILLIHERTZ = 1000
    }
}

/** What the effects support right now, shared with the UI (same process as the service). */
@Singleton
class AudioEffectsHub @Inject constructor() {
    private val _capabilities = MutableStateFlow(EffectsCapabilities())
    val capabilities: StateFlow<EffectsCapabilities> = _capabilities.asStateFlow()

    internal fun publish(caps: EffectsCapabilities) {
        _capabilities.value = caps
    }
}

/**
 * Equalizer, bass boost, virtualizer and loudness on the player's audio session. Every call into
 * an effect is guarded: if one throws (unsupported, removed by another app, driver error) that
 * effect is released and reported, and nothing else changes. Playback never depends on an effect.
 */
class AudioEffectsController(
    private val factory: EffectFactory,
    private val hub: AudioEffectsHub,
    private val onFailure: (String) -> Unit,
) {
    private var session = 0
    private var equalizer: EqualizerHandle? = null
    private var bassBoost: StrengthHandle? = null
    private var virtualizer: StrengthHandle? = null
    private var loudness: LoudnessHandle? = null
    private val failures = LinkedHashMap<String, String>()
    private var last: EqSettings? = null

    /** Creates the effects for [sessionId] (releasing any previous ones) and reapplies the settings. */
    fun attach(sessionId: Int) {
        if (sessionId == session && equalizer != null) return
        release()
        if (sessionId == 0) return
        session = sessionId
        failures.clear()
        equalizer = create(EQUALIZER) { factory.equalizer(sessionId) }
        bassBoost = create(BASS_BOOST) { factory.bassBoost(sessionId) }
        virtualizer = create(VIRTUALIZER) { factory.virtualizer(sessionId) }
        loudness = create(LOUDNESS) { factory.loudness(sessionId) }
        publish()
        last?.let { apply(it) }
    }

    /** Applies [settings]; with effects off every effect is disabled (the audio passes untouched). */
    fun apply(settings: EqSettings) {
        last = settings
        val caps = hub.capabilities.value
        equalizer?.let { eq ->
            guard(EQUALIZER) {
                val levels = EqMath.resolve(settings, caps)
                levels.forEachIndexed { band, level -> eq.setLevel(band, level) }
                eq.setEnabled(settings.enabled)
            }
        }
        bassBoost?.let { bb -> guard(BASS_BOOST) { bb.setStrength(settings.bassBoost); bb.setEnabled(settings.enabled && settings.bassBoost > 0) } }
        virtualizer?.let { v -> guard(VIRTUALIZER) { v.setStrength(settings.virtualizer); v.setEnabled(settings.enabled && settings.virtualizer > 0) } }
        loudness?.let { l -> guard(LOUDNESS) { l.setTargetGainMb(settings.loudnessMb); l.setEnabled(settings.enabled && settings.loudnessMb > 0) } }
    }

    fun release() {
        listOf(equalizer, bassBoost, virtualizer, loudness).forEach { h -> runCatching { h?.release() } }
        equalizer = null; bassBoost = null; virtualizer = null; loudness = null
        session = 0
        hub.publish(EffectsCapabilities(failures = failures.toMap()))
    }

    private fun <T : EffectHandle> create(name: String, make: () -> T): T? = try {
        make()
    } catch (e: Throwable) {
        Log.w(TAG, "$name unavailable on this device", e)
        // Not a failure the user caused: the effect is simply absent.
        null
    }

    private fun guard(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            Log.w(TAG, "$name failed; turning it off", e)
            when (name) {
                EQUALIZER -> { runCatching { equalizer?.release() }; equalizer = null }
                BASS_BOOST -> { runCatching { bassBoost?.release() }; bassBoost = null }
                VIRTUALIZER -> { runCatching { virtualizer?.release() }; virtualizer = null }
                LOUDNESS -> { runCatching { loudness?.release() }; loudness = null }
            }
            failures[name] = e.message ?: e.javaClass.simpleName
            publish()
            onFailure("$name stopped working and was turned off. Playback isn't affected.")
        }
    }

    private fun publish() {
        val eq = equalizer
        hub.publish(
            EffectsCapabilities(
                equalizer = eq != null,
                bandCentersHz = eq?.bandCentersHz.orEmpty(),
                minLevelMb = eq?.minLevelMb ?: 0,
                maxLevelMb = eq?.maxLevelMb ?: 0,
                bassBoost = bassBoost != null,
                virtualizer = virtualizer != null,
                loudness = loudness != null,
                failures = failures.toMap(),
                attached = session != 0,
            )
        )
    }

    companion object {
        private const val TAG = "AudioEffects"
        const val EQUALIZER = "Equalizer"
        const val BASS_BOOST = "Bass boost"
        const val VIRTUALIZER = "Virtualizer"
        const val LOUDNESS = "Loudness"
    }
}
