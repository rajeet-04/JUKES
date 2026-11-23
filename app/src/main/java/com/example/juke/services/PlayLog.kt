package com.example.juke.services

import com.example.juke.database.PlayEventEntity
import java.time.Instant
import java.time.ZoneId

/**
 * Times what the listener actually heard. Call [start] when a song begins, [setPlaying] when the
 * player starts or stops, and [end] when it is replaced or playback stops. Time only counts while the
 * player is playing, so pauses do not inflate it. Call from one thread (the main thread).
 *
 * Each finished listen goes to [sink] as one row; listens shorter than [MIN_LISTEN_MS] are dropped.
 */
class PlayLog(
    private val sink: (PlayEventEntity) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) {
    private class Current(
        val trackUuid: String,
        val songKey: String,
        val durationMs: Long,
        val startedAt: Long,
        val source: String?
    ) {
        var listenedMs = 0L
        var playingSince: Long? = null
    }

    private var current: Current? = null

    fun start(trackUuid: String, songKey: String, durationMs: Long, playingNow: Boolean, source: String? = null) {
        val now = clock()
        val c = Current(trackUuid, songKey, durationMs, now, source)
        if (playingNow) c.playingSince = now
        current = c
    }

    fun setPlaying(isPlaying: Boolean) {
        val c = current ?: return
        val now = clock()
        val since = c.playingSince
        if (isPlaying && since == null) {
            c.playingSince = now
        } else if (!isPlaying && since != null) {
            c.listenedMs += (now - since).coerceAtLeast(0)
            c.playingSince = null
        }
    }

    /** Finish the current listen. [completed] when it ran to its end, otherwise it counts as skipped. */
    fun end(completed: Boolean) {
        val c = current ?: return
        current = null
        setPlayingOn(c)
        val listened = if (c.durationMs > 0) minOf(c.listenedMs, c.durationMs) else c.listenedMs
        if (listened < MIN_LISTEN_MS) return
        val z = zone()
        sink(
            PlayEventEntity(
                trackUuid = c.trackUuid,
                songKey = c.songKey,
                startedAt = c.startedAt,
                localDate = Instant.ofEpochMilli(c.startedAt).atZone(z).toLocalDate().toString(),
                timeZone = z.id,
                listenedMs = listened,
                durationMs = c.durationMs,
                completed = completed,
                skipped = !completed,
                source = c.source
            )
        )
    }

    /** Fold in time still running when the listen ends. */
    private fun setPlayingOn(c: Current) {
        val since = c.playingSince ?: return
        c.listenedMs += (clock() - since).coerceAtLeast(0)
        c.playingSince = null
    }

    companion object {
        const val MIN_LISTEN_MS = 1_000L
    }
}
