package com.example.juke.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.juke.models.Track
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.MusicViewModel
import kotlin.math.absoluteValue

@Composable
fun PlayerArtwork(
    queue: List<Track>,
    queueIndex: Int,
    currentTrack: Track,
    currentPosition: Long,
    showLyrics: Boolean,
    musicViewModel: MusicViewModel,
    isTablet: Boolean,
    onToggleLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberJukeHaptics()

    val pagerState = rememberPagerState(
        initialPage = queueIndex.coerceAtLeast(0),
        pageCount = { queue.size.coerceAtLeast(1) }
    )

    // Sync external playback changes (next button, track end) to the Pager
    LaunchedEffect(queueIndex) {
        if (queueIndex in queue.indices && pagerState.currentPage != queueIndex) {
            pagerState.animateScrollToPage(queueIndex)
        }
    }

    // Sync manual user swipes to the ViewModel
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != queueIndex && pagerState.settledPage in queue.indices) {
            val swipedTrack = queue[pagerState.settledPage]
            musicViewModel.playTrackFromQueue(swipedTrack)
            haptic.click()
        }
    }

    // Use fillMaxHeight so the artwork is bounded by the parent Box's height constraint,
    // then use aspectRatio(1f) to ensure it stays square. This prevents overflow on small screens.
    val artworkModifier = modifier
        .fillMaxHeight(if (isTablet) 1f else 0.94f)
        .aspectRatio(1f)

    if (queue.isEmpty()) {
        // Fallback if queue is empty for some reason
        ArtworkCard(
            track = currentTrack,
            showLyrics = showLyrics,
            currentPosition = currentPosition,
            musicViewModel = musicViewModel,
            isTablet = isTablet,
            onToggleLyrics = onToggleLyrics,
            modifier = artworkModifier
        )
    } else {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 0.dp),
            modifier = artworkModifier
        ) { page ->
            // Calculate scale/alpha for smooth parallax transition
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val scale = 1f - (0.15f * pageOffset.absoluteValue.coerceIn(0f, 1f))
            val alpha = 1f - (0.5f * pageOffset.absoluteValue.coerceIn(0f, 1f))
            val pageTrack = if (page == queueIndex) {
                currentTrack
            } else {
                queue.getOrNull(page) ?: currentTrack
            }

            ArtworkCard(
                track = pageTrack,
                // Only show lyrics on the active page
                showLyrics = showLyrics && page == pagerState.currentPage,
                currentPosition = currentPosition,
                musicViewModel = musicViewModel,
                isTablet = isTablet,
                onToggleLyrics = onToggleLyrics,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
            )
        }
    }
}

@Composable
private fun ArtworkCard(
    track: Track,
    showLyrics: Boolean,
    currentPosition: Long,
    musicViewModel: MusicViewModel,
    isTablet: Boolean,
    onToggleLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberJukeHaptics()

    Box(
        modifier = modifier
            .fillMaxSize()
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(24.dp))
            .clickable {
                haptic.click()
                onToggleLyrics()
            },
        contentAlignment = Alignment.Center
    ) {
        if (track.thumbnailUri != null) {
            AsyncImage(
                model = track.thumbnailUri,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showLyrics) {
            LyricsOverlay(
                currentTrack = track,
                currentPosition = currentPosition,
                musicViewModel = musicViewModel,
                isTablet = isTablet,
                onDismiss = onToggleLyrics
            )
        }
    }
}
