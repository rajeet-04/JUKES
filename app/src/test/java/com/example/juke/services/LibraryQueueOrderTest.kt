package com.example.juke.services

import com.example.juke.models.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryQueueOrderTest {
    private fun tracks(vararg ids: String) = ids.map { Track(it, it, "Artist", durationSec = 180) }

    @Test fun `play next moves songs before current without shifting playback to the wrong song`() {
        val result = LibraryQueueOrder.arrange(tracks("a", "b", "current", "c"), "current", tracks("a", "b"), true)
        assertEquals(listOf("current", "a", "b", "c"), result.map { it.uuid })
    }

    @Test fun `play next keeps selected order and deduplicates current and incoming songs`() {
        val result = LibraryQueueOrder.arrange(tracks("past", "current", "a", "b"), "current",
            tracks("b", "current", "a", "b", "new"), true)
        assertEquals(listOf("past", "current", "b", "a", "new"), result.map { it.uuid })
    }

    @Test fun `queue end moves existing songs and keeps untouched order`() {
        val result = LibraryQueueOrder.arrange(tracks("a", "current", "b", "c"), "current",
            tracks("b", "a", "new", "current"), false)
        assertEquals(listOf("current", "c", "b", "a", "new"), result.map { it.uuid })
    }

    @Test fun `empty queue retains unique incoming order`() {
        assertEquals(listOf("a", "b"), LibraryQueueOrder.arrange(emptyList(), null, tracks("a", "a", "b"), true).map { it.uuid })
    }
}
