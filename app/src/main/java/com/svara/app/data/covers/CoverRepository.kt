package com.svara.app.data.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import com.svara.app.data.db.CoverOverrideDao
import com.svara.app.data.db.CoverOverrideEntity
import com.svara.app.data.repository.Clock
import com.svara.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

sealed interface CoverResult {
    data object Saved : CoverResult
    data object Removed : CoverResult
    data class Failed(val message: String) : CoverResult
}

/**
 * Custom covers: decoded with downsampling (long edge at most 1024 px), cropped square, saved as
 * JPEG (quality 90) in app-private storage. The audio files are never opened for writing. Every
 * failure (cancelled pick, unreadable or corrupt image, very large image, low storage) returns
 * [CoverResult.Failed] with a message; nothing throws.
 */
@Singleton
class CoverRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CoverOverrideDao,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    /** The picked image, downsampled for the preview and the crop; null if it can't be read. */
    suspend fun load(uri: Uri?): Bitmap? = if (uri == null) null else withContext(io) {
        decodeBounded { context.contentResolver.openInputStream(uri) }
    }

    /** Saves [image] cropped square at [offset] (0..1 along its long edge) for [scope] and [key]. */
    suspend fun save(image: Bitmap, offset: Float, scope: CoverScope, key: Long): CoverResult = withContext(io) {
        try {
            val (x, y, side, _) = CoverRules.squareCrop(image.width, image.height, offset).toList()
            val square = Bitmap.createBitmap(image, x, y, side, side)
            val out = if (side > MAX_EDGE) square.scale(MAX_EDGE, MAX_EDGE, filter = true) else square
            val previous = dao.get(scope.name, key)
            val version = CoverRules.nextVersion(previous?.version, clock.now())
            val file = File(dir, "${scope.name.lowercase()}_${key}_$version.jpg")
            val estimate = out.byteCount.toLong() / ESTIMATED_COMPRESSION
            if (dir.usableSpace < estimate * SPACE_MARGIN) return@withContext CoverResult.Failed("There isn't enough free storage to save this cover.")
            val tmp = File(dir, file.name + ".tmp")
            tmp.outputStream().use { if (!out.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)) throw IOException("JPEG encode failed") }
            if (!tmp.renameTo(file)) throw IOException("Couldn't move the cover into place")
            dao.upsert(CoverOverrideEntity(scope.name, key, file.absolutePath, version, clock.now()))
            previous?.let { File(it.path).delete() }
            CoverResult.Saved
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CoverResult.Failed("Couldn't save this cover. Your original artwork is still used.")
        } catch (e: OutOfMemoryError) {
            CoverResult.Failed("This image is too large to use as a cover.")
        }
    }

    /** Removes the custom cover; the next cover in the resolution order shows again. */
    suspend fun remove(scope: CoverScope, key: Long): CoverResult = withContext(io) {
        val row = dao.get(scope.name, key) ?: return@withContext CoverResult.Removed
        dao.delete(scope.name, key)
        File(row.path).delete()
        CoverResult.Removed
    }

    /** Storage: removes every custom cover and its file. */
    suspend fun clearAll() = withContext(io) {
        dao.all().forEach { File(it.path).delete() }
        dao.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    suspend fun totalBytes(): Long = withContext(io) { dir.listFiles()?.sumOf { it.length() } ?: 0L }

    /** For the settings export: which songs and albums have a custom cover (no image data). */
    suspend fun assignments(): List<Pair<CoverScope, Long>> = withContext(io) {
        dao.all().map { CoverScope.valueOf(it.scope) to it.key }
    }

    companion object {
        const val DIR = "covers"
        const val MAX_EDGE = 1024
        const val JPEG_QUALITY = 90
        private const val ESTIMATED_COMPRESSION = 8
        private const val SPACE_MARGIN = 4

        /**
         * Reads bounds first, samples down so the long edge is about [MAX_EDGE], then scales the
         * rest of the way. Null for anything unreadable; never throws.
         */
        fun decodeBounded(open: () -> InputStream?): Bitmap? = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
                val opts = BitmapFactory.Options().apply { inSampleSize = CoverRules.sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE) }
                open()?.use { BitmapFactory.decodeStream(it, null, opts) }?.let { b ->
                    val long = maxOf(b.width, b.height)
                    if (long <= MAX_EDGE) b else b.scale(b.width * MAX_EDGE / long, b.height * MAX_EDGE / long, filter = true)
                }
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}
