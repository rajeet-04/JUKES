package com.example.juke.ui.screens

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.juke.models.Track
import com.example.juke.ui.components.GlassCard
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.MusicViewModel
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurgeSelectionScreen(
    musicViewModel: MusicViewModel,
    onNavigateBack: () -> Unit
) {
    var purgeableTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var selectedTracks by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var showConfirmation by remember { mutableStateOf(false) }
    val haptic = rememberJukeHaptics()

    LaunchedEffect(Unit) {
        purgeableTracks = musicViewModel.getPurgeableTracks()
        isLoading = false
    }

    // Gradient Background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1C1E), // Dark
                        Color(0xFF0F1113), // Darker
                        Color.Black
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            TopAppBar(
                title = { Text("Purge Redundant", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                ),
                actions = {
                    if (purgeableTracks.isNotEmpty()) {
                        TextButton(onClick = {
                            haptic.click()
                            if (selectedTracks.size == purgeableTracks.size) {
                                selectedTracks = emptySet()
                            } else {
                                selectedTracks = purgeableTracks.map { it.uuid }.toSet()
                            }
                        }) {
                            Text(
                                if (selectedTracks.size == purgeableTracks.size) "Deselect All" else "Select All",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (purgeableTracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No redundant tracks found!",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Summary
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Selected: ${selectedTracks.size}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                val totalSize = purgeableTracks
                                    .filter { it.uuid in selectedTracks }
                                    .sumOf { track ->
                                        track.localUri?.let { uri ->
                                            val file = File(uri)
                                            if (file.exists()) file.length() else 0L
                                        } ?: 0L
                                    }
                                Text(
                                    "Est. Size: ${formatFileSize(totalSize)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }

                            Button(
                                onClick = { showConfirmation = true },
                                enabled = selectedTracks.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete")
                            }
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(purgeableTracks) { track ->
                            val isSelected = selectedTracks.contains(track.uuid)

                            PurgeTrackItem(
                                track = track,
                                isSelected = isSelected,
                                onClick = {
                                    haptic.click()
                                    selectedTracks = if (isSelected) {
                                        selectedTracks - track.uuid
                                    } else {
                                        selectedTracks + track.uuid
                                    }
                                }
                            )

                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.1f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(start = 72.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showConfirmation) {
            AlertDialog(
                onDismissRequest = { showConfirmation = false },
                title = { Text("Confirm Deletion") },
                text = {
                    Text("Are you sure you want to delete ${selectedTracks.size} tracks? This cannot be undone.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val tracksToDelete =
                                purgeableTracks.filter { it.uuid in selectedTracks }
                            musicViewModel.purgeTracks(tracksToDelete)
                            // Remove from list
                            purgeableTracks = purgeableTracks.filter { it.uuid !in selectedTracks }
                            selectedTracks = emptySet()
                            showConfirmation = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun PurgeTrackItem(
    track: Track,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val animatedContainerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        } else {
            Color.Transparent
        },
        label = "selectionColor"
    )

    val reason = when {
        track.playCount == 0 -> "Never played"
        track.durationSec < 60 -> "Short duration (< 60s)"
        else -> "Played ${track.playCount} times"
    }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = ListItemDefaults.colors(
            containerColor = animatedContainerColor,
            headlineColor = MaterialTheme.colorScheme.onBackground,
            supportingColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        ),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (track.thumbnailUri != null) {
                    AsyncImage(
                        model = track.thumbnailUri,
                        contentDescription = "${track.title} Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        headlineContent = {
            Text(
                text = track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = track.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailingContent = {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    )
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
