package com.svara.app.domain.queue

import kotlin.random.Random

/**
 * Pure queue arithmetic. The player stores items in an "underlying" order; when shuffle is on,
 * a play order (a permutation of underlying indices) decides what plays next. Every function
 * here takes and returns plain lists so it can be unit tested without a player.
 */
object QueueMath {

    /** A random play order over [size] items that starts with [first]. */
    fun shuffledStartingWith(size: Int, first: Int, random: Random = Random.Default): List<Int> {
        if (size <= 0) return emptyList()
        require(first in 0 until size) { "first=$first out of range for size=$size" }
        val rest = (0 until size).filter { it != first }.shuffled(random)
        return listOf(first) + rest
    }

    /**
     * Updates a play order after [count] items were inserted into the underlying list at
     * [insertAt]. Existing indices at or after [insertAt] shift up by [count]. The new items are
     * placed in the play order right after [afterUnderlyingIndex] (pre-insert index), or at the end
     * of the play order when it is null.
     */
    fun insertIntoOrder(order: List<Int>, insertAt: Int, count: Int, afterUnderlyingIndex: Int?): List<Int> {
        if (count <= 0) return order
        val shifted = order.map { if (it >= insertAt) it + count else it }
        val newItems = (insertAt until insertAt + count).toList()
        if (afterUnderlyingIndex == null) return shifted + newItems
        val anchor = if (afterUnderlyingIndex >= insertAt) afterUnderlyingIndex + count else afterUnderlyingIndex
        val pos = shifted.indexOf(anchor)
        if (pos < 0) return shifted + newItems
        return shifted.subList(0, pos + 1) + newItems + shifted.subList(pos + 1, shifted.size)
    }

    /** Updates a play order after the underlying item at [removed] was deleted. */
    fun removeFromOrder(order: List<Int>, removed: Int): List<Int> =
        order.filter { it != removed }.map { if (it > removed) it - 1 else it }

    /** Moves the element at play position [from] to play position [to]. */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from == to || from !in list.indices || to !in list.indices) return list
        val result = list.toMutableList()
        result.add(to, result.removeAt(from))
        return result
    }

    /** Whether [order] is a valid permutation of 0 until [size]. */
    fun isValidOrder(order: List<Int>, size: Int): Boolean =
        order.size == size && order.toSet() == (0 until size).toSet()

    /** Play position at which new items should go when the user picks "Play next". */
    fun playNextInsertIndex(currentIndex: Int, queueSize: Int): Int =
        if (queueSize == 0 || currentIndex < 0) queueSize else (currentIndex + 1).coerceAtMost(queueSize)
}

/** Result of rebuilding a saved queue against the songs that still exist. */
data class RestoredQueue(
    val songIds: List<Long>,
    val currentIndex: Int,
    /** Position to resume at; reset to 0 when the saved current song is gone. */
    val positionMs: Long,
    val shuffleOrder: List<Int>,
)

object QueueRestore {

    /**
     * Drops songs that no longer exist (deleted or moved files) and remaps the current index and
     * shuffle order. If the current song is gone, playback resumes at the next surviving song in
     * play order, from the start.
     */
    fun restore(
        savedIds: List<Long>,
        savedIndex: Int,
        savedPositionMs: Long,
        savedShuffleOrder: List<Int>,
        exists: (Long) -> Boolean,
    ): RestoredQueue {
        if (savedIds.isEmpty()) return RestoredQueue(emptyList(), 0, 0, emptyList())
        val keep = savedIds.map(exists)
        val newIndexOf = IntArray(savedIds.size) { -1 }
        var next = 0
        for (i in savedIds.indices) if (keep[i]) newIndexOf[i] = next++
        val ids = savedIds.filterIndexed { i, _ -> keep[i] }
        if (ids.isEmpty()) return RestoredQueue(emptyList(), 0, 0, emptyList())

        val orderValid = QueueMath.isValidOrder(savedShuffleOrder, savedIds.size)
        val playOrder = if (orderValid) savedShuffleOrder else savedIds.indices.toList()
        val safeIndex = savedIndex.coerceIn(0, savedIds.size - 1)

        val (index, position) = if (keep[safeIndex]) {
            newIndexOf[safeIndex] to savedPositionMs.coerceAtLeast(0)
        } else {
            val start = playOrder.indexOf(safeIndex)
            val survivor = (playOrder.drop(start + 1) + playOrder.take(start)).firstOrNull { keep[it] }
            (survivor?.let { newIndexOf[it] } ?: 0) to 0L
        }
        val newOrder = if (orderValid) playOrder.filter { keep[it] }.map { newIndexOf[it] } else emptyList()
        return RestoredQueue(ids, index, position, newOrder)
    }
}
