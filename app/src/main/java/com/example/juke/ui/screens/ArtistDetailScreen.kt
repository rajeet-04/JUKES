package com.example.juke.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.SpotifyImage
import com.example.juke.models.SpotifyTrack
import com.example.juke.ui.components.SwipeToAddNextContainer
import com.example.juke.utils.BlacklistManager
import com.example.juke.viewmodels.MusicViewModel
import com.example.juke.viewmodels.SearchViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    searchViewModel: SearchViewModel = viewModel(),
    musicViewModel: MusicViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToAlbum: (SpotifyAlbum) -> Unit = {},
    bottomPadding: Dp = 0.dp
) {
    val uiState by searchViewModel.artistDetailState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    if (uiState.artist == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val artist = uiState.artist!!


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(artist.name) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    val isBlacklisted = remember(artist.name) {
                        BlacklistManager.containsBlacklistedArtist(context, artist.name)
                    }
                    var blacklisted by remember(artist.name) { mutableStateOf(isBlacklisted) }
                    IconButton(onClick = {
                        if (blacklisted) {
                            BlacklistManager.removeArtist(context, artist.name)
                        } else {
                            BlacklistManager.addArtist(context, artist.name)
                        }
                        blacklisted = !blacklisted
                    }) {
                        Icon(
                            imageVector = if (blacklisted)
                                Icons.Filled.Block
                            else
                                Icons.Outlined.Block,
                            contentDescription = if (blacklisted) "Unblock Artist" else "Block Artist",
                            tint = if (blacklisted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 16.dp,
                    end = 20.dp,
                    bottom = 16.dp + bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Artist Header
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = bestImageUrl(artist.images) ?: artist.images.firstOrNull()?.url
                            ?: "",
                            contentDescription = artist.name,
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.headlineMedium
                        )

                        if (artist.followers != null) {
                            Text(
                                text = "${formatNumber(artist.followers.total)} followers",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (artist.genres.isNotEmpty()) {
                            Text(
                                text = artist.genres.joinToString(" • "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Top Tracks Section
                if (uiState.topTracks.isNotEmpty()) {
                    item {
                        Text(
                            text = "Top Tracks",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    val topTracksSubset = uiState.topTracks.take(10)
                    items(topTracksSubset) { track ->
                        SwipeToAddNextContainer(
                            onAddNext = {
                                scope.launch {
                                    musicViewModel.queueSpotifyTrackNext(track)
                                }
                            }
                        ) {
                            TrackItem(
                                track = track,
                                onClick = {
                                    scope.launch {
                                        // Queue this track and all tracks below it
                                        musicViewModel.setQueueFromSpotifyTracks(
                                            topTracksSubset,
                                            topTracksSubset.indexOf(track)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                // Albums Section (2x2 grid)
                if (uiState.albums.isNotEmpty()) {
                    item {
                        Text(
                            text = "Albums",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    val albumRows = uiState.albums.chunked(2)
                    items(albumRows) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (album in row) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                ) {
                                    AlbumItem(
                                        album = album,
                                        onClick = { onNavigateToAlbum(album) }
                                    )
                                }
                            }

                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackItem(
    track: SpotifyTrack,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                AsyncImage(
                    model = bestImageUrl(track.album.images) ?: track.album.images.lastOrNull()?.url
                    ?: "",
                    contentDescription = track.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = track.artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = track.album.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = formatDuration(track.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AlbumItem(
    album: SpotifyAlbum,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            AsyncImage(
                model = bestImageUrl(album.images) ?: album.images.lastOrNull()?.url ?: "",
                contentDescription = album.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${
                        album.albumType?.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(
                                java.util.Locale.getDefault()
                            ) else it.toString()
                        } ?: "Album"
                    } • ${album.releaseDate?.take(4) ?: ""} • ${album.totalTracks ?: 0} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatNumber(num: Int): String {
    return when {
        num >= 1_000_000 -> "${num / 1_000_000}M"
        num >= 1_000 -> "${num / 1_000}K"
        else -> num.toString()
    }
}

private fun formatDuration(durationMs: Int): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

// Helper to pick best available image (prefer 640x640)
private fun bestImageUrl(images: List<SpotifyImage>?): String? {
    if (images.isNullOrEmpty()) return null
    images.find { (it.height == 640 || it.width == 640) }?.let { return it.url }
    return images.maxByOrNull { (it.height ?: 0) * (it.width ?: 0) }?.url
}
