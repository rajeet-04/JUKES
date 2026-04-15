package com.example.juke.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.juke.ui.screens.parseSyncedLyrics
import com.example.juke.utils.LyricsRomanizer
import com.example.juke.utils.rememberJukeHaptics
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
    val currentPosition = uiState.position
    val isPlaying = uiState.isPlaying
    val isRomanizedLyricsEnabled by musicViewModel.isRomanizedLyricsEnabled.collectAsState()
    val isMiniPlayerLyricsEnabled by musicViewModel.isMiniPlayerLyricsEnabled.collectAsState()
    val haptic = rememberJukeHaptics()

    // Poll for progress updates when playing
    LaunchedEffect(uiState.isPlaying) {
        while (uiState.isPlaying) {
            musicViewModel.updateProgress()
            delay(300)
        }
    }

    if (currentTrack != null) {
        var offsetX by remember { mutableFloatStateOf(0f) }

        var romanizedSyncedLyrics by remember(
            currentTrack.uuid,
            isRomanizedLyricsEnabled
        ) { mutableStateOf<String?>(null) }

        LaunchedEffect(currentTrack.uuid, currentTrack.syncedLyrics, isRomanizedLyricsEnabled) {
            if (!isRomanizedLyricsEnabled) {
                romanizedSyncedLyrics = null
                return@LaunchedEffect
            }
            romanizedSyncedLyrics = currentTrack.syncedLyrics?.let {
                LyricsRomanizer.romanizeSyncedLyrics(it)
            }
        }

        val syncedLyricsForMini = if (isRomanizedLyricsEnabled) {
            romanizedSyncedLyrics ?: currentTrack.syncedLyrics
        } else {
            currentTrack.syncedLyrics
        }

        // Parse synced lyrics once per source/offset change for smooth real-time updates.
        val parsedLyrics = remember(
            currentTrack.uuid,
            syncedLyricsForMini,
            currentTrack.lyricsOffsetMs
        ) {
            syncedLyricsForMini?.let {
                parseSyncedLyrics(it, currentTrack.lyricsOffsetMs)
            } ?: emptyList()
        }

        val activeLyric = remember(
            currentPosition,
            parsedLyrics,
            isPlaying,
            isMiniPlayerLyricsEnabled
        ) {
            if (!isMiniPlayerLyricsEnabled || !isPlaying || parsedLyrics.isEmpty()) {
                null
            } else {
                val activeIndex = parsedLyrics.indexOfLast { it.timeMs <= currentPosition }
                if (activeIndex == -1) {
                    null
                } else {
                    val currentLine = parsedLyrics[activeIndex]
                    val nextLine = parsedLyrics.getOrNull(activeIndex + 1)
                    val shouldShowLine = if (nextLine != null) {
                        currentPosition < nextLine.timeMs
                    } else {
                        // Avoid pinning the final lyric line during long instrumental outros.
                        (currentPosition - currentLine.timeMs) <= 4500L
                    }

                    if (shouldShowLine) currentLine.text.takeIf { it.isNotBlank() } else null
                }
            }
        }

        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            haptic.click()
                            onExpand()
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX < -100f) {
                                haptic.click()
                                musicViewModel.skipToNext()
                            } else if (offsetX > 100f) {
                                haptic.click()
                                musicViewModel.skipToPrevious()
                            }
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX += dragAmount
                        }
                    )
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                DancingGlassBackground(
                    extractedColors = uiState.extractedColors,
                    isPlaying = uiState.isPlaying,
                    modifier = Modifier.matchParentSize()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentTrack.thumbnailUri != null) {
                        AsyncImage(
                            model = currentTrack.thumbnailUri,
                            contentDescription = currentTrack.title,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(com.example.juke.R.drawable.baseline_play_24),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    AnimatedContent(
                        targetState = activeLyric,
                        modifier = Modifier.weight(1f),
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(300))
                        },
                        label = "MiniPlayer_Lyrics_Transition"
                    ) { currentLyric ->
                        if (currentLyric != null) {
                            Text(
                                text = currentLyric,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Column {
                                Text(
                                    text = currentTrack.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentTrack.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.72f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Favourite button — tinted primary when hearted
                    IconButton(
                        onClick = {
                            haptic.confirm()
                            musicViewModel.toggleFavorite(currentTrack)
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (currentTrack.isFavourite) Icons.Filled.Favorite
                            else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (currentTrack.isFavourite) "Remove from favourites"
                            else "Add to favourites",
                            tint = if (currentTrack.isFavourite) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.86f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Download button — only shown for streamed tracks
                    if (currentTrack.isStream) {
                        IconButton(
                            onClick = {
                                haptic.click()
                                musicViewModel.promoteTrackToDownload(currentTrack)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download track",
                                tint = Color.White.copy(alpha = 0.86f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Play / Pause
                    val miniPlayScale = remember { Animatable(1f) }
                    LaunchedEffect(uiState.isPlaying) {
                        miniPlayScale.animateTo(0.85f, tween(90, easing = FastOutSlowInEasing))
                        miniPlayScale.animateTo(1f,    tween(150, easing = FastOutSlowInEasing))
                    }
                    IconButton(
                        onClick = {
                            haptic.heavyClick()
                            musicViewModel.togglePlayPause()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .scale(miniPlayScale.value)
                    ) {
                        Crossfade(
                            targetState = uiState.isPlaying,
                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                            label = "miniPlayPauseIcon"
                        ) { isPlaying ->
                            Icon(
                                painter = painterResource(
                                    if (isPlaying) com.example.juke.R.drawable.baseline_pause_24
                                    else com.example.juke.R.drawable.baseline_play_24
                                ),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(24.dp),
                                tint = Color.White
                            )
                        }
                    }
                }

                // Progress line at the bottom
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
                    color = Color.White.copy(alpha = 0.8f),
                    trackColor = Color.Transparent
                )
            }
        }
    }
}
