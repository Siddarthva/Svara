package com.svara.app.playback

import android.content.ComponentName
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.room.Room
import com.svara.app.data.db.RoomTransactionRunner
import com.svara.app.data.db.SvaraDatabase
import com.svara.app.data.repository.QueueSnapshotRepositoryImpl
import com.svara.app.data.db.SongEntity
import com.svara.app.domain.library.TextKeys
import com.svara.app.domain.model.QueueSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Simulates a device restart: a queue saved by an earlier process must come back when the
 * playback service starts fresh — restored paused, never auto-playing.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class RestoreAfterRestartTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun entity(id: Long, title: String) = SongEntity(
        id = id, title = title, artist = "Test", album = "Restore", albumArtist = null, albumId = 777, artistId = 1,
        durationMs = 200_000, trackNumber = 1, discNumber = 1, year = 2024, dateAddedSec = 1, dateModifiedSec = 1,
        sizeBytes = 1, mimeType = "audio/mpeg", genre = null, volume = "primary", relativePath = "Music/", fileName = "$title.mp3",
        dataPath = null, titleKey = TextKeys.sort(title), artistKey = "test", albumKey = "restore", searchText = title.lowercase(),
    )

    @Suppress("DEPRECATION") // still reports the caller's own services
    private fun serviceRunning(): Boolean =
        context.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == PlaybackService::class.java.name }

    @Test
    fun queueComesBackPausedAtTheSavedPosition() = runBlocking {
        // A second connection to the app's database file, as a previous process would have left it.
        val db = Room.databaseBuilder(context, SvaraDatabase::class.java, SvaraDatabase.NAME).build()
        val snapshots = QueueSnapshotRepositoryImpl(db.queueDao(), RoomTransactionRunner(db))
        val ids = listOf(990_001L, 990_002L, 990_003L)
        db.songDao().upsert(ids.mapIndexed { i, id -> entity(id, "Restore $i") })

        val saved = QueueSnapshot(ids, currentIndex = 1, positionMs = 42_000, shuffleEnabled = false, shuffleOrder = emptyList(), repeatMode = Player.REPEAT_MODE_ALL, speed = 1f, pitch = 1f)
        // Make sure no service from an earlier test is alive: its shutdown would write its own snapshot.
        // System UI's media-resume check may bind the service again right after it stops (seen on
        // API 37); that instance restores before our snapshot exists, so retry until the service
        // stays down across the save.
        val stayedDown = (1..5).any {
            context.stopService(Intent(context, PlaybackService::class.java))
            checkNotNull(withTimeoutOrNull(10_000) { while (serviceRunning()) delay(100) }) { "old service didn't stop" }
            // Its onDestroy saves a final snapshot asynchronously; let that land before writing ours.
            delay(1_000)
            snapshots.save(saved)
            delay(1_000)
            !serviceRunning()
        }
        check(stayedDown) { "the playback service kept being restarted by the system" }

        val controller = withContext(Dispatchers.Main) {
            MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync().await()
        }
        try {
            checkNotNull(withTimeoutOrNull(10_000) {
                while (withContext(Dispatchers.Main) { controller.mediaItemCount } < ids.size) delay(50)
            }) { "queue was not restored; saved snapshot = ${snapshots.load()}" }
            withContext(Dispatchers.Main) {
                assertEquals(ids, (0 until controller.mediaItemCount).map { MediaItems.songIdOf(controller.getMediaItemAt(it).mediaId) })
                assertEquals(1, controller.currentMediaItemIndex)
                assertEquals(42_000, controller.currentPosition)
                assertEquals(Player.REPEAT_MODE_ALL, controller.repeatMode)
                assertFalse("restored queue must not auto-play", controller.playWhenReady)
                assertEquals(Player.STATE_IDLE, controller.playbackState)
            }
        } finally {
            withContext(Dispatchers.Main) {
                controller.clearMediaItems()
                controller.release()
            }
            db.songDao().deleteByIds(ids)
            db.close()
        }
    }
}
