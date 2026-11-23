package com.example.juke.analytics

import com.example.juke.database.PlayEventDao
import com.example.juke.database.PlayEventEntity
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

    /**
     * Same user, device and day always give the same uuid, so a retry after a lost response is not counted
     * twice. The device is part of it so a phone and a desktop reporting the same day under one email do not
     * collide and get one of them dropped as a duplicate. [batch] names exactly which rows the report holds
     * (their ids): a retry of the same rows keeps the same uuid, while a report with a different set of rows,
     * such as one that gained a listen finishing late, gets its own uuid and is never dropped as a duplicate
     * of an accepted report that did not contain that listen.
     */
    fun eventUuid(distinctId: String, date: String, deviceId: String, batch: String = ""): String =
        UUID.nameUUIDFromBytes("$EVENT|$distinctId|$deviceId|$date|$batch".toByteArray()).toString()

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
            put("listened_ms_by_source", buildJsonObject {
                rows.groupBy { it.source ?: "unknown" }.toSortedMap().forEach { (src, list) -> put(src, list.sumOf { it.listenedMs }) }
            })
            put("tracks", JsonArray(tracks))
        }
    }

    /** The body for PostHog's /batch endpoint. */
    fun batchBody(apiKey: String, distinctId: String, deviceId: String, date: String, props: JsonObject, zone: ZoneId, batch: String = ""): JsonObject {
        // Noon of the local day, so the event lands on that day whatever the viewer's time zone does at midnight.
        val at = LocalDate.parse(date).atTime(12, 0).atZone(zone).toInstant().toString()
        return buildJsonObject {
            put("api_key", apiKey)
            put("batch", JsonArray(listOf(buildJsonObject {
                put("event", EVENT)
                put("distinct_id", distinctId)
                put("timestamp", at)
                put("uuid", eventUuid(distinctId, date, deviceId, batch))
                put("properties", props)
            })))
        }
    }
}

/**
 * The row ids each report was frozen to before it was first sent, by "distinct id|date". A report whose
 * response was lost is retried with exactly those rows and the same uuid, so PostHog drops the repeat; rows
 * that arrived since go out as their own report instead of being folded into one PostHog may already hold.
 */
interface PendingBatches {
    fun get(key: String): List<Long>?
    fun put(key: String, ids: List<Long>)
    fun remove(key: String)
}

class InMemoryPendingBatches : PendingBatches {
    private val map = mutableMapOf<String, List<Long>>()
    override fun get(key: String) = map[key]
    override fun put(key: String, ids: List<Long>) { map[key] = ids }
    override fun remove(key: String) { map.remove(key) }
}

/** Reports closed days oldest first, retaining failed batches for the next run. */
class DailyReportUploader(
    private val dao: PlayEventDao,
    private val pending: PendingBatches = InMemoryPendingBatches(),
    private val distinctId: () -> String,
    /** Stable for this install, never changed by login or logout. */
    private val deviceId: () -> String,
    private val enabled: () -> Boolean,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val platform: String = "android",
    /** Posts one event body; true only on a success response from PostHog. */
    private val send: suspend (body: JsonObject) -> Boolean,
    private val apiKey: () -> String,
    private val lock: Mutex = Mutex(),
    private val consentEpoch: () -> Int = { 0 }
) {
    /** @return how many days were reported and cleared. */
    suspend fun uploadPending(): Int = lock.withLock {
        val epoch = consentEpoch()
        if (!enabled() || apiKey().isBlank()) return 0
        val installId = deviceId()
        var done = 0
        for (date in dao.dates()) {
            val day = runCatching { LocalDate.parse(date) }.getOrNull()
            if (day == null) { dao.deleteDate(date); continue } // unreadable date, nothing to report
            if (!day.isBefore(today())) break
            val rows = dao.forDate(date)
            if (rows.isEmpty()) continue
            // New rows carry their original identity. Resolve legacy null owners once per day.
            val legacyOwner = distinctId()
            var allOk = true
            for ((id, list) in rows.groupBy { it.owner ?: legacyOwner }) {
                val key = "$id|$date"
                var left = list
                while (left.isNotEmpty()) {
                    if (!enabled() || consentEpoch() != epoch) return done
                    // A report that may already have been accepted keeps exactly its rows; anything newer is its own report.
                    val frozen = pending.get(key)
                    val batch = if (frozen != null) {
                        val frozenIds = frozen.toSet()
                        val remaining = left.filter { it.id in frozenIds }
                        if (remaining.isEmpty()) {
                            // The rows were purged, or acknowledged and deleted before the marker was cleared.
                            pending.remove(key)
                            continue
                        }
                        // Some frozen rows may be gone (purged); send the rest under the same uuid rather than wedge every later run.
                        remaining
                    } else left
                    val ids = batch.map { it.id }.sorted()
                    pending.put(key, ids)
                    val zone = runCatching { ZoneId.of(batch.first().timeZone) }.getOrDefault(ZoneId.systemDefault())
                    val body = DailyReport.batchBody(apiKey(), id, installId, date, DailyReport.properties(date, batch, platform), zone, batch = ids.joinToString(","))
                    if (!enabled() || consentEpoch() != epoch) return done
                    val ok = try { send(body) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { false }
                    if (!ok) { allOk = false; break }
                    dao.deleteBatch(ids) // a listen that finished meanwhile stays for the next report
                    pending.remove(key)
                    left = left - batch.toSet()
                }
                if (!allOk) break
            }
            if (!allOk) break // offline or rejected: keep this and later days, try again next time (same rows, same uuid)
            done++
        }
        done
    }
}
