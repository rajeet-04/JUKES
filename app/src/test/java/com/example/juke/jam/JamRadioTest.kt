package com.example.juke.jam

import org.junit.Assert.assertEquals
import org.junit.Test

class JamRadioTest {
    @Test
    fun topsUpToTheWantedSongsAfterTheCurrentOne() {
        assertEquals(5, JamRadio.needed(queueSize = 3, currentIndex = 2, want = 5)) // current is last: all 5 needed
        assertEquals(2, JamRadio.needed(queueSize = 10, currentIndex = 6, want = 5)) // 3 left after it
        assertEquals(0, JamRadio.needed(queueSize = 10, currentIndex = 2, want = 5)) // plenty left
    }

    @Test
    fun doesNothingWithoutACurrentSongOrWhenNearlyFull() {
        assertEquals(0, JamRadio.needed(queueSize = 4, currentIndex = -1, want = 5))
        assertEquals(0, JamRadio.needed(queueSize = MAX_QUEUE - 2, currentIndex = MAX_QUEUE - 3, want = 5))
    }
}
