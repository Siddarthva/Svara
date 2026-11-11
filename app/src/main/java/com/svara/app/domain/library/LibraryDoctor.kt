package com.svara.app.domain.library

import com.svara.app.domain.model.Song

/** Song fields Library Doctor can correct. Stored as SVARĀ overrides; audio files are never written. */
enum class MetadataField(val label: String) { TITLE("Title"), ARTIST("Artist"), ALBUM("Album") }

/** Why a song is listed. */
enum class IssueKind(val label: String) {
    ARTIST_SPELLING("Inconsistent artist name"),
    TITLE_FORMAT("Probable title error"),
    MISSING_TITLE("Missing title"),
    MISSING_ARTIST("Missing artist"),
    MISSING_ALBUM("Missing album"),
}

/**
 * One finding. [proposed] is SVARĀ's suggested value (shown as before → after); null when it can't
 * suggest one and the user must type it.
 */
data class DoctorIssue(
    val song: Song,
    val kind: IssueKind,
    val field: MetadataField,
    val current: String,
    val proposed: String?,
) {
    val key: String = "${song.id}:${field.name}"
}

/** Pure checks over song metadata. */
object LibraryDoctor {

    fun findIssues(songs: List<Song>): List<DoctorIssue> =
        artistSpellings(songs) + titleProblems(songs) + emptyMetadata(songs)

    /**
     * Artists whose names differ only in case, accents, punctuation, spacing, "&" vs "and" or a
     * leading "The": every song not using the most common spelling gets that spelling proposed.
     */
    fun artistSpellings(songs: List<Song>): List<DoctorIssue> {
        val named = songs.filter { it.artist.isNotBlank() && it.artist != Song.UNKNOWN_ARTIST }
        return named.groupBy { artistKey(it.artist) }.values.flatMap { group ->
            val spellings = group.groupingBy { it.artist }.eachCount()
            if (spellings.size < 2) return@flatMap emptyList()
            // Most used wins; ties go to the one with more capitals (likely the proper form), then alphabetical.
            val best = spellings.entries.sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { e -> e.key.count { it.isUpperCase() } }.thenBy { it.key },
            ).first().key
            group.filter { it.artist != best }.map { DoctorIssue(it, IssueKind.ARTIST_SPELLING, MetadataField.ARTIST, it.artist, best) }
        }
    }

    fun artistKey(name: String): String =
        TextKeys.search(name).replace("&", " and ").replace(PUNCTUATION, " ").replace(SPACES, " ").trim()

    /** Titles with a track-number prefix, a file extension, underscores or stray spacing. */
    fun titleProblems(songs: List<Song>): List<DoctorIssue> = songs.mapNotNull { song ->
        val title = song.title
        if (title.isBlank()) return@mapNotNull null
        val cleaned = cleanTitle(title)
        if (cleaned == title || cleaned.isBlank()) null
        else DoctorIssue(song, IssueKind.TITLE_FORMAT, MetadataField.TITLE, title, cleaned)
    }

    fun cleanTitle(title: String): String {
        var t = title.trim()
        t = AUDIO_EXTENSION.replace(t, "")
        if (!t.contains(' ') && t.contains('_')) t = t.replace('_', ' ')
        t = TRACK_PREFIX.replace(t, "")
        return t.replace(SPACES, " ").trim()
    }

    /**
     * Missing title, artist or album. A title falls back to the file name; an "Artist - Title"
     * file name supplies the artist. Albums can't be guessed, so the user types one.
     */
    fun emptyMetadata(songs: List<Song>): List<DoctorIssue> = songs.flatMap { song ->
        val out = ArrayList<DoctorIssue>(1)
        val base = song.fileName.substringBeforeLast('.')
        val parsed = ARTIST_TITLE.matchEntire(base)
        if (song.title.isBlank()) {
            val proposal = cleanTitle(parsed?.groupValues?.get(2) ?: base).takeIf { it.isNotBlank() }
            out += DoctorIssue(song, IssueKind.MISSING_TITLE, MetadataField.TITLE, song.title, proposal)
        }
        if (song.artist.isBlank() || song.artist == Song.UNKNOWN_ARTIST || song.artist.equals("<unknown>", ignoreCase = true)) {
            out += DoctorIssue(song, IssueKind.MISSING_ARTIST, MetadataField.ARTIST, song.artist, parsed?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() })
        }
        if (song.album.isBlank() || song.album == Song.UNKNOWN_ALBUM) {
            out += DoctorIssue(song, IssueKind.MISSING_ALBUM, MetadataField.ALBUM, song.album, null)
        }
        out
    }

    private val PUNCTUATION = Regex("[\\p{Punct}’‘“”]")
    private val SPACES = Regex("\\s+")
    private val AUDIO_EXTENSION = Regex("(?i)\\.(mp3|flac|m4a|aac|ogg|opus|wav|wma|alac)$")
    /** "01 - ", "01. ", "1) ", "01_" or a zero-padded "01 " at the start; "99 Problems" and "2.0" are left alone. */
    private val TRACK_PREFIX = Regex("^(?:\\d{1,3}(?:[.)]\\s+|_|\\s+-\\s+)|0\\d\\s+)(?=\\S)")
    private val ARTIST_TITLE = Regex("^(.+?)\\s+-\\s+(.+)$")
}
