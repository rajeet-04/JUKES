package com.example.juke.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juke.ui.components.SearchResultItem
import com.example.juke.viewmodels.MusicViewModel
import com.example.juke.viewmodels.SearchViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    musicViewModel: MusicViewModel,
    searchViewModel: SearchViewModel = viewModel()
) {
    val uiState by searchViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search for songs...") },
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                singleLine = true,
                trailingIcon = {
                    if (uiState.isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { searchViewModel.searchSongs(searchQuery) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSearching && searchQuery.isNotBlank()
            ) {
                Text("Search")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (uiState.error != null) {
                Text(
                    uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            if (uiState.results.isEmpty() && !uiState.isSearching && searchQuery.isNotBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No results found")
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.results) { song ->
                        SearchResultItem(
                            song = song,
                            isDownloading = uiState.downloadingId == song.url,
                            onDownload = {
                                scope.launch {
                                    searchViewModel.setDownloading(song.url)
                                    try {
                                        musicViewModel.downloadAndPlay(song)
                                    } finally {
                                        searchViewModel.setDownloading(null)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
