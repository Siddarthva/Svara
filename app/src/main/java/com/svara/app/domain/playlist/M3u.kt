package com.svara.app.domain.playlist

/** One playlist line from an M3U file, with the optional #EXTINF details. */
data class M3uEntry(val location: String, val durationSec: Int?, val artist: String?, val title: String?)

/** Minimal M3U/M3U8 reading and writing. Paths are written relative to storage roots so files survive device moves. */
object M3u {

    fun parse(text: String): List<M3uEntry> {
        val out = ArrayList<M3uEntry>()
        var pendingInfo: Triple<Int?, String?, String?>? = null
        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF:", ignoreCase = true) -> pendingInfo = parseExtInf(line.substring(8))
                line.startsWith("#") -> Unit
                else -> {
                    val (dur, artist, title) = pendingInfo ?: Triple(null, null, null)
                    out += M3uEntry(line.replace('\\', '/'), dur, artist, title)
                    pendingInfo = null
                }
            }
        }
        return out
    }

    /** "#EXTINF:215,Artist - Title" → (215, Artist, Title). Attributes before the comma are ignored. */
    private fun parseExtInf(body: String): Triple<Int?, String?, String?> {
        val comma = body.indexOf(',')
        if (comma < 0) return Triple(body.trim().substringBefore(' ').toIntOrNull(), null, null)
        val duration = body.substring(0, comma).trim().substringBefore(' ').toIntOrNull()?.takeIf { it >= 0 }
        val info = body.substring(comma + 1).trim()
        val dash = info.indexOf(" - ")
        return if (dash > 0) Triple(duration, info.substring(0, dash).trim(), info.substring(dash + 3).trim())
        else Triple(duration, null, info.ifBlank { null })
    }

    data class WriteItem(val location: String, val durationSec: Int, val artist: String, val title: String)

    fun write(items: List<WriteItem>): String = buildString {
        append("#EXTM3U\n")
        for (item in items) {
            append("#EXTINF:").append(item.durationSec).append(',').append(item.artist).append(" - ").append(item.title).append('\n')
            append(item.location).append('\n')
        }
    }

    /** File name of an entry's location ("../Music/A/b.mp3" → "b.mp3"). */
    fun fileName(location: String): String = location.substringAfterLast('/')

    /** The location without leading "./", "../" segments or a storage-root prefix, for suffix matching. */
    fun normalizedTail(location: String): String =
        location.replace('\\', '/')
            .removePrefix("file://")
            .split('/')
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .joinToString("/")
            .lowercase()
}
