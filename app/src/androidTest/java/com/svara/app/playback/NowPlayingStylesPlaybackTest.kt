package com.svara.app.playback

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.svara.app.MainActivity
import com.svara.app.appearance.CustomField
import com.svara.app.appearance.NowPlayingStyle
import com.svara.app.appearance.PresetId
import com.svara.app.appearance.StudioSection
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin

/**
 * With Now Playing open on screen, switching its style (Classic, Cinema, Compact), the artwork
 * aura and the preset must not create a player, interrupt playback, change the queue, move the
 * position backwards or start a scan; and Now Playing must stay open.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@LargeTest
class NowPlayingStylesPlaybackTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val entry = EntryPointAccessors.fromApplication(context, AppearanceEntryPoint::class.java)
    private var controller: MediaController? = null

    @After
    fun tearDown() = runBlocking {
        withContext(Dispatchers.Main) { controller?.run { stop(); clearMediaItems(); release() } }
        entry.themes().apply(com.svara.app.appearance.ThemeConfig())
        StudioSection.entries.forEach { entry.customization().resetSection(it) }
    }

    private suspend fun <T> MediaController.onMain(block: MediaController.() -> T): T = withContext(Dispatchers.Main) { block() }

    @Test
    fun stylesAndPresetsSwitchWhileNowPlayingIsOpen() = runBlocking {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val audio = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        automation.grantRuntimePermission(context.packageName, audio)
        if (Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        entry.flags().setOnboardingComplete()
        entry.themes().setPreset(PresetId.ANAHATA)
        // The live wave runs a frame loop for as long as music plays, so Compose never reports
        // idle; it has its own policy tests. Off here so the switches below can be checked.
        entry.customization().set(CustomField.LIVE_WAVE, "false")

        // Five-minute tones: the song cannot end on its own during the steps below.
        val tracks = (1..3).map { i -> Uri.fromFile(writeTone(File(context.cacheDir, "np_style_tone_$i.wav"), 220.0 + i * 110, seconds = 300)) }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val c = withContext(Dispatchers.Main) { MediaController.Builder(context, token).buildAsync().await() }.also { controller = it }
        c.onMain {
            shuffleModeEnabled = false
            setMediaItems(tracks.mapIndexed { i, uri -> MediaItem.Builder().setMediaId(MediaItems.newMediaId(9_700L + i)).setUri(uri).build() }, 1, 2_000)
            prepare()
            play()
        }
        withTimeout(10_000) { while (!c.onMain { isPlaying && currentPosition > 2_500 }) delay(50) }
        val players = PlaybackService.playersCreated.get()
        val ids = c.onMain { (0 until mediaItemCount).map { getMediaItemAt(it).mediaId } }

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val library = entry.library()
        withTimeout(30_000) { while (library.scanState.value.isScanning) delay(100) }
        val lastScan = library.scanState.value.lastScanAtMillis

        // Open Now Playing from the mini-player, as a user would.
        compose.waitUntilAtLeastOneExists(hasTestTag("mini_player"), 15_000)
        compose.onAllNodesWithTag("mini_player").onFirst().performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Close now playing"), 10_000)

        var lastPosition = c.onMain { currentPosition }
        suspend fun checkUnchanged(what: String) {
            delay(400)
            compose.waitForIdle()
            assertEquals("$what created a player", players, PlaybackService.playersCreated.get())
            assertTrue("$what interrupted playback", c.onMain { isPlaying })
            assertEquals("$what changed the current song", 1, c.onMain { currentMediaItemIndex })
            assertEquals("$what changed the queue", ids, c.onMain { (0 until mediaItemCount).map { getMediaItemAt(it).mediaId } })
            val now = c.onMain { currentPosition }
            assertTrue("$what moved the position backwards ($lastPosition -> $now)", now >= lastPosition)
            lastPosition = now
            assertEquals("$what ran a library scan", lastScan, library.scanState.value.lastScanAtMillis)
            // Now Playing hides the bottom chrome; the mini-player must not come back.
            assertTrue("$what closed Now Playing", compose.onAllNodesWithTag("mini_player").fetchSemanticsNodes().isEmpty())
        }

        val custom = entry.customization()
        val themes = entry.themes()
        NowPlayingStyle.entries.forEach { style ->
            custom.set(CustomField.NOW_PLAYING_STYLE, style.name); checkUnchanged("Style $style")
            PresetId.entries.forEach { preset -> themes.setPreset(preset); checkUnchanged("Style $style, preset ${preset.displayName}") }
        }
        custom.set(CustomField.ARTWORK_AURA, "false"); checkUnchanged("Aura off")
        custom.set(CustomField.ARTWORK_AURA, "true"); checkUnchanged("Aura on")
        scenario.close()
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
