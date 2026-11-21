package com.example.juke.services

import com.example.juke.network.RecommenderApi
import com.example.juke.network.RecommenderApi.YouTubeRecommendation
import com.example.juke.network.SongIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class MixPicksTest {
    private fun rec(id: String, title: String, artist: String = "Band") = YouTubeRecommendation(id, title, artist, "3:30")

    private fun picks(
        radio: List<YouTubeRecommendation>,
        blocked: SongIndex = SongIndex(),
        skipIds: Set<String> = emptySet(),
        limit: Int = 25,
    ) = mixPicks(radio, blocked, skipIds, limit, isSpam = { "spam" in it }, isBlocked = { it.artist == "Banned" })

    @Test fun `songs already queued or waiting are left out`() {
        val blocked = SongIndex().apply { add(RecommenderApi.songKey("Queued", "Band"), 210) }
        val out = picks(listOf(rec("a", "Queued"), rec("b", "Fresh")), blocked)
        assertEquals(listOf("b"), out.map { it.id })
    }

    @Test fun `the playing song, its seed, spam, blacklisted artists and repeats are left out`() {
        val radio = listOf(
            rec("now", "Playing"), rec("seed", "Seed upload"), rec("c", "Some spam remix"),
            rec("d", "Hit", "Banned"), rec("e", "Keep"), rec("f", "Keep"),
        )
        assertEquals(listOf("e"), picks(radio, skipIds = setOf("now", "seed")).map { it.id })
    }

    @Test fun `stops at the limit, in radio order`() {
        val radio = (1..40).map { rec("v$it", "Song $it") }
        assertEquals((1..25).map { "v$it" }, picks(radio).map { it.id })
    }
}
