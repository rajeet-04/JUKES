package com.example.juke.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.juke.ui.components.GlassFilterChip
import com.example.juke.ui.components.SearchHeader
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

    var previousFocusTrigger by remember { mutableIntStateOf(searchFocusTrigger) }
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    var active by rememberSaveable { mutableStateOf(false) }
    val filters = listOf("All", "Tracks", "Artists", "Playlists", "Albums")

    // Warm the YT suggestions connection once when search screen is opened.
    LaunchedEffect(Unit) {
        searchViewModel.warmSuggestionsConnection()
    }

    // Re-tapping the Search tab while on Search: open the bar; the header selects the query and shows the keyboard.
    LaunchedEffect(searchFocusTrigger) {
        if (searchFocusTrigger != previousFocusTrigger && searchFocusTrigger > 0) {
            previousFocusTrigger = searchFocusTrigger
            active = true
        }
    }

    Scaffold(containerColor = Color.Transparent) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            SearchHeader(
                title = "Search",
                query = uiState.query,
                onQueryChange = { searchViewModel.updateQuery(it) },
                open = active,
                onOpenChange = { active = it },
                placeholder = "Songs, artists, albums",
                selectAllTrigger = searchFocusTrigger,
                onSearch = {
                    if (uiState.query.isNotBlank() && !uiState.isSearching) {
                        keyboardController?.hide()
                        searchViewModel.search(uiState.query)
                    }
                }
            )

            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {

                    // ── Suggestions view (shown while the user is typing) ────────────
                    if (uiState.isShowingSuggestions && uiState.query.isNotBlank()) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(uiState.suggestions) { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            haptic.click()
                                            keyboardController?.hide()
                                            searchViewModel.search(suggestion)
                                        }
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
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
                            visible = uiState.query.isNotEmpty(),
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filters) { filter ->
                                    GlassFilterChip(
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
                            if (hasResults(uiState) && !uiState.isPlaylistUrl) {
                                SearchResultsList(
                                    uiState = uiState,
                                    selectedFilter = selectedFilter,
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
                            } else if (!hasResults(uiState) && !uiState.isImportingPlaylist) {
                                if (uiState.query.isBlank() && uiState.recentSearches.isNotEmpty()) {
                                    RecentSearches(
                                        searches = uiState.recentSearches,
                                        onSearchClick = {
                                            haptic.click()
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
