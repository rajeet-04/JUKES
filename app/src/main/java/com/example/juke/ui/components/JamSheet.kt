package com.example.juke.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.juke.jam.JamItem
import com.example.juke.jam.JamSession
import com.example.juke.network.SpotifyApi
import com.example.juke.repositories.SharedTrack
import com.example.juke.repositories.toSpotifyTrack
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.launch

/** Start, join and run a Jam: its code, who is in it, and the shared queue. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JamSheet(musicViewModel: MusicViewModel, onDismiss: () -> Unit) {
    val state by JamSession.state.collectAsStateWithLifecycle()
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Jam", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (state.active) ActiveJam(state, musicViewModel) else StartOrJoin(state.message)
        }
    }
}

@Composable
private fun StartOrJoin(message: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(JamSession.savedName(context)) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Text(
        "Listen together: one shared queue for everyone with the link. No account needed.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(24) },
        label = { Text("Your name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        enabled = !busy && name.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            busy = true
            scope.launch {
                val created = JamSession.create()
                if (created == null) Toast.makeText(context, "Couldn't start a Jam. Check your connection.", Toast.LENGTH_SHORT).show()
                else JamSession.join(context, created, name)
                busy = false
            }
        }
    ) { Text(if (busy) "Starting…" else "Start a Jam") }

    HorizontalDivider()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isLetterOrDigit).take(6).uppercase() },
            label = { Text("Jam code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            enabled = code.length == 6 && name.isNotBlank(),
            onClick = { JamSession.join(context, code, name) },
            modifier = Modifier.padding(top = 8.dp)
        ) { Text("Join") }
    }
    message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun ActiveJam(state: JamSession.UiState, musicViewModel: MusicViewModel) {
    val context = LocalContext.current
    val code = state.code.orEmpty()

    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(code, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Join my Jam on JUKES: ${JamSession.link(code)}")
                }
                context.startActivity(Intent.createChooser(send, null))
            }) { Text("Share link") }
            OutlinedButton(onClick = { JamSession.leave() }) { Text("Leave") }
        }
    }
    Text(
        when (state.status) {
            JamSession.Status.CONNECTED -> state.members.joinToString(", ") { if (it.clientId == state.you) "${it.name} (you)" else it.name }
            JamSession.Status.RECONNECTING -> "Reconnecting…"
            else -> "Connecting…"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

    HorizontalDivider()
    Text("Queue (${state.queue.size})", style = MaterialTheme.typography.titleMedium)
    if (state.queue.isEmpty()) {
        Text(
            "Nothing yet. Press and hold a song on Home and pick Add to Jam.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
        itemsIndexed(state.queue, key = { _, item -> item.id }) { index, item ->
            JamRow(
                item = item,
                canMoveUp = index > 0,
                onPlay = {
                    val track = SharedTrack(item.title, item.artist, item.durationSec, item.spotifyId, null, item.thumb).toSpotifyTrack()
                    if (track != null) musicViewModel.playInstant(SpotifyApi.spotifyTrackToSong(track))
                },
                onMoveUp = { JamSession.move(item.id, state.queue[index - 1].id) },
                onRemove = { JamSession.remove(item.id) }
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun JamRow(item: JamItem, canMoveUp: Boolean, onPlay: () -> Unit, onMoveUp: () -> Unit, onRemove: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onPlay() },
        leadingContent = {
            AsyncImage(
                model = item.thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
            )
        },
        headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                if (item.addedBy.isBlank()) item.artist else "${item.artist} · ${item.addedBy}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Row {
                if (canMoveUp) IconButton(onClick = onMoveUp) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove") }
            }
        }
    )
}
