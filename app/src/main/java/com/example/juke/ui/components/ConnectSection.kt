package com.example.juke.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.example.juke.ui.icons.JukeIcons
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private fun mmss(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

/**
 * Spotify Connect: your logged-in devices, what's playing on them, and the controls for it.
 * Shown in the Jam sheet when logged in (the pairing-code handoff covers logged-out use).
 */
@Composable
fun ConnectSection() {
    val state by Connect.state.collectAsStateWithLifecycle()
    val devices by Connect.devices.collectAsStateWithLifecycle()
    val connected by Connect.connected.collectAsStateWithLifecycle()
    val message by Connect.message.collectAsStateWithLifecycle()

    HorizontalDivider()
    Text("Your devices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (!connected) { Text("Connecting…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); return }

    val s = state
    val remote = Connect.isRemote(s)
    if (remote && s != null) {
        val song = s.track!!
        Text("Playing on ${s.activeName}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(song.thumb, null, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // live position, ticking locally from the playing device's last report
        var pos by remember { mutableLongStateOf(Connect.livePos(s)) }
        var dragging by remember { mutableStateOf(false) }
        var drag by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(s) { while (true) { if (!dragging) pos = Connect.livePos(s); delay(500.milliseconds) } }
        val dur = song.durationSec * 1000L
        if (dur > 0) {
            Slider(
                value = if (dragging) drag else (pos.toFloat() / dur).coerceIn(0f, 1f),
                onValueChange = { dragging = true; drag = it },
                onValueChangeFinished = { Connect.command("seek", posMs = (drag * dur).toLong()); pos = (drag * dur).toLong(); dragging = false }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(mmss(pos.coerceAtMost(dur)), style = MaterialTheme.typography.labelSmall)
                Text(mmss(dur), style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton({ Connect.command("prev") }) { Icon(JukeIcons.Previous, "Previous") }
            IconButton({ Connect.command("toggle") }, Modifier.size(56.dp)) {
                Icon(if (s.playing) JukeIcons.Pause else JukeIcons.Play, if (s.playing) "Pause" else "Play", Modifier.size(36.dp))
            }
            IconButton({ Connect.command("next") }) { Icon(JukeIcons.Next, "Next") }
        }
        Button({ Connect.playHere() }, Modifier.fillMaxWidth()) { Text("Play on this device") }
        if (s.queue.size > 1) {
            Text("Up next on ${s.activeName}", style = MaterialTheme.typography.labelLarge)
            s.queue.drop(1).take(8).forEachIndexed { i, q ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { Connect.command("index", index = i + 1) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(q.thumb, null, Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(q.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(q.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    val others = devices.filter { it.deviceId != Connect.me }
    if (others.isEmpty()) {
        Text("Log in with the same account on your phone or computer to control playback from either.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    for (d in others) {
        val playingThere = s?.activeId == d.deviceId && s.track != null
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.bodyLarge)
                Text(if (playingThere) (if (s!!.playing) "Playing" else "Paused") else if (d.kind == "desktop") "Computer" else "Phone",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!playingThere) OutlinedButton({ Connect.playOn(d.deviceId) }, enabled = s?.track != null) { Text("Play there") }
        }
    }
    if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall)
}
