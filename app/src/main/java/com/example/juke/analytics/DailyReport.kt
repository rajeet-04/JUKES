package com.example.juke.analytics

import com.example.juke.database.PlayEventDao
import com.example.juke.database.PlayEventEntity
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** One PostHog event per closed local day, built from that day's [PlayEventEntity] rows. */
object DailyReport {
    const val EVENT = "daily_listening_report"
    private const val VERSION = 1

    /** Same user and day always give the same uuid, so a retry after a lost response is not counted twice. */
    fun eventUuid(distinctId: String, date: String): String =
        UUID.nameUUIDFromBytes("$EVENT|$distinctId|$date".toByteArray()).toString()

    /** The day's rows (all one local date) as event properties; songs are grouped by title and artist. */
    fun properties(date: String, rows: List<PlayEventEntity>, platform: String): JsonObject {
        val tz = rows.firstOrNull()?.timeZone ?: ZoneId.systemDefault().id
        val zone = runCatching { ZoneId.of(tz) }.getOrDefault(ZoneId.systemDefault())
        val hours = LongArray(24)
        rows.forEach { r ->
            val h = Instant.ofEpochMilli(r.startedAt).atZone(zone).hour
            hours[h] += r.listenedMs
        }
        val tracks = rows.groupBy { it.songKey }.map { (key, list) ->
            val first = list.first()
            buildJsonObject {
                put("song_key", key)
                put("track_uuid", first.trackUuid)
                put("title", first.title)
                put("artist", first.artist)
                put("spotify_id", first.spotifyId)
                put("album_spotify_id", first.albumSpotifyId)
                put("artist_spotify_ids", first.artistSpotifyIds?.let { ids -> JsonArray(ids.map(::JsonPrimitive)) } ?: JsonNull)
                put("yt_video_id", first.ytVideoId)
                put("plays", list.size)
                put("completed", list.count { it.completed })
                put("skips", list.count { it.skipped })
                put("listened_ms", list.sumOf { it.listenedMs })
            }
        }
        return buildJsonObject {
            put("date", date)
            put("timezone", tz)
            put("platform", platform)
            put("report_version", VERSION)
            put("plays", rows.size)
            put("skips", rows.count { it.skipped })
            put("total_listened_ms", rows.sumOf { it.listenedMs })
            put("listened_ms_by_hour", JsonArray(hours.map(::JsonPrimitive)))
            put("tracks", JsonArray(tracks))
        }
    }

    /** The body for PostHog's /batch endpoint. */
    fun batchBody(apiKey: String, distinctId: String, date: String, props: JsonObject, zone: ZoneId): JsonObject {
        // Noon of the local day, so the event lands on that day whatever the viewer's time zone does at midnight.
        val at = LocalDate.parse(date).atTime(12, 0).atZone(zone).toInstant().toString()
        return buildJsonObject {
            put("api_key", apiKey)
            put("batch", JsonArray(listOf(buildJsonObject {
                put("event", EVENT)
                put("distinct_id", distinctId)
                put("timestamp", at)
                put("uuid", eventUuid(distinctId, date))
                put("properties", props)
            })))
        }
    }
}

/**
 * Reports every closed day that still has rows, oldest first, and deletes a day's rows only when [send]
 * says PostHog accepted it. Today is never reported. A failed day is kept for the next run.
 */
class DailyReportUploader(
    private val dao: PlayEventDao,
    private val distinctId: () -> String,
    private val enabled: () -> Boolean,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val platform: String = "android",
    /** Posts one event body; true only on a success response from PostHog. */
    private val send: suspend (body: JsonObject) -> Boolean,
    private val apiKey: () -> String
) {
    private val lock = Mutex()

    /** @return how many days were reported and cleared. */
    suspend fun uploadPending(): Int = lock.withLock {
        if (!enabled() || apiKey().isBlank()) return 0
        var done = 0
        for (date in dao.dates()) {
            val day = runCatching { LocalDate.parse(date) }.getOrNull()
            if (day == null) { dao.deleteDate(date); continue } // unreadable date, nothing to report
            if (!day.isBefore(today())) break
            val rows = dao.forDate(date)
            if (rows.isEmpty()) continue
            val id = distinctId()
            val zone = runCatching { ZoneId.of(rows.first().timeZone) }.getOrDefault(ZoneId.systemDefault())
            val body = DailyReport.batchBody(apiKey(), id, date, DailyReport.properties(date, rows, platform), zone)
            val ok = try { send(body) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { false }
            if (!ok) break // offline or rejected: keep this and later days, try again next time
            dao.deleteDate(date)
            done++
        }
        done
    }
}

/** Wires the uploader to PostHog's /batch endpoint, the account email and the install id. */
object DailyReports {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /** The account email when logged in, so every platform reports under one person; else the install id. */
    fun distinctId(context: android.content.Context): String =
        com.example.juke.account.Account.email.value?.trim()?.lowercase()
            ?: AnalyticsManager.getInstance(context).installId()

    /** Fire and forget; safe to call on every app open. */
    fun uploadPending(context: android.content.Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                DailyReportUploader(
                    dao = com.example.juke.database.MusicDatabase.getDatabase(app).playEventDao(),
                    distinctId = { distinctId(app) },
                    enabled = { true },
                    apiKey = { com.example.juke.BuildConfig.POSTHOG_API_KEY },
                    send = { body ->
                        val url = com.example.juke.BuildConfig.POSTHOG_HOST.trimEnd('/') + "/batch/"
                        com.example.juke.network.ApiClient.httpClient.post(url) {
                            contentType(ContentType.Application.Json)
                            setBody(body.toString())
                        }.status.isSuccess()
                    }
                ).uploadPending()
            }
        }
    }
}
