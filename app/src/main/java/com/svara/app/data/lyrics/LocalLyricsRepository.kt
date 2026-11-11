package com.svara.app.data.lyrics

import com.svara.app.data.db.LyricsOverrideDao
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.util.LruCache
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.inspector.MetadataRetriever
import com.svara.app.di.IoDispatcher
import com.svara.app.domain.model.FolderRuleType
import com.svara.app.domain.model.LyricLine
import com.svara.app.domain.model.Lyrics
import com.svara.app.domain.model.LyricsSource
import com.svara.app.domain.model.Song
import com.svara.app.domain.repository.FolderRepository
import com.svara.app.domain.repository.LyricsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds lyrics on the device: lyrics the user imported or edited in SVARĀ first, then the file's
 * own tags, then a sidecar "<name>.lrc" / "<name>.txt" next to it. Nothing is fetched from the
 * network, and the user's per-song timing offset is attached to whichever is used.
 */
@Singleton
class LocalLyricsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folders: FolderRepository,
    private val overrides: LyricsOverrideDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : LyricsRepository {

    private val cache = LruCache<Long, Result>(64)

    private class Result(val lyrics: Lyrics?)

    override suspend fun lyricsFor(song: Song): Lyrics? {
        val override = withContext(io) { overrides.get(song.id) }
        val base = override?.text?.let { LyricsText.lyrics(it, LyricsSource.IMPORTED) } ?: fileLyrics(song)
        return base?.copy(offsetMs = override?.offsetMs ?: 0L)
    }

    /** Lyrics from the audio file's tags or a sidecar file, ignoring anything imported in SVARĀ. */
    override suspend fun fileLyrics(song: Song): Lyrics? {
        cache.get(song.id)?.let { return it.lyrics }
        val lyrics = withContext(io) {
            embedded(song)?.let { LyricsText.lyrics(it, LyricsSource.EMBEDDED) }
                ?: sidecar(song)?.let { LyricsText.lyrics(it, LyricsSource.SIDECAR_FILE) }
        }?.takeIf { it.text.isNotBlank() }
        cache.put(song.id, Result(lyrics))
        return lyrics
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private suspend fun embedded(song: Song): String? = try {
        MetadataRetriever.Builder(context, MediaItem.fromUri(song.contentUri)).build().use { retriever ->
            val groups = withTimeoutOrNull(RETRIEVE_TIMEOUT_MS) { retriever.retrieveTrackGroups().await() } ?: return null
            for (g in 0 until groups.length) {
                val group = groups[g]
                for (t in 0 until group.length) {
                    group.getFormat(t).metadata?.let(::lyricsFromMetadata)?.let { return it }
                }
            }
            null
        }
    } catch (e: Exception) {
        Log.d(TAG, "No embedded lyrics for ${song.id}: ${e.message}")
        null
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun lyricsFromMetadata(metadata: Metadata): String? {
        for (i in 0 until metadata.length()) {
            when (val entry = metadata[i]) {
                is BinaryFrame -> if (entry.id == "USLT") LyricsText.parseUslt(entry.data)?.let { return it }
                is TextInformationFrame -> if (LyricsText.isLyricsFrame(entry.id, entry.description)) entry.values.firstOrNull()?.let { return it }
                is VorbisComment -> if (entry.key.equals("LYRICS", true) || entry.key.equals("UNSYNCEDLYRICS", true)) return entry.value
            }
        }
        return null
    }

    private suspend fun sidecar(song: Song): String? {
        val base = song.fileName.substringBeforeLast('.', song.fileName)
        val names = listOf("$base.lrc", "$base.txt", "$base.LRC", "$base.TXT")

        // A granted SAF tree covering the song's folder lets us read non-media files on any API level.
        val trees = folders.rules.first().filter {
            it.type == FolderRuleType.INCLUDE && it.accessGranted && it.treeUri != null && song.folderKey.isWithin(it.key)
        }
        for (rule in trees) {
            for (name in names) {
                val docId = "${rule.key.volume}:${song.relativePath}$name"
                val uri = runCatching { DocumentsContract.buildDocumentUriUsingTree(Uri.parse(rule.treeUri), docId) }.getOrNull() ?: continue
                readCapped { context.contentResolver.openInputStream(uri) }?.let { return it }
            }
        }

        // Fallback for older devices with legacy storage access.
        val dir = song.dataPath?.let { File(it).parentFile } ?: return null
        for (name in names) {
            val file = File(dir, name)
            if (file.canRead()) readCapped { file.inputStream() }?.let { return it }
        }
        return null
    }

    private inline fun readCapped(open: () -> InputStream?): String? = try {
        open()?.use { stream ->
            val bytes = stream.readNBytesCompat(MAX_SIDECAR_BYTES)
            LyricsText.decode(bytes)
        }
    } catch (_: Exception) {
        null
    }

    private fun InputStream.readNBytesCompat(max: Int): ByteArray {
        val buffer = ByteArray(max)
        var read = 0
        while (read < max) {
            val n = read(buffer, read, max - read)
            if (n < 0) break
            read += n
        }
        return buffer.copyOf(read)
    }

    private companion object {
        const val TAG = "LocalLyrics"
        const val RETRIEVE_TIMEOUT_MS = 5_000L
        const val MAX_SIDECAR_BYTES = 256 * 1024
    }
}

/** Pure text helpers for lyrics, unit tested. */
object LyricsText {
    private val TIMESTAMP = Regex("""\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?]""")
    private val WORD_TIMESTAMP = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")
    private val TAG_LINE = Regex("""^\[[a-zA-Z#]+:.*]$""")

    private val LYRICS_DESCRIPTIONS = setOf("LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS", "USLT")

    /**
     * Text frames that carry lyrics: MP4 ©lyr (mapped to USLT by Media3), and user-defined TXXX
     * frames that some taggers (and ffmpeg) write instead of a real USLT frame.
     */
    fun isLyricsFrame(id: String, description: String?): Boolean =
        id == "USLT" || id == "©lyr" || (id == "TXXX" && description?.uppercase() in LYRICS_DESCRIPTIONS)

    private val OFFSET = Regex("""^\[offset:\s*([+-]?\d+)\s*]$""", RegexOption.IGNORE_CASE)
    private val LEADING_TIMES = Regex("""^((?:\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?])+)(.*)$""")
    private val ONE_TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /**
     * Builds [Lyrics] from raw text. Timed LRC also keeps its lines with times; embedded text
     * without timing is kept exactly as tagged, sidecar text is cleaned of LRC tags.
     */
    fun lyrics(raw: String, source: LyricsSource): Lyrics {
        val timed = parseTimed(raw)
        val text = if (timed.isEmpty() && source == LyricsSource.EMBEDDED) raw else toPlainText(raw)
        return Lyrics(text, source, timed, raw = raw)
    }

    /**
     * LRC lines with their times, sorted. A line may carry several times ("[00:12][01:40]chorus");
     * [offset:ms] shifts every time (positive = earlier, as in the LRC convention). Word-level
     * times are dropped. Empty when the text has no line timing.
     */
    fun parseTimed(raw: String): List<LyricLine> {
        var offset = 0L
        val out = ArrayList<LyricLine>()
        raw.replace("\r\n", "\n").lines().forEach { rawLine ->
            val line = rawLine.trim()
            OFFSET.matchEntire(line)?.let { offset = it.groupValues[1].toLong(); return@forEach }
            val m = LEADING_TIMES.matchEntire(line) ?: return@forEach
            val text = m.groupValues[2].replace(WORD_TIMESTAMP, "").trim()
            ONE_TIME.findAll(m.groupValues[1]).forEach { t ->
                val min = t.groupValues[1].toLong()
                val sec = t.groupValues[2].toLong()
                val frac = t.groupValues[3]
                val fracMs = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                out += LyricLine(((min * 60 + sec) * 1000 + fracMs - offset).coerceAtLeast(0), text)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    /** Index of the line playing at [positionMs], or -1 before the first line. */
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        return found
    }

    /** Strips LRC timing so lyrics without a timed view display as plain text. */
    fun toPlainText(raw: String): String =
        raw.replace("\r\n", "\n").lines()
            .map { it.trim() }
            .filterNot { TAG_LINE.matches(it) }
            .map { it.replace(TIMESTAMP, "").replace(WORD_TIMESTAMP, "").trim() }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

    /**
     * ID3v2 USLT frame: encoding(1) language(3) descriptor(null-terminated) text.
     * Encodings: 0 ISO-8859-1, 1 UTF-16 with BOM, 2 UTF-16BE, 3 UTF-8.
     */
    fun parseUslt(data: ByteArray): String? {
        if (data.size < 5) return null
        val encoding = data[0].toInt()
        val charset: Charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> return null
        }
        val wide = encoding == 1 || encoding == 2
        val start = 4
        var end = start
        if (wide) {
            while (end + 1 < data.size && !(data[end].toInt() == 0 && data[end + 1].toInt() == 0)) end += 2
            end = (end + 2).coerceAtMost(data.size)
        } else {
            while (end < data.size && data[end].toInt() != 0) end++
            end = (end + 1).coerceAtMost(data.size)
        }
        if (end >= data.size) return null
        var textEnd = data.size
        while (textEnd > end && data[textEnd - 1].toInt() == 0) textEnd--
        if (wide && (textEnd - end) % 2 != 0) textEnd++
        val text = String(data, end, (textEnd - end).coerceAtMost(data.size - end), charset).trim()
        return text.ifEmpty { null }
    }

    /** Sidecar files are usually UTF-8; fall back to Latin-1 when the bytes aren't valid UTF-8. */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, Charsets.UTF_16LE).drop(1)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, Charsets.UTF_16BE).drop(1)
        val decoder = Charsets.UTF_8.newDecoder()
        return try {
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        } catch (_: java.nio.charset.CharacterCodingException) {
            String(bytes, Charsets.ISO_8859_1)
        }
    }
}
