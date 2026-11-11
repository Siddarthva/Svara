package com.svara.app.data.repository

import com.svara.app.data.db.HistoryDao
import com.svara.app.data.db.RecapCacheEntity
import com.svara.app.domain.recap.RankedAlbum
import com.svara.app.domain.recap.RankedName
import com.svara.app.domain.recap.RankedSong
import com.svara.app.domain.recap.Recap
import com.svara.app.domain.recap.RecapEngine
import com.svara.app.domain.recap.RecapKind
import com.svara.app.domain.recap.RecapPeriod
import com.svara.app.domain.recap.RecapPlay
import com.svara.app.domain.recap.TimeOfDay
import com.svara.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listening recaps, worked out on demand from local history off the main thread and cached per
 * period. The cache entry is only used while the history (and time zone and week start) is
 * unchanged; erasing history clears it. Never writes history, the queue or the library.
 */
@Singleton
class RecapRepository @Inject constructor(
    private val dao: HistoryDao,
    private val settings: SettingsRepository,
) {
    private fun zone(): ZoneId = ZoneId.systemDefault()
    private fun locale(): Locale = Locale.getDefault()

    /** Changes whenever a play is added or removed; screens reload on it. */
    val historyStamp: Flow<String> get() = dao.observeHistoryStamp()

    private suspend fun signature(): String {
        val s = settings.settings.value
        val (count, max) = dao.historyStamp().split(':').let { (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toLongOrNull() ?: 0L) }
        return RecapEngine.signature(count, max, s.statsSince, zone(), locale())
    }

    /** Periods of [kind] with at least one play (after the last statistics reset), newest first. */
    suspend fun periods(kind: RecapKind): List<Pair<RecapPeriod, Int>> = withContext(Dispatchers.IO) {
        val times = dao.playTimes(settings.settings.value.statsSince)
        withContext(Dispatchers.Default) { RecapEngine.periodsWithPlays(kind, times, zone(), locale()) }
    }

    /** The period of [kind] that is in progress today. */
    fun current(kind: RecapKind): RecapPeriod = RecapPeriod.containing(kind, LocalDate.now(zone()), locale())

    suspend fun recap(period: RecapPeriod): Recap = withContext(Dispatchers.IO) {
        val sig = signature()
        dao.cachedRecap(period.key)?.takeIf { it.signature == sig }?.let { cached ->
            runCatching { RecapCodec.decode(cached.payload) }.getOrNull()?.let { return@withContext it }
        }
        val zone = zone()
        val since = settings.settings.value.statsSince
        val from = maxOf(period.startMs(zone), since)
        val rows = dao.recapPlays(from, period.endMs(zone))
        val plays = rows.map { RecapPlay(it.songId, it.playedAt, it.listenedMs, it.title, it.artist, it.album, it.albumId ?: -1L, it.genre) }
        val firstPlays = rows.map { it.songId }.distinct().chunked(SQL_CHUNK).flatMap { dao.firstPlays(it) }.associate { it.songId to it.firstPlayed }
        val prev = period.previous()
        val prevMs = dao.listenedBetween(maxOf(prev.startMs(zone), since), prev.endMs(zone))
        val recap = withContext(Dispatchers.Default) { RecapEngine.compute(period, plays, firstPlays, prevMs, zone, locale()) }
        // A period still in progress changes with every play; only finished periods are cached.
        if (period.endMs(zone) <= System.currentTimeMillis()) {
            dao.cacheRecap(RecapCacheEntity(period.key, sig, RecapCodec.encode(recap), System.currentTimeMillis()))
        }
        recap
    }

    /** The newest recap with enough plays for Home: last full week, else this week so far. */
    suspend fun newestForHome(): Recap? {
        val thisWeek = current(RecapKind.WEEK)
        listOf(thisWeek.previous(), thisWeek).forEach { p ->
            val r = recap(p)
            if (r.plays >= RecapEngine.MIN_PLAYS) return r
        }
        return null
    }

    private companion object {
        const val SQL_CHUNK = 500
    }
}

/** Plain JSON for the recap cache; anything unreadable is recomputed. */
object RecapCodec {
    fun encode(r: Recap): String = JSONObject().apply {
        put("period", r.period.key)
        put("plays", r.plays); put("countOnly", r.countOnlyPlays); put("ms", r.listenedMs); put("distinct", r.distinctSongs)
        put("topSongs", songs(r.topSongs)); put("topArtists", names(r.topArtists)); put("genres", names(r.topGenres))
        r.topAlbum?.let { a -> put("album", JSONObject().put("n", a.name).put("a", a.albumId).put("s", a.songId).put("ar", a.artist ?: JSONObject.NULL).put("p", a.plays)) }
        put("hours", JSONArray(r.hours)); put("weekdays", JSONArray(r.weekdays)); put("wdl", JSONArray(r.weekdayLabels))
        put("activeDay", r.mostActiveDay?.toString() ?: JSONObject.NULL); put("activePlays", r.mostActiveDayPlays)
        put("tod", r.favouriteTime?.name ?: JSONObject.NULL)
        put("session", r.longestSessionMs); put("streak", r.streakDays); put("new", r.newSongs); put("newSamples", songs(r.newSongSamples))
        r.mostReplayed?.let { put("replayed", song(it)) }; put("replayedDay", r.mostReplayedInADay)
        put("change", r.changePercent ?: JSONObject.NULL)
    }.toString()

    fun decode(json: String): Recap {
        val o = JSONObject(json)
        fun ints(k: String) = o.getJSONArray(k).let { a -> List(a.length()) { a.getInt(it) } }
        return Recap(
            period = RecapPeriod.parse(o.getString("period")) ?: error("bad period"),
            plays = o.getInt("plays"), countOnlyPlays = o.getInt("countOnly"), listenedMs = o.getLong("ms"), distinctSongs = o.getInt("distinct"),
            topSongs = songs(o.getJSONArray("topSongs")), topArtists = names(o.getJSONArray("topArtists")),
            topAlbum = o.optJSONObject("album")?.let { a -> RankedAlbum(a.getString("n"), a.getLong("a"), a.getLong("s"), a.optStringOrNull("ar"), a.getInt("p")) },
            topGenres = names(o.getJSONArray("genres")),
            hours = ints("hours"), weekdays = ints("weekdays"),
            weekdayLabels = o.getJSONArray("wdl").let { a -> List(a.length()) { a.getString(it) } },
            mostActiveDay = o.optStringOrNull("activeDay")?.let(LocalDate::parse), mostActiveDayPlays = o.getInt("activePlays"),
            favouriteTime = o.optStringOrNull("tod")?.let { n -> TimeOfDay.entries.firstOrNull { it.name == n } },
            longestSessionMs = o.getLong("session"), streakDays = o.getInt("streak"), newSongs = o.getInt("new"),
            newSongSamples = songs(o.getJSONArray("newSamples")),
            mostReplayed = o.optJSONObject("replayed")?.let(::song), mostReplayedInADay = o.getInt("replayedDay"),
            changePercent = if (o.isNull("change")) null else o.getInt("change"),
        )
    }

    private fun JSONObject.optStringOrNull(k: String): String? = if (isNull(k)) null else optString(k)
    private fun song(s: RankedSong) = JSONObject().put("i", s.songId).put("a", s.albumId).put("t", s.title).put("ar", s.artist ?: JSONObject.NULL).put("p", s.plays).put("m", s.listenedMs)
    private fun song(o: JSONObject) = RankedSong(o.getLong("i"), o.getLong("a"), o.getString("t"), o.optStringOrNull("ar"), o.getInt("p"), o.getLong("m"))
    private fun songs(l: List<RankedSong>) = JSONArray().apply { l.forEach { put(song(it)) } }
    private fun songs(a: JSONArray) = List(a.length()) { song(a.getJSONObject(it)) }
    private fun names(l: List<RankedName>) = JSONArray().apply { l.forEach { put(JSONObject().put("n", it.name).put("p", it.plays).put("m", it.listenedMs)) } }
    private fun names(a: JSONArray) = List(a.length()) { a.getJSONObject(it).let { o -> RankedName(o.getString("n"), o.getInt("p"), o.getLong("m")) } }
}
