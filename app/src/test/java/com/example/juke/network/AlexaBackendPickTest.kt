package com.example.juke.network

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlexaBackendPickTest {

    private fun hit(id: String, title: String, artist: String, durationMs: Int = 0): JsonObject =
        buildJsonObject {
            put("video_id", id)
            put("title", title)
            put("artist", artist)
            put("duration_ms", durationMs)
        }

    // Real /alexa/search/ results for "Wishes Hasan Raheem, Umair, Talwiinder" (order kept).
    private val wishesSearch = listOf(
        hit("xLYQAbVKthM", "You", "THEMXXNLIGHT & Talwiinder"),
        hit("YVf3dNBuWEQ", "Wishes", "Millind Gaba and Star Boy Loc and Asli Gold"),
        hit("7PvZj0PWgaw", "WISHES", "FULLHOUSE"),
        hit("UG6ziVK5ykY", "Departure Lane", "Talha Anjum and Umair"),
        hit("palpal00000", "Pal Pal", "Afusic and Talwiinder"),
        hit("DvL-Tlnz7aY", "Wishes", "Nightswallow"),
        hit("Gz38Yj09k3A", "Hasan Raheem - Wishes ft Talwiinder | ", "Hasan Raheem and Abdullah Kasumbi", 219_000),
    )

    @Test
    fun picksTheRealSongNotTheFirstPopularHit() {
        val pick = AlexaBackendApi.pickSong(wishesSearch, "Wishes", "Hasan Raheem, Umair, Talwiinder", 219)
        assertEquals("Gz38Yj09k3A", pick!!.string())
    }

    @Test
    fun sameTitleByOtherArtistsIsRejected() {
        val others = wishesSearch.filter { it.string() != "Gz38Yj09k3A" }
        assertNull(AlexaBackendApi.pickSong(others, "Wishes", "Hasan Raheem, Umair, Talwiinder", 219))
    }

    @Test
    fun featuredArtistsOtherSongIsNotAMatch() {
        assertFalse(AlexaBackendApi.isSameSong(hit("p", "Pal Pal", "Afusic and Talwiinder"), "Wishes", "Hasan Raheem, Umair, Talwiinder"))
    }

    @Test
    fun featAndBracketsAreIgnored() {
        assertTrue(AlexaBackendApi.isSameSong(
            hit("k", "Kesariya (From \"Brahmastra\")", "Arijit Singh"), "Kesariya", "Arijit Singh, Pritam"))
        assertTrue(AlexaBackendApi.isSameSong(
            hit("o", "OBVIOUS", "Umair"), "OBVIOUS (feat. Hasan Raheem)", "Umair, Hasan Raheem"))
    }

    @Test
    fun clearlyDifferentLengthIsRejectedButUnknownLengthIsAllowed() {
        val long = hit("a", "Wishes", "Hasan Raheem", 400_000)
        val unknown = hit("b", "Wishes", "Hasan Raheem", 0)
        assertEquals("b", AlexaBackendApi.pickSong(listOf(long, unknown), "Wishes", "Hasan Raheem", 219)!!.string())
    }

    @Test
    fun remixIsSkippedUnlessAskedFor() {
        val remix = hit("r", "Wishes (Slowed + Reverb)", "Hasan Raheem")
        val plain = hit("p", "Wishes", "Hasan Raheem")
        assertEquals("p", AlexaBackendApi.pickSong(listOf(remix, plain), "Wishes", "Hasan Raheem", null)!!.string())
        assertEquals("r", AlexaBackendApi.pickSong(listOf(remix), "Wishes (Slowed + Reverb)", "Hasan Raheem", null)!!.string())
    }

    @Test
    fun spotifyVersionSuffixIsIgnored() {
        assertTrue(AlexaBackendApi.isSameSong(
            hit("h", "Here Comes The Sun", "The Beatles"), "Here Comes The Sun - Remastered 2009", "The Beatles"))
    }

    private fun JsonObject.string() = (this["video_id"] as kotlinx.serialization.json.JsonPrimitive).content
}
