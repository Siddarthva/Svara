package com.svara.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.svara.app.appearance.Argb
import com.svara.app.appearance.Contrast
import com.svara.app.ui.artwork.readableOn
import com.svara.app.ui.components.ScrollDirectionTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeLayoutTest {

    @Test
    fun `search button hides on scroll down and returns on scroll up`() {
        val t = ScrollDirectionTracker(thresholdPx = 24f)
        assertTrue(t.visible)
        t.onDelta(-10f)
        assertTrue("small moves don't hide it", t.visible)
        t.onDelta(-20f)
        assertFalse(t.visible)
        t.onDelta(-500f)
        assertFalse(t.visible)
        t.onDelta(30f)
        assertTrue(t.visible)
    }

    @Test
    fun `a change of direction starts counting afresh`() {
        val t = ScrollDirectionTracker(thresholdPx = 24f)
        t.onDelta(-100f)
        assertFalse(t.visible)
        // Jitter up and down never adds up to a flip.
        repeat(10) { t.onDelta(10f); t.onDelta(-10f) }
        assertFalse(t.visible)
    }

    @Test
    fun `zero deltas change nothing`() {
        val t = ScrollDirectionTracker()
        t.onDelta(0f)
        assertTrue(t.visible)
    }

    @Test
    fun `fallback tile symbols reach at least 3 to 1 on every colour`() {
        var worst = Double.MAX_VALUE
        for (r in 0..255 step 15) for (g in 0..255 step 15) for (b in 0..255 step 15) {
            val bg = Color(red = r / 255f, green = g / 255f, blue = b / 255f)
            val fg = readableOn(bg)
            val ratio = Contrast.ratio(argb(fg), argb(bg))
            if (ratio < worst) worst = ratio
        }
        assertTrue("worst contrast $worst", worst >= 3.0)
    }

    private fun argb(c: Color) = Argb(c.toArgb().toLong() and 0xFFFFFFFFL)
}
