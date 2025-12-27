package com.example.juke.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.delay
import java.util.Locale

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    val sleepTimerRemaining by musicViewModel.sleepTimerRemaining.collectAsState()
    
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
                    onShowSleepTimer = { showSleepTimerDialog = true },
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
                    onShowSleepTimer = { showSleepTimerDialog = true },
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
                uiState = uiState,
                onClose = { showQueue = false },
                onMoveTrack = { fromIndex, toIndex -> musicViewModel.moveInQueue(fromIndex, toIndex) },
                onRemoveTrack = { trackId -> musicViewModel.removeFromQueue(trackId) },
                onPlayTrack = { track -> musicViewModel.playTrackFromQueue(track) }
            )
        }
    }
    
    // Sleep Timer Dialog
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            currentTimerRemaining = sleepTimerRemaining,
            onDismiss = { showSleepTimerDialog = false },
            onSetTimer = { minutes ->
                if (minutes > 0) {
                    musicViewModel.startSleepTimer(minutes)
                } else {
                    musicViewModel.cancelSleepTimer()
                }
                showSleepTimerDialog = false
            },
            onCancelTimer = {
                musicViewModel.cancelSleepTimer()
                showSleepTimerDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueBottomSheetContent(
    currentTrack: com.example.juke.models.Track,
    queue: List<com.example.juke.models.Track>,
    queueIndex: Int,
    uiState: com.example.juke.viewmodels.MusicUiState,
    onClose: () -> Unit,
    onMoveTrack: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemoveTrack: (trackId: String) -> Unit,
    onPlayTrack: (track: com.example.juke.models.Track) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
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

        // Queue list with swipe-to-remove
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Current track indicator
            item(key = "now_playing_card") {
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
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee()
                            )
                            Text(
                                currentTrack.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                    }
                }
            }

            // Upcoming tracks - only show tracks after current index
            val upcomingTracks = queue.drop(queueIndex + 1)
            if (upcomingTracks.isNotEmpty()) {
                item(key = "queue_header_text") {
                    Text(
                        "Up Next",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                itemsIndexed(
                    items = upcomingTracks,
                    key = { index, track -> "queue_item_${track.uuid}_$index" }
                ) { index, track ->
                    val actualQueueIndex = queueIndex + 1 + index
                    val density = LocalDensity.current
                    
                    // Drag state for visual feedback
                    var dragOffset by remember { mutableFloatStateOf(0f) }
                    var isDragging by remember { mutableStateOf(false) }
                    
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            // Only allow dismiss when not dragging
                            if (!isDragging && dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                onRemoveTrack(track.uuid)
                                true
                            } else {
                                false
                            }
                        }
                    )

                    // Reset dismiss state when dragging starts to prevent stuck red background
                    LaunchedEffect(isDragging) {
                        if (isDragging) {
                            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                        }
                    }
                    
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = !isDragging,
                            backgroundContent = {
                                // Only show delete background when actually swiping (not dragging)
                                if (!isDragging && dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.Red.copy(alpha = 0.8f)),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Remove",
                                            tint = Color.White,
                                            modifier = Modifier.padding(end = 16.dp)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .zIndex(if (dragOffset != 0f) 100f else 0f)
                        ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationY = dragOffset
                                    shadowElevation = if (dragOffset != 0f) 12f else 0f
                                }
                                .animateItem()
                                .zIndex(if (dragOffset != 0f) 100f else 0f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (dragOffset != 0f) 8.dp else 0.dp
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .animateItem()
                                    .clickable { onPlayTrack(track) },
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
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.basicMarquee()
                                    )
                                    Text(
                                        track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.basicMarquee()
                                    )
                                }
                                
                                // Position indicator
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                
                                // Drag handle with gesture detection (moved to right side)
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = "Drag to reorder",
                                    tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .pointerInput(Unit) {
                                            if (!uiState.isQueueOperationInProgress) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        dragOffset = 0f
                                                        isDragging = true
                                                    },
                                                    onDragEnd = {
                                                        // Calculate how many positions to move based on drag distance
                                                        // Each item is approximately 88dp (72dp card + 8dp spacing + 8dp padding)
                                                        val itemHeightPx = with(density) { 88.dp.toPx() }
                                                        val positionsToMove = (dragOffset / itemHeightPx).toInt()
                                                        
                                                        if (positionsToMove != 0) {
                                                            val targetIndex = (actualQueueIndex + positionsToMove).coerceIn(
                                                                queueIndex + 1,  // Can't move before current track
                                                                queue.size - 1    // Can't move past end
                                                            )
                                                            
                                                            if (targetIndex != actualQueueIndex) {
                                                                onMoveTrack(actualQueueIndex, targetIndex)
                                                            }
                                                        }
                                                        
                                                        dragOffset = 0f
                                                        isDragging = false
                                                    },
                                                    onDragCancel = { 
                                                        dragOffset = 0f
                                                        isDragging = false
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffset += dragAmount.y
                                                    }
                                                )
                                            }
                                        }
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
    onShowSleepTimer: () -> Unit,
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
                        LyricsOverlay(currentTrack, currentPosition, musicViewModel, isTablet, true, onToggleLyrics)
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
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sleep Timer") },
                            onClick = {
                                showMenu = false
                                onShowSleepTimer()
                            }
                        )
                    }
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
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = currentTrack.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
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
                
                // Bottom buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { musicViewModel.toggleFavorite(currentTrack) }) {
                        Icon(
                            if (currentTrack.isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (currentTrack.isFavourite) "Remove from favorites" else "Add to favorites",
                            tint = if (currentTrack.isFavourite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onShowQueue) {
                        Icon(Icons.AutoMirrored.Filled.List, "Queue")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
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
    onShowSleepTimer: () -> Unit,
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
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Menu")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Sleep Timer") },
                        onClick = {
                            showMenu = false
                            onShowSleepTimer()
                        }
                    )
                }
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
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (showLyrics) {
                if (currentTrack.syncedLyrics != null || currentTrack.plainLyrics != null) {
                    LyricsOverlay(currentTrack, currentPosition, musicViewModel, isTablet, false, onToggleLyrics)
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
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().basicMarquee()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = currentTrack.artist,
            style = if (isTablet) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().basicMarquee()
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
        
        // Bottom buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { musicViewModel.toggleFavorite(currentTrack) }) {
                Icon(
                    if (currentTrack.isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (currentTrack.isFavourite) "Remove from favorites" else "Add to favorites",
                    tint = if (currentTrack.isFavourite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onShowQueue) {
                Icon(Icons.AutoMirrored.Filled.List, "Queue")
            }
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
                    onDragStart = { _: Offset -> isDragging = true },
                    onDragEnd = { isDragging = false },
                    onHorizontalDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, _: Float ->
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
    isLandscape: Boolean,
    onDismiss: () -> Unit
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
            key(currentTrack.uuid) {
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
                    } else {
                        // Reset to top if no position matched (next song starting point)
                        currentLineIndex = 0
                        if (lyricLines.isNotEmpty()) {
                            listState.scrollToItem(0)
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
        
        // Close button
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Lyrics",
                    tint = Color.White
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

@Composable
private fun SleepTimerDialog(
    currentTimerRemaining: Long?,
    onDismiss: () -> Unit,
    onSetTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(currentTimerRemaining?.let { it / 60000f } ?: 0f) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Timer") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (currentTimerRemaining != null) {
                    val minutes = (currentTimerRemaining / 1000 / 60).toInt()
                    val seconds = ((currentTimerRemaining / 1000) % 60).toInt()
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Playback will pause in:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Text(
                        "Set timer duration:",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        if (sliderValue > 0) {
                            "${sliderValue.toInt()} minutes"
                        } else {
                            "Timer off"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0f..180f,
                        steps = 179,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "0 min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "3 hours",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (currentTimerRemaining != null) {
                TextButton(onClick = onCancelTimer) {
                    Text("Cancel Timer")
                }
            } else {
                TextButton(
                    onClick = { onSetTimer(sliderValue.toInt()) },
                    enabled = sliderValue > 0
                ) {
                    Text("Set Timer")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (currentTimerRemaining != null) "Close" else "Cancel")
            }
        }
    )
}