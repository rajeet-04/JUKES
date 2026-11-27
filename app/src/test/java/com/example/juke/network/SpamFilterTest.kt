package com.example.juke.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamFilterTest {
    @Test
    fun variantsAreSpam() {
        assertTrue(RecommenderApi.isSpamTitle("Kesariya (Slowed + Reverb)"))
        assertTrue(RecommenderApi.isSpamTitle("Song Name | Nightcore"))
    }

    @Test
    fun plainSongIsNotSpam() {
        assertFalse(RecommenderApi.isSpamTitle("Kesariya"))
    }
}

class RenditionFilterTest {
    private val query = "Haule Haule Salim–Sulaiman, Sukhwinder Singh, Jaideep Sahni"

    @Test
    fun instrumentVersionsAreNotTheSong() {
        // Spotsaver served this upload: 1 s off and it names two of the three artists.
        assertTrue(RecommenderApi.isUnrequestedRendition("Flute Version: Haule Haule | Rab Ne Bana Di Jodi | Salim-Sulaiman | Jaideep Sahni", query))
        assertTrue(RecommenderApi.isUnrequestedRendition("Haule Haule - Piano Cover", query))
        assertTrue(RecommenderApi.isUnrequestedRendition("Haule Haule | LoFi by Jus Keys", query))
    }

    @Test
    fun theSongAndRequestedRenditionsPass() {
        assertFalse(RecommenderApi.isUnrequestedRendition("Haule Haule, Rab Ne Bana Di Jodi, Sukhwinder Singh", query))
        assertFalse(RecommenderApi.isUnrequestedRendition("Haule Haule - Flute Version", "Haule Haule - Flute Version Vijay Tambe"))
    }
}
