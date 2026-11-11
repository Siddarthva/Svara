package com.svara.app.ui

import android.os.SystemClock

/**
 * Drops a repeat of the same action within [windowMs], so a double tap on play/pause, next,
 * the heart or "Add to queue" acts once and nothing flickers. Different actions are never
 * blocked by each other.
 */
class TapGuard(
    private val windowMs: Long = WINDOW_MS,
    private val clock: () -> Long = { SystemClock.uptimeMillis() },
) {
    private val last = HashMap<Any, Long>()

    fun accept(key: Any): Boolean {
        val now = clock()
        val previous = last[key]
        if (previous != null && now - previous in 0 until windowMs) return false
        last[key] = now
        return true
    }

    /** Runs [action] unless the same [key] ran within the window. */
    inline fun run(key: Any, action: () -> Unit) {
        if (accept(key)) action()
    }

    companion object {
        const val WINDOW_MS = 300L
    }
}
