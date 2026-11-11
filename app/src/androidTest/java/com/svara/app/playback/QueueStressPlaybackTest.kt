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
import kotlin.random.Random

/**
 * Rapid queue edits on the real playback service while it plays: hundreds of random adds, moves
 * and removals (with and without shuffle) sent back to back. Afterwards the queue matches a plain
 * model, the same song is still playing, playback never stopped, and no player was recreated.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class QueueStressPlaybackTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var controller: MediaController? = null

    @After
    fun tearDown() {
        runBlocking { withContext(Dispatchers.Main) { controller?.run { stop(); clearMediaItems(); release() } } }
    }

    @Test
    fun hundredsOfRapidEditsKeepTheCurrentSongAndPosition() = runBlocking {
        // One five-minute tone, used by every item: the song can't end during the test.
        val uri = Uri.fromFile(writeTone(File(context.cacheDir, "stress_tone.wav"), 330.0, seconds = 300))
        fun item(mediaId: String) = MediaItem.Builder().setMediaId(mediaId).setUri(uri).build()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val c = withContext(Dispatchers.Main) { MediaController.Builder(context, token).buildAsync().await() }.also { controller = it }
        val model = (1L..20L).map { MediaItems.newMediaId(it) }.toMutableList()
        withContext(Dispatchers.Main) {
            c.shuffleModeEnabled = false
            c.setMediaItems(model.map { item(it) }, 5, 1_000)
            c.prepare()
            c.play()
        }
        withTimeout(10_000) { while (!withContext(Dispatchers.Main) { c.isPlaying && c.currentPosition > 1_500 }) delay(50) }
        val players = PlaybackService.playersCreated.get()
        val current = model[5]
        val startPosition = withContext(Dispatchers.Main) { c.currentPosition }

        val random = Random(42)
        var nextId = 1_000L
        withContext(Dispatchers.Main) {
            repeat(OPERATIONS) { step ->
                val currentIndex = c.currentMediaItemIndex
                when (random.nextInt(5)) {
                    0, 1 -> {
                        val at = random.nextInt(model.size + 1)
                        val mediaId = MediaItems.newMediaId(nextId++)
                        model.add(at, mediaId)
                        c.addMediaItem(at, item(mediaId))
                    }
                    2 -> if (model.size > 2) {
                        val index = (model.indices).filter { it != currentIndex }.random(random)
                        model.removeAt(index)
                        c.removeMediaItem(index)
                    }
                    3 -> {
                        val from = random.nextInt(model.size)
                        val to = random.nextInt(model.size)
                        model.add(to, model.removeAt(from))
                        c.moveMediaItem(from, to)
                    }
                    else -> if (step % 25 == 0) c.shuffleModeEnabled = !c.shuffleModeEnabled
                }
            }
        }
        // Let every command reach the service, then compare.
        withTimeout(10_000) {
            while (withContext(Dispatchers.Main) { (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId } } != model) delay(50)
        }
        delay(500)
        withContext(Dispatchers.Main) {
            assertEquals("queue matches the model", model, (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId })
            assertEquals("the same song is still playing", current, c.currentMediaItem?.mediaId)
            assertTrue("playback continued", c.isPlaying)
            assertTrue("position kept moving forward (${c.currentPosition} >= $startPosition)", c.currentPosition >= startPosition)
        }
        assertEquals("no player was recreated", players, PlaybackService.playersCreated.get())
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

    private companion object {
        const val OPERATIONS = 300
    }
}
