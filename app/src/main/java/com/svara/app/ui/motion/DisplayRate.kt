package com.svara.app.ui.motion

import android.app.Activity
import android.os.Build
import android.view.Display

/** One display mode, reduced to what choosing a refresh rate needs. */
data class DisplayModeSpec(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

/**
 * Smooth display: asks for the highest refresh rate the screen offers at its current resolution.
 * Only a preference; the system still lowers it for battery saver, low battery or heat, and the
 * app never fights that. Never asks for a mode that changes the resolution. Off clears the
 * request so the system chooses.
 */
object DisplayRate {

    /**
     * The mode to ask for: the fastest one at [current]'s resolution, even when it is already the
     * current mode (it may be current only because of this request, and dropping the request
     * would let the rate fall back). [NO_PREFERENCE] when [enabled] is off or nothing matches.
     */
    fun pick(modes: List<DisplayModeSpec>, current: DisplayModeSpec, enabled: Boolean): Int {
        if (!enabled) return NO_PREFERENCE
        return modes
            .filter { it.width == current.width && it.height == current.height }
            .maxByOrNull { it.refreshRate }?.id ?: NO_PREFERENCE
    }

    /** Highest rate at the current resolution, for Android 10 and older; 0 means no preference. */
    fun pickRate(modes: List<DisplayModeSpec>, current: DisplayModeSpec, enabled: Boolean): Float =
        if (!enabled) 0f
        else modes.filter { it.width == current.width && it.height == current.height }.maxOfOrNull { it.refreshRate } ?: 0f

    /** Applies the choice to the activity's window; writes the attributes only when they change. */
    fun apply(activity: Activity, enabled: Boolean) {
        val window = activity.window
        @Suppress("DEPRECATION")
        val display: Display = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display else null)
            ?: activity.windowManager.defaultDisplay
        val current = display.mode.spec()
        val modes = display.supportedModes.map { it.spec() }
        val attrs = window.attributes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val id = pick(modes, current, enabled)
            if (attrs.preferredDisplayModeId == id) return
            attrs.preferredDisplayModeId = id
        } else {
            val rate = pickRate(modes, current, enabled)
            if (attrs.preferredRefreshRate == rate) return
            attrs.preferredRefreshRate = rate
        }
        window.attributes = attrs
    }

    private fun Display.Mode.spec() = DisplayModeSpec(modeId, physicalWidth, physicalHeight, refreshRate)

    const val NO_PREFERENCE = 0
}
