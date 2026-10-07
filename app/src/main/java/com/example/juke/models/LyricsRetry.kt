package com.example.juke.models

import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

internal suspend fun fetchLyricsWithRetry(fetch: suspend () -> LRCLibResult?): LRCLibResult? {
    repeat(2) { attempt ->
        val result = fetch()
        if (result != null && (!result.syncedLyrics.isNullOrBlank() || !result.plainLyrics.isNullOrBlank() || result.instrumental)) {
            return result
        }
        if (attempt == 0) delay(1_500L.milliseconds)
    }
    return null
}
