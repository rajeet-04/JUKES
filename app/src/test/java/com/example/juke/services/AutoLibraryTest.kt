package com.example.juke.services

import com.example.juke.models.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoLibraryTest {
    private fun track(stream: Boolean) = Track(uuid = "u1", title = "t", artist = "a", durationSec = 1, isStream = stream)

    @Test fun pagesAreSlicedAndCappedAtOneHundred() {
        val all = (1..250).toList()
        assertEquals((1..10).toList(), AutoLibrary.pageOf(all, 0, 10))
        assertEquals((11..20).toList(), AutoLibrary.pageOf(all, 1, 10))
        assertEquals(100, AutoLibrary.pageOf(all, 0, 0).size)
        assertEquals(emptyList<Int>(), AutoLibrary.pageOf(all, 10, 10))
    }

    @Test fun missingFilesAreRefetchedIntoTheirOwnFolder() {
        assertEquals("stream_files/u1_stream.mp3", AutoLibrary.expectedPath(track(stream = true)))
        assertEquals("music/u1.mp3", AutoLibrary.expectedPath(track(stream = false)))
    }
}
