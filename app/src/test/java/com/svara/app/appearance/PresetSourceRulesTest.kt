package com.svara.app.appearance

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Preset composables take every colour, size, radius, duration and easing from tokens
 * (ThemeSpec / ThemeTokens, through LocalPresetUi). This scans every source file under the
 * preset packages for raw values and fails on any match. Token files live outside these
 * packages (appearance/tokens, ui/theme), so they are not scanned.
 */
class PresetSourceRulesTest {

    private val presetDir = File("src/main/java/com/svara/app/ui/presets")

    private val forbidden = listOf(
        Regex("""\bColor\(\s*0x""") to "hard-coded colour",
        Regex("""\bArgb\(\s*0x""") to "hard-coded colour",
        Regex("""\bColor\(\s*(red|green|blue)\s*=""") to "hard-coded colour",
        Regex("""\bColor\.(White|Black|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|DarkGray|LightGray)\b""") to "named colour",
        Regex("""(?<![\w.])\d+(\.\d+)?f?\.(dp|sp|em)\b""") to "hard-coded size",
        Regex("""\bfontSize\s*=""") to "hard-coded font size",
        Regex("""\b(tween|delay|keyframes|snap)\(\s*\d""") to "hard-coded duration",
        Regex("""\bdurationMillis\s*=\s*\d""") to "hard-coded duration",
        Regex("""\bspring\([^)]*\d""") to "hard-coded spring",
        Regex("""\bCubicBezierEasing\(""") to "hard-coded easing",
    )

    /** Strips line comments and KDoc/block-comment lines so prose can mention numbers. */
    private fun code(line: String): String {
        val t = line.trimStart()
        if (t.startsWith("*") || t.startsWith("/*")) return ""
        return line.substringBefore("//")
    }

    @Test
    fun `preset composables use tokens only`() {
        val files = presetDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("no preset sources found in ${presetDir.absolutePath}", files.size >= 20)
        val problems = files.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                val c = code(line)
                forbidden.firstOrNull { (re, _) -> re.containsMatchIn(c) }
                    ?.let { (_, what) -> "${file.relativeTo(presetDir)}:${i + 1} $what: ${line.trim()}" }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `the scanner catches raw values`() {
        val samples = listOf(
            "Modifier.size(48.dp)", "fontSize = 14.sp", "Color(0xFF1DB954)", "tween(300)", "delay(1000)",
            "spring(dampingRatio = 0.8f)", "Color.White", "Argb(0xFF000000)", "letterSpacing = 0.1.em",
        )
        samples.forEach { s -> assertTrue(s, forbidden.any { (re, _) -> re.containsMatchIn(s) }) }
        val allowed = listOf("p.spacing.touch", "tween(ms)", "delay(frameMs)", "p.sizes.icon", "spring(dampingRatio = p.motion.springDamping)", "TalaSpec.LARGE_TITLE_COLLAPSE_DP.dp")
        allowed.forEach { s -> assertTrue(s, forbidden.none { (re, _) -> re.containsMatchIn(s) }) }
    }
}
