package com.svara.app.playback

import androidx.core.net.toUri
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.svara.app.data.artwork.Artwork
import com.svara.app.data.artwork.SvaraBitmapLoader
import com.svara.app.domain.model.Song
import java.util.concurrent.atomic.AtomicLong

/**
 * Song <-> MediaItem mapping. Each queue entry gets a unique media id ("songId/nonce") so the
 * same song can appear in the queue twice and the UI still has stable keys.
 */
object MediaItems {
    private val nonce = AtomicLong(System.nanoTime() and 0xFFFFFF)

    const val EXTRA_ALBUM_ID = "svara.albumId"

    fun newMediaId(songId: Long): String = "$songId/${nonce.incrementAndGet()}"

    fun songIdOf(mediaId: String): Long? = mediaId.substringBefore('/').toLongOrNull()

    @androidx.annotation.OptIn(UnstableApi::class)
    fun fromSong(song: Song): MediaItem {
        val uri = song.contentUri.toUri()
        val extras = Bundle().apply {
            putLong(SvaraBitmapLoader.EXTRA_SONG_ID, song.id)
            putLong(EXTRA_ALBUM_ID, song.albumId)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.displayArtist)
            .setAlbumTitle(song.displayAlbum)
            .setAlbumArtist(song.albumArtist)
            .setTrackNumber(song.trackNumber.takeIf { it > 0 })
            .setDiscNumber(song.discNumber)
            .setReleaseYear(song.year.takeIf { it > 0 })
            .setGenre(song.genre)
            .setDurationMs(song.durationMs.takeIf { it > 0 })
            .setArtworkUri(if (song.albumId > 0) Artwork.albumArtUri(song.albumId) else null)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(newMediaId(song.id))
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(metadata)
            .build()
    }

    /** Restores the playable URI for items that arrive from a controller without one. */
    fun withPlayableUri(item: MediaItem): MediaItem {
        if (item.localConfiguration != null) return item
        val uri = item.requestMetadata.mediaUri
            ?: songIdOf(item.mediaId)?.let { "content://media/external/audio/media/$it".toUri() }
            ?: return item
        return item.buildUpon().setUri(uri).build()
    }
}

/** Custom session commands understood by [PlaybackService]. Only SVARĀ's own UI may send them. */
object SvaraCommands {
    /** Replaces the queue. Args: [ARG_SONG_IDS] (long[]), [ARG_START_INDEX], [ARG_SHUFFLE] (0 keep, 1 on, 2 off). */
    const val SET_QUEUE = "com.svara.app.SET_QUEUE"

    /** Adds songs to the queue. Args: [ARG_SONG_IDS] (long[]), [ARG_PLAY_NEXT] (boolean). */
    const val ADD_SONGS = "com.svara.app.ADD_SONGS"

    /** Moves a queue entry, positions in play order. Args: [ARG_FROM], [ARG_TO]. */
    const val MOVE = "com.svara.app.MOVE"

    /** Hearts or un-hearts the playing song (notification and media controls button). */
    const val TOGGLE_FAVOURITE = "com.svara.app.TOGGLE_FAVOURITE"

    const val ARG_SONG_IDS = "songIds"
    /** Restores an earlier queue (undo). Args: ids, [ARG_PLAY_ORDER], [ARG_START_INDEX], [ARG_POSITION], [ARG_SHUFFLE_ON]. */
    const val RESTORE_QUEUE = "com.svara.app.RESTORE_QUEUE"

    const val ARG_PLAY_NEXT = "playNext"
    const val ARG_PLAY_ORDER = "playOrder"
    const val ARG_POSITION = "position"
    const val ARG_SHUFFLE_ON = "shuffleOn"
    const val ARG_START_INDEX = "startIndex"
    const val ARG_SHUFFLE = "shuffle"
    const val ARG_FROM = "from"
    const val ARG_TO = "to"
}
