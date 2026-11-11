package com.svara.app.domain.recap

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

enum class RecapKind(val label: String, val title: String) {
    WEEK("Week", "Your week in music"),
    MONTH("Month", "Your month in music"),
    YEAR("Year", "Your year in music"),
}

/** A calendar period in the device's time zone. [end] is exclusive. */
data class RecapPeriod(val kind: RecapKind, val start: LocalDate) {
    val end: LocalDate
        get() = when (kind) {
            RecapKind.WEEK -> start.plusWeeks(1)
            RecapKind.MONTH -> start.plusMonths(1)
            RecapKind.YEAR -> start.plusYears(1)
        }

    val key: String get() = "${kind.name}:$start"

    fun previous(): RecapPeriod = when (kind) {
        RecapKind.WEEK -> copy(start = start.minusWeeks(1))
        RecapKind.MONTH -> copy(start = start.minusMonths(1))
        RecapKind.YEAR -> copy(start = start.minusYears(1))
    }

    fun startMs(zone: ZoneId): Long = start.atStartOfDay(zone).toInstant().toEpochMilli()
    fun endMs(zone: ZoneId): Long = end.atStartOfDay(zone).toInstant().toEpochMilli()

    fun contains(ms: Long, zone: ZoneId): Boolean = ms >= startMs(zone) && ms < endMs(zone)

    companion object {
        fun firstDayOfWeek(locale: Locale): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

        /** The period of [kind] that contains [date]; weeks start on the locale's first day. */
        fun containing(kind: RecapKind, date: LocalDate, locale: Locale): RecapPeriod = when (kind) {
            RecapKind.WEEK -> RecapPeriod(kind, date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek(locale))))
            RecapKind.MONTH -> RecapPeriod(kind, date.withDayOfMonth(1))
            RecapKind.YEAR -> RecapPeriod(kind, date.withDayOfYear(1))
        }

        fun parse(key: String): RecapPeriod? {
            val (k, d) = key.split(':', limit = 2).takeIf { it.size == 2 } ?: return null
            val kind = RecapKind.entries.firstOrNull { it.name == k } ?: return null
            return runCatching { RecapPeriod(kind, LocalDate.parse(d)) }.getOrNull()
        }
    }
}

/** One counted play, joined with whatever the library still knows about the song. */
data class RecapPlay(
    val songId: Long,
    val playedAt: Long,
    /** 0 for plays recorded before listening time was stored: counted, never added to minutes. */
    val listenedMs: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumId: Long,
    val genre: String?,
)

data class RankedSong(val songId: Long, val albumId: Long, val title: String, val artist: String?, val plays: Int, val listenedMs: Long)
data class RankedName(val name: String, val plays: Int, val listenedMs: Long)
data class RankedAlbum(val name: String, val albumId: Long, val songId: Long, val artist: String?, val plays: Int)

enum class TimeOfDay(val label: String) { MORNING("Morning"), AFTERNOON("Afternoon"), EVENING("Evening"), NIGHT("Night");
    companion object {
        fun of(hour: Int): TimeOfDay = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..21 -> EVENING
            else -> NIGHT
        }
    }
}

data class Recap(
    val period: RecapPeriod,
    val plays: Int,
    /** Plays without a recorded duration; excluded from [listenedMs]. */
    val countOnlyPlays: Int,
    val listenedMs: Long,
    val distinctSongs: Int,
    val topSongs: List<RankedSong>,
    val topArtists: List<RankedName>,
    val topAlbum: RankedAlbum?,
    val topGenres: List<RankedName>,
    /** Plays per hour of day, 0..23. */
    val hours: List<Int>,
    /** Plays per day of week, starting at the locale's first day. */
    val weekdays: List<Int>,
    val weekdayLabels: List<String>,
    val mostActiveDay: LocalDate?,
    val mostActiveDayPlays: Int,
    val favouriteTime: TimeOfDay?,
    val longestSessionMs: Long,
    val streakDays: Int,
    val newSongs: Int,
    val newSongSamples: List<RankedSong>,
    val mostReplayed: RankedSong?,
    /** Most plays of [mostReplayed] on a single day. */
    val mostReplayedInADay: Int,
    /** Percent change in listening time against the previous period; null when it had no timed listening. */
    val changePercent: Int?,
) {
    val minutes: Long get() = listenedMs / 60_000
}

object RecapEngine {
    const val TOP_N = 5
    const val MIN_PLAYS = 5
    /** Plays further apart than this start a new listening session. */
    const val SESSION_GAP_MS = 30L * 60 * 1000
    const val DISCOVERY_SAMPLES = 3

    private val UNKNOWN = setOf("", "<unknown>", "unknown", "unknown artist", "unknown album", "unknown genre")
    fun known(tag: String?): String? = tag?.trim()?.takeIf { it.lowercase() !in UNKNOWN }

    /** The existing counting rule: 30 s, or half of songs shorter than a minute. */
    fun counts(listenedMs: Long, durationMs: Long): Boolean =
        listenedMs >= minOf(PLAY_THRESHOLD_MS, if (durationMs > 0) durationMs / 2 else PLAY_THRESHOLD_MS)

    const val PLAY_THRESHOLD_MS = 30_000L

    /** Ranks by plays, then listening time, then name, so ties are always stable. */
    fun <T> rank(items: Map<String, T>, plays: (T) -> Int, ms: (T) -> Long): List<Pair<String, T>> =
        items.entries.sortedWith(compareByDescending<Map.Entry<String, T>> { plays(it.value) }.thenByDescending { ms(it.value) }.thenBy { it.key.lowercase() })
            .map { it.key to it.value }

    /**
     * Builds a recap from the period's plays. [firstPlays] maps song id to its first play ever;
     * [previousListenedMs] is the previous period's timed listening, or 0 when it has none.
     */
    fun compute(
        period: RecapPeriod,
        plays: List<RecapPlay>,
        firstPlays: Map<Long, Long>,
        previousListenedMs: Long,
        zone: ZoneId,
        locale: Locale,
    ): Recap {
        val inPeriod = plays.filter { period.contains(it.playedAt, zone) }.sortedBy { it.playedAt }
        val listened = inPeriod.sumOf { it.listenedMs.coerceAtLeast(0) }

        val bySong = inPeriod.groupBy { it.songId }
        val songStats = bySong.mapValues { (_, p) -> p }
        val topSongs = songStats.entries
            .sortedWith(compareByDescending<Map.Entry<Long, List<RecapPlay>>> { it.value.size }.thenByDescending { e -> e.value.sumOf { it.listenedMs } }.thenBy { it.key })
            .take(TOP_N).map { (id, p) -> song(id, p) }

        data class Agg(var plays: Int = 0, var ms: Long = 0)
        fun aggregate(key: (RecapPlay) -> String?): Map<String, Agg> {
            val m = LinkedHashMap<String, Agg>()
            inPeriod.forEach { p -> known(key(p))?.let { k -> m.getOrPut(k) { Agg() }.apply { this.plays++; ms += p.listenedMs } } }
            return m
        }
        val artists = rank(aggregate { it.artist }, { it.plays }, { it.ms }).take(TOP_N).map { (n, a) -> RankedName(n, a.plays, a.ms) }
        val genres = rank(aggregate { it.genre }, { it.plays }, { it.ms }).take(TOP_N).map { (n, a) -> RankedName(n, a.plays, a.ms) }
        val albumPlays = inPeriod.filter { known(it.album) != null }.groupBy { it.albumId to known(it.album)!! }
        val topAlbum = albumPlays.entries
            .sortedWith(compareByDescending<Map.Entry<Pair<Long, String>, List<RecapPlay>>> { it.value.size }.thenBy { it.key.second.lowercase() })
            .firstOrNull()?.let { (k, p) -> RankedAlbum(k.second, k.first, p.first().songId, known(p.first().artist), p.size) }

        val hours = MutableList(24) { 0 }
        val first = RecapPeriod.firstDayOfWeek(locale)
        val weekdays = MutableList(7) { 0 }
        val days = sortedMapOf<LocalDate, Int>()
        inPeriod.forEach { p ->
            val t = Instant.ofEpochMilli(p.playedAt).atZone(zone)
            hours[t.hour]++
            weekdays[Math.floorMod(t.dayOfWeek.value - first.value, 7)]++
            days.merge(t.toLocalDate(), 1, Int::plus)
        }
        val weekdayLabels = (0 until 7).map { first.plus(it.toLong()).getDisplayName(TextStyle.SHORT, locale) }
        val busiest = days.entries.maxWithOrNull(compareBy<Map.Entry<LocalDate, Int>> { it.value }.thenByDescending { it.key })
        val favourite = inPeriod.groupingBy { TimeOfDay.of(Instant.ofEpochMilli(it.playedAt).atZone(zone).hour) }.eachCount()
            .entries.maxWithOrNull(compareBy<Map.Entry<TimeOfDay, Int>> { it.value }.thenByDescending { it.key.ordinal })?.key

        var streak = 0
        var run = 0
        var prev: LocalDate? = null
        days.keys.forEach { d ->
            run = if (prev != null && prev!!.plusDays(1) == d) run + 1 else 1
            streak = maxOf(streak, run)
            prev = d
        }

        // Sessions: timed plays whose gaps stay under SESSION_GAP_MS, measured start to end.
        var longest = 0L
        var sessionStart = -1L
        var sessionEnd = -1L
        inPeriod.filter { it.listenedMs > 0 }.forEach { p ->
            if (sessionStart < 0 || p.playedAt - sessionEnd > SESSION_GAP_MS) sessionStart = p.playedAt
            sessionEnd = maxOf(sessionEnd, p.playedAt + p.listenedMs)
            longest = maxOf(longest, sessionEnd - sessionStart)
        }

        val newIds = bySong.keys.filter { id -> firstPlays[id]?.let { period.contains(it, zone) } == true }
        val newSamples = newIds.map { song(it, bySong.getValue(it)) }
            .sortedWith(compareByDescending<RankedSong> { it.plays }.thenBy { it.songId }).take(DISCOVERY_SAMPLES)

        val replayed = inPeriod.groupBy { it.songId to Instant.ofEpochMilli(it.playedAt).atZone(zone).toLocalDate() }
            .entries.maxWithOrNull(compareBy<Map.Entry<Pair<Long, LocalDate>, List<RecapPlay>>> { it.value.size }.thenByDescending { it.key.second })
            ?.takeIf { it.value.size >= 2 }

        val change = if (previousListenedMs > 0 && listened > 0)
            Math.round((listened - previousListenedMs) * 100.0 / previousListenedMs).toInt() else null

        return Recap(
            period = period,
            plays = inPeriod.size,
            countOnlyPlays = inPeriod.count { it.listenedMs <= 0 },
            listenedMs = listened,
            distinctSongs = bySong.size,
            topSongs = topSongs,
            topArtists = artists,
            topAlbum = topAlbum,
            topGenres = genres,
            hours = hours,
            weekdays = weekdays,
            weekdayLabels = weekdayLabels,
            mostActiveDay = busiest?.key,
            mostActiveDayPlays = busiest?.value ?: 0,
            favouriteTime = favourite,
            longestSessionMs = longest,
            streakDays = streak,
            newSongs = newIds.size,
            newSongSamples = newSamples,
            mostReplayed = replayed?.let { song(it.key.first, bySong.getValue(it.key.first)) },
            mostReplayedInADay = replayed?.value?.size ?: 0,
            changePercent = change,
        )
    }

    private fun song(id: Long, p: List<RecapPlay>): RankedSong {
        val s = p.first()
        return RankedSong(id, s.albumId, s.title ?: "Removed song", known(s.artist), p.size, p.sumOf { it.listenedMs })
    }

    /** Periods of [kind] that contain at least one play, newest first. */
    fun periodsWithPlays(kind: RecapKind, playTimes: List<Long>, zone: ZoneId, locale: Locale): List<Pair<RecapPeriod, Int>> =
        playTimes.groupingBy { RecapPeriod.containing(kind, Instant.ofEpochMilli(it).atZone(zone).toLocalDate(), locale) }.eachCount()
            .entries.sortedByDescending { it.key.start }.map { it.key to it.value }

    /** Recap animations: motion on, not minimal (reduced motion, Efficiency, saver, heat) and not turned off. */
    fun animationsAllowed(motionEnabled: Boolean, groups: com.svara.app.appearance.MotionGroups, recapAnimations: Boolean): Boolean =
        motionEnabled && !groups.minimal && recapAnimations

    /** How cards change: a shape wipe when animations are allowed, a short fade when motion is limited, else instant. */
    fun transition(motionEnabled: Boolean, groups: com.svara.app.appearance.MotionGroups, recapAnimations: Boolean): RecapTransition = when {
        !motionEnabled -> RecapTransition.INSTANT
        animationsAllowed(motionEnabled, groups, recapAnimations) -> RecapTransition.WIPE
        else -> RecapTransition.FADE
    }

    /** Cache key part that changes whenever the underlying history, zone or week start changes. */
    fun signature(eventCount: Int, maxEventId: Long, statsSince: Long, zone: ZoneId, locale: Locale): String =
        "$eventCount:$maxEventId:$statsSince:${zone.id}:${RecapPeriod.firstDayOfWeek(locale)}"
}

enum class RecapTransition { WIPE, FADE, INSTANT }

/** Picks the lightest scrim that keeps text at WCAG AA over every colour of a card's background. */
object RecapScrim {
    private fun channel(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }

    fun luminance(argb: Int): Double =
        0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** [scrim] blended over [under] at [alpha]. */
    fun blend(under: Int, scrim: Int, alpha: Float): Int {
        fun mix(sh: Int) = ((((under shr sh) and 0xFF) * (1 - alpha)) + (((scrim shr sh) and 0xFF) * alpha)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    fun alpha(stops: List<Int>, scrim: Int, text: Int, start: Float, step: Float, minContrast: Double): Float {
        var a = start
        while (a < 1f && stops.any { contrast(text, blend(it, scrim, a)) < minContrast }) a += step
        return a.coerceAtMost(1f)
    }
}
