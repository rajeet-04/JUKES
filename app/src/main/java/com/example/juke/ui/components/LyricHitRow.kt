package com.example.juke.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.juke.models.Track
import com.example.juke.ui.icons.JukeIcons
import com.example.juke.utils.TrackSearch

/**
 * Under a search result that matched on lyrics: the line that matched and, when the lyrics are synced,
 * a tap target that plays the song from that line.
 */
@Composable
fun LyricHitRow(track: Track, query: String, onPlayFrom: (Long) -> Unit) {
    val hit = remember(track.uuid, track.syncedLyrics, track.plainLyrics, query) { TrackSearch.lyricHit(track, query) } ?: return
    val start = hit.startMs
    val label = start?.let { "Play from %d:%02d".format(it / 60000, it / 1000 % 60) }
    Row(
        Modifier.fillMaxWidth()
            .then(if (start != null) Modifier.clickable { onPlayFrom(start) } else Modifier)
            .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(JukeIcons.MusicNote, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            "“${hit.text}”", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        if (label != null) {
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}
