package com.svara.app.playback

import com.svara.app.domain.model.SleepTimerOption
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {

    private class FakeTarget : SleepTimerTarget {
        var pauses = 0
        var pauseAtEnd = false
        val fades = mutableListOf<Float>()
        override fun setFade(volume: Float) {
            fades += volume
        }
        override fun pause() {
            pauses++
        }
        override fun setPauseAtEndOfMediaItems(enabled: Boolean) {
            pauseAtEnd = enabled
        }
    }

    @Test
    fun `timed timer pauses exactly when it expires`() = runTest {
        val target = FakeTarget()
        val timer = SleepTimer { testScheduler.currentTime }
        timer.attach(target, backgroundScope)

        timer.start(SleepTimerOption.MIN_15)
        assertEquals(15 * 60_000L, timer.state.value.endsAtElapsedMs)

        advanceTimeBy(15 * 60_000L - 1)
        runCurrent()
        assertEquals(0, target.pauses)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, target.pauses)
        assertFalse(timer.state.value.isActive)
    }

    @Test
    fun `cancel stops a timed timer`() = runTest {
        val target = FakeTarget()
        val timer = SleepTimer { testScheduler.currentTime }
        timer.attach(target, backgroundScope)
        timer.start(SleepTimerOption.MIN_30)
        advanceTimeBy(10 * 60_000L)
        timer.cancel()
        advanceTimeBy(60 * 60_000L)
        runCurrent()
        assertEquals(0, target.pauses)
        assertFalse(timer.state.value.isActive)
    }

    @Test
    fun `restarting replaces the previous timer`() = runTest {
        val target = FakeTarget()
        val timer = SleepTimer { testScheduler.currentTime }
        timer.attach(target, backgroundScope)
        timer.start(SleepTimerOption.MIN_15)
        advanceTimeBy(5 * 60_000L)
        timer.start(SleepTimerOption.MIN_60)
        advanceTimeBy(20 * 60_000L)
        runCurrent()
        assertEquals(0, target.pauses) // the 15-minute timer would have fired by now
        advanceTimeBy(41 * 60_000L)
        runCurrent()
        assertEquals(1, target.pauses)
    }

    @Test
    fun `end of track arms the player and clears once it pauses there`() = runTest {
        val target = FakeTarget()
        val timer = SleepTimer { testScheduler.currentTime }
        timer.attach(target, backgroundScope)
        timer.start(SleepTimerOption.END_OF_TRACK)
        assertTrue(target.pauseAtEnd)
        assertTrue(timer.state.value.endOfTrack)

        timer.onPausedAtEndOfItem()
        assertFalse(target.pauseAtEnd)
        assertFalse(timer.state.value.isActive)
    }

    @Test
    fun `switching from end of track to timed disarms pause at end`() = runTest {
        val target = FakeTarget()
        val timer = SleepTimer { testScheduler.currentTime }
        timer.attach(target, backgroundScope)
        timer.start(SleepTimerOption.END_OF_TRACK)
        timer.start(SleepTimerOption.MIN_15)
        assertFalse(target.pauseAtEnd)
        assertTrue(timer.state.value.endsAtElapsedMs != null)
    }

    @Test
    fun `fade out lowers volume over the final 30 seconds then pauses and restores`() = runTest {
        val target = FakeTarget()
        val timer = SleepTimer { testScheduler.currentTime }
        timer.attach(target, backgroundScope)
        timer.start(SleepTimerOption.MIN_15, fadeOut = true)

        advanceTimeBy(15 * 60_000L - 30_000L - 1)
        runCurrent()
        assertTrue("no fading before the last 30 s", target.fades.all { it == 1f })

        advanceTimeBy(15_002)
        runCurrent()
        assertTrue("halfway through the fade", target.fades.last() in 0.4f..0.6f)
        assertEquals(0, target.pauses)

        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(1, target.pauses)
        assertEquals(1f, target.fades.last()) // restored for next play
        val ramp = target.fades.dropLastWhile { it == 1f }.dropWhile { it == 1f }
        assertTrue(ramp.zipWithNext().all { (a, b) -> b <= a })
    }

    @Test
    fun `start without a player does nothing`() = runTest {
        val timer = SleepTimer { 0L }
        timer.start(SleepTimerOption.MIN_15)
        assertFalse(timer.state.value.isActive)
    }
}
