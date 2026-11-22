package com.example.juke.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.SpotifyImage
import com.example.juke.models.SpotifyTrack
import com.example.juke.ui.components.FlatTrackRow
import com.example.juke.ui.components.GlassTopAppBar
import com.example.juke.ui.components.MediaDetailSkeleton
import com.example.juke.ui.components.SwipeToAddNextContainer
import com.example.juke.ui.components.artistReleases
import com.example.juke.ui.icons.JukeIcons
import com.example.juke.utils.BlacklistManager
import com.example.juke.viewmodels.MusicViewModel
import com.example.juke.viewmodels.SearchViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun ArtistDetailScreen(
    searchViewModel: SearchViewModel = viewModel(),
    musicViewModel: MusicViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToAlbum: (SpotifyAlbum) -> Unit = {},
    bottomPadding: Dp = 0.dp
) {
    val uiState by searchViewModel.artistDetailState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val artist = uiState.artist


    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = { Text(artist?.name ?: "Artist") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(JukeIcons.Back, "Back")
                    }
                },
                actions = {
                    if (artist != null) {
                        com.example.juke.ui.components.ShareButton("artist", artist.id)
                        val isBlacklisted = remember(artist.name) {
                            BlacklistManager.containsBlacklistedArtist(context, artist.name)
                        }
                        var blacklisted by remember(artist.name) { mutableStateOf(isBlacklisted) }
                        IconButton(onClick = {
                            if (blacklisted) {
                                BlacklistManager.removeArtist(context, artist.name)
                                Toast.makeText(context, "${artist.name} unblocked", Toast.LENGTH_SHORT).show()
                            } else {
                                BlacklistManager.addArtist(context, artist.name)
                                Toast.makeText(context, "${artist.name} blocked", Toast.LENGTH_SHORT).show()
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
                }
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { paddingValues ->
        if (uiState.isLoading || artist == null) {
            MediaDetailSkeleton(
                modifier = Modifier.padding(paddingValues),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 16.dp,
                    end = 20.dp,
                    bottom = 16.dp + bottomPadding
                )
            )
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                        val top = uiState.topTracks.take(10)
                        val from by musicViewModel.playingFrom.collectAsStateWithLifecycle()
                        val playing by remember(musicViewModel) { musicViewModel.uiState.map { it.isPlaying }.distinctUntilChanged() }
                            .collectAsStateWithLifecycle(initialValue = false)
                        com.example.juke.ui.components.PlayShuffleRow(
                            title = "Top Tracks",
                            onShuffle = { musicViewModel.setQueueFromSpotifyTracks(top.shuffled(), 0, from = topContext(artist, top)) },
                            onPlay = {
                                musicViewModel.playList(com.example.juke.viewmodels.PlayContext.Kind.ARTIST, artist.id.orEmpty()) {
                                    musicViewModel.setQueueFromSpotifyTracks(top, 0, from = topContext(artist, top))
                                }
                            },
                            playing = playing && from?.isList(com.example.juke.viewmodels.PlayContext.Kind.ARTIST, artist.id.orEmpty()) == true
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
                                musicViewModel = musicViewModel,
                                onClick = {
                                    scope.launch {
                                        // Queue this track and all tracks below it
                                        musicViewModel.setQueueFromSpotifyTracks(
                                            topTracksSubset,
                                            topTracksSubset.indexOf(track),
                                            from = topContext(artist, topTracksSubset)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                // Discography: newest release, then albums and singles lanes
                artistReleases(uiState.albums, gutter = 20.dp, onOpen = onNavigateToAlbum)
            }
        }
    }
}

@Composable
private fun TrackItem(
    track: SpotifyTrack,
    musicViewModel: MusicViewModel,
    onClick: () -> Unit
) {
    FlatTrackRow(
        actions = com.example.juke.ui.components.spotifyTrackActions(track, musicViewModel, play = onClick),
        imageUrl = bestImageUrl(track.album.images) ?: track.album.images.lastOrNull()?.url,
        title = track.name,
        subtitle = "${track.artists.joinToString(", ") { it.name }} · ${track.album.name}",
        duration = formatDuration(track.durationMs),
        onClick = onClick
    )
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

/** An artist's top tracks as a list the player can name and reopen. */
private fun topContext(artist: com.example.juke.models.SpotifyArtist, tracks: List<SpotifyTrack>) =
    com.example.juke.viewmodels.PlayContext(
        com.example.juke.viewmodels.PlayContext.Kind.ARTIST, artist.id.orEmpty(), artist.name,
        keys = tracks.mapNotNullTo(HashSet()) { it.id }
    )
