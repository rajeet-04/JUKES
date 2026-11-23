package com.example.juke.analytics

import com.example.juke.database.PlayEventDao
import com.example.juke.database.PlayEventEntity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private class FakeDao(rows: List<PlayEventEntity>) : PlayEventDao {
    val rows = rows.toMutableList()
    override suspend fun insert(event: PlayEventEntity) { rows += event }
    override suspend fun dates() = rows.map { it.localDate }.distinct().sorted()
    override suspend fun forDate(date: String) = rows.filter { it.localDate == date }
    override suspend fun deleteDate(date: String): Int { val n = rows.count { it.localDate == date }; rows.removeAll { it.localDate == date }; return n }
    override suspend fun deleteAll(): Int { val n = rows.size; rows.clear(); return n }
}

class DailyReportTest {
    // 2023-11-14 22:13 UTC
    private val t0 = 1_700_000_000_000L

    private fun row(date: String, key: String = "t - a", listened: Long = 60_000, skipped: Boolean = false, at: Long = t0, owner: String? = null) =
        PlayEventEntity(
            trackUuid = "u-$key", songKey = key, startedAt = at, localDate = date, timeZone = "UTC",
            listenedMs = listened, durationMs = 200_000, completed = !skipped, skipped = skipped,
            title = key.substringBefore(" - "), artist = key.substringAfter(" - "), spotifyId = "sp-$key", owner = owner
        )

    private fun uploader(dao: PlayEventDao, today: String, sent: MutableList<JsonObject>, ok: () -> Boolean = { true }) =
        DailyReportUploader(
            dao = dao, distinctId = { "me@example.com" }, deviceId = { "dev1" }, enabled = { true },
            today = { LocalDate.parse(today) }, apiKey = { "key" },
            send = { sent += it; ok() }
        )

    @Test fun reportsClosedDaysAndClearsThemOnSuccess() = runBlocking {
        val dao = FakeDao(listOf(row("2023-11-13"), row("2023-11-14"), row("2023-11-15")))
        val sent = mutableListOf<JsonObject>()
        val n = uploader(dao, today = "2023-11-15", sent = sent).uploadPending()
        assertEquals(2, n)
        assertEquals(listOf("2023-11-15"), dao.dates()) // today is kept
    }

    @Test fun failureKeepsEverythingForNextTime() = runBlocking {
        val dao = FakeDao(listOf(row("2023-11-13"), row("2023-11-14")))
        val sent = mutableListOf<JsonObject>()
        val n = uploader(dao, today = "2023-11-20", sent = sent, ok = { false }).uploadPending()
        assertEquals(0, n)
        assertEquals(listOf("2023-11-13", "2023-11-14"), dao.dates())
        assertEquals(1, sent.size) // stops at the first failure
    }

    @Test fun eachOwnersListeningIsReportedUnderThemAndAnonymousUnderThisInstall() = runBlocking {
        val dao = FakeDao(listOf(row("2023-11-13", owner = "a@x.com"), row("2023-11-13", owner = "b@x.com"), row("2023-11-13")))
        val sent = mutableListOf<JsonObject>()
        uploader(dao, today = "2023-11-20", sent = sent).uploadPending()
        val ids = sent.map { it["batch"]!!.jsonArray.single().jsonObject["distinct_id"]!!.jsonPrimitive.content }.toSet()
        assertEquals(setOf("a@x.com", "b@x.com", "me@example.com"), ids)
        assertTrue(dao.rows.isEmpty())
    }

    @Test fun ifOneOwnerFailsTheDayIsKept() = runBlocking {
        val dao = FakeDao(listOf(row("2023-11-13", owner = "a@x.com"), row("2023-11-13", owner = "b@x.com")))
        var calls = 0
        uploader(dao, today = "2023-11-20", sent = mutableListOf(), ok = { ++calls == 1 }).uploadPending()
        assertEquals(2, dao.rows.size)
    }

    @Test fun sameDayAndUserGiveTheSameEventUuid() {
        assertEquals(DailyReport.eventUuid("a@b.c", "2023-11-14", "d1"), DailyReport.eventUuid("a@b.c", "2023-11-14", "d1"))
        assertNotEquals(DailyReport.eventUuid("a@b.c", "2023-11-14", "d1"), DailyReport.eventUuid("a@b.c", "2023-11-15", "d1"))
        assertNotEquals(DailyReport.eventUuid("a@b.c", "2023-11-14", "d1"), DailyReport.eventUuid("x@b.c", "2023-11-14", "d1"))
        // a phone and a desktop reporting the same day under one email must not share a uuid
        assertNotEquals(DailyReport.eventUuid("a@b.c", "2023-11-14", "phone"), DailyReport.eventUuid("a@b.c", "2023-11-14", "desktop"))
    }

    @Test fun reportSumsTracksAndHours() {
        val rows = listOf(
            row("2023-11-14", "x - a", 60_000),
            row("2023-11-14", "x - a", 30_000, skipped = true),
            row("2023-11-14", "y - b", 10_000)
        )
        val p = DailyReport.properties("2023-11-14", rows, "android")
        assertEquals(100_000L, p["total_listened_ms"]!!.jsonPrimitive.long)
        assertEquals(3, p["plays"]!!.jsonPrimitive.int)
        assertEquals(1, p["skips"]!!.jsonPrimitive.int)
        val tracks = p["tracks"]!!.jsonArray
        assertEquals(2, tracks.size)
        val x = tracks.first { it.jsonObject["song_key"]!!.jsonPrimitive.content == "x - a" }.jsonObject
        assertEquals(2, x["plays"]!!.jsonPrimitive.int)
        assertEquals(90_000L, x["listened_ms"]!!.jsonPrimitive.long)
        val hours = p["listened_ms_by_hour"] as JsonArray
        assertEquals(24, hours.size)
        assertEquals(100_000L, p["listened_ms_by_source"]!!.jsonObject["unknown"]!!.jsonPrimitive.long)
        assertEquals(100_000L, hours[22].jsonPrimitive.long) // t0 is 22:13 UTC
    }

    @Test fun batchBodyCarriesTheIdentityAndTimestamp() {
        val props = DailyReport.properties("2023-11-14", listOf(row("2023-11-14")), "android")
        val body = DailyReport.batchBody("k", "me@example.com", "dev1", "2023-11-14", props, java.time.ZoneId.of("UTC"))
        val ev = body["batch"]!!.jsonArray.single().jsonObject
        assertEquals("daily_listening_report", ev["event"]!!.jsonPrimitive.content)
        assertEquals("me@example.com", ev["distinct_id"]!!.jsonPrimitive.content)
        assertEquals("2023-11-14T12:00:00Z", ev["timestamp"]!!.jsonPrimitive.content)
        assertTrue(ev["uuid"]!!.jsonPrimitive.content.isNotBlank())
    }
}
