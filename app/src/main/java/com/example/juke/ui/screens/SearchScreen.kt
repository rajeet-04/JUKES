package com.example.juke.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.SpotifyArtist
import com.example.juke.models.SpotifyPlaylist
import com.example.juke.ui.icons.JukeIcons
import com.example.juke.ui.theme.GlassCard
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.MusicViewModel
import com.example.juke.viewmodels.SearchViewModel

@Composable
fun SearchScreen(
    musicViewModel: MusicViewModel,
    searchViewModel: SearchViewModel = viewModel(),
    searchFocusTrigger: Int = 0,
    onNavigateToArtist: (SpotifyArtist) -> Unit = {},
    onNavigateToPlaylist: (SpotifyPlaylist) -> Unit = {},
    onNavigateToAlbum: (SpotifyAlbum) -> Unit = {},
    bottomPadding: Dp = 0.dp
) {
    val uiState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val isStreamMode by musicViewModel.isStreamMode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = rememberJukeHaptics()

    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    val filters = listOf("All", "Songs", "Artists", "Albums", "Playlists")

    // Warm the YT suggestions connection once when search screen is opened.
    LaunchedEffect(Unit) {
        searchViewModel.warmSuggestionsConnection()
    }

    LaunchedEffect(uiState.query) {
        if (uiState.query.isBlank()) selectedFilter = "All"
    }

    Scaffold(containerColor = Color.Transparent) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            SearchInput(
                query = uiState.query,
                onQueryChange = { searchViewModel.updateQuery(it) },
                focusTrigger = searchFocusTrigger,
                onSearch = {
                    if (uiState.query.isNotBlank() && !uiState.isSearching) {
                        selectedFilter = "All"
                        keyboardController?.hide()
                        searchViewModel.search(uiState.query)
                    }
                }
            )

            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {

                    // ── Suggestions view (shown while the user is typing) ────────────
                    if (uiState.isShowingSuggestions && uiState.query.isNotBlank()) {
                        LazyColumn(modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)) {
                            item(key = "submit_query") {
                                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                                    selectedFilter = "All"
                                    keyboardController?.hide()
                                    searchViewModel.search(uiState.query)
                                }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(JukeIcons.Search, null, Modifier.size(20.dp))
                                    Spacer(Modifier.width(16.dp))
                                    Text("Search for “${uiState.query}”", style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                            items(uiState.suggestions) { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            haptic.click()
                                            selectedFilter = "All"
                                            keyboardController?.hide()
                                            searchViewModel.search(suggestion)
                                        }
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = JukeIcons.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = suggestion,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                )
                            }
                        }
                    }
                    // ── Full results view (shown after the user submits a search) ────
                    else {
                        AnimatedVisibility(
                            visible = uiState.query.isNotBlank() && !uiState.isPlaylistUrl,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filters) { filter ->
                                    FilterChip(
                                        selected = selectedFilter == filter,
                                        onClick = { selectedFilter = filter },
                                        label = {
                                            Text(
                                                filter,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        })
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            if (uiState.isImportingPlaylist && uiState.isPlaylistUrl) {
                                ImportProgressCard(progress = uiState.importProgress, total = uiState.importTotal)
                            } else if (hasResults(uiState) && !uiState.isPlaylistUrl && uiState.query.isNotBlank()) {
                                SearchResultsList(
                                    uiState = uiState,
                                    selectedFilter = selectedFilter,
                                    onFilterSelected = { selectedFilter = it },
                                    musicViewModel = musicViewModel,
                                    searchViewModel = searchViewModel,
                                    scope = scope,
                                    isStreamMode = isStreamMode,
                                    onNavigateToArtist = onNavigateToArtist,
                                    onNavigateToPlaylist = onNavigateToPlaylist,
                                    onNavigateToAlbum = onNavigateToAlbum,
                                    bottomPadding = bottomPadding,
                                    keyboardController = keyboardController
                                )
                            } else if (uiState.isPlaylistUrl && uiState.playlists.isNotEmpty() && !uiState.isImportingPlaylist) {
                                val playlist = uiState.playlists.first()
                                ImportPlaylistCard(
                                    playlist = playlist,
                                    onImport = {
                                        searchViewModel.importPlaylist(uiState.playlistId!!)
                                    }
                                )
                            } else if (uiState.isImportingPlaylist && uiState.query.isBlank()) {
                                ImportProgressCard(
                                    progress = uiState.importProgress,
                                    total = uiState.importTotal
                                )
                            } else if ((uiState.query.isBlank() || !hasResults(uiState)) && !uiState.isImportingPlaylist) {
                                if (uiState.query.isBlank() && uiState.recentSearches.isNotEmpty()) {
                                    RecentSearches(
                                        searches = uiState.recentSearches,
                                        onSearchClick = {
                                            haptic.click()
                                            selectedFilter = "All"
                                            keyboardController?.hide()
                                            searchViewModel.search(it)
                                        },
                                        onRemoveClick = { searchViewModel.removeRecentSearch(it) },
                                        onClearAll = {
                                            uiState.recentSearches.forEach {
                                                searchViewModel.removeRecentSearch(it)
                                            }
                                        },
                                        bottomPadding = bottomPadding
                                    )
                                } else {
                                    EmptySearchState(
                                        isQueryEmpty = uiState.query.isBlank(),
                                        isSearching = uiState.isSearching,
                                        bottomPadding = bottomPadding
                                    )
                                }
                            }

                            if (uiState.error != null) {
                                GlassCard(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(12.dp)
                                        .fillMaxWidth(),
        accent = MaterialTheme.colorScheme.errorContainer) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 12.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Warning,
                                            null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            uiState.error!!,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }

                            if (uiState.isSearching && hasResults(uiState)) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.Transparent
                                )
                            }
                        }
                    }
            }
        }
    }
}
