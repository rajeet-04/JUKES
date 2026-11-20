package com.example.juke.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.juke.models.Track
import com.example.juke.services.QueueManager
import com.example.juke.ui.components.player.karaokeText
import com.example.juke.ui.components.player.rememberFrameClock
import com.example.juke.ui.components.playerArtworkEndpoint
import com.example.juke.ui.icons.JukeIcons
import com.example.juke.ui.theme.ExtractedColors

/** What the phone player's stage shows. The controls under it never move. */
enum class PlayerStage { Art, Lyrics, Queue, Mix }

private val Base = Color(0xFF0E0B0C)

/**
 * The player's backdrop: a dark wash of the artwork's deep colour at the top, fading to near-black
 * where the controls sit. The lyrics view gets a slightly warmer wash so the switch is felt.
 */
@Composable
internal fun ArtBackdrop(colors: ExtractedColors?, stage: PlayerStage, modifier: Modifier = Modifier) {
    val tint = colors?.secondary ?: Base
    val strength = if (stage == PlayerStage.Lyrics) 0.62f else 0.45f
    val top by animateColorAsState(lerp(Base, tint, strength), tween(450), label = "backdropTop")
    val mid by animateColorAsState(lerp(Base, tint, strength * 0.4f), tween(450), label = "backdropMid")
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to top, 0.5f to mid, 1f to Base))
    )
}

/**
 * The line being sung under the artwork, with the next line dimmed beneath it. The block has a
 * fixed height so the title and controls never shift as lines change. Tap opens the lyrics view.
 */
@Composable
internal fun LyricTicker(
    syncedLyrics: String,
    offsetMs: Long,
    position: Long,
    playing: Boolean,
    accent: Color,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lines = remember(syncedLyrics, offsetMs) { parseSyncedLyrics(syncedLyrics, offsetMs) }
    val index = lines.indexOfLast { it.timeMs <= position }
    val current = lines.getOrNull(index)
    val next = lines.getOrNull(index + 1)
    Row(
        modifier
            .fillMaxWidth()
            .height(TICKER_HEIGHT)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClickLabel = "Open lyrics", onClick = onOpen)
    ) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(accent))
        Spacer(Modifier.width(12.dp))
        AnimatedContent(
            targetState = current to next,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "lyricTicker",
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) { (now, upcoming) ->
            var nowLines by remember(now) { mutableIntStateOf(1) }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                val ink = Color.White
                val clock = if (now != null && now.words.isNotEmpty()) rememberFrameClock(position, playing) else null
                Text(
                    text = when {
                        now == null -> AnnotatedString(upcoming?.text ?: "♪")
                        clock != null -> karaokeText(now, clock(), ink, dimAlpha = 0.45f)
                        else -> AnnotatedString(now.text)
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 21.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (now == null) ink.copy(alpha = 0.55f) else ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { nowLines = it.lineCount }
                )
                // A two-line current line takes the whole block; the next line would only be clipped.
                if (now != null && upcoming != null && nowLines < 2) {
                    Text(
                        upcoming.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        color = ink.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

internal val TICKER_HEIGHT = 52.dp

/** The current song in one row, shown above the lyrics, queue and mix views. Tap returns to the art. */
@Composable
internal fun StageMiniHeader(
    track: Track,
    onOpenArt: () -> Unit,
    onFavourite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = "Show artwork", onClick = onOpenArt)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                // The player sheet lands on (and leaves from) this thumbnail while the art is hidden.
                .playerArtworkEndpoint(expanded = true)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            if (track.thumbnailUri != null) {
                AsyncImage(
                    model = track.thumbnailUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onFavourite) {
            Icon(
                if (track.isFavourite) JukeIcons.HeartSelected else JukeIcons.Heart,
                contentDescription = if (track.isFavourite) "Unfavorite" else "Favorite",
                tint = if (track.isFavourite) MaterialTheme.colorScheme.primary else Color.White
            )
        }
    }
}

/** Lyrics, Up next and Mix. Tapping the open tab goes back to the artwork. */
@Composable
internal fun StageDock(
    stage: PlayerStage,
    onSelect: (PlayerStage) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(PlayerStage.Lyrics to "Lyrics", PlayerStage.Queue to "Up next", PlayerStage.Mix to "Mix").forEach { (tab, label) ->
            val on = stage == tab
            val bg by animateColorAsState(
                if (on) Color.White.copy(alpha = 0.16f) else Color.Transparent, tween(180), label = "dockTab"
            )
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .semantics { selected = on }
                    .clickable(role = Role.Tab) { onSelect(if (on) PlayerStage.Art else tab) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = Color.White.copy(alpha = if (on) 1f else 0.7f)
                )
            }
        }
    }
}

/** Start a mix from the current song, and see what it has picked so far. */
@Composable
internal fun MixStage(
    track: Track,
    picked: List<QueueManager.PendingSong>,
    resolving: Int,
    onStartMix: () -> Unit,
    onPlayPicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "start") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(18.dp)
            ) {
                Text("Mix from this song", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "Replaces what's up next with songs that sound like ${track.title}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.68f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onStartMix)
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text("Start mix", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        if (picked.isNotEmpty() || resolving > 0) {
            item(key = "pickedHeader") {
                Text(
                    if (resolving > 0) "Picked for your mix · loading $resolving" else "Picked for your mix",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
        items(picked, key = { it.id }) { song ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = "Play ${song.title}") { onPlayPicked(song.id) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f))
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.bodyLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Crossfades the stage between views, with a slight grow on the way in. */
@Composable
internal fun StageSwitcher(
    stage: PlayerStage,
    modifier: Modifier = Modifier,
    content: @Composable (PlayerStage) -> Unit
) {
    AnimatedContent(
        targetState = stage,
        transitionSpec = {
            (fadeIn(tween(240, delayMillis = 60)) + scaleIn(tween(300), initialScale = 0.97f)) togetherWith fadeOut(tween(140))
        },
        label = "playerStage",
        modifier = modifier
    ) { content(it) }
}

/** Square artwork inset from the edges, with the lyric ticker under it when there are synced lyrics. */
@Composable
internal fun ArtSlot(
    hasTicker: Boolean,
    modifier: Modifier = Modifier,
    artwork: @Composable (Modifier) -> Unit,
    ticker: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val tickerSpace = if (hasTicker) TICKER_HEIGHT + 16.dp else 0.dp
        val side = minOf(maxWidth - 16.dp, maxHeight - tickerSpace).coerceAtLeast(0.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            artwork(Modifier.size(side))
            if (hasTicker) {
                Spacer(Modifier.height(16.dp))
                ticker(Modifier.width(side))
            }
        }
    }
}
