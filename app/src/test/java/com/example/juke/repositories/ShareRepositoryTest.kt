package com.example.juke.repositories

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareRepositoryTest {

    private val host = "https://j.rajeet.in"

    @Test
    fun parsesWebAndSchemeLinks() {
        assertEquals("aB3dE5gH7j", ShareRepository.parsePlaylistId("$host/p/aB3dE5gH7j"))
        assertEquals("aB3dE5gH7j", ShareRepository.parsePlaylistId("check this out $host/p/aB3dE5gH7j"))
        assertEquals("aB3dE5gH7j", ShareRepository.parsePlaylistId("juke://p/aB3dE5gH7j"))
    }

    @Test
    fun ignoresOtherLinks() {
        assertNull(ShareRepository.parsePlaylistId("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"))
        assertNull(ShareRepository.parsePlaylistId("$host/p/short"))
    }
}
