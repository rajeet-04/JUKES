package com.example.juke.home

import com.example.juke.network.RecommenderApi
import kotlinx.coroutines.CancellationException

/**
 * Finds the playable version of a feed song only when it is tapped. Null means "not found",
 * including a failed lookup, so the caller shows one message instead of a stuck spinner.
 */
internal suspend fun <T> resolveFeedSong(
    song: FeedSong,
    match: suspend (RecommenderApi.YouTubeRecommendation) -> T?,
): T? = try {
    match(song.toRec())
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}
