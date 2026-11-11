package com.svara.app.data.repository

import com.svara.app.domain.model.WeekStat
import com.svara.app.domain.model.ArtistStat
import com.svara.app.domain.model.HistoryEntry
import com.svara.app.domain.playlist.RuleSong
import com.svara.app.domain.playlist.SmartRules
import com.svara.app.domain.playlist.SmartRuleEngine
import com.svara.app.data.db.FavoriteDao
import com.svara.app.data.db.FavoriteEntity
import com.svara.app.data.db.HistoryDao
import com.svara.app.data.db.PlayEventEntity
import com.svara.app.data.db.PlaybackSnapshotEntity
import com.svara.app.data.db.PlaylistDao
import com.svara.app.data.db.PlaylistEntity
import com.svara.app.data.db.PlaylistEntryEntity
import com.svara.app.data.db.PlaylistWithCount
import com.svara.app.data.db.SmartPinEntity
import com.svara.app.data.db.SmartPlaylistDao
import com.svara.app.data.db.SmartPlaylistEntity
import com.svara.app.di.RulesDispatcher
import com.svara.app.domain.model.SmartPlaylist
import com.svara.app.domain.model.SmartRule
import com.svara.app.domain.model.Unfinished
import com.svara.app.domain.repository.SmartPlaylistRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import com.svara.app.data.db.SongEntity
import com.svara.app.data.toSong
import com.svara.app.data.db.QueueDao
import com.svara.app.data.db.QueueItemEntity
import com.svara.app.data.db.TransactionRunner
import com.svara.app.domain.model.PlayCount
import com.svara.app.domain.model.Playlist
import com.svara.app.domain.model.PlaylistItem
import com.svara.app.domain.model.QueueSnapshot
import com.svara.app.domain.model.Song
import com.svara.app.domain.queue.QueueMath
import com.svara.app.domain.repository.FavoritesRepository
import com.svara.app.domain.repository.HistoryRepository
import com.svara.app.domain.repository.PlaylistRepository
import com.svara.app.domain.repository.QueueSnapshotRepository
import com.svara.app.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Minimum visible duration in ms, following the user's setting. */
internal fun SettingsRepository.minDurationMs(): Flow<Long> =
    settings.map { it.minTrackDurationSec * 1000L }.distinctUntilChanged()

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val dao: PlaylistDao,
    private val settings: SettingsRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) : PlaylistRepository {

    override val playlists: Flow<List<Playlist>> = dao.observeAll().map { rows -> rows.map { it.toPlaylist() } }

    override val deleted: Flow<List<Playlist>> = dao.observeDeleted().map { rows -> rows.map { it.toPlaylist() } }

    override fun observePlaylist(id: Long): Flow<Playlist?> = dao.observe(id).map { it?.toPlaylist() }

    override fun observeItems(playlistId: Long): Flow<List<PlaylistItem>> =
        settings.minDurationMs().flatMapLatest { dao.observeSongs(playlistId, it) }
            .map { rows -> rows.map { PlaylistItem(it.entryId, it.position, it.song.toSong()) } }

    private fun PlaylistWithCount.toPlaylist() =
        Playlist(id, name, songCount, createdAt, updatedAt, coverSongId, coverAlbumId, description, chosenCover, deletedAt)

    override suspend fun create(name: String): Long {
        val now = clock.now()
        return dao.insert(PlaylistEntity(name = name.trim(), createdAt = now, updatedAt = now))
    }

    override suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim(), clock.now())

    override suspend fun delete(id: Long) = dao.setDeleted(id, clock.now())

    override suspend fun restore(id: Long) = dao.setDeleted(id, null)

    override suspend fun purge(id: Long) = dao.delete(id)

    override suspend fun purgeExpired(nowMillis: Long): Int {
        val expired = dao.deletedBefore(nowMillis - RECENTLY_DELETED_MS)
        expired.forEach { dao.delete(it) }
        return expired.size
    }

    override suspend fun duplicate(id: Long, name: String): Long = transactions.run {
        val source = dao.get(id) ?: return@run -1L
        val now = clock.now()
        val copy = dao.insert(PlaylistEntity(name = name.trim(), createdAt = now, updatedAt = now, description = source.description, coverSongId = source.coverSongId))
        val entries = dao.getEntries(id)
        dao.insertEntries(entries.mapIndexed { i, e -> PlaylistEntryEntity(playlistId = copy, songId = e.songId, position = i) })
        copy
    }

    override suspend fun setDescription(id: Long, description: String?) =
        dao.setDescription(id, description?.trim()?.takeIf { it.isNotEmpty() }, clock.now())

    override suspend fun setCover(id: Long, songId: Long?) = dao.setCover(id, songId, clock.now())

    override suspend fun addSongs(playlistId: Long, songIds: List<Long>): Int = transactions.run {
        val existing = dao.getEntries(playlistId)
        val present = existing.mapTo(HashSet()) { it.songId }
        val toAdd = songIds.distinct().filter { it !in present }
        if (toAdd.isNotEmpty()) {
            dao.insertEntries(toAdd.mapIndexed { i, id -> PlaylistEntryEntity(playlistId = playlistId, songId = id, position = existing.size + i) })
            dao.touch(playlistId, clock.now())
        }
        toAdd.size
    }

    override suspend fun removeEntry(playlistId: Long, entryId: Long) = transactions.run {
        rewrite(playlistId, dao.getEntries(playlistId).filter { it.entryId != entryId })
    }

    override suspend fun coverArt(playlistId: Long, limit: Int): List<Pair<Long, Long>> =
        dao.coverArt(playlistId, limit, settings.minDurationMs().first()).map { it.songId to it.albumId }

    override suspend fun move(playlistId: Long, from: Int, to: Int) = transactions.run {
        rewrite(playlistId, QueueMath.move(dao.getEntries(playlistId), from, to))
    }

    override suspend fun insertAt(playlistId: Long, songId: Long, position: Int) = transactions.run {
        val entries = dao.getEntries(playlistId).toMutableList()
        entries.add(position.coerceIn(0, entries.size), PlaylistEntryEntity(playlistId = playlistId, songId = songId, position = 0))
        rewrite(playlistId, entries)
    }

    override fun unfinished(since: Long, limit: Int): Flow<List<Unfinished>> = dao.observeUnfinished(since, limit)
        .map { rows -> rows.map { Unfinished(Unfinished.Kind.PLAYLIST, it.id, it.title, it.played, it.total, it.coverSongId, it.coverAlbumId) } }

    override suspend fun songIds(playlistId: Long): List<Long> = dao.getEntries(playlistId).map { it.songId }

    /** Replaces all entries with [entries] renumbered 0..n-1, keeping entry ids stable for the UI. */
    private suspend fun rewrite(playlistId: Long, entries: List<PlaylistEntryEntity>) {
        dao.clearEntries(playlistId)
        dao.insertEntries(entries.mapIndexed { i, e -> e.copy(position = i) })
        dao.touch(playlistId, clock.now())
    }
}

/**
 * Smart playlists are stored as rules and evaluated with SQL. Room re-runs the query whenever
 * the songs, history or favorites tables change; results are debounced and computed on a
 * dedicated dispatcher so a large scan doesn't trigger repeated work.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Singleton
class SmartPlaylistRepositoryImpl @Inject constructor(
    private val dao: SmartPlaylistDao,
    private val history: HistoryDao,
    private val settings: SettingsRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    @RulesDispatcher private val dispatcher: CoroutineDispatcher,
) : SmartPlaylistRepository {

    override val smartPlaylists: Flow<List<SmartPlaylist>> = dao.observeAll().map { rows -> rows.mapNotNull { it.toModel() } }

    override fun observe(id: Long): Flow<SmartPlaylist?> = dao.observe(id).map { it?.toModel() }

    override fun observeSongs(id: Long): Flow<List<Song>> =
        combine(dao.observe(id), settings.minDurationMs()) { entity, min -> entity?.toModel() to min }
            .distinctUntilChanged()
            .flatMapLatest { (playlist, min) ->
                when {
                    playlist == null -> flowOf(emptyList())
                    playlist.pinned -> dao.observePinned(playlist.id, min).map { rows -> rows.map(SongEntity::toSong) }
                    playlist.rules != null -> preview(playlist.rules, playlist.limit)
                    else -> evaluate(playlist.rule, playlist.limit, min).map { rows -> rows.map(SongEntity::toSong) }
                }
            }
            .debounce(EVALUATION_DEBOUNCE_MS)
            .flowOn(dispatcher)

    private fun evaluate(rule: SmartRule, limit: Int, min: Long): Flow<List<SongEntity>> = when (rule) {
        SmartRule.RECENTLY_PLAYED -> history.observeRecent(min, limit)
        SmartRule.MOST_PLAYED -> history.observeMostPlayed(min, limit, settings.settings.value.statsSince).map { rows -> rows.map { it.song } }
        SmartRule.NOT_PLAYED_RECENTLY -> dao.observeNotPlayedSince(min, clock.now() - NOT_PLAYED_WINDOW_MS, limit)
        SmartRule.RECENTLY_ADDED -> history.observeRecentlyAddedFor(min, limit)
        SmartRule.FAVORITE_ARTISTS -> dao.observeFavoriteArtists(min, limit)
    }

    override suspend fun create(name: String, rule: SmartRule, limit: Int): Long {
        val now = clock.now()
        return dao.insert(SmartPlaylistEntity(name = name.trim(), rule = rule.name, limitCount = limit, pinned = false, createdAt = now, updatedAt = now))
    }

    override suspend fun createWithRules(name: String, rules: SmartRules, limit: Int): Long {
        val now = clock.now()
        return dao.insert(
            SmartPlaylistEntity(
                name = name.trim(), rule = SmartRule.RECENTLY_ADDED.name, limitCount = limit, pinned = false,
                createdAt = now, updatedAt = now, rules = SmartRuleEngine.encode(rules),
            )
        )
    }

    override suspend fun updateRules(id: Long, name: String, rules: SmartRules, limit: Int) {
        val current = dao.get(id) ?: return
        dao.upsert(current.copy(name = name.trim(), limitCount = limit, rules = SmartRuleEngine.encode(rules), updatedAt = clock.now()))
    }

    override fun preview(rules: SmartRules, limit: Int): Flow<List<Song>> =
        combine(dao.observeRuleInputs(), settings.minDurationMs(), settings.settings.map { it.statsSince }.distinctUntilChanged()) { _, min, since -> min to since }
            .debounce(EVALUATION_DEBOUNCE_MS)
            .map { (min, since) -> evaluateRules(rules, limit, min, since) }
            .flowOn(dispatcher)

    private suspend fun evaluateRules(rules: SmartRules, limit: Int, min: Long, since: Long): List<Song> {
        val candidates = dao.ruleCandidates(min, since).map { RuleSong(it.song.toSong(), it.plays, it.lastPlayed, it.favourite) }
        return SmartRuleEngine.evaluate(rules, candidates, limit, clock.now())
    }

    override suspend fun update(id: Long, name: String, rule: SmartRule, limit: Int) {
        val current = dao.get(id) ?: return
        dao.upsert(current.copy(name = name.trim(), rule = rule.name, limitCount = limit, updatedAt = clock.now()))
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun setPinned(id: Long, pinned: Boolean) {
        val current = dao.get(id)?.toModel() ?: return
        // Snapshot the live result before freezing it.
        val min = settings.minDurationMs().first()
        val frozen: List<Long> = when {
            !pinned -> emptyList()
            current.rules != null -> evaluateRules(current.rules, current.limit, min, settings.settings.value.statsSince).map { it.id }
            else -> evaluate(current.rule, current.limit, min).first().map { it.id }
        }
        transactions.run {
            // The playlist may have been deleted while the snapshot was taken.
            val latest = dao.get(id) ?: return@run
            dao.clearPins(id)
            if (pinned) dao.insertPins(frozen.mapIndexed { i, songId -> SmartPinEntity(id, i, songId) })
            dao.upsert(latest.copy(pinned = pinned, updatedAt = clock.now()))
        }
    }

    private fun SmartPlaylistEntity.toModel(): SmartPlaylist? {
        val r = SmartRule.entries.firstOrNull { it.name == rule } ?: return null
        return SmartPlaylist(id, name, r, limitCount, pinned, updatedAt, SmartRuleEngine.decode(rules))
    }

    private companion object {
        const val EVALUATION_DEBOUNCE_MS = 300L
        const val NOT_PLAYED_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val dao: FavoriteDao,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : FavoritesRepository {

    override val favoriteIds: Flow<Set<Long>> = dao.observeIds().map { it.toHashSet() }

    override fun favorites(limit: Int): Flow<List<Song>> =
        settings.minDurationMs().flatMapLatest { dao.observeSongs(it, limit) }.map { rows -> rows.map(SongEntity::toSong) }

    override suspend fun toggle(songId: Long) = setFavorite(songId, !dao.isFavorite(songId))

    override suspend fun setFavorite(songId: Long, favorite: Boolean) {
        if (favorite) dao.insert(FavoriteEntity(songId, clock.now())) else dao.delete(songId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val dao: HistoryDao,
    private val settings: SettingsRepository,
) : HistoryRepository {

    override fun recentlyPlayed(limit: Int): Flow<List<Song>> =
        settings.minDurationMs().flatMapLatest { dao.observeRecent(it, limit) }.map { rows -> rows.map(SongEntity::toSong) }

    /** Counts only plays since the last "reset statistics". */
    override fun mostPlayed(limit: Int): Flow<List<PlayCount>> =
        settings.settings.map { (it.minTrackDurationSec * 1000L) to it.statsSince }.distinctUntilChanged()
            .flatMapLatest { (min, since) -> dao.observeMostPlayed(min, limit, since) }
            .map { rows -> rows.map { PlayCount(it.song.toSong(), it.plays) } }

    override suspend fun recordPlay(songId: Long, atMillis: Long): Long = dao.insertReturningId(PlayEventEntity(songId = songId, playedAt = atMillis))

    override suspend fun setListened(eventId: Long, listenedMs: Long) = dao.setListened(eventId, listenedMs.coerceAtLeast(0))

    /** Erasing history also erases every recap computed from it. */
    override suspend fun clear() {
        dao.clear()
        dao.clearRecapCache()
    }

    override fun history(limit: Int): Flow<List<HistoryEntry>> =
        settings.minDurationMs().flatMapLatest { dao.observeHistory(it, limit) }
            .map { rows -> rows.map { HistoryEntry(it.eventId, it.playedAt, it.listenedMs, it.song.toSong()) } }

    override suspend fun deleteEntry(eventId: Long) = dao.deleteEvent(eventId)

    override fun topArtists(since: Long, limit: Int): Flow<List<ArtistStat>> =
        dao.observeTopArtists(since, limit).map { rows -> rows.map { ArtistStat(it.artist, it.plays, it.listenedMs) } }

    override fun weeks(since: Long, weekStartOffsetMs: Long): Flow<List<WeekStat>> =
        dao.observeWeeks(since, weekStartOffsetMs, WEEK_MS).map { rows ->
            rows.map { WeekStat(it.week * WEEK_MS + weekStartOffsetMs, it.listenedMs, it.plays) }
        }

    override val timedSince: Flow<Long?> = dao.observeFirstTimedPlay()

    private companion object {
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }
}

@Singleton
class QueueSnapshotRepositoryImpl @Inject constructor(
    private val dao: QueueDao,
    private val transactions: TransactionRunner,
) : QueueSnapshotRepository {

    override suspend fun load(): QueueSnapshot {
        val snapshot = dao.getSnapshot() ?: return QueueSnapshot.EMPTY
        val ids = dao.getItems().map { it.songId }
        return QueueSnapshot(
            songIds = ids,
            currentIndex = snapshot.currentIndex,
            positionMs = snapshot.positionMs,
            shuffleEnabled = snapshot.shuffleEnabled,
            shuffleOrder = snapshot.shuffleOrder.split(',').mapNotNull { it.toIntOrNull() },
            repeatMode = snapshot.repeatMode,
            speed = snapshot.speed,
            pitch = snapshot.pitch,
        )
    }

    override suspend fun save(snapshot: QueueSnapshot) = transactions.run {
        dao.clearItems()
        snapshot.songIds.mapIndexed { i, id -> QueueItemEntity(i, id) }.chunked(500).forEach { dao.insertItems(it) }
        dao.upsertSnapshot(
            PlaybackSnapshotEntity(
                currentIndex = snapshot.currentIndex,
                positionMs = snapshot.positionMs,
                shuffleEnabled = snapshot.shuffleEnabled,
                shuffleOrder = snapshot.shuffleOrder.joinToString(","),
                repeatMode = snapshot.repeatMode,
                speed = snapshot.speed,
                pitch = snapshot.pitch,
            )
        )
    }

    override suspend fun savePosition(currentIndex: Int, positionMs: Long) = dao.updatePosition(currentIndex, positionMs)
}

/** Injected time source so repositories can be tested deterministically. */
fun interface Clock {
    fun now(): Long
}

/** Playlists stay in Recently deleted this long before they are purged. */
const val RECENTLY_DELETED_MS = 30L * 24 * 60 * 60 * 1000
