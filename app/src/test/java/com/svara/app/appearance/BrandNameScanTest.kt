package com.svara.app.appearance

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * No other app's name, design system or proprietary font appears anywhere in the app: Kotlin
 * sources (class names and comments included), resources, the manifest, or file names. The
 * presets use their own names only. The design sources that name those apps stay in the project
 * root and docs, outside the app.
 */
class BrandNameScanTest {

    private val main = File("src/main")

    /** Lower-case terms; matched case-insensitively as substrings. */
    private val brands = listOf(
        "spotify", "apple", "gaana", "amazon", "alexa", "airplay", "itunes",
        "encore", "circular std", "amazon ember", "san francisco", "sf pro",
    )

    /** Registered identifiers that happen to contain a term, with why each is needed. */
    private val allowed = listOf(
        // IANA media type for .m3u8 playlists, needed by M3U import's file picker.
        "application/vnd.apple.mpegurl",
    )

    /** Tool-generated files listing third-party class names (Baseline Profiles). */
    private fun generated(f: File) = f.invariantSeparatorsPath.contains("/generated/")

    @Test
    fun `no brand names in sources, resources or file names`() {
        val files = main.walkTopDown().filter { it.isFile && !generated(it) }.toList()
        assertTrue("no sources found in ${main.absolutePath}", files.size > 50)
        val problems = mutableListOf<String>()
        files.forEach { f ->
            val name = f.name.lowercase()
            brands.filter { it in name }.forEach { problems += "file name ${f.relativeTo(main)}: $it" }
            if (f.extension in setOf("kt", "xml", "json", "txt", "pro", "md")) {
                f.readLines().forEachIndexed { i, line ->
                    var l = line.lowercase()
                    allowed.forEach { l = l.replace(it, "") }
                    brands.filter { it in l }.forEach { problems += "${f.relativeTo(main)}:${i + 1} '$it': ${line.trim()}" }
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `the preset names are the only names used`() {
        val names = PresetId.entries.map { it.displayName }
        assertTrue(names.containsAll(listOf("ETHEREAL", "Halo", "SOLACE", "OCTAVE", "ZENITH")))
        names.forEach { n -> brands.forEach { b -> assertTrue("$n contains $b", b !in n.lowercase()) } }
    }
}
