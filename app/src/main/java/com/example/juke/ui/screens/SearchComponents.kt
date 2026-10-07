package com.example.juke.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.SpotifyArtist
import com.example.juke.models.SpotifyPlaylist
import com.example.juke.models.Track
import com.example.juke.network.SpotifyApi
import com.example.juke.ui.components.AlbumCard
import com.example.juke.ui.components.ArtistCard
import com.example.juke.ui.components.GlassButton
import com.example.juke.ui.components.PlaylistCard
import com.example.juke.ui.components.SearchResultItemM3
import com.example.juke.ui.components.SwipeToAddNextContainer
import com.example.juke.ui.components.TrackListSkeleton
import com.example.juke.ui.theme.GlassCard
import com.example.juke.viewmodels.MusicViewModel
import com.example.juke.viewmodels.SearchViewModel
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal fun hasResults(uiState: com.example.juke.viewmodels.SearchUiState): Boolean {
    return uiState.tracks.isNotEmpty() ||
            uiState.localTracks.isNotEmpty() ||
            uiState.artists.isNotEmpty() ||
            uiState.playlists.isNotEmpty() ||
            uiState.albums.isNotEmpty()
}

@Composable
internal fun ImportPlaylistCard(
    playlist: SpotifyPlaylist,
    onImport: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = playlist.images.firstOrNull()?.url ?: "",
                contentDescription = null,
                modifier = Modifier
                    .size(132.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.tracks?.total ?: 0} tracks ready to download",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            GlassButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import playlist", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun ImportProgressCard(progress: Int, total: Int) {
    val fraction = if (total > 0) progress.toFloat() / total else 0f
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$progress / $total tracks",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Text(
                "Importing playlist…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
    }
}

@Composable
internal fun SearchResultsList(
    uiState: com.example.juke.viewmodels.SearchUiState,
    selectedFilter: String,
    musicViewModel: MusicViewModel,
    searchViewModel: SearchViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    isStreamMode: Boolean,
    onNavigateToArtist: (SpotifyArtist) -> Unit,
    onNavigateToPlaylist: (SpotifyPlaylist) -> Unit,
    onNavigateToAlbum: (SpotifyAlbum) -> Unit,
    bottomPadding: Dp,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController? = null
) {
    val hideKeyboardOnScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -5f) keyboardController?.hide()
                return Offset.Zero
            }
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
        modifier = Modifier.nestedScroll(hideKeyboardOnScrollConnection)
    ) {
        // ── In Your Library ──────────────────────────────────────────────
        if ((selectedFilter == "All" || selectedFilter == "Tracks") && uiState.localTracks.isNotEmpty()) {
            item { SectionHeader("In Your Library") }
            items(uiState.localTracks.distinctBy { it.uuid }, key = { it.uuid }) { track ->
                SwipeToAddNextContainer(
                    onAddNext = {
                        musicViewModel.addNext(track)
                    }
                ) {
                    LocalTrackItem(
                        track = track,
                        onClick = { musicViewModel.setQueue(listOf(track), 0) },
                        showAccentBar = false
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // ── Songs ────────────────────────────────────────────────────────
        if ((selectedFilter == "All" || selectedFilter == "Tracks") && uiState.tracks.isNotEmpty()) {
            item { SectionHeader("Songs") }
            items(
                uiState.tracks.distinctBy { it.id ?: it.uri },
                key = { it.id ?: it.uri }) { track ->
                SwipeToAddNextContainer(
                    onAddNext = {
                        scope.launch {
                            searchViewModel.setDownloading(track.id)
                            try {
                                musicViewModel.queueSpotifyTrackNext(
                                    spotifyTrack = track,
                                    useStreamMode = isStreamMode
                                )
                            } finally {
                                searchViewModel.setDownloading(null)
                            }
                        }
                    }
                ) {
                    SearchResultItemM3(
                        track = track,
                        isDownloading = uiState.downloadingId == track.id,
                        onClick = {
                            scope.launch {
                                searchViewModel.setDownloading(track.id)
                                musicViewModel.downloadAndPlay(SpotifyApi.spotifyTrackToSong(track))
                                kotlinx.coroutines.delay(2000.milliseconds)
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
                    items(
                        uiState.artists.distinctBy { it.id ?: it.uri ?: it.name },
                        key = { it.id ?: it.uri ?: it.name }) { artist ->
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
                    items(uiState.playlists.distinctBy { it.id }, key = { it.id }) { playlist ->
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
                    items(
                        uiState.albums.distinctBy { it.id ?: it.uri ?: it.name },
                        key = { it.id ?: it.uri ?: it.name }) { album ->
                        AlbumCard(album = album, onClick = { onNavigateToAlbum(album) })
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ── Section header ──────────────────────────────────────────────────────────
@Composable
internal fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp
        ),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)
    )
}

// ── Library track row (flat + left accent) ───────────────────────────────────
@Composable
internal fun LocalTrackItem(
    track: Track,
    onClick: () -> Unit,
    showAccentBar: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Primary-colored left accent bar
            if (showAccentBar) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(64.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.thumbnailUri ?: "",
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
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
            modifier = Modifier.padding(start = 82.dp, end = 20.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    }
}

// ── Empty / idle state ───────────────────────────────────────────────────────
@Composable
internal fun EmptySearchState(
    isQueryEmpty: Boolean,
    isSearching: Boolean,
    bottomPadding: Dp
) {
    if (isSearching && !isQueryEmpty) {
        TrackListSkeleton(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp + bottomPadding)
        )
        return
    }

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
            val title = if (isQueryEmpty) "Find your next song" else "No results found"
            val subtitle = if (isQueryEmpty) "Search for songs, artists, playlists or albums"
            else "Try a different spelling or keyword"

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = if (isQueryEmpty) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
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
internal fun RecentSearches(
    searches: List<String>,
    onSearchClick: (String) -> Unit,
    onRemoveClick: (String) -> Unit,
    onClearAll: () -> Unit,
    bottomPadding: Dp
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp, top = 4.dp, end = 12.dp, bottom = bottomPadding + 24.dp
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
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
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
                    .heightIn(min = 48.dp),
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
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
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
