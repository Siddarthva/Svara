package com.svara.app.appearance

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Version 2.1 has no mood feature: none of the code added for it (colour, covers, ambient,
 * blur, the Appearance page) declares or uses a mood symbol. Older mood-related lines are
 * listed in docs/v2.1-findings.md and left alone.
 */
class NoMoodScanTest {

    private val main = File("src/main/java/com/svara/app")

    private val v21 = listOf(
        "appearance/colour", "data/covers", "settings/AppearancePage.kt", "settings/CoverEditor.kt",
        "data/artwork/Artwork.kt", "di/CoverEntryPoint.kt",
    )

    @Test
    fun `no mood symbols in v2_1 code`() {
        val files = v21.flatMap { p -> File(main, p).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        assertTrue("v2.1 sources not found under ${main.absolutePath}", files.size >= 8)
        val problems = files.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if ("mood" in line.lowercase()) "${f.relativeTo(main)}:${i + 1}: ${line.trim()}" else null }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
