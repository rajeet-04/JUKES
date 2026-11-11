package com.example.juke.utils

import com.example.juke.models.Track
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * "Play from this line".
 * - [track] is already the current song: just seek (and resume). Restarting it would land after the seek and undo it.
 * - Another song: [start] it with the position, so the player opens it there.
 *   As a fallback (a stream that resolves late can open at 0:00), once this song is really the one loaded
 *   (its length matches, so the previous song still playing can't fool it) and it sits well before the line, seek.
 */
suspend fun MusicViewModel.playFromLine(track: Track, startMs: Long, start: (startMs: Long) -> Unit) {
    val pm = playbackManager
    if (uiState.value.currentTrack?.uuid == track.uuid && pm.getDuration() > 0) {
        seekTo(startMs)
        if (!pm.isPlayingFlow.value) togglePlayPause()
        return
    }
    start(startMs)
    val wantMs = track.durationSec * 1000L
    repeat(80) {
        delay(250.milliseconds)
        val dur = pm.getDuration()
        val loaded = uiState.value.currentTrack?.uuid == track.uuid && dur > 0 && pm.isPlayingFlow.value &&
            (wantMs <= 0 || abs(dur - wantMs) < 5000)
        if (loaded) {
            if (pm.getCurrentPosition() < startMs - 3000) seekTo(startMs)
            return
        }
    }
}
