package com.svara.app.data.appearance

import com.svara.app.appearance.CustomField
import com.svara.app.data.covers.CoverRules
import com.svara.app.data.covers.CoverOverrides
import androidx.palette.graphics.Palette
import com.svara.app.appearance.Argb
import com.svara.app.appearance.PresetId
import com.svara.app.appearance.ThemeConfig
import com.svara.app.appearance.ThemeConfigCodec
import com.svara.app.data.artwork.Artwork
import com.svara.app.data.artwork.ArtworkLoader
import com.svara.app.data.db.AlbumPaletteEntity
import com.svara.app.data.db.PaletteDao
import com.svara.app.data.db.SavedLookDao
import com.svara.app.data.db.SavedLookEntity
import com.svara.app.data.repository.Clock
import com.svara.app.di.PaletteDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SavedLook(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val config: ThemeConfig,
    /** Customization overrides saved with the look (V3); empty for older looks. */
    val custom: Map<CustomField, String> = emptyMap(),
)

/** Stores customization overrides safely: unit and record separators can't appear in values users type here. */
object CustomOverridesCodec {
    private const val UNIT = '\u001F'
    private const val RECORD = '\u001E'

    fun encode(map: Map<CustomField, String>): String = map.entries.joinToString(RECORD.toString()) { (k, v) -> k.name + UNIT + v }

    /** Unknown fields (from a newer version) are dropped; null or blank gives an empty map. */
    fun decode(raw: String?): Map<CustomField, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return raw.split(RECORD).mapNotNull { rec ->
            val i = rec.indexOf(UNIT)
            if (i <= 0) return@mapNotNull null
            val field = CustomField.entries.firstOrNull { it.name == rec.substring(0, i) } ?: return@mapNotNull null
            field to rec.substring(i + 1)
        }.toMap()
    }
}

/** Saved looks hold a preset id and the overrides only, never a copy of the preset. */
@Singleton
class SavedLookRepository @Inject constructor(
    private val dao: SavedLookDao,
    private val themes: ThemeRepository,
    private val customization: CustomizationRepository? = null,
    private val clock: Clock,
) {
    val looks: Flow<List<SavedLook>> = dao.observeAll().map { rows -> rows.map { it.toLook() } }

    /** "Duplicate preset": saves the current configuration under [name]. */
    suspend fun saveCurrent(name: String): Long = save(name, themes.config.value, customization?.overrides?.value.orEmpty())

    suspend fun save(name: String, config: ThemeConfig, custom: Map<CustomField, String> = emptyMap()): Long {
        val pruned = ThemeRepository.prune(config)
        return dao.insert(
            SavedLookEntity(
                name = name.trim().ifEmpty { "My look" },
                createdAt = clock.now(),
                presetId = pruned.preset.name,
                overrides = ThemeConfigCodec.encodeOverrides(pruned.overrides),
                custom = CustomOverridesCodec.encode(custom),
            )
        )
    }

    /** A copy of a saved look under [name]. */
    suspend fun duplicate(id: Long, name: String): Long? {
        val look = dao.get(id)?.toLook() ?: return null
        return save(name, look.config, look.custom)
    }

    suspend fun rename(id: Long, name: String) {
        if (name.isNotBlank()) dao.rename(id, name.trim())
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Replaces the current overrides with the look's. Touches appearance only, never playback. */
    suspend fun apply(id: Long): Boolean {
        val look = dao.get(id)?.toLook() ?: return false
        themes.apply(look.config)
        // Looks saved before V3 carry no customization: leave the current one alone.
        if (dao.get(id)?.custom != null) customization?.replaceAll(look.custom)
        return true
    }

    private fun SavedLookEntity.toLook() =
        SavedLook(id, name, createdAt, ThemeConfig(PresetId.parse(presetId), ThemeConfigCodec.decodeOverrides(overrides)), CustomOverridesCodec.decode(custom))
}

/**
 * Cover colours (dominant plus two supporting) for the album-artwork source and ambient tints.
 * Extracted once from a 128 px decode of the resolved cover on a background dispatcher, and
 * cached in Room by cover identity (album id, or song id for song covers) and cover version. The
 * cache is shared by every preset; switching preset never extracts again. Covers that don't
 * exist get a stable fallback colour, cached the same way.
 */
@Singleton
class PaletteRepository @Inject constructor(
    private val dao: PaletteDao,
    private val artwork: ArtworkLoader,
    private val covers: CoverOverrides,
    @PaletteDispatcher private val dispatcher: CoroutineDispatcher,
) {
    data class AlbumColours(val accent: Argb?, val muted: Argb?, val dominant: Argb? = null)

    /**
     * The last colours handed out per song and album, in memory and bounded, so a tile that has
     * been shown before starts with its colour instead of flashing the plain placeholder while
     * Room is read again. [coloursFor] stays the source of truth (it checks the cover version).
     */
    private val recent = android.util.LruCache<String, AlbumColours>(RECENT_ENTRIES)

    /** Colours from memory only, never blocking; null when this cover hasn't been seen yet. */
    fun peek(songId: Long, albumId: Long): AlbumColours? = recent.get(recentKey(songId, albumId))

    /** Extractions performed (not cache hits), for tests and measurement. */
    val extractions = java.util.concurrent.atomic.AtomicLong()

    /**
     * [version] is the original artwork's version (the file's modified time); a custom cover's
     * version replaces it. [albumName] and [fileName] pick the fallback colour when there's no art.
     */
    suspend fun coloursFor(songId: Long, albumId: Long, version: Long, albumName: String? = null, fileName: String = ""): AlbumColours =
        resolve(songId, albumId, version, albumName, fileName).also { recent.put(recentKey(songId, albumId), it) }

    private suspend fun resolve(songId: Long, albumId: Long, version: Long, albumName: String?, fileName: String): AlbumColours = withContext(dispatcher) {
        val id = covers.identity(songId, albumId, version)
        dao.get(id.key)?.takeIf { it.version == id.version }?.let {
            return@withContext AlbumColours(it.accent?.let(::Argb), it.muted?.let(::Argb), it.dominant?.let(::Argb))
        }
        extractions.incrementAndGet()
        val bitmap = artwork.load(Artwork(songId, albumId), SAMPLE_PX)
        val colours = if (bitmap == null) {
            val fallback = CoverRules.fallbackColour(albumName, fileName)
            AlbumColours(fallback, null, fallback)
        } else {
            val p = Palette.from(bitmap).maximumColorCount(12).generate()
            val accent = (p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch)?.rgb
            val muted = (p.mutedSwatch ?: p.darkMutedSwatch)?.rgb
            AlbumColours(accent?.toArgb(), muted?.toArgb(), p.dominantSwatch?.rgb?.toArgb())
        }
        dao.upsert(AlbumPaletteEntity(id.key, id.version, colours.accent?.value, colours.muted?.value, colours.dominant?.value))
        colours
    }

    suspend fun clear() {
        recent.evictAll()
        dao.clear()
    }
    suspend fun count(): Int = dao.count()

    private fun Int.toArgb() = Argb(toLong() and 0xFFFFFFFFL)

    private fun recentKey(songId: Long, albumId: Long) = "$songId:$albumId"

    private companion object {
        const val SAMPLE_PX = 128
        const val RECENT_ENTRIES = 512
    }
}
