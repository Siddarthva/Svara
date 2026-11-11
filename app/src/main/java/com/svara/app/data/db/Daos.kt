package com.svara.app.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

/** SQL condition for songs the user can see: allowed by folder rules and long enough. */
private const val VISIBLE = "visible = 1 AND (durationMs <= 0 OR durationMs >= :minMs)"
private const val VISIBLE_S = "s.visible = 1 AND (s.durationMs <= 0 OR s.durationMs >= :minMs)"

@Dao
interface SongDao {
    /** Paged, sorted song list; the query comes from [SongQueries]. */
    @RawQuery(observedEntities = [SongEntity::class, FavoriteEntity::class])
    fun pagingSource(query: SupportSQLiteQuery): PagingSource<Int, SongEntity>

    @RawQuery
    suspend fun ids(query: SupportSQLiteQuery): List<Long>

    @RawQuery(observedEntities = [SongEntity::class, FavoriteEntity::class])
    fun observeInt(query: SupportSQLiteQuery): Flow<Int>

    @RawQuery
    suspend fun songs(query: SupportSQLiteQuery): List<SongEntity>

    @Query("SELECT COUNT(*) FROM songs WHERE $VISIBLE")
    fun observeCount(minMs: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) AS songCount, COUNT(DISTINCT albumId) AS albumCount, COUNT(DISTINCT artistKey) AS artistCount,
               COALESCE(SUM(sizeBytes), 0) AS totalBytes, COALESCE(SUM(durationMs), 0) AS totalDurationMs
        FROM songs WHERE $VISIBLE
        """
    )
    fun observeStats(minMs: Long): Flow<LibraryStatsRow>

    @Query(
        """
        SELECT albumId AS id, MAX(album) AS title, MAX(COALESCE(NULLIF(albumArtist, ''), artist)) AS artist,
               MAX(year) AS year, COUNT(*) AS songCount, SUM(durationMs) AS durationMs, MIN(id) AS coverSongId,
               MIN(albumKey) AS sortKey, MIN(artistKey) AS artistSortKey
        FROM songs WHERE $VISIBLE GROUP BY albumId
        """
    )
    fun observeAlbums(minMs: Long): Flow<List<AlbumRow>>

    @Query(
        """
        SELECT MAX(artist) AS name, COUNT(*) AS songCount, COUNT(DISTINCT albumId) AS albumCount,
               MIN(id) AS coverSongId, MIN(albumId) AS coverAlbumId, artistKey AS sortKey
        FROM songs WHERE $VISIBLE GROUP BY artistKey
        """
    )
    fun observeArtists(minMs: Long): Flow<List<ArtistRow>>

    @Query(
        """
        SELECT MAX(volume) AS volume, MAX(relativePath) AS path, COUNT(*) AS songCount, MIN(id) AS coverSongId, MIN(albumId) AS coverAlbumId
        FROM songs WHERE $VISIBLE GROUP BY volume COLLATE NOCASE, relativePath COLLATE NOCASE
        ORDER BY relativePath COLLATE NOCASE
        """
    )
    fun observeFolders(minMs: Long): Flow<List<FolderRow>>

    @Query(
        """
        SELECT MAX(genre) AS name, COUNT(*) AS songCount, MIN(id) AS coverSongId, MIN(albumId) AS coverAlbumId
        FROM songs WHERE $VISIBLE AND genre IS NOT NULL AND genre != '' GROUP BY lower(genre) ORDER BY lower(genre)
        """
    )
    fun observeGenres(minMs: Long): Flow<List<GenreRow>>

    @Query("SELECT * FROM songs WHERE $VISIBLE AND albumId = :albumId ORDER BY discNumber, trackNumber, titleKey")
    fun observeAlbumSongs(albumId: Long, minMs: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE $VISIBLE AND artistKey = :artistKey ORDER BY albumKey, discNumber, trackNumber, titleKey")
    fun observeArtistSongs(artistKey: String, minMs: Long): Flow<List<SongEntity>>

    @Query(
        """
        SELECT * FROM songs WHERE $VISIBLE AND volume = :volume COLLATE NOCASE AND relativePath = :path COLLATE NOCASE
        ORDER BY fileName COLLATE NOCASE
        """
    )
    fun observeFolderSongs(volume: String, path: String, minMs: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE $VISIBLE AND lower(genre) = lower(:genre) ORDER BY titleKey")
    fun observeGenreSongs(genre: String, minMs: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE $VISIBLE ORDER BY dateAddedSec DESC, id DESC LIMIT :limit")
    fun observeRecentlyAdded(minMs: Long, limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE fileName = :fileName COLLATE NOCASE")
    suspend fun byFileName(fileName: String): List<SongEntity>

    @Query("SELECT * FROM songs WHERE titleKey = :titleKey AND artistKey = :artistKey")
    suspend fun byTitleArtist(titleKey: String, artistKey: String): List<SongEntity>

    @Query("SELECT id, dateModifiedSec, sizeBytes, visible, volume, relativePath FROM songs")
    suspend fun fingerprints(): List<SongFingerprint>

    @Query("SELECT id, dataPath FROM songs WHERE $VISIBLE")
    suspend fun paths(minMs: Long): List<SongPath>

    @Query("SELECT id, searchText FROM songs WHERE $VISIBLE")
    suspend fun searchIndex(minMs: Long): List<SearchIndexRow>

    /** Songs sharing title, artist and whole-second duration with at least one other visible song. */
    @Query(
        """
        SELECT * FROM songs WHERE $VISIBLE AND (titleKey, artistKey, durationMs / 1000) IN (
            SELECT titleKey, artistKey, durationMs / 1000 FROM songs WHERE $VISIBLE
            GROUP BY titleKey, artistKey, durationMs / 1000 HAVING COUNT(*) > 1
        ) ORDER BY titleKey, artistKey, durationMs / 1000, sizeBytes DESC
        """
    )
    suspend fun duplicateCandidates(minMs: Long): List<SongEntity>

    /** Albums with some, but not all, songs played since [since], most recently played first. */
    @Query(
        """
        SELECT s.albumId AS id, MAX(s.album) AS title, COUNT(DISTINCT p.songId) AS played,
               (SELECT COUNT(*) FROM songs a WHERE a.albumId = s.albumId AND a.visible = 1 AND (a.durationMs <= 0 OR a.durationMs >= :minMs)) AS total,
               MIN(s.id) AS coverSongId, s.albumId AS coverAlbumId, MAX(p.playedAt) AS lastPlayed
        FROM play_events p JOIN songs s ON s.id = p.songId
        WHERE $VISIBLE_S AND p.playedAt >= :since
        GROUP BY s.albumId HAVING played < total AND total > 1
        ORDER BY lastPlayed DESC LIMIT :limit
        """
    )
    fun observeUnfinishedAlbums(minMs: Long, since: Long, limit: Int): Flow<List<UnfinishedRow>>

    @Query("SELECT id, volume, relativePath, visible FROM songs")
    suspend fun locations(): List<SongLocation>

    @Query("UPDATE songs SET visible = :visible WHERE id IN (:ids)")
    suspend fun setVisible(ids: List<Long>, visible: Boolean)

    @Upsert
    suspend fun upsert(songs: List<SongEntity>)

    @Query("SELECT * FROM songs s WHERE $VISIBLE_S")
    suspend fun visibleSongs(minMs: Long): List<SongEntity>

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()
}

@Dao
interface FolderRuleDao {
    @Query("SELECT * FROM folder_rules ORDER BY addedAt")
    fun observeAll(): Flow<List<FolderRuleEntity>>

    @Query("SELECT * FROM folder_rules ORDER BY addedAt")
    suspend fun getAll(): List<FolderRuleEntity>

    @Upsert
    suspend fun upsert(rule: FolderRuleEntity)

    @Query("DELETE FROM folder_rules WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE folder_rules SET accessGranted = :granted WHERE id = :id")
    suspend fun setAccess(id: String, granted: Boolean)
}

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id, p.name, p.createdAt, p.updatedAt, p.description, p.deletedAt,
               p.coverSongId IS NOT NULL AS chosenCover,
               (SELECT COUNT(*) FROM playlist_entries e WHERE e.playlistId = p.id) AS songCount,
               COALESCE(p.coverSongId, (SELECT e.songId FROM playlist_entries e WHERE e.playlistId = p.id ORDER BY e.position LIMIT 1)) AS coverSongId,
               COALESCE((SELECT c.albumId FROM songs c WHERE c.id = p.coverSongId),
                   (SELECT s.albumId FROM playlist_entries e JOIN songs s ON s.id = e.songId WHERE e.playlistId = p.id ORDER BY e.position LIMIT 1)) AS coverAlbumId
        FROM playlists p WHERE p.deletedAt IS NULL ORDER BY p.name COLLATE NOCASE
        """
    )
    fun observeAll(): Flow<List<PlaylistWithCount>>

    /** Recently deleted playlists, newest first. */
    @Query(
        """
        SELECT p.id, p.name, p.createdAt, p.updatedAt, p.description, p.deletedAt,
               p.coverSongId IS NOT NULL AS chosenCover,
               (SELECT COUNT(*) FROM playlist_entries e WHERE e.playlistId = p.id) AS songCount,
               COALESCE(p.coverSongId, (SELECT e.songId FROM playlist_entries e WHERE e.playlistId = p.id ORDER BY e.position LIMIT 1)) AS coverSongId,
               COALESCE((SELECT c.albumId FROM songs c WHERE c.id = p.coverSongId),
                   (SELECT s.albumId FROM playlist_entries e JOIN songs s ON s.id = e.songId WHERE e.playlistId = p.id ORDER BY e.position LIMIT 1)) AS coverAlbumId
        FROM playlists p WHERE p.deletedAt IS NOT NULL ORDER BY p.deletedAt DESC
        """
    )
    fun observeDeleted(): Flow<List<PlaylistWithCount>>

    @Query(
        """
        SELECT p.id, p.name, p.createdAt, p.updatedAt, p.description, p.deletedAt,
               p.coverSongId IS NOT NULL AS chosenCover,
               (SELECT COUNT(*) FROM playlist_entries e WHERE e.playlistId = p.id) AS songCount,
               COALESCE(p.coverSongId, (SELECT e.songId FROM playlist_entries e WHERE e.playlistId = p.id ORDER BY e.position LIMIT 1)) AS coverSongId,
               COALESCE((SELECT c.albumId FROM songs c WHERE c.id = p.coverSongId),
                   (SELECT s.albumId FROM playlist_entries e JOIN songs s ON s.id = e.songId WHERE e.playlistId = p.id ORDER BY e.position LIMIT 1)) AS coverAlbumId
        FROM playlists p WHERE p.id = :id
        """
    )
    fun observe(id: Long): Flow<PlaylistWithCount?>

    /** One song per album, in playlist order: the covers a playlist's composition is built from. */
    @Query(
        """
        SELECT MIN(e.position) AS position, e.songId AS songId, s.albumId AS albumId
        FROM playlist_entries e JOIN songs s ON s.id = e.songId
        WHERE e.playlistId = :playlistId AND $VISIBLE_S
        GROUP BY s.albumId ORDER BY position LIMIT :limit
        """
    )
    suspend fun coverArt(playlistId: Long, limit: Int, minMs: Long): List<CoverArtRow>

    @Query(
        """
        SELECT e.entryId, e.position, s.* FROM playlist_entries e JOIN songs s ON s.id = e.songId
        WHERE e.playlistId = :playlistId AND $VISIBLE_S ORDER BY e.position
        """
    )
    fun observeSongs(playlistId: Long, minMs: Long): Flow<List<PlaylistSongRow>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("UPDATE playlists SET description = :description, updatedAt = :now WHERE id = :id")
    suspend fun setDescription(id: Long, description: String?, now: Long)

    @Query("UPDATE playlists SET coverSongId = :songId, updatedAt = :now WHERE id = :id")
    suspend fun setCover(id: Long, songId: Long?, now: Long)

    @Query("UPDATE playlists SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun setDeleted(id: Long, deletedAt: Long?)

    @Query("SELECT id FROM playlists WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun deletedBefore(cutoff: Long): List<Long>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position")
    suspend fun getEntries(playlistId: Long): List<PlaylistEntryEntity>

    @Insert
    suspend fun insertEntries(entries: List<PlaylistEntryEntity>)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun clearEntries(playlistId: Long)

    /** Playlists with some, but not all, songs played since [since]. */
    @Query(
        """
        SELECT pl.id AS id, pl.name AS title, COUNT(DISTINCT p.songId) AS played,
               (SELECT COUNT(*) FROM playlist_entries x WHERE x.playlistId = pl.id) AS total,
               MIN(s.id) AS coverSongId, MIN(s.albumId) AS coverAlbumId, MAX(p.playedAt) AS lastPlayed
        FROM playlists pl JOIN playlist_entries e ON e.playlistId = pl.id
        JOIN play_events p ON p.songId = e.songId JOIN songs s ON s.id = e.songId
        WHERE p.playedAt >= :since AND pl.deletedAt IS NULL
        GROUP BY pl.id HAVING played < total
        ORDER BY lastPlayed DESC LIMIT :limit
        """
    )
    fun observeUnfinished(since: Long, limit: Int): Flow<List<UnfinishedRow>>

    @Query("SELECT * FROM playlists WHERE deletedAt IS NULL AND name LIKE '%' || :term || '%' ESCAPE '\\' ORDER BY name COLLATE NOCASE")
    suspend fun allPlaylists(term: String = ""): List<PlaylistEntity>
}

@Dao
interface SmartPlaylistDao {
    @Query("SELECT * FROM smart_playlists ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SmartPlaylistEntity>>

    @Query("SELECT * FROM smart_playlists WHERE id = :id")
    fun observe(id: Long): Flow<SmartPlaylistEntity?>

    @Query("SELECT * FROM smart_playlists WHERE id = :id")
    suspend fun get(id: Long): SmartPlaylistEntity?

    /** Everything a rule-builder playlist can filter or sort by, for visible songs. */
    @Query(
        """
        SELECT s.*, COALESCE(h.plays, 0) AS plays, COALESCE(h.lastPlayed, 0) AS lastPlayed,
               (f.songId IS NOT NULL) AS favourite
        FROM songs s
        LEFT JOIN (SELECT songId, COUNT(*) AS plays, MAX(playedAt) AS lastPlayed FROM play_events WHERE playedAt >= :since GROUP BY songId) h ON h.songId = s.id
        LEFT JOIN favorites f ON f.songId = s.id
        WHERE $VISIBLE_S
        """
    )
    suspend fun ruleCandidates(minMs: Long, since: Long): List<RuleCandidateRow>

    /** Emits whenever anything a rule could depend on changes. */
    @Query("SELECT (SELECT COUNT(*) FROM songs) + (SELECT COUNT(*) FROM play_events) + (SELECT COUNT(*) FROM favorites)")
    fun observeRuleInputs(): Flow<Long>

    @Insert
    suspend fun insert(entity: SmartPlaylistEntity): Long

    @Upsert
    suspend fun upsert(entity: SmartPlaylistEntity)

    @Query("DELETE FROM smart_playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM smart_playlist_pins WHERE smartId = :id")
    suspend fun clearPins(id: Long)

    @Insert
    suspend fun insertPins(pins: List<SmartPinEntity>)

    @Query(
        """
        SELECT s.* FROM smart_playlist_pins p JOIN songs s ON s.id = p.songId
        WHERE p.smartId = :id AND $VISIBLE_S ORDER BY p.position
        """
    )
    fun observePinned(id: Long, minMs: Long): Flow<List<SongEntity>>

    @Query(
        """
        SELECT s.* FROM songs s LEFT JOIN (SELECT songId, MAX(playedAt) AS lastPlayed FROM play_events GROUP BY songId) h ON h.songId = s.id
        WHERE $VISIBLE_S AND (h.lastPlayed IS NULL OR h.lastPlayed < :cutoff)
        ORDER BY h.lastPlayed IS NULL, h.lastPlayed ASC, s.dateAddedSec DESC LIMIT :limit
        """
    )
    fun observeNotPlayedSince(minMs: Long, cutoff: Long, limit: Int): Flow<List<SongEntity>>

    @Query(
        """
        SELECT * FROM songs s WHERE $VISIBLE_S AND s.artistKey IN (
            SELECT DISTINCT f2.artistKey FROM favorites f JOIN songs f2 ON f2.id = f.songId
        ) ORDER BY s.artistKey, s.albumKey, s.discNumber, s.trackNumber LIMIT :limit
        """
    )
    fun observeFavoriteArtists(minMs: Long, limit: Int): Flow<List<SongEntity>>
}

@Dao
interface FavoriteDao {
    @Query("SELECT songId FROM favorites")
    fun observeIds(): Flow<List<Long>>

    @Query("SELECT s.* FROM favorites f JOIN songs s ON s.id = f.songId WHERE $VISIBLE_S ORDER BY f.addedAt DESC LIMIT :limit")
    fun observeSongs(minMs: Long, limit: Int): Flow<List<SongEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun isFavorite(songId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun delete(songId: Long)

    @Query("SELECT songId FROM favorites")
    suspend fun allIds(): List<Long>
}

@Dao
interface HistoryDao {
    @Query(
        """
        SELECT s.* FROM songs s JOIN (SELECT songId, MAX(playedAt) AS lastPlayed FROM play_events GROUP BY songId) h ON h.songId = s.id
        WHERE $VISIBLE_S ORDER BY h.lastPlayed DESC LIMIT :limit
        """
    )
    fun observeRecent(minMs: Long, limit: Int): Flow<List<SongEntity>>

    @Query(
        """
        SELECT s.*, h.plays AS plays FROM songs s
        JOIN (SELECT songId, COUNT(*) AS plays, MAX(playedAt) AS lastPlayed FROM play_events WHERE playedAt >= :since GROUP BY songId) h ON h.songId = s.id
        WHERE $VISIBLE_S ORDER BY h.plays DESC, h.lastPlayed DESC LIMIT :limit
        """
    )
    fun observeMostPlayed(minMs: Long, limit: Int, since: Long = 0): Flow<List<SongWithPlays>>

    @Query("SELECT * FROM songs s WHERE $VISIBLE_S ORDER BY s.dateAddedSec DESC, s.id DESC LIMIT :limit")
    fun observeRecentlyAddedFor(minMs: Long, limit: Int): Flow<List<SongEntity>>

    /** Total listening events and distinct songs, for the privacy screen. */
    @Query("SELECT COUNT(*) FROM play_events")
    fun observeEventCount(): Flow<Int>

    @Insert
    suspend fun insert(event: PlayEventEntity)

    @Query("DELETE FROM play_events")
    suspend fun clear()

    @Insert
    suspend fun insertReturningId(event: PlayEventEntity): Long

    @Query("UPDATE play_events SET listenedMs = :listenedMs WHERE id = :id")
    suspend fun setListened(id: Long, listenedMs: Long)

    /** Listening history, newest first. Songs that are now hidden or gone are left out. */
    @Query(
        """
        SELECT p.id AS eventId, p.playedAt AS playedAt, p.listenedMs AS listenedMs, s.* FROM play_events p
        JOIN songs s ON s.id = p.songId WHERE $VISIBLE_S ORDER BY p.playedAt DESC LIMIT :limit
        """
    )
    fun observeHistory(minMs: Long, limit: Int): Flow<List<HistoryRow>>

    @Query("DELETE FROM play_events WHERE id = :id")
    suspend fun deleteEvent(id: Long)

    @Query(
        """
        SELECT s.artist AS artist, COUNT(*) AS plays, SUM(p.listenedMs) AS listenedMs FROM play_events p
        JOIN songs s ON s.id = p.songId WHERE p.playedAt >= :since AND s.artist != ''
        GROUP BY s.artistKey ORDER BY plays DESC, listenedMs DESC LIMIT :limit
        """
    )
    fun observeTopArtists(since: Long, limit: Int): Flow<List<ArtistPlays>>

    /** Listening time per week; [weekMs] buckets from the epoch, so weeks start on Thursdays UTC unless offset. */
    @Query(
        """
        SELECT ((p.playedAt - :offsetMs) / :weekMs) AS week, SUM(p.listenedMs) AS listenedMs, COUNT(*) AS plays
        FROM play_events p WHERE p.playedAt >= :since GROUP BY week ORDER BY week
        """
    )
    fun observeWeeks(since: Long, offsetMs: Long, weekMs: Long): Flow<List<WeekListening>>

    @Query("SELECT MIN(playedAt) FROM play_events WHERE listenedMs > 0")
    fun observeFirstTimedPlay(): Flow<Long?>

    // Recap: everything below reads history only.

    @Query(
        """
        SELECT e.songId, e.playedAt, e.listenedMs, s.title, s.artist, s.album, s.albumId, s.genre
        FROM play_events e LEFT JOIN songs s ON s.id = e.songId
        WHERE e.playedAt >= :from AND e.playedAt < :to ORDER BY e.playedAt
        """
    )
    suspend fun recapPlays(from: Long, to: Long): List<RecapPlayRow>

    @Query("SELECT COALESCE(SUM(listenedMs), 0) FROM play_events WHERE playedAt >= :from AND playedAt < :to")
    suspend fun listenedBetween(from: Long, to: Long): Long

    @Query("SELECT songId, MIN(playedAt) AS firstPlayed FROM play_events WHERE songId IN (:songIds) GROUP BY songId")
    suspend fun firstPlays(songIds: List<Long>): List<FirstPlay>

    @Query("SELECT playedAt FROM play_events WHERE playedAt >= :since ORDER BY playedAt")
    suspend fun playTimes(since: Long): List<Long>

    @Query("SELECT COUNT(*) || ':' || COALESCE(MAX(id), 0) FROM play_events")
    suspend fun historyStamp(): String

    @Query("SELECT COUNT(*) || ':' || COALESCE(MAX(id), 0) FROM play_events")
    fun observeHistoryStamp(): Flow<String>

    @Query("SELECT * FROM recap_cache WHERE periodKey = :key")
    suspend fun cachedRecap(key: String): RecapCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheRecap(entity: RecapCacheEntity)

    @Query("DELETE FROM recap_cache")
    suspend fun clearRecapCache()

    @Query("SELECT * FROM play_events ORDER BY playedAt")
    suspend fun allEvents(): List<PlayEventEntity>

    @Query("SELECT COUNT(*) FROM play_events WHERE songId = :songId AND playedAt = :playedAt")
    suspend fun countAt(songId: Long, playedAt: Long): Int
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items ORDER BY position")
    suspend fun getItems(): List<QueueItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<QueueItemEntity>)

    @Query("DELETE FROM queue_items")
    suspend fun clearItems()

    @Query("SELECT * FROM playback_snapshot WHERE id = 0")
    suspend fun getSnapshot(): PlaybackSnapshotEntity?

    @Upsert
    suspend fun upsertSnapshot(snapshot: PlaybackSnapshotEntity)

    @Query("UPDATE playback_snapshot SET currentIndex = :index, positionMs = :positionMs WHERE id = 0")
    suspend fun updatePosition(index: Int, positionMs: Long)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM settings")
    suspend fun getAll(): List<SettingEntity>

    @Upsert
    suspend fun upsert(settings: List<SettingEntity>)

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?
}

@Dao
interface LyricsOverrideDao {
    @Query("SELECT * FROM lyrics_override WHERE songId = :songId")
    suspend fun get(songId: Long): LyricsOverrideEntity?

    @Query("SELECT * FROM lyrics_override WHERE songId = :songId")
    fun observe(songId: Long): Flow<LyricsOverrideEntity?>

    @Upsert
    suspend fun upsert(entity: LyricsOverrideEntity)

    @Query("DELETE FROM lyrics_override WHERE songId = :songId")
    suspend fun delete(songId: Long)

    @Query("SELECT * FROM lyrics_override")
    suspend fun all(): List<LyricsOverrideEntity>
}

@Dao
interface MetadataOverrideDao {
    @Query("SELECT * FROM metadata_override ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MetadataOverrideEntity>>

    @Query("SELECT * FROM metadata_override")
    suspend fun all(): List<MetadataOverrideEntity>

    @Query("SELECT * FROM metadata_override WHERE songId = :songId AND field = :field")
    suspend fun get(songId: Long, field: String): MetadataOverrideEntity?

    @Upsert
    suspend fun upsert(entity: MetadataOverrideEntity)

    @Query("DELETE FROM metadata_override WHERE songId = :songId AND field = :field")
    suspend fun delete(songId: Long, field: String)
}
