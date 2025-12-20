package com.example.juke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.juke.viewmodels.MusicViewModel

@Composable
fun MiniPlayer(
    musicViewModel: MusicViewModel,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by musicViewModel.uiState.collectAsState()
    val currentTrack = uiState.currentTrack
    
    if (currentTrack != null) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentTrack.thumbnailUri != null) {
                        AsyncImage(
                            model = currentTrack.thumbnailUri,
                            contentDescription = currentTrack.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            painter = painterResource(com.example.juke.R.drawable.baseline_play_24),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentTrack.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                IconButton(
                    onClick = { musicViewModel.togglePlayPause() }
                ) {
                    Icon(
                        painter = painterResource(if (uiState.isPlaying) com.example.juke.R.drawable.baseline_pause_24 else com.example.juke.R.drawable.baseline_play_24),
                        contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
