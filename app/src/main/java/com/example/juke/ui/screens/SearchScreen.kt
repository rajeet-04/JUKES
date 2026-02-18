package com.example.juke.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.SpotifyArtist
import com.example.juke.models.SpotifyPlaylist
import com.example.juke.models.Track
import com.example.juke.network.SpotifyApi
import com.example.juke.ui.components.AlbumCard
import com.example.juke.ui.components.ArtistCard
import com.example.juke.ui.components.PlaylistCard
import com.example.juke.ui.components.SwipeToAddNextContainer
import com.example.juke.viewmodels.MusicViewModel
import com.example.juke.viewmodels.SearchViewModel
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    musicViewModel: MusicViewModel,
    searchViewModel: SearchViewModel = viewModel(),
    searchResetTrigger: Int = 0,
    onNavigateToArtist: (SpotifyArtist) -> Unit = {},
    onNavigateToPlaylist: (SpotifyPlaylist) -> Unit = {},
    onNavigateToAlbum: (SpotifyAlbum) -> Unit = {},
    bottomPadding: Dp = 0.dp
) {
    val uiState by searchViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var previousTrigger by remember { mutableIntStateOf(searchResetTrigger) }
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Tracks", "Artists", "Playlists", "Albums")

    LaunchedEffect(searchResetTrigger) {
        if (searchResetTrigger != previousTrigger && searchResetTrigger > 0) {
            searchViewModel.updateQuery("")
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .statusBarsPadding()
            ) {
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Pill search bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (uiState.query.isEmpty()) {
                                Text(
                                    text = "Songs, artists, playlists…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            BasicTextField(
                                value = uiState.query,
                                onValueChange = { searchViewModel.updateQuery(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        if (uiState.query.isNotBlank() && !uiState.isSearching) {
                                            keyboardController?.hide()
                                            searchViewModel.search(uiState.query)
                                        }
                                    }
                                )
                            )
                        }
                        if (uiState.isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (uiState.query.isNotEmpty()) {
                            IconButton(
                                onClick = { searchViewModel.updateQuery("") },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Filter chips — slide in when query is active
                AnimatedVisibility(
                    visible = uiState.query.isNotEmpty(),
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut()
                ) {
                    LazyRow(
                        modifier = Modifier.padding(top = 10.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
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
                                },
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selectedFilter == filter,
                                    borderColor = Color.Transparent,
                                    selectedBorderColor = Color.Transparent
                                ),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = CircleShape
                            )
                        }
                    }
                }
            }

            // Thin divider below header
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // ── Content ─────────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {

                if (hasResults(uiState) && !uiState.isPlaylistUrl) {
                    SearchResultsList(
                        uiState = uiState,
                        selectedFilter = selectedFilter,
                        musicViewModel = musicViewModel,
                        searchViewModel = searchViewModel,
                        scope = scope,
                        onNavigateToArtist = onNavigateToArtist,
                        onNavigateToPlaylist = onNavigateToPlaylist,
                        onNavigateToAlbum = onNavigateToAlbum,
                        bottomPadding = bottomPadding
                    )
                } else if (uiState.isPlaylistUrl && uiState.playlists.isNotEmpty() && !uiState.isImportingPlaylist) {
                    val playlist = uiState.playlists.first()
                    ImportPlaylistCard(
                        playlist = playlist,
                        onImport = {
                            scope.launch {
                                searchViewModel.importPlaylist(uiState.playlistId!!) { track ->
                                    musicViewModel.downloadSong(SpotifyApi.spotifyTrackToSong(track))
                                }
                            }
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
                            onSearchClick = { searchViewModel.updateQuery(it) },
                            onRemoveClick = { searchViewModel.removeRecentSearch(it) },
                            onClearAll = {
                                uiState.recentSearches.forEach {
                                    searchViewModel.removeRecentSearch(
                                        it
                                    )
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

                // Error snackbar-style banner
                if (uiState.error != null) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp)
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
            }
        }
    }
}

private fun hasResults(uiState: com.example.juke.viewmodels.SearchUiState): Boolean {
    return uiState.tracks.isNotEmpty() ||
            uiState.localTracks.isNotEmpty() ||
            uiState.artists.isNotEmpty() ||
            uiState.playlists.isNotEmpty() ||
            uiState.albums.isNotEmpty()
}

@Composable
private fun ImportPlaylistCard(
    playlist: SpotifyPlaylist,
    onImport: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Column {
                    Text(
                        text = "Import Playlist",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${playlist.tracks.total} tracks ready to download",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start Import", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ImportProgressCard(progress: Int, total: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "Importing…",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { if (total > 0) progress.toFloat() / total.toFloat() else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "$progress / $total tracks",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    uiState: com.example.juke.viewmodels.SearchUiState,
    selectedFilter: String,
    musicViewModel: MusicViewModel,
    searchViewModel: SearchViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    onNavigateToArtist: (SpotifyArtist) -> Unit,
    onNavigateToPlaylist: (SpotifyPlaylist) -> Unit,
    onNavigateToAlbum: (SpotifyAlbum) -> Unit,
    bottomPadding: Dp
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)
    ) {
        // ── In Your Library ──────────────────────────────────────────────
        if ((selectedFilter == "All" || selectedFilter == "Tracks") && uiState.localTracks.isNotEmpty()) {
            item { SectionHeader("In Your Library") }
            items(uiState.localTracks, key = { it.uuid }) { track ->
                LocalTrackItem(
                    track = track,
                    onClick = { musicViewModel.setQueue(listOf(track), 0) }
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // ── Songs ────────────────────────────────────────────────────────
        if ((selectedFilter == "All" || selectedFilter == "Tracks") && uiState.tracks.isNotEmpty()) {
            item { SectionHeader("Songs") }
            items(uiState.tracks, key = { it.id ?: it.uri }) { track ->
                SwipeToAddNextContainer(
                    onAddNext = {
                        scope.launch {
                            searchViewModel.setDownloading(track.id)
                            try {
                                musicViewModel.queueSpotifyTrackNext(track)
                            } finally {
                                searchViewModel.setDownloading(null)
                            }
                        }
                    }
                ) {
                    PremiumTrackItem(
                        track = track,
                        isDownloading = uiState.downloadingId == track.id,
                        onClick = {
                            scope.launch {
                                searchViewModel.setDownloading(track.id)
                                musicViewModel.downloadAndPlay(SpotifyApi.spotifyTrackToSong(track))
                                kotlinx.coroutines.delay(2000)
                                searchViewModel.setDownloading(null)
                            }
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // ── Artists ──────────────────────────────────────────────────────
        if ((selectedFilter == "All" || selectedFilter == "Artists") && uiState.artists.isNotEmpty()) {
            item { SectionHeader("Artists") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.artists, key = { it.id ?: it.uri ?: it.name }) { artist ->
                        ArtistCard(artist = artist, onClick = { onNavigateToArtist(artist) })
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // ── Playlists ────────────────────────────────────────────────────
        if ((selectedFilter == "All" || selectedFilter == "Playlists") && uiState.playlists.isNotEmpty()) {
            item { SectionHeader("Playlists") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(uiState.playlists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onNavigateToPlaylist(playlist) }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // ── Albums ───────────────────────────────────────────────────────
        if ((selectedFilter == "All" || selectedFilter == "Albums") && uiState.albums.isNotEmpty()) {
            item { SectionHeader("Albums") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(uiState.albums, key = { it.id ?: it.uri ?: it.name }) { album ->
                        AlbumCard(album = album, onClick = { onNavigateToAlbum(album) })
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ── Section header with accent bar ──────────────────────────────────────────
@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Spotify track row (flat, no card shadow) ─────────────────────────────────
@Composable
private fun PremiumTrackItem(
    track: com.example.juke.models.SpotifyTrack,
    isDownloading: Boolean,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .clickable(enabled = !isDownloading, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                AsyncImage(
                    model = track.album.images.lastOrNull()?.url ?: "",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (isDownloading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isDownloading) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = track.artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 78.dp, end = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    }
}

// ── Library track row (flat + left accent) ───────────────────────────────────
@Composable
private fun LocalTrackItem(
    track: Track,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Primary-coloured left accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(64.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.thumbnailUri ?: "",
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "%d:%02d".format(track.durationSec / 60, track.durationSec % 60),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 78.dp, end = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    }
}

// ── Empty / idle state ───────────────────────────────────────────────────────
@Composable
private fun EmptySearchState(
    isQueryEmpty: Boolean,
    isSearching: Boolean,
    bottomPadding: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomPadding),
        contentAlignment = BiasAlignment(0f, -0.25f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            val icon = if (isQueryEmpty) Icons.Default.MusicNote else Icons.Outlined.SearchOff
            val title = if (isQueryEmpty) "What do you want to hear?" else "No results found"
            val subtitle = if (isQueryEmpty) "Search for songs, artists, playlists or albums"
            else "Try a different spelling or keyword"

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Recent searches ──────────────────────────────────────────────────────────
@Composable
private fun RecentSearches(
    searches: List<String>,
    onSearchClick: (String) -> Unit,
    onRemoveClick: (String) -> Unit,
    onClearAll: () -> Unit,
    bottomPadding: Dp
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, top = 4.dp, end = 16.dp, bottom = bottomPadding + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClearAll) {
                    Text(
                        "Clear all",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        items(searches) { query ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSearchClick(query) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onRemoveClick(query) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        }
    }
}

private fun formatDuration(durationMs: Int): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
