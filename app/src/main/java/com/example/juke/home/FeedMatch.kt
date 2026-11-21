package com.example.juke.home

import com.example.juke.network.SongTitle
import kotlin.math.abs

/**
 * Whether a Home feed song (a YouTube title and credit) is the same song as one from the library or
 * a Spotify search. YouTube titles carry extras ("(From ...)", "Official Video") and credits name
 * the composer or list artists differently, which the strict radio matcher scores down; with no
 * length on many feed songs, that left songs like ones already in the library "not found".
 */
object FeedMatch {
    /**
     * Same core title (brackets, labels and "From ..." removed), same version (a remix or slowed
     * cut is not the song), and either an artist in common (credits or "feat." in the title) or,
     * when both lengths are known, lengths within 5 seconds.
     */
    fun sameSong(title: String, artist: String, otherTitle: String, otherArtist: String, sec: Int?, otherSec: Int?): Boolean {
        val a = SongTitle.parse(title)
        val b = SongTitle.parse(otherTitle)
        if (a.core.isEmpty() || a.core != b.core || a.versions != b.versions) return false
        // Length only stands in for a credit spelled differently. It never rules out a shared
        // artist: a music video's intro or outro makes it 20+ seconds longer than the track.
        if (sec != null && otherSec != null && sec > 0 && otherSec > 0 && abs(sec - otherSec) <= 5) return true
        val ours = (SongTitle.artists(artist) + a.guests).toSet()
        return (SongTitle.artists(otherArtist) + b.guests).any { it in ours }
    }

    /** The first of [songs] that is the same song as [feed], or null. */
    fun <T> pick(feed: FeedSong, songs: List<T>, title: (T) -> String, artist: (T) -> String, sec: (T) -> Int?): T? =
        songs.firstOrNull { sameSong(feed.title, feed.artist, title(it), artist(it), feed.durationSec, sec(it)) }

    /** What to search the library for: the title up to its first bracket or " - ". */
    fun libraryQuery(feed: FeedSong): String =
        feed.title.substringBefore("(").substringBefore("[").substringBefore(" - ").trim().ifEmpty { feed.title }
}
