package com.example.juke.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.delay

@Composable
fun MiniPlayer(
    musicViewModel: MusicViewModel,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by musicViewModel.uiState.collectAsState()
    val currentTrack = uiState.currentTrack
    
    // Poll for progress updates when playing
    LaunchedEffect(uiState.isPlaying) {
        while (uiState.isPlaying) {
            musicViewModel.updateProgress()
            delay(1000)
        }
    }
    
    if (currentTrack != null) {
        var offsetX by remember { mutableFloatStateOf(0f) }
        
        Card(
            modifier = modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onExpand() }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // Swipe threshold ~50dp
                            if (offsetX < -100f) {
                                musicViewModel.skipToNext()
                            } else if (offsetX > 100f) {
                                musicViewModel.skipToPrevious()
                            }
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX += dragAmount
                        }
                    )
                },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp) // Space for progress bar
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentTrack.thumbnailUri != null) {
                            AsyncImage(
                                model = currentTrack.thumbnailUri,
                                contentDescription = currentTrack.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                painter = painterResource(com.example.juke.R.drawable.baseline_play_24),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = currentTrack.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentTrack.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    IconButton(
                        onClick = { musicViewModel.togglePlayPause() }
                    ) {
                        Icon(
                            painter = painterResource(if (uiState.isPlaying) com.example.juke.R.drawable.baseline_pause_24 else com.example.juke.R.drawable.baseline_play_24),
                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                // Progress Line
                val progress = if (uiState.duration > 0) {
                    (uiState.position.toFloat() / uiState.duration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
