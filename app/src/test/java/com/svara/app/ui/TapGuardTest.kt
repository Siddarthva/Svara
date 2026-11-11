package com.svara.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapGuardTest {
    private var now = 1_000L
    private val guard = TapGuard(windowMs = 300L, clock = { now })

    @Test
    fun `a double tap acts once`() {
        var plays = 0
        guard.run("play") { plays++ }
        now += 120
        guard.run("play") { plays++ }
        assertEquals(1, plays)
    }

    @Test
    fun `the same action works again after the window`() {
        assertTrue(guard.accept("next"))
        now += 300
        assertTrue(guard.accept("next"))
    }

    @Test
    fun `different actions never block each other`() {
        assertTrue(guard.accept("play"))
        assertTrue(guard.accept("next"))
        assertTrue(guard.accept("favourite" to 7L))
        assertTrue(guard.accept("favourite" to 8L))
        assertFalse(guard.accept("favourite" to 7L))
    }

    @Test
    fun `queueing the same songs twice in a burst queues them once`() {
        assertTrue(guard.accept("queue" to listOf(1L, 2L)))
        now += 50
        assertFalse(guard.accept("queue" to listOf(1L, 2L)))
        assertTrue(guard.accept("queue" to listOf(3L)))
    }

    @Test
    fun `a clock that goes backwards does not lock an action out`() {
        assertTrue(guard.accept("play"))
        now -= 10_000
        assertTrue(guard.accept("play"))
    }
}
