package com.example.juke.services

import com.example.juke.database.PlayEventEntity
import com.example.juke.models.Track
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
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    /** The account email listening right now, or null; read when the listen starts. */
    private val owner: () -> String? = { null },
    /** Checked when a listen starts: one that begins while this is false is never recorded, even if it turns true before the song ends. */
    private val enabled: () -> Boolean = { true }
) {
    private class Current(val track: Track, val startedAt: Long, val source: String?, val owner: String?) {
        val durationMs = track.durationSec * 1000L
        var listenedMs = 0L
        var playingSince: Long? = null
    }

    private var current: Current? = null

    fun start(track: Track, playingNow: Boolean, source: String? = null) {
        if (!enabled()) { current = null; return }
        val now = clock()
        val c = Current(track, now, source, owner())
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

    /**
     * Finish the current listen. [completed] when it ran to its end, otherwise it counts as skipped.
     * @return the time actually listened in ms, or null when nothing was playing.
     */
    fun end(completed: Boolean): Long? {
        val c = current ?: return null
        current = null
        setPlayingOn(c)
        val listened = if (c.durationMs > 0) minOf(c.listenedMs, c.durationMs) else c.listenedMs
        if (listened < MIN_LISTEN_MS) return listened
        val z = zone()
        sink(
            PlayEventEntity(
                trackUuid = c.track.uuid,
                songKey = "${c.track.title} - ${c.track.artist}",
                startedAt = c.startedAt,
                localDate = Instant.ofEpochMilli(c.startedAt).atZone(z).toLocalDate().toString(),
                timeZone = z.id,
                listenedMs = listened,
                durationMs = c.durationMs,
                completed = completed,
                skipped = !completed,
                source = c.source,
                title = c.track.title,
                artist = c.track.artist,
                spotifyId = c.track.spotifyId,
                albumSpotifyId = c.track.albumSpotifyId,
                artistSpotifyIds = c.track.artistSpotifyIds,
                ytVideoId = c.track.ytVideoId,
                owner = c.owner
            )
        )
        return listened
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
