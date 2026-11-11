package com.svara.app.domain.model
import com.svara.app.domain.audio.FadeCurve
import com.svara.app.domain.audio.EqSettings

/**
 * A single audio file discovered on the device. SVARĀ never copies audio; [contentUri]
 * always points at the user's own file through MediaStore.
 */
data class Song(
    val id: Long,
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
    /** Storage volume id as used by the Storage Access Framework, e.g. "primary" or "1234-5678". */
    val volume: String,
    /** Folder path relative to the volume root, always ending in '/', e.g. "Music/Rock/". */
    val relativePath: String,
    val fileName: String,
    /** Absolute file path when MediaStore exposes one; used only for read-only sidecar lookup. */
    val dataPath: String?,
) {
    val contentUri: String get() = "content://media/external/audio/media/$id"
    val folderKey: FolderKey get() = FolderKey(volume, relativePath)
    val displayArtist: String get() = artist.ifBlank { UNKNOWN_ARTIST }
    val displayAlbum: String get() = album.ifBlank { UNKNOWN_ALBUM }
    /** No artist or album tag (Library Doctor lists these). */
    val missingTags: Boolean get() = artist.isBlank() || artist == UNKNOWN_ARTIST || artist.equals("<unknown>", ignoreCase = true) || album.isBlank() || album == UNKNOWN_ALBUM

    companion object {
        const val UNKNOWN_ARTIST = "Unknown artist"
        const val UNKNOWN_ALBUM = "Unknown album"
    }
}

/** A folder on a storage volume. [path] is relative to the volume root and ends in '/'. */
data class FolderKey(val volume: String, val path: String) {
    val id: String get() = "$volume:$path"
    val displayName: String
        get() = path.trimEnd('/').substringAfterLast('/').ifEmpty { if (volume == "primary") "Internal storage" else volume }

    /** True if this folder is [other] or lives somewhere beneath it. */
    fun isWithin(other: FolderKey): Boolean =
        (other.volume == "*" || volume.equals(other.volume, ignoreCase = true)) && path.startsWith(other.path, ignoreCase = true)

    companion object {
        fun normalizePath(raw: String): String {
            val trimmed = raw.trim('/')
            return if (trimmed.isEmpty()) "" else "$trimmed/"
        }

        fun fromId(id: String): FolderKey? {
            val idx = id.indexOf(':')
            if (idx <= 0) return null
            return FolderKey(id.substring(0, idx), normalizePath(id.substring(idx + 1)))
        }
    }
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val songCount: Int,
    val durationMs: Long,
    /** A representative song, used to load artwork. */
    val coverSongId: Long,
    val sortKey: String = title.lowercase(),
    val artistSortKey: String = artist.lowercase(),
)

data class Artist(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val coverSongId: Long,
    val coverAlbumId: Long,
    /** Folded artist key; also identifies the artist when navigating. */
    val sortKey: String = name.lowercase(),
)

data class Folder(
    val key: FolderKey,
    val songCount: Int,
    val coverSongId: Long,
    val coverAlbumId: Long,
)

data class Genre(
    val name: String,
    val songCount: Int,
    val coverSongId: Long,
    val coverAlbumId: Long,
)

data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val coverSongId: Long?,
    val coverAlbumId: Long?,
    val description: String? = null,
    /** True when the user picked the cover; otherwise it is composed from the songs. */
    val chosenCover: Boolean = false,
    /** Set while the playlist is in Recently deleted. */
    val deletedAt: Long? = null,
)

enum class FolderRuleType { INCLUDE, EXCLUDE }

data class FolderRule(
    val key: FolderKey,
    val type: FolderRuleType,
    /** Persisted SAF tree URI, null for rules created without a tree grant. */
    val treeUri: String?,
    val addedAt: Long,
    /** False when the persisted URI permission is gone (user revoked it or the volume is missing). */
    val accessGranted: Boolean = true,
    /** Built-in default (system sound folders); not stored, toggled by a setting. */
    val builtIn: Boolean = false,
)

/** Folders Android uses for system sounds and recordings; hidden by default. */
object SystemFolders {
    /** "*" matches any storage volume. */
    val paths = listOf("Ringtones/", "Notifications/", "Alarms/", "Recordings/", "Call recordings/")
    val rules: List<FolderRule> = paths.map { FolderRule(FolderKey("*", it), FolderRuleType.EXCLUDE, null, 0, builtIn = true) }
}

enum class SongSortField { TITLE, ARTIST, ALBUM, DATE_ADDED, DURATION, LOCATION }

enum class AlbumSortField { TITLE, ARTIST, YEAR, SONG_COUNT }

enum class ArtistSortField { NAME, SONG_COUNT }

data class SortOrder<T : Enum<T>>(val field: T, val ascending: Boolean = true)

enum class SleepTimerOption(val minutes: Int) {
    MIN_15(15), MIN_30(30), MIN_60(60), END_OF_TRACK(0);

    val label: String get() = if (this == END_OF_TRACK) "End of track" else "$minutes minutes"
}

enum class LibraryTab(val label: String) {
    SONGS("Songs"), ALBUMS("Albums"), ARTISTS("Artists"), FOLDERS("Folders"), GENRES("Genres"), DOWNLOADS("Downloads")
}

/** Narrows the paged song list. Downloads is a quick filter over the folder, not a separate source. */
enum class SongFilter(val label: String) { ALL("All"), DOWNLOADS("Downloads"), FAVOURITES("Favourites"), MISSING_TAGS("Missing tags") }

enum class HomeSection(val label: String) {
    CONTINUE("Continue listening"),
    JUMP_BACK_IN("Jump back in"),
    RECENTLY_PLAYED("Recently played"),
    MOST_PLAYED("Most played"),
    RECENTLY_ADDED("Recently added"),
    FAVORITES("Favorites"),
    PLAYLISTS("Playlists"),
}

data class AppSettings(
    val songSort: SortOrder<SongSortField> = SortOrder(SongSortField.TITLE),
    val albumSort: SortOrder<AlbumSortField> = SortOrder(AlbumSortField.TITLE),
    val artistSort: SortOrder<ArtistSortField> = SortOrder(ArtistSortField.NAME),
    val defaultSleepTimer: SleepTimerOption = SleepTimerOption.MIN_30,
    val restoreQueueOnLaunch: Boolean = true,
    val skipSilence: Boolean = false,
    /** Songs shorter than this are hidden from the library (filters out ringtones and voice notes). */
    val minTrackDurationSec: Int = 10,
    val excludeSystemFolders: Boolean = true,

    // Playback
    /** Keep pitch constant when changing speed (best for speech). */
    val preservePitch: Boolean = true,
    /** Fade-out/fade-in length between tracks, 0 = off. */
    val crossfadeSec: Int = 0,
    /** Never fade between consecutive tracks of the same album. */
    val gaplessAlbums: Boolean = true,
    /** Apply ReplayGain track gain when the file carries it. */
    val volumeLeveling: Boolean = false,

    // Rituals (all off by default)
    val sleepFadeOut: Boolean = true,
    /** Pause after this many minutes of continuous playback, 0 = off. */
    val autoPauseMinutes: Int = 0,
    val resumeOnHeadphones: Boolean = false,
    val stopOnBluetoothDisconnect: Boolean = false,

    // Privacy
    val historyEnabled: Boolean = true,
    /** Play counts (most played) only include plays after this time; 0 = all. History itself is kept. */
    val statsSince: Long = 0,
    val hideHistory: Boolean = false,
    val appLock: Boolean = false,

    // Accessibility and layout
    val leftHanded: Boolean = false,
    val highContrast: Boolean = false,
    val reducedMotion: Boolean = false,
    val homeSections: List<HomeSection> = HomeSection.entries,
    val hiddenHomeSections: Set<HomeSection> = emptySet(),

    // V3 core listening
    /** Screen SVARĀ opens on: a bottom-bar tab name, or [START_LAST] for wherever the user left off. */
    val startScreen: String = START_LAST,
    /** Custom sleep timer length in minutes, offered next to the presets. */
    val sleepCustomMinutes: Int = 45,
    /** Lyrics text size, percent of the theme's body size. */
    val lyricsTextScale: Int = 100,
    /** Lyrics line spacing multiplier, in tenths (14 = 1.4×). */
    val lyricsLineSpacing: Int = 14,
    /** Up to [RECENT_SEARCH_LIMIT] recent searches, newest first, kept on this device only. */
    val recentSearches: List<String> = emptyList(),
    /** A search button within thumb reach at the bottom of the main screens. */
    val oneHandedSearch: Boolean = false,

    // V3 audio
    /** Shape of the crossfade ramp; linear keeps the v2.1 behaviour. */
    val crossfadeCurve: FadeCurve = FadeCurve.LINEAR,
    /** Headset button: double press skips to the next song, triple press to the previous (opt-in). */
    val headsetMultiPress: Boolean = false,
    val eq: EqSettings = EqSettings(),

    // Recap
    val recapAutoAdvance: Boolean = true,
    val recapHomeCard: Boolean = true,
) {
    companion object {
        const val START_LAST = "LAST"
        const val RECENT_SEARCH_LIMIT = 6
        val LYRICS_TEXT_SCALES = 80..160
        val LYRICS_SPACINGS = 10..24
        val SLEEP_CUSTOM_RANGE = 5..240
    }
}

/** Everything needed to rebuild the queue after the process dies. */
data class QueueSnapshot(
    /** Song ids in the underlying (unshuffled) queue order. */
    val songIds: List<Long>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    /** Play order as underlying indices; empty when not shuffled. */
    val shuffleOrder: List<Int>,
    val repeatMode: Int,
    val speed: Float,
    val pitch: Float,
) {
    companion object {
        val EMPTY = QueueSnapshot(emptyList(), 0, 0, false, emptyList(), 0, 1f, 1f)
    }
}

enum class LyricsSource { EMBEDDED, SIDECAR_FILE, IMPORTED }

/** One timed lyrics line from LRC timing (embedded or sidecar). */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * [timed] is empty when the lyrics carry no timing; [text] is always the plain form. [offsetMs]
 * is the user's timing correction for this song; [raw] is the original text (LRC or plain).
 */
data class Lyrics(
    val text: String,
    val source: LyricsSource,
    val timed: List<LyricLine> = emptyList(),
    val offsetMs: Long = 0,
    val raw: String = text,
)

/** One listening-history row. */
data class HistoryEntry(val eventId: Long, val playedAt: Long, val listenedMs: Long, val song: Song)

data class ArtistStat(val artist: String, val plays: Int, val listenedMs: Long)

/** Listening in one week, starting at [weekStart] (local midnight). */
data class WeekStat(val weekStart: Long, val listenedMs: Long, val plays: Int)

/** One row of a playlist. [position] is the absolute position, including songs that are currently hidden. */
data class PlaylistItem(val entryId: Long, val position: Int, val song: Song)

data class PlayCount(val song: Song, val count: Int)

/** Progress of a running scan; [total] is 0 until MediaStore reports a count. */
data class ScanProgress(val scanned: Int, val total: Int)

/** Songs that look like the same recording. [identicalSize] means likely an exact copy. */
data class DuplicateGroup(val songs: List<Song>, val identicalSize: Boolean)

/** An album or playlist the user started recently but didn't finish. */
data class Unfinished(
    val kind: Kind,
    val id: Long,
    val title: String,
    val played: Int,
    val total: Int,
    val coverSongId: Long,
    val coverAlbumId: Long,
) {
    enum class Kind { ALBUM, PLAYLIST }
}

enum class SmartRule(val label: String, val description: String) {
    RECENTLY_PLAYED("Recently played", "Songs you played most recently, newest first."),
    MOST_PLAYED("Most played", "Songs with the most plays. A play counts after 30 seconds or half the song."),
    NOT_PLAYED_RECENTLY(
        "Not played recently",
        "Songs you haven't played in the last 30 days, longest-unplayed first, then never-played songs, newest first.",
    ),
    RECENTLY_ADDED("Recently added", "Songs most recently added to this device."),
    FAVORITE_ARTISTS("Favorite artists", "Songs by any artist you've hearted a song from, grouped by artist and album."),
}

data class SmartPlaylist(
    val id: Long,
    val name: String,
    val rule: SmartRule,
    val limit: Int,
    val pinned: Boolean,
    val updatedAt: Long,
    /** Rule-builder conditions; null for playlists made from one of the [SmartRule] presets. */
    val rules: com.svara.app.domain.playlist.SmartRules? = null,
) {
    companion object {
        val LIMITS = listOf(10, 30, 50, 100, 250, 500)
    }
}

/** A queue edit the user can undo. */
data class QueueState(
    /** Song ids in underlying order. */
    val songIds: List<Long>,
    /** Play order over underlying indices (identity when not shuffled). */
    val playOrder: List<Int>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
)

data class AudioInfo(
    val format: String?,
    val codec: String?,
    /** Bits per second as reported by the file; null when not reported. */
    val bitrate: Int?,
    /** Average bitrate calculated from size and duration, shown separately and labelled. */
    val calculatedBitrate: Int?,
    val sampleRate: Int?,
    val channels: Int?,
    val bitDepth: Int?,
    val durationMs: Long?,
    val sizeBytes: Long?,
    val path: String?,
)

data class OutputDeviceInfo(
    val name: String,
    val type: String,
    val sampleRates: List<Int>,
    val channelCounts: List<Int>,
    val encodings: List<String>,
    val isActiveRoute: Boolean,
)

data class LibraryStats(
    val songCount: Int,
    val albumCount: Int,
    val artistCount: Int,
    val totalBytes: Long,
    val totalDurationMs: Long,
)
