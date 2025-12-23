package com.example.juke.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juke.ui.components.TrackCard
import com.example.juke.viewmodels.HomeViewModel
import com.example.juke.viewmodels.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    musicViewModel: MusicViewModel,
    homeViewModel: HomeViewModel = viewModel()
) {
    val uiState by homeViewModel.uiState.collectAsState()
    
    LaunchedEffect(Unit) {
        homeViewModel.loadHomeData()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JUKE") }
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
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Welcome to JUKE!",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Start by searching and downloading your favorite music",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        "Made with ❤️ by RASH",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp)
            ) {
                if (uiState.recentlyPlayed.isNotEmpty()) {
                    item {
                        Text(
                            "Recently Played",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(bottom = 24.dp)
                        ) {
                            items(uiState.recentlyPlayed) { track ->
                                TrackCard(
                                    track = track,
                                    onClick = {
                                        musicViewModel.setQueue(
                                            uiState.recentlyPlayed,
                                            uiState.recentlyPlayed.indexOf(track)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                
                if (uiState.mostPlayed.isNotEmpty()) {
                    item {
                        Text(
                            "Most Played",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.mostPlayed) { track ->
                                TrackCard(
                                    track = track,
                                    onClick = {
                                        musicViewModel.setQueue(
                                            uiState.mostPlayed,
                                            uiState.mostPlayed.indexOf(track)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Footer with credits
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Made with ❤️ by RASH",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
