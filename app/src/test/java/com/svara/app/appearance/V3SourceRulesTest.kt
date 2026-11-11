package com.svara.app.appearance

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V3 composables (artwork system, state screens, Now Playing styles) take every colour, size and
 * duration from tokens (`ui/theme/V3Tokens.kt`, preset tokens, Spacing/Radii), like the preset
 * composables. Same rules as [PresetSourceRulesTest], over the V3 sources.
 */
class V3SourceRulesTest {

    private val root = File("src/main/java/com/svara/app")
    private val sources = listOf("ui/artwork", "ui/states", "nowplaying/NowPlayingStyles.kt", "nowplaying/LyricsStudio.kt", "nowplaying/AudioScreens.kt")

    private val forbidden = listOf(
        Regex("""\bColor\(\s*0x""") to "hard-coded colour",
        Regex("""\bArgb\(\s*0x""") to "hard-coded colour",
        Regex("""\bColor\(\s*(red|green|blue)\s*=""") to "hard-coded colour",
        Regex("""\bColor\.(White|Black|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|DarkGray|LightGray)\b""") to "named colour",
        Regex("""(?<![\w.])\d+(\.\d+)?f?\.(dp|sp|em)\b""") to "hard-coded size",
        // Scaling the theme's size (fontSize = style.fontSize * scale) is allowed; a literal isn't.
        Regex("""\bfontSize\s*=\s*\d""") to "hard-coded font size",
        Regex("""\b(tween|delay|keyframes|snap)\(\s*\d""") to "hard-coded duration",
        Regex("""\bdurationMillis\s*=\s*\d""") to "hard-coded duration",
        Regex("""\bspring\([^)]*\d""") to "hard-coded spring",
        Regex("""\bCubicBezierEasing\(""") to "hard-coded easing",
    )

    private fun code(line: String): String {
        val t = line.trimStart()
        if (t.startsWith("*") || t.startsWith("/*")) return ""
        return line.substringBefore("//")
    }

    @Test
    fun `v3 composables use tokens only`() {
        val files = sources.flatMap { File(root, it).walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList() }
        assertTrue("V3 sources not found under ${root.absolutePath}", files.size >= 4)
        val problems = files.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                val c = code(line)
                forbidden.firstOrNull { (re, _) -> re.containsMatchIn(c) }
                    ?.let { (_, what) -> "${file.relativeTo(root)}:${i + 1} $what: ${line.trim()}" }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
