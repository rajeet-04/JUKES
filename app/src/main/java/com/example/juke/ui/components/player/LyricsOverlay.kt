package com.example.juke.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juke.models.Track
import com.example.juke.ui.screens.parseSyncedLyrics
import com.example.juke.viewmodels.MusicViewModel

@Composable
fun LyricsOverlay(
    currentTrack: Track,
    currentPosition: Long,
    musicViewModel: MusicViewModel,
    isTablet: Boolean,
    isLandscape: Boolean,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val syncedLyrics = currentTrack.syncedLyrics

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
            key(currentTrack.uuid) {
                val lyricLines = remember(syncedLyrics) { parseSyncedLyrics(syncedLyrics) }
                val listState = rememberLazyListState()
                var currentLineIndex by remember { mutableIntStateOf(0) }

                LaunchedEffect(currentPosition, lyricLines) {
                    val newIndex = lyricLines.indexOfLast { it.timeMs <= currentPosition }
                    if (newIndex >= 0) {
                        currentLineIndex = newIndex
                        if (lyricLines.isNotEmpty()) {
                            listState.animateScrollToItem(
                                index = newIndex,
                                scrollOffset = 0
                            )
                        }
                    } else {
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

        // Close button
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.TopEnd
        ) {
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
