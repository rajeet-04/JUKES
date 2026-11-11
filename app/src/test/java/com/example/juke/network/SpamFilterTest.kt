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
