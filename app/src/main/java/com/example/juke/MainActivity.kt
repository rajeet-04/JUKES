package com.example.juke

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import com.example.juke.ui.components.GlassAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.juke.analytics.AnalyticsManager
import com.example.juke.models.GithubRelease
import com.example.juke.network.SpotifyApi
import com.example.juke.services.DownloadedUpdate
import com.example.juke.services.UpdateManager
import com.example.juke.services.UpdateDownloadState
import com.example.juke.ui.components.GlassNavBar
import com.example.juke.ui.components.GlassNavItem
import com.example.juke.ui.components.GlassNavRail
import com.example.juke.ui.components.MiniPlayer
import com.example.juke.ui.theme.GlassBackdrop
import com.example.juke.ui.theme.isGlassDark
import com.example.juke.ui.theme.LocalHazeState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import com.example.juke.ui.screens.AlbumDetailScreen
import com.example.juke.ui.screens.ArtistDetailScreen
import com.example.juke.ui.screens.AudioSettingsScreen
import com.example.juke.ui.screens.HomeScreen
import com.example.juke.ui.screens.LibraryScreen
import com.example.juke.ui.screens.PlayerScreen
import com.example.juke.ui.screens.PlaylistDetailScreen
import com.example.juke.ui.screens.SearchScreen
import com.example.juke.ui.theme.JUKETheme
import com.example.juke.viewmodels.AlbumDetailViewModel
import com.example.juke.viewmodels.MusicViewModel
import com.example.juke.viewmodels.PlaylistDetailViewModel
import com.example.juke.viewmodels.SearchViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

sealed class Screen(
    val route: String,
    val title: String,
    val filledIcon: @Composable () -> Unit,
    val outlinedIcon: @Composable () -> Unit
) {
    object Home : Screen(
        "home",
        "Home",
        { Icon(Icons.Filled.Home, contentDescription = "Home") },
        { Icon(Icons.Outlined.Home, contentDescription = "Home") })

    object Search : Screen(
        "search",
        "Search",
        { Icon(Icons.Filled.Search, contentDescription = "Search") },
        { Icon(Icons.Outlined.Search, contentDescription = "Search") })

    object Library : Screen(
        "library",
        "Library",
        {
            Icon(
                painter = painterResource(R.drawable.library_outlined),
                contentDescription = "Library"
            )
        },
        { Icon(painter = painterResource(R.drawable.library), contentDescription = "Library") })
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val showPlayerOnLaunch = mutableStateOf(false)

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
                var searchResetTrigger by remember { mutableIntStateOf(0) }
                var searchFocusTrigger by remember { mutableIntStateOf(0) }

                // --- UPDATE CHECK LOGIC ---
                var updateAvailable by remember { mutableStateOf<GithubRelease?>(null) }
                val updateDownloadState by UpdateManager.downloadState.collectAsStateWithLifecycle()
                val isUpdateDownloading = updateDownloadState is UpdateDownloadState.Downloading

                LaunchedEffect(Unit) {
                    // Yield the first frame before optional launch work.
                    withFrameNanos { }
                    musicViewModel.startDeferredStartupWork()
                    AnalyticsManager.getInstance(context).trackAppOpened()
                    updateAvailable = UpdateManager.checkForUpdates()


                }

                LaunchedEffect(updateDownloadState) {
                    val errorMessage =
                        (updateDownloadState as? UpdateDownloadState.Error)?.message ?: return@LaunchedEffect
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    UpdateManager.clearDownloadState()
                }

                if (updateAvailable != null) {
                    val release = updateAvailable!!

                    // Determine if the update is an emergency update (e.g., contains "emergency" or "hotfix" in tags or body)
                    val isEmergency = release.tagName.contains("emergency", ignoreCase = true) ||
                            release.tagName.contains("hotfix", ignoreCase = true) ||
                            (release.body?.contains("emergency", ignoreCase = true) == true) ||
                            (release.body?.contains("critical", ignoreCase = true) == true) ||
                            (release.body?.contains("hotfix", ignoreCase = true) == true)

                    GlassAlertDialog(
                        onDismissRequest = {
                            // Only allow dismiss if not emergency
                            if (!isEmergency) {
                                updateAvailable = null
                            }
                        },
                        title = {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isEmergency) Icons.Filled.Warning else Icons.Filled.SystemUpdate,
                                    contentDescription = "Update Icon",
                                    tint = if (isEmergency) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = if (isEmergency) "Critical Update Required" else "Update Available",
                                    color = if (isEmergency) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // Make the content scrollable
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = "Version ${release.tagName} is now available.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                if (isEmergency) {
                                    Text(
                                        text = "This update contains critical bug fixes. Please update immediately to continue using the app smoothly.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .padding(bottom = 8.dp)
                                            .background(
                                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(
                                                    8.dp
                                                )
                                            )
                                            .padding(8.dp)
                                    )
                                }

                                if (release.isPrerelease) {
                                    Text(
                                        text = "Note: This is a pre-release (beta) version.",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }

                                if (!release.body.isNullOrBlank()) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(
                                            8.dp
                                        )
                                    ) {
                                        Text(
                                            text = release.body.trim(),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (UpdateManager.startUpdateDownload(context, release)) {
                                        updateAvailable = null
                                    }
                                },
                                enabled = !isUpdateDownloading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isEmergency) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(if (isUpdateDownloading) "Downloading..." else "Download Update")
                            }
                        },
                        dismissButton = {
                            if (!isEmergency) {
                                TextButton(onClick = { updateAvailable = null }) {
                                    Text("Maybe Later")
                                }
                            }
                        }
                    )
                }

                val readyUpdate = (updateDownloadState as? UpdateDownloadState.Ready)?.update
                if (readyUpdate != null) {
                    UpdateReadyDialog(
                        downloadedUpdate = readyUpdate,
                        onDismiss = { UpdateManager.clearDownloadState() }
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

                val isExpanded = LocalConfiguration.current.screenWidthDp >= 600
                // Scrolling down a list folds the tab bar into one button beside the mini player.
                val chromeThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.toPx() }
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
                val navItems = items.map { screen ->
                    GlassNavItem(
                        label = screen.title,
                        selected = currentMainTab == screen.route,
                        onClick = { onNavigate(screen) },
                        icon = { if (currentMainTab == screen.route) screen.filledIcon() else screen.outlinedIcon() }
                    )
                }

                CompositionLocalProvider(LocalHazeState provides hazeState) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    bottomBar = {
                        if (currentRoute != "settings") {
                            Column(
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // The backend is downloading the tapped song before it can play.
                                val preparing by com.example.juke.network.JukesApi.preparing.collectAsStateWithLifecycle()
                                androidx.compose.animation.AnimatedVisibility(visible = preparing != null) {
                                    PreparingPill(title = preparing.orEmpty())
                                }
                                // One composable slot for the mini player in both layouts, so its state
                                // (live lyric, swipe) carries through the fold.
                                val fold = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntSize>(
                                    dampingRatio = 1f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = merged,
                                        enter = androidx.compose.animation.expandHorizontally(fold) +
                                            androidx.compose.animation.fadeIn() +
                                            androidx.compose.animation.scaleIn(initialScale = 0.6f),
                                        exit = androidx.compose.animation.shrinkHorizontally(fold) +
                                            androidx.compose.animation.fadeOut() +
                                            androidx.compose.animation.scaleOut(targetScale = 0.6f)
                                    ) {
                                        val tab = items.firstOrNull { it.route == currentMainTab } ?: items.first()
                                        Row {
                                            com.example.juke.ui.components.CollapsedTabButton(
                                                label = tab.title,
                                                onClick = { chrome.expand() },
                                                icon = { tab.filledIcon() }
                                            )
                                            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                                        }
                                    }
                                    MiniPlayer(
                                        musicViewModel = musicViewModel,
                                        onExpand = { showPlayerModal = true },
                                        modifier = Modifier.weight(1f),
                                        compact = merged
                                    )
                                }
                                if (!isExpanded) {
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = !merged,
                                        enter = androidx.compose.animation.expandVertically(fold) +
                                            androidx.compose.animation.fadeIn(),
                                        exit = androidx.compose.animation.shrinkVertically(fold) +
                                            androidx.compose.animation.fadeOut()
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
                                    bottomPadding = bottomPadding
                                )
                            }
                            composable(Screen.Search.route) {
                                val searchViewModel =
                                    activityViewModelProvider[SearchViewModel::class.java]
                                SearchScreen(
                                    musicViewModel = musicViewModel,
                                    searchViewModel = searchViewModel,
                                    searchResetTrigger = searchResetTrigger,
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
                                    bottomPadding = bottomPadding
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
                if (showPlayerModal) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlayerIntent(intent)
    }

    private fun handlePlayerIntent(intent: Intent?) {
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
private fun UpdateReadyDialog(
    downloadedUpdate: DownloadedUpdate,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Downloaded") },
        text = {
            Text(
                "${downloadedUpdate.fileName} is ready. Install ${downloadedUpdate.releaseTag} now or open Downloads to manage the APK manually."
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (UpdateManager.installDownloadedUpdate(context, downloadedUpdate)) {
                        onDismiss()
                    }
                }
            ) {
                Text("Install Now")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    if (UpdateManager.openDownloadsFolder(context)) {
                        onDismiss()
                    }
                }
            ) {
                Text("Open Folder")
            }
        }
    )
}

@Composable
private fun PreparingPill(title: String) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
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
