package com.svara.app.ui

import android.os.Build
import android.os.Trace
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos

/**
 * Async trace sections for the macrobenchmarks: a section starts at a user action and ends after
 * the first frame that shows its result. Costs nothing unless a trace is being recorded.
 */
object PerfTrace {
    const val PRESET_SWITCH = "PresetSwitch"
    const val NOW_PLAYING_OPEN = "NowPlayingOpen"
    private const val COOKIE = 0

    private val open = mutableSetOf<String>()

    @Synchronized
    fun begin(name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !open.add(name)) return
        Trace.beginAsyncSection(name, COOKIE)
    }

    @Synchronized
    fun end(name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !open.remove(name)) return
        Trace.endAsyncSection(name, COOKIE)
    }
}

/**
 * Ends [name] once the composition keyed on [key] has been drawn: the effect starts after the
 * frame that applied the change, and the next frame callback runs after that frame was produced.
 */
@Composable
fun EndTraceAfterFrame(name: String, key: Any?) {
    LaunchedEffect(key) {
        withFrameNanos { }
        PerfTrace.end(name)
    }
}
