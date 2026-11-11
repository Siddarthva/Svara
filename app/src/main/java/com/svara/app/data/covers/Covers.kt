package com.svara.app.data.covers

import com.svara.app.appearance.Argb
import com.svara.app.appearance.colour.Swatches
import com.svara.app.data.db.CoverOverrideDao
import com.svara.app.data.db.CoverOverrideEntity
import com.svara.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class CoverScope { SONG, ALBUM }

/** A custom cover as the app sees it. */
data class CustomCover(val scope: CoverScope, val key: Long, val file: File, val version: Long)

/** Where a song's artwork comes from, in resolution order. */
sealed interface CoverSource {
    data class Custom(val cover: CustomCover) : CoverSource
    /** Embedded tags or MediaStore album art (the loader falls back to [None] if there is none). */
    data object Original : CoverSource
    data object None : CoverSource
}

/**
 * The key and version every cache uses for one song's artwork: Coil, the palette cache and the
 * blur cache. Custom covers use negative versions, so they never collide with file timestamps.
 */
data class CoverIdentity(val key: Long, val version: Long, val scope: CoverScope?)

/** Pure cover rules, unit tested. */
object CoverRules {
    fun songKey(songId: Long) = "${CoverScope.SONG}:$songId"
    fun albumKey(albumId: Long) = "${CoverScope.ALBUM}:$albumId"

    /** 1. song cover, 2. album cover, 3. embedded or MediaStore art, 4. none. */
    fun resolve(songId: Long, albumId: Long, covers: Map<String, CustomCover>, hasOriginal: Boolean): CoverSource {
        covers[songKey(songId)]?.takeIf { songId > 0 }?.let { return CoverSource.Custom(it) }
        covers[albumKey(albumId)]?.takeIf { albumId > 0 }?.let { return CoverSource.Custom(it) }
        return if (hasOriginal) CoverSource.Original else CoverSource.None
    }

    /** The custom cover that applies, if any (steps 1 and 2 of [resolve]). */
    fun custom(songId: Long, albumId: Long, covers: Map<String, CustomCover>): CustomCover? =
        (resolve(songId, albumId, covers, hasOriginal = true) as? CoverSource.Custom)?.cover

    fun identity(songId: Long, albumId: Long, originalVersion: Long, covers: Map<String, CustomCover>): CoverIdentity {
        val custom = custom(songId, albumId, covers)
        return when (custom?.scope) {
            CoverScope.SONG -> CoverIdentity(-songId, -custom.version, CoverScope.SONG)
            CoverScope.ALBUM -> CoverIdentity(albumId, -custom.version, CoverScope.ALBUM)
            null -> CoverIdentity(if (albumId > 0) albumId else -songId, originalVersion, null)
        }
    }

    /** Next version: always larger than the last one, so every change gets a fresh cache key. */
    fun nextVersion(previous: Long?, now: Long): Long = maxOf(now, (previous ?: 0L) + 1)

    /**
     * A stable colour for artwork that doesn't exist: a chromatic swatch picked by a hash of the
     * album name (or the file name), so it always looks intentional and never changes.
     */
    fun fallbackColour(albumName: String?, fileName: String): Argb {
        val name = albumName?.takeIf { it.isNotBlank() } ?: fileName
        // FNV-1a: stable across runs and JVMs (String.hashCode is too, but this spreads better).
        var h = FNV_OFFSET
        name.lowercase().forEach { h = (h xor it.code.toLong()) * FNV_PRIME }
        val swatches = Swatches.chromatic
        return swatches[Math.floorMod(h, swatches.size.toLong()).toInt()].colour
    }

    private const val FNV_OFFSET = -0x340d631b7bdddcdbL
    private const val FNV_PRIME = 0x100000001b3L

    /** Decode sample size so the long edge is at least [maxEdge] after sampling, then scaled down. */
    fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        val long = maxOf(width, height)
        while (long / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    /**
     * The square crop of a [width] × [height] image: the short edge, positioned along the long
     * edge by [offset] (0 = start, 0.5 = centre, 1 = end).
     */
    fun squareCrop(width: Int, height: Int, offset: Float): IntArray {
        val side = minOf(width, height)
        val slack = maxOf(width, height) - side
        val start = (slack * offset.coerceIn(0f, 1f)).toInt()
        return if (width >= height) intArrayOf(start, 0, side, side) else intArrayOf(0, start, side, side)
    }
}

/**
 * The custom covers, kept in memory as well as in Room (the table holds one row per changed
 * cover, so it is tiny). Lookups are synchronous for the image loader and cache keys.
 */
@Singleton
class CoverOverrides @Inject constructor(
    private val dao: CoverOverrideDao,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _covers = MutableStateFlow<Map<String, CustomCover>>(emptyMap())
    val covers: StateFlow<Map<String, CustomCover>> = _covers.asStateFlow()

    init {
        scope.launch {
            dao.observeAll().collect { rows -> _covers.value = rows.associate { r -> keyOf(r) to r.toCover() } }
        }
    }

    fun custom(songId: Long, albumId: Long): CustomCover? = CoverRules.custom(songId, albumId, _covers.value)

    fun identity(songId: Long, albumId: Long, originalVersion: Long): CoverIdentity =
        CoverRules.identity(songId, albumId, originalVersion, _covers.value)

    /** Emits only when this song's applicable cover changes; list rows collect this. */
    fun versionOf(songId: Long, albumId: Long): Flow<CustomCover?> = covers.map { CoverRules.custom(songId, albumId, it) }.distinctUntilChanged()

    private fun keyOf(r: CoverOverrideEntity) = if (r.scope == CoverScope.SONG.name) CoverRules.songKey(r.key) else CoverRules.albumKey(r.key)

    private fun CoverOverrideEntity.toCover() = CustomCover(
        scope = if (scope == CoverScope.SONG.name) CoverScope.SONG else CoverScope.ALBUM,
        key = key, file = File(path), version = version,
    )
}
