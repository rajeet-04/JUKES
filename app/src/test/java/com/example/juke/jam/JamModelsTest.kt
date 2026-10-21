package com.example.juke.jam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class JamModelsTest {
    private fun track(n: Int) = JamTrack("a".repeat(21) + n, "Song $n", "Artist", 100)
    private fun List<JamItem>.ids() = map { it.id }

    private fun queue(vararg ids: String): List<JamItem> =
        ids.fold(emptyList()) { q, id -> q.applied(JamOp.Add(id, track(1)), "ann") }

    @Test
    fun addAppendsOrInsertsAfter() {
        val q = queue("item-0001", "item-0002").applied(JamOp.Add("item-0003", track(3), afterId = "item-0001"), "bob")
        assertEquals(listOf("item-0001", "item-0003", "item-0002"), q.ids())
        assertEquals("bob", q[1].addedBy)
    }

    @Test
    fun repeatedAddIsIgnored() {
        val q = queue("item-0001")
        assertSame(q, q.applied(JamOp.Add("item-0001", track(1)), "ann"))
    }

    @Test
    fun removeAndMove() {
        var q = queue("item-0001", "item-0002", "item-0003")
        q = q.applied(JamOp.Move("item-0003", beforeId = "item-0001"), "x")
        assertEquals(listOf("item-0003", "item-0001", "item-0002"), q.ids())
        q = q.applied(JamOp.Move("item-0003"), "x")
        assertEquals(listOf("item-0001", "item-0002", "item-0003"), q.ids())
        q = q.applied(JamOp.Remove("item-0002"), "x")
        assertEquals(listOf("item-0001", "item-0003"), q.ids())
        assertEquals(q.ids(), q.applied(JamOp.Remove("missing-id"), "x").ids())
    }

    @Test
    fun parsesJamLinks() {
        assertEquals("9R344N", JamSession.parseCode("https://j.rajeet.in/j/9R344N"))
        assertEquals("9R344N", JamSession.parseCode("join me juke://j/9r344n"))
        assertNull(JamSession.parseCode("https://j.rajeet.in/p/aB3dE5gH7j"))
        assertNull(JamSession.parseCode("https://j.rajeet.in/j/ABCDE"))
    }
}
