package com.svara.app.appearance.colour

import com.svara.app.appearance.Argb

/**
 * Colours taken from the current song's cover: the dominant colour and two supporting ones. The
 * album-artwork accent is [accent]; ambient tints use [dominant]. Text never takes its colour
 * from these: it is drawn on a scrim computed by the contrast engine.
 */
data class AmbientColours(val dominant: Argb, val accent: Argb, val support: Argb) {
    companion object {
        /** Fills gaps from what the palette found; null when it found nothing. */
        fun from(dominant: Argb?, accent: Argb?, muted: Argb?): AmbientColours? {
            val d = dominant ?: accent ?: muted ?: return null
            return AmbientColours(dominant = d, accent = accent ?: d, support = muted ?: accent ?: d)
        }
    }
}
