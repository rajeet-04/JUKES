package com.example.juke.services

import com.example.juke.database.PlayEventEntity
import com.example.juke.models.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class PlayLogTest {
    private var now = 1_700_000_000_000L
    private val rows = mutableListOf<PlayEventEntity>()
    private fun track(durationSec: Int) = Track(uuid = "u1", title = "t", artist = "a", durationSec = durationSec, spotifyId = "sp1")
    private val log = PlayLog({ rows += it }, { now }, { ZoneId.of("UTC") })

    @Test fun pausedTimeIsNotCounted() {
        log.start(track(200), playingNow = true)
        now += 30_000; log.setPlaying(false)
        now += 600_000; log.setPlaying(true)
        now += 20_000; log.end(completed = false)
        assertEquals(50_000L, rows.single().listenedMs)
        assertTrue(rows.single().skipped)
    }

    @Test fun listenedIsCappedAtDuration() {
        log.start(track(10), playingNow = true)
        now += 99_000; log.end(completed = true)
        assertEquals(10_000L, rows.single().listenedMs)
        assertTrue(rows.single().completed)
        assertFalse(rows.single().skipped)
    }

    @Test fun shortListensAreDropped() {
        log.start(track(200), playingNow = true)
        now += 500; log.end(completed = false)
        assertTrue(rows.isEmpty())
    }

    @Test fun songStartedPausedCountsOnlyAfterPlay() {
        log.start(track(200), playingNow = false)
        now += 60_000; log.setPlaying(true)
        now += 5_000; log.end(completed = false)
        assertEquals(5_000L, rows.single().listenedMs)
    }

    @Test fun localDateFollowsTheZone() {
        val tokyo = PlayLog({ rows += it }, { now }, { ZoneId.of("Asia/Tokyo") })
        now = 1_700_000_000_000L // 2023-11-14 22:13 UTC = 2023-11-15 07:13 JST
        tokyo.start(track(200), playingNow = true)
        now += 5_000; tokyo.end(completed = true)
        assertEquals("2023-11-15", rows.single().localDate)
        assertEquals("Asia/Tokyo", rows.single().timeZone)
    }

    @Test fun aListenThatStartsWhileOptedOutIsNeverRecorded() {
        var on = false
        val gated = PlayLog({ rows += it }, { now }, { ZoneId.of("UTC") }, enabled = { on })
        gated.start(track(200), playingNow = true)
        now += 30_000
        on = true // opting back in mid-song must not retro-record it
        gated.end(completed = false)
        assertTrue(rows.isEmpty())
        gated.start(track(200), playingNow = true)
        now += 30_000; gated.end(completed = false)
        assertEquals(1, rows.size)
    }

    @Test fun aListenThatSpansAnOptOutIsNeverRecorded() {
        var epoch = 0
        val gated = PlayLog({ rows += it }, { now }, { ZoneId.of("UTC") }, epoch = { epoch })
        gated.start(track(200), playingNow = true)
        now += 30_000
        epoch++ // opted out, then back in, while the song played
        gated.end(completed = false)
        assertTrue(rows.isEmpty())
        gated.start(track(200), playingNow = true)
        now += 30_000; gated.end(completed = false)
        assertEquals(1, rows.size)
    }

    @Test fun endWithoutStartDoesNothing() {
        log.end(completed = true)
        assertTrue(rows.isEmpty())
    }
}
