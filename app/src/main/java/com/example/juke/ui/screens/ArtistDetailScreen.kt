package com.example.juke.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.SpotifyArtist
import com.example.juke.models.SpotifyTrack
import com.example.juke.models.SpotifyImage
import com.example.juke.ui.components.SwipeToAddNextContainer
import com.example.juke.viewmodels.SearchViewModel
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    searchViewModel: SearchViewModel = viewModel(),
    musicViewModel: MusicViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToAlbum: (SpotifyAlbum) -> Unit = {}
) {
    val uiState by searchViewModel.artistDetailState.collectAsState()
    val scope = rememberCoroutineScope()
    
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
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Artist Header
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = bestImageUrl(artist.images) ?: artist.images.firstOrNull()?.url ?: "",
                            contentDescription = artist.name,
                            modifier = Modifier
                                .size(200.dp)
                                .clip(CircleShape),
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
                    
                    items(uiState.topTracks.take(10)) { track ->
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
                                        musicViewModel.downloadAndPlay(
                                            com.example.juke.network.SpotifyApi.spotifyTrackToSong(track)
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
                                Box(modifier = Modifier
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
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = bestImageUrl(track.album.images) ?: track.album.images.lastOrNull()?.url ?: "",
                contentDescription = track.name,
                modifier = Modifier.size(60.dp),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.bodyLarge,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        onClick = onClick
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
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier
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
                    text = "${album.albumType.replaceFirstChar { it.uppercase() }} • ${album.releaseDate.take(4)} • ${album.totalTracks} tracks",
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
