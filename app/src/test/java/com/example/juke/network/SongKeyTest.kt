package com.example.juke.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SongKeyTest {
    @Test
    fun sameSongAcrossSpellingsGivesSameKey() {
        assertEquals(
            RecommenderApi.songKey("Tum Hi Ho (From \"Aashiqui 2\")", "Arijit Singh, Mithoon"),
            RecommenderApi.songKey("Tum Hi Ho", "Arijit Singh")
        )
    }

    @Test
    fun differentSongsDiffer() {
        assertNotEquals(
            RecommenderApi.songKey("Tum Hi Ho", "Arijit Singh"),
            RecommenderApi.songKey("Channa Mereya", "Arijit Singh")
        )
    }
}
