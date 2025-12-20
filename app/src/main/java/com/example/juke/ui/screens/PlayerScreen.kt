package com.example.juke.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.delay

data class LyricLine(
    val timeMs: Long,
    val text: String
)

fun parseSyncedLyrics(syncedLyrics: String): List<LyricLine> {
    val lines = mutableListOf<LyricLine>()
    val regex = """\[(\d{2}):(\d{2})\.(\d{1,3})\]\s*(.*)""".toRegex()

    syncedLyrics.lines().forEach { line ->
        regex.find(line)?.let { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val frac = match.groupValues[3]
            val text = match.groupValues[4]

            // Convert fractional part to milliseconds correctly depending on digits
            val millisFromFrac = when (frac.length) {
                3 -> frac.toLongOrNull() ?: 0L // already milliseconds
                2 -> (frac.toLongOrNull() ?: 0L) * 10L // hundredths -> ms
                1 -> (frac.toLongOrNull() ?: 0L) * 100L // tenths -> ms
                else -> frac.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            }

            val timeMs = (minutes * 60 * 1000) + (seconds * 1000) + millisFromFrac
            if (text.isNotBlank()) {
                lines.add(LyricLine(timeMs, text))
            }
        }
    }
    
    return lines.sortedBy { it.timeMs }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    musicViewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val uiState by musicViewModel.uiState.collectAsState()
    val currentTrack = uiState.currentTrack
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    
    // Update position more frequently for synced lyrics (every 100ms)
    LaunchedEffect(uiState.isPlaying) {
        while (uiState.isPlaying) {
            delay(100)
            currentPosition = musicViewModel.playbackManager.getCurrentPosition()
        }
    }
    
    if (currentTrack == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No track playing")
        }
        return
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, "Close")
                }
                
                Text(
                    "Now Playing",
                    style = MaterialTheme.typography.titleMedium
                )
                
                IconButton(onClick = { showQueue = !showQueue }) {
                    Icon(Icons.Default.List, "Queue")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (showQueue) {
                // Queue view
                Text(
                    "Queue",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.queue) { track ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (track.uuid == currentTrack.uuid) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (track.thumbnailUri != null) {
                                        AsyncImage(
                                            model = track.thumbnailUri,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.PlayArrow, null)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        track.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Player view
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Album artwork with overlay lyrics
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showLyrics = !showLyrics },
                        contentAlignment = Alignment.Center
                    ) {
                        // Background image
                        if (currentTrack.thumbnailUri != null) {
                            AsyncImage(
                                model = currentTrack.thumbnailUri,
                                contentDescription = currentTrack.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(120.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Lyrics overlay
                        if (showLyrics && (currentTrack.syncedLyrics != null || currentTrack.plainLyrics != null)) {
                            val syncedLyrics = currentTrack.syncedLyrics
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.75f))
                            ) {
                                if (syncedLyrics != null) {
                                    // Synced lyrics with auto-scroll
                                    val lyricLines = remember(syncedLyrics) { parseSyncedLyrics(syncedLyrics) }
                                    val listState = rememberLazyListState()
                                    var currentLineIndex by remember { mutableStateOf(0) }
                                    
                                    // Update current line based on position and auto-scroll
                                    LaunchedEffect(currentPosition) {
                                        val newIndex = lyricLines.indexOfLast { it.timeMs <= currentPosition }
                                        if (newIndex >= 0 && newIndex != currentLineIndex) {
                                            currentLineIndex = newIndex
                                            listState.animateScrollToItem(index = newIndex)
                                        }
                                    }
                                    
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        items(lyricLines.size) { index ->
                                            val line = lyricLines[index]
                                            val isCurrentLine = index == currentLineIndex
                                            
                                            Text(
                                                text = line.text,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = if (isCurrentLine) Color.White else Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 6.dp),
                                                textAlign = TextAlign.Center,
                                                fontWeight = if (isCurrentLine)
                                                    androidx.compose.ui.text.font.FontWeight.Bold
                                                else
                                                    androidx.compose.ui.text.font.FontWeight.Normal
                                            )
                                        }
                                    }
                                } else {
                                    // Plain lyrics
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp)
                                            .verticalScroll(rememberScrollState()),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            currentTrack.plainLyrics ?: "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Track info
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = currentTrack.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Progress bar
                    // Prefer duration from UI state, but fall back to the player's reported duration when 0
                    val duration = if (uiState.duration > 0) uiState.duration else musicViewModel.playbackManager.getDuration()
                    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = progress,
                            onValueChange = { newProgress ->
                                val newPosition = (newProgress * duration).toLong()
                                musicViewModel.seekTo(newPosition)
                                currentPosition = newPosition
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatTime(currentPosition),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatTime(duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Playback controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { musicViewModel.skipToPrevious() },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Previous",
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        
                        FilledIconButton(
                            onClick = { musicViewModel.togglePlayPause() },
                            modifier = Modifier.size(80.dp)
                        ) {
                            Icon(
                                if (uiState.isPlaying) Icons.Default.PlayArrow else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        
                        IconButton(
                            onClick = { musicViewModel.skipToNext() },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = "Next",
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
