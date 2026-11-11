package com.svara.app.playback

import androidx.media3.session.CommandButton
import com.svara.app.domain.repository.FavoritesRepository
import com.svara.app.widget.WidgetUpdater
import com.svara.app.widget.NowPlayingSnapshot
import com.svara.app.playback.effects.AudioEffectsController
import com.svara.app.playback.effects.AudioEffectsHub
import com.svara.app.playback.effects.PlatformEffectFactory
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import com.svara.app.data.covers.CoverOverrides
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import android.content.Context
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.svara.app.data.appearance.CustomizationRepository
import com.svara.app.playback.analysis.AudioAnalysis
import com.svara.app.playback.analysis.SpectrumAudioProcessor
import java.util.concurrent.atomic.AtomicInteger
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.svara.app.MainActivity
import com.svara.app.R
import com.svara.app.data.artwork.ArtworkLoader
import com.svara.app.data.artwork.SvaraBitmapLoader
import com.svara.app.di.ApplicationScope
import com.svara.app.domain.model.QueueSnapshot
import com.svara.app.domain.model.QueueState
import com.svara.app.playback.rituals.RitualsController
import com.svara.app.domain.queue.QueueMath
import com.svara.app.domain.queue.QueueRestore
import com.svara.app.domain.repository.HistoryRepository
import com.svara.app.domain.repository.LibraryRepository
import com.svara.app.domain.repository.QueueSnapshotRepository
import com.svara.app.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import javax.inject.Inject
import kotlin.random.Random

/**
 * Hosts the app's single ExoPlayer inside a MediaSession. Everything else — the UI, the
 * notification, Bluetooth and the lock screen — controls playback through this session, so a UI
 * change (including a theme switch) can never create a second player or interrupt audio.
 */
@AndroidEntryPoint
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    @Inject lateinit var library: LibraryRepository
    @Inject lateinit var snapshots: QueueSnapshotRepository
    @Inject lateinit var history: HistoryRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var sleepTimer: SleepTimer
    @Inject lateinit var events: UserMessages
    @Inject lateinit var artworkLoader: ArtworkLoader
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var customization: CustomizationRepository
    @Inject lateinit var analysis: AudioAnalysis
    @Inject lateinit var covers: CoverOverrides
    @Inject lateinit var effectsHub: AudioEffectsHub
    @Inject lateinit var widgetUpdater: WidgetUpdater
    @Inject lateinit var favourites: FavoritesRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var exoPlayer: ExoPlayer
    /** Equalizer and other platform effects on this player's audio session (V3). */
    private lateinit var effects: AudioEffectsController
    private lateinit var player: SvaraPlayer
    private var session: MediaSession? = null

    /** Saves are suppressed until the saved queue has been restored, so it's never overwritten with an empty one. */
    private var restoreComplete = false
    private var saveJob: Job? = null

    private var listenedMs = 0L
    private var playRecorded = false
    /** The history row of the current play, once it counts; its listening time is filled in as it goes. */
    private var playEvent: Deferred<Long>? = null
    private var favouriteIds: Set<Long> = emptySet()
    private var widgetJob: Job? = null
    private val multiPress = MultiPressDetector()

    private lateinit var mixer: VolumeMixer
    private lateinit var fader: TransitionFader
    private lateinit var rituals: RitualsController

    override fun onCreate() {
        super.onCreate()
        exoPlayer = ExoPlayer.Builder(this, AnalysisRenderersFactory(this, analysis))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // Local files need far less read-ahead than streaming; see LOAD_* constants.
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(LOAD_MIN_BUFFER_MS, LOAD_MAX_BUFFER_MS, LOAD_PLAYBACK_MS, LOAD_REBUFFER_MS)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
            )
            // No app wake lock. Media3 1.11 defaults to a local wake lock unless told otherwise, so
            // opt out explicitly: the foreground media session keeps the process alive, and the audio
            // output path keeps the CPU awake only while audio is actually being rendered.
            .setWakeMode(C.WAKE_MODE_NONE)
            .build()
        playersCreated.incrementAndGet()
        player = SvaraPlayer(exoPlayer)

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setSessionActivity(openApp)
            .setBitmapLoader(SvaraBitmapLoader(this, artworkLoader, covers))
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_stat_svara) }
        )

        mixer = VolumeMixer(exoPlayer)
        fader = TransitionFader(
            player = exoPlayer,
            mixer = mixer,
            scope = scope,
            fadeMs = { settings.settings.value.crossfadeSec * 1000L },
            keepGapless = { settings.settings.value.gaplessAlbums && nextIsSameAlbum() },
        )
        rituals = RitualsController(this, exoPlayer, { settings.settings.value }, events).also { it.start() }
        // Effects attach to the player's own session; any effect that fails is turned off on its own.
        effects = AudioEffectsController(PlatformEffectFactory(), effectsHub) { events.emit(it) }
        effects.attach(exoPlayer.audioSessionId)

        exoPlayer.addListener(PlayerEvents())
        sleepTimer.attach(object : SleepTimerTarget {
            override fun pause() = exoPlayer.pause()
            override fun setPauseAtEndOfMediaItems(enabled: Boolean) {
                exoPlayer.pauseAtEndOfMediaItems = enabled
            }
            override fun setFade(volume: Float) {
                mixer.sleep = volume
            }
        }, scope)
        observeSettings()
        scope.launch {
            favourites.favoriteIds.collect { ids ->
                favouriteIds = ids
                updateFavouriteButton()
                publishWidget()
            }
        }
        scope.launch { restoreQueue() }
        // A changed custom cover: rebuild the notification so it shows at once. Only the
        // notification is updated; the player and the queue are not touched.
        scope.launch {
            covers.covers.drop(1).collect {
                val s = session ?: return@collect
                if (s.player.currentMediaItem != null) onUpdateNotification(s, false)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing in the background; only shut down when nothing is playing.
        if (!isPlaybackOngoing) pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        flushListened(endOfPlay = true)
        // Widgets show the last state, paused, once the service is gone.
        val last = widgetSnapshot().copy(isPlaying = false)
        appScope.launch { widgetUpdater.publish(last) }
        if (restoreComplete) {
            val snapshot = currentSnapshot()
            appScope.launch { snapshots.save(snapshot) }
        }
        sleepTimer.detach()
        effects.release()
        rituals.stop()
        fader.cancel()
        scope.cancel()
        session?.run {
            release()
            session = null
        }
        exoPlayer.release()
        super.onDestroy()
    }

    // region Queue restore & persistence

    private suspend fun restoreQueue() {
        try {
            if (exoPlayer.mediaItemCount > 0 || !settings.settings.value.restoreQueueOnLaunch) return
            val t0 = System.nanoTime()
            val restored = withContext(Dispatchers.Default) { loadRestorableQueue() } ?: return
            val t1 = System.nanoTime()
            if (exoPlayer.mediaItemCount > 0) return // a controller started something while we were loading
            installQueue(restored.items, restored.index, restored.positionMs)
            Log.i(TAG, "Restored ${restored.items.size} items: load ${(t1 - t0) / 1_000_000} ms (background), install ${(System.nanoTime() - t1) / 1_000_000} ms")
            exoPlayer.repeatMode = restored.snapshot.repeatMode
            exoPlayer.playbackParameters = PlaybackParameters(restored.snapshot.speed, restored.snapshot.pitch)
            if (restored.snapshot.shuffleEnabled) {
                if (QueueMath.isValidOrder(restored.shuffleOrder, restored.items.size)) {
                    exoPlayer.setShuffleOrder(DefaultShuffleOrder(restored.shuffleOrder.toIntArray(), Random.nextLong()))
                }
                exoPlayer.shuffleModeEnabled = true
            }
            // Left in STATE_IDLE on purpose: no notification appears until the user presses play.
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't restore queue", e)
        } finally {
            restoreComplete = true
        }
    }

    private class Restorable(val snapshot: QueueSnapshot, val items: List<MediaItem>, val index: Int, val positionMs: Long, val shuffleOrder: List<Int>)

    private suspend fun loadRestorableQueue(): Restorable? {
        val snapshot = snapshots.load()
        if (snapshot.songIds.isEmpty()) return null
        val songs = library.songsByIds(snapshot.songIds).associateBy { it.id }
        val restored = QueueRestore.restore(snapshot.songIds, snapshot.currentIndex, snapshot.positionMs, snapshot.shuffleOrder) { it in songs }
        if (restored.songIds.isEmpty()) return null
        val items = restored.songIds.mapNotNull { id -> songs[id]?.let(MediaItems::fromSong) }
        if (items.size != restored.songIds.size) return null
        return Restorable(snapshot, items, restored.currentIndex, restored.positionMs, restored.shuffleOrder)
    }

    private fun currentSnapshot(): QueueSnapshot {
        val ids = (0 until exoPlayer.mediaItemCount).mapNotNull { MediaItems.songIdOf(exoPlayer.getMediaItemAt(it).mediaId) }
        val consistent = ids.size == exoPlayer.mediaItemCount
        return QueueSnapshot(
            songIds = if (consistent) ids else emptyList(),
            currentIndex = exoPlayer.currentMediaItemIndex.coerceAtLeast(0),
            // A finished queue resumes from the top of the current track rather than its last millisecond.
            positionMs = if (exoPlayer.playbackState == Player.STATE_ENDED) 0 else exoPlayer.currentPosition.coerceAtLeast(0),
            shuffleEnabled = exoPlayer.shuffleModeEnabled,
            shuffleOrder = if (exoPlayer.shuffleModeEnabled && consistent) playOrder() else emptyList(),
            repeatMode = exoPlayer.repeatMode,
            speed = exoPlayer.playbackParameters.speed,
            pitch = exoPlayer.playbackParameters.pitch,
        )
    }

    private fun scheduleSave() {
        if (!restoreComplete) return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            val snapshot = currentSnapshot()
            appScope.launch { snapshots.save(snapshot) }
        }
    }

    private fun savePosition() {
        if (!restoreComplete || exoPlayer.mediaItemCount == 0) return
        val index = exoPlayer.currentMediaItemIndex
        val position = exoPlayer.currentPosition
        appScope.launch { snapshots.savePosition(index, position) }
    }

    /** Underlying indices in the order they'll play when shuffle is on. */
    private fun playOrder(): List<Int> = exoPlayer.currentTimeline.playOrder(shuffled = true)

    // endregion

    // region Background loops

    private fun observeSettings() {
        scope.launch {
            settings.settings.map { it.skipSilence }.distinctUntilChanged().collect { exoPlayer.skipSilenceEnabled = it }
        }
        scope.launch {
            settings.settings.map { it.volumeLeveling }.distinctUntilChanged().collect { applyLeveling() }
        }
        scope.launch {
            settings.settings.map { it.crossfadeSec to it.gaplessAlbums }.distinctUntilChanged().collect { fader.reschedule() }
        }
        scope.launch {
            settings.settings.map { it.crossfadeCurve }.distinctUntilChanged().collect { mixer.curve = it }
        }
        scope.launch {
            settings.settings.map { it.eq }.distinctUntilChanged().collect { effects.apply(it) }
        }
    }

    /** ReplayGain from the current track's tags, when levelling is on. */
    private fun applyLeveling() {
        if (!settings.settings.value.volumeLeveling) {
            mixer.leveling = 1f
            return
        }
        val db = exoPlayer.currentTracks.groups.asSequence()
            .flatMap { g -> (0 until g.length).asSequence().map { g.getTrackFormat(it) } }
            .mapNotNull { it.metadata?.let(ReplayGain::fromMetadata) }
            .firstOrNull()
        mixer.leveling = db?.let(ReplayGain::linear) ?: 1f
    }

    private fun nextIsSameAlbum(): Boolean {
        val next = exoPlayer.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: return false
        val album = { i: Int -> exoPlayer.getMediaItemAt(i).mediaMetadata.extras?.getLong(MediaItems.EXTRA_ALBUM_ID, -1L) ?: -1L }
        val current = album(exoPlayer.currentMediaItemIndex)
        return current > 0 && current == album(next)
    }

    private var progressJob: Job? = null

    /**
     * Counts listening time for play history and periodically saves the position. Runs only
     * while audio is playing, so a paused player schedules no work at all.
     */
    private fun updateProgressLoop(playing: Boolean) {
        progressJob?.cancel()
        progressJob = if (!playing) null else scope.launch {
            var ticks = 0
            while (isActive) {
                delay(TICK_MS)
                listenedMs += TICK_MS
                rituals.onPlayingTick(TICK_MS)
                maybeRecordPlay()
                if (++ticks % POSITION_SAVE_TICKS == 0) savePosition()
            }
        }
    }

    private fun maybeRecordPlay() {
        if (playRecorded || !settings.settings.value.historyEnabled) return
        val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        val threshold = duration?.let { minOf(PLAY_THRESHOLD_MS, it / 2) } ?: PLAY_THRESHOLD_MS
        if (listenedMs < threshold) return
        val songId = exoPlayer.currentMediaItem?.mediaId?.let(MediaItems::songIdOf) ?: return
        playRecorded = true
        playEvent = appScope.async { history.recordPlay(songId) }
    }

    /** Writes the current play's listening time to its history row (absolute, so repeated calls are safe). */
    private fun flushListened(endOfPlay: Boolean) {
        val event = playEvent ?: return
        val ms = listenedMs
        appScope.launch { runCatching { history.setListened(event.await(), ms) } }
        if (endOfPlay) playEvent = null
    }

    // endregion

    // region Widgets and the notification's favourite button

    /** The playing song's state for widgets and the tile. Read on the main thread. */
    private fun widgetSnapshot(): NowPlayingSnapshot {
        val item = exoPlayer.currentMediaItem
        val songId = item?.mediaId?.let(MediaItems::songIdOf) ?: -1
        val order = if (exoPlayer.mediaItemCount > 0) exoPlayer.currentTimeline.playOrder(exoPlayer.shuffleModeEnabled) else emptyList()
        val at = order.indexOf(exoPlayer.currentMediaItemIndex)
        val upNext = if (at < 0) emptyList() else order.drop(at + 1).take(UP_NEXT).mapNotNull { exoPlayer.getMediaItemAt(it).mediaMetadata.title?.toString() }
        return NowPlayingSnapshot(
            songId = songId,
            albumId = item?.mediaMetadata?.extras?.getLong(MediaItems.EXTRA_ALBUM_ID, -1L) ?: -1,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            isPlaying = exoPlayer.isPlaying,
            hasNext = exoPlayer.hasNextMediaItem(),
            hasPrevious = exoPlayer.hasPreviousMediaItem(),
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0),
            durationMs = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: 0,
            favourite = songId in favouriteIds,
            upNext = upNext,
            updatedAt = System.currentTimeMillis(),
        )
    }

    /** Coalesces bursts (a skip changes item, timeline and play state together) into one widget update. */
    private fun publishWidget() {
        widgetJob?.cancel()
        widgetJob = scope.launch {
            delay(WIDGET_DEBOUNCE_MS)
            val snapshot = widgetSnapshot()
            appScope.launch { widgetUpdater.publish(snapshot) }
        }
    }

    /** The heart in the notification and system media controls reflects the playing song. */
    private fun updateFavouriteButton() {
        val s = session ?: return
        val id = exoPlayer.currentMediaItem?.mediaId?.let(MediaItems::songIdOf)
        if (id == null) {
            s.setMediaButtonPreferences(emptyList())
            return
        }
        val fav = id in favouriteIds
        val button = CommandButton.Builder(if (fav) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(if (fav) "Remove from favourites" else "Add to favourites")
            .setSessionCommand(SessionCommand(SvaraCommands.TOGGLE_FAVOURITE, Bundle.EMPTY))
            .build()
        s.setMediaButtonPreferences(listOf(button))
    }

    // endregion

    // region Queue commands (run on the main thread with direct player access)

    /** Replaces the queue. Items are built here from ids, so even 10,000-song queues stay small over IPC. */
    private suspend fun setQueue(songIds: List<Long>, startIndex: Int, shuffle: Int): SessionResult {
        val startId = songIds.getOrNull(startIndex)
        val (items, start) = withContext(Dispatchers.Default) {
            val songs = library.songsByIds(songIds)
            songs.map(MediaItems::fromSong) to songs.indexOfFirst { it.id == startId }.coerceAtLeast(0)
        }
        if (items.isEmpty()) return SessionResult(SessionError.ERROR_BAD_VALUE)
        val shuffleOn = shuffle == SHUFFLE_ON || (shuffle == SHUFFLE_KEEP && exoPlayer.shuffleModeEnabled)
        exoPlayer.shuffleModeEnabled = false
        installQueue(items, start, 0) {
            exoPlayer.prepare()
            exoPlayer.play()
        }
        if (shuffleOn) {
            setPlayOrder(QueueMath.shuffledStartingWith(exoPlayer.mediaItemCount, exoPlayer.currentMediaItemIndex))
            exoPlayer.shuffleModeEnabled = true
        }
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    /**
     * Installs a queue current-song-first, so playback and the UI start immediately even for
     * 10,000-song queues; the songs before and after are added once that first frame is out.
     */
    private suspend fun installQueue(items: List<MediaItem>, index: Int, positionMs: Long, onFirstItemReady: () -> Unit = {}) {
        exoPlayer.setMediaItems(listOf(items[index]), 0, positionMs)
        onFirstItemReady()
        // Chunked so the main thread can draw frames between additions.
        var before = index
        while (before > 0) {
            yield()
            val from = maxOf(0, before - INSTALL_CHUNK)
            exoPlayer.addMediaItems(0, items.subList(from, before))
            before = from
        }
        var after = index + 1
        while (after < items.size) {
            yield()
            val to = minOf(items.size, after + INSTALL_CHUNK)
            exoPlayer.addMediaItems(items.subList(after, to))
            after = to
        }
    }

    private suspend fun addSongs(songIds: List<Long>, playNext: Boolean): SessionResult {
        val items = withContext(Dispatchers.Default) { library.songsByIds(songIds).map(MediaItems::fromSong) }
        if (items.isEmpty()) return SessionResult(SessionError.ERROR_BAD_VALUE)
        if (exoPlayer.mediaItemCount == 0) {
            exoPlayer.setMediaItems(items)
            exoPlayer.prepare()
            return SessionResult(SessionResult.RESULT_SUCCESS)
        }
        val current = exoPlayer.currentMediaItemIndex
        val orderBefore = playOrder()
        val insertAt = if (playNext) QueueMath.playNextInsertIndex(current, exoPlayer.mediaItemCount) else exoPlayer.mediaItemCount
        exoPlayer.addMediaItems(insertAt, items)
        if (exoPlayer.shuffleModeEnabled) {
            // ExoPlayer scatters inserted items randomly; place them where the user asked instead.
            val order = QueueMath.insertIntoOrder(orderBefore, insertAt, items.size, afterUnderlyingIndex = if (playNext) current else null)
            setPlayOrder(order)
        }
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    /**
     * Puts back an earlier queue (undo). When the song that's playing is the same in both, the
     * items around it are swapped out so playback continues without a gap.
     */
    private suspend fun restoreQueueState(state: QueueState): SessionResult {
        val found = library.songsByIds(state.songIds).associateBy { it.id }
        val target = QueueRestore.restore(state.songIds, state.currentIndex, state.positionMs, if (state.shuffle) state.playOrder else emptyList()) { it in found }
        if (target.songIds.isEmpty()) {
            exoPlayer.clearMediaItems()
            return SessionResult(SessionResult.RESULT_SUCCESS)
        }
        val items = target.songIds.mapNotNull { id -> found[id]?.let(MediaItems::fromSong) }
        if (items.size != target.songIds.size) return SessionResult(SessionError.ERROR_BAD_VALUE)
        val playingSong = exoPlayer.currentMediaItem?.mediaId?.let(MediaItems::songIdOf)
        val cur = exoPlayer.currentMediaItemIndex
        // Undo restores the list, not the past: if the song playing now is in the restored queue,
        // keep playing it (prefer the saved slot when it's the same song, else its first occurrence).
        val keep = when {
            playingSong == null || cur < 0 -> -1
            target.songIds[target.currentIndex] == playingSong -> target.currentIndex
            else -> target.songIds.indexOf(playingSong)
        }
        if (keep >= 0) {
            if (cur + 1 < exoPlayer.mediaItemCount) exoPlayer.removeMediaItems(cur + 1, exoPlayer.mediaItemCount)
            if (cur > 0) exoPlayer.removeMediaItems(0, cur)
            exoPlayer.addMediaItems(0, items.subList(0, keep))
            exoPlayer.addMediaItems(items.subList(keep + 1, items.size))
        } else {
            exoPlayer.setMediaItems(items, target.currentIndex, target.positionMs)
            exoPlayer.prepare()
        }
        exoPlayer.shuffleModeEnabled = state.shuffle
        if (state.shuffle && QueueMath.isValidOrder(target.shuffleOrder, items.size)) setPlayOrder(target.shuffleOrder)
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    private fun move(from: Int, to: Int): SessionResult {
        val size = exoPlayer.mediaItemCount
        if (from !in 0 until size || to !in 0 until size) return SessionResult(SessionError.ERROR_BAD_VALUE)
        if (exoPlayer.shuffleModeEnabled) {
            setPlayOrder(QueueMath.move(playOrder(), from, to))
        } else {
            exoPlayer.moveMediaItem(from, to)
        }
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    private fun setPlayOrder(order: List<Int>) {
        if (QueueMath.isValidOrder(order, exoPlayer.mediaItemCount)) {
            exoPlayer.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), Random.nextLong()))
        }
    }

    // endregion

    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            if (controller.packageName == packageName) {
                builder.setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(SvaraCommands.SET_QUEUE, Bundle.EMPTY))
                        .add(SessionCommand(SvaraCommands.ADD_SONGS, Bundle.EMPTY))
                        .add(SessionCommand(SvaraCommands.MOVE, Bundle.EMPTY))
                        .add(SessionCommand(SvaraCommands.RESTORE_QUEUE, Bundle.EMPTY))
                        .add(SessionCommand(SvaraCommands.TOGGLE_FAVOURITE, Bundle.EMPTY))
                        .build()
                )
            }
            return builder.build()
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map(MediaItems::withPlayableUri).toMutableList())

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> = when (customCommand.customAction) {
            SvaraCommands.SET_QUEUE -> scope.future {
                setQueue(
                    args.getLongArray(SvaraCommands.ARG_SONG_IDS)?.toList().orEmpty(),
                    args.getInt(SvaraCommands.ARG_START_INDEX),
                    args.getInt(SvaraCommands.ARG_SHUFFLE, SHUFFLE_KEEP),
                )
            }
            SvaraCommands.ADD_SONGS -> scope.future {
                addSongs(args.getLongArray(SvaraCommands.ARG_SONG_IDS)?.toList().orEmpty(), args.getBoolean(SvaraCommands.ARG_PLAY_NEXT))
            }
            SvaraCommands.MOVE -> Futures.immediateFuture(move(args.getInt(SvaraCommands.ARG_FROM), args.getInt(SvaraCommands.ARG_TO)))
            SvaraCommands.TOGGLE_FAVOURITE -> scope.future {
                val id = exoPlayer.currentMediaItem?.mediaId?.let(MediaItems::songIdOf)
                if (id != null) favourites.toggle(id)
                SessionResult(SessionResult.RESULT_SUCCESS)
            }
            SvaraCommands.RESTORE_QUEUE -> scope.future {
                restoreQueueState(
                    QueueState(
                        songIds = args.getLongArray(SvaraCommands.ARG_SONG_IDS)?.toList().orEmpty(),
                        playOrder = args.getIntArray(SvaraCommands.ARG_PLAY_ORDER)?.toList().orEmpty(),
                        currentIndex = args.getInt(SvaraCommands.ARG_START_INDEX),
                        positionMs = args.getLong(SvaraCommands.ARG_POSITION),
                        shuffle = args.getBoolean(SvaraCommands.ARG_SHUFFLE_ON),
                    )
                )
            }
            else -> Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }

        /**
         * Optional headset mapping: with "Headset button presses" on, presses of the play/pause or
         * headset button within a short window become 1 = play/pause, 2 = next, 3 = previous.
         * Off, Media3's own handling applies.
         */
        override fun onMediaButtonEvent(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent): Boolean {
            if (!settings.settings.value.headsetMultiPress) return false
            val event = intent.getParcelableExtra<android.view.KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
            if (event.keyCode != android.view.KeyEvent.KEYCODE_HEADSETHOOK && event.keyCode != android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) return false
            if (event.action != android.view.KeyEvent.ACTION_DOWN || event.repeatCount > 0) return true
            multiPress.press(scope) { count ->
                when (count) {
                    1 -> if (exoPlayer.isPlaying) exoPlayer.pause() else { if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare(); exoPlayer.play() }
                    2 -> exoPlayer.seekToNextMediaItem()
                    else -> exoPlayer.seekToPrevious()
                }
            }
            return true
        }

        /** Lets the system resume SVARĀ's last queue from the media controls after a reboot. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val restored = loadRestorableQueue() ?: throw UnsupportedOperationException("Nothing to resume")
            MediaSession.MediaItemsWithStartPosition(restored.items, restored.index, restored.positionMs)
        }
    }

    private inner class PlayerEvents : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            flushListened(endOfPlay = true)
            listenedMs = 0
            playRecorded = false
            scheduleSave()
            // Fade the new track in only if the previous one was faded out.
            fader.reschedule(fadeIn = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && mixer.transition < 1f)
            updateFavouriteButton()
            publishWidget()
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) = applyLeveling()

        override fun onAudioSessionIdChanged(audioSessionId: Int) = effects.attach(audioSessionId)

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                scheduleSave()
                publishWidget()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = scheduleSave()
        override fun onRepeatModeChanged(repeatMode: Int) = scheduleSave()
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            scheduleSave()
            fader.reschedule()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            analysis.setPlaying(isPlaying)
            updateProgressLoop(isPlaying)
            if (!isPlaying) flushListened(endOfPlay = false)
            publishWidget()
            rituals.onIsPlayingChanged(isPlaying)
            fader.reschedule()
            if (!isPlaying) savePosition()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) sleepTimer.onPausedAtEndOfItem()
            rituals.onPlayWhenReadyChanged(playWhenReady, reason)
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                fader.reschedule()
                publishWidget()
            }
            // Repeat-one loops don't change the media item, but they're a new listen.
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION && oldPosition.mediaItemIndex == newPosition.mediaItemIndex) {
                flushListened(endOfPlay = true)
                listenedMs = 0
                playRecorded = false
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val title = exoPlayer.currentMediaItem?.mediaMetadata?.title ?: "a song"
            when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                    // The file was deleted, moved or is unreadable: drop it and carry on with the queue.
                    events.emit("Skipped “$title” — the file is missing or can't be played")
                    val index = exoPlayer.currentMediaItemIndex
                    val missingId = exoPlayer.currentMediaItem?.mediaId?.let(MediaItems::songIdOf)
                    if (index in 0 until exoPlayer.mediaItemCount) exoPlayer.removeMediaItem(index)
                    if (exoPlayer.mediaItemCount > 0) {
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                    if (missingId != null && error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) {
                        appScope.launch { library.removeSongs(listOf(missingId)) }
                    }
                }
                PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
                    events.emit("SVARĀ no longer has permission to read “$title”")
                else -> events.emit("Couldn't play “$title” (${error.errorCodeName})")
            }
        }
    }

    /**
     * Wraps ExoPlayer so that turning shuffle on — from the app, the notification or a headset —
     * always keeps the current song first instead of jumping into the middle of a random order.
     */
    private inner class SvaraPlayer(private val exo: ExoPlayer) : ForwardingPlayer(exo) {
        override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
            if (shuffleModeEnabled && !exo.shuffleModeEnabled) reshuffleFrom(exo.currentMediaItemIndex)
            super.setShuffleModeEnabled(shuffleModeEnabled)
        }

        override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
            super.setMediaItems(mediaItems, startIndex, startPositionMs)
            if (exo.shuffleModeEnabled) reshuffleFrom(startIndex)
        }

        override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
            super.setMediaItems(mediaItems, resetPosition)
            if (exo.shuffleModeEnabled) reshuffleFrom(exo.currentMediaItemIndex)
        }

        // Control Studio's seek increment, used by the app, the notification and the lock screen.
        // ExoPlayer only takes increments at build time, so they're applied here instead.
        private val seekMs: Long get() = customization.customization.value.seekIncrementSec * 1000L

        override fun getSeekBackIncrement(): Long = seekMs
        override fun getSeekForwardIncrement(): Long = seekMs

        override fun seekBack() {
            exo.seekTo((exo.currentPosition - seekMs).coerceAtLeast(0))
        }

        override fun seekForward() {
            val duration = exo.duration
            val target = exo.currentPosition + seekMs
            exo.seekTo(if (duration != C.TIME_UNSET) target.coerceAtMost(duration) else target)
        }

        private fun reshuffleFrom(index: Int) {
            val size = exo.mediaItemCount
            if (size == 0) return
            setPlayOrder(QueueMath.shuffledStartingWith(size, index.coerceIn(0, size - 1)))
        }
    }

    /**
     * Adds the pass-through analysis tap to the standard audio pipeline. Everything else matches
     * DefaultRenderersFactory's own sink (16-bit output, playback parameters via Sonic).
     */
    private class AnalysisRenderersFactory(context: Context, private val analysis: AudioAnalysis) : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink =
            DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .setAudioProcessors(arrayOf(SpectrumAudioProcessor(analysis)))
                .build()
    }

    internal companion object {
        /** Players built in this process; tests check settings changes never add one. */
        val playersCreated = AtomicInteger()

        const val TAG = "PlaybackService"
        const val TICK_MS = 1_000L
        const val WIDGET_DEBOUNCE_MS = 300L
        const val UP_NEXT = 3
        const val POSITION_SAVE_TICKS = 5
        const val SAVE_DEBOUNCE_MS = 750L
        const val PLAY_THRESHOLD_MS = 30_000L

        // Local playback: 5-15 s of read-ahead is plenty (streaming defaults are 50 s).
        const val LOAD_MIN_BUFFER_MS = 5_000
        const val LOAD_MAX_BUFFER_MS = 15_000
        const val LOAD_PLAYBACK_MS = 500
        const val LOAD_REBUFFER_MS = 1_000

        const val INSTALL_CHUNK = 1_000

        const val SHUFFLE_KEEP = 0
        const val SHUFFLE_ON = 1
        const val SHUFFLE_OFF = 2
    }
}

/** Window indices of this timeline in play order. */
fun Timeline.playOrder(shuffled: Boolean): List<Int> {
    if (isEmpty) return emptyList()
    val order = ArrayList<Int>(windowCount)
    var index = getFirstWindowIndex(shuffled)
    while (index != C.INDEX_UNSET && order.size < windowCount) {
        order += index
        index = getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffled)
    }
    return order
}

/**
 * Counts headset button presses: the action runs once no further press arrives within
 * [windowMs], with the number of presses (1, 2, or 3 and more).
 */
class MultiPressDetector(private val windowMs: Long = WINDOW_MS) {
    private var count = 0
    private var job: Job? = null

    fun press(scope: CoroutineScope, onResolved: (Int) -> Unit) {
        count++
        job?.cancel()
        job = scope.launch {
            delay(windowMs)
            val n = count
            count = 0
            onResolved(n.coerceAtMost(MAX_PRESSES))
        }
    }

    companion object {
        const val WINDOW_MS = 450L
        const val MAX_PRESSES = 3
    }
}
