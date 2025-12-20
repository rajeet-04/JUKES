package com.example.juke.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
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
    // Intercept back gesture/button to dismiss instead of exiting app
    BackHandler(onBack = onDismiss)
    
    val uiState by musicViewModel.uiState.collectAsState()
    val currentTrack = uiState.currentTrack
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val isTablet = screenWidth >= 600.dp
    val isLandscape = screenWidth > screenHeight
    
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
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxSize(),
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
        if (showQueue) {
            QueueView(
                currentTrack = currentTrack,
                queue = uiState.queue,
                onClose = { showQueue = false },
                isTablet = isTablet
            )
        } else if (isTablet && isLandscape) {
            TabletLandscapePlayer(
                currentTrack = currentTrack,
                uiState = uiState,
                currentPosition = currentPosition,
                showLyrics = showLyrics,
                onToggleLyrics = { showLyrics = !showLyrics },
                onDismiss = onDismiss,
                onShowQueue = { showQueue = true },
                musicViewModel = musicViewModel
            )
        } else {
            PortraitPlayer(
                currentTrack = currentTrack,
                uiState = uiState,
                currentPosition = currentPosition,
                showLyrics = showLyrics,
                onToggleLyrics = { showLyrics = !showLyrics },
                onDismiss = onDismiss,
                onShowQueue = { showQueue = true },
                musicViewModel = musicViewModel,
                isTablet = isTablet
            )
        }
        }
    }
}

@Composable
private fun QueueView(
    currentTrack: com.example.juke.models.Track,
    queue: List<com.example.juke.models.Track>,
    onClose: () -> Unit,
    isTablet: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, "Back")
            }
            Text(
                "Queue",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.width(48.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(queue) { track ->
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
                                Icon(
                                    painter = painterResource(com.example.juke.R.drawable.baseline_play_24),
                                    contentDescription = null
                                )
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
    }
}

@Composable
private fun TabletLandscapePlayer(
    currentTrack: com.example.juke.models.Track,
    uiState: com.example.juke.viewmodels.MusicUiState,
    currentPosition: Long,
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    onDismiss: () -> Unit,
    onShowQueue: () -> Unit,
    musicViewModel: MusicViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // Left side: Artwork
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onToggleLyrics() },
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
                        modifier = Modifier.size(120.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (showLyrics && (currentTrack.syncedLyrics != null || currentTrack.plainLyrics != null)) {
                    LyricsOverlay(currentTrack, currentPosition)
                }
            }
        }
        
        // Right side: Controls
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
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
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onShowQueue) {
                    Icon(Icons.Default.List, "Queue")
                }
            }
            
            // Track info and controls
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentTrack.title,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = currentTrack.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Progress bar
                PlayerProgress(
                    currentPosition = currentPosition,
                    uiState = uiState,
                    musicViewModel = musicViewModel
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Playback controls
                PlaybackControls(
                    uiState = uiState,
                    musicViewModel = musicViewModel,
                    isLarge = true
                )
            }
        }
    }
}

@Composable
private fun PortraitPlayer(
    currentTrack: com.example.juke.models.Track,
    uiState: com.example.juke.viewmodels.MusicUiState,
    currentPosition: Long,
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    onDismiss: () -> Unit,
    onShowQueue: () -> Unit,
    musicViewModel: MusicViewModel,
    isTablet: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isTablet) Modifier
                else Modifier.verticalScroll(rememberScrollState())
            )
            .padding(16.dp)
    ) {
        // Header
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
            IconButton(onClick = onShowQueue) {
                Icon(Icons.Default.List, "Queue")
            }
        }
        
        Spacer(modifier = Modifier.height(if (isTablet) 24.dp else 16.dp))
        
        // Artwork
        val artworkModifier = if (isTablet) {
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .aspectRatio(1f)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        }
        
        Box(
            modifier = artworkModifier
                .clip(RoundedCornerShape(16.dp))
                .clickable { onToggleLyrics() },
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
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (showLyrics && (currentTrack.syncedLyrics != null || currentTrack.plainLyrics != null)) {
                LyricsOverlay(currentTrack, currentPosition)
            }
        }
        
        Spacer(modifier = Modifier.height(if (isTablet) 32.dp else 24.dp))
        
        // Track info
        Text(
            text = currentTrack.title,
            style = if (isTablet) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = currentTrack.artist,
            style = if (isTablet) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(if (isTablet) 32.dp else 24.dp))
        
        // Progress bar
        PlayerProgress(
            currentPosition = currentPosition,
            uiState = uiState,
            musicViewModel = musicViewModel
        )
        
        Spacer(modifier = Modifier.height(if (isTablet) 32.dp else 24.dp))
        
        // Playback controls
        PlaybackControls(
            uiState = uiState,
            musicViewModel = musicViewModel,
            isLarge = isTablet
        )
        
        if (!isTablet) {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PlayerProgress(
    currentPosition: Long,
    uiState: com.example.juke.viewmodels.MusicUiState,
    musicViewModel: MusicViewModel
) {
    val duration = if (uiState.duration > 0) uiState.duration else musicViewModel.playbackManager.getDuration()
    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = progress,
            onValueChange = { newProgress ->
                val newPosition = (newProgress * duration).toLong()
                musicViewModel.seekTo(newPosition)
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
}

@Composable
private fun PlaybackControls(
    uiState: com.example.juke.viewmodels.MusicUiState,
    musicViewModel: MusicViewModel,
    isLarge: Boolean
) {
    val buttonSize = if (isLarge) 72.dp else 64.dp
    val playButtonSize = if (isLarge) 88.dp else 80.dp
    val iconSize = if (isLarge) 56.dp else 48.dp
    val playIconSize = if (isLarge) 56.dp else 48.dp
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { musicViewModel.skipToPrevious() },
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                painter = painterResource(com.example.juke.R.drawable.prev_svgrepo_com),
                contentDescription = "Previous",
                modifier = Modifier.size(iconSize)
            )
        }
        
        FilledIconButton(
            onClick = { musicViewModel.togglePlayPause() },
            modifier = Modifier.size(playButtonSize)
        ) {
            Icon(
                painter = painterResource(if (uiState.isPlaying) com.example.juke.R.drawable.baseline_pause_24 else com.example.juke.R.drawable.baseline_play_24),
                contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(playIconSize)
            )
        }
        
        IconButton(
            onClick = { musicViewModel.skipToNext() },
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                painter = painterResource(com.example.juke.R.drawable.next_svgrepo_com),
                contentDescription = "Next",
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun LyricsOverlay(
    currentTrack: com.example.juke.models.Track,
    currentPosition: Long
) {
    val syncedLyrics = currentTrack.syncedLyrics
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
    ) {
        if (syncedLyrics != null) {
            val lyricLines = remember(syncedLyrics) { parseSyncedLyrics(syncedLyrics) }
            val listState = rememberLazyListState()
            var currentLineIndex by remember { mutableStateOf(0) }
            
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

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
