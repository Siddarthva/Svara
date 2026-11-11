package com.svara.app.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.svara.app.di.AppearanceEntryPoint
import com.svara.app.domain.audio.EqPreset
import com.svara.app.domain.audio.EqSettings
import com.svara.app.widget.WidgetCommands
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
 * Phase 3 and 4 on the real service: equalizer changes never interrupt playback; widget commands
 * control the real session, and the widget snapshot follows track and play-state changes.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class IntegrationPlaybackTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val entry = EntryPointAccessors.fromApplication(context, AppearanceEntryPoint::class.java)
    private var controller: MediaController? = null

    @After
    fun tearDown() {
        runBlocking {
            withContext(Dispatchers.Main) { controller?.run { stop(); clearMediaItems(); release() } }
            entry.settings().update { it.copy(eq = EqSettings()) }
        }
    }

    private suspend fun <T> MediaController.onMain(block: MediaController.() -> T): T = withContext(Dispatchers.Main) { block() }

    private suspend fun start(): MediaController {
        val tracks = (1..3).map { i -> Uri.fromFile(writeTone(File(context.cacheDir, "integration_tone_$i.wav"), 250.0 + i * 100, seconds = 300)) }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val c = withContext(Dispatchers.Main) { MediaController.Builder(context, token).buildAsync().await() }.also { controller = it }
        c.onMain {
            shuffleModeEnabled = false
            setMediaItems(tracks.mapIndexed { i, uri -> MediaItem.Builder().setMediaId(MediaItems.newMediaId(9_800L + i)).setUri(uri).build() }, 0, 1_000)
            prepare()
            play()
        }
        withTimeout(10_000) { while (!c.onMain { isPlaying && currentPosition > 1_500 }) delay(50) }
        return c
    }

    @Test
    fun equalizerChangesNeverInterruptPlayback() = runBlocking {
        val c = start()
        val players = PlaybackService.playersCreated.get()
        val settings = entry.settings()
        val steps = listOf(
            EqSettings(enabled = true, preset = EqPreset.BASS_BOOST.name),
            EqSettings(enabled = true, preset = EqPreset.VOCAL.name, bassBoost = 500, virtualizer = 300, loudnessMb = 400),
            EqSettings(enabled = true, preset = null, bandLevelsMb = listOf(300, -300, 0, 300, -300)),
            EqSettings(enabled = false),
        )
        var last = c.onMain { currentPosition }
        steps.forEach { eq ->
            settings.update { it.copy(eq = eq) }
            delay(600)
            assertTrue("equalizer change $eq stopped playback", c.onMain { isPlaying })
            val now = c.onMain { currentPosition }
            assertTrue("position went backwards", now >= last)
            last = now
            assertEquals(players, PlaybackService.playersCreated.get())
        }
        // The emulator's effects attach to the session; what's supported is reported, not assumed.
        assertTrue("effects attached to the player's session", entry.effects().capabilities.value.attached)
    }

    @Test
    fun widgetCommandsControlTheRealSessionAndTheSnapshotFollows() = runBlocking {
        val c = start()
        val store = entry.widgetStore()
        withTimeout(5_000) { while (!store.read().isPlaying) delay(100) }
        assertEquals(9_800L, store.read().songId)

        WidgetCommands.playPause(context)
        withTimeout(5_000) { while (c.onMain { isPlaying }) delay(50) }
        withTimeout(5_000) { while (store.read().isPlaying) delay(100) }

        WidgetCommands.playPause(context)
        withTimeout(5_000) { while (!c.onMain { isPlaying }) delay(50) }

        WidgetCommands.next(context)
        withTimeout(5_000) { while (c.onMain { currentMediaItemIndex } != 1) delay(50) }
        withTimeout(5_000) { while (store.read().songId != 9_801L) delay(100) }

        WidgetCommands.previous(context)
        withTimeout(5_000) { while (c.onMain { currentMediaItemIndex } != 0) delay(50) }
        assertTrue("still playing after widget commands", c.onMain { isPlaying })
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
