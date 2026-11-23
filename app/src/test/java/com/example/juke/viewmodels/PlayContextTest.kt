package com.example.juke.viewmodels

import com.example.juke.models.Track
import org.junit.Assert.assertEquals
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

    @Test fun `source is the kind of list the song was started from`() {
        fun src(kind: PlayContext.Kind) =
            PlayContext.sourceOf(PlayContext(kind, "i", "n", setOf("u1")), track("u1"), radio = false, jam = false)
        assertEquals("playlist", src(PlayContext.Kind.PLAYLIST))
        assertEquals("playlist", src(PlayContext.Kind.LIBRARY_PLAYLIST))
        assertEquals("album", src(PlayContext.Kind.ALBUM))
        assertEquals("artist", src(PlayContext.Kind.ARTIST))
        assertEquals("library", src(PlayContext.Kind.LIBRARY))
    }

    @Test fun `source falls back to direct, and jam and radio win`() {
        assertEquals("direct", PlayContext.sourceOf(null, track("u1"), radio = false, jam = false))
        assertEquals("direct", PlayContext.sourceOf(ctx, track("u9"), radio = false, jam = false)) // not in the list
        assertEquals("radio", PlayContext.sourceOf(ctx, track("u1"), radio = true, jam = false))
        assertEquals("jam", PlayContext.sourceOf(ctx, track("u1"), radio = true, jam = true))
    }

    @Test fun `is checks kind and id`() {
        assertTrue(ctx.isList(PlayContext.Kind.PLAYLIST, "p1"))
        assertFalse(ctx.isList(PlayContext.Kind.ALBUM, "p1"))
        assertFalse(ctx.isList(PlayContext.Kind.PLAYLIST, "p2"))
    }
}

class PlayContextPersistTest {
    @Test fun `survives a round trip through its saved form`() {
        val ctx = PlayContext(PlayContext.Kind.ALBUM, "a1", "Moods", keys = setOf("u1", "s2"))
        org.junit.Assert.assertEquals(ctx, PlayContext.decode(ctx.encode()))
    }

    @Test fun `bad or missing saved form gives nothing`() {
        org.junit.Assert.assertNull(PlayContext.decode(null))
        org.junit.Assert.assertNull(PlayContext.decode("not json"))
    }
}
