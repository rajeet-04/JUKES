package com.example.juke.jam

import org.junit.Assert.assertEquals
import org.junit.Test

class JamWarmupTest {
    private fun item(n: Int) = JamItem("item-000$n", "a".repeat(22), "Song $n", "Artist", 100)
    private val queue = (1..7).map { item(it) }

    @Test
    fun warmsTheFourSongsAfterTheCurrentOne() {
        assertEquals(listOf("item-0003", "item-0004", "item-0005", "item-0006"), JamWarmup.nextUp(queue, "item-0002").map { it.id })
    }

    @Test
    fun warmsWhateverIsLeftNearTheEnd() {
        assertEquals(listOf("item-0007"), JamWarmup.nextUp(queue, "item-0006").map { it.id })
        assertEquals(emptyList<String>(), JamWarmup.nextUp(queue, "item-0007").map { it.id })
    }

    @Test
    fun startsAtTheTopWhenNothingIsPlayingYet() {
        assertEquals(listOf("item-0001", "item-0002", "item-0003", "item-0004"), JamWarmup.nextUp(queue, null).map { it.id })
        assertEquals(listOf("item-0001", "item-0002", "item-0003", "item-0004"), JamWarmup.nextUp(queue, "gone-item").map { it.id })
    }
}
