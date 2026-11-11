package com.svara.app.appearance

import com.svara.app.appearance.tokens.GovernorTokens
import com.svara.app.appearance.tokens.PageMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPolicyTest {

    private fun motion(limits: EffectLimits, reduced: Boolean = false) =
        MotionSpec.from(limits, AnimationStyle.SUBTLE, reduced, speed = 1f, intensityPercent = 100, presetFadesOnly = false)

    private fun groups(limits: EffectLimits, reduced: Boolean = false, custom: Customization = Customization(), governed: MotionLevel? = null) =
        MotionGroups.resolve(limits, motion(limits, reduced), custom, governed)

    private fun assertMinimal(g: MotionGroups) {
        assertEquals(MotionLevel.MINIMAL, g.level)
        assertFalse(g.sharedElements)
        assertFalse(g.scrollEffects)
        assertFalse(g.parallax)
        assertFalse(g.staggers)
        assertFalse(g.songChange)
        assertTrue(g.pageMs <= PageMotion.MINIMAL_FADE_MS)
    }

    @Test
    fun `reduced motion turns every group off`() =
        assertMinimal(groups(EffectsPolicy.limitsFor(PerformanceMode.IMMERSIVE), reduced = true))

    @Test
    fun `efficiency turns every group off`() =
        assertMinimal(groups(EffectsPolicy.limitsFor(PerformanceMode.EFFICIENCY)))

    @Test
    fun `battery saver turns every group off`() =
        assertMinimal(groups(EffectsPolicy.resolve(PerformanceMode.IMMERSIVE, batterySaver = true, thermal = ThermalLevel.NONE)))

    @Test
    fun `severe heat turns every group off`() =
        assertMinimal(groups(EffectsPolicy.resolve(PerformanceMode.IMMERSIVE, batterySaver = false, thermal = ThermalLevel.SEVERE)))

    @Test
    fun `balanced transitions stay between 200 and 300 ms`() {
        val g = groups(EffectsPolicy.limitsFor(PerformanceMode.BALANCED))
        assertEquals(MotionLevel.BALANCED, g.level)
        assertTrue(g.pageMs in PageMotion.BALANCED_MIN_MS..PageMotion.BALANCED_MAX_MS)
        assertTrue(g.parallax && g.scrollEffects && g.songChange && g.sharedElements)
    }

    @Test
    fun `user switches turn their group off`() {
        val g = groups(
            EffectsPolicy.limitsFor(PerformanceMode.IMMERSIVE),
            custom = Customization(pageTransitions = false, scrollEffects = false, songChangeAnimation = false),
        )
        assertEquals(0, g.pageMs)
        assertFalse(g.scrollEffects || g.parallax || g.songChange || g.sharedElements)
    }

    @Test
    fun `governed level never lifts above the ceiling or drops below light`() {
        val immersive = EffectsPolicy.limitsFor(PerformanceMode.IMMERSIVE)
        assertEquals(MotionLevel.LIGHT, groups(immersive, governed = MotionLevel.MINIMAL).level)
        assertEquals(MotionLevel.BALANCED, groups(immersive, governed = MotionLevel.BALANCED).level)
        assertEquals(MotionLevel.MINIMAL, groups(EffectsPolicy.limitsFor(PerformanceMode.EFFICIENCY), governed = MotionLevel.IMMERSIVE).level)
        assertEquals(MotionLevel.IMMERSIVE, groups(immersive, custom = Customization(smartSmoothing = false), governed = MotionLevel.LIGHT).level)
    }

    /** Feeds [seconds] of 60 fps frames, [jankEvery] = n marks every nth frame janky (0 = none). */
    private fun MotionGovernor.feed(startMs: Long, seconds: Int, jankEvery: Int): Long {
        var t = startMs
        repeat(seconds * 60) { i ->
            onFrame(t, janky = jankEvery > 0 && i % jankEvery == 0)
            t += 16
        }
        return t
    }

    @Test
    fun `governor steps down one level per bad window and never below its floor`() {
        val g = MotionGovernor(MotionLevel.IMMERSIVE)
        var t = g.feed(0, 4, jankEvery = 2)
        assertEquals(MotionLevel.BALANCED, g.level)
        assertTrue(g.reduced)
        t = g.feed(t, 4, jankEvery = 2)
        assertEquals(MotionLevel.LIGHT, g.level)
        g.feed(t, 10, jankEvery = 2)
        assertEquals(MotionLevel.LIGHT, g.level)
    }

    @Test
    fun `governor ignores a few slow frames`() {
        val g = MotionGovernor(MotionLevel.BALANCED)
        g.feed(0, 10, jankEvery = 20)
        assertEquals(MotionLevel.BALANCED, g.level)
    }

    @Test
    fun `governor steps back up after stable frames at most once a minute`() {
        val g = MotionGovernor(MotionLevel.IMMERSIVE)
        var t = g.feed(0, 4, jankEvery = 2)
        t = g.feed(t, 4, jankEvery = 2)
        assertEquals(MotionLevel.LIGHT, g.level)
        val stableSeconds = (GovernorTokens.STABLE_MS / 1000).toInt() + 4
        t = g.feed(t, stableSeconds, jankEvery = 0)
        assertEquals(MotionLevel.BALANCED, g.level)
        t = g.feed(t, stableSeconds, jankEvery = 0)
        assertEquals("one step per minute", MotionLevel.BALANCED, g.level)
        g.feed(t, 40, jankEvery = 0)
        assertEquals(MotionLevel.IMMERSIVE, g.level)
        assertFalse(g.reduced)
    }

    @Test
    fun `governor never goes above the user's ceiling`() {
        val g = MotionGovernor(MotionLevel.BALANCED)
        g.feed(0, 120, jankEvery = 0)
        assertEquals(MotionLevel.BALANCED, g.level)
    }

    @Test
    fun `duplicate navigation and song requests are ignored`() {
        val guard = RepeatGuard()
        assertTrue(guard.accept("album/1", 0))
        assertFalse(guard.accept("album/1", PageMotion.DUPLICATE_WINDOW_MS - 1))
        assertTrue(guard.accept("album/2", PageMotion.DUPLICATE_WINDOW_MS))
        assertTrue(guard.accept("album/2", PageMotion.DUPLICATE_WINDOW_MS * 3))
        val songs = RepeatGuard()
        assertTrue(songs.accept(42L, 0))
        assertFalse(songs.accept(42L, 10))
    }

    @Test
    fun `every new motion setting is in the motion reset section`() {
        val motionFields = CustomField.entries.filter { it.section == StudioSection.MOTION }
        listOf(CustomField.PAGE_TRANSITIONS, CustomField.SCROLL_EFFECTS, CustomField.SONG_CHANGE_ANIMATION, CustomField.SMART_SMOOTHING, CustomField.TRANSITION_SPEED)
            .forEach { assertTrue(it.name, it in motionFields) }
    }

    @Test
    fun `new motion settings default on and old data still loads`() {
        val old = CustomizationCodec.decode(mapOf(CustomField.INTENSITY to "80"))
        assertTrue(old.pageTransitions && old.scrollEffects && old.songChangeAnimation && old.smartSmoothing)
        val off = CustomizationCodec.decode(mapOf(CustomField.SCROLL_EFFECTS to "false", CustomField.SMART_SMOOTHING to "garbage"))
        assertFalse(off.scrollEffects)
        assertTrue(off.smartSmoothing)
    }

    @Test
    fun `a few slow frames after a still screen do not step motion down`() {
        val g = MotionGovernor(MotionLevel.IMMERSIVE)
        // Bursts of three frames, one janky, with idle gaps between (only drawn frames arrive).
        var t = 0L
        repeat(20) {
            g.onFrame(t, janky = true); g.onFrame(t + 16, janky = false); g.onFrame(t + 32, janky = false)
            t += 1_000
        }
        assertEquals(MotionLevel.IMMERSIVE, g.level)
    }

    @Test
    fun `more than ten percent janky in a busy window steps down one level, recovers after stable time, never below the floor`() {
        val g = MotionGovernor(MotionLevel.IMMERSIVE, floor = MotionLevel.BALANCED)
        var t = g.feed(0, 4, jankEvery = 5)
        assertEquals(MotionLevel.BALANCED, g.level)
        t = g.feed(t, 8, jankEvery = 5)
        assertEquals("never below the user's floor", MotionLevel.BALANCED, g.level)
        t = g.feed(t, 35, jankEvery = 0)
        assertEquals(MotionLevel.IMMERSIVE, g.level)
    }
}
