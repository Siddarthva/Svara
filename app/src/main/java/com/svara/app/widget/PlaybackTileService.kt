package com.svara.app.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.svara.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick settings tile: play or pause SVARĀ. Shows the last known state from the widget snapshot
 * while the shade is open (no background work otherwise). Taps go to the real MediaSession.
 */
class PlaybackTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { render(applicationContext.widgetEntry().store().read()) }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            WidgetCommands.playPause(applicationContext)
            render(applicationContext.widgetEntry().store().read())
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(s: NowPlayingSnapshot) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_label)
        tile.state = when {
            s.empty -> Tile.STATE_INACTIVE
            s.isPlaying -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.icon = Icon.createWithResource(this, if (s.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
        tile.contentDescription = when {
            s.empty -> "SVARĀ: nothing queued"
            s.isPlaying -> "Pause ${s.title}"
            else -> "Play ${s.title}"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = if (s.empty) "Nothing queued" else s.title
        tile.updateTile()
    }

    companion object {
        /** Asks the system to refresh the tile if it's showing; harmless when it isn't added. */
        fun requestUpdate(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, PlaybackTileService::class.java)) }
        }
    }
}
