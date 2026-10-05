package com.example.juke.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LRCLibResultTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun nullDuration_stillDecodes() {
        val results = json.decodeFromString<List<LRCLibResult>>(
            """[{"id":1,"name":"High On You","trackName":"High On You","artistName":"Jind",
               "albumName":"A","duration":null,"instrumental":false,"plainLyrics":"la","syncedLyrics":null},
              {"id":2,"name":"X","trackName":"X","artistName":"Y","albumName":"B","duration":201.0,
               "instrumental":false,"plainLyrics":null,"syncedLyrics":"[00:01.00] la"}]"""
        )
        assertNull(results[0].duration)
        assertEquals("la", results[0].plainLyrics)
        assertEquals(201.0, results[1].duration!!, 0.0)
    }
}
