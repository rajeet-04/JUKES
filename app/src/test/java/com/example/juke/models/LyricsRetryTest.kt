package com.example.juke.models

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsRetryTest {
    private val lyrics = LRCLibResult(1, "Song", "Song", "Artist", "Album", 180.0, false, "Words", null)
    @Test fun emptyFirstResultAutomaticallyRetries() = runBlocking {
        var attempts = 0
        assertEquals(lyrics, fetchLyricsWithRetry { if (++attempts == 1) null else lyrics })
    }
    @Test fun successfulResultDoesNotRefetch() = runBlocking {
        var attempts = 0
        assertEquals(lyrics, fetchLyricsWithRetry { attempts++; lyrics })
        assertEquals(1, attempts)
    }
    @Test fun missingLyricsStopsAfterOneRetry() = runBlocking {
        var attempts = 0
        assertNull(fetchLyricsWithRetry { attempts++; null })
        assertEquals(2, attempts)
    }
}
