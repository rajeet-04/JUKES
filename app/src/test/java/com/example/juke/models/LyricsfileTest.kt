package com.example.juke.models

import com.example.juke.ui.screens.parseSyncedLyrics
import com.example.juke.utils.LyricsRomanizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsfileTest {
    // LRCLIB 3204715 (Blinding Lights), as served
    private val yaml = """
        version: '1.0'
        metadata:
          title: Blinding Lights
          artist: The Weeknd
          duration_ms: 200000
          instrumental: false
        lines:
        - text: Yeah
          start_ms: 13120
          end_ms: 14551
          words:
          - text: Yeah
            start_ms: 13159
        - text: ''
          start_ms: 14551
          end_ms: 27170
        - text: I've been tryna call
          start_ms: 27170
          end_ms: 28268
          words:
          - text: 'I''ve '
            start_ms: 27209
          - text: 'been '
            start_ms: 27598
          - text: "tryna "
            start_ms: 27889
          - text: call
            start_ms: 28200
        plain: |
          Yeah
          I've been tryna call
    """.trimIndent()

    @Test fun wordTimingsBecomeEnhancedLrc() {
        assertEquals(
            "[00:13.12]<00:13.16>Yeah\n[00:14.55]\n[00:27.17]<00:27.21>I've <00:27.60>been <00:27.89>tryna <00:28.20>call",
            Lyricsfile.toEnhancedLrc(yaml)
        )
    }

    @Test fun lineOnlyFileIsLeftToThePlainLrc() {
        assertNull(Lyricsfile.toEnhancedLrc("version: '1.0'\nlines:\n- text: Hoya Hoya\n  start_ms: 8440\n"))
        val r = LRCLibResult(1, "x", "x", "y", "", 200.0, false, "p", "[00:01.00] a", lyricsfile = null)
        assertEquals(r, r.withWordSync())
    }

    @Test fun parserKeepsWordsAndTheLineText() {
        val line = parseSyncedLyrics(Lyricsfile.toEnhancedLrc(yaml)!!, offsetMs = 0).last()
        assertEquals("I've been tryna call", line.text)
        assertEquals(listOf("I've ", "been ", "tryna ", "call"), line.words.map { it.text })
        assertEquals(27_210L - 120, line.words.first().timeMs) // word lead, not the line's
    }

    @Test fun romanizedLinesKeepWordTimingsWhenTheWordsLineUp() {
        val body = "<00:16.10>ਸੋਚਾਂ <00:16.60>ਤੇਰੇ <00:17.00>ਬਾਰੇ"
        assertEquals("<00:16.10>Sochan <00:16.60>tere <00:17.00>baare", LyricsRomanizer.restampWords(body, "Sochan tere baare"))
        assertEquals("Sochan tere", LyricsRomanizer.restampWords(body, "Sochan tere")) // word count changed
    }
}
