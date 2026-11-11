package com.svara.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached MediaStore metadata. Never holds audio data. Sort and search keys are precomputed at
 * scan time and indexed, so large libraries sort, filter and page entirely in SQLite.
 */
@Entity(
    tableName = "songs",
    indices = [
        Index("visible", "titleKey"),
        Index("visible", "artistKey"),
        Index("visible", "albumKey"),
        Index("visible", "dateAddedSec"),
        Index("visible", "durationMs"),
        Index("albumId"),
        Index("volume", "relativePath"),
        Index("genre"),
    ],
)
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String?,
    val albumId: Long,
    val artistId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val genre: String?,
    val volume: String,
    val relativePath: String,
    val fileName: String,
    val dataPath: String?,
    /** Accent- and case-folded keys with leading articles removed. */
    val titleKey: String,
    val artistKey: String,
    val albumKey: String,
    /** Folded "title artist album" used for search. */
    val searchText: String,
    /** Result of the user's folder include/exclude rules; hidden songs stay cached. */
    val visible: Boolean = true,
)

/** Just enough of a song to decide whether it changed since the last scan. */
data class SongFingerprint(
    val id: Long,
    val dateModifiedSec: Long,
    val sizeBytes: Long,
    val visible: Boolean,
    val volume: String,
    val relativePath: String,
)

data class SongPath(val id: Long, val dataPath: String?)

data class SearchIndexRow(val id: Long, val searchText: String)

data class UnfinishedRow(
    val id: Long,
    val title: String,
    val played: Int,
    val total: Int,
    val coverSongId: Long,
    val coverAlbumId: Long,
    val lastPlayed: Long,
)

data class SongLocation(val id: Long, val volume: String, val relativePath: String, val visible: Boolean)

data class AlbumRow(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val songCount: Int,
    val durationMs: Long,
    val coverSongId: Long,
    val sortKey: String,
    val artistSortKey: String,
)

data class ArtistRow(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val coverSongId: Long,
    val coverAlbumId: Long,
    val sortKey: String,
)

data class FolderRow(val volume: String, val path: String, val songCount: Int, val coverSongId: Long, val coverAlbumId: Long)

data class GenreRow(val name: String, val songCount: Int, val coverSongId: Long, val coverAlbumId: Long)

data class LibraryStatsRow(
    val songCount: Int,
    val albumCount: Int,
    val artistCount: Int,
    val totalBytes: Long,
    val totalDurationMs: Long,
)

@Entity(tableName = "folder_rules")
data class FolderRuleEntity(
    /** "volume:path", lower-cased, so the same folder can't be added twice. */
    @PrimaryKey val id: String,
    val volume: String,
    val path: String,
    val type: String,
    val treeUri: String?,
    val addedAt: Long,
    val accessGranted: Boolean,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** v5: optional description. */
    val description: String? = null,
    /** v5: a song whose cover the user chose for this playlist; null = automatic composition. */
    val coverSongId: Long? = null,
    /** v5: when the playlist was moved to Recently deleted; null = live. Purged after 30 days. */
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "playlist_entries",
    foreignKeys = [ForeignKey(PlaylistEntity::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["playlistId", "position"]), Index("songId")],
)
data class PlaylistEntryEntity(
    @PrimaryKey(autoGenerate = true) val entryId: Long = 0,
    val playlistId: Long,
    val songId: Long,
    val position: Int,
)

data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val songCount: Int,
    val coverSongId: Long?,
    val coverAlbumId: Long?,
    val description: String? = null,
    val chosenCover: Boolean = false,
    val deletedAt: Long? = null,
)

data class RuleCandidateRow(
    @Embedded val song: SongEntity,
    val plays: Int,
    val lastPlayed: Long,
    val favourite: Boolean,
)

data class CoverArtRow(val position: Int, val songId: Long, val albumId: Long)

data class PlaylistSongRow(
    val entryId: Long,
    val position: Int,
    @Embedded val song: SongEntity,
)

@Entity(tableName = "favorites", indices = [Index("addedAt")])
data class FavoriteEntity(
    @PrimaryKey val songId: Long,
    val addedAt: Long,
)

@Entity(tableName = "play_events", indices = [Index("songId", "playedAt"), Index("playedAt")])
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val playedAt: Long,
    /** v5: how long this play actually lasted; 0 for plays recorded before v5. */
    @ColumnInfo(defaultValue = "0") val listenedMs: Long = 0,
)

/** One row of the listening history screen. */
data class HistoryRow(
    val eventId: Long,
    val playedAt: Long,
    val listenedMs: Long,
    @Embedded val song: SongEntity,
)

data class ArtistPlays(val artist: String, val plays: Int, val listenedMs: Long)

data class WeekListening(val week: Long, val listenedMs: Long, val plays: Int)

data class SongWithPlays(
    @Embedded val song: SongEntity,
    @ColumnInfo(name = "plays") val plays: Int,
)

@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey val position: Int,
    val songId: Long,
)

@Entity(tableName = "playback_snapshot")
data class PlaybackSnapshotEntity(
    @PrimaryKey val id: Int = 0,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    /** Comma-separated underlying indices in play order. */
    val shuffleOrder: String,
    val repeatMode: Int,
    val speed: Float,
    val pitch: Float,
)

@Entity(tableName = "smart_playlists")
data class SmartPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** [com.svara.app.domain.model.SmartRule] name. */
    val rule: String,
    val limitCount: Int,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    /** v5: rule-builder conditions ([com.svara.app.domain.playlist.SmartRules] codec); null = [rule] only. */
    val rules: String? = null,
)

/** v5: lyrics the user imported or edited, and a per-song timing offset. Audio files are never changed. */
@Entity(tableName = "lyrics_override")
data class LyricsOverrideEntity(
    @PrimaryKey val songId: Long,
    /** Imported or edited lyrics (LRC or plain text); null keeps the embedded or sidecar lyrics. */
    val text: String?,
    /** Added to the playback position when matching timed lines; positive shows lines later. */
    @ColumnInfo(defaultValue = "0") val offsetMs: Long = 0,
    val updatedAt: Long,
)

/**
 * v5: a Library Doctor correction, stored instead of writing to the audio file. [original] is the
 * scanned value it replaced, so undo restores it exactly. Reapplied after every rescan.
 */
@Entity(tableName = "metadata_override", primaryKeys = ["songId", "field"])
data class MetadataOverrideEntity(
    val songId: Long,
    /** TITLE, ARTIST or ALBUM. */
    val field: String,
    val value: String,
    val original: String,
    val createdAt: Long,
)

/** Frozen contents of a pinned smart playlist. */
@Entity(
    tableName = "smart_playlist_pins",
    primaryKeys = ["smartId", "position"],
    foreignKeys = [ForeignKey(SmartPlaylistEntity::class, ["id"], ["smartId"], onDelete = ForeignKey.CASCADE)],
)
data class SmartPinEntity(val smartId: Long, val position: Int, val songId: Long)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** A computed recap for one period, valid while [signature] matches the current history. */
@Entity(tableName = "recap_cache")
data class RecapCacheEntity(
    @PrimaryKey val periodKey: String,
    val signature: String,
    val payload: String,
    val computedAt: Long,
)

/** One play with what the library still knows about its song (null when the song is gone). */
data class RecapPlayRow(
    val songId: Long,
    val playedAt: Long,
    val listenedMs: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumId: Long?,
    val genre: String?,
)

data class FirstPlay(val songId: Long, val firstPlayed: Long)
