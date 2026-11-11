package com.svara.app.data.artwork

import com.svara.app.data.covers.CoverOverrides
import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.svara.app.di.PaletteDispatcher
import com.svara.app.domain.repository.LibraryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Least-recently-used cache keyed by album, where each entry remembers the artwork version it
 * was built from. A different version rebuilds the entry; the same version reuses it. Pure
 * Kotlin so the rules are unit tested without bitmaps.
 */
class VersionedLruCache<K : Any, V : Any>(private val capacity: Int) {
    private class Entry<V>(val version: Long, val value: V)

    // accessOrder = true: iteration starts at the least recently used entry.
    private val map = object : LinkedHashMap<K, Entry<V>>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?) = size > capacity
    }

    val size: Int @Synchronized get() = map.size

    @Synchronized
    fun get(key: K, version: Long): V? = map[key]?.takeIf { it.version == version }?.value

    @Synchronized
    fun put(key: K, version: Long, value: V) {
        map[key] = Entry(version, value)
    }

    /** Returns the cached value for this version, or builds, stores and returns a new one. */
    inline fun getOrBuild(key: K, version: Long, build: () -> V?): V? =
        get(key, version) ?: build()?.also { put(key, version, it) }

    @Synchronized
    fun clear() = map.clear()
}

/**
 * Blurred album art for Now Playing backdrops (SOLACE, OCTAVE, ZENITH). Built once per album from a
 * 32 px image, kept for the 24 most recent albums, and rebuilt only when the artwork version
 * (the file's modified time) changes. The screen scales it up, which keeps it soft; there is no
 * live blur.
 */
@Singleton
class BlurredArtworkCache @Inject constructor(
    private val loader: ArtworkLoader,
    private val library: LibraryRepository,
    private val covers: CoverOverrides,
    @PaletteDispatcher private val dispatcher: CoroutineDispatcher,
) {
    /** Keyed by cover identity and strength bucket; the version is the cover's version. */
    private val cache = VersionedLruCache<String, Bitmap>(CAPACITY)

    /**
     * The blurred cover at [strength] (1 = lightest .. [STRENGTHS]). Built once per cover
     * version and strength; changing either builds only that one bitmap, off the main thread.
     */
    suspend fun blurred(songId: Long, albumId: Long, strength: Int = DEFAULT_STRENGTH): Bitmap? = withContext(dispatcher) {
        val id = covers.identity(songId, albumId, library.songById(songId)?.dateModifiedSec ?: 0L)
        val s = strength.coerceIn(1, STRENGTHS)
        cache.getOrBuild("${id.key}:$s", id.version) {
            loader.load(Artwork(songId, albumId), SOURCE_PX)?.let { source -> build(source, s) }
        }
    }

    fun clear() = cache.clear()

    companion object {
        const val CAPACITY = 24
        const val SIZE_PX = 32
        const val STRENGTHS = 5
        const val DEFAULT_STRENGTH = 3

        /** Stronger blur = smaller source and a wider box: bucket 1 is 48 px r1, bucket 5 is 16 px r3. */
        fun sizeFor(strength: Int): Int = SIZE_BY_STRENGTH[strength.coerceIn(1, STRENGTHS) - 1]
        fun radiusFor(strength: Int): Int = RADIUS_BY_STRENGTH[strength.coerceIn(1, STRENGTHS) - 1]
        private val SIZE_BY_STRENGTH = intArrayOf(48, 40, 32, 24, 16)
        private val RADIUS_BY_STRENGTH = intArrayOf(1, 2, 2, 3, 3)

        fun build(source: Bitmap, strength: Int): Bitmap {
            val side = sizeFor(strength)
            return boxBlur(source.scale(side, side, filter = true), radiusFor(strength))
        }
        /** The loader decodes at least 64 px; it is scaled down to [SIZE_PX] here. */
        private const val SOURCE_PX = 64

        /** Two-pass box blur on a small bitmap (32 × 32 = 1,024 pixels, so this is cheap). */
        fun boxBlur(src: Bitmap, radius: Int): Bitmap {
            val w = src.width
            val h = src.height
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            val tmp = IntArray(w * h)
            fun pass(input: IntArray, output: IntArray, horizontal: Boolean) {
                val outer = if (horizontal) h else w
                val inner = if (horizontal) w else h
                for (o in 0 until outer) {
                    for (i in 0 until inner) {
                        var r = 0; var g = 0; var b = 0; var n = 0
                        for (k in -radius..radius) {
                            val j = (i + k).coerceIn(0, inner - 1)
                            val c = if (horizontal) input[o * w + j] else input[j * w + o]
                            r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                        }
                        val idx = if (horizontal) o * w + i else i * w + o
                        output[idx] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
                    }
                }
            }
            pass(px, tmp, horizontal = true)
            pass(tmp, px, horizontal = false)
            return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        }
    }
}
