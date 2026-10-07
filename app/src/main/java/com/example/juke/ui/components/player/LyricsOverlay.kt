package com.example.juke.ui.components.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.juke.models.Track
import com.example.juke.ui.icons.JukeIcons
import com.example.juke.ui.screens.parseSyncedLyrics
import com.example.juke.ui.theme.GlassCard
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

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
            delay(2500.milliseconds)
            if (currentTrack.syncedLyrics.isNullOrBlank() && currentTrack.plainLyrics.isNullOrBlank()) {
                lyricsPending = false
            }
        }
    }

    // Measure the actual overlay height to compute center padding dynamically
    var overlayHeightPx by remember { mutableIntStateOf(0) }
    // Active line rests ~30% from the top (reading position), leaving room for upcoming lines
    val topPadding = with(density) { (overlayHeightPx * 0.30f).toDp() }
    val bottomPadding = with(density) { (overlayHeightPx * 0.70f).toDp() }

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
                    .padding(horizontal = 24.dp)
                    .fadingEdges(topFraction = 0.14f, bottomFraction = 0.22f),
                contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(lyricLines.size) { index ->
                    val line = lyricLines[index]
                    val isCurrentLine = index == currentLineIndex

                    // Animate color for the active line
                    val textColor by animateColorAsState(
                        targetValue = if (isCurrentLine)
                            Color.White
                        else
                            Color.White.copy(alpha = 0.42f),
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
                    val baseFontSize = 28.sp
                    val animatedFontSize = baseFontSize * fontScale

                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = animatedFontSize,
                            lineHeight = animatedFontSize * 1.25f,
                            letterSpacing = (-0.3).sp
                        ),
                        color = textColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = {
                                musicViewModel.seekTo(line.timeMs)
                            }),
                        textAlign = TextAlign.Start,
                        fontWeight = FontWeight.Bold,
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

        // Sync controls use a dense glass surface so lyric text cannot bleed through.
        if (syncedLyrics != null && showSyncControls) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                LyricsSyncPanel(
                    offsetMs = localOffsetMs,
                    onOffsetChange = { value ->
                        val step = (value / 100f).roundToInt()
                        if (step != lastTick) lastTick = step
                        localOffsetMs = (step * 100f).coerceIn(-60000f, 60000f)
                    },
                    onSave = { musicViewModel.saveLyricsOffset(currentTrack, localOffsetMs.toLong()) },
                    onReset = {
                        haptic.click()
                        localOffsetMs = 0f
                        lastTick = 0
                        musicViewModel.saveLyricsOffset(currentTrack, 0L)
                    },
                    onDone = {
                        haptic.click()
                        musicViewModel.saveLyricsOffset(currentTrack, localOffsetMs.toLong())
                        showSyncControls = false
                    }
                )
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
                                imageVector = JukeIcons.Equalizer,
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
                        imageVector = JukeIcons.Close,
                        contentDescription = "Close Lyrics",
                        tint = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

/** User adjustment is relative to the fixed internal render lead; zero remains the default. */
@Composable
private fun LyricsSyncPanel(
    offsetMs: Float,
    onOffsetChange: (Float) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val foreground = Color(0xFFF4F4F5)
    val secondary = Color(0xFFB7B7BD)
    val buttonColors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = foreground)
    GlassCard(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF18181B).copy(alpha = 0.96f)),
        shape = shape,
        level = com.example.juke.ui.theme.GlassLevel.Thin
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Lyrics sync", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = foreground)
                val sign = if (offsetMs > 0f) "+" else ""
                Text("$sign${String.format(Locale.US, "%.1f", offsetMs / 1000f)} s",
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold, color = foreground)
            }
            Text("Adjust when each line appears", style = MaterialTheme.typography.bodySmall, color = secondary)
            Column {
                Slider(
                    value = offsetMs.coerceIn(-60000f, 60000f),
                    onValueChange = onOffsetChange,
                    onValueChangeFinished = onSave,
                    valueRange = -60000f..60000f,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Lyrics timing offset" },
                    colors = SliderDefaults.colors(thumbColor = foreground, activeTrackColor = foreground,
                        inactiveTrackColor = Color.White.copy(alpha = 0.18f))
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("−60 s · Earlier", style = MaterialTheme.typography.labelSmall, color = secondary)
                    Text("Later · +60 s", style = MaterialTheme.typography.labelSmall, color = secondary)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { onOffsetChange((offsetMs - 500f).coerceAtLeast(-60000f)); onSave() },
                    enabled = offsetMs > -60000f,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = buttonColors
                ) { Text("−0.5 s") }
                androidx.compose.material3.OutlinedButton(
                    onClick = { onOffsetChange((offsetMs + 500f).coerceAtMost(60000f)); onSave() },
                    enabled = offsetMs < 60000f,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = buttonColors
                ) { Text("+0.5 s") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onReset) { Text("Reset", color = secondary) }
                androidx.compose.material3.Button(onClick = onDone, shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = foreground, contentColor = Color(0xFF18181B))) { Text("Done") }
            }
        }
    }
}
