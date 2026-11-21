package com.example.juke.home

import com.example.juke.models.Track
import com.example.juke.network.RecommenderApi.YouTubeRecommendation
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class FeedPagerTest {
    private fun seed(i: Int) = Track(uuid = "u$i", title = "Seed $i", artist = "Artist $i", durationSec = 200, ytVideoId = "v$i")
    private fun recs(videoId: String, n: Int = 20) =
        (0 until n).map { YouTubeRecommendation(id = "$videoId-$it", title = "Song $it of $videoId", artist = "By $videoId", duration = "3:20") }

    private val calls = AtomicInteger()
    private fun pager(radio: suspend (String) -> List<YouTubeRecommendation> = { calls.incrementAndGet(); recs(it) }) =
        FeedPager(radio = radio, isSpam = { false })

    @Test fun `first page starts with quick picks of 20`() = runTest {
        val p = pager(); p.reset((1..5).map(::seed), emptySet())
        val page = p.nextPage()
        assertEquals(3, page.sections.size)
        val qp = page.sections.first() as HomeSection.QuickPicks
        assertEquals("Quick picks", qp.title)
        assertEquals(20, qp.songs.size)
        assertTrue(page.sections[1] is HomeSection.SongRow)
        assertEquals("Because you played Seed 2", page.sections[1].title)
    }

    @Test fun `no song repeats across pages or from known keys`() = runTest {
        // Every seed's radio overlaps: same 30 songs, so dedupe has real work.
        val shared = (0 until 30).map { YouTubeRecommendation("s$it", "Shared $it", "Band", "3:00") }
        val known = setOf(FeedSong.from(shared[0]).key)
        val p = pager { shared + recs(it, 12) }
        p.reset((1..9).map(::seed), known)
        val songs = (1..3).flatMap { p.nextPage().sections }.flatMap { s ->
            when (s) {
                is HomeSection.QuickPicks -> s.songs
                is HomeSection.SongRow -> s.songs
                is HomeSection.HeroMix -> s.songs
                else -> emptyList()
            }
        }
        assertEquals(songs.size, songs.map { it.key }.toSet().size)
        assertFalse(songs.any { it.key in known })
    }

    @Test fun `sections under 4 songs are skipped`() = runTest {
        val p = pager { v -> if (v == "v2") recs(v, 3) else recs(v) }
        p.reset((1..5).map(::seed), emptySet())
        val titles = p.nextPage().sections.map { it.title }
        assertFalse(titles.contains("Because you played Seed 2"))
        assertEquals(3, titles.size)
    }

    @Test fun `fourth section is a hero mix`() = runTest {
        val p = pager(); p.reset((1..8).map(::seed), emptySet())
        p.nextPage()
        val hero = p.nextPage().sections.first() as HomeSection.HeroMix
        assertEquals("Mixed for you", hero.title)
        assertEquals("Based on Artist 4", hero.subtitle)
    }

    @Test fun `derived seeds keep the feed going after explicit seeds run out`() = runTest {
        val p = pager(); p.reset((1..2).map(::seed), emptySet())
        repeat(2) { p.nextPage() }
        val page3 = p.nextPage()
        assertTrue(page3.sections.isNotEmpty())
        assertTrue(page3.sections.any { it.title == "Fresh finds" })
        assertFalse(p.exhausted)
    }

    @Test fun `empty seeds yield no radio pages`() = runTest {
        val p = pager(); p.reset(emptyList(), emptySet())
        assertTrue(p.nextPage().sections.isEmpty())
        assertTrue(p.exhausted)
        assertEquals(0, calls.get())
    }

    @Test fun `fetch failure ends page without losing earlier sections`() = runTest {
        var offline = false
        val p = pager { v -> if (offline) error("offline") else recs(v) }
        p.reset((1..9).map(::seed), emptySet())
        val first = p.nextPage().sections
        offline = true
        val failed = p.nextPage()
        assertTrue(failed.failed)
        assertTrue(failed.sections.isEmpty())
        offline = false
        val retry = p.nextPage()
        assertEquals("Mixed for you", retry.sections.first().title) // same seeds retried: section 4 is next
        assertEquals("Based on Artist 4", (retry.sections.first() as HomeSection.HeroMix).subtitle)
        assertEquals(3, first.size)
    }

    @Test fun `concurrent loadMore runs one page`() = runTest {
        val p = pager { v -> calls.incrementAndGet(); delay(100); recs(v) }
        p.reset((1..9).map(::seed), emptySet())
        val pages = listOf(async { p.nextPage() }, async { p.nextPage() }).awaitAll()
        assertEquals(6, calls.get()) // two pages, run one after the other: 3 + 3 fetches
        assertEquals(6, pages.flatMap { it.sections }.map { it.id }.toSet().size)
    }

    @Test fun `reset clears seen keys and seeds`() = runTest {
        val p = pager(); p.reset((1..3).map(::seed), emptySet())
        val a = p.nextPage().sections
        p.reset((1..3).map(::seed), emptySet())
        val b = p.nextPage().sections
        assertEquals(a.map { it.title }, b.map { it.title })
        assertEquals((a.first() as HomeSection.QuickPicks).songs, (b.first() as HomeSection.QuickPicks).songs)
    }
}
