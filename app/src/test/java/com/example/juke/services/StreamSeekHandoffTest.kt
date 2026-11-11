package com.example.juke.services

import com.example.juke.models.Track
import org.junit.Assert.*
import org.junit.Test

class StreamSeekHandoffTest {
    private val local = Track("song", "Song", "Artist", durationSec = 180, localUri = "/local.mp3", isStream = true)
    @Test fun completedDownloadRemainsAvailableForUserSeek() {
        val handoff = StreamSeekHandoff()
        handoff.completed(local)
        assertEquals(local, handoff.fileFor("song"))
    }
    @Test fun seekBeforeDownloadCompletionIsReplayedAtRequestedPosition() {
        val handoff = StreamSeekHandoff()
        handoff.defer("song", 120_000)
        assertEquals(120_000L, handoff.completed(local))
        assertNull(handoff.completed(local))
    }
    @Test fun changingSongsCancelsDeferredSeek() {
        val handoff = StreamSeekHandoff()
        handoff.defer("song", 120_000)
        handoff.trackChanged("other")
        assertNull(handoff.completed(local))
    }
    @Test fun otherTrackCannotConsumePendingSeek() {
        val handoff = StreamSeekHandoff()
        handoff.defer("song", 120_000)
        assertNull(handoff.completed(local.copy(uuid = "other")))
        assertEquals(120_000L, handoff.completed(local))
    }
}
