package com.example.juke.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorMessagesTest {
    private val fallback = "Search failed. Try again."

    @Test
    fun rawTextNeverShows() {
        // The leak from the report: Ktor's socket timeout, with the URL in its message.
        val timeout = java.net.SocketTimeoutException("Socket timeout has expired [url=https://api.spotify.com/v1/search?q=x, socket_timeout=60000] ms")
        assertEquals("That took too long. Check your connection and try again.", timeout.userMessage(fallback))
        assertEquals("That took too long. Check your connection and try again.", Exception("wrapped", timeout).userMessage(fallback))
        assertEquals("You're offline. Check your connection and try again.", java.net.UnknownHostException("api.spotify.com").userMessage(fallback))
        assertEquals("You're offline. Check your connection and try again.", OfflineException().userMessage(fallback))
        assertEquals("The service is having trouble. Try again later.", Exception("Spotify returned status 503: <html>").userMessage(fallback))
        assertEquals("Too many requests. Wait a moment and try again.", Exception("Download failed with status 429").userMessage(fallback))
        assertEquals(fallback, IllegalStateException("Both stream providers failed: yt=null").userMessage(fallback))
        assertEquals("The playlist is either private or does not exist.", ShownException("The playlist is either private or does not exist.").userMessage(fallback))
    }
}
