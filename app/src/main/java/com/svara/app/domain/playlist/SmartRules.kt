package com.svara.app.domain.playlist

import com.svara.app.domain.library.TextKeys
import com.svara.app.domain.model.Song

/** What a rule-builder condition looks at. Local metadata and local play history only; no mood. */
enum class SmartField(val label: String, val kind: Kind) {
    ADDED_WITHIN_DAYS("Added in the last (days)", Kind.NUMBER),
    PLAYS_AT_LEAST("Played at least (times)", Kind.NUMBER),
    PLAYS_AT_MOST("Played at most (times)", Kind.NUMBER),
    FAVOURITE("Is a favourite", Kind.FLAG),
    ARTIST("Artist contains", Kind.TEXT),
    ALBUM("Album contains", Kind.TEXT),
    FOLDER("Folder starts with", Kind.TEXT),
    GENRE("Genre contains", Kind.TEXT),
    LONGER_THAN_MIN("Longer than (minutes)", Kind.NUMBER),
    SHORTER_THAN_MIN("Shorter than (minutes)", Kind.NUMBER),
    ;

    enum class Kind { NUMBER, TEXT, FLAG }
}

data class SmartCondition(val field: SmartField, val value: String = "")

enum class SmartMatch(val label: String) { ALL("Match all rules"), ANY("Match any rule") }

enum class SmartSort(val label: String) {
    RECENTLY_ADDED("Recently added"),
    MOST_PLAYED("Most played"),
    RECENTLY_PLAYED("Recently played"),
    TITLE("Title"),
    ARTIST("Artist"),
    LONGEST("Longest first"),
}

data class SmartRules(
    val match: SmartMatch = SmartMatch.ALL,
    val conditions: List<SmartCondition> = emptyList(),
    val sort: SmartSort = SmartSort.RECENTLY_ADDED,
)

/** One song with the local statistics rules can use. */
data class RuleSong(val song: Song, val plays: Int, val lastPlayed: Long, val favourite: Boolean)

/** Pure evaluation and storage of rule-builder playlists. */
object SmartRuleEngine {

    /** Conditions with an unusable value (empty text, non-numbers) are ignored rather than matching nothing. */
    fun evaluate(rules: SmartRules, songs: List<RuleSong>, limit: Int, nowMillis: Long): List<Song> {
        val active = rules.conditions.filter { usable(it) }
        val matched = if (active.isEmpty()) songs else songs.filter { s ->
            when (rules.match) {
                SmartMatch.ALL -> active.all { matches(it, s, nowMillis) }
                SmartMatch.ANY -> active.any { matches(it, s, nowMillis) }
            }
        }
        val sorted = when (rules.sort) {
            SmartSort.RECENTLY_ADDED -> matched.sortedWith(compareByDescending<RuleSong> { it.song.dateAddedSec }.thenByDescending { it.song.id })
            SmartSort.MOST_PLAYED -> matched.sortedWith(compareByDescending<RuleSong> { it.plays }.thenByDescending { it.lastPlayed }.thenBy { it.song.id })
            SmartSort.RECENTLY_PLAYED -> matched.filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed } +
                matched.filter { it.lastPlayed <= 0 }.sortedByDescending { it.song.dateAddedSec }
            SmartSort.TITLE -> matched.sortedBy { TextKeys.sort(it.song.title) }
            SmartSort.ARTIST -> matched.sortedWith(compareBy<RuleSong> { TextKeys.sort(it.song.artist) }.thenBy { TextKeys.sort(it.song.album) }.thenBy { it.song.trackNumber })
            SmartSort.LONGEST -> matched.sortedByDescending { it.song.durationMs }
        }
        return sorted.take(limit.coerceAtLeast(0)).map { it.song }
    }

    fun usable(c: SmartCondition): Boolean = when (c.field.kind) {
        SmartField.Kind.FLAG -> true
        SmartField.Kind.NUMBER -> c.value.trim().toDoubleOrNull()?.let { it >= 0 } == true
        SmartField.Kind.TEXT -> c.value.isNotBlank()
    }

    private fun matches(c: SmartCondition, s: RuleSong, now: Long): Boolean {
        val number = c.value.trim().toDoubleOrNull() ?: 0.0
        val text = TextKeys.search(c.value)
        return when (c.field) {
            SmartField.ADDED_WITHIN_DAYS -> s.song.dateAddedSec * MS >= now - (number * DAY_MS).toLong()
            SmartField.PLAYS_AT_LEAST -> s.plays >= number
            SmartField.PLAYS_AT_MOST -> s.plays <= number
            SmartField.FAVOURITE -> s.favourite
            SmartField.ARTIST -> TextKeys.search(s.song.artist).contains(text) || TextKeys.search(s.song.albumArtist.orEmpty()).contains(text)
            SmartField.ALBUM -> TextKeys.search(s.song.album).contains(text)
            SmartField.FOLDER -> TextKeys.search(s.song.relativePath).startsWith(TextKeys.search(c.value.trim('/') + "/")) ||
                TextKeys.search(s.song.relativePath).trimEnd('/') == text.trimEnd('/')
            SmartField.GENRE -> TextKeys.search(s.song.genre.orEmpty()).contains(text)
            SmartField.LONGER_THAN_MIN -> s.song.durationMs > number * MINUTE_MS
            SmartField.SHORTER_THAN_MIN -> s.song.durationMs in 1 until (number * MINUTE_MS).toLong()
        }
    }

    // Storage: one line per item. Line 1: match and sort; then FIELD<TAB>value per condition.
    fun encode(rules: SmartRules): String = buildString {
        append(rules.match.name).append('\t').append(rules.sort.name)
        rules.conditions.forEach { append('\n').append(it.field.name).append('\t').append(escape(it.value)) }
    }

    /** Unknown or corrupt parts fall back to defaults; unknown fields are dropped. */
    fun decode(raw: String?): SmartRules? {
        if (raw.isNullOrBlank()) return null
        val lines = raw.split('\n')
        val head = lines.first().split('\t')
        val match = SmartMatch.entries.firstOrNull { it.name == head.getOrNull(0) } ?: SmartMatch.ALL
        val sort = SmartSort.entries.firstOrNull { it.name == head.getOrNull(1) } ?: SmartSort.RECENTLY_ADDED
        val conditions = lines.drop(1).mapNotNull { line ->
            val tab = line.indexOf('\t')
            val name = if (tab < 0) line else line.substring(0, tab)
            val field = SmartField.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
            SmartCondition(field, if (tab < 0) "" else unescape(line.substring(tab + 1)))
        }
        return SmartRules(match, conditions, sort)
    }

    private fun escape(v: String) = v.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unescape(v: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < v.length) {
            val c = v[i]
            if (c == '\\' && i + 1 < v.length) {
                when (v[i + 1]) {
                    't' -> out.append('\t')
                    'n' -> out.append('\n')
                    else -> out.append(v[i + 1])
                }
                i += 2
            } else {
                out.append(c); i++
            }
        }
        return out.toString()
    }

    private const val MS = 1000L
    private const val MINUTE_MS = 60_000.0
    private const val DAY_MS = 86_400_000.0
}
