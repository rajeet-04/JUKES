package com.example.juke.network

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class GamepvzStreamUrlTest {
    private fun proxy(target: String) = "https://gamepvz.com/api/download/dl?url=" +
        Base64.getUrlEncoder().withoutPadding().encodeToString(target.toByteArray())

    @Test fun streamsFromReturnedHttpsCdnInsteadOfSlowProxy() {
        val target = "https://cdn-spotify-inter.zm.io.vn/download/track?token=signed&name=Song"
        assertEquals(target, gamepvzStreamUrl(proxy(target)))
    }

    @Test fun unrecognizedOrUnsafeTargetsRetainProxy() {
        for (target in listOf("http://cdn-spotify.zm.io.vn/download/track", "https://example.com/download/track", "https://cdn-spotify.zm.io.vn.evil.com/download/track", "https://user@cdn-spotify.zm.io.vn/download/track")) {
            val url = proxy(target)
            assertEquals(url, gamepvzStreamUrl(url))
        }
        val malformed = "https://gamepvz.com/api/download/dl?url=%%%"
        assertEquals(malformed, gamepvzStreamUrl(malformed))
    }
}
