package com.example.juke.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juke.ui.components.CompactTrackCard
import com.example.juke.ui.components.HeroTrackCard
import com.example.juke.viewmodels.HomeViewModel
import com.example.juke.viewmodels.MusicViewModel

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "JUKE",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.recentlyPlayed.isEmpty() && uiState.mostPlayed.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        "Welcome to JUKE!",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Your library is looking a bit empty.\nStart by searching and downloading your favorite music!",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp + bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Recently Played Section (Full Width)
                if (uiState.recentlyPlayed.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Recently Played",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = onSeeAllClick) {
                                    Text("See All")
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val pagerState =
                                rememberPagerState(pageCount = { uiState.recentlyPlayed.size })

                            val lifecycleOwner =
                                androidx.lifecycle.compose.LocalLifecycleOwner.current
                            LaunchedEffect(pagerState, uiState.recentlyPlayed, lifecycleOwner) {
                                while (true) {
                                    kotlinx.coroutines.delay(3000)
                                    // Only scroll if:
                                    // 1. Not currently being dragged
                                    // 2. List is not empty
                                    // 3. Screen is RESUMED (visible and active)
                                    val isResumed =
                                        lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)

                                    if (!pagerState.isScrollInProgress && uiState.recentlyPlayed.isNotEmpty() && isResumed) {
                                        val nextPage =
                                            (pagerState.currentPage + 1) % uiState.recentlyPlayed.size
                                        pagerState.animateScrollToPage(nextPage)
                                    }
                                }
                            }

                            Column {
                                HorizontalPager(
                                    state = pagerState,
                                    contentPadding = PaddingValues(horizontal = 0.dp),
                                    pageSpacing = 16.dp
                                ) { page ->
                                    HeroTrackCard(
                                        track = uiState.recentlyPlayed[page],
                                        onClick = {
                                            musicViewModel.setQueue(
                                                uiState.recentlyPlayed,
                                                page
                                            )
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Page Indicators
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(10.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    repeat(uiState.recentlyPlayed.size.coerceAtMost(5)) { iteration ->
                                        val color = if (pagerState.currentPage == iteration)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                        Box(
                                            modifier = Modifier
                                                .padding(2.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .size(8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }

                // Most Played Section (Grid)
                if (uiState.mostPlayed.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Most Played",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 0.dp)
                        )
                    }

                    items(uiState.mostPlayed) { track ->
                        CompactTrackCard(
                            track = track,
                            onClick = {
                                musicViewModel.setQueue(
                                    uiState.mostPlayed,
                                    uiState.mostPlayed.indexOf(track)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "\nFIRST PLAYS MAY TAKE A WHILE :)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp, top = 12.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
