package com.svara.app.data

import com.svara.app.data.db.AlbumRow
import com.svara.app.data.db.ArtistRow
import com.svara.app.data.db.FolderRow
import com.svara.app.data.db.FolderRuleEntity
import com.svara.app.data.db.GenreRow
import com.svara.app.data.db.SongEntity
import com.svara.app.domain.model.Album
import com.svara.app.domain.model.Artist
import com.svara.app.domain.model.Folder
import com.svara.app.domain.model.FolderKey
import com.svara.app.domain.model.Genre
import com.svara.app.domain.model.FolderRule
import com.svara.app.domain.model.FolderRuleType
import com.svara.app.domain.model.Song

fun SongEntity.toSong() = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    albumId = albumId,
    artistId = artistId,
    durationMs = durationMs,
    trackNumber = trackNumber,
    discNumber = discNumber,
    year = year,
    dateAddedSec = dateAddedSec,
    dateModifiedSec = dateModifiedSec,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    genre = genre,
    volume = volume,
    relativePath = relativePath,
    fileName = fileName,
    dataPath = dataPath,
)

fun FolderRuleEntity.toRule() = FolderRule(
    key = FolderKey(volume, path),
    // An unrecognised value (e.g. from a future version) hides that folder rather than crashing.
    type = FolderRuleType.entries.firstOrNull { it.name == type } ?: FolderRuleType.EXCLUDE,
    treeUri = treeUri,
    addedAt = addedAt,
    accessGranted = accessGranted,
)

fun FolderKey.ruleId(): String = id.lowercase()

fun AlbumRow.toAlbum() = Album(id, title.ifBlank { Song.UNKNOWN_ALBUM }, artist.ifBlank { Song.UNKNOWN_ARTIST }, year, songCount, durationMs, coverSongId, sortKey, artistSortKey)

fun ArtistRow.toArtist() = Artist(name.ifBlank { Song.UNKNOWN_ARTIST }, songCount, albumCount, coverSongId, coverAlbumId, sortKey)

fun FolderRow.toFolder() = Folder(FolderKey(volume, path), songCount, coverSongId, coverAlbumId)

fun GenreRow.toGenre() = Genre(name.trim(), songCount, coverSongId, coverAlbumId)
