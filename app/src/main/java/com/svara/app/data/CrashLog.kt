package com.svara.app.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps short summaries of uncaught exceptions in app-private storage so the user can read or
 * export them. Nothing leaves the device unless the user exports it. The previous handler still
 * runs, so the system's own crash behaviour is unchanged.
 */
object CrashLog {
    private const val FILE = "crash-log.txt"
    private const val MAX_ENTRIES = 10
    private const val MAX_STACK_LINES = 12
    private const val SEPARATOR = "\n----\n"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, thread.name, error, System.currentTimeMillis()) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun summary(threadName: String, error: Throwable, atMs: Long): String = buildString {
        append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(atMs)))
        append(" on ").append(threadName).append('\n')
        append(error.javaClass.name).append(": ").append(error.message ?: "").append('\n')
        error.stackTrace.take(MAX_STACK_LINES).forEach { append("  at ").append(it).append('\n') }
        error.cause?.let { append("Caused by ").append(it.javaClass.name).append(": ").append(it.message ?: "") }
    }

    /** Adds an entry, keeping the newest [MAX_ENTRIES]. */
    fun record(context: Context, threadName: String, error: Throwable, atMs: Long) {
        val entries = listOf(summary(threadName, error, atMs)) + entries(context)
        file(context).writeText(entries.take(MAX_ENTRIES).joinToString(SEPARATOR))
    }

    fun entries(context: Context): List<String> =
        file(context).takeIf { it.exists() }?.readText()?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()

    fun read(context: Context): String = entries(context).joinToString(SEPARATOR)

    fun clear(context: Context) { file(context).delete() }

    private fun file(context: Context) = File(context.filesDir, FILE)
}
