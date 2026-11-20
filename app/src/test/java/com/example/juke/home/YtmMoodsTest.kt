package com.example.juke.home

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtmMoodsTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun `moods come from the moods and moments grid only`() {
        val moods = YtmMoods.parseMoods(fixture("ytm_moods.json"))
        assertEquals(listOf("Chill", "Commute", "Energize", "Feel good"), moods.take(4).map { it.title })
        assertEquals("ggMPOg1uX1JOQWZFeDByc2Jm", moods.first().params)
        assertFalse(moods.any { it.title == "Hindi" }) // genres grid is not a mood
    }

    @Test fun `a mood page splits into named shelves of playlists`() {
        val shelves = YtmMoods.parseCategory(fixture("ytm_mood_chill.json"))
        assertEquals("Coffee shop blends", shelves.first().title)
        assertEquals("RDCLAK5uy_nBE4bLuBHUXWZrF59ZrkPEToKt8M_I3Vc", shelves.first().playlists.first()) // VL prefix stripped for the playlist, re-added on browse
        assertTrue(shelves.size >= 10)
    }
}

class MoodPagerTest {
    private fun songs(id: String, n: Int = 20) = (0 until n).map { FeedSong("$id-$it", "Song $it $id", "Band $id", FeedSong.art("$id-$it"), 200) }
    private val shelves = (1..8).map { YtmMoods.Shelf("Shelf $it", listOf("p$it", "q$it")) }

    @Test fun `first mood page opens with quick picks named after the shelf`() = runTest {
        val p = MoodPager({ songs(it) }); p.reset(shelves)
        val page = p.nextPage()
        assertEquals(3, page.sections.size)
        val qp = page.sections.first() as HomeSection.QuickPicks
        assertEquals("Shelf 1", qp.title)
        assertEquals(20, qp.songs.size)
        assertTrue(page.sections[2] is HomeSection.Ranked)
    }

    @Test fun `songs never repeat across mood shelves`() = runTest {
        val shared = songs("x", 30)
        val p = MoodPager({ shared + songs(it, 10) }); p.reset(shelves)
        val all = (1..2).flatMap { p.nextPage().sections }.flatMap {
            when (it) { is HomeSection.QuickPicks -> it.songs; is HomeSection.SongRow -> it.songs; is HomeSection.Ranked -> it.songs; else -> emptyList() }
        }
        assertEquals(all.size, all.map { it.key }.toSet().size)
    }

    @Test fun `a thin playlist falls back to the shelf's next playlist`() = runTest {
        val p = MoodPager({ id -> if (id == "p1") songs(id, 2) else songs(id) }); p.reset(shelves)
        val qp = p.nextPage().sections.first() as HomeSection.QuickPicks
        assertEquals("Shelf 1", qp.title)
        assertTrue(qp.songs.all { it.videoId.startsWith("q1") })
    }

    @Test fun `offline page fails and retries the same shelves`() = runTest {
        var offline = true
        val p = MoodPager({ if (offline) error("offline") else songs(it) }); p.reset(shelves)
        assertTrue(p.nextPage().failed)
        offline = false
        assertEquals("Shelf 1", p.nextPage().sections.first().title)
    }

    @Test fun `mood ends when shelves run out`() = runTest {
        val p = MoodPager({ songs(it) }); p.reset(shelves.take(3))
        p.nextPage()
        assertTrue(p.exhausted)
        assertTrue(p.nextPage().sections.isEmpty())
    }
}
