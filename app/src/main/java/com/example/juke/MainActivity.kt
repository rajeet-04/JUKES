package com.example.juke

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.juke.analytics.AnalyticsManager
import com.example.juke.models.GithubRelease
import com.example.juke.network.SpotifyApi
import com.example.juke.services.UpdateDownloadState
import com.example.juke.services.UpdateManager
import com.example.juke.ui.components.GlassNavBar
import com.example.juke.ui.components.GlassNavItem
import com.example.juke.ui.components.GlassNavRail
import com.example.juke.ui.components.MiniPlayer
import com.example.juke.ui.components.UpdateSheet
import com.example.juke.ui.icons.JukeIcons
import com.example.juke.ui.screens.AlbumDetailScreen
import com.example.juke.ui.screens.ArtistDetailScreen
import com.example.juke.ui.screens.AudioSettingsScreen
import com.example.juke.ui.screens.HomeScreen
import com.example.juke.ui.screens.LibraryScreen
import com.example.juke.ui.screens.PlayerScreen
import com.example.juke.ui.screens.PlaylistDetailScreen
import com.example.juke.ui.screens.SearchScreen
import com.example.juke.ui.theme.GlassBackdrop
import com.example.juke.ui.theme.JUKETheme
import com.example.juke.ui.theme.LocalHazeState
import com.example.juke.ui.theme.isGlassDark
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.AlbumDetailViewModel
import com.example.juke.viewmodels.MusicViewModel
import com.example.juke.viewmodels.PlaylistDetailViewModel
import com.example.juke.viewmodels.SearchViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt

sealed class Screen(
    val route: String,
    val title: String,
    val filledIcon: @Composable () -> Unit,
    val outlinedIcon: @Composable () -> Unit
) {
    object Home : Screen(
        "home",
        "Home",
        { Icon(JukeIcons.HomeSelected, contentDescription = "Home") },
        { Icon(JukeIcons.Home, contentDescription = "Home") })

    object Search : Screen(
        "search",
        "Search",
        { Icon(JukeIcons.Search, contentDescription = "Search") },
        { Icon(JukeIcons.Search, contentDescription = "Search") })

    object Library : Screen(
        "library",
        "Library",
        {
            Icon(
                imageVector = JukeIcons.LibrarySelected,
                contentDescription = "Library"
            )
        },
        { Icon(imageVector = JukeIcons.Library, contentDescription = "Library") })
}

class MainActivity : ComponentActivity() {

    private val showPlayerOnLaunch = mutableStateOf(false)
    private val pendingSpotifyLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        com.example.juke.ui.theme.GlassPrefs.solid =
            getSharedPreferences("ui_prefs", MODE_PRIVATE).getBoolean("solid_surfaces", false)

        // Check intent immediately
        handlePlayerIntent(intent)

        setContent {
            val musicViewModel: MusicViewModel = viewModel()
            val uiState by musicViewModel.uiState.collectAsStateWithLifecycle()

            JUKETheme(
                extractedColors = uiState.extractedColors
            ) {
                val navController = rememberNavController()
                val activityViewModelProvider = remember(this@MainActivity) {
                    ViewModelProvider(this@MainActivity)
                }

                // Initialize SpotifyApi with saved market code
                LaunchedEffect(Unit) {
                    val savedMarket = musicViewModel.marketCode.value
                    SpotifyApi.setDefaultMarket(savedMarket)
                }
                val context = LocalContext.current
                var showPlayerModal by remember { mutableStateOf(false) }
                var searchFocusTrigger by remember { mutableIntStateOf(0) }

                // --- UPDATE CHECK LOGIC ---
                var updateAvailable by remember { mutableStateOf<GithubRelease?>(null) }
                val updateDownloadState by UpdateManager.downloadState.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    // Yield the first frame before optional launch work.
                    withFrameNanos { }
                    musicViewModel.startDeferredStartupWork()
                    AnalyticsManager.getInstance(context).trackAppOpened()
                    updateAvailable = UpdateManager.checkForUpdates(context)


                }

                LaunchedEffect(updateDownloadState) {
                    val errorMessage =
                        (updateDownloadState as? UpdateDownloadState.Error)?.message ?: return@LaunchedEffect
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    UpdateManager.clearDownloadState()
                }

                if (updateAvailable != null || updateDownloadState is UpdateDownloadState.Ready) {
                    UpdateSheet(
                        release = updateAvailable,
                        state = updateDownloadState,
                        onStart = { release -> UpdateManager.startUpdateDownload(context, release) },
                        onDismiss = {
                            updateAvailable?.let { if (!it.isCritical) UpdateManager.snooze(context, it) }
                            updateAvailable = null
                            UpdateManager.clearDownloadState()
                        }
                    )
                }
                // --- END UPDATE CHECK LOGIC ---

                // Listen for changes to showPlayerOnLaunch
                LaunchedEffect(showPlayerOnLaunch.value) {
                    if (showPlayerOnLaunch.value) {
                        showPlayerModal = true
                        showPlayerOnLaunch.value = false
                    }
                }

                // Spotify link shared to / opened with JUKES: show it in Search
                LaunchedEffect(pendingSpotifyLink.value) {
                    val link = pendingSpotifyLink.value ?: return@LaunchedEffect
                    pendingSpotifyLink.value = null
                    navController.navigate(Screen.Search.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                    }
                    activityViewModelProvider[SearchViewModel::class.java].search(link)
                }

                val items = listOf(
                    Screen.Home,
                    Screen.Search,
                    Screen.Library
                )

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                var currentMainTab by remember { mutableStateOf(Screen.Home.route) }

                LaunchedEffect(currentRoute) {
                    if (
                        currentRoute == Screen.Home.route ||
                        currentRoute == Screen.Search.route ||
                        currentRoute == Screen.Library.route
                    ) {
                        currentMainTab = currentRoute
                    }
                }

                val windowWidth = with(LocalDensity.current) {
                    LocalWindowInfo.current.containerSize.width.toDp()
                }
                val isExpanded = windowWidth >= 600.dp
                // Scrolling down a list folds the tab bar into one button beside the mini player.
                val chromeThresholdPx = with(LocalDensity.current) { 40.dp.toPx() }
                val chrome = remember(chromeThresholdPx) { com.example.juke.ui.components.CollapsingChrome(chromeThresholdPx) }
                LaunchedEffect(currentRoute) { chrome.expand() } // a new screen starts unfolded
                val hasMiniPlayer = musicViewModel.uiState.collectAsStateWithLifecycle().value.currentTrack != null
                val merged = chrome.collapsed && !isExpanded && hasMiniPlayer
                // Artist/album/playlist are child screens: they can be opened on top of any tab
                // (and on top of each other), so a tab tap must first dispose of all of them.
                fun isDetailRoute(route: String?) =
                    route != null && (route.startsWith("artist/") ||
                        route.startsWith("album/") || route.startsWith("playlist/"))

                val onNavigate: (Screen) -> Unit = { screen ->
                    val wasDetail = isDetailRoute(currentRoute)
                    if (wasDetail) {
                        activityViewModelProvider[SearchViewModel::class.java].clearArtistDetail()
                        activityViewModelProvider[AlbumDetailViewModel::class.java].clearAlbumDetail()
                        activityViewModelProvider[PlaylistDetailViewModel::class.java].clearPlaylistDetail()
                        // Pop before navigating so saveState never captures a child screen.
                        while (isDetailRoute(navController.currentDestination?.route) &&
                            navController.popBackStack()
                        ) { /* keep popping */ }
                    }

                    if (navController.currentDestination?.route == screen.route) {
                        // Already on Search: select the query and open the keyboard for typing.
                        if (!wasDetail && screen == Screen.Search) searchFocusTrigger++
                    } else {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                }

                val hazeState = remember { HazeState() }
                val haptic = rememberJukeHaptics()
                val navItems = items.map { screen ->
                    GlassNavItem(
                        label = screen.title,
                        selected = currentMainTab == screen.route,
                        onClick = { haptic.nav(); onNavigate(screen) },
                        icon = { if (currentMainTab == screen.route) screen.filledIcon() else screen.outlinedIcon() }
                    )
                }

                com.example.juke.ui.components.PlayerTransitionHost(expanded = showPlayerModal, musicViewModel = musicViewModel) {
                CompositionLocalProvider(LocalHazeState provides hazeState) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    bottomBar = {
                        if (currentRoute != "settings") {
                            // One progress value (0 = two rows, 1 = merged) drives every part of the
                            // fold, read only in layout/draw: the tab button's width, scale and fade and
                            // the tab bar's height (gap included) and fade move together and land on the
                            // same frame. Nothing is clipped and no gap appears or vanishes in one frame.
                            val foldProgress = androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (merged) 1f else 0f,
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = 1f, // no overshoot: sizes never go negative
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                ),
                                label = "chromeFold"
                            )
                            val showTabButton by remember { derivedStateOf { foldProgress.value > 0f } }
                            val showTabBar by remember { derivedStateOf { foldProgress.value < 1f } }
                            Column(
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 8.dp)
                            ) {
                                // The backend is downloading the tapped song before it can play.
                                val preparing by com.example.juke.network.JukesApi.preparing.collectAsStateWithLifecycle()
                                androidx.compose.animation.AnimatedVisibility(visible = preparing != null) {
                                    // The gap travels with the pill, so it never pops in or out.
                                    Box(Modifier.padding(bottom = 8.dp)) { PreparingPill(title = preparing.orEmpty()) }
                                }
                                // One composable slot for the mini player in both layouts, so its state
                                // (live lyric, swipe) carries through the fold.
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (showTabButton) {
                                        val tab = items.firstOrNull { it.route == currentMainTab } ?: items.first()
                                        Box(
                                            Modifier.layout { measurable, _ ->
                                                // Full size always (never squeezed or clipped); only the
                                                // space it takes grows, button + 8 dp gap.
                                                val size = 56.dp.roundToPx()
                                                val placeable = measurable.measure(
                                                    androidx.compose.ui.unit.Constraints.fixed(size, size)
                                                )
                                                val width = ((size + 8.dp.roundToPx()) * foldProgress.value).roundToInt()
                                                layout(width, size) {
                                                    placeable.placeRelativeWithLayer(0, 0) {
                                                        val t = foldProgress.value
                                                        // Grows from its left edge at the slot's rate,
                                                        // so it always fits the space it is opening.
                                                        alpha = t
                                                        scaleX = t
                                                        scaleY = t
                                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                                                    }
                                                }
                                            }
                                        ) {
                                            com.example.juke.ui.components.CollapsedTabButton(
                                                label = tab.title,
                                                onClick = { chrome.expand() },
                                                icon = { tab.filledIcon() }
                                            )
                                        }
                                    }
                                    MiniPlayer(
                                        musicViewModel = musicViewModel,
                                        onExpand = { showPlayerModal = true },
                                        modifier = Modifier.weight(1f),
                                        compact = merged
                                    )
                                }
                                if (!isExpanded && showTabBar) {
                                    Box(
                                        Modifier.layout { measurable, constraints ->
                                            // The bar keeps its size and sinks while its slot (8 dp gap +
                                            // bar) shrinks to nothing.
                                            val gap = 8.dp.roundToPx()
                                            val placeable = measurable.measure(constraints.copy(minHeight = 0))
                                            val keep = 1f - foldProgress.value
                                            val height = ((gap + placeable.height) * keep).roundToInt()
                                            layout(placeable.width, height) {
                                                placeable.placeRelativeWithLayer(0, gap) { alpha = keep }
                                            }
                                        }
                                    ) {
                                        GlassNavBar(items = navItems)
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    val layoutDirection = LocalLayoutDirection.current
                    // While the chrome is folded, lists keep the unfolded padding: shrinking it would
                    // resize every list mid-scroll (and could fold/unfold it back and forth).
                    val barPadding = innerPadding.calculateBottomPadding()
                    val unfolded = remember { arrayOf(barPadding) } // plain holder: no extra recomposition
                    if (!merged) unfolded[0] = barPadding
                    val bottomPadding = if (merged) maxOf(barPadding, unfolded[0]) else barPadding
                    val contentPadding = PaddingValues(
                        start = innerPadding.calculateStartPadding(layoutDirection),
                        top = 0.dp,
                        end = innerPadding.calculateEndPadding(layoutDirection),
                        bottom = 0.dp
                    )

                    Box(modifier = Modifier.fillMaxSize()) {
                    // Backdrop source: the ambient light field and every screen scroll inside it,
                    // so the floating glass (tab bar, mini player) blurs what is really behind it.
                    Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
                    Box(Modifier.fillMaxSize().background(GlassBackdrop.color(isGlassDark())))
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (isExpanded && currentRoute != "settings") {
                            GlassNavRail(items = navItems, modifier = Modifier.statusBarsPadding())
                        }
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Home.route,
                            modifier = Modifier
                                .weight(1f)
                                .padding(contentPadding)
                                .nestedScroll(chrome.connection)
                        ) {
                            composable(Screen.Home.route) {
                                HomeScreen(
                                    musicViewModel = musicViewModel,
                                    onSettingsClick = { navController.navigate("settings") },
                                    onSearchClick = { onNavigate(Screen.Search) },
                                    onSeeAllClick = {
                                        navController.navigate(Screen.Library.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onAlbumClick = { album ->
                                        activityViewModelProvider[AlbumDetailViewModel::class.java]
                                            .loadAlbumDetails(album)
                                        navController.navigate("album/${album.id}")
                                    },
                                    bottomPadding = bottomPadding
                                )
                            }
                            composable(Screen.Search.route) {
                                val searchViewModel =
                                    activityViewModelProvider[SearchViewModel::class.java]
                                SearchScreen(
                                    musicViewModel = musicViewModel,
                                    searchViewModel = searchViewModel,
                                    searchFocusTrigger = searchFocusTrigger,
                                    onNavigateToArtist = { artist ->
                                        searchViewModel.loadArtistDetails(artist)
                                        navController.navigate("artist/${artist.id}")
                                    },
                                    onNavigateToPlaylist = { playlist ->
                                        activityViewModelProvider[PlaylistDetailViewModel::class.java]
                                            .loadPlaylistDetails(playlist)
                                        navController.navigate("playlist/${playlist.id}")
                                    },
                                    onNavigateToAlbum = { album ->
                                        activityViewModelProvider[AlbumDetailViewModel::class.java]
                                            .loadAlbumDetails(album)
                                        navController.navigate("album/${album.id}")
                                    },
                                    bottomPadding = bottomPadding
                                )
                            }
                            composable(Screen.Library.route) {
                                LibraryScreen(
                                    musicViewModel = musicViewModel,
                                    bottomPadding = bottomPadding,
                                    onAlbumClick = { album ->
                                        activityViewModelProvider[AlbumDetailViewModel::class.java].loadAlbumDetails(album)
                                        navController.navigate("album/${album.id}")
                                    }
                                )
                            }
                            composable("settings") {
                                AudioSettingsScreen(
                                    musicViewModel = musicViewModel,
                                    onNavigateBack = {
                                        navController.popBackStack()
                                    },
                                    onNavigateToPurge = {
                                        navController.navigate("settings/purge")
                                    },
                                    onNavigateToPowerTools = {
                                        navController.navigate("settings/power")
                                    }
                                )
                            }
                            composable("settings/power") {
                                com.example.juke.ui.screens.PowerToolsScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    bottomPadding = bottomPadding
                                )
                            }
                            composable("settings/purge") {
                                com.example.juke.ui.screens.PurgeSelectionScreen(
                                    musicViewModel = musicViewModel,
                                    onNavigateBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                            composable("artist/{artistId}") {
                                val searchViewModel =
                                    activityViewModelProvider[SearchViewModel::class.java]
                                ArtistDetailScreen(
                                    searchViewModel = searchViewModel,
                                    musicViewModel = musicViewModel,
                                    onNavigateBack = {
                                        searchViewModel.clearArtistDetail()
                                        navController.popBackStack()
                                    },
                                    onNavigateToAlbum = { album ->
                                        activityViewModelProvider[AlbumDetailViewModel::class.java]
                                            .loadAlbumDetails(album)
                                        navController.navigate("album/${album.id}")
                                    },
                                    bottomPadding = bottomPadding
                                )
                            }
                            composable("playlist/{playlistId}") {
                                val playlistDetailViewModel =
                                    activityViewModelProvider[PlaylistDetailViewModel::class.java]
                                PlaylistDetailScreen(
                                    playlistDetailViewModel = playlistDetailViewModel,
                                    musicViewModel = musicViewModel,
                                    onNavigateBack = {
                                        playlistDetailViewModel.clearPlaylistDetail()
                                        navController.popBackStack()
                                    },
                                    bottomPadding = bottomPadding
                                )
                            }
                            composable("album/{albumId}") {
                                val albumDetailViewModel =
                                    activityViewModelProvider[AlbumDetailViewModel::class.java]
                                AlbumDetailScreen(
                                    albumDetailViewModel = albumDetailViewModel,
                                    musicViewModel = musicViewModel,
                                    onNavigateBack = {
                                        albumDetailViewModel.clearAlbumDetail()
                                        navController.popBackStack()
                                    },
                                    bottomPadding = bottomPadding
                                )
                            }
                        }
                    }
                    }
                    }
                }
                }

                // Player Modal
                com.example.juke.ui.components.PlayerTransitionContent(onDismiss = { showPlayerModal = false }) {
                    PlayerScreen(
                        musicViewModel = musicViewModel,
                        onDismiss = { showPlayerModal = false },
                        onNavigateToArtist = { artistId ->
                            showPlayerModal = false
                            activityViewModelProvider[SearchViewModel::class.java]
                                .loadArtistDetailsById(artistId)
                            navController.navigate("artist/$artistId")
                        },
                        onNavigateToAlbum = { albumId ->
                            showPlayerModal = false
                            activityViewModelProvider[AlbumDetailViewModel::class.java]
                                .loadAlbumDetailsById(albumId)
                            navController.navigate("album/$albumId")
                        },
                        onShareTrack = { spotifyId ->
                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "https://open.spotify.com/track/$spotifyId"
                                )
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        }
                    )
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlayerIntent(intent)
    }

    private fun handlePlayerIntent(intent: Intent?) {
        val text = intent?.dataString ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (text != null && Regex("""open\.spotify\.com/|spotify:|spotify(\.app)?\.link/""").containsMatchIn(text)) {
            pendingSpotifyLink.value = text
        }
        if (intent?.getBooleanExtra("open_player", false) == true) {
            showPlayerOnLaunch.value = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Track app closed and end session
        AnalyticsManager.getIfInitialized()?.let { analytics ->
            analytics.trackAppClosed()
            analytics.endSession()
        }
    }
}

@Composable
private fun PreparingPill(title: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
            Text(
                text = "Preparing $title…",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
