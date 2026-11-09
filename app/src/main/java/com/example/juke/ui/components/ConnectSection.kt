package com.example.juke.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.juke.jam.Connect
import com.example.juke.jam.JamResolver
import com.example.juke.ui.icons.JukeIcons
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private fun mmss(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

/**
 * Spotify Connect: your logged-in devices. Each plays on its own; every card shows what that device is playing,
 * with its own controls, and can bring its queue here or send this device's playback there.
 * Shown in the Jam sheet when logged in (the pairing-code handoff covers logged-out use).
 */
@Composable
fun ConnectSection() {
    val states by Connect.states.collectAsStateWithLifecycle()
    val devices by Connect.devices.collectAsStateWithLifecycle()
    val connected by Connect.connected.collectAsStateWithLifecycle()
    val message by Connect.message.collectAsStateWithLifecycle()

    HorizontalDivider()
    Text("Your devices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (!connected) { Text("Connecting…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); return }

    val others = devices.filter { it.deviceId != Connect.me }
    if (others.isEmpty()) {
        Text("Log in with the same account on your phone or computer to control playback from either.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val herePlaying = states[Connect.me]?.track != null
    for (d in others) DeviceCard(d, states[d.deviceId]?.takeIf { it.track != null }, canSendHere = herePlaying)
    if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun DeviceCard(d: Connect.Device, s: Connect.State?, canSendHere: Boolean) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        (if (d.kind == "desktop") "Computer" else "Phone") + " · " + when { s == null -> "Nothing playing"; s.playing -> "Playing"; else -> "Paused" },
                        style = MaterialTheme.typography.bodySmall, color = if (s?.playing == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canSendHere) TextButton({ Connect.playOn(d.deviceId) }) { Text("Play there") }
            }
            if (s == null) return@Column
            val song = s.track!!
            Row(verticalAlignment = Alignment.CenterVertically) {
                val art by produceState(song.thumb, song) { value = song.thumb ?: song.spotifyId?.let { JamResolver.artworkFor(listOf(it))[it] } ?: song.yt?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" } }
                AsyncImage(art, null, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // live position, ticking locally from that device's last report
            var pos by remember { mutableLongStateOf(Connect.livePos(s)) }
            var dragging by remember { mutableStateOf(false) }
            var drag by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(s) { while (true) { if (!dragging) pos = Connect.livePos(s); delay(500.milliseconds) } }
            val dur = song.durationSec * 1000L
            if (dur > 0) {
                Slider(
                    value = if (dragging) drag else (pos.toFloat() / dur).coerceIn(0f, 1f),
                    onValueChange = { dragging = true; drag = it },
                    onValueChangeFinished = { Connect.command(d.deviceId, "seek", posMs = (drag * dur).toLong()); pos = (drag * dur).toLong(); dragging = false }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(mmss(pos.coerceAtMost(dur)), style = MaterialTheme.typography.labelSmall)
                    Text(mmss(dur), style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton({ Connect.command(d.deviceId, "prev") }) { Icon(JukeIcons.Previous, "Previous on ${d.name}") }
                IconButton({ Connect.command(d.deviceId, "toggle") }, Modifier.size(56.dp)) {
                    Icon(if (s.playing) JukeIcons.Pause else JukeIcons.Play, if (s.playing) "Pause ${d.name}" else "Play ${d.name}", Modifier.size(36.dp))
                }
                IconButton({ Connect.command(d.deviceId, "next") }) { Icon(JukeIcons.Next, "Next on ${d.name}") }
            }
            // Join: both play the same queue, that device carries on. Move here: this one takes over and that one pauses.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ Connect.playHere(d.deviceId, move = false) }, Modifier.weight(1f)) { Text("Join") }
                androidx.compose.material3.Button({ Connect.playHere(d.deviceId, move = true) }, Modifier.weight(1f)) { Text("Move here") }
            }
            val upNext = s.queue.drop(1)
            if (upNext.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                TextButton({ open = !open }) { Text(if (open) "Hide up next" else "Up next (${upNext.size})") }
                if (open) {
                    // the device sends only web artwork; fill the rest from Spotify (cached), else YouTube's thumbnail
                    val art by produceState(emptyMap<String, String>(), upNext) {
                        value = JamResolver.artworkFor(upNext.filter { it.thumb == null }.mapNotNull { it.spotifyId })
                    }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        itemsIndexed(upNext) { i, q ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { Connect.command(d.deviceId, "index", index = i + 1) }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(q.thumb ?: q.spotifyId?.let { art[it] } ?: q.yt?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" }, null,
                                    Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(q.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(q.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
