package com.example.juke.models

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AlbumOfflineTest {
    private val urls = SpotifyExternalUrls(spotify = "https://open.spotify.com/track/test")
    private fun song(id: String = "song") = SpotifySimplifiedTrack(
        artists = listOf(SpotifyArtist(externalUrls = urls, name = "Artist")),
        durationMs = 180000, explicit = false, externalUrls = urls,
        id = id, name = "Song $id", uri = "spotify:track:$id"
    )
    private fun local(id: String = "song") = Track(
        uuid = "local-$id", title = "Song $id", artist = "Artist",
        durationSec = 180, localUri = "/music/$id.mp3", spotifyId = id
    )

    @Test fun `stream URLs never count as saved audio`() {
        assertNull(song().findOfflineTrack(listOf(local().copy(isStream = true, localUri = "https://audio.test/song"))))
        assertNull(song().findOfflineTrack(listOf(local().copy(localUri = null))))
        assertNull(song().findOfflineTrack(listOf(local().copy(localUri = ""))))
    }

    @Test fun `existing download is reused by Spotify identity`() {
        val track = local().copy(title = "Different capitalization", artist = "Other metadata")
        assertEquals(track, song().findOfflineTrack(listOf(track)))
    }

    @Test fun `legacy downloads without Spotify IDs match title artist and duration`() {
        val track = local().copy(spotifyId = null, title = "song song", artist = "artist", durationSec = 181)
        assertEquals(track, song().findOfflineTrack(listOf(track)))
        assertNull(song().findOfflineTrack(listOf(track.copy(artist = "Other artist"))))
        assertNull(song().findOfflineTrack(listOf(track.copy(durationSec = 230))))
    }

    @Test fun `large albums fetch all pages in album order`() = runBlocking {
        val all = (0 until 65).map { song(it.toString()) }
        val offsets = mutableListOf<Int>()
        val result = collectAlbumTracks { offset ->
            offsets += offset
            SpotifyAlbumTracksResponse(
                href = "album", limit = 50, offset = offset, total = all.size,
                next = if (offset == 0) "next-page" else null,
                items = all.drop(offset).take(50)
            )
        }
        assertEquals(listOf(0, 50), offsets)
        assertEquals(all, result)
    }

    @Test fun `incomplete page fails rather than marking a partial album saved`() = runBlocking {
        try {
            collectAlbumTracks { offset ->
                SpotifyAlbumTracksResponse("album", 50, next = "next", offset = offset, total = 65, items = emptyList())
            }
            fail("Expected incomplete album failure")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("incomplete"))
        }
    }

    @Test fun `saved album metadata round trips with ordered tracks`() {
        val album = SpotifyAlbum(artists = song().artists, externalUrls = urls,
            id = "album", images = emptyList(), name = "Album", totalTracks = 2)
        val entry = com.example.juke.repositories.OfflineAlbum(album, listOf(song("2"), song("1")))
        val json = kotlinx.serialization.json.Json
        val encoded = json.encodeToString(com.example.juke.repositories.OfflineAlbum.serializer(), entry)
        assertEquals(entry, json.decodeFromString(com.example.juke.repositories.OfflineAlbum.serializer(), encoded))
    }
}
