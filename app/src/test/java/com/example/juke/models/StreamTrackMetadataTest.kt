package com.example.juke.models

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamTrackMetadataTest {
    @Test fun downloadCompletionPreservesLyricsHydratedDuringPlayback() {
        val snapshot = Track("id", "Song", "Artist", durationSec = 180, localUri = "/stream.mp3", isStream = true)
        val latest = snapshot.copy(localUri = "https://stream", syncedLyrics = "[00:01] Lyrics", plainLyrics = "Lyrics", ytVideoId = "video", isFavourite = true, lyricsOffsetMs = 300)
        val result = mergeCompletedStream(snapshot, latest)
        assertEquals("/stream.mp3", result.localUri)
        assertEquals("[00:01] Lyrics", result.syncedLyrics)
        assertEquals("Lyrics", result.plainLyrics)
        assertEquals("video", result.ytVideoId)
        assertEquals(true, result.isFavourite)
        assertEquals(300L, result.lyricsOffsetMs)
    }
}
