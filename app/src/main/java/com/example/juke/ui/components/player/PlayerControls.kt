package com.example.juke.ui.components.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.juke.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.media3.common.Player
import com.example.juke.viewmodels.MusicUiState
import com.example.juke.viewmodels.MusicViewModel

@Composable
fun PlayerControls(
    uiState: MusicUiState,
    musicViewModel: MusicViewModel,
    isLarge: Boolean,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val buttonSize = if (isLarge) 72.dp else 64.dp
    val playButtonSize = if (isLarge) 88.dp else 80.dp
    val iconSize = if (isLarge) 56.dp else 48.dp
    val playIconSize = if (isLarge) 56.dp else 48.dp

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle Button
        IconButton(
            onClick = { musicViewModel.toggleShuffle() },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (uiState.isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(28.dp)
            )
        }
        IconButton(
            onClick = { 
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                musicViewModel.skipToPrevious() 
            },
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                painter = painterResource(R.drawable.prev_svgrepo_com),
                contentDescription = "Previous",
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        FilledIconButton(
            onClick = { 
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                musicViewModel.togglePlayPause() 
            },
            modifier = Modifier.size(playButtonSize),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                painter = painterResource(if (uiState.isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_24),
                contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(playIconSize)
            )
        }

        IconButton(
            onClick = { 
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                musicViewModel.skipToNext() 
            },
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                painter = painterResource(R.drawable.next_svgrepo_com),
                contentDescription = "Next",
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Repeat Button
        IconButton(
            onClick = { musicViewModel.toggleRepeat() },
            modifier = Modifier.size(48.dp)
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
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
