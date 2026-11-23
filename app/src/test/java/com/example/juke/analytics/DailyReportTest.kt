package com.example.juke.analytics

import com.example.juke.database.PlayEventDao
import com.example.juke.database.PlayEventEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
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
    private var nextId = 1L
    val rows = rows.map { it.copy(id = nextId++) }.toMutableList()
    var beforeDelete: () -> Unit = {}
    override suspend fun insert(event: PlayEventEntity) { rows += event.copy(id = nextId++) }
    override suspend fun deleteOwner(owner: String): Int { val n = rows.count { it.owner == owner }; rows.removeAll { it.owner == owner }; return n }
    override suspend fun deleteIds(ids: List<Long>): Int { beforeDelete(); val n = rows.count { it.id in ids }; rows.removeAll { it.id in ids }; return n }
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

    @Test fun anonymousAndSignedInListeningOfOneDayGoOutAsOneReport() = runBlocking {
        // distinctId() is me@example.com: the anonymous rows resolve to the same person as the signed-in ones
        val dao = FakeDao(listOf(row("2023-11-13", "a - x"), row("2023-11-13", "b - x", owner = "me@example.com")))
        val sent = mutableListOf<JsonObject>()
        uploader(dao, today = "2023-11-20", sent = sent).uploadPending()
        assertEquals(1, sent.size)
        assertTrue(dao.rows.isEmpty())
    }

    @Test fun aListenThatFinishesWhileTheDayIsBeingClearedIsKeptAndReportedLater() = runBlocking {
        val dao = FakeDao(listOf(row("2023-11-13", "a - x")))
        var late = true
        dao.beforeDelete = { if (late) { late = false; kotlinx.coroutines.runBlocking { dao.insert(row("2023-11-13", "late - x")) } } }
        val sent = mutableListOf<JsonObject>()
        val up = uploader(dao, today = "2023-11-20", sent = sent)
        up.uploadPending()
        assertEquals(listOf("late - x"), dao.rows.map { it.songKey }) // not deleted unreported
        up.uploadPending()
        assertTrue(dao.rows.isEmpty())
        val uuids = sent.map { it["batch"]!!.jsonArray.single().jsonObject["uuid"]!!.jsonPrimitive.content }
        assertEquals(2, uuids.size)
        assertNotEquals(uuids[0], uuids[1]) // the late report is not dropped as a duplicate of the first
    }

    @Test fun aRetryKeepsTheFrozenRowsAndAnyLateListenGoesOutSeparately() = runBlocking {
        // the first report may have been accepted with its response lost: the retry must repeat it exactly
        // (same rows, same uuid) and report the late listen as its own event, so nothing is lost or counted twice
        val dao = FakeDao(listOf(row("2023-11-13", "a - x")))
        val sent = mutableListOf<JsonObject>()
        var ok = false
        val up = uploader(dao, today = "2023-11-20", sent = sent, ok = { ok })
        up.uploadPending()
        dao.insert(row("2023-11-13", "late - x"))
        ok = true
        up.uploadPending()
        val uuids = sent.map { it["batch"]!!.jsonArray.single().jsonObject["uuid"]!!.jsonPrimitive.content }
        assertEquals(3, uuids.size) // failed attempt, the same report again, then the late listen alone
        assertEquals(uuids[0], uuids[1])
        assertNotEquals(uuids[1], uuids[2])
        assertEquals(1, sent[1]["batch"]!!.jsonArray.single().jsonObject["properties"]!!.jsonObject["plays"]!!.jsonPrimitive.content.toInt())
        assertTrue(dao.rows.isEmpty())
    }

    @Test fun aFrozenBatchWithSomeRowsGoneStillUploadsTheRest() = runBlocking {
        val dao = FakeDao(listOf(row("2023-11-13", "a - x", owner = "a@x.com"), row("2023-11-13", "b - x", owner = "a@x.com")))
        val sent = mutableListOf<JsonObject>()
        var ok = false
        val pending = InMemoryPendingBatches()
        val up = DailyReportUploader(
            dao = dao, pending = pending, distinctId = { "me@example.com" }, deviceId = { "dev1" }, enabled = { true },
            today = { LocalDate.parse("2023-11-20") }, apiKey = { "key" }, send = { sent += it; ok }
        )
        up.uploadPending() // fails: both rows are now frozen as one batch
        dao.deleteIds(listOf(dao.rows.first().id)) // one of them is purged meanwhile
        ok = true
        up.uploadPending() // must not throw or wedge
        assertEquals(2, sent.size)
        assertTrue(dao.rows.isEmpty())
    }

    @Test fun aRetryOfTheSameRowsKeepsTheSameUuid() = runBlocking {
        val dao = FakeDao(listOf(row("2023-11-13")))
        val sent = mutableListOf<JsonObject>()
        var ok = false
        val up = uploader(dao, today = "2023-11-20", sent = sent, ok = { ok })
        up.uploadPending(); ok = true; up.uploadPending()
        val uuids = sent.map { it["batch"]!!.jsonArray.single().jsonObject["uuid"]!!.jsonPrimitive.content }
        assertEquals(2, uuids.size)
        assertEquals(uuids[0], uuids[1])
    }

    @Test fun restartAfterAnAcknowledgementRetriesTheFrozenRowsBeforeLateRows() = runBlocking {
        val day = "2023-11-13"
        val dao = FakeDao(listOf(row(day)))
        val pending = InMemoryPendingBatches()
        val sent = mutableListOf<JsonObject>()
        fun newUploader() = DailyReportUploader(
            dao = dao, pending = pending, distinctId = { "me@example.com" }, deviceId = { "dev1" },
            enabled = { true }, today = { LocalDate.parse("2023-11-20") }, apiKey = { "key" },
            send = { sent += it; true }
        )
        dao.beforeDelete = { error("Stopped after acknowledgement") }
        assertTrue(runCatching { newUploader().uploadPending() }.isFailure)
        dao.beforeDelete = {}
        dao.insert(row(day, "late - x"))
        newUploader().uploadPending()
        assertEquals(3, sent.size)
        assertEquals(sent[0], sent[1])
        assertNotEquals(sent[1], sent[2])
        assertTrue(dao.rows.isEmpty())
    }

    @Test fun restartAfterRowDeletionSendsOnlyTheLateRows() = runBlocking {
        val day = "2023-11-13"
        val dao = FakeDao(listOf(row(day)))
        val pending = InMemoryPendingBatches()
        val sent = mutableListOf<JsonObject>()
        fun newUploader(store: PendingBatches) = DailyReportUploader(
            dao = dao, pending = store, distinctId = { "me@example.com" }, deviceId = { "dev1" },
            enabled = { true }, today = { LocalDate.parse("2023-11-20") }, apiKey = { "key" },
            send = { sent += it; true }
        )
        val stopped = object : PendingBatches by pending {
            override fun remove(key: String) = error("Stopped after deleting acknowledged rows")
        }
        assertTrue(runCatching { newUploader(stopped).uploadPending() }.isFailure)
        assertTrue(dao.rows.isEmpty())
        dao.insert(row(day, "late - x"))
        newUploader(pending).uploadPending()
        assertEquals(2, sent.size)
        assertNotEquals(sent[0], sent[1])
        assertTrue(dao.rows.isEmpty())
    }

    @Test fun uploadersWithTheSameLockDoNotReadOverlappingBatches() = runBlocking {
        val day = "2023-11-13"
        val dao = FakeDao(listOf(row(day)))
        val pending = InMemoryPendingBatches()
        val lock = Mutex()
        val release = CompletableDeferred<Unit>()
        val sent = mutableListOf<JsonObject>()
        fun newUploader() = DailyReportUploader(
            dao = dao, pending = pending, lock = lock, distinctId = { "me@example.com" }, deviceId = { "dev1" },
            enabled = { true }, today = { LocalDate.parse("2023-11-20") }, apiKey = { "key" },
            send = { sent += it; release.await(); true }
        )
        val first = async(start = CoroutineStart.UNDISPATCHED) { newUploader().uploadPending() }
        dao.insert(row(day, "late - x"))
        val second = async(start = CoroutineStart.UNDISPATCHED) { newUploader().uploadPending() }
        try {
            assertEquals(1, sent.size)
        } finally {
            release.complete(Unit)
        }
        assertEquals(1, first.await())
        assertEquals(1, second.await())
        assertEquals(listOf(1, 1), sent.map {
            it["batch"]!!.jsonArray.single().jsonObject["properties"]!!.jsonObject["plays"]!!.jsonPrimitive.int
        })
    }

    @Test fun anAnonymousListenKeepsItsInstallIdWhateverAccountIsActiveAtUpload() = runBlocking {
        // listened anonymously as install-1, later signed in as someone else: it must not move to them
        val dao = FakeDao(listOf(row("2023-11-13", "a - x", owner = "install-1"), row("2023-11-13", "b - x", owner = "me@example.com")))
        val sent = mutableListOf<JsonObject>()
        uploader(dao, today = "2023-11-20", sent = sent).uploadPending()
        val ids = sent.map { it["batch"]!!.jsonArray.single().jsonObject["distinct_id"]!!.jsonPrimitive.content }.toSet()
        assertEquals(setOf("install-1", "me@example.com"), ids)
    }

    @Test fun nothingMoreIsSentOnceConsentIsWithdrawnMidRun() = runBlocking {
        val dao = FakeDao(listOf(row("2023-11-13", owner = "a@x.com"), row("2023-11-13", owner = "b@x.com")))
        val sent = mutableListOf<JsonObject>()
        var on = true
        DailyReportUploader(
            dao = dao, distinctId = { "me@example.com" }, deviceId = { "dev1" }, enabled = { on },
            today = { LocalDate.parse("2023-11-20") }, apiKey = { "key" },
            send = { sent += it; on = false; true } // the user opts out while the first report is in flight
        ).uploadPending()
        assertEquals(1, sent.size)
        assertEquals(1, dao.rows.size) // the report that went out is cleared, the other waits
    }

    @Test fun reEnablingConsentDoesNotResumeAnOldUploadRun() = runBlocking {
        val dao = FakeDao(listOf(row("2023-11-13", owner = "a@x.com"), row("2023-11-13", owner = "b@x.com")))
        var epoch = 0
        val sent = mutableListOf<JsonObject>()
        DailyReportUploader(
            dao = dao, distinctId = { "me@example.com" }, deviceId = { "dev1" },
            enabled = { true }, consentEpoch = { epoch },
            today = { LocalDate.parse("2023-11-20") }, apiKey = { "key" },
            send = { sent += it; epoch++; true }
        ).uploadPending()
        assertEquals(1, sent.size)
    }

    @Test fun ifOneOwnerFailsTheDayIsKept() = runBlocking {
        val dao = FakeDao(listOf(row("2023-11-13", owner = "a@x.com"), row("2023-11-13", owner = "b@x.com")))
        var calls = 0
        uploader(dao, today = "2023-11-20", sent = mutableListOf(), ok = { ++calls == 1 }).uploadPending()
        assertEquals(1, dao.rows.size)
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
