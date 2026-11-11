package com.svara.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.svara.app.data.db.RoomTransactionRunner
import com.svara.app.data.db.SongEntity
import com.svara.app.data.db.SvaraDatabase
import com.svara.app.data.mediastore.AudioSource
import com.svara.app.domain.library.TextKeys
import com.svara.app.domain.model.AppSettings
import com.svara.app.domain.model.FolderKey
import com.svara.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

fun inMemoryDb(): SvaraDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SvaraDatabase::class.java)
        .allowMainThreadQueries()
        .build()

fun SvaraDatabase.tx() = RoomTransactionRunner(this)

class FakeSettings(initial: AppSettings = AppSettings(minTrackDurationSec = 0)) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

/** Returns whatever [songs] holds; [gate] lets a test hold a scan open to cancel it. */
class FakeAudioSource(var songs: List<SongEntity> = emptyList()) : AudioSource {
    var gate: CompletableDeferred<Unit>? = null
    var scans = 0
    var lastFolders: List<FolderKey>? = null
    /** Changes whenever [songs] changes, like MediaStore's real fingerprint. */
    override suspend fun fingerprint(): String = songs.joinToString { "${it.id}/${it.dateModifiedSec}" }
    override suspend fun scan(folders: List<FolderKey>?, onProgress: (Int, Int) -> Unit): List<SongEntity> {
        scans++
        lastFolders = folders
        onProgress(0, songs.size)
        gate?.await()
        onProgress(songs.size, songs.size)
        return if (folders == null) songs else songs.filter { s -> folders.any { FolderKey(s.volume, s.relativePath).isWithin(it) } }
    }
}

fun song(
    id: Long,
    title: String = "Song $id",
    artist: String = "Artist",
    album: String = "Album",
    albumId: Long = 1,
    durationMs: Long = 180_000,
    dateAdded: Long = id,
    path: String = "Music/",
    volume: String = "primary",
    genre: String? = null,
    track: Int = id.toInt(),
    modified: Long = 1,
    size: Long = 1000,
) = SongEntity(
    id = id, title = title, artist = artist, album = album, albumArtist = null, albumId = albumId, artistId = 1,
    durationMs = durationMs, trackNumber = track, discNumber = 1, year = 2020, dateAddedSec = dateAdded,
    dateModifiedSec = modified, sizeBytes = size, mimeType = "audio/mpeg", genre = genre, volume = volume,
    relativePath = path, fileName = "$title.mp3", dataPath = null,
    titleKey = TextKeys.sort(title), artistKey = TextKeys.sort(artist), albumKey = TextKeys.sort(album),
    searchText = TextKeys.search("$title $artist $album"),
)
