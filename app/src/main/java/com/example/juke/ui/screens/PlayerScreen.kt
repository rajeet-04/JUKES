package com.example.juke.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.delay

data class LyricLine(
    val timeMs: Long,
    val text: String
)

fun parseSyncedLyrics(syncedLyrics: String): List<LyricLine> {
    val lines = mutableListOf<LyricLine>()
    val regex = """\[(\d{2}):(\d{2})\.(\d{1,3})]\s*(.*)""".toRegex()

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
    
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val isTablet = screenWidth >= 600.dp
    val isLandscape = screenWidth > screenHeight
    
    // Poll playback progress continuously (updates uiState.position via ViewModel)
    LaunchedEffect(Unit) {
        while (true) {
            musicViewModel.updateProgress()
            delay(300)
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
            if (isTablet && isLandscape) {
                TabletLandscapePlayer(
                    currentTrack = currentTrack,
                    uiState = uiState,
                    currentPosition = uiState.position,
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
                    currentPosition = uiState.position,
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
    
    // Queue Bottom Sheet - Full screen height
    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true // Disable drag-to-dismiss
            ),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxSize(), // Use full screen height
            dragHandle = null // Remove drag handle since we disabled dragging
        ) {
            QueueBottomSheetContent(
                currentTrack = currentTrack,
                queue = uiState.queue,
                queueIndex = uiState.queueIndex,
                onClose = { showQueue = false }
            )
        }
    }
}

@Composable
private fun QueueBottomSheetContent(
    currentTrack: com.example.juke.models.Track,
    queue: List<com.example.juke.models.Track>,
    queueIndex: Int,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header with close button and title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close")
            }
            Text(
                "Up Next",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            // Spacer to balance the layout
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Queue list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Current track indicator
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Playing indicator
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(com.example.juke.R.drawable.baseline_play_24),
                                contentDescription = "Now Playing",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentTrack.thumbnailUri != null) {
                                AsyncImage(
                                    model = currentTrack.thumbnailUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Now Playing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                currentTrack.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                currentTrack.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Upcoming tracks - only show tracks after current index
            val upcomingTracks = queue.drop(queueIndex + 1)
            if (upcomingTracks.isNotEmpty()) {
                item {
                    Text(
                        "Up Next",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(upcomingTracks) { track ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No upcoming tracks",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val isTablet = screenWidth >= 600.dp
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
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (showLyrics) {
                    if (currentTrack.syncedLyrics != null || currentTrack.plainLyrics != null) {
                        LyricsOverlay(currentTrack, currentPosition, musicViewModel, isTablet, true)
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.75f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No lyrics available",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close")
                }
                Text(
                    "Now Playing",
                    style = MaterialTheme.typography.titleLarge
                )
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
                
                // Queue button at bottom
                IconButton(
                    onClick = onShowQueue,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 16.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, "Queue")
                }
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close")
            }
            Text(
                "Now Playing",
                style = MaterialTheme.typography.titleMedium
            )
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
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (showLyrics) {
                if (currentTrack.syncedLyrics != null || currentTrack.plainLyrics != null) {
                    LyricsOverlay(currentTrack, currentPosition, musicViewModel, isTablet, false)
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No lyrics available",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
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
        
        // Queue button at bottom
        IconButton(
            onClick = onShowQueue,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 16.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.List, "Queue")
        }
        
        if (!isTablet) {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CustomSeekBar(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onHorizontalDrag = { change, _ ->
                        val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        onProgressChange(newProgress)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2
            // Inactive track
            drawLine(
                color = Color.Gray.copy(alpha = 0.5f),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
            // Active track
            drawLine(
                color = Color.White,
                start = Offset(0f, centerY),
                end = Offset(size.width * progress, centerY),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
            // Thumb
            val thumbX = size.width * progress
            drawCircle(
                color = Color.White,
                radius = if (isDragging) 20f else 15f,
                center = Offset(thumbX, centerY)
            )
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
        CustomSeekBar(
            progress = progress,
            onProgressChange = { newProgress ->
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
    currentPosition: Long,
    musicViewModel: MusicViewModel,
    isTablet: Boolean,
    isLandscape: Boolean
) {
    val configuration = LocalConfiguration.current
    val syncedLyrics = currentTrack.syncedLyrics
    
    // Calculate padding to center active line in the image
    val verticalPadding = if (isTablet && isLandscape) {
        // Tablet landscape: image height = screenHeight - 48.dp (24.dp top/bottom padding)
        (configuration.screenHeightDp.toFloat() / 2).dp
    } else {
        // Portrait/tablet portrait: image height = screenWidth - 32.dp (16.dp left/right padding)
        (configuration.screenWidthDp.toFloat() / 2).dp
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
    ) {
        if (syncedLyrics != null) {
            val lyricLines = remember(syncedLyrics) { parseSyncedLyrics(syncedLyrics) }
            val listState = rememberLazyListState()
            var currentLineIndex by remember { mutableIntStateOf(0) }
            
            // Calculate the center offset to position active line in the middle
            LaunchedEffect(currentPosition, lyricLines) {
                val newIndex = lyricLines.indexOfLast { it.timeMs <= currentPosition }
                if (newIndex >= 0) {
                    currentLineIndex = newIndex
                    // Scroll with center offset so active line is in the middle
                    if (lyricLines.isNotEmpty()) {
                        listState.animateScrollToItem(
                            index = newIndex,
                            scrollOffset = 0
                        )
                    }
                }
            }
            
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = verticalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                items(lyricLines.size) { index ->
                    val line = lyricLines[index]
                    val isCurrentLine = index == currentLineIndex
                    
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isCurrentLine) Color.White else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = {
                                musicViewModel.seekTo(line.timeMs)
                            }),
                        textAlign = TextAlign.Center,
                        fontSize = if (isCurrentLine) 18.sp else 16.sp,
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