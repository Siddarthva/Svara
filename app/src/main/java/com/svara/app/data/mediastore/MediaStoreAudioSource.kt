package com.svara.app.data.mediastore

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.svara.app.data.db.SongEntity
import com.svara.app.domain.model.FolderKey
import com.svara.app.domain.library.TextKeys
import com.svara.app.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import javax.inject.Singleton

/** Source of audio file metadata. Abstracted so the library repository can be tested off-device. */
interface AudioSource {
    /**
     * Reads every audio file the app can see. Cooperative with cancellation and reports
     * (scanned, total) as it goes. Throws [SecurityException] without permission.
     */
    suspend fun scan(
        folders: List<FolderKey>? = null,
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> },
    ): List<SongEntity>

    /**
     * A cheap summary of the audio collection that changes whenever songs are added, removed,
     * moved or edited. Null when it can't be read (treat the library as stale).
     */
    suspend fun fingerprint(): String?
}

@Singleton
class MediaStoreAudioSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioSource {

    private val resolver: ContentResolver get() = context.contentResolver

    private val collection: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    override suspend fun scan(folders: List<FolderKey>?, onProgress: (scanned: Int, total: Int) -> Unit): List<SongEntity> {
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.ARTIST_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            @Suppress("DEPRECATION")
            add(MediaStore.Audio.Media.DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
                add(MediaStore.Audio.Media.VOLUME_NAME)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.GENRE)
                add(MediaStore.Audio.Media.ALBUM_ARTIST)
            }
        }.toTypedArray()

        val genresById = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) legacyGenres() else emptyMap()
        val (folderClause, folderArgs) = folderSelection(folders)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0$folderClause"

        val result = ArrayList<SongEntity>()
        resolver.query(collection, projection, selection, folderArgs, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val artistIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val modifiedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            @Suppress("DEPRECATION")
            val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
            val relCol = c.optColumn(MediaStore.Audio.Media.RELATIVE_PATH, Build.VERSION_CODES.Q)
            val volCol = c.optColumn(MediaStore.Audio.Media.VOLUME_NAME, Build.VERSION_CODES.Q)
            val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) c.optColumn(MediaStore.Audio.Media.GENRE, Build.VERSION_CODES.R) else null
            val albumArtistCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) c.optColumn(MediaStore.Audio.Media.ALBUM_ARTIST, Build.VERSION_CODES.R) else null

            val total = c.count
            onProgress(0, total)
            while (c.moveToNext()) {
                if (c.position % PROGRESS_EVERY == 0) {
                    currentCoroutineContext().ensureActive()
                    onProgress(c.position, total)
                }
                val id = c.getLong(idCol)
                val dataPath = if (dataCol >= 0) c.getString(dataCol) else null
                val location = resolveLocation(
                    relativePath = relCol?.let(c::getString),
                    volumeName = volCol?.let(c::getString),
                    dataPath = dataPath,
                )
                val rawTrack = c.getInt(trackCol)
                val fileName = c.getString(nameCol).orEmpty()
                val title = c.getString(titleCol)?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.')
                val artist = c.getString(artistCol).cleanTag()
                val album = c.getString(albumCol).cleanTag()
                result += SongEntity(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    albumArtist = albumArtistCol?.let(c::getString)?.cleanTag()?.takeIf(String::isNotBlank),
                    albumId = c.getLong(albumIdCol),
                    artistId = c.getLong(artistIdCol),
                    durationMs = c.getLong(durationCol),
                    trackNumber = rawTrack % 1000,
                    discNumber = (rawTrack / 1000).coerceAtLeast(1),
                    year = c.getInt(yearCol),
                    dateAddedSec = c.getLong(addedCol),
                    dateModifiedSec = c.getLong(modifiedCol),
                    sizeBytes = c.getLong(sizeCol),
                    mimeType = c.getString(mimeCol).orEmpty(),
                    genre = (genreCol?.let(c::getString) ?: genresById[id])?.trim()?.takeIf(String::isNotEmpty),
                    volume = location.volume,
                    relativePath = location.path,
                    fileName = fileName,
                    dataPath = dataPath,
                    titleKey = TextKeys.sort(title),
                    artistKey = TextKeys.sort(artist.ifBlank { Song.UNKNOWN_ARTIST }),
                    albumKey = TextKeys.sort(album.ifBlank { Song.UNKNOWN_ALBUM }),
                    searchText = TextKeys.search("$title $artist $album"),
                )
            }
            onProgress(total, total)
            if (!folders.isNullOrEmpty()) result.retainAll { e -> folders.any { FolderKey(e.volume, e.relativePath).isWithin(it) } }
        } ?: Log.w(TAG, "MediaStore query returned null")
        return result
    }

    override suspend fun fingerprint(): String? = try {
        val version = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> MediaStore.getVersion(context, MediaStore.VOLUME_EXTERNAL)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> MediaStore.getVersion(context)
            else -> ""
        }
        var count = 0
        var maxId = 0L
        var maxModified = 0L
        // Two narrow columns: a few milliseconds even for 10,000 songs.
        resolver.query(
            collection,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATE_MODIFIED),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                count++
                maxId = maxOf(maxId, c.getLong(0))
                maxModified = maxOf(maxModified, c.getLong(1))
            }
            "$version:$count:$maxId:$maxModified"
        }
    } catch (e: SecurityException) {
        null
    } catch (e: RuntimeException) {
        Log.w(TAG, "Couldn't read library fingerprint", e)
        null
    }

    /** Before Android 11 the genre isn't a column on the audio table, so it's joined manually. */
    private fun legacyGenres(): Map<Long, String> {
        val map = HashMap<Long, String>()
        try {
            @Suppress("DEPRECATION")
            resolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null, null, null,
            )?.use { genres ->
                while (genres.moveToNext()) {
                    val genreId = genres.getLong(0)
                    val name = genres.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    resolver.query(
                        MediaStore.Audio.Genres.Members.getContentUri("external", genreId),
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                        null, null, null,
                    )?.use { members ->
                        while (members.moveToNext()) map.putIfAbsent(members.getLong(0), name)
                    }
                }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Genre lookup failed", e)
        }
        return map
    }

    /** Limits a scan to the given folders (and their subfolders). Volume is checked after reading. */
    private fun folderSelection(folders: List<FolderKey>?): Pair<String, Array<String>?> {
        if (folders.isNullOrEmpty()) return "" to null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            folders.joinToString(" OR ", prefix = " AND (", postfix = ")") { "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?" } to
                folders.map { "${it.path}%" }.toTypedArray()
        } else {
            @Suppress("DEPRECATION")
            folders.joinToString(" OR ", prefix = " AND (", postfix = ")") { "${MediaStore.Audio.Media.DATA} LIKE ?" } to
                folders.map { "%/${it.path}%" }.toTypedArray()
        }
    }

    private fun Cursor.optColumn(name: String, minSdk: Int): Int? =
        if (Build.VERSION.SDK_INT >= minSdk) getColumnIndex(name).takeIf { it >= 0 } else null

    private fun String?.cleanTag(): String =
        if (this == null || this == MediaStore.UNKNOWN_STRING) "" else trim()

    companion object {
        private const val TAG = "MediaStoreAudioSource"
        private const val PROGRESS_EVERY = 250

        /**
         * Maps MediaStore location columns to the volume/path form used by the Storage Access
         * Framework ("primary" + "Music/Rock/"), so SAF folder grants can be matched to songs.
         */
        fun resolveLocation(relativePath: String?, volumeName: String?, dataPath: String?): FolderKey {
            if (relativePath != null && volumeName != null) {
                val volume = if (volumeName == "external_primary" || volumeName == "external") "primary" else volumeName.uppercase()
                return FolderKey(volume, FolderKey.normalizePath(relativePath))
            }
            if (dataPath != null) {
                val parent = dataPath.substringBeforeLast('/', "")
                val primary = "/storage/emulated/0"
                if (parent.startsWith(primary)) return FolderKey("primary", FolderKey.normalizePath(parent.removePrefix(primary)))
                val match = Regex("^/storage/([^/]+)(/.*)?$").find(parent)
                if (match != null) return FolderKey(match.groupValues[1].uppercase(), FolderKey.normalizePath(match.groupValues[2]))
                return FolderKey("primary", FolderKey.normalizePath(parent))
            }
            return FolderKey("primary", "")
        }
    }
}
