package com.example.juke.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LyricsTrustTest {
    private fun result(synced: String?, plain: String? = null, duration: Double? = 222.0) =
        LRCLibResult(1, "Rangeen", "Rangeen", "Hasan Raheem", "", duration, false, plain, synced)

    @Test fun untimedTextInSyncedFieldBecomesPlain() {
        val trusted = result("First line\nSecond line").trusted(180)
        assertNull(trusted.syncedLyrics)
        assertEquals("First line\nSecond line", trusted.plainLyrics)
    }

    @Test fun emptyTimedLinesAreNotRenderable() {
        assertEquals(false, LyricsTrust.hasRenderableLine("[00:01.00]\n[00:02.00]"))
        assertEquals(true, LyricsTrust.hasRenderableLine("[00:01.00] words"))
    }

    @Test fun wholeSecondTimingKeepsOnlyTheWords() {
        // Rangeen's LRCLib copies: every stamp .00, up to 57 s off.
        val synced = listOf(0, 6, 26, 32, 42, 52).joinToString("\n") { "[00:%02d.00] line $it".format(it) }
        val trusted = result(synced).trusted(222)
        assertNull(trusted.syncedLyrics)
        assertEquals("line 0\nline 6\nline 26\nline 32\nline 42\nline 52", trusted.plainLyrics)
    }

    @Test fun realTimingIsKept() {
        val synced = "[00:14.95] Subha ki yeh hawa\n[00:18.91] Yeh raaz\n[00:22.66] Puchay\n[00:26.42] Hain\n[00:30.27] Ab\n[00:33.94] Khulay"
        val r = result(synced, "words")
        assertSame(r, r.trusted(205))
    }

    @Test fun timingPastTheRecordLengthIsRejected() {
        val synced = "[00:10.50] a\n[01:00.25] b\n[03:32.40] c"
        assertNull(result(synced, "a b c", duration = 167.0).trusted(222).syncedLyrics)
    }

    @Test fun rules() {
        assertEquals("out_of_order", LyricsTrust.timingProblem(listOf(5_000, 1_000), null))
        assertNull(LyricsTrust.timingProblem(listOf(0, 1_000, 2_000), null)) // too few lines to judge
        assertEquals(listOf(62_500L, 3_000L), LyricsTrust.stampsMs("[01:02.50] a\n[00:03] b\nno stamp"))
    }
}
