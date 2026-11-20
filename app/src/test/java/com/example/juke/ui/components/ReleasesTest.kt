package com.example.juke.ui.components

import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.SpotifyExternalUrls
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleasesTest {
    private fun album(name: String, type: String?, date: String?, tracks: Int? = 1) =
        SpotifyAlbum(albumType = type, artists = emptyList(), externalUrls = SpotifyExternalUrls(), id = name, images = emptyList(), name = name, releaseDate = date, totalTracks = tracks)

    @Test fun `newest release is pulled out, the rest split into albums and singles`() {
        val r = splitReleases(listOf(
            album("Old single", "single", "2023-01-02"),
            album("LP", "album", "2024-05-01", 10),
            album("New single", "single", "2025-08-09"),
            album("EP", "single", "2024", 4),
        ))
        assertEquals("New single", r.latest?.name)
        assertEquals(listOf("LP"), r.albums.map { it.name })
        assertEquals(listOf("EP", "Old single"), r.singles.map { it.name })
    }

    @Test fun `an artist with one release has only the latest`() {
        val r = splitReleases(listOf(album("Only", "album", "2020-01-01", 8)))
        assertEquals("Only", r.latest?.name)
        assertEquals(emptyList<SpotifyAlbum>(), r.albums + r.singles)
    }

    @Test fun `meta reads naturally`() {
        assertEquals("Single · 2025", releaseMeta(album("a", "single", "2025-08-09", 1)))
        assertEquals("EP · 2024 · 4 songs", releaseMeta(album("b", "single", "2024", 4)))
        assertEquals("Album · 2024 · 10 songs", releaseMeta(album("c", "album", "2024-05-01", 10)))
        assertEquals("Album", releaseMeta(album("d", null, null, null)))
    }

    @Test fun `only a dated release from the last two months is new`() {
        val today = java.time.LocalDate.of(2026, 11, 21)
        assertEquals(true, isRecent("2026-10-30", today))
        assertEquals(false, isRecent("2025-08-09", today))
        assertEquals(false, isRecent("2026", today))
        assertEquals(false, isRecent(null, today))
    }
}
