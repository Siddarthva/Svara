package com.svara.app.settings

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.svara.app.data.covers.CoverOverrides
import com.svara.app.data.covers.CoverRepository
import com.svara.app.data.covers.CoverResult
import com.svara.app.data.covers.CoverRules
import com.svara.app.data.covers.CoverScope
import com.svara.app.playback.UserMessages
import com.svara.app.ui.components.ArtworkImage
import com.svara.app.ui.components.BackButton
import com.svara.app.ui.navigation.Navigator
import com.svara.app.ui.theme.Radii
import com.svara.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoverEditorUi(
    val image: Bitmap? = null,
    /** 0..1 along the image's long edge. */
    val offset: Float = 0.5f,
    val scope: CoverScope = CoverScope.ALBUM,
    val busy: Boolean = false,
    val unreadable: Boolean = false,
)

@HiltViewModel
class CoverEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: CoverRepository,
    private val covers: CoverOverrides,
    private val messages: UserMessages,
) : ViewModel() {
    val songId: Long = savedState.get<Long>("songId") ?: -1L
    val albumId: Long = savedState.get<Long>("albumId") ?: -1L
    val albumAvailable: Boolean get() = albumId > 0

    private val _ui = MutableStateFlow(CoverEditorUi(scope = if (albumId > 0) CoverScope.ALBUM else CoverScope.SONG))
    val ui: StateFlow<CoverEditorUi> = _ui.asStateFlow()

    /** The custom cover for the chosen scope, if there is one (for "Remove custom cover"). */
    fun hasCustom(scope: CoverScope): Boolean = when (scope) {
        CoverScope.SONG -> covers.covers.value[CoverRules.songKey(songId)] != null
        CoverScope.ALBUM -> covers.covers.value[CoverRules.albumKey(albumId)] != null
    }

    fun picked(uri: Uri?) {
        // A cancelled pick returns null: nothing changes.
        if (uri == null) return
        _ui.update { it.copy(busy = true, unreadable = false) }
        viewModelScope.launch {
            val image = repository.load(uri)
            _ui.update { it.copy(image = image, offset = DEFAULT_OFFSET, busy = false, unreadable = image == null) }
        }
    }

    fun move(offset: Float) = _ui.update { it.copy(offset = offset.coerceIn(0f, 1f)) }
    fun scope(scope: CoverScope) = _ui.update { it.copy(scope = scope) }

    fun save(done: () -> Unit) {
        val state = _ui.value
        val image = state.image ?: return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            val key = if (state.scope == CoverScope.ALBUM) albumId else songId
            when (val r = repository.save(image, state.offset, state.scope, key)) {
                CoverResult.Saved -> { messages.emit(if (state.scope == CoverScope.ALBUM) "Album cover changed. Playback unchanged." else "Song cover changed. Playback unchanged."); done() }
                is CoverResult.Failed -> { messages.emit(r.message); _ui.update { it.copy(busy = false) } }
                CoverResult.Removed -> done()
            }
        }
    }

    fun remove(done: () -> Unit) {
        val scope = _ui.value.scope
        viewModelScope.launch {
            repository.remove(scope, if (scope == CoverScope.ALBUM) albumId else songId)
            messages.emit("Custom cover removed. The original artwork is back.")
            done()
        }
    }

    private companion object {
        const val DEFAULT_OFFSET = 0.5f
    }
}

/**
 * Change cover: pick an image (system photo picker, or the document picker where it isn't
 * available), adjust the square crop by dragging, choose this song or the whole album, and save.
 * Only app-private data changes; the audio file is never written.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverEditorScreen(navigator: Navigator, viewModel: CoverEditorViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), viewModel::picked)
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::picked)
    val choose = {
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            documentPicker.launch(arrayOf("image/*"))
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Change cover") }, navigationIcon = { BackButton(navigator::back) }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                "Your audio file is not changed. The cover is stored only inside SVARĀ.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(Radii.medium)), contentAlignment = Alignment.Center) {
                val image = ui.image
                when {
                    ui.busy -> CircularProgressIndicator()
                    image != null -> CropPreview(image, ui.offset, viewModel::move)
                    else -> ArtworkImage(viewModel.songId, viewModel.albumId, Modifier.fillMaxSize(), iconSize = Spacing.xxl)
                }
            }
            if (ui.unreadable) Text("That image couldn't be read. Try another one.", color = MaterialTheme.colorScheme.error)
            if (ui.image != null) Text("Drag the image to choose the part that shows.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = choose, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touch)) {
                Text(if (ui.image == null) "Choose image" else "Choose another image")
            }
            Text("Use for", style = MaterialTheme.typography.titleSmall)
            Column(Modifier.selectableGroup()) {
                ScopeOption("This song only", ui.scope == CoverScope.SONG, enabled = true) { viewModel.scope(CoverScope.SONG) }
                ScopeOption("Whole album", ui.scope == CoverScope.ALBUM, enabled = viewModel.albumAvailable) { viewModel.scope(CoverScope.ALBUM) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                TextButton(onClick = navigator::back, modifier = Modifier.heightIn(min = Spacing.touch)) { Text("Cancel") }
                Button(onClick = { viewModel.save(navigator::back) }, enabled = ui.image != null && !ui.busy, modifier = Modifier.heightIn(min = Spacing.touch)) {
                    Text("Use this cover")
                }
            }
            if (viewModel.hasCustom(ui.scope)) {
                TextButton(onClick = { viewModel.remove(navigator::back) }, modifier = Modifier.heightIn(min = Spacing.touch)) {
                    Text(if (ui.scope == CoverScope.ALBUM) "Remove custom album cover" else "Remove custom song cover")
                }
            }
        }
    }
}

@Composable
private fun ScopeOption(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.touch).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, modifier = Modifier.padding(start = Spacing.s))
    }
}

/** The square crop, positioned by dragging along the image's long edge. */
@Composable
private fun CropPreview(image: Bitmap, offset: Float, onMove: (Float) -> Unit) {
    val bitmap = androidx.compose.runtime.remember(image) { image.asImageBitmap() }
    val landscape = image.width >= image.height
    Canvas(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Cover preview. Drag to adjust the crop." }
            .pointerInput(image) {
                var current = offset
                detectDragGestures(onDragStart = { current = offset }) { change, drag ->
                    change.consume()
                    val slackPx = (if (landscape) image.width - image.height else image.height - image.width).toFloat()
                    if (slackPx <= 0f) return@detectDragGestures
                    val scale = minOf(image.width, image.height) / size.width.toFloat()
                    val delta = (if (landscape) -drag.x else -drag.y) * scale / slackPx
                    current = (current + delta).coerceIn(0f, 1f)
                    onMove(current)
                }
            },
    ) {
        val (x, y, side, _) = CoverRules.squareCrop(image.width, image.height, offset).toList()
        drawImage(bitmap, srcOffset = IntOffset(x, y), srcSize = IntSize(side, side), dstSize = IntSize(size.width.toInt(), size.height.toInt()), dstOffset = IntOffset.Zero)
    }
}
