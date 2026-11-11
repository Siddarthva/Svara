package com.svara.app.domain.repository

import com.svara.app.domain.library.MetadataField
import com.svara.app.domain.model.WeekStat
import com.svara.app.domain.model.ArtistStat
import com.svara.app.domain.model.HistoryEntry
import com.svara.app.domain.playlist.SmartRules
import androidx.paging.PagingData
import com.svara.app.domain.library.SearchResults
import com.svara.app.domain.model.Album
import com.svara.app.domain.model.AppSettings
import com.svara.app.domain.model.Artist
import com.svara.app.domain.model.Folder
import com.svara.app.domain.model.FolderKey
import com.svara.app.domain.model.FolderRule
import com.svara.app.domain.model.FolderRuleType
import com.svara.app.domain.model.Genre
import com.svara.app.domain.model.LibraryStats
import com.svara.app.domain.model.Lyrics
import com.svara.app.domain.model.PlayCount
import com.svara.app.domain.model.Playlist
import com.svara.app.domain.model.PlaylistItem
import com.svara.app.domain.model.QueueSnapshot
import com.svara.app.domain.model.ScanProgress
import com.svara.app.domain.model.DuplicateGroup
import com.svara.app.domain.model.SmartPlaylist
import com.svara.app.domain.model.SmartRule
import com.svara.app.domain.model.SongFilter
import com.svara.app.domain.model.Unfinished
import com.svara.app.domain.model.Song
import com.svara.app.domain.model.SongSortField
import com.svara.app.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class ScanState(
    val isScanning: Boolean = false,
    val lastScanAtMillis: Long = 0,
    val lastResult: ScanResult? = null,
    val error: String? = null,
)

data class ScanResult(val total: Int, val added: Int, val updated: Int, val removed: Int, val cancelled: Boolean = false)

/**
 * The cached library. All lists are queried from Room with folder rules and the minimum
 * duration applied in SQL; nothing here holds the whole library in memory.
 */
interface LibraryRepository {
    val scanState: StateFlow<ScanState>

    /** Kept separate from [scanState] so progress ticks only recompose the progress indicator. */
    val scanProgress: StateFlow<ScanProgress?>

    /** Visible song count; null until the database has been read. */
    val songCount: Flow<Int>
    val stats: Flow<LibraryStats>

    fun pagedSongs(order: SortOrder<SongSortField>, filter: SongFilter = SongFilter.ALL): Flow<PagingData<Song>>

    /** Ids of all visible songs in [order]; used for "play all" and "select all" without loading rows. */
    suspend fun songIds(order: SortOrder<SongSortField>, filter: SongFilter = SongFilter.ALL): List<Long>
    fun songCount(filter: SongFilter): Flow<Int>

    val albums: Flow<List<Album>>
    val artists: Flow<List<Artist>>
    val folders: Flow<List<Folder>>
    val genres: Flow<List<Genre>>

    fun albumSongs(albumId: Long): Flow<List<Song>>
    fun artistSongs(artistKey: String): Flow<List<Song>>
    fun folderSongs(folder: FolderKey): Flow<List<Song>>
    fun genreSongs(genre: String): Flow<List<Song>>
    fun recentlyAdded(limit: Int): Flow<List<Song>>

    /** Songs, albums and artists matching [query]; partial and typo-tolerant. */
    suspend fun search(query: String): SearchResults

    fun unfinishedAlbums(since: Long, limit: Int): Flow<List<Unfinished>>

    /** Groups of songs that look like the same recording. Read-only; nothing is deleted. */
    suspend fun duplicates(): List<DuplicateGroup>

    /** Visible songs whose file path no longer exists. */
    suspend fun missingFiles(): List<Song>

    /** Runs an incremental scan (or joins the one in progress). Cancellable via [cancelScan]. */
    suspend fun rescan(): ScanResult

    /**
     * Scans only if the device's audio collection changed since the last full scan (or the app
     * was updated). Returns null when the cache is already current. Used at app start.
     */
    suspend fun rescanIfStale(): ScanResult?

    /** Rescans only [folders], leaving the rest of the cache untouched. */
    suspend fun rescanFolders(folders: List<FolderKey>): ScanResult
    fun cancelScan()

    /** Re-applies folder rules to cached songs after the rules change. */
    suspend fun applyFolderRules()

    /**
     * Empties the cached song metadata and rebuilds it from MediaStore. Playlists, favorites and
     * history reference songs by MediaStore id, so they reattach after the rescan.
     */
    suspend fun clearCacheAndRescan(): ScanResult

    /** Drops songs whose files turned out to be missing. */
    suspend fun removeSongs(ids: List<Long>)

    /** Looks up songs by id in the given order, skipping ids that no longer exist. */
    suspend fun songsByIds(ids: List<Long>): List<Song>
    suspend fun songById(id: Long): Song?
    /** Every visible song (Library Doctor and rule checks run over it off the main thread). */
    suspend fun visibleSongs(): List<Song> = emptyList()
    /** Library Doctor corrections, newest first. */
    val metadataFixes: Flow<List<MetadataFix>> get() = kotlinx.coroutines.flow.flowOf(emptyList())
    /** Stores a correction as a SVARĀ override and shows it everywhere at once. The file is not changed. */
    suspend fun applyMetadataFix(songId: Long, field: MetadataField, value: String): Boolean = false
    /** Removes a correction; the scanned value comes back. */
    suspend fun undoMetadataFix(songId: Long, field: MetadataField): Boolean = false
}

interface FolderRepository {
    val rules: Flow<List<FolderRule>>
    /** Persists URI permission for [treeUri] and stores the rule. Returns null if the tree can't be mapped to device storage. */
    suspend fun addFolder(treeUri: String, type: FolderRuleType): FolderRule?
    suspend fun removeRule(key: FolderKey)
    /** Re-checks persisted URI grants; rules whose grant is gone are flagged. */
    suspend fun refreshAccess()
}

/** A Library Doctor correction: what SVARĀ shows instead of the file's [original]. */
data class MetadataFix(val songId: Long, val field: MetadataField, val value: String, val original: String, val createdAt: Long)

interface PlaylistRepository {
    val playlists: Flow<List<Playlist>>
    /** Playlists in Recently deleted (kept 30 days, then purged). */
    val deleted: Flow<List<Playlist>>
    fun observePlaylist(id: Long): Flow<Playlist?>
    fun observeItems(playlistId: Long): Flow<List<PlaylistItem>>
    suspend fun create(name: String): Long
    suspend fun rename(id: Long, name: String)
    /** Moves the playlist to Recently deleted; [restore] brings it back unchanged. */
    suspend fun delete(id: Long)
    suspend fun restore(id: Long)
    /** Deletes for good (from Recently deleted). */
    suspend fun purge(id: Long)
    /** Purges playlists deleted more than 30 days before [nowMillis]. Returns how many. */
    suspend fun purgeExpired(nowMillis: Long): Int
    /** A copy with the same songs, order, description and cover. Returns the new id. */
    suspend fun duplicate(id: Long, name: String): Long
    suspend fun setDescription(id: Long, description: String?)
    /** [songId] null returns to the automatic cover. */
    suspend fun setCover(id: Long, songId: Long?)
    /** Appends songs not already in the playlist. Returns how many were added. */
    suspend fun addSongs(playlistId: Long, songIds: List<Long>): Int
    suspend fun removeEntry(playlistId: Long, entryId: Long)
    /** Puts a song back at [position] (undo of a removal). */
    suspend fun insertAt(playlistId: Long, songId: Long, position: Int)
    fun unfinished(since: Long, limit: Int): Flow<List<Unfinished>>
    suspend fun songIds(playlistId: Long): List<Long>
    /** Moves the entry at absolute position [from] to absolute position [to]. */
    suspend fun move(playlistId: Long, from: Int, to: Int)
    /** Up to [limit] (song id, album id) pairs, one per album, in playlist order. */
    suspend fun coverArt(playlistId: Long, limit: Int): List<Pair<Long, Long>> = emptyList()
}

interface SmartPlaylistRepository {
    val smartPlaylists: Flow<List<SmartPlaylist>>
    fun observe(id: Long): Flow<SmartPlaylist?>
    /** Live (or frozen, when pinned) contents. Re-evaluates off the main thread, debounced. */
    fun observeSongs(id: Long): Flow<List<Song>>
    suspend fun create(name: String, rule: SmartRule, limit: Int): Long
    suspend fun update(id: Long, name: String, rule: SmartRule, limit: Int)
    /** Rule-builder playlists. */
    suspend fun createWithRules(name: String, rules: SmartRules, limit: Int): Long
    suspend fun updateRules(id: Long, name: String, rules: SmartRules, limit: Int)
    /** What [rules] would contain right now, kept up to date while collected (live preview). */
    fun preview(rules: SmartRules, limit: Int): Flow<List<Song>>
    suspend fun delete(id: Long)
    /** Pinning freezes the current contents; unpinning resumes live updates. */
    suspend fun setPinned(id: Long, pinned: Boolean)
}

interface FavoritesRepository {
    val favoriteIds: Flow<Set<Long>>
    fun favorites(limit: Int = Int.MAX_VALUE): Flow<List<Song>>
    suspend fun toggle(songId: Long)
    suspend fun setFavorite(songId: Long, favorite: Boolean)
}

interface HistoryRepository {
    fun recentlyPlayed(limit: Int): Flow<List<Song>>
    fun mostPlayed(limit: Int): Flow<List<PlayCount>>
    /** Records a play; returns its id so its listening time can be filled in when it ends. */
    suspend fun recordPlay(songId: Long, atMillis: Long = System.currentTimeMillis()): Long
    suspend fun setListened(eventId: Long, listenedMs: Long)
    suspend fun clear()
    fun history(limit: Int): Flow<List<HistoryEntry>>
    suspend fun deleteEntry(eventId: Long)
    fun topArtists(since: Long, limit: Int): Flow<List<ArtistStat>>
    /** Listening per week since [since], weeks starting at local midnight on [firstDayOfWeek]. */
    fun weeks(since: Long, weekStartOffsetMs: Long): Flow<List<WeekStat>>
    /** When listening time started being recorded (first play with a duration), or null. */
    val timedSince: Flow<Long?>
}

interface LyricsOverrideRepository {
    /** The user's lyrics and offset for a song, or null. */
    fun observe(songId: Long): Flow<com.svara.app.data.db.LyricsOverrideEntity?>
    suspend fun setText(songId: Long, text: String?)
    suspend fun setOffset(songId: Long, offsetMs: Long)
    suspend fun clear(songId: Long)
}

interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    /** The stored settings, read now (not the flow's start-up default). */
    suspend fun current(): AppSettings = settings.value
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

interface QueueSnapshotRepository {
    suspend fun load(): QueueSnapshot
    suspend fun save(snapshot: QueueSnapshot)
    suspend fun savePosition(currentIndex: Int, positionMs: Long)
}

interface LyricsRepository {
    /** Imported lyrics when present, otherwise the file's; always with the song's timing offset. */
    suspend fun lyricsFor(song: Song): Lyrics?
    /** Only the file's own (tags or sidecar). */
    suspend fun fileLyrics(song: Song): Lyrics? = lyricsFor(song)
}
