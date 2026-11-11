package com.svara.app.playback

import com.svara.app.di.CoverEntryPoint
import com.svara.app.data.artwork.Artwork
import com.svara.app.data.covers.CoverResult
import com.svara.app.data.covers.CoverScope
import com.svara.app.appearance.colour.Swatches
import com.svara.app.appearance.BackgroundTone
import com.svara.app.appearance.ColourSystem
import android.graphics.Bitmap
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.svara.app.MainActivity
import com.svara.app.appearance.CustomField
import com.svara.app.appearance.PerformanceMode
import com.svara.app.appearance.PresetId
import com.svara.app.appearance.StudioSection
import com.svara.app.appearance.ThemeField
import com.svara.app.appearance.VisualiserMode
import com.svara.app.di.AppearanceEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin

/**
 * Switching theme, studio settings or performance mode while music plays must not create a
 * player, interrupt playback, change the queue or move the position. Also checks the ZENITH
 * audio tap starts and stops on demand without disturbing playback.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AppearancePlaybackTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val entry = EntryPointAccessors.fromApplication(context, AppearanceEntryPoint::class.java)
    private var controller: MediaController? = null

    @After
    fun tearDown() = runBlocking {
        withContext(Dispatchers.Main) {
            controller?.run { stop(); clearMediaItems(); release() }
        }
        entry.themes().apply(com.svara.app.appearance.ThemeConfig())
        // Only the covers these tests made; never a user's own.
        EntryPointAccessors.fromApplication(context, CoverEntryPoint::class.java).repository().run {
            remove(CoverScope.SONG, 9_501L); remove(CoverScope.ALBUM, 777L)
            remove(CoverScope.SONG, 987_654L); remove(CoverScope.ALBUM, 876_543L)
        }
        StudioSection.entries.forEach { entry.customization().resetSection(it) }
    }

    /** JPEG may move a flat colour by a few levels per channel. */
    private fun assertNear(expected: Int, actual: Int?, what: String = "cover colour") {
        assertTrue("$what: no image", actual != null)
        val a = actual!!
        val close = listOf(16, 8, 0).all { shift -> kotlin.math.abs(((expected shr shift) and 0xFF) - ((a shr shift) and 0xFF)) <= 12 }
        assertTrue("$what: expected ${Integer.toHexString(expected)}, got ${Integer.toHexString(a)}", close)
    }

    private suspend fun <T> MediaController.onMain(block: MediaController.() -> T): T = withContext(Dispatchers.Main) { block() }

    private suspend fun MediaController.awaitCondition(timeoutMs: Long = 10_000, condition: MediaController.() -> Boolean) {
        withTimeout(timeoutMs) { while (!onMain(condition)) delay(50) }
    }

    @Test
    fun themeStudioAndModeChangesLeavePlaybackAlone() = runBlocking {
        // Five minutes: long enough that the song cannot end on its own during the many steps below.
        val tracks = (1..3).map { i -> Uri.fromFile(writeTone(File(context.cacheDir, "appearance_tone_$i.wav"), 200.0 + i * 150, seconds = 300)) }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val c = withContext(Dispatchers.Main) { MediaController.Builder(context, token).buildAsync().await() }.also { controller = it }
        c.onMain {
            shuffleModeEnabled = false
            setMediaItems(tracks.mapIndexed { i, uri -> MediaItem.Builder().setMediaId(MediaItems.newMediaId(9_500L + i)).setUri(uri).build() }, 1, 2_000)
            prepare()
            play()
        }
        c.awaitCondition { isPlaying && currentPosition > 2_500 }
        val players = PlaybackService.playersCreated.get()
        val ids = c.onMain { (0 until mediaItemCount).map { getMediaItemAt(it).mediaId } }

        // The UI is on screen too, so every change recomposes real screens.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val library = entry.library()
        // Let any launch-time scan finish; from here on, nothing may start one.
        withTimeout(30_000) { while (library.scanState.value.isScanning) delay(100) }
        val lastScan = library.scanState.value.lastScanAtMillis
        var lastPosition = c.onMain { currentPosition }
        suspend fun checkUnchanged(what: String) {
            delay(400)
            assertEquals("$what created a player", players, PlaybackService.playersCreated.get())
            assertTrue("$what interrupted playback", c.onMain { isPlaying })
            assertEquals("$what changed the current song", 1, c.onMain { currentMediaItemIndex })
            assertEquals("$what changed the queue", ids, c.onMain { (0 until mediaItemCount).map { getMediaItemAt(it).mediaId } })
            val now = c.onMain { currentPosition }
            assertTrue("$what moved the position backwards ($lastPosition -> $now)", now >= lastPosition)
            lastPosition = now
            assertTrue("$what started a library scan", !library.scanState.value.isScanning)
            assertEquals("$what ran a library scan", lastScan, library.scanState.value.lastScanAtMillis)
        }

        val themes = entry.themes()
        val custom = entry.customization()
        PresetId.entries.forEach { themes.setPreset(it); checkUnchanged("Preset ${it.displayName}") }
        // Back and forth between the layout presets, as a user comparing them would.
        listOf(PresetId.TALA, PresetId.SHRUTI, PresetId.SPANDA, PresetId.RAGA, PresetId.ANAHATA).forEach {
            themes.setPreset(it); checkUnchanged("Switch to ${it.displayName}")
        }
        PerformanceMode.entries.forEach { custom.set(CustomField.PERFORMANCE_MODE, it.name); checkUnchanged("Mode $it") }
        themes.setOverride(ThemeField.VISUALISER, VisualiserMode.FULL_SPECTRUM.name); checkUnchanged("Visualiser")
        custom.set(CustomField.TEXT_SCALE, "130"); checkUnchanged("Text scale")
        custom.set(CustomField.SEEK_INCREMENT, "15"); checkUnchanged("Seek increment")
        custom.set(CustomField.NAV_HIDDEN, "HOME"); checkUnchanged("Navigation")
        themes.resetAll(); checkUnchanged("Reset everything")

        // v2.1: colour, ambient, blur and cover changes.
        themes.edit(mapOf(ThemeField.COLOUR_SYSTEM to ColourSystem.CUSTOM.name, ThemeField.CUSTOM_SWATCH to Swatches.chromatic[3].id)); checkUnchanged("Swatch")
        themes.edit(mapOf(ThemeField.CUSTOM_HUE to "210.0"), remove = setOf(ThemeField.CUSTOM_SWATCH)); checkUnchanged("Hue")
        ColourSystem.entries.filter { it.selectable }.forEach { themes.setOverride(ThemeField.COLOUR_SYSTEM, it.name); checkUnchanged("Source $it") }
        BackgroundTone.entries.forEach { themes.setOverride(ThemeField.BACKGROUND_TONE, it.name); checkUnchanged("Tone $it") }
        themes.setOverride(ThemeField.AMBIENT_INTENSITY, "100"); checkUnchanged("Ambient")
        (1..5).forEach { themes.setOverride(ThemeField.BLUR_STRENGTH, it.toString()); checkUnchanged("Blur $it") }
        themes.setOverride(ThemeField.FROSTED, "true"); checkUnchanged("Frosted")
        themes.setOverride(ThemeField.BLUR_TINT, "true"); checkUnchanged("Tint")
        val covers = EntryPointAccessors.fromApplication(context, CoverEntryPoint::class.java).repository()
        val image = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        assertTrue(covers.save(image, 0.5f, CoverScope.SONG, 9_501L) is CoverResult.Saved); checkUnchanged("Song cover")
        assertTrue(covers.save(image, 0.5f, CoverScope.ALBUM, 777L) is CoverResult.Saved); checkUnchanged("Album cover")
        covers.remove(CoverScope.SONG, 9_501L); checkUnchanged("Remove song cover")
        covers.remove(CoverScope.ALBUM, 777L); checkUnchanged("Remove album cover")
        themes.resetAll(); checkUnchanged("Reset everything (v2.1)")

        scenario.close()
    }

    @Test
    fun removingACustomCoverRestoresTheNextCoverInOrder() = runBlocking {
        val entry = EntryPointAccessors.fromApplication(context, CoverEntryPoint::class.java)
        val repo = entry.repository()
        val overrides = entry.covers()
        val loader = entry.artwork()
        // A song with no artwork of its own (not in MediaStore), on an album with none either.
        val song = 987_654L
        val album = 876_543L
        fun pixel(): Int? = loader.load(Artwork(song, album), 64)?.let { it.getPixel(it.width / 2, it.height / 2) }
        assertEquals("no original artwork to start with", null, pixel())

        val blue = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val green = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GREEN) }
        assertTrue(repo.save(blue, 0.5f, CoverScope.ALBUM, album) is CoverResult.Saved)
        withTimeout(5_000) { while (overrides.custom(song, album) == null) delay(20) }
        assertNear(android.graphics.Color.BLUE, pixel())

        assertTrue(repo.save(green, 0.5f, CoverScope.SONG, song) is CoverResult.Saved)
        withTimeout(5_000) { while (overrides.custom(song, album)?.scope != CoverScope.SONG) delay(20) }
        assertNear(android.graphics.Color.GREEN, pixel(), "song cover wins over album cover")

        repo.remove(CoverScope.SONG, song)
        withTimeout(5_000) { while (overrides.custom(song, album)?.scope != CoverScope.ALBUM) delay(20) }
        assertNear(android.graphics.Color.BLUE, pixel(), "removing the song cover restores the album cover")

        repo.remove(CoverScope.ALBUM, album)
        withTimeout(5_000) { while (overrides.custom(song, album) != null) delay(20) }
        assertEquals("removing the album cover restores the original (none here)", null, pixel())
    }

    @Test
    fun audioTapRunsOnlyWhileWantedAndStopsWithinASecond() = runBlocking {
        val analysis = entry.analysis()
        val tone = Uri.fromFile(writeTone(File(context.cacheDir, "analysis_tone.wav"), 90.0, seconds = 30))
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val c = withContext(Dispatchers.Main) { MediaController.Builder(context, token).buildAsync().await() }.also { controller = it }
        c.onMain {
            setMediaItems(listOf(MediaItem.Builder().setMediaId(MediaItems.newMediaId(9_600L)).setUri(tone).build()))
            prepare()
            play()
        }
        c.awaitCondition { isPlaying }

        val idle = analysis.analysedFrames.get()
        delay(1_000)
        assertEquals("no analysis unless a visual asks for it", idle, analysis.analysedFrames.get())

        analysis.setWanted(true, 30)
        delay(2_000)
        val running = analysis.analysedFrames.get()
        assertTrue("analysis should run while wanted (frames=${running - idle})", running - idle >= 20)
        assertTrue("a bass tone shows up as bass", analysis.bands.value.bass > analysis.bands.value.treble)

        // Paused: stops.
        c.onMain { pause() }
        c.awaitCondition { !isPlaying }
        delay(1_000)
        val paused = analysis.analysedFrames.get()
        delay(1_000)
        assertEquals("analysis must stop within a second of pausing", paused, analysis.analysedFrames.get())

        // Hidden: stops even while playing.
        c.onMain { play() }
        c.awaitCondition { isPlaying }
        delay(500)
        analysis.setWanted(false, 0)
        delay(1_000)
        val hidden = analysis.analysedFrames.get()
        delay(1_000)
        assertEquals("analysis must stop within a second of hiding", hidden, analysis.analysedFrames.get())
        assertTrue("playback continues", c.onMain { isPlaying })
    }

    /** Writes a mono 16-bit PCM WAV sine tone. */
    private fun writeTone(file: File, freq: Double, seconds: Int): File {
        val rate = 22_050
        val samples = rate * seconds
        val data = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val v = (sin(2 * PI * freq * i / rate) * 0.2 * Short.MAX_VALUE).toInt()
            data[i * 2] = (v and 0xFF).toByte()
            data[i * 2 + 1] = (v shr 8 and 0xFF).toByte()
        }
        RandomAccessFile(file, "rw").use { f ->
            f.setLength(0)
            fun i32(x: Int) = f.write(byteArrayOf(x.toByte(), (x shr 8).toByte(), (x shr 16).toByte(), (x shr 24).toByte()))
            fun i16(x: Int) = f.write(byteArrayOf(x.toByte(), (x shr 8).toByte()))
            f.writeBytes("RIFF"); i32(36 + data.size); f.writeBytes("WAVE")
            f.writeBytes("fmt "); i32(16); i16(1); i16(1); i32(rate); i32(rate * 2); i16(2); i16(16)
            f.writeBytes("data"); i32(data.size); f.write(data)
        }
        return file
    }
}
