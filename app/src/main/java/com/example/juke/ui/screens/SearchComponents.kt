package com.example.juke.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.SpotifyArtist
import com.example.juke.models.SpotifyPlaylist
import com.example.juke.models.Track
import com.example.juke.network.SpotifyApi
import com.example.juke.ui.components.GlassButton
import com.example.juke.ui.components.SearchResultItemM3
import com.example.juke.ui.components.SwipeToAddNextContainer
import com.example.juke.ui.components.TrackListSkeleton
import com.example.juke.ui.icons.JukeIcons
import com.example.juke.ui.theme.GlassCard
import com.example.juke.viewmodels.MusicViewModel
import com.example.juke.viewmodels.SearchViewModel
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal fun hasResults(uiState: com.example.juke.viewmodels.SearchUiState): Boolean {
    return uiState.tracks.isNotEmpty() ||
            uiState.localTracks.isNotEmpty() ||
            uiState.artists.isNotEmpty() ||
            uiState.playlists.isNotEmpty() ||
            uiState.albums.isNotEmpty()
}

@Composable
internal fun ImportPlaylistCard(
    playlist: SpotifyPlaylist,
    onImport: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = playlist.images.firstOrNull()?.url ?: "",
                contentDescription = null,
                modifier = Modifier
                    .size(132.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.tracks?.total ?: 0} tracks ready to download",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            GlassButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Icon(JukeIcons.PlaylistAdd, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import playlist", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun ImportProgressCard(progress: Int, total: Int) {
    val fraction = if (total > 0) progress.toFloat() / total else 0f
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$progress / $total tracks",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Text(
                "Importing playlist…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
    }
}

@Composable
internal fun SearchResultsList(
    uiState: com.example.juke.viewmodels.SearchUiState,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    musicViewModel: MusicViewModel,
    searchViewModel: SearchViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    isStreamMode: Boolean,
    onNavigateToArtist: (SpotifyArtist) -> Unit,
    onNavigateToPlaylist: (SpotifyPlaylist) -> Unit,
    onNavigateToAlbum: (SpotifyAlbum) -> Unit,
    bottomPadding: Dp,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController? = null
) {
    val hideKeyboardOnScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -5f) keyboardController?.hide()
                return Offset.Zero
            }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(selectedFilter, uiState.query) { listState.scrollToItem(0) }
    val preview = selectedFilter == "All"
    val localTracks = uiState.localTracks.distinctBy { it.uuid }.let { if (preview) it.take(2) else it }
    val songs = uiState.tracks.distinctBy { it.id ?: it.uri }.let { if (preview) it.take(3) else it }
    val artists = uiState.artists.distinctBy { it.id ?: it.uri ?: it.name }.let { if (preview) it.take(2) else it }
    val playlists = uiState.playlists.distinctBy { it.id }.let { if (preview) it.take(2) else it }
    val albums = uiState.albums.distinctBy { it.id ?: it.uri ?: it.name }.let { if (preview) it.take(2) else it }
    val categoryEmpty = when (selectedFilter) {
        "Songs" -> songs.isEmpty() && localTracks.isEmpty()
        "Artists" -> artists.isEmpty()
        "Albums" -> albums.isEmpty()
        "Playlists" -> playlists.isEmpty()
        else -> !hasResults(uiState)
    }
    if (categoryEmpty) {
        EmptySearchState(isQueryEmpty = false, isSearching = uiState.isSearching, bottomPadding = bottomPadding)
        return
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
        modifier = Modifier.nestedScroll(hideKeyboardOnScrollConnection)
    ) {
        // ── In Your Library ──────────────────────────────────────────────
        if ((selectedFilter == "All" || selectedFilter == "Songs") && uiState.localTracks.isNotEmpty()) {
            item { SearchSectionHeader("In your library", preview && uiState.localTracks.size > 2) { onFilterSelected("Songs") } }
            items(localTracks, key = { "local_${it.uuid}" }) { track ->
                SwipeToAddNextContainer(
                    onAddNext = {
                        musicViewModel.addNext(track)
                    }
                ) {
                    LocalTrackItem(
                        track = track,
                        onClick = { musicViewModel.setQueue(listOf(track), 0) },
                        showAccentBar = false,
                        onPlayNext = { musicViewModel.addNext(track) }
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // ── Songs ────────────────────────────────────────────────────────
        if ((selectedFilter == "All" || selectedFilter == "Songs") && uiState.tracks.isNotEmpty()) {
            item { SearchSectionHeader("Songs", preview && uiState.tracks.size > 3) { onFilterSelected("Songs") } }
            items(
                songs,
                key = { "song_${it.id ?: it.uri}" }) { track ->
                SwipeToAddNextContainer(
                    onAddNext = {
                        scope.launch {
                            searchViewModel.setDownloading(track.id)
                            try {
                                musicViewModel.queueSpotifyTrackNext(
                                    spotifyTrack = track,
                                    useStreamMode = isStreamMode
                                )
                            } finally {
                                searchViewModel.setDownloading(null)
                            }
                        }
                    }
                ) {
                    SearchResultItemM3(
                        track = track,
                        isDownloading = uiState.downloadingId == track.id,
                        onPlayNext = {
                            scope.launch {
                                searchViewModel.setDownloading(track.id)
                                try { musicViewModel.queueSpotifyTrackNext(track, useStreamMode = isStreamMode) }
                                finally { searchViewModel.setDownloading(null) }
                            }
                        },
                        onClick = {
                            scope.launch {
                                searchViewModel.setDownloading(track.id)
                                musicViewModel.downloadAndPlay(SpotifyApi.spotifyTrackToSong(track))
                                kotlinx.coroutines.delay(2000.milliseconds)
                                searchViewModel.setDownloading(null)
                            }
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if ((preview || selectedFilter == "Artists") && artists.isNotEmpty()) {
            item { SearchSectionHeader("Artists", preview && uiState.artists.size > 2) { onFilterSelected("Artists") } }
            items(artists, key = { "artist_${it.id ?: it.uri ?: it.name}" }) { artist ->
                SearchCollectionRow(artist.name, "Artist", artist.images.firstOrNull()?.url, roundArtwork = true) {
                    onNavigateToArtist(artist)
                }
            }
        }
        if ((preview || selectedFilter == "Albums") && albums.isNotEmpty()) {
            item { SearchSectionHeader("Albums", preview && uiState.albums.size > 2) { onFilterSelected("Albums") } }
            items(albums, key = { "album_${it.id ?: it.uri ?: it.name}" }) { album ->
                SearchCollectionRow(album.name, "${album.artists.joinToString(", ") { it.name }} · Album", album.images.firstOrNull()?.url) {
                    onNavigateToAlbum(album)
                }
            }
        }
        if ((preview || selectedFilter == "Playlists") && playlists.isNotEmpty()) {
            item { SearchSectionHeader("Playlists", preview && uiState.playlists.size > 2) { onFilterSelected("Playlists") } }
            items(playlists, key = { "playlist_${it.id}" }) { playlist ->
                SearchCollectionRow(playlist.name, "${playlist.tracks?.total ?: 0} songs · Playlist", playlist.images.firstOrNull()?.url) {
                    onNavigateToPlaylist(playlist)
                }
            }
        }
    }
}

// ── Section header ──────────────────────────────────────────────────────────
@Composable
internal fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp
        ),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)
    )
}

// ── Library track row (flat + left accent) ───────────────────────────────────
@Composable
internal fun LocalTrackItem(
    track: Track,
    onClick: () -> Unit,
    showAccentBar: Boolean = true,
    onPlayNext: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Primary-colored left accent bar
            if (showAccentBar) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(64.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.thumbnailUri ?: "",
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(JukeIcons.More, "Options for ${track.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Play now") }, onClick = { menuOpen = false; onClick() })
                        DropdownMenuItem(text = { Text("Play next") }, onClick = { menuOpen = false; onPlayNext() })
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 82.dp, end = 20.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    }
}

// ── Empty / idle state ───────────────────────────────────────────────────────
@Composable
internal fun EmptySearchState(
    isQueryEmpty: Boolean,
    isSearching: Boolean,
    bottomPadding: Dp
) {
    if (isSearching && !isQueryEmpty) {
        TrackListSkeleton(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp + bottomPadding)
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomPadding),
        contentAlignment = BiasAlignment(0f, -0.25f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            val icon = if (isQueryEmpty) JukeIcons.MusicNote else Icons.Outlined.SearchOff
            val title = if (isQueryEmpty) "Find your next song" else "No results found"
            val subtitle = if (isQueryEmpty) "Search for songs, artists, playlists or albums"
            else "Try a different spelling or keyword"

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = if (isQueryEmpty) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Recent searches ──────────────────────────────────────────────────────────
@Composable
internal fun RecentSearches(
    searches: List<String>,
    onSearchClick: (String) -> Unit,
    onRemoveClick: (String) -> Unit,
    onClearAll: () -> Unit,
    bottomPadding: Dp
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp, top = 4.dp, end = 12.dp, bottom = bottomPadding + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClearAll) {
                    Text(
                        "Clear all",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        items(searches) { query ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSearchClick(query) }
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onRemoveClick(query) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = JukeIcons.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        }
    }
}


@Composable
internal fun SearchInput(query: String, onQueryChange: (String) -> Unit, focusTrigger: Int, onSearch: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var field by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    LaunchedEffect(query) {
        if (field.text != query) field = TextFieldValue(query, TextRange(query.length))
    }
    LaunchedEffect(focusTrigger) {
        if (focusTrigger > 0) {
            field = field.copy(selection = TextRange(0, field.text.length))
            focus.requestFocus()
            keyboard?.show()
        }
    }
    OutlinedTextField(
        value = field,
        onValueChange = { field = it; if (it.text != query) onQueryChange(it.text) },
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = { Text("Songs, artists, albums…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(JukeIcons.Search, null) },
        trailingIcon = if (query.isNotEmpty()) {{ IconButton(onClick = { onQueryChange(""); focus.requestFocus() }) { Icon(JukeIcons.Close, "Clear search") } }} else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedBorderColor = Color.Transparent
        )
    )
}

@Composable
private fun SearchSectionHeader(title: String, showAll: Boolean, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (showAll) TextButton(onClick = onSeeAll) { Text("See all") }
    }
}

@Composable
private fun SearchCollectionRow(title: String, subtitle: String, image: String?, roundArtwork: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
        leadingContent = { AsyncImage(model = image, contentDescription = null,
            modifier = Modifier.size(48.dp).clip(if (roundArtwork) CircleShape else RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp)
    )
}
