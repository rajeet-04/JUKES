package com.example.juke.services

import com.example.juke.database.SongExposureEntity
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VarietyTest {
    private val day = 24 * 60 * 60 * 1000L
    private val t0 = 1_000_000_000_000L

    @Test
    fun unseenSongIsFullyFresh() {
        assertEquals(1.0, Variety.freshness(null, t0), 1e-9)
    }

    @Test
    fun playDecaysByHalfEveryThreeDays() {
        val e = Variety.record(null, "k", Variety.Event.PLAY, t0)
        assertEquals(1.0, e.playScore, 1e-9)
        assertEquals(0.5, Variety.decayed(e, t0 + 3 * day).playScore, 1e-9)
        assertEquals(0.25, Variety.decayed(e, t0 + 6 * day).playScore, 1e-9)
    }

    @Test
    fun eventsAccumulateAfterDecay() {
        var e = Variety.record(null, "k", Variety.Event.PLAY, t0)
        e = Variety.record(e, "k", Variety.Event.PLAY, t0 + 3 * day)
        assertEquals(1.5, e.playScore, 1e-9)
    }

    @Test
    fun skipsPenaliseMoreAndLongerThanPlays() {
        val played = Variety.record(null, "k", Variety.Event.PLAY, t0)
        val skipped = Variety.record(null, "k", Variety.Event.SKIP, t0)
        val later = t0 + 7 * day
        assertTrue(Variety.freshness(skipped, later) < Variety.freshness(played, later))
    }

    @Test
    fun heavilyHeardSongIsRarelyFresh() {
        var e: SongExposureEntity? = null
        e = Variety.record(e, "k", Variety.Event.PLAY, t0)
        e = Variety.record(e, "k", Variety.Event.PLAY, t0 + day)
        e = Variety.record(e, "k", Variety.Event.SKIP, t0 + 2 * day)
        assertTrue(Variety.freshness(e, t0 + 2 * day) < 0.1)
        // ...and recovers after a few weeks
        assertTrue(Variety.freshness(e, t0 + 30 * day) > 0.8)
    }

    @Test
    fun relevanceFallsWithRank() {
        assertEquals(1.0, Variety.relevance(0), 1e-9)
        assertEquals(0.5, Variety.relevance(10), 1e-9)
        assertTrue(Variety.relevance(40) < Variety.relevance(5))
    }

    @Test
    fun pickReturnsDistinctItemsAndSkipsZeroWeights() {
        val items = (0 until 20).toList()
        val picked = Variety.pick(items, 10, { if (it == 3) 0.0 else 1.0 }, { "a$it" }, random = Random(1))
        assertEquals(10, picked.size)
        assertEquals(10, picked.toSet().size)
        assertTrue(3 !in picked)
    }

    @Test
    fun pickPrefersHeavierWeights() {
        // Item 0 weighs 50x the others; over many draws it should almost always be picked.
        val items = (0 until 30).toList()
        val r = Random(7)
        val hits = (0 until 200).count { 0 in Variety.pick(items, 3, { if (it == 0) 50.0 else 1.0 }, { "a$it" }, random = r) }
        assertTrue(hits > 180)
    }

    @Test
    fun sameSeedPoolGivesDifferentPicksAcrossSessions() {
        // Same radio, same weights: different sessions (random draws) should not repeat the same set.
        val items = (0 until 45).toList()
        val sets = (0 until 10).map { s ->
            Variety.pick(items, 5, { Variety.relevance(it) }, { "a$it" }, random = Random(s)).toSet()
        }.toSet()
        assertTrue(sets.size >= 8)
    }

    @Test
    fun sameArtistIsSpreadOut() {
        // Half the pool is one artist; with spreading, a 6-pick batch should not be dominated by it.
        val items = (0 until 40).toList()
        val artist = { i: Int -> if (i % 2 == 0) "same" else "a$i" }
        val r = Random(3)
        val avgSame = (0 until 200).sumOf { _ ->
            Variety.pick(items, 6, { 1.0 }, artist, random = r).count { artist(it) == "same" }
        } / 200.0
        assertTrue("avg same-artist picks $avgSame", avgSame < 2.0)
    }

    @Test
    fun recentArtistsStartPenalised() {
        val items = listOf("x1", "y1")
        val r = Random(11)
        val xFirst = (0 until 400).count {
            Variety.pick(items, 1, { 1.0 }, { it.take(1) }, recentArtists = listOf("x"), random = r).first() == "x1"
        }
        assertNotEquals(200, xFirst)
        assertTrue(xFirst < 140) // ~0.35/1.35 ≈ 26%
    }
}
