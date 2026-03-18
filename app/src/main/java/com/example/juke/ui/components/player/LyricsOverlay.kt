package com.example.juke.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juke.models.Track
import com.example.juke.ui.screens.parseSyncedLyrics
import com.example.juke.viewmodels.MusicViewModel
import java.util.Locale

@Composable
fun LyricsOverlay(
    currentTrack: Track,
    currentPosition: Long,
    musicViewModel: MusicViewModel,
    isTablet: Boolean,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val syncedLyrics = currentTrack.syncedLyrics
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    var localOffsetMs by remember(currentTrack.uuid) {
        mutableFloatStateOf(currentTrack.lyricsOffsetMs.toFloat())
    }
    var showSyncControls by remember(currentTrack.uuid) { mutableStateOf(false) }
    var currentLineIndex by remember(currentTrack.uuid) { mutableIntStateOf(0) }

    // Calculate padding to center active line in the image
    val verticalPadding = if (isTablet && isLandscape) {
        (configuration.screenHeightDp.dp / 2)
    } else {
        (configuration.screenWidthDp.dp / 2)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)) // Slightly darker for better readability
    ) {
        if (syncedLyrics != null) {
            val lyricLines = remember(syncedLyrics, localOffsetMs) {
                parseSyncedLyrics(syncedLyrics, localOffsetMs.toLong())
            }
            val listState = rememberLazyListState()

            LaunchedEffect(currentPosition, lyricLines) {
                val newIndex = lyricLines.indexOfLast { it.timeMs <= currentPosition }
                if (newIndex >= 0 && newIndex != currentLineIndex) {
                    currentLineIndex = newIndex
                    if (lyricLines.isNotEmpty() && !listState.isScrollInProgress) {
                        listState.animateScrollToItem(
                            index = newIndex,
                            scrollOffset = 0
                        )
                    }
                } else if (newIndex < 0 && currentLineIndex != 0) {
                    currentLineIndex = 0
                    if (lyricLines.isNotEmpty() && !listState.isScrollInProgress) {
                        listState.animateScrollToItem(0)
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(vertical = verticalPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
            ) {
                items(lyricLines.size) { index ->
                    val line = lyricLines[index]
                    val isCurrentLine = index == currentLineIndex

                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (isCurrentLine) Color.White else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = {
                                musicViewModel.seekTo(line.timeMs)
                            }),
                        textAlign = TextAlign.Center,
                        fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                        lineHeight = 32.sp
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    currentTrack.plainLyrics ?: "No lyrics available",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp
                )
            }
        }

        if (syncedLyrics != null && showSyncControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.DarkGray.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val sign = if (localOffsetMs > 0f) "+" else ""
                    Text(
                        text = "Sync Offset: $sign${String.format(Locale.US, "%.1f", localOffsetMs / 1000f)}s",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )

                    Slider(
                        value = localOffsetMs,
                        onValueChange = { newValue ->
                            localOffsetMs = newValue
                        },
                        onValueChangeFinished = {
                            musicViewModel.saveLyricsOffset(currentTrack, localOffsetMs.toLong())
                        },
                        valueRange = -20000f..20000f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            localOffsetMs = (localOffsetMs - 500f).coerceAtLeast(-20000f)
                            musicViewModel.saveLyricsOffset(currentTrack, localOffsetMs.toLong())
                        }) {
                            Icon(Icons.Default.Remove, "Minus 0.5s", tint = Color.White)
                        }

                        TextButton(onClick = {
                            localOffsetMs = 0f
                            musicViewModel.saveLyricsOffset(currentTrack, 0L)
                        }) {
                            Text("Reset", color = Color.White)
                        }

                        IconButton(onClick = {
                            localOffsetMs = (localOffsetMs + 500f).coerceAtMost(20000f)
                            musicViewModel.saveLyricsOffset(currentTrack, localOffsetMs.toLong())
                        }) {
                            Icon(Icons.Default.Add, "Plus 0.5s", tint = Color.White)
                        }
                    }
                }
            }
        }

        // Top action buttons
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (syncedLyrics != null) {
                        IconButton(
                            onClick = { showSyncControls = !showSyncControls },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Toggle Sync Controls",
                                tint = Color.White
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
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
}
