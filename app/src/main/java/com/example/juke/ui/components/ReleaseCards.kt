package com.example.juke.ui.components

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.juke.models.SpotifyAlbum
import com.example.juke.ui.screens.home.ArtImage
import com.example.juke.ui.theme.GlassLevel
import com.example.juke.ui.theme.GlassShapes
import com.example.juke.ui.theme.glassPane
import com.example.juke.utils.rememberJukeHaptics

/** An artist's discography: the newest release on its own, then albums and singles/EPs, each newest first. */
data class Releases(val latest: SpotifyAlbum?, val albums: List<SpotifyAlbum>, val singles: List<SpotifyAlbum>)

fun splitReleases(all: List<SpotifyAlbum>): Releases {
    val sorted = all.distinctBy { it.id ?: it.name }.sortedByDescending { it.releaseDate.orEmpty() }
    val latest = sorted.firstOrNull()
    val rest = sorted.drop(1)
    val (singles, albums) = rest.partition { it.albumType == "single" }
    return Releases(latest, albums, singles)
}

/** "Single · 2025", "EP · 2024 · 4 songs", "Album · 2024 · 10 songs". */
fun releaseMeta(album: SpotifyAlbum): String {
    val tracks = album.totalTracks ?: 0
    val kind = when (album.albumType) {
        "single" -> if (tracks >= 3) "EP" else "Single"
        "compilation" -> "Compilation"
        else -> "Album"
    }
    val year = album.releaseDate?.take(4)?.takeIf { it.length == 4 }
    val songs = if (kind != "Single" && tracks > 1) "$tracks songs" else null
    return listOfNotNull(kind, year, songs).joinToString(" · ")
}

/** True for a full release date within the last [days] days; year-only dates never count. */
fun isRecent(releaseDate: String?, today: java.time.LocalDate = java.time.LocalDate.now(), days: Long = 60): Boolean {
    val d = releaseDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return false
    return !d.isAfter(today) && !d.isBefore(today.minusDays(days))
}

/** Adds the discography to an artist page: a latest-release feature, then side-scrolling lanes. */
fun LazyListScope.artistReleases(albums: List<SpotifyAlbum>, gutter: Dp, onOpen: (SpotifyAlbum) -> Unit) {
    val r = splitReleases(albums)
    r.latest?.let { latest ->
        item(key = "latest_release") {
            Column(Modifier.padding(top = 20.dp)) {
                ReleaseHeading("Latest release")
                LatestReleaseCard(latest) { onOpen(latest) }
            }
        }
    }
    if (r.albums.isNotEmpty()) item(key = "lane_albums") { ReleaseLane("Albums", r.albums, gutter, onOpen) }
    if (r.singles.isNotEmpty()) item(key = "lane_singles") { ReleaseLane("Singles & EPs", r.singles, gutter, onOpen) }
}

@Composable
private fun ReleaseHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
}

/** Wide glass card tinted by its own blurred artwork, so the newest release reads first. */
@Composable
private fun LatestReleaseCard(album: SpotifyAlbum, onClick: () -> Unit) {
    val art = album.images.firstOrNull()?.url
    PressScale(onClick, "${album.name}, ${releaseMeta(album)}", Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().glassPane(GlassShapes.Card, GlassLevel.Regular)) {
            if (art != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ArtImage(art, Modifier.matchParentSize().blur(48.dp).alpha(0.38f))
            }
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtImage(art, Modifier.size(112.dp).clip(ART_SHAPE).border(1.dp, Color.White.copy(alpha = 0.10f), ART_SHAPE))
                Column(Modifier.padding(start = 16.dp, end = 8.dp)) {
                    if (isRecent(album.releaseDate)) {
                        Text(
                            "NEW",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.6.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(album.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(releaseMeta(album), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

/** A titled side-scrolling row that runs to the screen edges past the page gutter. */
@Composable
private fun ReleaseLane(title: String, releases: List<SpotifyAlbum>, gutter: Dp, onOpen: (SpotifyAlbum) -> Unit) {
    Column(Modifier.padding(top = 24.dp)) {
        ReleaseHeading(title)
        LazyRow(
            modifier = Modifier.bleed(gutter),
            contentPadding = PaddingValues(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(releases, key = { it.id ?: it.name }) { album -> ReleaseTile(album) { onOpen(album) } }
        }
    }
}

/** Artwork on its own with the caption underneath: no card slab, so the cover is the shape. */
@Composable
private fun ReleaseTile(album: SpotifyAlbum, onClick: () -> Unit) {
    PressScale(onClick, "${album.name}, ${releaseMeta(album)}", Modifier.width(TILE)) {
        Column {
            ArtImage(album.images.firstOrNull()?.url, Modifier.size(TILE).clip(ART_SHAPE).border(1.dp, Color.White.copy(alpha = 0.08f), ART_SHAPE))
            Spacer(Modifier.height(8.dp))
            Text(album.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(releaseMeta(album), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PressScale(onClick: () -> Unit, description: String, modifier: Modifier, content: @Composable () -> Unit) {
    val haptic = rememberJukeHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "releasePress")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptic.click(); onClick() }
    ) { content() }
}

/** Widens a child by [gutter] on each side so it can scroll under the parent's padding. */
private fun Modifier.bleed(gutter: Dp) = layout { measurable, constraints ->
    val extra = (gutter * 2).roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

private val TILE = 148.dp
private val ART_SHAPE = RoundedCornerShape(18.dp)
