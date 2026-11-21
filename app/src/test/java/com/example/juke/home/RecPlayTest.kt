package com.example.juke.home

import com.example.juke.network.RecommenderApi.YouTubeRecommendation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecPlayTest {
    private val song = FeedSong("abcdefghijk", "Kesariya", "Arijit Singh", FeedSong.art("abcdefghijk"), 268)

    @Test fun `resolveFeedSong passes title artist and id to match`() = runTest {
        var asked: YouTubeRecommendation? = null
        val out = resolveFeedSong(song) { asked = it; "track-1" }
        assertEquals("track-1", out)
        assertEquals("abcdefghijk", asked!!.id)
        assertEquals("Kesariya", asked!!.title)
        assertEquals("Arijit Singh", asked!!.artist)
        assertEquals("4:28", asked!!.duration)
    }

    @Test fun `playRec reports failure when match is null`() = runTest {
        assertNull(resolveFeedSong<String>(song) { null })
    }

    @Test fun `a match that throws counts as not found`() = runTest {
        assertNull(resolveFeedSong<String>(song) { error("network") })
    }
}
