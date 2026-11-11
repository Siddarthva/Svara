package com.svara.app.data.repository

import com.svara.app.domain.repository.MetadataFix
import com.svara.app.domain.library.MetadataField
import com.svara.app.data.db.MetadataOverrideEntity
import com.svara.app.data.db.MetadataOverrideDao
import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.sqlite.db.SimpleSQLiteQuery
import com.svara.app.data.db.FolderRuleDao
import com.svara.app.BuildConfig
import com.svara.app.data.db.SettingEntity
import com.svara.app.data.db.SettingsDao
import com.svara.app.data.db.SongDao
import com.svara.app.data.db.SongEntity
import com.svara.app.data.db.TransactionRunner
import com.svara.app.data.mediastore.AudioSource
import com.svara.app.data.toAlbum
import com.svara.app.data.toArtist
import com.svara.app.data.toFolder
import com.svara.app.data.toGenre
import com.svara.app.data.toRule
import com.svara.app.data.toSong
import com.svara.app.di.ApplicationScope
import com.svara.app.di.DefaultDispatcher
import com.svara.app.di.ScanDispatcher
import com.svara.app.domain.library.CollectionSearch
import com.svara.app.domain.library.FuzzyMatch
import com.svara.app.data.db.SearchIndexRow
import com.svara.app.domain.model.DuplicateGroup
import com.svara.app.domain.model.SongFilter
import com.svara.app.domain.model.Unfinished
import com.svara.app.domain.library.FolderFilter
import com.svara.app.domain.library.SearchResults
import com.svara.app.domain.library.SongQueries
import com.svara.app.domain.library.TextKeys
import com.svara.app.domain.model.Album
import com.svara.app.domain.model.Artist
import com.svara.app.domain.model.Folder
import com.svara.app.domain.model.FolderKey
import com.svara.app.domain.model.Genre
import com.svara.app.domain.model.LibraryStats
import com.svara.app.domain.model.ScanProgress
import com.svara.app.domain.model.Song
import com.svara.app.domain.model.SongSortField
import com.svara.app.domain.model.SortOrder
import com.svara.app.domain.repository.LibraryRepository
import com.svara.app.domain.repository.ScanResult
import com.svara.app.domain.repository.ScanState
import com.svara.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val source: AudioSource,
    private val songDao: SongDao,
    private val folderRuleDao: FolderRuleDao,
    private val settingsDao: SettingsDao,
    private val settingsRepository: SettingsRepository,
    private val transactions: TransactionRunner,
    @ApplicationScope private val scope: CoroutineScope,
    @ScanDispatcher private val scanDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val compute: CoroutineDispatcher,
    private val metadataOverrides: MetadataOverrideDao,
) : LibraryRepository {

    private val minMs: Flow<Long> = settingsRepository.settings
        .map { it.minTrackDurationSec * 1000L }
        .distinctUntilChanged()

    private val _scanState = MutableStateFlow(ScanState())
    override val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    override val scanProgress: StateFlow<ScanProgress?> = _scanProgress.asStateFlow()

    override val songCount: Flow<Int> = minMs.flatMapLatest(songDao::observeCount)

    override fun songCount(filter: SongFilter): Flow<Int> =
        if (filter == SongFilter.ALL) songCount
        else minMs.flatMapLatest { min ->
            val (sql, args) = SongQueries.list(SortOrder(SongSortField.TITLE), min, columns = "COUNT(*)", filter = filter)
            // ORDER BY on a single aggregate row is harmless.
            songDao.observeInt(SimpleSQLiteQuery(sql, args))
        }

    override val stats: Flow<LibraryStats> = minMs.flatMapLatest(songDao::observeStats)
        .map { LibraryStats(it.songCount, it.albumCount, it.artistCount, it.totalBytes, it.totalDurationMs) }

    override fun pagedSongs(order: SortOrder<SongSortField>, filter: SongFilter): Flow<PagingData<Song>> =
        minMs.flatMapLatest { min ->
            Pager(PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = PAGE_SIZE, enablePlaceholders = true, maxSize = PAGE_SIZE * 8)) {
                val (sql, args) = SongQueries.list(order, min, filter = filter)
                songDao.pagingSource(SimpleSQLiteQuery(sql, args))
            }.flow
        }.map { page -> page.map(SongEntity::toSong) }

    override suspend fun songIds(order: SortOrder<SongSortField>, filter: SongFilter): List<Long> {
        val (sql, args) = SongQueries.list(order, minMs.first(), columns = "id", filter = filter)
        return songDao.ids(SimpleSQLiteQuery(sql, args))
    }

    // Collections are small (one row per album/artist), so they're shared and mapped in memory.
    override val albums: Flow<List<Album>> = minMs.flatMapLatest(songDao::observeAlbums)
        .map { rows -> rows.map { it.toAlbum() } }
        .flowOn(compute)
        .shareIn(scope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), replay = 1)

    override val artists: Flow<List<Artist>> = minMs.flatMapLatest(songDao::observeArtists)
        .map { rows -> rows.map { it.toArtist() } }
        .flowOn(compute)
        .shareIn(scope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), replay = 1)

    override val folders: Flow<List<Folder>> = minMs.flatMapLatest(songDao::observeFolders)
        .map { rows -> rows.map { it.toFolder() } }
        .shareIn(scope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), replay = 1)

    override val genres: Flow<List<Genre>> = minMs.flatMapLatest(songDao::observeGenres)
        .map { rows -> rows.map { it.toGenre() } }
        .shareIn(scope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MS), replay = 1)

    override fun albumSongs(albumId: Long) = minMs.flatMapLatest { songDao.observeAlbumSongs(albumId, it) }.toSongs()
    override fun artistSongs(artistKey: String) = minMs.flatMapLatest { songDao.observeArtistSongs(artistKey, it) }.toSongs()
    override fun folderSongs(folder: FolderKey) = minMs.flatMapLatest { songDao.observeFolderSongs(folder.volume, folder.path, it) }.toSongs()
    override fun genreSongs(genre: String) = minMs.flatMapLatest { songDao.observeGenreSongs(genre, it) }.toSongs()
    override fun recentlyAdded(limit: Int) = minMs.flatMapLatest { songDao.observeRecentlyAdded(it, limit) }.toSongs()

    private fun Flow<List<SongEntity>>.toSongs(): Flow<List<Song>> = map { rows -> rows.map(SongEntity::toSong) }

    override fun unfinishedAlbums(since: Long, limit: Int): Flow<List<Unfinished>> =
        minMs.flatMapLatest { songDao.observeUnfinishedAlbums(it, since, limit) }
            .map { rows -> rows.map { Unfinished(Unfinished.Kind.ALBUM, it.id, it.title, it.played, it.total, it.coverSongId, it.coverAlbumId) } }

    /** Bumped on every cache write so the fuzzy-search index is rebuilt only when the library changes. */
    @Volatile private var generation = 0L
    @Volatile private var searchIndex: Triple<Long, Long, List<SearchIndexRow>>? = null

    private suspend fun searchIndex(min: Long): List<SearchIndexRow> {
        val cached = searchIndex
        if (cached != null && cached.first == generation && cached.second == min) return cached.third
        return songDao.searchIndex(min).also { searchIndex = Triple(generation, min, it) }
    }

    override suspend fun search(query: String): SearchResults {
        val terms = TextKeys.searchTerms(query)
        if (terms.isEmpty()) return SearchResults.EMPTY
        val min = minMs.first()
        val (sql, args) = SongQueries.search(terms, min, SEARCH_LIMIT)
        val exact = songDao.songs(SimpleSQLiteQuery(sql, args)).map(SongEntity::toSong)

        // Typo tolerance: only when substring matching found little, rank the cached (id, text) index.
        var fuzzy = emptyList<Song>()
        if (exact.size < FUZZY_THRESHOLD) {
            val seen = exact.mapTo(HashSet()) { it.id }
            val ids = withContext(compute) {
                searchIndex(min).asSequence()
                    .filter { it.id !in seen }
                    .mapNotNull { row -> FuzzyMatch.score(terms, row.searchText)?.let { row.id to it } }
                    .sortedBy { it.second }
                    .take(SEARCH_LIMIT - exact.size)
                    .map { it.first }
                    .toList()
            }
            fuzzy = songsByIds(ids)
        }
        return withContext(compute) {
            SearchResults(
                songs = exact + fuzzy,
                albums = CollectionSearch.albums(albums.first(), terms),
                artists = CollectionSearch.artists(artists.first(), terms),
                folders = CollectionSearch.rank(folders.first(), terms, 20, { "${it.key.displayName} ${it.key.path.replace('/', ' ')}" }, { it.key.path }),
                includesFuzzy = fuzzy.isNotEmpty(),
            )
        }
    }

    override suspend fun duplicates(): List<DuplicateGroup> = withContext(compute) {
        songDao.duplicateCandidates(minMs.first())
            .map(SongEntity::toSong)
            .groupBy { Triple(TextKeys.sort(it.title), TextKeys.sort(it.displayArtist), it.durationMs / 1000) }
            .values
            .filter { it.size > 1 }
            .map { group -> DuplicateGroup(group, identicalSize = group.map { it.sizeBytes }.distinct().size == 1) }
    }

    override suspend fun missingFiles(): List<Song> = withContext(scanDispatcher) {
        val missing = songDao.paths(minMs.first()).filter { p -> p.dataPath != null && !java.io.File(p.dataPath).exists() }
        songsByIds(missing.map { it.id })
    }

    // region Scanning

    private val scanLock = Any()
    private var scanJob: Deferred<ScanResult>? = null

    override suspend fun rescan(): ScanResult = runScan(null)

    override suspend fun rescanIfStale(): ScanResult? {
        val current = withContext(scanDispatcher) { currentFingerprint() }
        val saved = settingsDao.get(FINGERPRINT_KEY)
        if (current != null && current == saved) return null
        return rescan()
    }

    /** The MediaStore fingerprint plus the app version, so an update re-reads tags once. */
    private suspend fun currentFingerprint(): String? = source.fingerprint()?.let { "${BuildConfig.VERSION_CODE}|$it" }

    override suspend fun rescanFolders(folders: List<FolderKey>): ScanResult = runScan(folders.takeIf { it.isNotEmpty() })

    private suspend fun runScan(folders: List<FolderKey>?): ScanResult {
        val job = synchronized(scanLock) {
            // A full scan already covers any folder; otherwise start a new one.
            scanJob?.takeIf { it.isActive } ?: scope.async(scanDispatcher) { scan(folders) }.also { scanJob = it }
        }
        return try {
            job.await()
        } catch (e: CancellationException) {
            if (!currentCoroutineContext().isActive) throw e // the caller was cancelled, not the scan
            ScanResult(0, 0, 0, 0, cancelled = true)
        }
    }

    override fun cancelScan() {
        scanJob?.cancel()
    }

    private suspend fun rules() = FolderFilter.effectiveRules(
        folderRuleDao.getAll().map { it.toRule() },
        settingsRepository.settings.value.excludeSystemFolders,
    )

    private suspend fun scan(folders: List<FolderKey>?): ScanResult {
        _scanState.update { it.copy(isScanning = true, error = null) }
        _scanProgress.value = ScanProgress(0, 0)
        val startedAt = System.nanoTime()
        // Taken before reading, so changes made during the scan are picked up next time.
        val fingerprint = if (folders == null) currentFingerprint() else null
        try {
            val rules = rules()
            val scanned = source.scan(folders) { done, total -> _scanProgress.value = ScanProgress(done, total) }
                .map { it.copy(visible = FolderFilter.isVisible(FolderKey(it.volume, it.relativePath), rules)) }
            currentCoroutineContext().ensureActive()

            // Diff against what's cached and write only the difference.
            val existing = songDao.fingerprints()
                .filter { f -> folders == null || folders.any { FolderKey(f.volume, f.relativePath).isWithin(it) } }
                .associateBy { it.id }
            val scannedIds = HashSet<Long>(scanned.size)
            val changed = scanned.filter { e ->
                scannedIds += e.id
                val f = existing[e.id]
                f == null || f.dateModifiedSec != e.dateModifiedSec || f.sizeBytes != e.sizeBytes || f.visible != e.visible
            }
            val removed = existing.keys.filter { it !in scannedIds }
            currentCoroutineContext().ensureActive()
            if (changed.isNotEmpty() || removed.isNotEmpty()) {
                // Library Doctor corrections live in SVARĀ, not in the files: put them back on rescanned rows.
                val fixes = metadataOverrides.all().groupBy { it.songId }
                val toWrite = if (fixes.isEmpty()) changed else changed.map { e -> fixes[e.id]?.let { MetadataFixes.apply(e, it) } ?: e }
                transactions.run {
                    removed.chunked(CHUNK).forEach { songDao.deleteByIds(it) }
                    toWrite.chunked(CHUNK).forEach { songDao.upsert(it) }
                }
                generation++
            }
            val added = changed.count { it.id !in existing }
            val result = ScanResult(scanned.size, added, changed.size - added, removed.size)
            Log.i(TAG, "Scan: ${result.total} songs, +${result.added} ~${result.updated} -${result.removed} in ${(System.nanoTime() - startedAt) / 1_000_000} ms")
            if (fingerprint != null) settingsDao.upsert(listOf(SettingEntity(FINGERPRINT_KEY, fingerprint)))
            _scanState.update { it.copy(isScanning = false, lastScanAtMillis = System.currentTimeMillis(), lastResult = result) }
            return result
        } catch (e: CancellationException) {
            _scanState.update { it.copy(isScanning = false) }
            throw e
        } catch (e: SecurityException) {
            Log.w(TAG, "Scan failed: no permission", e)
            _scanState.update { it.copy(isScanning = false, error = "SVARĀ doesn't have permission to read audio files.") }
            return ScanResult(0, 0, 0, 0)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Scan failed", e)
            _scanState.update { it.copy(isScanning = false, error = "Couldn't read the music library.") }
            return ScanResult(0, 0, 0, 0)
        } finally {
            _scanProgress.value = null
        }
    }

    override suspend fun applyFolderRules(): Unit = withContext(scanDispatcher) {
        val rules = rules()
        val (show, hide) = songDao.locations()
            .mapNotNull { loc ->
                val visible = FolderFilter.isVisible(FolderKey(loc.volume, loc.relativePath), rules)
                if (visible != loc.visible) loc.id to visible else null
            }
            .partition { it.second }
        transactions.run {
            show.map { it.first }.chunked(CHUNK).forEach { songDao.setVisible(it, true) }
            hide.map { it.first }.chunked(CHUNK).forEach { songDao.setVisible(it, false) }
        }
        generation++
    }

    // endregion

    override suspend fun clearCacheAndRescan(): ScanResult {
        cancelScan()
        songDao.deleteAll()
        settingsDao.upsert(listOf(SettingEntity(FINGERPRINT_KEY, "")))
        generation++
        return rescan()
    }

    override suspend fun removeSongs(ids: List<Long>) {
        if (ids.isNotEmpty()) songDao.deleteByIds(ids)
        generation++
    }

    override suspend fun songsByIds(ids: List<Long>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val found = ids.distinct().chunked(CHUNK).flatMap { songDao.getByIds(it) }.associate { it.id to it.toSong() }
        return ids.mapNotNull(found::get)
    }

    override suspend fun songById(id: Long): Song? = songDao.getById(id)?.toSong()

    override suspend fun visibleSongs(): List<Song> = withContext(compute) { songDao.visibleSongs(minMs.first()).map(SongEntity::toSong) }

    override val metadataFixes: Flow<List<MetadataFix>> =
        metadataOverrides.observeAll().map { rows -> rows.mapNotNull { it.toFix() } }

    override suspend fun applyMetadataFix(songId: Long, field: MetadataField, value: String): Boolean = transactions.run {
        val song = songDao.getById(songId) ?: return@run false
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return@run false
        // Keep the very first scanned value, so undo always goes back to what the file says.
        val original = metadataOverrides.get(songId, field.name)?.original ?: MetadataFixes.read(song, field)
        metadataOverrides.upsert(MetadataOverrideEntity(songId, field.name, trimmed, original, System.currentTimeMillis()))
        songDao.upsert(listOf(MetadataFixes.write(song, field, trimmed)))
        generation++
        true
    }

    override suspend fun undoMetadataFix(songId: Long, field: MetadataField): Boolean = transactions.run {
        val fix = metadataOverrides.get(songId, field.name) ?: return@run false
        metadataOverrides.delete(songId, field.name)
        songDao.getById(songId)?.let { songDao.upsert(listOf(MetadataFixes.write(it, field, fix.original))) }
        generation++
        true
    }

    private fun MetadataOverrideEntity.toFix(): MetadataFix? {
        val f = MetadataField.entries.firstOrNull { it.name == field } ?: return null
        return MetadataFix(songId, f, value, original, createdAt)
    }

    private companion object {
        const val TAG = "LibraryRepository"
        const val CHUNK = 500
        const val PAGE_SIZE = 60
        const val SEARCH_LIMIT = 200
        const val FUZZY_THRESHOLD = 30
        const val FINGERPRINT_KEY = "library.scanFingerprint"
        const val SHARE_TIMEOUT_MS = 5_000L
    }
}
