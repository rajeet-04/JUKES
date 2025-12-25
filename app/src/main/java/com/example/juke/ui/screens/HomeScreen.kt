package com.example.juke.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juke.ui.components.TrackCard
import com.example.juke.viewmodels.HomeViewModel
import com.example.juke.viewmodels.MusicViewModel
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    musicViewModel: MusicViewModel,
    homeViewModel: HomeViewModel = viewModel(),
    onSettingsClick: () -> Unit = {},
    bottomPadding: Dp = 0.dp
) {
    val uiState by homeViewModel.uiState.collectAsState()
    
    LaunchedEffect(Unit) {
        homeViewModel.loadHomeData()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JUKE", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) },
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

                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp + bottomPadding
                )
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
                    item {
                        Text(
                            "\nFIRST PLAYS MAY TAKE A WHILE :)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, top = 12.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 21.sp,
                            fontWeight = MaterialTheme.typography.bodyLarge.fontWeight
                        )
                    }
                }
            }
        }
    }
}
