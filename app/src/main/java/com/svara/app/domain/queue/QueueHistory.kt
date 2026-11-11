package com.svara.app.domain.queue

import com.svara.app.domain.model.QueueState

/**
 * Bounded undo stack for queue edits. Each entry is the queue as it was *before* an edit, with a
 * short label for the undo snackbar ("Removed “Song”").
 */
class QueueHistory(private val capacity: Int = 20) {

    data class Entry(val label: String, val before: QueueState)

    private val entries = ArrayDeque<Entry>()

    val size: Int get() = entries.size
    val latest: Entry? get() = entries.lastOrNull()

    fun push(label: String, before: QueueState) {
        entries.addLast(Entry(label, before))
        while (entries.size > capacity) entries.removeFirst()
    }

    fun pop(): Entry? = entries.removeLastOrNull()

    fun clear() = entries.clear()
}
