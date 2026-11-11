package com.example.juke.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.ui.text.style.TextAlign
import com.example.juke.ui.components.player.PlaybackSeekSlider
import com.example.juke.ui.theme.GlassLevel
import com.example.juke.ui.theme.GlassShapes
import com.example.juke.ui.theme.glassPane
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

    Spacer(Modifier.height(4.dp))
    HorizontalDivider()
    Text("Your devices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
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
    val playing = s?.playing == true
    Column(
        Modifier.fillMaxWidth().glassPane(GlassShapes.Card, GlassLevel.Thick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // who and what state, and sending this device's music there
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (d.kind == "desktop") "Computer" else "Phone") + " · " + when { s == null -> "Nothing playing"; playing -> "Playing"; else -> "Paused" },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canSendHere) TextButton({ Connect.playOn(d.deviceId) }) { Text("Play there") }
        }
        if (s == null) return@Column
        val song = s.track!!

        // the song, with that device's transport beside it (like the mini player)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val art by produceState(song.thumb, song) { value = song.thumb ?: song.spotifyId?.let { JamResolver.artworkFor(listOf(it))[it] } ?: song.yt?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" } }
            AsyncImage(art, null, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton({ Connect.command(d.deviceId, "prev") }, Modifier.size(40.dp)) { Icon(JukeIcons.Previous, "Previous on ${d.name}", Modifier.size(20.dp)) }
            FilledIconButton({ Connect.command(d.deviceId, "toggle") }, Modifier.size(44.dp)) {
                Icon(if (playing) JukeIcons.Pause else JukeIcons.Play, if (playing) "Pause ${d.name}" else "Play ${d.name}", Modifier.size(22.dp))
            }
            IconButton({ Connect.command(d.deviceId, "next") }, Modifier.size(40.dp)) { Icon(JukeIcons.Next, "Next on ${d.name}", Modifier.size(20.dp)) }
        }

        // the player's seek bar; a remote seeks once, on release
        val dur = song.durationSec * 1000L
        if (dur > 0) {
            var pos by remember { mutableLongStateOf(Connect.livePos(s)) }
            var drag by remember { mutableStateOf<Long?>(null) }
            LaunchedEffect(s) { while (true) { if (drag == null) pos = Connect.livePos(s).coerceAtMost(dur); delay(500.milliseconds) } }
            val shown = drag ?: pos
            val time = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mmss(shown), style = time, modifier = Modifier.width(36.dp))
                PlaybackSeekSlider(
                    positionMs = shown, durationMs = dur, thumbSize = 14.dp,
                    onSeek = { drag = it },
                    onSeekEnd = { drag?.let { Connect.command(d.deviceId, "seek", posMs = it); pos = it }; drag = null },
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                )
                Text(mmss(dur), style = time, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
            }
        }

        // bring it here: Join keeps that device playing, Move here stops it
        val nowAt = s.index.coerceIn(0, maxOf(s.queue.lastIndex, 0))
        val upNext = s.queue.drop(nowAt + 1)
        var open by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ Connect.playHere(d.deviceId, move = false) }, Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Join") }
            Button({ Connect.playHere(d.deviceId, move = true) }, Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Move here") }
            if (upNext.isNotEmpty()) IconToggleButton(open, { open = it }, Modifier.size(40.dp)) {
                Icon(JukeIcons.Queue, if (open) "Hide up next" else "Up next on ${d.name} (${upNext.size})",
                    tint = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (open && upNext.isNotEmpty()) {
            // the device sends only web artwork; fill the rest from Spotify (cached), else YouTube's thumbnail
            val art by produceState(emptyMap<String, String>(), upNext) {
                value = JamResolver.artworkFor(upNext.filter { it.thumb == null }.mapNotNull { it.spotifyId })
            }
            Text("Up next · ${upNext.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                itemsIndexed(upNext) { i, q ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { Connect.command(d.deviceId, "index", index = nowAt + 1 + i) }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(q.thumb ?: q.spotifyId?.let { art[it] } ?: q.yt?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" }, null,
                            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
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
