package com.example.juke.ui.components.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.juke.models.Track
import com.example.juke.ui.screens.parseSyncedLyrics
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Fading edge modifier that masks content with a vertical gradient,
 * creating a smooth fade-out at the top and bottom edges.
 */
private fun Modifier.fadingEdges(
    topFraction: Float = 0.12f,
    bottomFraction: Float = 0.12f
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val gradient = Brush.verticalGradient(
            0f to Color.Transparent,
            topFraction to Color.Black,
            (1f - bottomFraction) to Color.Black,
            1f to Color.Transparent
        )
        drawRect(brush = gradient, blendMode = BlendMode.DstIn)
    }

/**
 * Applies a radial alpha mask so blur falls off smoothly toward edges
 * instead of ending with a hard rectangular transition.
 */
@Composable
fun LyricsOverlay(
    currentTrack: Track,
    currentPosition: Long,
    musicViewModel: MusicViewModel,
    isTablet: Boolean,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val haptic = rememberJukeHaptics()
    val syncedLyrics = currentTrack.syncedLyrics?.takeIf { it.isNotBlank() }
    val plainLyrics = currentTrack.plainLyrics?.takeIf { it.isNotBlank() }
    var localOffsetMs by remember(currentTrack.uuid) {
        mutableFloatStateOf(currentTrack.lyricsOffsetMs.toFloat())
    }
    var lastTick by remember(currentTrack.uuid) {
        mutableIntStateOf((currentTrack.lyricsOffsetMs / 100L).toInt())
    }
    var showSyncControls by remember(currentTrack.uuid) { mutableStateOf(false) }
    var lyricsPending by remember(currentTrack.uuid) { mutableStateOf(false) }

    LaunchedEffect(currentTrack.uuid, syncedLyrics, plainLyrics) {
        if (syncedLyrics != null || plainLyrics != null) {
            lyricsPending = false
        } else {
            lyricsPending = true
            delay(2500)
            if (currentTrack.syncedLyrics.isNullOrBlank() && currentTrack.plainLyrics.isNullOrBlank()) {
                lyricsPending = false
            }
        }
    }

    // Measure the actual overlay height to compute center padding dynamically
    var overlayHeightPx by remember { mutableIntStateOf(0) }
    val centerPadding = with(density) {
        // Half the container height so the active line sits at vertical center
        (overlayHeightPx / 2).toDp()
    }

    // Premium full-bleed frosted background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { overlayHeightPx = it.height }
    ) {
        if (!currentTrack.thumbnailUri.isNullOrBlank()) {
            val blurDp = if (isTablet) 92.dp else 72.dp
            AsyncImage(
                model = currentTrack.thumbnailUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.2f)
                    .blur(blurDp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                contentScale = ContentScale.Crop
            )
        }

        // Lighter scrim for readability while preserving premium glass look
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.18f),
                            Color.Black.copy(alpha = 0.28f),
                            Color.Black.copy(alpha = 0.40f)
                        )
                    )
                )
        )

        if (syncedLyrics != null) {
            val lyricLines = remember(syncedLyrics, localOffsetMs) {
                parseSyncedLyrics(syncedLyrics, localOffsetMs.toLong())
            }
            val listState = rememberLazyListState()
            var currentLineIndex by remember(currentTrack.uuid) { mutableIntStateOf(-1) }
            var isFirstScroll by remember(currentTrack.uuid) { mutableStateOf(true) }

            LaunchedEffect(currentPosition, lyricLines) {
                val newIndex = lyricLines.indexOfLast { it.timeMs <= currentPosition }
                if (newIndex >= 0 && newIndex != currentLineIndex) {
                    currentLineIndex = newIndex
                    if (lyricLines.isNotEmpty() && !listState.isScrollInProgress) {
                        if (isFirstScroll) {
                            listState.scrollToItem(index = newIndex, scrollOffset = 0)
                            isFirstScroll = false
                        } else {
                            listState.animateScrollToItem(index = newIndex, scrollOffset = 0)
                        }
                    }
                } else if (newIndex < 0 && currentLineIndex != 0) {
                    currentLineIndex = 0
                    if (lyricLines.isNotEmpty() && !listState.isScrollInProgress) {
                        if (isFirstScroll) {
                            listState.scrollToItem(index = 0)
                            isFirstScroll = false
                        } else {
                            listState.animateScrollToItem(0)
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .fadingEdges(topFraction = 0.18f, bottomFraction = 0.18f),
                contentPadding = PaddingValues(
                    top = centerPadding,
                    bottom = centerPadding
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)
            ) {
                items(lyricLines.size) { index ->
                    val line = lyricLines[index]
                    val isCurrentLine = index == currentLineIndex

                    // Animate color for the active line
                    val textColor by animateColorAsState(
                        targetValue = if (isCurrentLine)
                            Color.White
                        else
                            Color.White.copy(alpha = 0.30f),
                        animationSpec = tween(durationMillis = 300),
                        label = "lyricColor"
                    )
                    val fontScale by animateFloatAsState(
                        targetValue = if (isCurrentLine) 1.0f else 0.92f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "lyricScale"
                    )

                    // Use fontSize scaling instead of graphicsLayer scale to prevent overflow
                    val baseFontSize = 22.sp
                    val animatedFontSize = baseFontSize * fontScale

                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = animatedFontSize,
                            lineHeight = animatedFontSize * 1.4f,
                            letterSpacing = (-0.3).sp
                        ),
                        color = textColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = {
                                musicViewModel.seekTo(line.timeMs)
                            }),
                        textAlign = TextAlign.Center,
                        fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Medium,
                        overflow = TextOverflow.Clip,
                        softWrap = true
                    )
                }
            }
        } else if (lyricsPending) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        text = "Loading lyrics...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        } else {
            // Plain lyrics (no syncing)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 48.dp)
                    .fadingEdges(topFraction = 0.08f, bottomFraction = 0.08f)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    plainLyrics ?: "No lyrics available",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 30.sp,
                        letterSpacing = 0.2.sp
                    ),
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        // Sync controls panel
        if (syncedLyrics != null && showSyncControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 12.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(bottom = 32.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val sign = if (localOffsetMs > 0f) "+" else ""
                        Text(
                            text = "Sync Offset: $sign${String.format(Locale.US, "%.1f", localOffsetMs / 1000f)}s",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                haptic.click()
                                localOffsetMs = (localOffsetMs - 500f).coerceAtLeast(-12000f)
                                val step = (localOffsetMs / 100f).roundToInt()
                                lastTick = step
                            }) {
                                Icon(
                                    Icons.Default.Remove,
                                    contentDescription = "-0.5s",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Slider(
                                value = localOffsetMs.coerceIn(-12000f, 12000f),
                                onValueChange = { value ->
                                    val currentStep = (value / 100f).roundToInt()
                                    if (currentStep != lastTick) {
                                        haptic.tick()
                                        lastTick = currentStep
                                    }
                                    localOffsetMs = value
                                },
                                onValueChangeFinished = {
                                    musicViewModel.saveLyricsOffset(currentTrack, localOffsetMs.toLong())
                                },
                                valueRange = -12000f..12000f,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .height(36.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                )
                            )

                            IconButton(onClick = {
                                haptic.click()
                                localOffsetMs = (localOffsetMs + 500f).coerceAtMost(12000f)
                                val step = (localOffsetMs / 100f).roundToInt()
                                lastTick = step
                            }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "+0.5s",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                haptic.click()
                                localOffsetMs = 0f
                                lastTick = 0
                                musicViewModel.saveLyricsOffset(currentTrack, 0L)
                            }) {
                                Text(
                                    "Reset",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            TextButton(onClick = {
                                haptic.heavyClick()
                                musicViewModel.saveLyricsOffset(currentTrack, localOffsetMs.toLong())
                                showSyncControls = false
                            }) {
                                Text(
                                    "Done",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top action buttons
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
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
                                .size(40.dp)
                                .background(
                                    Color.White.copy(alpha = 0.08f),
                                    CircleShape
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.04f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Toggle Sync Controls",
                                tint = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Color.White.copy(alpha = 0.08f),
                            CircleShape
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.04f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Lyrics",
                        tint = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}
