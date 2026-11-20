package com.example.juke.home

import com.example.juke.models.Track
import java.time.Duration
import java.time.Instant

/** Home sections built from the library alone: no network. */
object LocalSections {
    private val FORGOTTEN_AFTER: Duration = Duration.ofDays(30)

    /** Top 9 by play count, one per artist. */
    fun speedDial(tracks: List<Track>): List<Track> =
        tracks.sortedByDescending { it.playCount }.distinctBy { it.artist }.take(9)

    /** Favourites not played for 30 days (never played first, then oldest), at most 12. */
    fun forgottenFavourites(tracks: List<Track>, now: Instant): List<Track> {
        val cutoff = now.minus(FORGOTTEN_AFTER)
        return tracks.asSequence()
            .filter { it.isFavourite }
            .map { it to it.lastPlayedAt?.let { s -> runCatching { Instant.parse(s) }.getOrNull() } }
            .filter { (_, at) -> at == null || at < cutoff }
            .sortedBy { (_, at) -> at ?: Instant.MIN }
            .map { it.first }
            .take(12)
            .toList()
    }
}
