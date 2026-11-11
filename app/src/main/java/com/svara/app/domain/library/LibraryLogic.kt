package com.svara.app.domain.library

import com.svara.app.domain.model.Album
import com.svara.app.domain.model.AlbumSortField
import com.svara.app.domain.model.Artist
import com.svara.app.domain.model.ArtistSortField
import com.svara.app.domain.model.FolderKey
import com.svara.app.domain.model.FolderRule
import com.svara.app.domain.model.FolderRuleType
import com.svara.app.domain.model.Song
import com.svara.app.domain.model.SongSortField
import com.svara.app.domain.model.SortOrder
import com.svara.app.domain.model.SongFilter
import com.svara.app.domain.model.SystemFolders
import java.text.Normalizer
import java.util.Locale

/** Decides which folders are visible given the user's include/exclude rules. */
object FolderFilter {

    /**
     * The most specific (longest) matching rule wins. When at least one INCLUDE rule exists,
     * folders outside every included folder are hidden.
     */
    fun isVisible(folder: FolderKey, rules: List<FolderRule>): Boolean {
        if (rules.isEmpty()) return true
        // Longest match wins; on a tie the user's own rule beats a built-in default.
        val match = rules.filter { folder.isWithin(it.key) }
            .maxWithOrNull(compareBy<FolderRule> { it.key.path.length }.thenBy { !it.builtIn })
        if (match != null) return match.type == FolderRuleType.INCLUDE
        return rules.none { it.type == FolderRuleType.INCLUDE }
    }

    /** The user's rules plus the built-in system-folder exclusions when that setting is on. */
    fun effectiveRules(userRules: List<FolderRule>, excludeSystemFolders: Boolean): List<FolderRule> =
        if (excludeSystemFolders) userRules + SystemFolders.rules else userRules
}

object TextKeys {
    private val DIACRITICS = Regex("\\p{Mn}+")
    private val WHITESPACE = Regex("\\s+")
    private val ARTICLES = listOf("the ", "a ", "an ")

    /** Case- and accent-insensitive form used for searching. */
    fun search(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE, " ")
            .trim()

    /** Sort key that also ignores leading articles, so "The Beatles" sorts under B. */
    fun sort(text: String): String {
        val key = search(text)
        for (article in ARTICLES) if (key.startsWith(article) && key.length > article.length) return key.substring(article.length)
        return key
    }

    fun searchTerms(query: String): List<String> = search(query).split(' ').filter(String::isNotBlank)
}

/** SQL for the paged song list and search. Column names match the `songs` table. */
object SongQueries {
    const val VISIBLE = "visible = 1 AND (durationMs <= 0 OR durationMs >= ?)"

    fun orderBy(order: SortOrder<SongSortField>): String {
        val dir = if (order.ascending) "ASC" else "DESC"
        val primary = when (order.field) {
            SongSortField.TITLE -> "titleKey $dir"
            SongSortField.ARTIST -> "artistKey $dir, albumKey $dir, discNumber $dir, trackNumber $dir"
            SongSortField.ALBUM -> "albumKey $dir, discNumber $dir, trackNumber $dir"
            SongSortField.DATE_ADDED -> "dateAddedSec $dir"
            SongSortField.DURATION -> "durationMs $dir"
            SongSortField.LOCATION -> "volume $dir, relativePath COLLATE NOCASE $dir, fileName COLLATE NOCASE $dir"
        }
        return "$primary, titleKey $dir, id $dir"
    }

    /** Matches the standard Download folder on any volume. */
    const val DOWNLOADS = "relativePath LIKE 'Download/%' ESCAPE '\\'"

    const val FAVOURITES = "id IN (SELECT songId FROM favorites)"

    /** No artist or album tag; the same test as [com.svara.app.domain.model.Song.missingTags]. */
    const val MISSING_TAGS = "(artist = '' OR artist = '<unknown>' OR album = '')"

    /** Song list query; `columns` is "*" for rows or "id" for the play-all id list. */
    fun list(
        order: SortOrder<SongSortField>,
        minMs: Long,
        columns: String = "*",
        filter: SongFilter = SongFilter.ALL,
    ): Pair<String, Array<Any>> {
        val extra = when (filter) {
            SongFilter.ALL -> ""
            SongFilter.DOWNLOADS -> " AND $DOWNLOADS"
            SongFilter.FAVOURITES -> " AND $FAVOURITES"
            SongFilter.MISSING_TAGS -> " AND $MISSING_TAGS"
        }
        return "SELECT $columns FROM songs WHERE $VISIBLE$extra ORDER BY ${orderBy(order)}" to arrayOf(minMs)
    }

    /** Every term must appear somewhere in title, artist or album. Prefix matches on the title rank first. */
    fun search(terms: List<String>, minMs: Long, limit: Int): Pair<String, Array<Any>> {
        require(terms.isNotEmpty())
        val likes = terms.joinToString(" AND ") { "searchText LIKE ? ESCAPE '\\'" }
        val args = ArrayList<Any>()
        args += minMs
        terms.forEach { args += "%${escapeLike(it)}%" }
        args += "${escapeLike(terms.first())}%"
        args += limit
        val sql = "SELECT * FROM songs WHERE $VISIBLE AND $likes " +
            "ORDER BY CASE WHEN titleKey LIKE ? ESCAPE '\\' THEN 0 ELSE 1 END, titleKey LIMIT ?"
        return sql to args.toTypedArray()
    }

    fun escapeLike(term: String): String = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}

object LibrarySorting {

    fun sortAlbums(albums: List<Album>, order: SortOrder<AlbumSortField>): List<Album> {
        val comparator: Comparator<Album> = when (order.field) {
            AlbumSortField.TITLE -> compareBy { it.sortKey }
            AlbumSortField.ARTIST -> compareBy<Album> { it.artistSortKey }.thenBy { it.year }
            AlbumSortField.YEAR -> compareBy { it.year }
            AlbumSortField.SONG_COUNT -> compareBy { it.songCount }
        }
        val tieBreak = comparator.thenBy { it.sortKey }.thenBy { it.id }
        return albums.sortedWith(if (order.ascending) tieBreak else tieBreak.reversed())
    }

    fun sortArtists(artists: List<Artist>, order: SortOrder<ArtistSortField>): List<Artist> {
        val comparator: Comparator<Artist> = when (order.field) {
            ArtistSortField.NAME -> compareBy { it.sortKey }
            ArtistSortField.SONG_COUNT -> compareBy<Artist> { it.songCount }.thenBy { it.sortKey }
        }
        return artists.sortedWith(if (order.ascending) comparator else comparator.reversed())
    }
}

data class SearchResults(
    val songs: List<Song>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val folders: List<com.svara.app.domain.model.Folder> = emptyList(),
    val playlists: List<com.svara.app.domain.model.Playlist> = emptyList(),
    /** True when some results only matched with a typo correction. */
    val includesFuzzy: Boolean = false,
) {
    val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && folders.isEmpty() && playlists.isEmpty()

    companion object {
        val EMPTY = SearchResults(emptyList(), emptyList(), emptyList())
    }
}

/**
 * Partial and typo-tolerant matching. A term matches a word when the word contains it, or when it
 * is within a small edit distance of the word (or of the word's prefix of similar length):
 * 0 edits for terms up to 3 letters, 1 up to 6, and 2 beyond.
 */
object FuzzyMatch {
    const val EXACT = 0
    const val PARTIAL = 1
    const val FUZZY = 2

    fun allowedEdits(term: String): Int = when {
        term.length <= 3 -> 0
        term.length <= 6 -> 1
        else -> 2
    }

    /** Best match quality of [term] against any word in [words], or null. */
    fun termQuality(term: String, words: List<String>): Int? {
        var best: Int? = null
        val max = allowedEdits(term)
        for (word in words) {
            val q = when {
                word.startsWith(term) -> EXACT
                word.contains(term) -> PARTIAL
                max == 0 -> null
                distance(term, word, max) <= max -> FUZZY
                word.length > term.length && distance(term, word.substring(0, term.length), max) <= max -> FUZZY
                else -> null
            }
            if (q != null && (best == null || q < best)) best = q
            if (best == EXACT) return best
        }
        return best
    }

    /** Score for [text] (lower is better), or null unless every term matches some word. */
    fun score(terms: List<String>, text: String): Int? {
        val words = text.split(' ')
        var total = 0
        for (term in terms) total += termQuality(term, words) ?: return null
        return total
    }

    /** Optimal-string-alignment distance (a swap of two adjacent letters counts as one edit), capped at max + 1. */
    fun distance(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        val prev2 = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            System.arraycopy(prev, 0, prev2, 0, prev.size)
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}

/** Matching for small collections (albums, artists, folders, playlists), done in memory with typo tolerance. */
object CollectionSearch {
    fun <T> rank(items: List<T>, terms: List<String>, limit: Int = 50, text: (T) -> String, sortKey: (T) -> String): List<T> =
        items.mapNotNull { item -> FuzzyMatch.score(terms, TextKeys.search(text(item)))?.let { item to it } }
            .sortedWith(compareBy<Pair<T, Int>> { it.second }.thenBy { sortKey(it.first) })
            .take(limit)
            .map { it.first }

    fun albums(albums: List<Album>, terms: List<String>, limit: Int = 50): List<Album> =
        rank(albums, terms, limit, { "${it.title} ${it.artist}" }, { it.sortKey })

    fun artists(artists: List<Artist>, terms: List<String>, limit: Int = 50): List<Artist> =
        rank(artists, terms, limit, { it.name }, { it.sortKey })
}
