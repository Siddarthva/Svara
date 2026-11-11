package com.svara.app.data

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.svara.app.appearance.colour.Swatches
import com.svara.app.data.appearance.PaletteRepository
import com.svara.app.data.artwork.Artwork
import com.svara.app.data.artwork.ArtworkLoader
import com.svara.app.data.artwork.VersionedLruCache
import com.svara.app.data.covers.CoverOverrides
import com.svara.app.data.covers.CoverRepository
import com.svara.app.data.covers.CoverResult
import com.svara.app.data.covers.CoverRules
import com.svara.app.data.covers.CoverScope
import com.svara.app.data.covers.CoverSource
import com.svara.app.data.covers.CustomCover
import com.svara.app.data.db.SvaraDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File

/** Native graphics: real image decoding, so corrupt bytes really fail to decode. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoversTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, SvaraDatabase::class.java).allowMainThreadQueries().build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After fun tearDown() {
        scope.cancel()
        db.close()
    }

    private val song = CustomCover(CoverScope.SONG, 7, File("s.jpg"), 3)
    private val album = CustomCover(CoverScope.ALBUM, 70, File("a.jpg"), 5)

    // ---- Resolution order ------------------------------------------------------------------

    @Test
    fun `resolution order covers every combination`() {
        val both = mapOf(CoverRules.songKey(7) to song, CoverRules.albumKey(70) to album)
        val onlyAlbum = mapOf(CoverRules.albumKey(70) to album)
        val onlySong = mapOf(CoverRules.songKey(7) to song)
        listOf(true, false).forEach { original ->
            assertEquals(CoverSource.Custom(song), CoverRules.resolve(7, 70, both, original))
            assertEquals(CoverSource.Custom(song), CoverRules.resolve(7, 70, onlySong, original))
            assertEquals(CoverSource.Custom(album), CoverRules.resolve(7, 70, onlyAlbum, original))
            // Another song on the same album gets the album cover, not this song's.
            assertEquals(CoverSource.Custom(album), CoverRules.resolve(8, 70, both, original))
        }
        assertEquals(CoverSource.Original, CoverRules.resolve(7, 70, emptyMap(), hasOriginal = true))
        assertEquals(CoverSource.None, CoverRules.resolve(7, 70, emptyMap(), hasOriginal = false))
        // A song without an album can't pick up an album cover.
        assertEquals(CoverSource.Original, CoverRules.resolve(9, -1, onlyAlbum, hasOriginal = true))
    }

    // ---- Version bump invalidates every cache key --------------------------------------------

    @Test
    fun `a cover version bump changes the Coil key, palette identity and blur key`() {
        val v1 = mapOf(CoverRules.albumKey(70) to album)
        val v2 = mapOf(CoverRules.albumKey(70) to album.copy(version = 6))
        val id1 = CoverRules.identity(7, 70, 1000, v1)
        val id2 = CoverRules.identity(7, 70, 1000, v2)
        assertEquals(id1.key, id2.key)
        assertNotEquals("palette entry is re-extracted", id1.version, id2.version)
        assertNotEquals(Artwork(7, 70, CoverScope.ALBUM, 5).cacheKey, Artwork(7, 70, CoverScope.ALBUM, 6).cacheKey)
        // Removing the cover goes back to the file's version, which never equals a cover version.
        val original = CoverRules.identity(7, 70, 1000, emptyMap())
        assertEquals(1000, original.version)
        assertTrue(id1.version < 0 && id2.version < 0)
        // Song covers key the song alone.
        assertEquals(-7L, CoverRules.identity(7, 70, 1000, mapOf(CoverRules.songKey(7) to song)).key)
        // The blur cache rebuilds on a new version for the same key.
        val blur = VersionedLruCache<String, String>(24)
        var builds = 0
        blur.getOrBuild("${id1.key}:3", id1.version) { builds++; "b1" }
        blur.getOrBuild("${id1.key}:3", id1.version) { builds++; "b1" }
        blur.getOrBuild("${id2.key}:3", id2.version) { builds++; "b2" }
        assertEquals(2, builds)
    }

    @Test
    fun `versions always increase`() {
        assertEquals(5_000L, CoverRules.nextVersion(null, 5_000))
        assertEquals(9_001L, CoverRules.nextVersion(9_000, 5_000))
    }

    // ---- Fallback colour ---------------------------------------------------------------------

    @Test
    fun `fallback colour is deterministic and always a chromatic swatch`() {
        val a = CoverRules.fallbackColour("Blue Train", "x.mp3")
        assertEquals(a, CoverRules.fallbackColour("Blue Train", "other.mp3"))
        assertEquals(a, CoverRules.fallbackColour("blue train", "x.mp3"))
        assertEquals(CoverRules.fallbackColour(null, "track01.flac"), CoverRules.fallbackColour("", "track01.flac"))
        val chromatic = Swatches.chromatic.map { it.colour }.toSet()
        listOf("A", "Kind of Blue", "Abbey Road", "x", "Rāga Sketches").forEach { assertTrue(it, CoverRules.fallbackColour(it, "f") in chromatic) }
    }

    // ---- Crop and sampling -------------------------------------------------------------------

    @Test
    fun `square crop follows the offset along the long edge`() {
        assertEquals(listOf(0, 0, 600, 600), CoverRules.squareCrop(1000, 600, 0f).toList())
        assertEquals(listOf(200, 0, 600, 600), CoverRules.squareCrop(1000, 600, 0.5f).toList())
        assertEquals(listOf(400, 0, 600, 600), CoverRules.squareCrop(1000, 600, 1f).toList())
        assertEquals(listOf(0, 300, 400, 400), CoverRules.squareCrop(400, 1000, 0.5f).toList())
        assertEquals(1, CoverRules.sampleSize(800, 800, 1024))
        // 9000 / 8 = 1125: still at least 1024 on the long edge, then scaled the rest of the way.
        assertEquals(8, CoverRules.sampleSize(9000, 6000, 1024))
    }

    // ---- Processing never throws -------------------------------------------------------------

    private fun repository() = CoverRepository(context, db.coverOverrideDao(), { 1_000L }, Dispatchers.IO)

    @Test
    fun `corrupt, empty and missing images fall back without exceptions`() = runBlocking {
        assertNull(CoverRepository.decodeBounded { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)) })
        assertNull(CoverRepository.decodeBounded { ByteArrayInputStream(ByteArray(0)) })
        assertNull(CoverRepository.decodeBounded { null })
        assertNull(CoverRepository.decodeBounded { throw SecurityException("revoked") })
        // A cancelled pick hands back no image.
        assertNull(repository().load(null))
    }

    @Test
    fun `a huge image is sampled and scaled down to 1024 px`() = runBlocking {
        val big = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        val r = repository().save(big, 0.5f, CoverScope.ALBUM, 70)
        assertEquals(CoverResult.Saved, r)
        val row = db.coverOverrideDao().get("ALBUM", 70)!!
        assertTrue(File(row.path).isFile)
        assertTrue(File(row.path).path.contains("covers"))
        assertEquals(CoverResult.Removed, repository().remove(CoverScope.ALBUM, 70))
        assertTrue(!File(row.path).exists())
    }

    @Test
    fun `a missing cover file falls back to the original artwork`() = runBlocking {
        db.coverOverrideDao().upsert(com.svara.app.data.db.CoverOverrideEntity("SONG", 7, "/nonexistent/cover.jpg", 2, 1))
        val covers = CoverOverrides(db.coverOverrideDao(), scope)
        withTimeout(5_000) { covers.covers.first { it.isNotEmpty() } }
        val loader = ArtworkLoader(context, covers)
        // No custom file and no MediaStore art in the test: null (placeholder), and no exception.
        assertNull(loader.load(Artwork(7, 70), 128))
    }

    // ---- Palette cache -------------------------------------------------------------------------

    @Test
    fun `palette is extracted once per cover version and reused across presets`() = runBlocking {
        val covers = CoverOverrides(db.coverOverrideDao(), scope)
        val palettes = PaletteRepository(db.paletteDao(), ArtworkLoader(context, covers), covers, Dispatchers.IO)
        // Five presets asking for the same cover: one extraction.
        repeat(5) { palettes.coloursFor(7, 70, 1000, "Some Album", "a.mp3") }
        assertEquals(1, palettes.extractions.get())
        // No art: the cached fallback colour, stable.
        val first = palettes.coloursFor(7, 70, 1000, "Some Album", "a.mp3")
        assertEquals(CoverRules.fallbackColour("Some Album", "a.mp3"), first.dominant)
        // A new cover version extracts again, once.
        db.coverOverrideDao().upsert(com.svara.app.data.db.CoverOverrideEntity("ALBUM", 70, "/nonexistent/a.jpg", 9, 1))
        withTimeout(5_000) { covers.covers.first { it.isNotEmpty() } }
        repeat(3) { palettes.coloursFor(7, 70, 1000, "Some Album", "a.mp3") }
        assertEquals(2, palettes.extractions.get())
    }
}
