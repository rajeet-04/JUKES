package com.example.juke.network

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.*
import java.net.URI

internal fun parseSpotsaverDownloadUrl(body: String): String? = try {
    val response = Json.parseToJsonElement(body).jsonObject
    if (response["success"]?.jsonPrimitive?.content !in listOf("true", "1")) null
    else response["downloadUrl"]?.jsonPrimitive?.contentOrNull?.takeIf {
        val uri = URI(it)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
    }
} catch (_: Exception) { null }

object SpotsaverApi {
    suspend fun getDownloadRequest(
        title: String,
        artist: String,
        durationSec: Int? = null,
        client: HttpClient = ApiClient.httpClient
    ): SpotifyApi.DirectDownloadRequest {
        suspend fun post(path: String, payload: JsonObject): String {
            val response = client.post("https://spotsaver.net/api/$path/") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            val body = response.bodyAsText()
            check(response.status.value in 200..299) { "Spotsaver $path HTTP ${response.status.value}" }
            return body
        }
        // Pick the video ourselves: title + every credited artist + length. Spotsaver's own lookup
        // takes the first search hit, which is how remixes and other songs sneaked in.
        val knownDuration = durationSec?.takeIf { it > 0 }
        val ranked = RecommenderApi.rankVideoMatches("$title $artist", knownDuration, artist)
        val videoId = ranked.firstOrNull() ?: if (knownDuration == null) {
            val lookup = Json.parseToJsonElement(post("get-id", buildJsonObject {
                put("title", title); put("artist", artist)
            })).jsonObject
            check(lookup["success"]?.jsonPrimitive?.content in listOf("true", "1")) { "Spotsaver lookup failed" }
            lookup["videoId"]?.jsonPrimitive?.contentOrNull
        } else {
            error("No YouTube video matches '$title' by $artist at ${durationSec}s") // let exact-URL providers handle it
        }
        check(!videoId.isNullOrBlank()) { "Spotsaver videoId missing" }
        val body = post("download", buildJsonObject {
            put("videoId", videoId)
            put("candidateIds", buildJsonArray { ranked.drop(1).take(3).forEach { add(it) } })
            put("format", "mp3")
            put("title", "$title - $artist")
            put("licenseKey", JsonNull)
        })
        val url = parseSpotsaverDownloadUrl(body)
            ?: error("Spotsaver returned no playable HTTPS downloadUrl")
        return SpotifyApi.DirectDownloadRequest(url = url, probeRanges = false)
    }
}
