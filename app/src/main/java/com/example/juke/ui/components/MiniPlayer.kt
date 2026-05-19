package com.example.juke.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.juke.ui.screens.LyricLine
import com.example.juke.ui.screens.parseSyncedLyrics
import com.example.juke.utils.LyricsRomanizer
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.delay

/** Returns true if the lyric line contains only musical notation symbols / whitespace.
 *  Such lines signal an instrumental passage and should not be shown in the mini-player. */
private fun isMusicOnlyLine(text: String): Boolean {
    // Strip all recognized music/notation Unicode symbols and whitespace, then check if empty.
    val musicPattern = Regex("[♪♫♬♩𝄞𝄢𝄫𝅗𝅝𝅗𝅥♯♭♮\\s()\\[\\]{}]+")
    return text.replace(musicPattern, "").isEmpty()
}

/**
 * Computes a per-song adaptive gap threshold for the mini-player lyric fallback.
 *
 * Strategy:
 *  1. Collect all inter-line gaps from the parsed lyrics list.
 *  2. Sort them and take the median — the median is naturally outlier-resistant,
 *     meaning a 38-second instrumental silence won't pull the value up.
 *  3. Multiply by 2.2 to get a threshold that covers genuinely long sung lines
 *     (where the LRC timestamp is set once for the start of a phrase that the
 *     singer holds for 10–14 s) while still catching real silences which always
 *     exceed the median by a wide margin.
 *  4. Clamp to [1800 ms, 12 000 ms] to handle degenerate edge-cases.
 */
private fun computeAdaptiveLyricsGapThreshold(lyrics: List<LyricLine>): Long {
    if (lyrics.size < 3) return 4_000L
    val gaps = (0 until lyrics.size - 1)
        .map { i -> lyrics[i + 1].timeMs - lyrics[i].timeMs }
        .filter { it > 0L }
        .sorted()
    if (gaps.size < 2) return 4_000L
    val median = gaps[gaps.size / 2]
    return (median * 2.2).toLong().coerceIn(1_800L, 12_000L)
}

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
    val context = LocalContext.current

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
            isRomanizedLyricsEnabled,
            currentTrack.syncedLyrics,
            currentTrack.romanizedSyncedLyrics
        ) { mutableStateOf<String?>(null) }

        LaunchedEffect(
            currentTrack.uuid,
            currentTrack.syncedLyrics,
            currentTrack.romanizedSyncedLyrics,
            isRomanizedLyricsEnabled
        ) {
            if (!isRomanizedLyricsEnabled) {
                romanizedSyncedLyrics = null
                return@LaunchedEffect
            }
            romanizedSyncedLyrics = currentTrack.romanizedSyncedLyrics ?: currentTrack.syncedLyrics?.let {
                LyricsRomanizer.romanizeSyncedLyrics(it)
            }

            if (romanizedSyncedLyrics != null &&
                romanizedSyncedLyrics != currentTrack.romanizedSyncedLyrics
            ) {
                musicViewModel.persistRomanizedLyrics(
                    track = currentTrack,
                    romanizedSyncedLyrics = romanizedSyncedLyrics,
                    romanizedPlainLyrics = null
                )
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

        // Compute the adaptive gap threshold once per lyric set so it adapts to
        // each song's own pacing without any manual tuning.
        val gapThreshold = remember(parsedLyrics) {
            computeAdaptiveLyricsGapThreshold(parsedLyrics)
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

                    // --- Condition 1: music-symbol-only lines are instrumentation markers ---
                    // Lines like "♪", "(♫)", etc. indicate a musical interlude; treat as blank.
                    if (isMusicOnlyLine(currentLine.text)) return@remember null

                    val shouldShowLine = if (nextLine != null) {
                        val msUntilNext = nextLine.timeMs - currentPosition
                        // --- Condition 2: adaptive gap detection ---
                        // `gapThreshold` is derived from the song's own median inter-line gap
                        // (× 2.2), so it naturally tolerates long sung phrases while still
                        // falling back during genuine instrumental silences.
                        msUntilNext > 0 && (currentPosition - currentLine.timeMs) < gapThreshold
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
                        // Explicit cache keys ensure Coil hits memory/disk cache immediately
                        // when the MiniPlayer re-renders after a track change, preventing
                        // the blank thumbnail flash on already-loaded images.
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(currentTrack.thumbnailUri)
                                .memoryCacheKey(currentTrack.thumbnailUri)
                                .diskCacheKey(currentTrack.thumbnailUri)
                                .crossfade(true)
                                .build(),
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
                        miniPlayScale.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
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
                            animationSpec = tween(
                                durationMillis = 180,
                                easing = FastOutSlowInEasing
                            ),
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
