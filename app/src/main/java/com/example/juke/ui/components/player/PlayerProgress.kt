package com.example.juke.ui.components.player

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.MusicUiState
import com.example.juke.viewmodels.MusicViewModel

@Composable
fun PlayerProgress(
    currentPosition: Long,
    uiState: MusicUiState,
    musicViewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val duration = if (uiState.duration > 0) uiState.duration else musicViewModel.playbackManager.getDuration()

    Column(modifier = modifier.fillMaxWidth()) {
        PlaybackSeekSlider(
            positionMs = currentPosition,
            durationMs = duration,
            onSeek = musicViewModel::seekTo
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatTime(currentPosition),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatTime(duration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackSeekSlider(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Called when the drag ends. A remote device seeks here only; [onSeek] then just moves the thumb. */
    onSeekEnd: (() -> Unit)? = null,
    thumbSize: androidx.compose.ui.unit.Dp = 22.dp
) {
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val haptic = rememberJukeHaptics()
    var lastBucket by remember(durationMs) { mutableIntStateOf((progress * 24).toInt()) }
    Slider(
        value = progress,
        enabled = durationMs > 0,
        onValueChange = { value ->
            val bucket = (value * 24).toInt()
            if (bucket != lastBucket) {
                haptic.tick()
                lastBucket = bucket
            }
            onSeek((value * durationMs).toLong())
        },
        onValueChangeFinished = { haptic.gestureEnd(); onSeekEnd?.invoke() },
        thumb = {
            Box(
                Modifier
                    .size(thumbSize)
                    .shadow(6.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.45f))
                    .background(
                        if (durationMs > 0) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        CircleShape
                    )
            )
        },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                modifier = Modifier.height(6.dp),
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null,
                enabled = durationMs > 0,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                )
            )
        },
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "Playback position" }
    )
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
