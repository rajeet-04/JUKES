package com.example.juke.network

import com.example.juke.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class JukesApiTest {
    private val base = BuildConfig.JUKE_BACKEND_URL.trimEnd('/')
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(vararg replies: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        var i = 0
        return HttpClient(MockEngine { request ->
            requests += request
            replies.getOrElse(i++) { error("unexpected request ${request.url}") }(this, request)
        })
    }

    private fun json(status: HttpStatusCode, body: String, vararg headers: Pair<String, String>):
            MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        respond(body, status, headersOf(HttpHeaders.ContentType to listOf("application/json"),
            *headers.map { it.first to listOf(it.second) }.toTypedArray()))
    }

    private fun prepared(status: String, title: String = "Tum Hi Ho", artist: String = "Arijit Singh", durationMs: Int = 262000) =
        json(HttpStatusCode.fromValue(if (status == "ready") 200 else 202), """
            {"video_id":"fsiPzT50ZiM","title":"$title","artists":["$artist"],"artist":"$artist",
             "duration_ms":$durationMs,"job_id":"j1","status":"$status","pool":"requested",
             ${if (status == "ready") "\"audio_url\":\"$base/v1/audio/fsiPzT50ZiM\"," else ""}
             "personalization_status":"anonymous"}""")

    private fun job(status: String, extra: String = "") = json(HttpStatusCode.OK,
        """{"job_id":"j1","video_id":"fsiPzT50ZiM","status":"$status","pool":"requested"$extra}""")

    private val ready = job("ready", ""","audio_url":"$base/v1/audio/fsiPzT50ZiM"""")

    private fun error(status: Int, code: String, retryable: Boolean = false, vararg headers: Pair<String, String>) =
        json(HttpStatusCode.fromValue(status), """{"error":{"code":"$code","message":"x","retryable":$retryable}}""", *headers)

    private suspend fun play(client: HttpClient, title: String = "Tum Hi Ho", artist: String = "Arijit Singh", sec: Int? = 262) =
        JukesApi.requestForPlayback(title, artist, sec, showPreparing = true, client = client)

    @Test
    fun readyOnPrepare_playsAudioUrlWithRanges() = runTest {
        val request = play(client(prepared("ready")), artist = "Arijit Singh, Mithoon")
        assertEquals("$base/v1/audio/fsiPzT50ZiM", request.url)
        assertTrue(request.probeRanges)
        val body = (requests.single().body as TextContent).text
        // Main artist only; seconds sent as milliseconds.
        assertTrue(body, "\"artist\":\"Arijit Singh\"" in body && "\"duration_ms\":262000" in body)
        assertEquals("$base/v1/audio/prepare", requests.single().url.toString())
        assertNull(JukesApi.preparing.value)
    }

    @Test
    fun queued_pollsJobUntilReady() = runTest {
        val request = play(client(prepared("queued"), job("downloading"), job("downloading"), ready))
        assertEquals("$base/v1/audio/fsiPzT50ZiM", request.url)
        assertEquals("$base/v1/jobs/j1?wait=10", requests.last().url.toString())
        // Polls at once; a server that answers instantly (no long poll) gets the 1 s, 2 s backoff.
        assertEquals(1_000L + 2_000L, currentTime)
    }

    @Test
    fun wrongSong_isRejectedBeforePolling() = runTest {
        try {
            play(client(prepared("queued", title = "Pal Pal", artist = "Afusic")), title = "Wishes", artist = "Hasan Raheem")
            fail("expected a mismatch")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Pal Pal"))
        }
        assertEquals(1, requests.size)
        assertNull(JukesApi.preparing.value)
    }

    @Test
    fun wrongLength_isRejected() = runTest {
        try {
            play(client(prepared("ready", durationMs = 330000)))
            fail("expected a length mismatch")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("330s"))
        }
    }

    @Test
    fun rateLimited_honoursRetryAfterThenRetries() = runTest {
        val request = play(client(error(429, "rate_limited", true, HttpHeaders.RetryAfter to "7"), prepared("ready")))
        assertEquals("$base/v1/audio/fsiPzT50ZiM", request.url)
        assertEquals(7_000L, currentTime)
    }

    @Test
    fun pendingAndCapacity_areRetriedNotFailed() = runTest {
        play(client(error(503, "pending", true, HttpHeaders.RetryAfter to "2"), error(503, "cache_capacity", true), prepared("ready")))
        assertEquals(3, requests.size)
    }

    @Test
    fun unknownJob_preparesAgain() = runTest {
        play(client(prepared("queued"), error(404, "job_not_found"), prepared("ready")))
        assertEquals("$base/v1/audio/prepare", requests.last().url.toString())
    }

    @Test
    fun networkErrorWhilePolling_keepsPolling() = runTest {
        play(client(prepared("queued"), { throw IOException("airplane mode") }, { throw IOException("no route") }, ready))
        assertEquals(4, requests.size)
    }

    @Test
    fun retryableJobFailure_preparesAgain_terminalOneFallsThrough() = runTest {
        play(client(prepared("queued"), job("failed", ""","error":{"code":"extraction_timeout","retryable":true}"""), prepared("ready")))
        requests.clear()
        try {
            play(client(prepared("queued"), job("failed", ""","error":{"code":"video_unavailable","retryable":false}""")))
            fail("expected a terminal failure")
        } catch (e: JukesApi.JukesException) {
            assertEquals("video_unavailable", e.code)
        }
    }

    @Test
    fun noMatch_isTerminal() = runTest {
        try {
            play(client(error(404, "no_match")))
            fail("expected no_match")
        } catch (e: JukesApi.JukesException) {
            assertEquals(404, e.http)
            assertEquals("no_match", e.code)
        }
        assertEquals(1, requests.size)
    }

    @Test
    fun serverWithoutV1_isReportedAsMissing() = runTest {
        for (reply in listOf(
            json(HttpStatusCode.NotFound, """{"error":{"code":"not_found","message":"not found","retryable":false}}"""),
            { _: HttpRequestData -> respond("<html>Page not found</html>", HttpStatusCode.NotFound) },
        )) {
            try {
                play(client(reply))
                fail("expected V1MissingException")
            } catch (_: JukesApi.V1MissingException) {
            }
        }
    }

    @Test
    fun videoIdOf_readsBackendAudioUrls() {
        assertEquals("fsiPzT50ZiM", JukesApi.videoIdOf("$base/v1/audio/fsiPzT50ZiM"))
        assertEquals("ab_c-1", JukesApi.videoIdOf("$base/audio/?video_id=ab_c-1&wait=1"))
        assertNull(JukesApi.videoIdOf("https://example.com/v1/audio/fsiPzT50ZiM"))
        assertNull(JukesApi.videoIdOf("/data/user/0/music/x.mp3"))
    }

    @Test
    fun lengthTolerance_matchesServer() {
        assertTrue(JukesApi.isLengthOk(270, 262))   // within 8 s
        assertTrue(!JukesApi.isLengthOk(281, 262))  // 7 % of 262 s = 18 s
        assertTrue(JukesApi.isLengthOk(640, 600))   // 7 % of 600 s = 42 s
        assertTrue(JukesApi.isLengthOk(0, 262))     // unknown
    }
}
