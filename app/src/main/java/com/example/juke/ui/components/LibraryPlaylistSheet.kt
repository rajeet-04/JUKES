package com.example.juke.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.juke.database.PlaylistEntity
import com.example.juke.models.Track
import com.example.juke.ui.icons.JukeIcons
import com.example.juke.ui.theme.GlassShapes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Add-only picker: existing membership is a status, never an implicit removal command. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryPlaylistSheet(
    tracks: List<Track>,
    playlists: List<PlaylistEntity>,
    loadMembershipCounts: suspend () -> Map<String, Int>,
    onAdd: suspend (PlaylistEntity) -> Int,
    onCreateAndAdd: suspend (String) -> Int,
    onSuccess: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val count = tracks.distinctBy { it.uuid }.size

    LaunchedEffect(tracks, playlists, retry) {
        loading = true
        try {
            counts = loadMembershipCounts()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error = "Couldn’t load playlist membership. Try again."
        } finally {
            loading = false
        }
    }

    fun save(destination: String, operation: suspend () -> Int) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                val added = operation()
                onSuccess(destination, added)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn’t add songs. Your selection is kept; try again."
            } finally {
                busy = false
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = busy) { /* Wait for the atomic save. */ }
    GlassModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // A single scroll container keeps search, creation and feedback reachable with large text or the keyboard.
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            item(key = "playlist_controls") {
                Column {
                    LibrarySheetHeader(
                        "Add to playlist",
                        if (count == 1) tracks.first().title else "$count songs selected",
                        onDismiss = { if (!busy) onDismiss() }, enabled = !busy
                    )
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, enabled = !busy,
                        label = { Text("Search playlists") }, singleLine = true,
                        leadingIcon = { Icon(JukeIcons.Search, null) },
                        shape = GlassShapes.Control, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    if (creating) {
                        OutlinedTextField(
                            value = name, onValueChange = { name = it }, label = { Text("Playlist name") },
                            enabled = !busy, singleLine = true, shape = GlassShapes.Control,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { creating = false }, enabled = !busy) { Text("Cancel") }
                            TextButton(
                                onClick = { val destination = name.trim(); save(destination) { onCreateAndAdd(destination) } },
                                enabled = name.isNotBlank() && !busy && !loading,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text("Create and add") }
                        }
                    } else {
                        TextButton(onClick = { creating = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(JukeIcons.Add, null)
                            Text("New playlist", Modifier.padding(start = 8.dp))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 12.dp).semantics { liveRegion = LiveRegionMode.Polite })
                        TextButton(onClick = { retry++ }, enabled = !busy) { Text("Retry") }
                    }
                    if (busy || loading) {
                        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(if (busy) "Adding songs…" else "Loading playlists…",
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    }
                }
            }
            val filtered = playlists.filter { it.name.contains(query.trim(), ignoreCase = true) }
            if (filtered.isEmpty()) item {
                Text(
                    if (playlists.isEmpty()) "Create your first playlist to collect these songs."
                    else "No matching playlists. Try another name.",
                    Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(filtered, key = { it.id }) { playlist ->
                val existing = counts[playlist.id] ?: 0
                val complete = existing >= count
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (playlist.thumbnailUri != null) {
                        AsyncImage(playlist.thumbnailUri, null,
                            Modifier.size(44.dp).clip(GlassShapes.Control), contentScale = ContentScale.Crop)
                    } else Icon(JukeIcons.Queue, null, Modifier.size(44.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, style = MaterialTheme.typography.titleMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                complete -> "Already added"
                                existing > 0 -> "$existing of $count already added"
                                else -> "${playlist.trackCount} songs"
                            }, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (complete && !loading) Icon(JukeIcons.Check, "Already added", tint = MaterialTheme.colorScheme.primary)
                    else TextButton(
                        onClick = { save(playlist.name) { onAdd(playlist) } },
                        enabled = !busy && !loading && error == null,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Add") }
                }
            }
        }
    }
}
