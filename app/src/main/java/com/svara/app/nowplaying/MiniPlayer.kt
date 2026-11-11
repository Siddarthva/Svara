package com.svara.app.nowplaying

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import com.svara.app.ui.theme.Spacing
import com.svara.app.appearance.tokens.WaveTokens
import com.svara.app.ui.motion.AmbientWave
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.svara.app.ui.presets.MiniPlayerModel
import com.svara.app.ui.presets.PresetMiniPlayer
import com.svara.app.appearance.PresetId
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.presets.raga.RagaMiniPlayer
import com.svara.app.ui.presets.shruti.ShrutiMiniPlayer
import com.svara.app.ui.presets.spanda.SpandaMiniPlayer
import com.svara.app.ui.presets.tala.TalaMiniPlayer
import com.svara.app.ui.theme.LocalAppearance

/** Strip above the navigation bar. Its look comes from the preset; it stays the same ViewModel and controller. */
@Composable
fun MiniPlayer(onOpen: () -> Unit, navigator: Navigator? = null, viewModel: MiniPlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Read only inside the progress lambda (draw phase), so ticks never recompose the strip.
    val progress = viewModel.progress.collectAsStateWithLifecycle()
    if (state.serviceUnavailable) {
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Can't reach the player",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::retry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Retry") }
            }
        }
        return
    }
    val current = state.current
    val model = MiniPlayerModel(
            songId = current?.songId ?: -1,
            albumId = current?.albumId ?: -1,
            title = current?.title,
            artist = current?.artist.orEmpty(),
            isPlaying = state.isPlaying,
            hasNext = state.hasNext,
            progress = { progress.value.let { p -> if (p.durationMs > 0) (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f) else 0f } },
        )
    // A clear gap above (between the list's last row and the player) and below (before the bar).
    Column {
    if (current != null) Spacer(Modifier.height(Spacing.s))
    Box {
    when (LocalAppearance.current.preset) {
        PresetId.ANAHATA -> PresetMiniPlayer(model, onOpen, viewModel::togglePlayPause, viewModel::skipNext, viewModel::skipPrevious)
        PresetId.SHRUTI -> ShrutiMiniPlayer(model, onOpen, viewModel::togglePlayPause, viewModel::skipNext, viewModel::skipPrevious)
        PresetId.TALA -> TalaMiniPlayer(model, navigator, onOpen, viewModel::togglePlayPause, viewModel::skipNext)
        PresetId.RAGA -> RagaMiniPlayer(model, onOpen, viewModel::togglePlayPause, viewModel::skipNext)
        PresetId.SPANDA -> SpandaMiniPlayer(model, onOpen, viewModel::togglePlayPause, viewModel::skipNext, viewModel::skipPrevious)
    }
        // Thin live wave on the top edge while playing.
        if (current != null) AmbientWave(state.isPlaying, Modifier.fillMaxWidth().height(WaveTokens.STRIP_DP.dp))
    }
    if (current != null) Spacer(Modifier.height(Spacing.s))
    }
}
