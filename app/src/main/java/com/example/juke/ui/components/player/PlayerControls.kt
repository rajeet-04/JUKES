package com.example.juke.ui.components.player

import com.example.juke.ui.icons.JukeIcons

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.juke.ui.theme.GlassLevel
import com.example.juke.ui.theme.glassPane
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
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle Button
        IconButton(
            onClick = {
                haptic.toggle()
                musicViewModel.toggleShuffle()
            },
            modifier = Modifier.size(resolvedButtonSize).semantics {
                stateDescription = if (uiState.isShuffleEnabled) "On" else "Off"
                toggleableState = if (uiState.isShuffleEnabled) ToggleableState.On else ToggleableState.Off
            }
        ) {
            Icon(
                imageVector = JukeIcons.Shuffle,
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
                imageVector = JukeIcons.Previous,
                contentDescription = "Previous",
                modifier = Modifier.size(resolvedIconSize),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        val playButtonScale = remember { Animatable(1f) }
        LaunchedEffect(uiState.isPlaying) {
            playButtonScale.animateTo(0.85f, tween(90, easing = FastOutSlowInEasing))
            playButtonScale.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
        }

        val accent = MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .size(resolvedPlayButtonSize)
                .scale(playButtonScale.value)
                .glassPane(CircleShape, GlassLevel.Thick, accent)
                .background(accent.copy(alpha = 0.9f))
                .clickable(role = Role.Button) {
                    haptic.heavyClick()
                    musicViewModel.togglePlayPause()
                },
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = uiState.isPlaying,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                label = "playPauseIcon"
            ) { isPlaying ->
                Icon(
                    imageVector = if (isPlaying) JukeIcons.Pause else JukeIcons.Play,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
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
                imageVector = JukeIcons.Next,
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
            modifier = Modifier.size(resolvedButtonSize).semantics {
                stateDescription = when (uiState.repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repeat one"
                    Player.REPEAT_MODE_ALL -> "Repeat all"
                    else -> "Off"
                }
            }
        ) {
            val (icon, tint) = when (uiState.repeatMode) {
                Player.REPEAT_MODE_ONE -> JukeIcons.RepeatOne to MaterialTheme.colorScheme.primary
                Player.REPEAT_MODE_ALL -> JukeIcons.Repeat to MaterialTheme.colorScheme.primary
                else -> JukeIcons.Repeat to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
