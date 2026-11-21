package com.example.juke.viewmodels

import com.example.juke.models.Track
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayContextTest {
    private fun track(uuid: String, spotifyId: String? = null) = Track(uuid = uuid, title = "t", artist = "a", durationSec = 1, spotifyId = spotifyId)

    private val ctx = PlayContext(PlayContext.Kind.PLAYLIST, "p1", "Mix", keys = setOf("u1", "s2"))

    @Test fun `owns a song by uuid or spotify id`() {
        assertTrue(ctx.owns(track("u1")))
        assertTrue(ctx.owns(track("other", spotifyId = "s2")))
    }

    @Test fun `does not own a song from elsewhere or nothing`() {
        assertFalse(ctx.owns(track("u9", spotifyId = "s9")))
        assertFalse(ctx.owns(null))
    }

    @Test fun `is checks kind and id`() {
        assertTrue(ctx.isList(PlayContext.Kind.PLAYLIST, "p1"))
        assertFalse(ctx.isList(PlayContext.Kind.ALBUM, "p1"))
        assertFalse(ctx.isList(PlayContext.Kind.PLAYLIST, "p2"))
    }
}
