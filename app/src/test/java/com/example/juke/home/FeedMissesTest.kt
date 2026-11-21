package com.example.juke.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FeedMissesTest {
    private fun song(id: String) = FeedSong(id, "t$id", "a", "", null)

    @Test fun `a song with no match is dropped from every section`() {
        val sections = listOf(
            HomeSection.QuickPicks("q", "Quick picks", listOf(song("1"), song("2")), null),
            HomeSection.Ranked("r", "Charts", listOf(song("2"), song("3"))),
        )
        val out = withoutMisses(sections, setOf("2"))
        assertEquals(listOf("1"), (out[0] as HomeSection.QuickPicks).songs.map { it.videoId })
        assertEquals(listOf("3"), (out[1] as HomeSection.Ranked).songs.map { it.videoId })
    }

    @Test fun `a section left with no songs goes away`() {
        val out = withoutMisses(listOf(HomeSection.SongRow("s", "Row", listOf(song("9")), null)), setOf("9"))
        assertEquals(emptyList<HomeSection>(), out)
    }

    @Test fun `nothing missed leaves the feed as it was`() {
        val sections = listOf(HomeSection.Ranked("r", "Charts", listOf(song("1"))))
        assertSame(sections, withoutMisses(sections, emptySet()))
    }
}
