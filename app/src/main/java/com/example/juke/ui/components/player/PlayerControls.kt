package com.example.juke.ui.components.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.juke.R
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.MusicUiState
import com.example.juke.viewmodels.MusicViewModel

@Composable
fun PlayerControls(
    uiState: MusicUiState,
    musicViewModel: MusicViewModel,
    isLarge: Boolean,
    modifier: Modifier = Modifier,
    // Optional override: lets parent scale all control sizes
    playButtonSize: Dp? = null,
    buttonSize: Dp? = null,
    iconSize: Dp? = null,
    smallIconSize: Dp? = null
) {
    val haptic = rememberJukeHaptics()
    val resolvedPlayButtonSize = playButtonSize ?: if (isLarge) 88.dp else 72.dp
    val resolvedButtonSize = buttonSize ?: if (isLarge) 72.dp else 56.dp
    val resolvedIconSize = iconSize ?: if (isLarge) 56.dp else 40.dp
    val resolvedSmallIconSize = smallIconSize ?: if (isLarge) 32.dp else 24.dp

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle Button
        IconButton(
            onClick = {
                haptic.toggle()
                musicViewModel.toggleShuffle()
            },
            modifier = Modifier.size(resolvedButtonSize)
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (uiState.isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.7f
                ),
                modifier = Modifier.size(resolvedSmallIconSize)
            )
        }
        IconButton(
            onClick = {
                haptic.click()
                musicViewModel.skipToPrevious()
            },
            modifier = Modifier.size(resolvedButtonSize)
        ) {
            Icon(
                painter = painterResource(R.drawable.prev_svgrepo_com),
                contentDescription = "Previous",
                modifier = Modifier.size(resolvedIconSize),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        val playButtonScale = remember { Animatable(1f) }
        LaunchedEffect(uiState.isPlaying) {
            playButtonScale.animateTo(0.85f, tween(90, easing = FastOutSlowInEasing))
            playButtonScale.animateTo(1f,    tween(150, easing = FastOutSlowInEasing))
        }

        FilledIconButton(
            onClick = {
                haptic.heavyClick()
                musicViewModel.togglePlayPause()
            },
            modifier = Modifier
                .size(resolvedPlayButtonSize)
                .scale(playButtonScale.value),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Crossfade(
                targetState = uiState.isPlaying,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                label = "playPauseIcon"
            ) { isPlaying ->
                Icon(
                    painter = painterResource(
                        if (isPlaying) R.drawable.baseline_pause_24
                        else R.drawable.baseline_play_24
                    ),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(resolvedIconSize)
                )
            }
        }

        IconButton(
            onClick = {
                haptic.click()
                musicViewModel.skipToNext()
            },
            modifier = Modifier.size(resolvedButtonSize)
        ) {
            Icon(
                painter = painterResource(R.drawable.next_svgrepo_com),
                contentDescription = "Next",
                modifier = Modifier.size(resolvedIconSize),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Repeat Button
        IconButton(
            onClick = {
                haptic.toggle()
                musicViewModel.toggleRepeat()
            },
            modifier = Modifier.size(resolvedButtonSize)
        ) {
            val (icon, tint) = when (uiState.repeatMode) {
                Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne to MaterialTheme.colorScheme.primary
                Player.REPEAT_MODE_ALL -> Icons.Default.Repeat to MaterialTheme.colorScheme.primary
                else -> Icons.Default.Repeat to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            }

            Icon(
                imageVector = icon,
                contentDescription = "Repeat",
                tint = tint,
                modifier = Modifier.size(resolvedSmallIconSize)
            )
        }
    }
}
