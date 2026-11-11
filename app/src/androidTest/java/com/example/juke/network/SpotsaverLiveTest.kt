package com.example.juke.network

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.readAvailable
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URI

/** Opt-in live network probe; never run on ordinary builds; playback checks require a silent emulator. */
class SpotsaverLiveTest {
    @Test fun productionResolverDownloadsCompleteMp3() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("spotsaverLive") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.cacheDir, "spotsaver-production-test.mp3")
        val started = SystemClock.elapsedRealtime()
        try {
            val request = SpotsaverApi.getDownloadRequest("Jasmine", "Talha Anjum, Umair")
            // Validate the real download path with no HEAD/range probes for tunnel URLs.
            com.example.juke.utils.FastDownloader.downloadSegmented(
                request.url, file, request.headers, threads = 4, probeRanges = request.probeRanges
            )
            assertTrue("Downloaded audio too small", file.length() > 100_000)
            val retriever = android.media.MediaMetadataRetriever()
            val duration = try {
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally { retriever.release() }
            assertTrue("Downloaded MP3 cannot be decoded", duration > 30_000L)
            verifyForwardSeek(request.url, file, duration)
            context.openFileOutput("spotsaver-production-results.json", 0).use {
                it.write(buildJsonObject {
                    put("passed", true)
                    put("fileBytes", file.length())
                    put("durationMs", duration)
                    put("totalMs", SystemClock.elapsedRealtime() - started)
                    put("rangeProbe", request.probeRanges)
                    put("forwardSeekVerified", true)
                }.toString().toByteArray())
            }
        } finally {
            file.delete()
        }
    }

    private suspend fun verifyForwardSeek(url: String, file: java.io.File, durationMs: Long) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = com.example.juke.services.PlaybackManager.getInstance(context)
        withContext(Dispatchers.Main) { manager.initialize() }
        val future = withContext(Dispatchers.Main) {
            androidx.media3.session.MediaController.Builder(context,
                androidx.media3.session.SessionToken(context, android.content.ComponentName(context,
                    com.example.juke.services.PlaybackService::class.java))).buildAsync()
        }
        val witness = future.get(15, java.util.concurrent.TimeUnit.SECONDS)
        val remote = com.example.juke.models.Track("seek-probe", "Jasmine", "Talha Anjum, Umair",
            durationSec = (durationMs / 1000).toInt(), localUri = url, isStream = true)
        val local = remote.copy(localUri = file.absolutePath)
        try {
            withTimeout(10_000) {
                while (manager.currentTrackIdFlow.value != remote.uuid) {
                    withContext(Dispatchers.Main) { manager.setQueue(listOf(remote, local.copy(uuid = "seek-tail")), 0) }
                    delay(100)
                }
            }
            withTimeout(30_000) { manager.isPlayingFlow.first { it } }
            val target = 120_000L
            withContext(Dispatchers.Main) {
                manager.replaceTrackInQueue(remote.uuid, local, seamlessIfPlaying = true)
                manager.seekTo(target)
            }
            withTimeout(10_000) {
                while (!withContext(Dispatchers.Main) {
                    manager.isPlayingFlow.value && manager.getCurrentPosition() in target..(target + 5000)
                }) delay(100)
            }
            withContext(Dispatchers.Main) {
                assertTrue("Seek must use completed local source", witness.currentMediaItem?.localConfiguration?.uri?.path == file.absolutePath)
                assertTrue("Seeking must preserve queue", witness.mediaItemCount == 2)
                witness.pause()
                manager.seekTo(60_000)
            }
            withTimeout(10_000) {
                while (!withContext(Dispatchers.Main) { manager.getCurrentPosition() == 60_000L && !manager.isPlayingFlow.value }) delay(100)
            }
        } finally {
            withContext(Dispatchers.Main) {
                witness.pause()
                witness.clearMediaItems()
                witness.release()
            }
        }
    }

    @Test fun coldSessionsResolveAndReturnMp3Bytes() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Enable with -e spotsaverLive true", args.getString("spotsaverLive") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reports = mutableListOf<JsonObject>()
        try {
            repeat(2) { run ->
                // Fresh client, no cookie storage, credentials, JS or browser session.
                val client = HttpClient(OkHttp) {
                    install(HttpTimeout) {
                        requestTimeoutMillis = 45_000
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 20_000
                    }
                }
                val report = mutableMapOf<String, JsonElement>("run" to JsonPrimitive(run + 1))
                try {
                    suspend fun api(path: String, payload: JsonObject): JsonObject {
                        val start = SystemClock.elapsedRealtime()
                        val response = client.post("https://spotsaver.net/api/$path/") {
                            contentType(ContentType.Application.Json)
                            header("Accept", "application/json")
                            setBody(payload.toString())
                        }
                        val body = response.bodyAsText()
                        report["${path}Status"] = JsonPrimitive(response.status.value)
                        report["${path}Ms"] = JsonPrimitive(SystemClock.elapsedRealtime() - start)
                        val challenge = body.contains("cf-chl", true) || body.contains("Just a moment", true) || body.contains("challenge-platform", true)
                        report["${path}Challenge"] = JsonPrimitive(challenge)
                        assertTrue("$path blocked/challenged: HTTP ${response.status.value}", response.status.value in 200..299 && !challenge)
                        return try { Json.parseToJsonElement(body).jsonObject } catch (e: Exception) {
                            throw AssertionError("$path returned non-JSON (${response.headers["Content-Type"]})", e)
                        }
                    }
                    val id = api("get-id", buildJsonObject {
                        put("title", "Jasmine")
                        put("artist", "Talha Anjum, Umair")
                    })["videoId"]?.jsonPrimitive?.contentOrNull
                    assertTrue("Missing videoId", !id.isNullOrBlank())
                    val response = api("download", buildJsonObject {
                        put("videoId", id!!)
                        put("candidateIds", buildJsonArray {})
                        put("format", "mp3")
                        put("title", "Jasmine - Talha Anjum, Umair")
                        put("licenseKey", JsonNull)
                    })
                    val url = response["downloadUrl"]?.jsonPrimitive?.contentOrNull
                    assertTrue("Missing downloadUrl (status=${response["status"]})", !url.isNullOrBlank())
                    val uri = URI(url!!)
                    assertTrue("Expected HTTPS download URL", uri.scheme == "https" && uri.host != null)
                    report["mediaHost"] = JsonPrimitive(uri.host)
                    val start = SystemClock.elapsedRealtime()
                    client.prepareGet(url).execute { media ->
                        report["mediaStatus"] = JsonPrimitive(media.status.value)
                        report["mediaContentType"] = JsonPrimitive(media.headers["Content-Type"] ?: "")
                        report["mediaHeadersMs"] = JsonPrimitive(SystemClock.elapsedRealtime() - start)
                        assertTrue("Media HTTP ${media.status.value}", media.status.value in 200..299)
                        val bytes = ByteArray(4096)
                        var count = 0
                        val channel = media.bodyAsChannel()
                        while (count < bytes.size) {
                            val read = channel.readAvailable(bytes, count, bytes.size - count)
                            if (read < 0) break
                            count += read
                        }
                        report["firstBytesMs"] = JsonPrimitive(SystemClock.elapsedRealtime() - start)
                        report["sampleBytes"] = JsonPrimitive(count)
                        val id3 = count >= 3 && bytes[0] == 73.toByte() && bytes[1] == 68.toByte() && bytes[2] == 51.toByte()
                        val frame = (0 until (count - 1).coerceAtLeast(0)).any {
                            (bytes[it].toInt() and 255) == 255 && (bytes[it + 1].toInt() and 224) == 224
                        }
                        report["mp3Signature"] = JsonPrimitive(id3 || frame)
                        assertTrue("Returned bytes are not MP3 (could be HTML/challenge)", id3 || frame)
                    }
                    report["passed"] = JsonPrimitive(true)
                } catch (e: Throwable) {
                    report["passed"] = JsonPrimitive(false)
                    report["failureType"] = JsonPrimitive(e.javaClass.simpleName)
                    throw e
                } finally {
                    reports += JsonObject(report)
                    client.close()
                }
            }
        } finally {
            // Signed URLs and cookies are intentionally excluded from the report.
            context.openFileOutput("spotsaver-live-results.json", 0).use {
                it.write(JsonArray(reports).toString().toByteArray())
            }
        }
    }
}
