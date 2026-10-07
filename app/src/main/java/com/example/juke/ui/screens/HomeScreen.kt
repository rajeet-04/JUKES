package com.example.juke.ui.screens

import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.SpotifyTrack
import com.example.juke.models.Track
import com.example.juke.network.SpotifyApi
import com.example.juke.ui.components.GlassIconButton
import com.example.juke.ui.components.GlassPillButton
import com.example.juke.ui.components.HomeSkeleton
import com.example.juke.ui.components.ShapedSkeletonBlock
import com.example.juke.ui.icons.JukeIcons
import com.example.juke.ui.theme.GlassLevel
import com.example.juke.ui.theme.GlassShapes
import com.example.juke.ui.theme.glassPane
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.HomeUiState
import com.example.juke.viewmodels.HomeViewModel
import com.example.juke.viewmodels.MusicViewModel
import com.example.juke.viewmodels.RadioSection
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    musicViewModel: MusicViewModel,
    homeViewModel: HomeViewModel = viewModel(),
    onSettingsClick: () -> Unit = {},
    onSeeAllClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onAlbumClick: (SpotifyAlbum) -> Unit = {},
    bottomPadding: Dp = 0.dp
) {
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val haptic = rememberJukeHaptics()

    LaunchedEffect(Unit) {
        homeViewModel.loadHomeData()
    }

    // Fill the feed as soon as connectivity returns instead of waiting for a manual refresh.
    val context = LocalContext.current
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = homeViewModel.onNetworkAvailable()
        }
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }

    if (uiState.isLoading) {
        HomeSkeleton(bottomPadding = bottomPadding)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HomeHeader(
            greeting = uiState.greeting,
            onSettingsClick = {
                haptic.click()
                onSettingsClick()
            }
        )

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { homeViewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (uiState.recentlyPlayed.isEmpty() && uiState.mostPlayed.isEmpty() && uiState.favorites.isEmpty()) {
                EmptyHomeState(
                    onSearchClick = onSearchClick,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomPadding)
                )
            } else {
                HomeFeed(
                    state = uiState,
                    musicViewModel = musicViewModel,
                    onSeeAllClick = onSeeAllClick,
                    onAlbumClick = onAlbumClick,
                    onRetry = { homeViewModel.refresh() },
                    bottomPadding = bottomPadding
                )
            }
        }
    }
}

@Composable
private fun HomeFeed(
    state: HomeUiState,
    musicViewModel: MusicViewModel,
    onSeeAllClick: () -> Unit,
    onAlbumClick: (SpotifyAlbum) -> Unit,
    onRetry: () -> Unit,
    bottomPadding: Dp
) {
    val rotation = remember(state.recentlyPlayed, state.mostPlayed) {
        (state.recentlyPlayed + state.mostPlayed).distinctBy { it.uuid }.take(12)
    }
    val hasDiscovery = state.newReleases.isNotEmpty() || state.becauseYouPlayed.isNotEmpty() ||
            state.likeFavorites != null

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .animateContentSize(),
        contentPadding = PaddingValues(bottom = 24.dp + bottomPadding)
    ) {
        if (state.releasesPending && state.newReleases.isEmpty()) {
            item(key = "releases_skeleton") { CardRowSkeleton() }
        }
        if (state.discoveryOffline && !hasDiscovery) {
            item(key = "offline") { OfflineNotice(onRetry = onRetry) }
        }

        if (state.newReleases.isNotEmpty()) {
            item {
                SectionHeader(title = "New from artists you play")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.newReleases, key = { it.id.orEmpty() }) { album ->
                        ReleaseCard(album = album, onClick = { onAlbumClick(album) })
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }

        items(state.becauseYouPlayed, key = { it.seed.uuid }) { section ->
            SectionHeader(title = "Because you played ${section.seed.title}")
            RadioList(section = section, musicViewModel = musicViewModel)
            Spacer(Modifier.height(28.dp))
        }
        if (state.becausePending > 0) {
            items(state.becausePending, key = { "because_skeleton_$it" }) { ListSectionSkeleton() }
        }
        if (state.likePending && state.likeFavorites == null) {
            item(key = "like_skeleton") { CardRowSkeleton() }
        }

        state.likeFavorites?.let { section ->
            item {
                SectionHeader(title = "More like ${section.seed.title}")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(section.tracks, key = { it.id.orEmpty() }) { track ->
                        RadioCard(
                            track = track,
                            onClick = {
                                musicViewModel.playInstant(
                                    SpotifyApi.spotifyTrackToSong(
                                        track
                                    )
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }

        if (rotation.isNotEmpty()) {
            item {
                SectionHeader(title = "Back in rotation", onActionClick = onSeeAllClick)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(rotation, key = { _, t -> t.uuid }) { index, track ->
                        LibraryCard(
                            track = track,
                            onClick = { musicViewModel.setQueue(rotation, index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    greeting: String,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        GlassIconButton(
            onClick = onSettingsClick,
            contentDescription = "Settings"
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null)
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    "See All",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private val releaseDateFormat = DateTimeFormatter.ofPattern("MMM d")

@Composable
private fun ReleaseCard(album: SpotifyAlbum, onClick: () -> Unit) {
    val haptic = rememberJukeHaptics()
    val date = remember(album.releaseDate) {
        album.releaseDate?.let {
            runCatching {
                LocalDate.parse(it).format(releaseDateFormat)
            }.getOrNull()
        }
    }
    val artist = album.artists.joinToString(", ") { it.name }
    val kind = if (album.albumType == "single") "Single" else "Album"
    Column(
        modifier = Modifier
            .width(148.dp)
            .glassPane(GlassShapes.Card, GlassLevel.Regular)
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "${album.name}, $kind by $artist${date?.let { ", released $it" }.orEmpty()}"
            }
            .clickable(role = Role.Button, onClickLabel = "Open ${album.name}") {
                haptic.click()
                onClick()
            }
    ) {
        ArtImage(
            url = album.images.firstOrNull()?.url,
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
        )
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                album.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(artist, date).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RadioList(section: RadioSection, musicViewModel: MusicViewModel) {
    val haptic = rememberJukeHaptics()
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .glassPane(GlassShapes.Card, GlassLevel.Regular)
            .padding(vertical = 4.dp)
    ) {
        section.tracks.take(5).forEach { track ->
            val artist = track.artists.joinToString(", ") { it.name }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "Play ${track.name}") {
                        haptic.click()
                        musicViewModel.playInstant(SpotifyApi.spotifyTrackToSong(track))
                    }
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtImage(
                    url = track.album.images.lastOrNull { (it.width ?: 0) >= 128 }?.url
                        ?: track.album.images.firstOrNull()?.url,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp)
                ) {
                    Text(
                        track.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = {
                        haptic.click()
                        musicViewModel.queueSpotifyTrackNext(track)
                    }
                ) {
                    Icon(
                        JukeIcons.Queue,
                        contentDescription = "Play ${track.name} next",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioCard(track: SpotifyTrack, onClick: () -> Unit) {
    CoverCard(
        title = track.name,
        subtitle = track.artists.joinToString(", ") { it.name },
        imageUrl = track.album.images.firstOrNull()?.url,
        onClick = onClick,
        description = "${track.name} by ${track.artists.joinToString(", ") { it.name }}"
    )
}

@Composable
private fun LibraryCard(track: Track, onClick: () -> Unit) {
    CoverCard(
        title = track.title,
        subtitle = track.artist,
        imageUrl = track.thumbnailUri,
        onClick = onClick,
        description = "${track.title} by ${track.artist}"
    )
}

/** Square art with a glass caption plate; the shared shape for song lanes. */
@Composable
private fun CoverCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
    description: String
) {
    val haptic = rememberJukeHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "coverCardScale"
    )
    Box(
        modifier = Modifier
            .size(148.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glassPane(GlassShapes.Card, GlassLevel.Regular)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "Play $title"
            ) {
                haptic.click()
                onClick()
            }
    ) {
        ArtImage(url = imageUrl, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        startY = 160f
                    )
                )
        )
        Column(Modifier
            .align(Alignment.BottomStart)
            .padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ArtImage(url: String?, modifier: Modifier) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                JukeIcons.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun CardRowSkeleton() {
    Column(Modifier.padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ShapedSkeletonBlock(
            Modifier
                .padding(horizontal = 24.dp)
                .width(190.dp)
                .height(20.dp),
            RoundedCornerShape(6.dp)
        )
        Row(
            Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) { ShapedSkeletonBlock(Modifier.size(148.dp), GlassShapes.Card) }
        }
    }
}

@Composable
private fun ListSectionSkeleton() {
    Column(
        Modifier
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ShapedSkeletonBlock(Modifier
            .width(230.dp)
            .height(20.dp), RoundedCornerShape(6.dp))
        repeat(3) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ShapedSkeletonBlock(Modifier.size(52.dp), RoundedCornerShape(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShapedSkeletonBlock(
                        Modifier
                            .width(170.dp)
                            .height(14.dp),
                        RoundedCornerShape(5.dp)
                    )
                    ShapedSkeletonBlock(
                        Modifier
                            .width(110.dp)
                            .height(12.dp),
                        RoundedCornerShape(5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineNotice(onRetry: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .glassPane(GlassShapes.Card, GlassLevel.Thin)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "You're offline. New picks appear when you're back online.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        GlassPillButton(text = "Retry", onClick = onRetry)
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun EmptyHomeState(
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val isShort = maxHeight < 400.dp
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(if (isShort) 16.dp else 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (isShort) 48.dp else 80.dp)
                    .glassPane(CircleShape, GlassLevel.Thick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    JukeIcons.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(if (isShort) 28.dp else 40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(if (isShort) 12.dp else 24.dp))
            Text(
                "Welcome to JUKE",
                style = if (isShort) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Play a few songs and Home fills with picks from your history and new releases from your artists.",
                style = if (isShort) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp
            )
            Spacer(modifier = Modifier.height(if (isShort) 12.dp else 24.dp))
            GlassPillButton(text = "Find music", onClick = onSearchClick)
        }
    }
}
