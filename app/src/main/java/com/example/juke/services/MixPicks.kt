package com.example.juke.services

import com.example.juke.network.RecommenderApi
import com.example.juke.network.SongIndex
import com.example.juke.network.SongTitle

/**
 * The songs a new mix would start with, in radio order: nothing already queued or waiting
 * ([blocked]), not the playing song or the radio's seed ([skipIds]), no spam, no blacklisted
 * artists, and each song once.
 */
internal fun mixPicks(
    radio: List<RecommenderApi.YouTubeRecommendation>,
    blocked: SongIndex,
    skipIds: Set<String>,
    limit: Int,
    isSpam: (String) -> Boolean = RecommenderApi::isSpamTitle,
    isBlocked: (RecommenderApi.YouTubeRecommendation) -> Boolean = { false },
): List<RecommenderApi.YouTubeRecommendation> {
    val out = ArrayList<RecommenderApi.YouTubeRecommendation>()
    for (rec in radio) {
        if (out.size >= limit) break
        if (rec.id in skipIds || isSpam(rec.title) || isBlocked(rec)) continue
        if (!blocked.add(RecommenderApi.songKey(rec.title, rec.artist), SongTitle.clockSeconds(rec.duration))) continue
        out += rec
    }
    return out
}
