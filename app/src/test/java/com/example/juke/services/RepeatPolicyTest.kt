package com.example.juke.services

import com.example.juke.database.SongExposureEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RepeatPolicyTest {
    private val hour = 3_600_000L
    private val t = 1_000_000_000L
    private val session = ListeningSession(start = t, lastActivity = t + hour / 2, songSeq = 100, recSeq = 0)

    private fun played(at: Long, seq: Long) = SongExposureEntity("k", playScore = 0.0, updatedAt = at, lastPlayAt = at, lastPlaySeq = seq)

    @Test fun `gap of an hour starts a new session`() {
        val s = ListeningSession(start = t, lastActivity = t, songSeq = 7, recSeq = 5)
        val fresh = RepeatPolicy.current(s, t + hour)
        assertEquals(t + hour, fresh.start)
        assertEquals(0L, fresh.recSeq)
        assertEquals(7L, fresh.songSeq)
        assertEquals(t, RepeatPolicy.current(s, t + hour - 1).start)
    }

    @Test fun `no session yet starts one`() {
        assertEquals(t, RepeatPolicy.current(null, t).start)
    }

    @Test fun `non favourite heard this session is blocked`() {
        assertTrue(RepeatPolicy.blocked(played(t + 10, 90), favourite = false, s = session, cooldown = 20))
    }

    @Test fun `heard before the session is not blocked`() {
        assertFalse(RepeatPolicy.blocked(played(t - 10, 90), favourite = false, s = session, cooldown = 20))
        assertFalse(RepeatPolicy.blocked(null, favourite = false, s = session, cooldown = 20))
    }

    @Test fun `skipped this session is blocked, favourite too`() {
        val skipped = SongExposureEntity("k", updatedAt = t + 5, lastSkipAt = t + 5)
        assertTrue(RepeatPolicy.blocked(skipped, favourite = false, s = session, cooldown = 20))
        assertTrue(RepeatPolicy.blocked(skipped, favourite = true, s = session, cooldown = 20))
    }

    @Test fun `favourite inside cooldown is blocked, at cooldown it is not`() {
        assertTrue(RepeatPolicy.blocked(played(t + 10, 81), favourite = true, s = session, cooldown = 20))
        assertFalse(RepeatPolicy.blocked(played(t + 10, 80), favourite = true, s = session, cooldown = 20))
    }

    @Test fun `favourite ramp rises from a quarter to full over a second cooldown`() {
        val now = t + hour / 2
        fun w(gap: Long) = RepeatPolicy.weight(played(t + 10, 100 - gap), true, session, 20, now, base = 1.0)
        assertEquals(0.25, w(20) / w(40), 1e-6)
        assertEquals(0.625, w(30) / w(40), 1e-6)
        assertEquals(w(40), w(80), 1e-9)
    }

    @Test fun `favourite outweighs non favourite`() {
        val now = t + hour / 2
        val fav = RepeatPolicy.weight(null, true, session, 20, now, base = 1.0)
        val other = RepeatPolicy.weight(null, false, session, 20, now, base = 1.0)
        assertEquals(2.0, fav / other, 1e-9)
    }

    @Test fun `favourite plays do not wear down its freshness`() {
        val now = t + hour / 2
        val playedLots = SongExposureEntity("k", playScore = 5.0, updatedAt = now)
        assertEquals(
            RepeatPolicy.weight(null, true, session, 20, now, base = 1.0),
            RepeatPolicy.weight(playedLots, true, session, 20, now, base = 1.0),
            1e-9
        )
        assertTrue(RepeatPolicy.weight(playedLots, false, session, 20, now, 1.0) < 0.01)
    }

    @Test fun `never heard`() {
        assertTrue(RepeatPolicy.neverHeard(null))
        assertTrue(RepeatPolicy.neverHeard(SongExposureEntity("k", recScore = 1.0, updatedAt = t)))
        assertFalse(RepeatPolicy.neverHeard(SongExposureEntity("k", playScore = 0.5, updatedAt = t)))
        assertFalse(RepeatPolicy.neverHeard(played(t, 1)))
        assertFalse(RepeatPolicy.neverHeard(null, libraryPlays = 3))
    }

    private data class Item(val name: String, val fresh: Boolean, val weight: Double = 1.0)

    @Test fun `order puts fresh items in every third slot`() {
        val items = (1..6).map { Item("h$it", false) } + (1..3).map { Item("f$it", true) }
        repeat(20) { seed ->
            val out = RepeatPolicy.order(items, 0, { it.weight }, { it.fresh }, { it.name }, Random(seed))
            assertEquals(listOf(2, 5, 8), out.indices.filter { out[it].fresh })
        }
    }

    @Test fun `fresh slots follow the running count`() {
        val items = (1..4).map { Item("h$it", false) } + Item("f", true)
        val out = RepeatPolicy.order(items, 1, { it.weight }, { it.fresh }, { it.name }, Random(1))
        assertTrue(out[1].fresh) // recSeq 1 + index 1 = 2
    }

    @Test fun `order keeps every item and puts zero weight last`() {
        val items = listOf(Item("a", false, 0.0), Item("b", false), Item("c", false))
        val out = RepeatPolicy.order(items, 0, { it.weight }, { it.fresh }, { it.name }, Random(3))
        assertEquals(items.toSet(), out.toSet())
        assertEquals("a", out.last().name)
    }
}
