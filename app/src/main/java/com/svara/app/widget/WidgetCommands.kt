package com.svara.app.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.svara.app.MainActivity
import com.svara.app.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** One widget's own choices, stored per widget in its Glance state. */
data class WidgetSettings(
    val showArtwork: Boolean = true,
    val background: Background = Background.TRANSLUCENT,
    val accent: AccentSource = AccentSource.CUSTOM,
    val compact: Boolean = false,
    val showText: Boolean = true,
    val showControls: Boolean = true,
) {
    enum class Background(val label: String) { TRANSPARENT("Transparent"), TRANSLUCENT("Translucent"), SOLID("Solid") }

    /** PRESET: the preset's own accent. CUSTOM: whatever colour SVARĀ uses now (custom accent included). ARTWORK: the cover's colour. */
    enum class AccentSource(val label: String) { PRESET("Preset"), CUSTOM("Current app colour"), ARTWORK("Album artwork") }

    companion object {
        val ART = booleanPreferencesKey("w.art")
        val BACKGROUND = stringPreferencesKey("w.background")
        val ACCENT = stringPreferencesKey("w.accent")
        val COMPACT = booleanPreferencesKey("w.compact")
        val TEXT = booleanPreferencesKey("w.text")
        val CONTROLS = booleanPreferencesKey("w.controls")

        fun from(p: Preferences): WidgetSettings {
            val d = WidgetSettings()
            return WidgetSettings(
                showArtwork = p[ART] ?: d.showArtwork,
                background = p[BACKGROUND]?.let { n -> Background.entries.firstOrNull { it.name == n } } ?: d.background,
                accent = p[ACCENT]?.let { n -> AccentSource.entries.firstOrNull { it.name == n } } ?: d.accent,
                compact = p[COMPACT] ?: d.compact,
                showText = p[TEXT] ?: d.showText,
                showControls = p[CONTROLS] ?: d.showControls,
            )
        }
    }
}

/**
 * Widget and tile commands go to the real MediaSession through a short-lived MediaController,
 * so they work when the app isn't visible or its process isn't running: connecting starts the
 * playback service, which restores the saved queue. With nothing to play, the app opens instead.
 */
object WidgetCommands {

    suspend fun playPause(context: Context) = withController(context) { c ->
        if (c.mediaItemCount == 0) {
            // A fresh service restores the saved queue shortly after connecting.
            withTimeoutOrNull(RESTORE_WAIT_MS) { while (c.mediaItemCount == 0) delay(POLL_MS) }
        }
        when {
            c.mediaItemCount == 0 -> openApp(context)
            c.isPlaying -> c.pause()
            else -> {
                if (c.playbackState == androidx.media3.common.Player.STATE_IDLE) c.prepare()
                c.play()
            }
        }
    }

    suspend fun next(context: Context) = withController(context) { c -> if (c.hasNextMediaItem()) c.seekToNextMediaItem() }

    suspend fun previous(context: Context) = withController(context) { c -> c.seekToPrevious() }

    suspend fun toggleFavourite(context: Context) {
        val entry = context.widgetEntry()
        val s = entry.store().read()
        if (s.empty) return
        entry.favourites().toggle(s.songId)
        // The service republishes on the change; reflect it at once for when it isn't running.
        entry.updater().publish(s.copy(favourite = !s.favourite, updatedAt = System.currentTimeMillis()))
    }

    private suspend fun withController(context: Context, block: suspend (MediaController) -> Unit) = withContext(Dispatchers.Main) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controller = runCatching { MediaController.Builder(context, token).buildAsync().await() }.getOrNull()
        if (controller == null) {
            openApp(context)
            return@withContext
        }
        try {
            block(controller)
        } finally {
            controller.release()
        }
    }

    private fun openApp(context: Context) {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private const val RESTORE_WAIT_MS = 3_000L
    private const val POLL_MS = 50L
}
