package com.example.juke.jam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun addManyInsertsARunAfterAnItemInOrder() {
        val q = queue("item-0001", "item-0002").applied(
            JamOp.AddMany(listOf("many-0001" to track(1), "many-0002" to track(2), "item-0002" to track(3)), afterId = "item-0001"), "bob"
        )
        assertEquals(listOf("item-0001", "many-0001", "many-0002", "item-0002"), q.ids())
        assertEquals("bob", q[1].addedBy)
        assertEquals(q.ids(), q.applied(JamOp.AddMany(listOf("many-0001" to track(1)), null), "bob").ids()) // a retry adds nothing
    }

    @Test
    fun addManyStopsAtTheQueueLimit() {
        val full = (0 until MAX_QUEUE - 1).fold(emptyList<JamItem>()) { q, i -> q.applied(JamOp.Add("item-%04d".format(i), track(1)), "a") }
        val q = full.applied(JamOp.AddMany(listOf("many-0001" to track(1), "many-0002" to track(2)), null), "a")
        assertEquals(MAX_QUEUE, q.size)
        assertEquals("many-0001", q.last().id)
    }

    @Test
    fun radioBatchIsCreditedToTheRadio() {
        val q = emptyList<JamItem>().applied(JamOp.AddMany(listOf("radio-0001" to track(1)), null, label = "Radio"), "host")
        assertEquals("Radio", q[0].addedBy)
        val other = emptyList<JamItem>().applied(JamOp.AddMany(listOf("radio-0002" to track(1)), null, label = "Other"), "host")
        assertEquals("host", other[0].addedBy)
    }

    @Test
    fun removeManyDropsTheListedSongs() {
        val q = queue("item-0001", "item-0002", "item-0003").applied(JamOp.RemoveMany(listOf("item-0001", "item-0003")), "a")
        assertEquals(listOf("item-0002"), q.ids())
    }

    @Test
    fun everyOpTheRoomSendsIsUnderstood() {
        // The dispatcher once ignored a new kind of op for want of a branch; this keeps the list and the parser in step.
        val track = """{"spotifyId":"aaaaaaaaaaaaaaaaaaaaaa","title":"t","artist":"a","durationSec":1}"""
        val messages = mapOf(
            "add" to """{"t":"add","id":"item-0001","track":$track,"afterId":null}""",
            "addMany" to """{"t":"addMany","items":[{"id":"item-0001","track":$track}],"afterId":null,"label":"Radio"}""",
            "remove" to """{"t":"remove","id":"item-0001"}""",
            "removeMany" to """{"t":"removeMany","ids":["item-0001","item-0002"]}""",
            "move" to """{"t":"move","id":"item-0001","beforeId":null}"""
        )
        assertEquals(JamSession.OP_TYPES, messages.keys)
        messages.forEach { (type, text) ->
            val op = JamSession.parseOp(kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject)
            assertNotNull("$type was not understood", op)
        }
    }
}
