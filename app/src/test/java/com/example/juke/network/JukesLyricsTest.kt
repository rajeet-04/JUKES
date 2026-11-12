package com.example.juke.network

import com.example.juke.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JukesLyricsTest {
    private val base = BuildConfig.JUKE_BACKEND_URL.trimEnd('/')
    private var url = ""

    private fun client(status: Int, body: String, vararg headers: Pair<String, String>) = HttpClient(MockEngine { request ->
        url = request.url.toString()
        respond(body, HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType to listOf("application/json"),
            *headers.map { it.first to listOf(it.second) }.toTypedArray()))
    })

    @Test fun alignedSubtitleLinesBecomeLrc() = runTest {
        val answer = JukesApi.lyrics("Rangeen", "Hasan Raheem", "", 222, "J78lINC__HE", client(200, """
            {"source":"youtube_subtitles","sync":"line","verified":false,"plain_lyrics":"Ghubar\nAap",
             "lines":[{"text":"Ghubar","start_ms":21329},{"text":"Aap","start_ms":86530}]}"""))
        assertEquals("$base/v1/lyrics?title=Rangeen&artist=Hasan+Raheem&duration_ms=222000&video_id=J78lINC__HE", url)
        assertEquals("[00:21.33] Ghubar\n[01:26.53] Aap", answer.result!!.syncedLyrics)
        assertEquals("Ghubar\nAap", answer.result!!.plainLyrics)
        assertNull(answer.retryAfterMs)
    }

    @Test fun lrclibLrcIsPassedThroughAndPlainOnlyHasNoTiming() = runTest {
        val timed = JukesApi.lyrics("Khoj", "Hasan Raheem", "", 205, null,
            client(200, """{"source":"lrclib","sync":"line","plain_lyrics":"a","synced_lyrics":"[00:14.95] a"}"""))
        assertEquals("[00:14.95] a", timed.result!!.syncedLyrics)
        val plain = JukesApi.lyrics("Rangeen", "Hasan Raheem", "", 222, "J78lINC__HE", client(200,
            """{"source":"lrclib","sync":"none","timing_rejected":"whole_seconds","plain_lyrics":"words","upgrading":true}"""))
        assertNull(plain.result!!.syncedLyrics)
        assertEquals("words", plain.result!!.plainLyrics)
        assertEquals(60_000L, plain.retryAfterMs) // the server is aligning better lyrics
    }

    @Test fun notFoundYetAsksAgainLaterAndFinalMissDoesNot() = runTest {
        val pending = JukesApi.lyrics("Jaana", "Maaidah", "", 149, "2LKJiANbmWA", client(404,
            """{"error":{"code":"lyrics_not_found","message":"x","retryable":true}}""", HttpHeaders.RetryAfter to "60"))
        assertNull(pending.result); assertEquals(60_000L, pending.retryAfterMs)
        val final = JukesApi.lyrics("Jaana", "Maaidah", "", 149, "2LKJiANbmWA", client(404,
            """{"error":{"code":"lyrics_not_found","message":"x","retryable":false}}"""))
        assertNull(final.result); assertNull(final.retryAfterMs)
    }

    @Test fun serverTroubleThrowsSoTheDeviceSourcesRun() = runTest {
        for (status in listOf(502, 503)) {
            try {
                JukesApi.lyrics("x", "y", "", 100, null, client(status, """{"error":{"code":"busy","retryable":true}}"""))
                fail("expected an exception")
            } catch (e: JukesApi.JukesException) {
                assertTrue(e.http == status)
            }
        }
    }
}
