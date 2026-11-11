package com.svara.app.playback

import android.os.SystemClock
import com.svara.app.domain.model.SleepTimerOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class SleepTimerState(
    /** Elapsed-realtime at which playback pauses, or null. */
    val endsAtElapsedMs: Long? = null,
    val endOfTrack: Boolean = false,
) {
    val isActive: Boolean get() = endsAtElapsedMs != null || endOfTrack
}

/** What the sleep timer controls; implemented by the playback service over ExoPlayer. */
interface SleepTimerTarget {
    fun pause()
    fun setPauseAtEndOfMediaItems(enabled: Boolean)
    /** Sleep fade gain, 1.0 = full volume. */
    fun setFade(volume: Float)
}

/**
 * Pauses playback after a delay or at the end of the current track. Lives in the playback
 * layer: the service attaches its player on create and detaches on destroy. A timed timer is a
 * single suspended coroutine; nothing ticks while it waits.
 */
@Singleton
class SleepTimer internal constructor(private val elapsedRealtime: () -> Long) {

    @Inject constructor() : this({ SystemClock.elapsedRealtime() })

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var target: SleepTimerTarget? = null
    private var scope: CoroutineScope? = null
    private var job: Job? = null

    fun attach(target: SleepTimerTarget, scope: CoroutineScope) {
        this.target = target
        this.scope = scope
    }

    fun detach() {
        cancel()
        target = null
        scope = null
    }

    /**
     * Starts a timer. With [fadeOut], a timed timer ramps the volume down over the final
     * [FADE_MS] before pausing, then restores it for the next time the user presses play.
     */
    fun start(option: SleepTimerOption, fadeOut: Boolean = false) {
        val t = target ?: return
        if (option == SleepTimerOption.END_OF_TRACK) {
            cancel()
            t.setPauseAtEndOfMediaItems(true)
            _state.value = SleepTimerState(endOfTrack = true)
            return
        }
        startMinutes(option.minutes, fadeOut)
    }

    /** A timed timer of any length (the presets and the custom duration). */
    fun startMinutes(minutes: Int, fadeOut: Boolean = false) {
        if (target == null || minutes <= 0) return
        cancel()
        val durationMs = minutes * 60_000L
        _state.value = SleepTimerState(endsAtElapsedMs = elapsedRealtime() + durationMs)
        job = scope?.launch {
            val fadeMs = if (fadeOut) minOf(FADE_MS, durationMs) else 0L
            delay(durationMs - fadeMs)
            if (fadeMs > 0) {
                val steps = fadeMs / FADE_STEP_MS
                for (i in 1..steps) {
                    target?.setFade(1f - i.toFloat() / steps)
                    delay(FADE_STEP_MS)
                }
            }
            target?.pause()
            target?.setFade(1f)
            cancel()
        }
    }

    /** Called by the service when the player paused itself at the end of an item. */
    fun onPausedAtEndOfItem() {
        if (_state.value.endOfTrack) cancel()
    }

    fun cancel() {
        job?.cancel()
        job = null
        if (_state.value.endOfTrack) target?.setPauseAtEndOfMediaItems(false)
        target?.setFade(1f)
        _state.value = SleepTimerState()
    }

    companion object {
        const val FADE_MS = 30_000L
        private const val FADE_STEP_MS = 500L
    }
}
