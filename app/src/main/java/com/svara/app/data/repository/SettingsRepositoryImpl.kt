package com.svara.app.data.repository

import com.svara.app.domain.audio.FadeCurve
import com.svara.app.domain.audio.EqSettings
import com.svara.app.data.db.SettingEntity
import com.svara.app.data.db.SettingsDao
import com.svara.app.di.ApplicationScope
import com.svara.app.domain.model.AppSettings
import com.svara.app.domain.model.HomeSection
import com.svara.app.domain.model.SleepTimerOption
import com.svara.app.domain.model.SortOrder
import com.svara.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dao: SettingsDao,
    @ApplicationScope scope: CoroutineScope,
) : SettingsRepository {

    override val settings: StateFlow<AppSettings> = dao.observeAll()
        .map { rows -> SettingsCodec.decode(rows.associate { it.key to it.value }) }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private val writeLock = Mutex()

    override suspend fun current(): AppSettings = SettingsCodec.decode(dao.getAll().associate { it.key to it.value })

    override suspend fun update(transform: (AppSettings) -> AppSettings) = writeLock.withLock {
        val current = SettingsCodec.decode(dao.getAll().associate { it.key to it.value })
        val updated = transform(current)
        if (updated != current) {
            dao.upsert(SettingsCodec.encode(updated).map { (k, v) -> SettingEntity(k, v) })
        }
    }
}

/** Key/value encoding for [AppSettings]. Unknown or malformed values fall back to defaults. */
object SettingsCodec {
    private const val SONG_SORT = "library.songSort"
    private const val ALBUM_SORT = "library.albumSort"
    private const val ARTIST_SORT = "library.artistSort"
    private const val SLEEP_TIMER = "playback.defaultSleepTimer"
    private const val RESTORE_QUEUE = "playback.restoreQueue"
    private const val SKIP_SILENCE = "playback.skipSilence"
    private const val MIN_DURATION = "library.minDurationSec"
    private const val EXCLUDE_SYSTEM = "library.excludeSystemFolders"
    private const val PRESERVE_PITCH = "playback.preservePitch"
    private const val CROSSFADE = "playback.crossfadeSec"
    private const val GAPLESS_ALBUMS = "playback.gaplessAlbums"
    private const val LEVELING = "playback.volumeLeveling"
    private const val SLEEP_FADE = "rituals.sleepFadeOut"
    private const val AUTO_PAUSE = "rituals.autoPauseMinutes"
    private const val RESUME_HEADPHONES = "rituals.resumeOnHeadphones"
    private const val STOP_BT = "rituals.stopOnBluetoothDisconnect"
    private const val HISTORY = "privacy.historyEnabled"
    private const val RECAP_AUTO = "recap.autoAdvance"
    private const val RECAP_HOME = "recap.homeCard"
    private const val STATS_SINCE = "privacy.statsSince"
    private const val HIDE_HISTORY = "privacy.hideHistory"
    private const val APP_LOCK = "privacy.appLock"
    private const val LEFT_HANDED = "a11y.leftHanded"
    private const val HIGH_CONTRAST = "a11y.highContrast"
    private const val REDUCED_MOTION = "a11y.reducedMotion"
    private const val HOME_SECTIONS = "home.sections"
    private const val HOME_HIDDEN = "home.hidden"
    private const val START_SCREEN = "nav.startScreen"
    private const val SLEEP_CUSTOM = "playback.sleepCustomMinutes"
    private const val LYRICS_SCALE = "lyrics.textScale"
    private const val LYRICS_SPACING = "lyrics.lineSpacing"
    private const val RECENT_SEARCHES = "search.recent"
    private const val ONE_HANDED_SEARCH = "a11y.oneHandedSearch"
    private const val FADE_CURVE = "playback.crossfadeCurve"
    private const val MULTI_PRESS = "rituals.headsetMultiPress"
    private const val EQ_ENABLED = "eq.enabled"
    private const val EQ_PRESET = "eq.preset"
    private const val EQ_LEVELS = "eq.levels"
    private const val EQ_BASS = "eq.bassBoost"
    private const val EQ_VIRT = "eq.virtualizer"
    private const val EQ_LOUD = "eq.loudnessMb"
    private const val EQ_USER = "eq.userPresets"
    private const val UNIT = '\u001F'
    private const val RECORD = '\u001E'

    fun encode(s: AppSettings): Map<String, String> = mapOf(
        SONG_SORT to s.songSort.encode(),
        ALBUM_SORT to s.albumSort.encode(),
        ARTIST_SORT to s.artistSort.encode(),
        SLEEP_TIMER to s.defaultSleepTimer.name,
        RESTORE_QUEUE to s.restoreQueueOnLaunch.toString(),
        SKIP_SILENCE to s.skipSilence.toString(),
        MIN_DURATION to s.minTrackDurationSec.toString(),
        EXCLUDE_SYSTEM to s.excludeSystemFolders.toString(),
        PRESERVE_PITCH to s.preservePitch.toString(),
        CROSSFADE to s.crossfadeSec.toString(),
        GAPLESS_ALBUMS to s.gaplessAlbums.toString(),
        LEVELING to s.volumeLeveling.toString(),
        SLEEP_FADE to s.sleepFadeOut.toString(),
        AUTO_PAUSE to s.autoPauseMinutes.toString(),
        RESUME_HEADPHONES to s.resumeOnHeadphones.toString(),
        STOP_BT to s.stopOnBluetoothDisconnect.toString(),
        HISTORY to s.historyEnabled.toString(),
        RECAP_AUTO to s.recapAutoAdvance.toString(),
        RECAP_HOME to s.recapHomeCard.toString(),
        STATS_SINCE to s.statsSince.toString(),
        HIDE_HISTORY to s.hideHistory.toString(),
        APP_LOCK to s.appLock.toString(),
        LEFT_HANDED to s.leftHanded.toString(),
        HIGH_CONTRAST to s.highContrast.toString(),
        REDUCED_MOTION to s.reducedMotion.toString(),
        HOME_SECTIONS to s.homeSections.joinToString(",") { it.name },
        HOME_HIDDEN to s.hiddenHomeSections.joinToString(",") { it.name },
        START_SCREEN to s.startScreen,
        SLEEP_CUSTOM to s.sleepCustomMinutes.toString(),
        LYRICS_SCALE to s.lyricsTextScale.toString(),
        LYRICS_SPACING to s.lyricsLineSpacing.toString(),
        // Searches are separated by a character a search can't usefully contain.
        RECENT_SEARCHES to s.recentSearches.joinToString("\u001F"),
        ONE_HANDED_SEARCH to s.oneHandedSearch.toString(),
        FADE_CURVE to s.crossfadeCurve.name,
        MULTI_PRESS to s.headsetMultiPress.toString(),
        EQ_ENABLED to s.eq.enabled.toString(),
        EQ_PRESET to (s.eq.preset ?: ""),
        EQ_LEVELS to s.eq.bandLevelsMb.joinToString(","),
        EQ_BASS to s.eq.bassBoost.toString(),
        EQ_VIRT to s.eq.virtualizer.toString(),
        EQ_LOUD to s.eq.loudnessMb.toString(),
        EQ_USER to s.eq.userPresets.entries.joinToString(RECORD.toString()) { (name, levels) -> name + UNIT + levels.joinToString(",") },
    )

    fun decode(map: Map<String, String>): AppSettings {
        val d = AppSettings()
        fun bool(key: String, default: Boolean) = map[key]?.toBooleanStrictOrNull() ?: default
        fun int(key: String, default: Int, range: IntRange) = map[key]?.toIntOrNull()?.coerceIn(range) ?: default
        return AppSettings(
            songSort = map[SONG_SORT].decodeSort(d.songSort),
            albumSort = map[ALBUM_SORT].decodeSort(d.albumSort),
            artistSort = map[ARTIST_SORT].decodeSort(d.artistSort),
            defaultSleepTimer = map[SLEEP_TIMER]?.let { runCatching { SleepTimerOption.valueOf(it) }.getOrNull() } ?: d.defaultSleepTimer,
            restoreQueueOnLaunch = bool(RESTORE_QUEUE, d.restoreQueueOnLaunch),
            skipSilence = bool(SKIP_SILENCE, d.skipSilence),
            minTrackDurationSec = int(MIN_DURATION, d.minTrackDurationSec, 0..600),
            excludeSystemFolders = bool(EXCLUDE_SYSTEM, d.excludeSystemFolders),
            preservePitch = bool(PRESERVE_PITCH, d.preservePitch),
            crossfadeSec = int(CROSSFADE, d.crossfadeSec, 0..12),
            gaplessAlbums = bool(GAPLESS_ALBUMS, d.gaplessAlbums),
            volumeLeveling = bool(LEVELING, d.volumeLeveling),
            sleepFadeOut = bool(SLEEP_FADE, d.sleepFadeOut),
            autoPauseMinutes = int(AUTO_PAUSE, d.autoPauseMinutes, 0..600),
            resumeOnHeadphones = bool(RESUME_HEADPHONES, d.resumeOnHeadphones),
            stopOnBluetoothDisconnect = bool(STOP_BT, d.stopOnBluetoothDisconnect),
            historyEnabled = bool(HISTORY, d.historyEnabled),
            recapAutoAdvance = bool(RECAP_AUTO, d.recapAutoAdvance),
            recapHomeCard = bool(RECAP_HOME, d.recapHomeCard),
            statsSince = map[STATS_SINCE]?.toLongOrNull()?.coerceAtLeast(0) ?: d.statsSince,
            hideHistory = bool(HIDE_HISTORY, d.hideHistory),
            appLock = bool(APP_LOCK, d.appLock),
            leftHanded = bool(LEFT_HANDED, d.leftHanded),
            highContrast = bool(HIGH_CONTRAST, d.highContrast),
            reducedMotion = bool(REDUCED_MOTION, d.reducedMotion),
            homeSections = decodeSections(map[HOME_SECTIONS]) ?: d.homeSections,
            hiddenHomeSections = map[HOME_HIDDEN]?.split(',')
                ?.mapNotNullTo(HashSet()) { n -> HomeSection.entries.firstOrNull { it.name == n } } ?: d.hiddenHomeSections,
            startScreen = map[START_SCREEN]?.takeIf { it.isNotBlank() } ?: d.startScreen,
            sleepCustomMinutes = int(SLEEP_CUSTOM, d.sleepCustomMinutes, AppSettings.SLEEP_CUSTOM_RANGE),
            lyricsTextScale = int(LYRICS_SCALE, d.lyricsTextScale, AppSettings.LYRICS_TEXT_SCALES),
            lyricsLineSpacing = int(LYRICS_SPACING, d.lyricsLineSpacing, AppSettings.LYRICS_SPACINGS),
            recentSearches = map[RECENT_SEARCHES]?.split('\u001F')?.filter { it.isNotBlank() }?.take(AppSettings.RECENT_SEARCH_LIMIT) ?: d.recentSearches,
            oneHandedSearch = bool(ONE_HANDED_SEARCH, d.oneHandedSearch),
            headsetMultiPress = bool(MULTI_PRESS, d.headsetMultiPress),
            crossfadeCurve = map[FADE_CURVE]?.let { n -> FadeCurve.entries.firstOrNull { it.name == n } } ?: d.crossfadeCurve,
            eq = EqSettings(
                enabled = bool(EQ_ENABLED, d.eq.enabled),
                preset = map[EQ_PRESET]?.let { it.ifEmpty { null } } ?: if (map.containsKey(EQ_PRESET)) null else d.eq.preset,
                bandLevelsMb = map[EQ_LEVELS]?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: d.eq.bandLevelsMb,
                bassBoost = int(EQ_BASS, d.eq.bassBoost, 0..EqSettings.MAX_STRENGTH),
                virtualizer = int(EQ_VIRT, d.eq.virtualizer, 0..EqSettings.MAX_STRENGTH),
                loudnessMb = int(EQ_LOUD, d.eq.loudnessMb, 0..EqSettings.MAX_LOUDNESS_MB),
                userPresets = map[EQ_USER]?.takeIf { it.isNotEmpty() }?.split(RECORD)?.mapNotNull { rec ->
                    val parts = rec.split(UNIT)
                    if (parts.size != 2 || parts[0].isBlank()) null
                    else parts[0] to parts[1].split(',').mapNotNull { it.trim().toIntOrNull() }
                }?.take(EqSettings.MAX_USER_PRESETS)?.toMap() ?: d.eq.userPresets,
            ),
        )
    }

    /** Keeps the saved order, and appends sections added in later versions so they're never lost. */
    private fun decodeSections(raw: String?): List<HomeSection>? {
        if (raw.isNullOrBlank()) return null
        val saved = raw.split(',').mapNotNull { n -> HomeSection.entries.firstOrNull { it.name == n } }.distinct()
        return saved + HomeSection.entries.filter { it !in saved }
    }

    private fun <T : Enum<T>> SortOrder<T>.encode() = "${field.name}:${if (ascending) "asc" else "desc"}"

    private inline fun <reified T : Enum<T>> String?.decodeSort(default: SortOrder<T>): SortOrder<T> {
        if (this == null) return default
        val (name, dir) = split(':').let { it.getOrNull(0) to it.getOrNull(1) }
        val field = enumValues<T>().firstOrNull { it.name == name } ?: return default
        return SortOrder(field, dir != "desc")
    }
}
