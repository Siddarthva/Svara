package com.svara.app.domain

import com.svara.app.appearance.Customization
import com.svara.app.appearance.EffectsPolicy
import com.svara.app.appearance.AnimationStyle
import com.svara.app.appearance.MotionGroups
import com.svara.app.appearance.MotionSpec
import com.svara.app.appearance.PerformanceMode
import com.svara.app.appearance.ThermalLevel
import com.svara.app.domain.recap.RecapEngine
import com.svara.app.domain.recap.RecapKind
import com.svara.app.domain.recap.RecapPeriod
import com.svara.app.domain.recap.RecapPlay
import com.svara.app.domain.recap.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class RecapEngineTest {
    private val zone = ZoneId.of("Europe/London")
    private val uk = Locale.UK

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, z: ZoneId = zone) = LocalDateTime.of(y, m, d, h, 0).atZone(z).toInstant().toEpochMilli()
    private fun play(id: Long, ms: Long, listened: Long = 60_000, artist: String? = "A$id", genre: String? = null, album: String? = "Album $id") =
        RecapPlay(id, ms, listened, "Song $id", artist, album, id, genre)

    @Test
    fun `weeks start on the locale's first day`() {
        val wed = LocalDate.of(2026, 10, 28)
        assertEquals(DayOfWeek.MONDAY, RecapPeriod.containing(RecapKind.WEEK, wed, uk).start.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, RecapPeriod.containing(RecapKind.WEEK, wed, Locale.US).start.dayOfWeek)
        assertEquals(LocalDate.of(2026, 11, 2), RecapPeriod.containing(RecapKind.WEEK, wed, uk).end)
    }

    @Test
    fun `month and year edges`() {
        val m = RecapPeriod.containing(RecapKind.MONTH, LocalDate.of(2024, 2, 29), uk)
        assertEquals(LocalDate.of(2024, 2, 1), m.start)
        assertEquals(LocalDate.of(2024, 3, 1), m.end)
        assertEquals(LocalDate.of(2024, 1, 1), m.previous().start)
        val y = RecapPeriod.containing(RecapKind.YEAR, LocalDate.of(2025, 12, 31), uk)
        assertEquals(LocalDate.of(2026, 1, 1), y.end)
        assertTrue(y.contains(at(2025, 12, 31, 23), zone))
        assertFalse(y.contains(at(2026, 1, 1, 0), zone))
        assertEquals(m, RecapPeriod.parse(m.key))
    }

    @Test
    fun `time zone decides which day a play belongs to`() {
        val instant = at(2026, 1, 1, 2, ZoneId.of("UTC"))
        val year2026 = RecapPeriod(RecapKind.YEAR, LocalDate.of(2026, 1, 1))
        assertTrue(year2026.contains(instant, ZoneId.of("UTC")))
        assertFalse("still 31 December in New York", year2026.contains(instant, ZoneId.of("America/New_York")))
        assertNotEquals(
            RecapEngine.signature(3, 9, 0, ZoneId.of("UTC"), uk),
            RecapEngine.signature(3, 9, 0, ZoneId.of("America/New_York"), uk),
        )
    }

    @Test
    fun `play counting rule`() {
        assertTrue(RecapEngine.counts(30_000, 240_000))
        assertFalse(RecapEngine.counts(29_999, 240_000))
        assertTrue("half of a short song", RecapEngine.counts(20_000, 40_000))
        assertFalse(RecapEngine.counts(19_000, 40_000))
    }

    @Test
    fun `top lists rank by plays then time then name, skipping unknown tags`() {
        val p = RecapPeriod.containing(RecapKind.WEEK, LocalDate.of(2026, 10, 28), uk)
        val t = at(2026, 10, 27)
        val plays = listOf(
            play(1, t, artist = "Beta"), play(2, t + 1, artist = "Alpha"),
            play(3, t + 2, artist = "<unknown>"), play(3, t + 3, artist = "<unknown>"), play(3, t + 4, artist = "<unknown>"),
        )
        val r = RecapEngine.compute(p, plays, emptyMap(), 0, zone, uk)
        assertEquals(3L, r.topSongs.first().songId)
        assertEquals(listOf(1L, 2L), r.topSongs.drop(1).map { it.songId })
        assertEquals("ties sort by name", listOf("Alpha", "Beta"), r.topArtists.map { it.name })
        assertTrue(r.topGenres.isEmpty())
        assertNull(r.topSongs.first().artist)
    }

    @Test
    fun `older plays count but never add minutes`() {
        val p = RecapPeriod.containing(RecapKind.MONTH, LocalDate.of(2026, 10, 1), uk)
        val r = RecapEngine.compute(p, listOf(play(1, at(2026, 10, 2), listened = 0), play(2, at(2026, 10, 3), listened = 120_000)), emptyMap(), 0, zone, uk)
        assertEquals(2, r.plays)
        assertEquals(1, r.countOnlyPlays)
        assertEquals(2L, r.minutes)
    }

    @Test
    fun `new discoveries are first plays ever inside the period`() {
        val p = RecapPeriod.containing(RecapKind.MONTH, LocalDate.of(2026, 10, 1), uk)
        val plays = listOf(play(1, at(2026, 10, 5)), play(2, at(2026, 10, 6)))
        val first = mapOf(1L to at(2026, 10, 5), 2L to at(2025, 3, 1))
        val r = RecapEngine.compute(p, plays, first, 0, zone, uk)
        assertEquals(1, r.newSongs)
        assertEquals(listOf(1L), r.newSongSamples.map { it.songId })
    }

    @Test
    fun `comparison only when the previous period has listening`() {
        val p = RecapPeriod.containing(RecapKind.MONTH, LocalDate.of(2026, 10, 1), uk)
        val plays = listOf(play(1, at(2026, 10, 5), listened = 120_000))
        assertNull(RecapEngine.compute(p, plays, emptyMap(), 0, zone, uk).changePercent)
        assertEquals(100, RecapEngine.compute(p, plays, emptyMap(), 60_000, zone, uk).changePercent)
        assertEquals(-50, RecapEngine.compute(p, plays, emptyMap(), 240_000, zone, uk).changePercent)
    }

    @Test
    fun `streak, sessions, time of day and replays`() {
        val p = RecapPeriod.containing(RecapKind.MONTH, LocalDate.of(2026, 10, 1), uk)
        val plays = listOf(
            play(1, at(2026, 10, 1, 8)), play(1, at(2026, 10, 1, 8) + 120_000), play(1, at(2026, 10, 1, 9)),
            play(2, at(2026, 10, 2, 9)), play(3, at(2026, 10, 3, 9)), play(4, at(2026, 10, 9, 22)),
        )
        val r = RecapEngine.compute(p, plays, emptyMap(), 0, zone, uk)
        assertEquals(3, r.streakDays)
        assertEquals(TimeOfDay.MORNING, r.favouriteTime)
        assertEquals(1L, r.mostReplayed?.songId)
        assertEquals(3, r.mostReplayedInADay)
        assertEquals(LocalDate.of(2026, 10, 1), r.mostActiveDay)
        // 08:00 to 08:03; the 09:00 play is more than 30 minutes later, so a new session.
        assertEquals(3L * 60_000, r.longestSessionMs)
    }

    @Test
    fun `periods with plays are listed newest first and empty ones never`() {
        val times = listOf(at(2026, 9, 3), at(2026, 10, 2), at(2026, 10, 3))
        val list = RecapEngine.periodsWithPlays(RecapKind.MONTH, times, zone, uk)
        assertEquals(listOf(LocalDate.of(2026, 10, 1) to 2, LocalDate.of(2026, 9, 1) to 1), list.map { it.first.start to it.second })
    }

    @Test
    fun `recap animations are off in reduced motion, efficiency, battery saver and severe heat`() {
        fun groups(limits: com.svara.app.appearance.EffectLimits, reduced: Boolean = false) =
            MotionGroups.resolve(limits, MotionSpec.from(limits, AnimationStyle.SUBTLE, reduced, 1f, 100, false), Customization(), null)
        listOf(
            groups(EffectsPolicy.limitsFor(PerformanceMode.IMMERSIVE), reduced = true),
            groups(EffectsPolicy.limitsFor(PerformanceMode.EFFICIENCY)),
            groups(EffectsPolicy.resolve(PerformanceMode.IMMERSIVE, true, ThermalLevel.NONE)),
            groups(EffectsPolicy.resolve(PerformanceMode.IMMERSIVE, false, ThermalLevel.SEVERE)),
        ).forEach { assertFalse(RecapEngine.animationsAllowed(motionEnabled = true, groups = it, recapAnimations = true)) }
        val ok = groups(EffectsPolicy.limitsFor(PerformanceMode.BALANCED))
        assertTrue(RecapEngine.animationsAllowed(true, ok, true))
        assertFalse(RecapEngine.animationsAllowed(true, ok, recapAnimations = false))
    }
}

class RecapTransitionTest {
    private fun groups(limits: com.svara.app.appearance.EffectLimits, reduced: Boolean = false) =
        MotionGroups.resolve(limits, MotionSpec.from(limits, AnimationStyle.SUBTLE, reduced, 1f, 100, false), Customization(), null)

    @Test
    fun `the wipe is off in every reduced mode`() {
        listOf(
            groups(EffectsPolicy.limitsFor(PerformanceMode.EFFICIENCY)),
            groups(EffectsPolicy.resolve(PerformanceMode.IMMERSIVE, true, ThermalLevel.NONE)),
            groups(EffectsPolicy.resolve(PerformanceMode.IMMERSIVE, false, ThermalLevel.SEVERE)),
        ).forEach { assertEquals(com.svara.app.domain.recap.RecapTransition.FADE, RecapEngine.transition(true, it, true)) }
        val reduced = groups(EffectsPolicy.limitsFor(PerformanceMode.IMMERSIVE), reduced = true)
        assertEquals(com.svara.app.domain.recap.RecapTransition.INSTANT, RecapEngine.transition(false, reduced, true))
        assertEquals(com.svara.app.domain.recap.RecapTransition.WIPE, RecapEngine.transition(true, groups(EffectsPolicy.limitsFor(PerformanceMode.BALANCED)), true))
    }

    @Test
    fun `scrim keeps text at AA over any card colour`() {
        val s = com.svara.app.domain.recap.RecapScrim
        val dark = 0xFF121216.toInt(); val light = 0xFFF4F1EA.toInt()
        listOf(0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FFFF.toInt(), 0xFF808080.toInt()).forEach { c ->
            val a = s.alpha(listOf(c, dark), dark, light, 0.55f, 0.05f, 4.5)
            assertTrue(s.contrast(light, s.blend(c, dark, a)) >= 4.5)
        }
    }
}
