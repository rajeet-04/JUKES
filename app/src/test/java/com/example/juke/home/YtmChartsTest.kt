package com.example.juke.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YtmChartsTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun `charts page names the daily top videos playlist`() {
        assertEquals("VLPL4fGSI1pDJn5oibdgJt8Hy0-dr2B7kSs2", YtmCharts.parseChartPlaylistId(fixture("ytm_charts.json")))
    }

    @Test fun `playlist parse returns ranked songs with video ids`() {
        val songs = YtmCharts.parsePlaylist(fixture("ytm_trending_playlist.json"))
        assertEquals(20, songs.size)
        assertTrue(songs.all { it.videoId.length == 11 && it.title.isNotBlank() && it.artist.isNotBlank() })
        assertEquals("Yl-nQ_V-bAA", songs[0].videoId)
        assertEquals("Kalaippuli S Thanu", songs[0].artist)
        assertEquals(101, songs[0].durationSec)
    }

    @Test fun `video titles are trimmed to the song name`() {
        assertEquals("Vetri Maaran’s Arasan 'FIRE' Glimpse", YtmCharts.displayTitle("Vetri Maaran’s Arasan 'FIRE' Glimpse | Silambarasan TR | Anirudh"))
        assertEquals("Rathinamo", YtmCharts.displayTitle("Rathinamo - Official Music Video | Saurav Srisan"))
        assertEquals("Saiyaara", YtmCharts.displayTitle("Saiyaara (Official Video)"))
        assertEquals("Plain", YtmCharts.displayTitle("Plain"))
    }
}
