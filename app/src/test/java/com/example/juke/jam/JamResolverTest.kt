package com.example.juke.jam

import com.example.juke.jam.JamResolver.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JamResolverTest {
    private fun pick(title: String, artist: String, sec: Int = 200) = JamPick(title, artist, sec, null, null)
    private fun cand(id: String, title: String, artists: List<String>, sec: Int = 200) = Candidate(id, title, artists, sec, null)

    @Test
    fun normalizeDropsNoise() {
        assertEquals("song", JamResolver.normalize("Song (Remastered 2011)"))
        assertEquals("song", JamResolver.normalize("Song - From The Movie"))
        assertEquals("song", JamResolver.normalize("Song feat. Someone"))
        assertEquals("tum hi ho", JamResolver.normalize("Tum Hi Ho!"))
    }

    @Test
    fun picksTheMatchWithTheClosestLength() {
        val best = JamResolver.bestMatch(
            listOf(cand("a", "Song", listOf("Band"), 230), cand("b", "Song", listOf("Band"), 201), cand("c", "Other", listOf("Band"), 200)),
            pick("Song", "Band")
        )
        assertEquals("b", best?.id)
    }

    @Test
    fun rejectsWrongArtistWrongSongOrWrongLength() {
        assertNull(JamResolver.bestMatch(listOf(cand("a", "Song", listOf("Someone Else"))), pick("Song", "Band")))
        assertNull(JamResolver.bestMatch(listOf(cand("a", "Songs of Love", listOf("Band"))), pick("Song", "Band")))
        assertNull(JamResolver.bestMatch(listOf(cand("a", "Song", listOf("Band"), 330)), pick("Song", "Band", 200))) // a 5 minute remix
    }

    @Test
    fun acceptsSeveralArtistsAndRemasterSuffixes() {
        val best = JamResolver.bestMatch(
            listOf(cand("a", "Song - Remastered", listOf("Band", "Guest"))),
            pick("Song", "Guest & Band")
        )
        assertEquals("a", best?.id)
    }

    @Test
    fun unknownLengthDoesNotBlockAMatch() {
        assertEquals("a", JamResolver.bestMatch(listOf(cand("a", "Song", listOf("Band"), 500)), pick("Song", "Band", 0))?.id)
    }
}
