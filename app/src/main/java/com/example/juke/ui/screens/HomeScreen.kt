package com.example.juke.ui.screens

import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.example.juke.ui.components.HomeSkeleton
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.HomeViewModel
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
    val haptic = rememberJukeHaptics()

    LaunchedEffect(Unit) {
        homeViewModel.loadHomeData()
    }

    if (uiState.isLoading) {
        HomeSkeleton(bottomPadding = bottomPadding)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Frozen header
        HomeHeader(
            greeting = uiState.greeting,
            onSettingsClick = {
                haptic.click()
                onSettingsClick()
            }
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
                                onSeeAllClick = onSeeAllClick,
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
                                onSeeAllClick = onSeeAllClick,
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
            .padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "JUKE",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        FilledIconButton(
            onClick = onSettingsClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Settings"
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
    val context = LocalContext.current
    val haptic = rememberJukeHaptics()
    val pagerState = rememberPagerState(pageCount = { tracks.size })
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val accessibilityManager = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)
    }
    val autoAdvanceDelayMillis = remember(accessibilityManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            accessibilityManager?.getRecommendedTimeoutMillis(
                6000,
                AccessibilityManager.FLAG_CONTENT_TEXT or
                        AccessibilityManager.FLAG_CONTENT_ICONS or
                        AccessibilityManager.FLAG_CONTENT_CONTROLS
            ) ?: 6000
        } else {
            6000
        }
    }
    val touchExplorationEnabled = accessibilityManager?.isTouchExplorationEnabled == true
    var autoAdvanceResetKey by remember(tracks.size) { mutableIntStateOf(0) }
    var isAutoScrolling by remember { mutableStateOf(false) }
    val visibleIndicatorCount = tracks.size.coerceAtMost(8)
    val indicatorStart = when {
        tracks.size <= visibleIndicatorCount -> 0
        pagerState.currentPage <= 3 -> 0
        pagerState.currentPage >= tracks.lastIndex - 3 -> tracks.size - visibleIndicatorCount
        else -> pagerState.currentPage - 3
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .collectLatest { isScrollInProgress ->
                if (isScrollInProgress && !isAutoScrolling) {
                    autoAdvanceResetKey++
                }
            }
    }

    LaunchedEffect(
        autoAdvanceResetKey,
        tracks.size,
        autoAdvanceDelayMillis,
        touchExplorationEnabled
    ) {
        if (tracks.size <= 1 || touchExplorationEnabled) return@LaunchedEffect

        delay(autoAdvanceDelayMillis.toLong())
        val isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (!pagerState.isScrollInProgress && isResumed) {
            isAutoScrolling = true
            try {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % tracks.size)
            } finally {
                isAutoScrolling = false
            }
        }
    }

    Column {
        SectionHeader(title = "Recently Played", onActionClick = onSeeAllClick)

        HorizontalPager(
            modifier = Modifier.semantics {
                contentDescription = "Recently played tracks carousel"
                stateDescription = "Track ${pagerState.currentPage + 1} of ${tracks.size}"
            },
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 12.dp),
            pageSpacing = 12.dp
        ) { page ->
            HeroTrackCard(
                track = tracks[page],
                onClick = { onTrackClick(page) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = if (tracks.size > 1) Arrangement.SpaceBetween else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (tracks.size > 1) {
                FilledIconButton(
                    onClick = {
                        coroutineScope.launch {
                            haptic.click()
                            pagerState.animateScrollToPage(
                                if (pagerState.currentPage == 0) tracks.lastIndex else pagerState.currentPage - 1
                            )
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous recently played track"
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(visibleIndicatorCount) { offset ->
                    val pageIndex = indicatorStart + offset
                    val isSelected = pageIndex == pagerState.currentPage
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

            if (tracks.size > 1) {
                FilledIconButton(
                    onClick = {
                        coroutineScope.launch {
                            haptic.click()
                            pagerState.animateScrollToPage(
                                if (pagerState.currentPage == tracks.lastIndex) 0 else pagerState.currentPage + 1
                            )
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next recently played track"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun HorizontalTrackSection(
    title: String,
    tracks: List<Track>,
    onSeeAllClick: () -> Unit,
    onTrackClick: (Int) -> Unit
) {
    Column {
        SectionHeader(title = title, onActionClick = onSeeAllClick)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
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
    onSeeAllClick: () -> Unit,
    onTrackClick: (Int) -> Unit
) {
    Column {
        SectionHeader(title = "Favorites", onActionClick = onSeeAllClick)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
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
            .padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    "See All",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
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
    val haptic = rememberJukeHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "musicCardScale"
    )

    Box(
        modifier = Modifier
            .size(160.dp)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(track.title)
                    append(" by ")
                    append(track.artist)
                    if (track.playCount > 0) {
                        append(", played ")
                        append(track.playCount)
                        append(" times")
                    }
                }
            }
            .clickable(
                interactionSource = interactionSource,
                role = Role.Button,
                onClickLabel = "Play ${track.title}"
            ) {
                haptic.click()
                onClick()
            }
    ) {
        if (track.thumbnailUri != null) {
            AsyncImage(
                model = track.thumbnailUri,
                contentDescription = null,
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
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        startY = 120f
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (track.playCount > 0) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.White
                )
                Text(
                    text = track.playCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
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
    val haptic = rememberJukeHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "favoriteCardScale"
    )

    Column(
        modifier = Modifier
            .width(112.dp)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .semantics(mergeDescendants = true) {
                contentDescription = "${track.title} by ${track.artist}"
                stateDescription = "Favorite track"
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                role = Role.Button,
                onClickLabel = "Play ${track.title}"
            ) {
                haptic.click()
                onClick()
            }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
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
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f)),
                            radius = 150f
                        )
                    )
            )

            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
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
                    contentDescription = "Music note",
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

