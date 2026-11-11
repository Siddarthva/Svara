package com.svara.app.domain

import com.svara.app.domain.queue.QueueMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Hundreds of random queue edits (inserts after the current song or at the end, removals, moves)
 * applied to the play-order arithmetic and checked against a plain list model after every step:
 * the order stays a valid permutation, the current song never changes unless removed, and inserted
 * items land exactly where asked.
 */
class QueueStressTest {

    @Test
    fun `random edits keep the play order consistent with a simple model`() {
        repeat(SEEDS) { seed ->
            val random = Random(seed)
            // Underlying list holds song ids; the play order is a permutation of underlying indices.
            val underlying = (1L..START_SIZE).toMutableList()
            var order = QueueMath.shuffledStartingWith(underlying.size, 0, random)
            var currentPos = 0
            val current = underlying[order[currentPos]]
            var nextId = START_SIZE + 1
            fun model() = order.map { underlying[it] }

            repeat(OPERATIONS) { step ->
                when (random.nextInt(4)) {
                    0, 1 -> {
                        // Insert 1–3 songs: play next (after current) or at the end.
                        val count = random.nextInt(1, 4)
                        val playNext = random.nextBoolean()
                        val before = model()
                        val newIds = (0 until count).map { nextId++ }
                        val insertAt = underlying.size
                        underlying.addAll(insertAt, newIds)
                        order = QueueMath.insertIntoOrder(order, insertAt, count, if (playNext) order[currentPos] else null)
                        val after = model()
                        val expected = if (playNext) before.subList(0, currentPos + 1) + newIds + before.subList(currentPos + 1, before.size) else before + newIds
                        assertEquals("seed $seed step $step insert", expected, after)
                    }
                    2 -> if (order.size > 1) {
                        // Remove a song that isn't playing.
                        val pos = (0 until order.size).filter { it != currentPos }.random(random)
                        val removedIndex = order[pos]
                        val before = model()
                        underlying.removeAt(removedIndex)
                        order = QueueMath.removeFromOrder(order, removedIndex)
                        if (pos < currentPos) currentPos--
                        assertEquals("seed $seed step $step remove", before.filterIndexed { i, _ -> i != pos }, model())
                    }
                    else -> if (order.size > 1) {
                        val from = random.nextInt(order.size)
                        val to = random.nextInt(order.size)
                        val before = model()
                        order = QueueMath.move(order, from, to)
                        currentPos = when {
                            from == currentPos -> to
                            from < currentPos && to >= currentPos -> currentPos - 1
                            from > currentPos && to <= currentPos -> currentPos + 1
                            else -> currentPos
                        }
                        assertEquals("seed $seed step $step move", QueueMath.move(before, from, to), model())
                    }
                }
                assertTrue("seed $seed step $step: not a permutation", QueueMath.isValidOrder(order, underlying.size))
                assertEquals("seed $seed step $step: current song changed", current, model()[currentPos])
                assertEquals("seed $seed step $step: duplicate ids", underlying.size, underlying.toSet().size)
            }
        }
    }

    private companion object {
        const val SEEDS = 20
        const val OPERATIONS = 400
        const val START_SIZE = 25L
    }
}
