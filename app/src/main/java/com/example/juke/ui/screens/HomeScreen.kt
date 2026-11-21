package com.example.juke.ui.screens

import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.semantics.selected
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
import com.example.juke.ui.components.ActionMenu
import com.example.juke.ui.components.MenuAction
import com.example.juke.ui.components.libraryTrackActions
import com.example.juke.ui.components.onRightClick
import com.example.juke.ui.components.spotifyTrackActions
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import com.example.juke.home.HomeSection
import com.example.juke.ui.screens.home.HomeSectionView
import com.example.juke.ui.theme.liquidGlass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    musicViewModel: MusicViewModel,
    homeViewModel: HomeViewModel = viewModel(),
    onSettingsClick: () -> Unit = {},
    onSeeAllClick: () -> Unit = {},
    onSearchClick: (query: String?) -> Unit = {},
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
            },
            onJamClick = {
                haptic.click()
                com.example.juke.jam.JamSession.sheetOpen.value = true
            }
        )

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { homeViewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            HomeFeed(
                state = uiState,
                musicViewModel = musicViewModel,
                onSeeAllClick = onSeeAllClick,
                onAlbumClick = onAlbumClick,
                onSearchClick = onSearchClick,
                onRetry = { homeViewModel.refresh() },
                onLoadMore = { homeViewModel.loadMore() },
                onMoodClick = { homeViewModel.selectMood(it) },
                bottomPadding = bottomPadding
            )
        }
    }
}

/**
 * The endless feed: the head, then a new page of sections whenever the listener comes within three
 * sections of the end. Sections fade and rise into place as they land.
 */
@Composable
private fun HomeFeed(
    state: HomeUiState,
    musicViewModel: MusicViewModel,
    onSeeAllClick: () -> Unit,
    onAlbumClick: (SpotifyAlbum) -> Unit,
    onSearchClick: (String?) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onMoodClick: (com.example.juke.home.YtmMoods.Mood) -> Unit,
    bottomPadding: Dp
) {
    val listState = rememberLazyListState()
    // Songs with no Spotify match are found on tap or long-press; Home drops them from then on.
    val misses by com.example.juke.home.FeedMisses.ids.collectAsStateWithLifecycle()
    val shown = remember(state.sections, misses) { com.example.juke.home.withoutMisses(state.sections, misses) }
    val inMood = state.selectedMood != null
    val emptyLibrary = !inMood && state.recentlyPlayed.isEmpty() && state.mostPlayed.isEmpty() && state.favorites.isEmpty()
    // A new mood starts at the top.
    LaunchedEffect(state.selectedMood) { if (listState.firstVisibleItemIndex > 1) listState.scrollToItem(0) }
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.sections.size, state.loadingMore) {
        if (nearEnd && !state.loadingMore && !state.endReached && !state.loadMoreFailed && !state.headPending) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp + bottomPadding)
    ) {
        // Always present (placeholder pills until the moods land): an item inserted above the first
        // visible one later would be scrolled out of view, since the list keeps its anchor.
        item(key = "moods", contentType = "moods") {
            if (state.moods.isEmpty()) MoodChipsPlaceholder()
            else MoodChips(state.moods, state.selectedMood, onMoodClick)
        }

        if (emptyLibrary) {
            item(key = "empty", contentType = "empty") {
                EmptyHomeState(
                    onSearchClick = { onSearchClick(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp)
                )
            }
        }
        if (state.headPending && state.sections.none { it is HomeSection.QuickPicks }) {
            item(key = "head_skeleton", contentType = "skeleton") { ListSectionSkeleton() }
        }
        if (state.discoveryOffline && !inMood) {
            item(key = "offline", contentType = "offline") { OfflineNotice(onRetry = onRetry) }
        }

        items(shown, key = { it.id }, contentType = { it::class.simpleName }) { section ->
            Column(Modifier.animateItem(fadeInSpec = tween(320), placementSpec = spring(dampingRatio = 0.85f, stiffness = 300f))) {
                HomeSectionView(section, musicViewModel, onAlbumClick, onSeeAllClick)
                Spacer(Modifier.height(28.dp))
            }
        }

        when {
            state.loadingMore -> item(key = "more_skeleton", contentType = "skeleton") {
                Column { CardRowSkeleton(); ListSectionSkeleton() }
            }
            state.loadMoreFailed -> item(key = "more_failed", contentType = "offline") { OfflineNotice(onRetry = onLoadMore) }
        }
    }
}

/**
 * YouTube Music's moods as clear liquid-glass pills. The picked one turns the feed into that mood;
 * picking it again goes back to "For you".
 */
@Composable
private fun MoodChips(
    moods: List<com.example.juke.home.YtmMoods.Mood>,
    selectedTitle: String?,
    onMoodClick: (com.example.juke.home.YtmMoods.Mood) -> Unit
) {
    val haptic = rememberJukeHaptics()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        items(moods, key = { it.params }) { mood ->
            val on = mood.title == selectedTitle
            Box(
                Modifier
                    .clip(GlassShapes.Pill)
                    .liquidGlass(
                        shape = GlassShapes.Pill,
                        // Picked: a brighter body of the same clear glass, never a colour tint.
                        fill = MaterialTheme.colorScheme.onSurface.copy(alpha = if (on) 0.22f else 0.05f),
                        blur = 2.dp,
                    )
                    .clickable(role = Role.Button, onClickLabel = if (on) "Back to For you" else "Show ${mood.title}") {
                        haptic.click()
                        onMoodClick(mood)
                    }
                    .semantics { selected = on }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    mood.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun MoodChipsPlaceholder() {
    Row(
        Modifier
            .padding(start = 16.dp, bottom = 4.dp)
            .height(38.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(64.dp, 92.dp, 84.dp, 96.dp, 72.dp).forEach {
            ShapedSkeletonBlock(Modifier.width(it).height(38.dp), GlassShapes.Pill)
        }
    }
}

@Composable
private fun HomeHeader(
    greeting: String,
    onSettingsClick: () -> Unit,
    onJamClick: () -> Unit
) {
    val inJam by com.example.juke.jam.JamSession.state.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
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
            onClick = onJamClick,
            contentDescription = if (inJam.active) "Jam ${inJam.code}" else "Jam"
        ) {
            Icon(
                Icons.Filled.Group,
                contentDescription = null,
                tint = if (inJam.active) MaterialTheme.colorScheme.primary else LocalContentColor.current
            )
        }

        Spacer(Modifier.width(12.dp))

        GlassIconButton(
            onClick = onSettingsClick,
            contentDescription = "Settings"
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null)
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
