package com.example.juke.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class ParseSyncedLyricsTest {
    private val lrc = """
        [ar:Someone]
        [00:01.50] First line
        [00:05.5]Second
        [01:02.123]   Third  
        [00:09.00]
    """.trimIndent()

    @Test
    fun parsesTimesTextAndSkipsTagsAndBlankLines() {
        val lines = parseSyncedLyrics(lrc)
        assertEquals(listOf(1000L, 5000L, 61623L), lines.map { it.timeMs })
        assertEquals(listOf("First line", "Second", "Third"), lines.map { it.text })
    }

    @Test
    fun defaultRenderLeadClampsEarlyLinesAndAllowsPerSongCompensation() {
        assertEquals(0L, parseSyncedLyrics("[00:00.20] Early").single().timeMs)
        assertEquals(1500L, parseSyncedLyrics("[00:01.50] Line", offsetMs = 500L).single().timeMs)
    }

    @Test
    fun appliesOffsetAndNeverGoesNegative() {
        val lines = parseSyncedLyrics(lrc, offsetMs = -2000L)
        assertEquals(listOf(0L, 3000L, 59623L), lines.map { it.timeMs })
    }
}
