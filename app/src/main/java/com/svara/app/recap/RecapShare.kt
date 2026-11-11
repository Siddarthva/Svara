package com.svara.app.recap

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.svara.app.ui.theme.RecapTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What one shared image shows: a heading, a few lines, all already on screen. */
data class SharePoster(val heading: String, val lines: List<String>, val period: String)

/**
 * Renders recap posters to images in the app cache and hands them to the system share sheet, or
 * saves them to Pictures through MediaStore (no storage permission). Nothing is uploaded.
 */
object RecapShare {
    private const val DIR = "recap_share"

    /** Theme colours as ARGB ints: background start and end, text. */
    data class Colours(val start: Int, val end: Int, val text: Int, val accent: Int)

    suspend fun render(poster: SharePoster, colours: Colours): Bitmap = withContext(Dispatchers.Default) {
        val w = RecapTokens.SHARE_WIDTH
        val h = RecapTokens.SHARE_HEIGHT
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), colours.start, colours.end, Shader.TileMode.CLAMP)
        })
        val margin = w / 12f
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colours.text }
        text.textSize = w / 22f
        var y = h / 6f
        c.drawText(poster.period, margin, y, text)
        text.textSize = w / 11f
        text.typeface = Typeface.DEFAULT_BOLD
        y += w / 7f
        wrap(poster.heading, text, w - 2 * margin).forEach { line -> c.drawText(line, margin, y, text); y += text.textSize * 1.2f }
        text.typeface = Typeface.DEFAULT
        text.textSize = w / 18f
        y += w / 18f
        poster.lines.forEach { l ->
            wrap(l, text, w - 2 * margin).forEach { line -> c.drawText(line, margin, y, text); y += text.textSize * 1.35f }
        }
        val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colours.accent; textSize = w / 26f; typeface = Typeface.DEFAULT_BOLD }
        c.drawText("SVARĀ", margin, h - margin, mark)
        bmp
    }

    private fun wrap(s: String, p: Paint, max: Float): List<String> {
        val out = mutableListOf<String>()
        var line = ""
        s.split(' ').forEach { word ->
            val next = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(next) > max && line.isNotEmpty()) { out += line; line = word } else line = next
        }
        if (line.isNotEmpty()) out += line
        return out
    }

    /** Writes the poster to cache (removing older ones) and opens the share sheet. */
    suspend fun share(context: Context, bitmap: Bitmap) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            File(dir, "recap-${System.currentTimeMillis()}.png").also { f -> f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share recap").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Saving needs scoped storage (Android 10+); older versions only offer Share. */
    val canSave: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    suspend fun save(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        if (!canSave) return@withContext false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "recap-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Recap")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        runCatching { resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }.isSuccess
    }
}
