package com.example.juke.home

import com.example.juke.models.Track
import com.example.juke.network.RecommenderApi
import com.example.juke.network.SongTitle
import com.example.juke.models.SpotifyAlbum

/** One song in a home row, straight from YouTube radio; matched on Spotify only when played. */
data class FeedSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val art: String,
    val durationSec: Int?,
) {
    /** Dedupe key shared with the library ([RecommenderApi.songKey]). */
    val key: String get() = SongTitle.key(title, artist)

    fun toRec() = RecommenderApi.YouTubeRecommendation(
        id = videoId, title = title, artist = artist,
        duration = durationSec?.let { "%d:%02d".format(it / 60, it % 60) },
    )

    companion object {
        fun art(videoId: String) = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"

        fun from(rec: RecommenderApi.YouTubeRecommendation) = FeedSong(
            videoId = rec.id,
            title = YtmCharts.displayTitle(rec.title),
            artist = rec.artist,
            art = art(rec.id),
            durationSec = SongTitle.clockSeconds(rec.duration),
        )
    }
}

/** A home feed section. Each shape renders differently; [id] is stable for LazyColumn keys. */
sealed interface HomeSection {
    val id: String
    val title: String

    /** 4-row paged grid of songs. */
    data class QuickPicks(override val id: String, override val title: String, val songs: List<FeedSong>, val seed: Track?) : HomeSection

    /** 3×3 tiles of the listener's own most played songs. */
    data class SpeedDial(override val id: String, override val title: String, val tracks: List<Track>) : HomeSection

    /** Horizontal cards of radio picks ("Because you played X", "Fresh finds"). */
    data class SongRow(override val id: String, override val title: String, val songs: List<FeedSong>, val seed: Track?) : HomeSection

    /** Horizontal cards of library tracks ("Forgotten favourites", "Back in rotation"). */
    data class LocalRow(override val id: String, override val title: String, val tracks: List<Track>) : HomeSection

    data class Releases(override val id: String, override val title: String, val albums: List<SpotifyAlbum>) : HomeSection

    /** One big glass card standing in for a whole mix. */
    data class HeroMix(override val id: String, override val title: String, val subtitle: String, val songs: List<FeedSong>, val seed: Track) : HomeSection

    /** Numbered list (Trending). */
    data class Ranked(override val id: String, override val title: String, val songs: List<FeedSong>) : HomeSection
}
