package com.example.juke.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedMatchTest {
    private data class Song(val title: String, val artist: String, val sec: Int? = null)

    private fun pick(feed: FeedSong, songs: List<Song>) =
        FeedMatch.pick(feed, songs, { it.title }, { it.artist }, { it.sec })

    private fun feed(title: String, artist: String, sec: Int? = null) = FeedSong("v", title, artist, "", sec)

    @Test fun `same song despite YouTube title extras`() {
        assertTrue(FeedMatch.sameSong("O Soneya (From \"Ishq Vishk Rebound\")", "Arijit Singh", "O Soneya", "Arijit Singh, Shilpa Rao", null, null))
        assertTrue(FeedMatch.sameSong("Kesariya (Official Video)", "Pritam & Arijit Singh", "Kesariya", "Arijit Singh", null, null))
    }

    @Test fun `a shared artist anywhere in the credits counts`() {
        assertTrue(FeedMatch.sameSong("Tum Se", "Sachin-Jigar, Raghav Chaitanya", "Tum Se", "Raghav Chaitanya", null, null))
    }

    @Test fun `matching length stands in for a differently spelled artist`() {
        assertTrue(FeedMatch.sameSong("Jhol", "Maanu x Annural Khalid", "Jhol", "Maanu, Annural Khalid", 220, 222))
        assertTrue(FeedMatch.sameSong("Jhol", "Annural", "Jhol", "Maanu", 220, 222))
        assertFalse(FeedMatch.sameSong("Jhol", "Annural", "Jhol", "Maanu", 220, 260))
    }

    @Test fun `a video's intro does not hide the same song by the same artist`() {
        assertTrue(FeedMatch.sameSong("Kho Sa Gaya Hoon", "OutStation", "Kho Sa Gaya Hoon", "OutStation", 148, 127))
    }

    @Test fun `different title or version is a different song`() {
        assertFalse(FeedMatch.sameSong("O Soneya", "Arijit Singh", "Soneya", "Arijit Singh", null, null))
        assertFalse(FeedMatch.sameSong("O Soneya (Slowed + Reverb)", "Arijit Singh", "O Soneya", "Arijit Singh", null, null))
        assertFalse(FeedMatch.sameSong("O Soneya", "Arijit Singh", "O Soneya", "Someone Else", null, null))
    }

    @Test fun `pick takes the first same song`() {
        val songs = listOf(Song("O Soneya - Remix", "Arijit Singh"), Song("O Soneya", "Arijit Singh, Shilpa Rao"), Song("O Soneya", "Arijit Singh"))
        assertEquals(songs[1], pick(feed("O Soneya (From \"Ishq Vishk Rebound\")", "Arijit Singh"), songs))
        assertNull(pick(feed("Kesariya", "Arijit Singh"), songs))
    }
}
