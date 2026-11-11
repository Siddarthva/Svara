package com.svara.app.domain

import com.svara.app.domain.queue.QueueMath
import com.svara.app.domain.queue.QueueRestore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueueMathTest {

    @Test
    fun `shuffle keeps the chosen song first and contains every index once`() {
        repeat(50) { seed ->
            val order = QueueMath.shuffledStartingWith(20, 7, Random(seed))
            assertEquals(7, order.first())
            assertTrue(QueueMath.isValidOrder(order, 20))
        }
    }

    @Test
    fun `shuffle of empty queue is empty`() {
        assertEquals(emptyList<Int>(), QueueMath.shuffledStartingWith(0, 0))
    }

    @Test
    fun `play next in shuffle places new items right after the current one`() {
        // Underlying [0..4], playing index 2, shuffled play order 2,4,0,3,1.
        val order = listOf(2, 4, 0, 3, 1)
        val insertAt = QueueMath.playNextInsertIndex(currentIndex = 2, queueSize = 5) // 3
        val result = QueueMath.insertIntoOrder(order, insertAt, count = 2, afterUnderlyingIndex = 2)
        // Old 3,4 shift to 5,6; new items are 3,4.
        assertEquals(listOf(2, 3, 4, 6, 0, 5, 1), result)
        assertTrue(QueueMath.isValidOrder(result, 7))
    }

    @Test
    fun `add to queue in shuffle appends to the end of play order`() {
        val result = QueueMath.insertIntoOrder(listOf(1, 0, 2), insertAt = 3, count = 2, afterUnderlyingIndex = null)
        assertEquals(listOf(1, 0, 2, 3, 4), result)
    }

    @Test
    fun `inserting when the anchor shifts still follows the anchor`() {
        // Anchor 3 is at/after the insert point, so it becomes 4.
        val result = QueueMath.insertIntoOrder(listOf(3, 0, 1, 2), insertAt = 1, count = 1, afterUnderlyingIndex = 3)
        assertEquals(listOf(4, 1, 0, 2, 3), result)
    }

    @Test
    fun `remove from order closes the gap`() {
        assertEquals(listOf(1, 0, 2), QueueMath.removeFromOrder(listOf(2, 0, 1, 3), removed = 1))
    }

    @Test
    fun `move reorders and ignores out of range`() {
        assertEquals(listOf("b", "c", "a"), QueueMath.move(listOf("a", "b", "c"), 0, 2))
        assertEquals(listOf("c", "a", "b"), QueueMath.move(listOf("a", "b", "c"), 2, 0))
        assertEquals(listOf("a", "b"), QueueMath.move(listOf("a", "b"), 0, 5))
    }

    @Test
    fun `order validity`() {
        assertTrue(QueueMath.isValidOrder(listOf(2, 0, 1), 3))
        assertFalse(QueueMath.isValidOrder(listOf(0, 0, 1), 3))
        assertFalse(QueueMath.isValidOrder(listOf(0, 1), 3))
    }

    @Test
    fun `play next on an empty queue appends`() {
        assertEquals(0, QueueMath.playNextInsertIndex(-1, 0))
        assertEquals(5, QueueMath.playNextInsertIndex(4, 5))
    }

    @Test
    fun `restore keeps position when the current song still exists`() {
        val r = QueueRestore.restore(listOf(10, 11, 12), 1, 42_000, emptyList()) { true }
        assertEquals(listOf(10L, 11L, 12L), r.songIds)
        assertEquals(1, r.currentIndex)
        assertEquals(42_000, r.positionMs)
    }

    @Test
    fun `restore drops deleted songs and remaps the index`() {
        val r = QueueRestore.restore(listOf(10, 11, 12, 13), 3, 5_000, emptyList()) { it != 11L }
        assertEquals(listOf(10L, 12L, 13L), r.songIds)
        assertEquals(2, r.currentIndex)
        assertEquals(5_000, r.positionMs)
    }

    @Test
    fun `restore moves to the next surviving song in play order when the current one is gone`() {
        // Play order 2,0,3,1; current is underlying 0 (deleted) -> next in play order is 3.
        val r = QueueRestore.restore(listOf(10, 11, 12, 13), 0, 9_000, listOf(2, 0, 3, 1)) { it != 10L }
        assertEquals(listOf(11L, 12L, 13L), r.songIds)
        assertEquals(2, r.currentIndex) // 13 is now at index 2
        assertEquals(0, r.positionMs)
        assertEquals(listOf(1, 2, 0), r.shuffleOrder) // 12,13,11
    }

    @Test
    fun `restore with nothing left is empty`() {
        val r = QueueRestore.restore(listOf(1, 2), 0, 100, emptyList()) { false }
        assertTrue(r.songIds.isEmpty())
    }

    @Test
    fun `restore ignores a corrupt shuffle order`() {
        val r = QueueRestore.restore(listOf(1, 2, 3), 7, 100, listOf(0, 0, 9)) { true }
        assertEquals(2, r.currentIndex) // clamped
        assertTrue(r.shuffleOrder.isEmpty())
    }
}
