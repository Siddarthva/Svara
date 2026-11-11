package com.svara.app.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.svara.app.domain.model.QueueState
import com.svara.app.domain.model.SleepTimerOption
import com.svara.app.domain.queue.QueueHistory
import com.svara.app.domain.repository.SettingsRepository
import com.svara.app.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

enum class RepeatSetting { OFF, ONE, ALL }

/** One entry of the queue as the UI sees it. [key] is unique even when a song is queued twice. */
data class QueueEntry(
    val key: String,
    val songId: Long,
    /** Index in the player's underlying list (differs from the display position when shuffled). */
    val underlyingIndex: Int,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
)

data class PlaybackState(
    val isConnected: Boolean = false,
    /** The playback service couldn't be reached; the UI offers a retry. */
    val serviceUnavailable: Boolean = false,
    val current: QueueEntry? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeat: RepeatSetting = RepeatSetting.OFF,
    /** Queue in play order. */
    val queue: List<QueueEntry> = emptyList(),
    /** Position of [current] within [queue]. */
    val currentPosition: Int = -1,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
)

data class PlaybackProgress(val positionMs: Long = 0, val durationMs: Long = 0, val bufferedMs: Long = 0)

/** A snackbar message, optionally with one action such as Undo. */
data class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

/** One-off messages for the user (e.g. a missing file was skipped). */
@Singleton
class UserMessages @Inject constructor() {
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()
    fun emit(message: String) {
        _messages.tryEmit(UiMessage(message))
    }
    fun emit(message: String, actionLabel: String, action: () -> Unit) {
        _messages.tryEmit(UiMessage(message, actionLabel, action))
    }
}

/**
 * The UI's only handle on playback. Wraps a [MediaController] connected to [PlaybackService]
 * and exposes a single [state] stream. ViewModels call these methods; composables never touch
 * the player.
 */
@Singleton
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sleepTimer: SleepTimer,
    private val settings: SettingsRepository,
    private val messages: UserMessages,
) {
    private val history = QueueHistory(UNDO_DEPTH)
    private val _undoLabel = MutableStateFlow<String?>(null)

    /** Label of the queue edit that Undo would revert, or null. */
    val undoLabel: StateFlow<String?> = _undoLabel.asStateFlow()
    // MediaController must be used from the thread it was built on: the main thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controller: MediaController? = null
    private var connection: CompletableDeferred<MediaController>? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    val sleepTimerState: StateFlow<SleepTimerState> get() = sleepTimer.state

    private val progressTrigger = MutableStateFlow(0)

    /**
     * Playback position sampled every [intervalMs] while playing; emits once and then waits
     * while paused. Callers pick the slowest rate their UI needs.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun progress(intervalMs: Long): Flow<PlaybackProgress> = combine(
        state.map { it.isPlaying }.distinctUntilChanged(),
        progressTrigger,
    ) { playing, _ -> playing }
        .flatMapLatest { playing ->
            flow {
                emit(readProgress())
                while (playing) {
                    delay(intervalMs)
                    emit(readProgress())
                }
            }
        }
        .distinctUntilChanged()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val queueChanged = events.containsAny(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_MEDIA_METADATA_CHANGED,
            )
            refresh(player, rebuildQueue = queueChanged)
            progressTrigger.value++
        }
    }

    /** Connects to the playback service. Called when the app comes to the foreground. */
    /** True while the app is in the foreground and should hold a controller. */
    private var wanted = false

    fun connect() {
        wanted = true
        if (connection != null) return
        val deferred = CompletableDeferred<MediaController>()
        connection = deferred
        scope.launch {
            try {
                val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                val mc = MediaController.Builder(context, token)
                    .setListener(object : MediaController.Listener {
                        // The service process died or released the session: drop the dead controller and reconnect.
                        override fun onDisconnected(controller: MediaController) = onControllerLost(controller)
                    })
                    .buildAsync()
                    .await()
                if (connection !== deferred) {
                    mc.release()
                    return@launch
                }
                controller = mc
                mc.addListener(listener)
                refresh(mc, rebuildQueue = true)
                deferred.complete(mc)
            } catch (e: Exception) {
                // SecurityException, a session that refused us, or a service that failed to start.
                Log.e(TAG, "Couldn't connect to playback service", e)
                connection = null
                _state.value = _state.value.copy(isConnected = false, serviceUnavailable = true)
                deferred.completeExceptionally(e)
            }
        }
    }

    /** Called from the UI's Retry action. */
    fun retryConnection() {
        _state.value = _state.value.copy(serviceUnavailable = false)
        connect()
    }

    private fun onControllerLost(lost: MediaController) {
        if (controller !== lost) return
        controller = null
        connection = null
        _state.value = _state.value.copy(isConnected = false)
        if (wanted) {
            scope.launch {
                delay(RECONNECT_DELAY_MS)
                if (wanted && connection == null) connect()
            }
        }
    }

    /**
     * Releases the controller when the app goes to the background, so the service can stop
     * itself once playback is paused. Playback itself is unaffected.
     */
    fun disconnect() {
        wanted = false
        connection = null
        controller?.run {
            removeListener(listener)
            release()
        }
        controller = null
        _state.value = _state.value.copy(isConnected = false)
    }

    private fun withController(block: (MediaController) -> Unit) {
        scope.launch {
            val mc = controller ?: run {
                connect()
                try {
                    connection?.await()
                } catch (_: Exception) {
                    null
                }
            }
            if (mc == null) {
                messages.emit("The player isn't available right now. Try again in a moment.")
                return@launch
            }
            try {
                block(mc)
            } catch (e: IllegalStateException) {
                // The controller was released underneath us (service restarting); the next call reconnects.
                Log.w(TAG, "Player command dropped", e)
            }
        }
    }

    // region Commands

    /**
     * Replaces the queue with [songIds] and starts at [startIndex]. [shuffle] forces shuffle on
     * or off; null keeps the user's current setting. Only ids cross to the service.
     */
    fun playQueue(songIds: List<Long>, startIndex: Int = 0, shuffle: Boolean? = null) {
        if (songIds.isEmpty()) return
        if (_state.value.queue.size > 1) record("Replaced the queue")
        val args = Bundle().apply {
            putLongArray(SvaraCommands.ARG_SONG_IDS, songIds.toLongArray())
            putInt(SvaraCommands.ARG_START_INDEX, startIndex.coerceIn(0, songIds.lastIndex))
            putInt(SvaraCommands.ARG_SHUFFLE, when (shuffle) { null -> 0; true -> 1; false -> 2 })
        }
        withController { it.sendCustomCommand(SessionCommand(SvaraCommands.SET_QUEUE, Bundle.EMPTY), args) }
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean? = null) =
        playQueue(songs.map { it.id }, startIndex, shuffle)

    fun shuffleAll(songIds: List<Long>) {
        if (songIds.isNotEmpty()) playQueue(songIds, Random.nextInt(songIds.size), shuffle = true)
    }

    fun togglePlayPause() = withController { mc ->
        if (mc.isPlaying) {
            mc.pause()
        } else {
            if (mc.playbackState == Player.STATE_IDLE) mc.prepare()
            if (mc.playbackState == Player.STATE_ENDED) mc.seekToDefaultPosition()
            mc.play()
        }
    }

    /** Plays the current (or restored) queue; nothing happens when there is none. */
    fun resume() = withController { mc ->
        if (mc.mediaItemCount == 0) return@withController
        if (mc.playbackState == Player.STATE_IDLE) mc.prepare()
        if (mc.playbackState == Player.STATE_ENDED) mc.seekToDefaultPosition()
        mc.play()
    }

    fun skipNext() = withController { it.seekToNext() }
    fun skipPrevious() = withController { it.seekToPrevious() }
    fun seekTo(positionMs: Long) = withController {
        it.seekTo(positionMs)
        progressTrigger.value++
    }

    /** Uses the player's seek increment (Control Studio), same as the lock screen and headset keys. */
    fun seekBack() = withController {
        it.seekBack()
        progressTrigger.value++
    }

    fun seekForward() = withController {
        it.seekForward()
        progressTrigger.value++
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Jumps to the entry at [position] in play order. */
    fun skipToQueuePosition(position: Int) {
        val entry = _state.value.queue.getOrNull(position) ?: return
        withController { mc ->
            // The queue may have changed since the UI drew this row.
            if (entry.underlyingIndex >= mc.mediaItemCount || mc.getMediaItemAt(entry.underlyingIndex).mediaId != entry.key) return@withController
            mc.seekTo(entry.underlyingIndex, 0)
            if (mc.playbackState == Player.STATE_IDLE) mc.prepare()
            mc.play()
        }
    }

    fun playNext(songIds: List<Long>) = addSongs(songIds, playNext = true)
    fun addToQueue(songIds: List<Long>) = addSongs(songIds, playNext = false)

    private fun addSongs(songIds: List<Long>, playNext: Boolean) {
        if (songIds.isEmpty()) return
        record(if (playNext) "Play next" else "Add to queue")
        val args = Bundle().apply {
            putLongArray(SvaraCommands.ARG_SONG_IDS, songIds.toLongArray())
            putBoolean(SvaraCommands.ARG_PLAY_NEXT, playNext)
        }
        withController { it.sendCustomCommand(SessionCommand(SvaraCommands.ADD_SONGS, Bundle.EMPTY), args) }
    }

    /** Moves an entry between two positions in play order. */
    fun moveQueueItem(from: Int, to: Int) {
        if (from == to) return
        record("Moved “${_state.value.queue.getOrNull(from)?.title}”")
        val args = Bundle().apply {
            putInt(SvaraCommands.ARG_FROM, from)
            putInt(SvaraCommands.ARG_TO, to)
        }
        withController { it.sendCustomCommand(SessionCommand(SvaraCommands.MOVE, Bundle.EMPTY), args) }
    }

    fun removeQueueItem(position: Int) {
        val entry = _state.value.queue.getOrNull(position) ?: return
        record("Removed “${entry.title}”", offerUndo = true)
        withController { mc ->
            if (entry.underlyingIndex < mc.mediaItemCount && mc.getMediaItemAt(entry.underlyingIndex).mediaId == entry.key) {
                mc.removeMediaItem(entry.underlyingIndex)
            }
        }
    }

    /** Removes everything except the song that's playing; clears completely when nothing is. */
    fun clearQueue() {
        record("Cleared the queue", offerUndo = true)
        clearQueueInternal()
    }

    private fun clearQueueInternal() = withController { mc ->
        val current = mc.currentMediaItemIndex
        if (mc.isPlaying && current in 0 until mc.mediaItemCount) {
            if (current + 1 < mc.mediaItemCount) mc.removeMediaItems(current + 1, mc.mediaItemCount)
            if (current > 0) mc.removeMediaItems(0, current)
        } else {
            mc.stop()
            mc.clearMediaItems()
        }
    }

    /** Speed 0.5–2.0×. With pitch preservation on, pitch stays at 1.0; otherwise it follows speed. */
    fun setSpeed(speed: Float) = withController {
        val s = speed.coerceIn(0.5f, 2f)
        it.playbackParameters = PlaybackParameters(s, if (settings.settings.value.preservePitch) 1f else s)
    }

    fun setPitch(pitch: Float) = withController {
        it.playbackParameters = PlaybackParameters(it.playbackParameters.speed, pitch.coerceIn(0.5f, 2f))
    }

    fun startSleepTimer(option: SleepTimerOption) = withController { sleepTimer.start(option, settings.settings.value.sleepFadeOut) }
    fun startSleepTimerMinutes(minutes: Int) = withController { sleepTimer.startMinutes(minutes, settings.settings.value.sleepFadeOut) }
    fun cancelSleepTimer() = sleepTimer.cancel()

    /** Song ids of the queue in play order (for "Save queue as playlist"). */
    fun queueSongIdsInPlayOrder(): List<Long> = _state.value.queue.map { it.songId }

    /** Reverts the most recent queue edit. */
    fun undoQueueChange() {
        val entry = history.pop() ?: return
        _undoLabel.value = history.latest?.label
        val s = entry.before
        val args = Bundle().apply {
            putLongArray(SvaraCommands.ARG_SONG_IDS, s.songIds.toLongArray())
            putIntArray(SvaraCommands.ARG_PLAY_ORDER, s.playOrder.toIntArray())
            putInt(SvaraCommands.ARG_START_INDEX, s.currentIndex)
            putLong(SvaraCommands.ARG_POSITION, s.positionMs)
            putBoolean(SvaraCommands.ARG_SHUFFLE_ON, s.shuffle)
        }
        withController { it.sendCustomCommand(SessionCommand(SvaraCommands.RESTORE_QUEUE, Bundle.EMPTY), args) }
    }

    /** Saves the queue as it is now so the next edit can be undone. */
    private fun record(label: String, offerUndo: Boolean = false) {
        val state = captureQueue() ?: return
        history.push(label, state)
        _undoLabel.value = label
        if (offerUndo) messages.emit(label, "Undo") { undoQueueChange() }
    }

    private fun captureQueue(): QueueState? {
        val st = _state.value
        if (st.queue.isEmpty()) return null
        val underlying = st.queue.sortedBy { it.underlyingIndex }
        return QueueState(
            songIds = underlying.map { it.songId },
            playOrder = st.queue.map { it.underlyingIndex },
            currentIndex = st.current?.underlyingIndex ?: 0,
            positionMs = controller?.currentPosition ?: 0L,
            shuffle = st.shuffleEnabled,
        )
    }

    // endregion

    private fun refresh(player: Player, rebuildQueue: Boolean) {
        val old = _state.value
        val queue = if (rebuildQueue || !old.isConnected) buildQueue(player) else old.queue
        val currentKey = player.currentMediaItem?.mediaId
        val position = if (currentKey == null) -1 else queue.indexOfFirst { it.key == currentKey }
        _state.value = PlaybackState(
            isConnected = true,
            serviceUnavailable = false,
            current = queue.getOrNull(position),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            shuffleEnabled = player.shuffleModeEnabled,
            repeat = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatSetting.ONE
                Player.REPEAT_MODE_ALL -> RepeatSetting.ALL
                else -> RepeatSetting.OFF
            },
            queue = queue,
            currentPosition = position,
            speed = player.playbackParameters.speed,
            pitch = player.playbackParameters.pitch,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.mediaItemCount > 0,
        )
    }

    private fun buildQueue(player: Player): List<QueueEntry> {
        val order = player.currentTimeline.playOrder(player.shuffleModeEnabled)
        return order.map { index -> player.getMediaItemAt(index).toEntry(index) }
    }

    private fun MediaItem.toEntry(index: Int): QueueEntry {
        val md = mediaMetadata
        return QueueEntry(
            key = mediaId,
            songId = MediaItems.songIdOf(mediaId) ?: -1,
            underlyingIndex = index,
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            album = md.albumTitle?.toString().orEmpty(),
            albumId = md.extras?.getLong(MediaItems.EXTRA_ALBUM_ID, -1L) ?: -1L,
            durationMs = md.durationMs ?: 0L,
        )
    }

    private fun readProgress(): PlaybackProgress {
        val mc = controller ?: return PlaybackProgress()
        val duration = mc.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: (_state.value.current?.durationMs ?: 0L)
        return PlaybackProgress(mc.currentPosition.coerceAtLeast(0), duration, mc.bufferedPosition.coerceAtLeast(0))
    }

    private companion object {
        const val TAG = "PlaybackController"
        const val UNDO_DEPTH = 20
        const val RECONNECT_DELAY_MS = 500L
    }
}
