package com.svara.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.svara.app.data.portability.BackupFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupAndCrashLogTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun rejects(text: String) {
        try { BackupFormat.parse(text); fail("accepted: $text") } catch (_: BackupFormat.InvalidBackup) { }
    }

    @Test
    fun `invalid or unknown backups are rejected before anything changes`() {
        rejects("not json")
        rejects("{}")
        rejects("""{"app":"x","format":99}""")
        rejects("""{"app":"x","format":2,"playlists":{}}""")
        rejects("""{"app":"x","format":2,"history":[{"path":"a"}]}""")
        rejects("""{"app":"x","format":2,"playlists":[{"songs":[]}]}""")
    }

    @Test
    fun `format 1 and 2 backups are accepted`() {
        BackupFormat.parse("""{"app":"x","format":1,"settings":{},"playlists":[]}""")
        val r = BackupFormat.parse("""{"app":"x","format":2,"favourites":["Music/a.mp3"],"history":[{"path":"Music/a.mp3","at":5,"ms":1000}]}""")
        assertEquals(1, r.getJSONArray("history").length())
    }

    @Test
    fun `crash log keeps the newest ten summaries on the device and clears`() {
        CrashLog.clear(context)
        repeat(12) { CrashLog.record(context, "main", IllegalStateException("boom $it"), it * 1000L) }
        val entries = CrashLog.entries(context)
        assertEquals(10, entries.size)
        assertTrue(entries.first().contains("boom 11"))
        assertTrue(entries.first().contains("IllegalStateException"))
        CrashLog.clear(context)
        assertTrue(CrashLog.read(context).isEmpty())
    }
}
