package com.example.juke.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongTitleTest {
    private fun key(title: String, artist: String = "Afusic") = SongTitle.key(title, artist)

    @Test
    fun uploadLabelsAreNoise() {
        val plain = key("Pal Pal")
        listOf(
            "Pal Pal (Official Video)", "Pal Pal [Official Music Video]", "Pal Pal (Lyrics)",
            "Pal Pal (Lyrical Video)", "Pal Pal [HD]", "Pal Pal (4K)", "Pal Pal (Audio Visualizer)",
            "Pal Pal (2024)", "Pal Pal (prod. by Umair)", "Pal Pal - Remastered 2011",
            "Pal Pal (Radio Edit)", "Pal Pal (Explicit)",
        ).forEach { assertEquals(it, plain, key(it)) }
        assertEquals(key("Tum Hi Ho", "Arijit Singh"), key("Tum Hi Ho (From \"Aashiqui 2\")", "Arijit Singh"))
    }

    @Test
    fun versionsAreDifferentSongs() {
        val plain = key("Pal Pal")
        listOf(
            "Pal Pal (Encore ABJ Remix)", "Pal Pal (Slowed + Reverb)", "Pal Pal (Live)",
            "Pal Pal (Acoustic)", "Pal Pal (Lofi Mix)", "Pal Pal (Sped Up)", "Pal Pal (Female Version)",
            "Pal Pal (Unplugged)", "Pal Pal (DJ Mixed)",
        ).forEach { assertNotEquals(it, plain, key(it)) }
        assertNotEquals(key("Pal Pal (Encore ABJ Remix)"), key("Pal Pal (Live)"))
    }

    @Test
    fun spotifyDashSuffixMatchesYoutubeBrackets() {
        assertEquals(
            key("Chalre Chalre Waal - Female Version", "Mandeep Bhullar"),
            key("Chalre Chalre Waal (Female Version)", "Mandeep Bhullar")
        )
        assertNotEquals(
            key("Chalre Chalre Waal - Female Version", "Mandeep Bhullar"),
            key("Chalre Chalre Waal", "Mandeep Bhullar")
        )
        // An "Artist - Song" upload keeps its dash text.
        assertNotEquals(key("Seedhe Maut - Madira", "Seedhe Maut"), key("Madira", "Seedhe Maut"))
    }

    @Test
    fun creditsAreReadButNotPartOfTheKey() {
        assertEquals(key("Pal Pal"), key("Pal Pal (with Talwiinder)"))
        assertEquals(key("Ek Roz", "Joshi G"), key("Ek Roz (feat. Keman)", "Joshi G"))
        assertEquals(listOf("talwiinder"), SongTitle.parse("Pal Pal (with Talwiinder)").guests)
        assertEquals(listOf("a", "b"), SongTitle.parse("Song (feat. A & B)").guests)
        assertEquals(listOf("keman"), SongTitle.parse("Song feat. Keman").guests)
    }

    @Test
    fun firstArtistIgnoresHowTheListIsJoined() {
        assertEquals("afusic", SongTitle.primaryArtist("Afusic&AliSoomroMusic"))
        assertEquals("afusic", SongTitle.primaryArtist("Afusic, AliSoomroMusic"))
        assertEquals("afusic", SongTitle.primaryArtist("Afusic x Talwiinder"))
        assertEquals("seedhemaut", SongTitle.primaryArtist("Seedhe Maut feat. KR\$NA"))
    }

    @Test
    fun lengthSeparatesRecordingsThatShareAKey() {
        val index = SongIndex()
        val k = key("Pal Pal")
        assertTrue(index.add(k, 148))
        assertTrue("a respelling of the same recording", index.contains(key("Pal Pal (Official Audio)"), 150))
        assertFalse("the longer 'with Talwiinder' recording is another song", index.contains(key("Pal Pal (with Talwiinder)"), 215))
        assertTrue(index.add(key("Pal Pal (with Talwiinder)"), 215))
        assertFalse(index.add(k, 151))
        assertTrue("unknown length matches", index.contains(k, null))
    }

    @Test
    fun versionDifferenceForSpotifyMatching() {
        assertTrue(SongTitle.differentVersion("Pal Pal", "Pal Pal - Remix"))
        assertFalse(SongTitle.differentVersion("Pal Pal (Official Video)", "Pal Pal"))
    }

    @Test
    fun clockParsing() {
        assertEquals(185, SongTitle.clockSeconds("3:05"))
        assertEquals(3723, SongTitle.clockSeconds("1:02:03"))
        assertEquals(null, SongTitle.clockSeconds(null))
        assertEquals(null, SongTitle.clockSeconds("live"))
    }
}
