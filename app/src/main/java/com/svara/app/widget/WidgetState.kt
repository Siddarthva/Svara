package com.svara.app.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the widgets and the quick settings tile show: the last known playback state, written by
 * the playback service when the track, play state or favourite changes (never on a timer).
 * Survives the service and the process, so widgets show something sensible when nothing runs.
 */
data class NowPlayingSnapshot(
    val songId: Long = -1,
    val albumId: Long = -1,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val favourite: Boolean = false,
    /** Up to three upcoming titles for the large widget's queue preview. */
    val upNext: List<String> = emptyList(),
    val updatedAt: Long = 0,
) {
    val empty: Boolean get() = songId <= 0 || title.isEmpty()
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

private val Context.widgetStore by preferencesDataStore("widget_state")

@Singleton
class WidgetStateStore @Inject constructor(@ApplicationContext private val context: Context) {

    val snapshot = context.widgetStore.data.map { p ->
        NowPlayingSnapshot(
            songId = p[SONG] ?: -1,
            albumId = p[ALBUM] ?: -1,
            title = p[TITLE].orEmpty(),
            artist = p[ARTIST].orEmpty(),
            isPlaying = p[PLAYING] ?: false,
            hasNext = p[NEXT] ?: false,
            hasPrevious = p[PREVIOUS] ?: false,
            positionMs = p[POSITION] ?: 0,
            durationMs = p[DURATION] ?: 0,
            favourite = p[FAVOURITE] ?: false,
            upNext = p[UP_NEXT]?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty(),
            updatedAt = p[UPDATED] ?: 0,
        )
    }

    suspend fun read(): NowPlayingSnapshot = snapshot.first()

    suspend fun write(s: NowPlayingSnapshot) {
        context.widgetStore.edit { p ->
            p[SONG] = s.songId; p[ALBUM] = s.albumId; p[TITLE] = s.title; p[ARTIST] = s.artist
            p[PLAYING] = s.isPlaying; p[NEXT] = s.hasNext; p[PREVIOUS] = s.hasPrevious
            p[POSITION] = s.positionMs; p[DURATION] = s.durationMs; p[FAVOURITE] = s.favourite
            p[UP_NEXT] = s.upNext.joinToString(SEPARATOR); p[UPDATED] = s.updatedAt
        }
    }

    private companion object {
        val SONG = longPreferencesKey("song")
        val ALBUM = longPreferencesKey("album")
        val TITLE = stringPreferencesKey("title")
        val ARTIST = stringPreferencesKey("artist")
        val PLAYING = booleanPreferencesKey("playing")
        val NEXT = booleanPreferencesKey("next")
        val PREVIOUS = booleanPreferencesKey("previous")
        val POSITION = longPreferencesKey("position")
        val DURATION = longPreferencesKey("duration")
        val FAVOURITE = booleanPreferencesKey("favourite")
        val UP_NEXT = stringPreferencesKey("upNext")
        val UPDATED = longPreferencesKey("updated")
        const val SEPARATOR = "\u001F"
    }
}

/** Pushes a new snapshot to every installed widget and the tile. Does nothing when none are installed. */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: WidgetStateStore,
) {
    /** Writes [snapshot] if it changed what a widget shows, then redraws installed widgets. */
    suspend fun publish(snapshot: NowPlayingSnapshot) {
        val previous = store.read()
        if (previous.copy(updatedAt = 0, positionMs = 0) == snapshot.copy(updatedAt = 0, positionMs = 0) &&
            kotlin.math.abs(previous.positionMs - snapshot.positionMs) < MIN_PROGRESS_STEP_MS
        ) return
        store.write(snapshot)
        refresh()
        PlaybackTileService.requestUpdate(context)
    }

    /** Redraws installed widgets with the stored snapshot (appearance changed, settings saved). */
    suspend fun refresh() {
        if (!hasWidgets()) return
        SmallWidget().updateAll(context)
        MediumWidget().updateAll(context)
        LargeWidget().updateAll(context)
    }

    private suspend fun hasWidgets(): Boolean {
        val manager = GlanceAppWidgetManager(context)
        return listOf(SmallWidget::class.java, MediumWidget::class.java, LargeWidget::class.java)
            .any { manager.getGlanceIds(it).isNotEmpty() }
    }

    private companion object {
        /** Progress alone never triggers a redraw unless it moved this much (seek, track end). */
        const val MIN_PROGRESS_STEP_MS = 15_000L
    }
}
