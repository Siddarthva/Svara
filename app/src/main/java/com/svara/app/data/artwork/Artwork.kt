package com.svara.app.data.artwork

import com.svara.app.data.covers.CoverScope
import com.svara.app.data.covers.CoverOverrides
import androidx.core.net.toUri
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.decode.ImageSource
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.Dimension
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Image model for album art. Songs on the same album share artwork, so the album id is the cache
 * key. A custom cover adds its scope and version, so a changed cover gets a new key everywhere.
 */
data class Artwork(val songId: Long, val albumId: Long, val coverScope: CoverScope? = null, val coverVersion: Long = 0) {
    val cacheKey: String get() = when (coverScope) {
        CoverScope.SONG -> "art:song:$songId:c$coverVersion"
        CoverScope.ALBUM -> "art:album:$albumId:c$coverVersion"
        null -> if (albumId > 0) "art:album:$albumId" else "art:song:$songId"
    }

    companion object {
        /** The classic MediaStore album art URI; used as MediaMetadata.artworkUri for system surfaces. */
        fun albumArtUri(albumId: Long): Uri =
            ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), albumId)
    }
}

class NoArtworkException : IOException("No artwork") {
    override fun fillInStackTrace(): Throwable = this
}

/**
 * Reads album art straight from MediaStore and embedded tags. Nothing is copied to app storage;
 * decoded bitmaps live only in Coil's memory cache.
 */
@Singleton
class ArtworkLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val covers: CoverOverrides,
) {

    /**
     * The one cover resolution every surface uses: 1. the song's custom cover, 2. the album's
     * custom cover, 3. embedded or MediaStore art, 4. none (null; callers show a placeholder).
     * A custom cover file that is missing or unreadable falls through to the original.
     */
    fun load(art: Artwork, maxSizePx: Int): Bitmap? {
        val size = maxSizePx.coerceIn(64, MAX_SIZE)
        covers.custom(art.songId, art.albumId)?.let { custom ->
            if (custom.file.isFile) decodeStream(Uri.fromFile(custom.file), size)?.let { return it }
        }
        return loadOriginal(art, size)
    }

    private fun loadOriginal(art: Artwork, size: Int): Bitmap? {
        val songUri = if (art.songId > 0) {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, art.songId)
        } else null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val candidates = listOfNotNull(
                if (art.albumId > 0) ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, art.albumId) else null,
                songUri,
            )
            for (uri in candidates) {
                try {
                    return context.contentResolver.loadThumbnail(uri, Size(size, size), null)
                } catch (_: IOException) {
                } catch (_: RuntimeException) {
                }
            }
            return null
        }
        if (art.albumId > 0) {
            decodeStream(Artwork.albumArtUri(art.albumId), size)?.let { return it }
        }
        return songUri?.let { embedded(it, size) }
    }

    private fun decodeStream(uri: Uri, size: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) null else {
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, size) }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    } catch (_: IOException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    private fun embedded(uri: Uri, size: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, size) }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: RuntimeException) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
        return sample
    }

    companion object {
        const val MAX_SIZE = 1024
    }
}

/**
 * Loads album art at (bucketed) display size. Decoded art is written to Coil's disk cache as a
 * small JPEG, so scrolling back through a large library doesn't re-extract embedded artwork.
 */
class ArtworkFetcher(
    private val data: Artwork,
    private val options: Options,
    private val loader: ArtworkLoader,
    private val imageLoader: ImageLoader,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val width = (options.size.width as? Dimension.Pixels)?.px ?: 512
        val height = (options.size.height as? Dimension.Pixels)?.px ?: 512
        val bucket = sizeBucket(maxOf(width, height))
        val diskKey = "${data.cacheKey}@$bucket"
        val diskCache = imageLoader.diskCache

        diskCache?.openSnapshot(diskKey)?.let { snapshot ->
            return SourceFetchResult(
                source = ImageSource(snapshot.data, diskCache.fileSystem, diskKey, snapshot),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK,
            )
        }

        val bitmap = withContext(Dispatchers.IO) { loader.load(data, bucket) } ?: throw NoArtworkException()
        if (diskCache != null) {
            withContext(Dispatchers.IO) {
                val editor = diskCache.openEditor(diskKey) ?: return@withContext
                try {
                    diskCache.fileSystem.write(editor.data) { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, outputStream()) }
                    editor.commit()
                } catch (e: Exception) {
                    editor.abort()
                }
            }
        }
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory(private val loader: ArtworkLoader) : Fetcher.Factory<Artwork> {
        override fun create(data: Artwork, options: Options, imageLoader: ImageLoader): Fetcher =
            ArtworkFetcher(data, options, loader, imageLoader)
    }

    companion object {
        /** A few fixed sizes keep the cache small while never decoding more than the view needs. */
        fun sizeBucket(px: Int): Int = when {
            px <= 128 -> 128
            px <= 256 -> 256
            px <= 512 -> 512
            else -> ArtworkLoader.MAX_SIZE
        }
    }
}

class ArtworkKeyer : Keyer<Artwork> {
    override fun key(data: Artwork, options: Options): String = data.cacheKey
}

/**
 * Media3 bitmap loader for the notification and lock screen. Every request goes through
 * [ArtworkLoader] (so custom covers, embedded art and MediaStore art resolve the same way as in
 * the app) and the last bitmap is cached by its cover key, which includes the custom cover's
 * version. Replaces Media3's CacheBitmapLoader, which caches by artwork URI only and so would
 * keep showing an album's old cover after it changed.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class SvaraBitmapLoader(context: Context, private val loader: ArtworkLoader, private val covers: CoverOverrides) : BitmapLoader {
    private val executor: ListeningExecutorService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    private val delegate = DataSourceBitmapLoader.Builder(context).setExecutorService(executor).build()
    private var lastKey: String? = null
    private var lastFuture: ListenableFuture<Bitmap>? = null

    override fun supportsMimeType(mimeType: String): Boolean = delegate.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = delegate.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val albumId = albumIdOf(uri) ?: return delegate.loadBitmap(uri)
        return load(-1, albumId)
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        val songId = metadata.extras?.getLong(EXTRA_SONG_ID, -1L) ?: -1L
        val albumId = metadata.artworkUri?.let(::albumIdOf) ?: -1L
        if (songId > 0 || albumId > 0) return load(songId, albumId)
        return super.loadBitmapFromMetadata(metadata)
    }

    @Synchronized
    private fun load(songId: Long, albumId: Long): ListenableFuture<Bitmap> {
        val custom = covers.custom(songId, albumId)
        val art = Artwork(songId, albumId, custom?.scope, custom?.version ?: 0)
        if (art.cacheKey == lastKey) lastFuture?.let { if (!it.isCancelled) return it }
        val future = executor.submit<Bitmap> { loader.load(art, NOTIFICATION_ART_SIZE) ?: throw NoArtworkException() }
        lastKey = art.cacheKey
        lastFuture = future
        return future
    }

    private fun albumIdOf(uri: Uri): Long? =
        if (uri.scheme == "content" && uri.path?.startsWith("/external/audio/albumart/") == true) uri.lastPathSegment?.toLongOrNull() else null

    companion object {
        const val EXTRA_SONG_ID = "svara.songId"
        private const val NOTIFICATION_ART_SIZE = 512
    }
}
