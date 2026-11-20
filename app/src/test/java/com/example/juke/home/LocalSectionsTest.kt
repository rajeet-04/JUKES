package com.example.juke.home

import com.example.juke.models.Track
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class LocalSectionsTest {
    @Test fun `speed dial takes top 9 by play count, one per artist`() {
        val t = (1..12).map {
            Track(uuid = "$it", title = "s$it", artist = if (it <= 2) "A" else "B$it", durationSec = 180, playCount = 100 - it)
        }
        val dial = LocalSections.speedDial(t)
        assertEquals(9, dial.size)
        assertEquals(listOf("1", "3", "4"), dial.take(3).map { it.uuid }) // "2" dropped: same artist as "1"
    }

    @Test fun `forgotten favourites are favourites unplayed for 30 days, oldest first, max 12`() {
        val now = Instant.parse("2026-11-20T00:00:00Z")
        val fav = { id: String, last: String? ->
            Track(uuid = id, title = id, artist = id, durationSec = 1, isFavourite = true, lastPlayedAt = last)
        }
        val out = LocalSections.forgottenFavourites(
            listOf(
                fav("recent", "2026-11-10T00:00:00Z"), fav("old", "2026-09-01T00:00:00Z"),
                fav("older", "2026-01-01T00:00:00Z"), fav("never", null),
                Track(uuid = "notfav", title = "x", artist = "x", durationSec = 1, lastPlayedAt = "2025-01-01T00:00:00Z"),
            ), now
        )
        assertEquals(listOf("never", "older", "old"), out.map { it.uuid })
    }

    @Test fun `forgotten favourites cap at 12`() {
        val now = Instant.parse("2026-11-20T00:00:00Z")
        val many = (1..20).map { Track(uuid = "$it", title = "$it", artist = "$it", durationSec = 1, isFavourite = true) }
        assertEquals(12, LocalSections.forgottenFavourites(many, now).size)
    }
}

class FeedSongTest {
    @Test fun `feed songs from radio drop official video clutter`() {
        val s = FeedSong.from(com.example.juke.network.RecommenderApi.YouTubeRecommendation("abcdefghijk", "OutStation - Dilhaara (Official Music Video)", "OutStation", "3:05"))
        assertEquals("OutStation - Dilhaara", s.title)
        assertEquals(185, s.durationSec)
    }
}
