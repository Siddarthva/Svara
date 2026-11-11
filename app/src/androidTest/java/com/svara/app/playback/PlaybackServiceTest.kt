package com.svara.app.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin

/**
 * Exercises the real PlaybackService through MediaController, the same way the UI does:
 * service start, play, skip, shuffle, and playback continuing after the UI's controller is
 * released (what happens when the app goes to the background).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PlaybackServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val controllers = mutableListOf<MediaController>()
    private lateinit var tracks: List<Uri>

    @Before
    fun setUp() {
        tracks = (1..3).map { i -> Uri.fromFile(writeTone(File(context.cacheDir, "test_tone_$i.wav"), 300.0 + i * 100, seconds = 8)) }
    }

    @After
    fun tearDown() = runBlocking {
        withContext(Dispatchers.Main) {
            controllers.forEach {
                it.stop()
                it.clearMediaItems()
                it.release()
            }
        }
    }

    private suspend fun connect(): MediaController = withContext(Dispatchers.Main) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        MediaController.Builder(context, token).buildAsync().await().also { controllers += it }
    }

    private suspend fun <T> MediaController.onMain(block: MediaController.() -> T): T = withContext(Dispatchers.Main) { block() }

    private suspend fun MediaController.awaitCondition(timeoutMs: Long = 10_000, condition: MediaController.() -> Boolean) {
        withTimeout(timeoutMs) {
            while (!onMain(condition)) delay(50)
        }
    }

    private fun item(uri: Uri, id: Long) = MediaItem.Builder()
        .setMediaId(MediaItems.newMediaId(id))
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .build()

    @Test
    fun serviceStartsPlaysAndSkips() = runBlocking {
        val controller = connect()
        controller.onMain {
            shuffleModeEnabled = false
            setMediaItems(tracks.mapIndexed { i, uri -> item(uri, 9_000L + i) })
            prepare()
            play()
        }
        controller.awaitCondition { isPlaying }
        controller.awaitCondition { currentPosition > 500 }

        controller.onMain { seekToNext() }
        controller.awaitCondition { currentMediaItemIndex == 1 && isPlaying }

        controller.onMain { pause() }
        controller.awaitCondition { !isPlaying }
        val pausedAt = controller.onMain { currentPosition }
        delay(1_000)
        assertEquals("position must not advance while paused", pausedAt, controller.onMain { currentPosition })
    }

    @Test
    fun turningShuffleOnKeepsTheCurrentSongFirst() = runBlocking {
        val controller = connect()
        controller.onMain {
            shuffleModeEnabled = false
            setMediaItems(tracks.mapIndexed { i, uri -> item(uri, 9_100L + i) }, 2, 0)
            prepare()
            play()
        }
        controller.awaitCondition { isPlaying }
        controller.onMain { shuffleModeEnabled = true }
        controller.awaitCondition { shuffleModeEnabled }
        val first = controller.onMain { currentTimeline.getFirstWindowIndex(true) }
        assertEquals("current song should lead the shuffled order", 2, first)
    }

    @Test
    fun playbackContinuesAfterTheUiControllerIsReleased() = runBlocking {
        val ui = connect()
        ui.onMain {
            setMediaItems(tracks.mapIndexed { i, uri -> item(uri, 9_200L + i) })
            prepare()
            play()
        }
        ui.awaitCondition { isPlaying }
        val before = ui.onMain { currentPosition }

        // The app going to the background releases its controller; the service must keep playing.
        ui.onMain { release() }
        controllers.remove(ui)
        delay(2_000)

        val probe = connect()
        assertTrue("still playing after the UI let go", probe.onMain { isPlaying })
        val after = probe.onMain { currentPosition + currentMediaItemIndex * 8_000L }
        assertTrue("position advanced in the background (before=$before after=$after)", after - before >= 1_500)
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
