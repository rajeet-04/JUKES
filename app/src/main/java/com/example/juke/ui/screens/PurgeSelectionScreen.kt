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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.juke.models.Track
import com.example.juke.ui.components.GlassAlertDialog
import com.example.juke.ui.components.GlassButton
import com.example.juke.ui.components.GlassCard
import com.example.juke.ui.components.GlassTopAppBar
import com.example.juke.ui.components.TrackListSkeleton
import com.example.juke.ui.icons.JukeIcons
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.MusicViewModel
import java.io.File
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

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
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            GlassTopAppBar(
                title = { Text("Purge Redundant", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(JukeIcons.Back, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    if (purgeableTracks.isNotEmpty()) {
                        TextButton(onClick = {
                            haptic.click()
                            selectedTracks = if (selectedTracks.size == purgeableTracks.size) {
                                emptySet()
                            } else {
                                purgeableTracks.map { it.uuid }.toSet()
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
                TrackListSkeleton(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
                )
            } else if (purgeableTracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No redundant tracks found!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    color = MaterialTheme.colorScheme.onSurface,
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            GlassButton(
                                onClick = { showConfirmation = true },
                                enabled = selectedTracks.isNotEmpty()) {
                                Icon(JukeIcons.Delete, null, modifier = Modifier.size(18.dp))
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
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(start = 72.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showConfirmation) {
            GlassAlertDialog(
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
                        imageVector = JukeIcons.MusicNote,
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
    val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
    val pre = listOf("K", "M", "G", "T", "P", "E")[exp - 1]
    return String.format(Locale.US, "%.1f %sB", bytes / 1024.0.pow(exp.toDouble()), pre)
}
