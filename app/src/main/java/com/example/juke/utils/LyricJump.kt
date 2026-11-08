package com.example.juke.utils

import com.example.juke.models.Track
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.delay

/** "Play from this line": once [track] is loaded and playing, jump to [startMs]. Gives up quietly if it never loads. */
suspend fun MusicViewModel.seekWhenReady(track: Track, startMs: Long) {
    val pm = playbackManager
    repeat(80) {
        if (uiState.value.currentTrack?.uuid == track.uuid && pm.getDuration() > 0 && pm.isPlayingFlow.value) {
            seekTo(startMs); return
        }
        delay(250)
    }
}
