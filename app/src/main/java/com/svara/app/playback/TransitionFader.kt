package com.svara.app.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Fade-out / fade-in between tracks on the single player (no overlap). Only runs while audio
 * plays: one coroutine sleeps until the fade window, then ramps. Nothing is scheduled while paused.
 */
class TransitionFader(
    private val player: Player,
    private val mixer: VolumeMixer,
    private val scope: CoroutineScope,
    private val fadeMs: () -> Long,
    /** True when the next track should join without a fade (e.g. same album with album-gapless on). */
    private val keepGapless: () -> Boolean,
) {
    private var job: Job? = null

    /** Call on play/pause, seeks, item transitions and speed changes. */
    fun reschedule(fadeIn: Boolean = false) {
        job?.cancel()
        val window = fadeMs()
        if (window <= 0 || !player.isPlaying) {
            if (window <= 0) mixer.transition = 1f
            job = null
            return
        }
        job = scope.launch {
            if (fadeIn) ramp(from = 0f, to = 1f, window) else if (mixer.transition < 1f) ramp(mixer.transition, 1f, FADE_RECOVER_MS)
            while (isActive) {
                val duration = player.duration
                if (duration == C.TIME_UNSET || !player.hasNextMediaItem() || keepGapless()) return@launch
                val speed = player.playbackParameters.speed.coerceAtLeast(0.1f)
                val untilFade = ((duration - player.currentPosition - window) / speed).toLong()
                if (untilFade > 0) {
                    delay(untilFade)
                    continue // re-check: the user may have seeked
                }
                val remaining = ((duration - player.currentPosition) / speed).toLong().coerceAtLeast(STEP_MS)
                ramp(mixer.transition, 0f, remaining)
                return@launch
            }
        }
    }

    fun cancel() {
        job?.cancel()
        mixer.transition = 1f
    }

    private suspend fun ramp(from: Float, to: Float, durationMs: Long) {
        val steps = (durationMs / STEP_MS).coerceAtLeast(1)
        for (i in 1..steps) {
            mixer.transition = from + (to - from) * i / steps
            delay(STEP_MS)
        }
    }

    private companion object {
        const val STEP_MS = 50L
        const val FADE_RECOVER_MS = 300L
    }
}
