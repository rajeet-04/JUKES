package com.example.juke.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.juke.models.Track
import com.example.juke.ui.components.HeroTrackCard
import com.example.juke.viewmodels.HomeViewModel
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    musicViewModel: MusicViewModel,
    homeViewModel: HomeViewModel = viewModel(),
    onSettingsClick: () -> Unit = {},
    onSeeAllClick: () -> Unit = {},
    bottomPadding: Dp = 0.dp
) {
    val uiState by homeViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        homeViewModel.loadHomeData()
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Frozen header
        HomeHeader(
            greeting = uiState.greeting,
            onSettingsClick = onSettingsClick
        )

        // Scrollable content with pull-to-refresh
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { homeViewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (uiState.recentlyPlayed.isEmpty() && uiState.mostPlayed.isEmpty() && uiState.favorites.isEmpty()) {
                EmptyHomeState(
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp + bottomPadding)
                ) {
                    if (uiState.recentlyPlayed.isNotEmpty()) {
                        item {
                            RecentlyPlayedSection(
                                tracks = uiState.recentlyPlayed,
                                onTrackClick = { index ->
                                    musicViewModel.setQueue(uiState.recentlyPlayed, index)
                                },
                                onSeeAllClick = onSeeAllClick
                            )
                        }
                    }

                    if (uiState.mostPlayed.isNotEmpty()) {
                        item {
                            HorizontalTrackSection(
                                title = "Most Played",
                                tracks = uiState.mostPlayed,
                                onTrackClick = { index ->
                                    musicViewModel.setQueue(uiState.mostPlayed, index)
                                }
                            )
                        }
                    }

                    if (uiState.favorites.isNotEmpty()) {
                        item {
                            FavoritesSection(
                                tracks = uiState.favorites,
                                onTrackClick = { index ->
                                    musicViewModel.setQueue(uiState.favorites, index)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    greeting: String,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "JUKE",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "What are we listening to?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onSettingsClick) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentlyPlayedSection(
    tracks: List<Track>,
    onTrackClick: (Int) -> Unit,
    onSeeAllClick: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { tracks.size })
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(pagerState, tracks) {
        while (true) {
            delay(4000)
            val isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!pagerState.isScrollInProgress && tracks.isNotEmpty() && isResumed) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % tracks.size)
            }
        }
    }

    Column {
        SectionHeader(title = "Recently Played", onActionClick = onSeeAllClick)

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp
        ) { page ->
            HeroTrackCard(
                track = tracks[page],
                onClick = { onTrackClick(page) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Animated pill indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(tracks.size.coerceAtMost(8)) { index ->
                val isSelected = index == pagerState.currentPage
                val pillWidth by animateDpAsState(
                    targetValue = if (isSelected) 22.dp else 6.dp,
                    animationSpec = tween(durationMillis = 300),
                    label = "pillWidth"
                )
                val pillColor by animateColorAsState(
                    targetValue = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    animationSpec = tween(durationMillis = 300),
                    label = "pillColor"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(pillWidth)
                        .clip(CircleShape)
                        .background(pillColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun HorizontalTrackSection(
    title: String,
    tracks: List<Track>,
    onTrackClick: (Int) -> Unit
) {
    Column {
        SectionHeader(title = title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(tracks) { index, track ->
                MusicCard(track = track, onClick = { onTrackClick(index) })
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun FavoritesSection(
    tracks: List<Track>,
    onTrackClick: (Int) -> Unit
) {
    Column {
        SectionHeader(title = "Favorites")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(tracks) { index, track ->
                FavoriteCard(track = track, onClick = { onTrackClick(index) })
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(
                    "See All",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MusicCard(
    track: Track,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(140.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                })
            }
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
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        startY = 80f
                    )
                )
        )

        // Title and artist at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Play count pill (top-right)
        if (track.playCount > 0) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = Color.White
                )
                Text(
                    text = track.playCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun FavoriteCard(
    track: Track,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .width(90.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                })
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
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
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .size(13.dp),
                tint = Color(0xFFE91E63)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EmptyHomeState(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Settings icon top-right
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentAlignment = Alignment.TopEnd
        ) {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Welcome to JUKE",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Your library is empty.\nSearch and download your favorite music to get started.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp
            )
        }
    }
}

