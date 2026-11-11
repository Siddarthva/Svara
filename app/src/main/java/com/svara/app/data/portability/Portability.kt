package com.svara.app.data.portability

import com.svara.app.data.db.CoverOverrideDao
import com.svara.app.data.db.FavoriteDao
import com.svara.app.data.db.FavoriteEntity
import com.svara.app.data.db.HistoryDao
import com.svara.app.data.db.PlayEventEntity
import com.svara.app.data.db.SmartPlaylistEntity
import com.svara.app.BuildConfig
import com.svara.app.data.db.FolderRuleDao
import com.svara.app.data.db.PlaylistDao
import com.svara.app.data.db.SmartPlaylistDao
import com.svara.app.data.db.SongDao
import com.svara.app.data.db.SongEntity
import com.svara.app.data.repository.SettingsCodec
import com.svara.app.di.IoDispatcher
import com.svara.app.domain.library.TextKeys
import com.svara.app.domain.playlist.M3u
import com.svara.app.domain.repository.PlaylistRepository
import com.svara.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class M3uImportResult(val playlistId: Long, val matched: Int, val unmatched: List<String>)

/**
 * Moves playlists and settings in and out of SVARĀ. Only references and metadata are written —
 * never audio.
 */
@Singleton
class Portability @Inject constructor(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val smartDao: SmartPlaylistDao,
    private val folderRuleDao: FolderRuleDao,
    private val playlists: PlaylistRepository,
    private val settings: SettingsRepository,
    private val coverDao: CoverOverrideDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** M3U with paths relative to the storage root ("Music/Album/01 Song.mp3"). */
    suspend fun exportM3u(playlistId: Long): String = withContext(io) {
        val ids = playlistDao.getEntries(playlistId).map { it.songId }
        val byId = ids.distinct().chunked(500).flatMap { songDao.getByIds(it) }.associateBy { it.id }
        M3u.write(ids.mapNotNull(byId::get).map {
            M3u.WriteItem(it.relativePath + it.fileName, (it.durationMs / 1000).toInt(), it.artist, it.title)
        })
    }

    /**
     * Creates a playlist from M3U text. Entries are matched by path, then by file name, then by
     * the #EXTINF artist and title. Anything unmatched is reported, not guessed.
     */
    suspend fun importM3u(name: String, text: String): M3uImportResult = withContext(io) {
        val entries = M3u.parse(text)
        val matchedIds = ArrayList<Long>()
        val unmatched = ArrayList<String>()
        for (entry in entries) {
            val song = resolve(entry.location, entry.artist, entry.title)
            if (song != null) matchedIds += song.id else unmatched += entry.title?.let { t -> entry.artist?.let { "$it – $t" } ?: t } ?: entry.location
        }
        val id = playlists.create(name)
        playlists.addSongs(id, matchedIds)
        M3uImportResult(id, matchedIds.distinct().size, unmatched)
    }

    private suspend fun resolve(location: String, artist: String?, title: String?): SongEntity? {
        val tail = M3u.normalizedTail(location)
        val byName = songDao.byFileName(M3u.fileName(location))
        // Prefer the candidate whose folder path matches the end of the M3U path.
        byName.firstOrNull { tail.endsWith((it.relativePath + it.fileName).lowercase()) }?.let { return it }
        byName.singleOrNull()?.let { return it }
        if (title != null) {
            val candidates = songDao.byTitleArtist(TextKeys.sort(title), TextKeys.sort(artist ?: ""))
            candidates.firstOrNull()?.let { return it }
        }
        return byName.firstOrNull()
    }

    /** Settings, folder choices, playlists and smart playlist rules as one JSON document. */
    suspend fun exportBackup(): String = withContext(io) {
        val root = JSONObject()
        root.put("app", "SVARĀ").put("version", BuildConfig.VERSION_NAME).put("format", BackupFormat.CURRENT)
        root.put("settings", JSONObject(SettingsCodec.encode(settings.settings.value)))
        root.put("folders", JSONArray().apply {
            folderRuleDao.getAll().forEach { put(JSONObject().put("volume", it.volume).put("path", it.path).put("type", it.type)) }
        })
        root.put("playlists", JSONArray().apply {
            for (p in playlistDao.allPlaylists()) {
                val ids = playlistDao.getEntries(p.id).map { it.songId }
                val songs = ids.distinct().chunked(500).flatMap { songDao.getByIds(it) }.associateBy { it.id }
                put(JSONObject().put("name", p.name).put("songs", JSONArray().apply {
                    ids.mapNotNull(songs::get).forEach { s ->
                        put(JSONObject().put("path", s.relativePath + s.fileName).put("title", s.title).put("artist", s.artist).put("album", s.album))
                    }
                }))
            }
        })
        root.put("smartPlaylists", JSONArray().apply {
            smartDao.observeAll().first().forEach { put(JSONObject().put("name", it.name).put("rule", it.rule).put("limit", it.limitCount).put("pinned", it.pinned)) }
        })
        // Which songs and albums have a custom cover, by id. The images themselves stay on the device.
        root.put("covers", JSONArray().apply {
            coverDao.all().forEach { put(JSONObject().put("scope", it.scope).put("key", it.key)) }
        })
        // Format 2: favourites and listening history, referenced by file path like playlists.
        val favIds = favoriteDao.allIds()
        val events = historyDao.allEvents()
        val paths = (favIds + events.map { it.songId }).distinct().chunked(500).flatMap { songDao.getByIds(it) }
            .associate { it.id to (it.relativePath + it.fileName) }
        root.put("favourites", JSONArray().apply { favIds.mapNotNull(paths::get).forEach { put(it) } })
        root.put("history", JSONArray().apply {
            events.forEach { e -> paths[e.songId]?.let { put(JSONObject().put("path", it).put("at", e.playedAt).put("ms", e.listenedMs)) } }
        })
        root.toString(2)
    }

    /**
     * Restores a backup made by [exportBackup]. The file is checked first and nothing changes if it
     * is not valid. Settings are replaced; playlists, smart playlists, favourites and history are
     * added (history entries already present are skipped). Songs are matched by path on this device;
     * anything not found is counted, never guessed. Folder access and cover images are not restored:
     * folders need fresh permission and covers are never exported.
     */
    suspend fun importBackup(text: String): BackupImportResult = withContext(io) {
        val root = BackupFormat.parse(text)
        val settingsJson = root.optJSONObject("settings")
        if (settingsJson != null) {
            val map = settingsJson.keys().asSequence().associateWith { settingsJson.getString(it) }
            val restored = SettingsCodec.decode(map)
            settings.update { restored }
        }
        var playlistsAdded = 0
        var missing = 0
        root.optJSONArray("playlists")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                val songs = p.optJSONArray("songs") ?: JSONArray()
                val ids = (0 until songs.length()).mapNotNull { j ->
                    val s = songs.getJSONObject(j)
                    resolve(s.optString("path"), s.optString("artist").ifBlank { null }, s.optString("title").ifBlank { null })?.id ?: run { missing++; null }
                }
                val id = playlists.create(p.getString("name"))
                playlists.addSongs(id, ids)
                playlistsAdded++
            }
        }
        root.optJSONArray("smartPlaylists")?.let { arr ->
            val now = System.currentTimeMillis()
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                smartDao.insert(SmartPlaylistEntity(name = p.getString("name"), rule = p.getString("rule"), limitCount = p.optInt("limit"), pinned = p.optBoolean("pinned"), createdAt = now, updatedAt = now))
            }
        }
        val byPath = HashMap<String, Long?>()
        suspend fun songFor(path: String): Long? = byPath.getOrPut(path) { resolve(path, null, null)?.id }
        var favourites = 0
        root.optJSONArray("favourites")?.let { arr ->
            for (i in 0 until arr.length()) {
                val id = songFor(arr.getString(i))
                if (id == null) missing++ else { favoriteDao.insert(FavoriteEntity(id, System.currentTimeMillis())); favourites++ }
            }
        }
        var plays = 0
        root.optJSONArray("history")?.let { arr ->
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val id = songFor(e.getString("path")) ?: run { missing++; null } ?: continue
                val at = e.getLong("at")
                if (historyDao.countAt(id, at) == 0) { historyDao.insert(PlayEventEntity(songId = id, playedAt = at, listenedMs = e.optLong("ms"))); plays++ }
            }
            // History changed: computed recaps are no longer valid.
            historyDao.clearRecapCache()
        }
        BackupImportResult(playlistsAdded, favourites, plays, missing)
    }
}

data class BackupImportResult(val playlists: Int, val favourites: Int, val plays: Int, val notFound: Int)

/** The backup file format: checked in full before anything is imported. */
object BackupFormat {
    const val CURRENT = 2
    private val SUPPORTED = 1..CURRENT

    class InvalidBackup(message: String) : Exception(message)

    /** Parses and validates; throws [InvalidBackup] with a user-facing reason. */
    fun parse(text: String): JSONObject {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: throw InvalidBackup("This file isn't a SVARĀ backup.")
        if (!root.has("app") || !root.has("format")) throw InvalidBackup("This file isn't a SVARĀ backup.")
        val format = root.optInt("format", -1)
        if (format !in SUPPORTED) throw InvalidBackup("This backup was made by a newer version of SVARĀ.")
        fun check(key: String, ok: Boolean) { if (root.has(key) && !ok) throw InvalidBackup("The backup is damaged ($key).") }
        check("settings", root.optJSONObject("settings") != null)
        listOf("playlists", "smartPlaylists", "favourites", "history", "covers").forEach { check(it, root.optJSONArray(it) != null) }
        root.optJSONArray("playlists")?.let { a -> for (i in 0 until a.length()) check("playlists", a.optJSONObject(i)?.has("name") == true) }
        root.optJSONArray("history")?.let { a -> for (i in 0 until a.length()) check("history", a.optJSONObject(i)?.let { it.has("path") && it.has("at") } == true) }
        return root
    }
}
