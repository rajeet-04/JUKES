package com.example.juke.models

/** Never count streams as offline audio, even though they also have a localUri. */
fun SpotifySimplifiedTrack.findOfflineTrack(library: List<Track>): Track? =
    library.findOffline(id, name, artists.joinToString(", ") { it.name }, durationMs)

/** The downloaded copy of a search, artist, playlist or Home result, if there is one. */
fun SpotifyTrack.findOfflineTrack(library: List<Track>): Track? =
    library.findOffline(id, name, artists.joinToString(", ") { it.name }, durationMs)

private fun List<Track>.findOffline(id: String?, name: String, artists: String, durationMs: Int): Track? = find {
    !it.isStream && !it.localUri.isNullOrBlank() &&
        ((id != null && it.spotifyId == id) ||
            (it.title.equals(name, ignoreCase = true) && it.artist.equals(artists, ignoreCase = true) &&
                kotlin.math.abs(it.durationSec - durationMs / 1000) <= 2))
}

/** Fetch every page; fail instead of silently saving an incomplete album. */
suspend fun collectAlbumTracks(
    fetchPage: suspend (offset: Int) -> SpotifyAlbumTracksResponse
): List<SpotifySimplifiedTrack> {
    val tracks = mutableListOf<SpotifySimplifiedTrack>()
    var offset = 0
    while (true) {
        val page = fetchPage(offset)
        tracks.addAll(page.items)
        val nextOffset = page.offset + page.items.size
        if (page.next == null && nextOffset >= page.total) return tracks
        check(page.items.isNotEmpty() && nextOffset > offset) { "Album track list is incomplete. Please retry." }
        offset = nextOffset
    }
}
