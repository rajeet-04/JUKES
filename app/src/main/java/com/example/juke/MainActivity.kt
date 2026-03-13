package com.example.juke

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.juke.analytics.AnalyticsManager
import com.example.juke.models.GithubRelease
import com.example.juke.network.SpotifyApi
import com.example.juke.services.UpdateManager
import com.example.juke.ui.components.MiniPlayer
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

    // 1. Define the permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            android.util.Log.d("MainActivity", "Read Phone State permission granted")
        } else {
            android.util.Log.w(
                "MainActivity",
                "Read Phone State permission denied - Auto-pause on call will not work"
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Portrait lock for phones (shortest width < 600dp)
        // Tablets and large screens will support landscape
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        enableEdgeToEdge()

        // 2. Request the permission immediately on launch
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            }
        }

        // Track app opened
        AnalyticsManager.getInstance().trackAppOpened()

        // Check intent immediately
        handlePlayerIntent(intent)

        setContent {
            val musicViewModel: MusicViewModel = viewModel()
            val uiState by musicViewModel.uiState.collectAsState()

            JUKETheme(
                extractedColors = uiState.extractedColors
            ) {
                val navController = rememberNavController()
                val searchViewModel: SearchViewModel = viewModel()
                val playlistDetailViewModel: PlaylistDetailViewModel = viewModel()

                // Initialize SpotifyApi with saved market code
                LaunchedEffect(Unit) {
                    val savedMarket = musicViewModel.marketCode.value
                    SpotifyApi.setDefaultMarket(savedMarket)
                }
                val albumDetailViewModel: AlbumDetailViewModel = viewModel()
                val context = LocalContext.current
                var showPlayerModal by remember { mutableStateOf(false) }
                var searchResetTrigger by remember { mutableIntStateOf(0) }

                // --- UPDATE CHECK LOGIC ---
                var updateAvailable by remember { mutableStateOf<GithubRelease?>(null) }
                val uriHandler = LocalUriHandler.current

                LaunchedEffect(Unit) {
                    // Runs once on app launch
                    updateAvailable = UpdateManager.checkForUpdates()
                }

                if (updateAvailable != null) {
                    val release = updateAvailable!!

                    // Determine if the update is an emergency update (e.g., contains "emergency" or "hotfix" in tags or body)
                    val isEmergency = release.tagName.contains("emergency", ignoreCase = true) ||
                            release.tagName.contains("hotfix", ignoreCase = true) ||
                            (release.body?.contains("emergency", ignoreCase = true) == true) ||
                            (release.body?.contains("critical", ignoreCase = true) == true) ||
                            (release.body?.contains("hotfix", ignoreCase = true) == true)

                    AlertDialog(
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
                                    try {
                                        uriHandler.openUri(release.htmlUrl)
                                        updateAvailable = null
                                        if (isEmergency) {
                                            (context as? android.app.Activity)?.finishAffinity()
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("MainActivity", "Failed to open update URL", e)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isEmergency) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Download Update")
                            }
                        },
                        dismissButton = {
                            if (!isEmergency) {
                                TextButton(onClick = { updateAvailable = null }) {
                                    Text("Maybe Later")
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
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

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route

                        if (currentRoute != "settings") {
                            Column {
                                MiniPlayer(
                                    musicViewModel = musicViewModel,
                                    onExpand = { showPlayerModal = true }
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.surface
                                                ),
                                                startY = 0f,
                                                endY = 100f
                                            )
                                        )
                                ) {
                                    NavigationBar(
                                        containerColor = Color.Transparent
                                    ) {
                                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                                        val currentDestination = navBackStackEntry?.destination

                                        items.forEach { screen ->
                                            NavigationBarItem(
                                                icon = {
                                                    if (currentDestination?.hierarchy?.any { it.route == screen.route } == true) {
                                                        screen.filledIcon()
                                                    } else {
                                                        screen.outlinedIcon()
                                                    }
                                                },
                                                label = { Text(screen.title) },
                                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                                onClick = {
                                                    // Check if already on the selected screen
                                                    val isSelected =
                                                        currentDestination?.hierarchy?.any { it.route == screen.route } == true

                                                    if (screen == Screen.Search && isSelected) {
                                                        // Already on search, trigger search reset
                                                        searchResetTrigger++
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
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    val layoutDirection = LocalLayoutDirection.current
                    val bottomPadding = innerPadding.calculateBottomPadding()
                    val contentPadding = PaddingValues(
                        start = innerPadding.calculateStartPadding(layoutDirection),
                        top = 0.dp,
                        end = innerPadding.calculateEndPadding(layoutDirection),
                        bottom = 0.dp
                    )

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.padding(contentPadding)
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                musicViewModel = musicViewModel,
                                onSettingsClick = { navController.navigate("settings") },
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
                            SearchScreen(
                                musicViewModel = musicViewModel,
                                searchViewModel = searchViewModel,
                                searchResetTrigger = searchResetTrigger,
                                onNavigateToArtist = { artist ->
                                    searchViewModel.loadArtistDetails(artist)
                                    navController.navigate("artist/${artist.id}")
                                },
                                onNavigateToPlaylist = { playlist ->
                                    playlistDetailViewModel.loadPlaylistDetails(playlist)
                                    navController.navigate("playlist/${playlist.id}")
                                },
                                onNavigateToAlbum = { album ->
                                    albumDetailViewModel.loadAlbumDetails(album)
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
                                }
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
                            ArtistDetailScreen(
                                searchViewModel = searchViewModel,
                                musicViewModel = musicViewModel,
                                onNavigateBack = {
                                    searchViewModel.clearArtistDetail()
                                    navController.popBackStack()
                                },
                                onNavigateToAlbum = { album ->
                                    albumDetailViewModel.loadAlbumDetails(album)
                                    navController.navigate("album/${album.id}")
                                },
                                bottomPadding = bottomPadding
                            )
                        }
                        composable("playlist/{playlistId}") {
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

                // Player Modal
                if (showPlayerModal) {
                    PlayerScreen(
                        musicViewModel = musicViewModel,
                        onDismiss = { showPlayerModal = false },
                        onNavigateToArtist = { artistId ->
                            showPlayerModal = false
                            searchViewModel.loadArtistDetailsById(artistId)
                            navController.navigate("artist/$artistId")
                        },
                        onNavigateToAlbum = { albumId ->
                            showPlayerModal = false
                            albumDetailViewModel.loadAlbumDetailsById(albumId)
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
        val analytics = AnalyticsManager.getInstance()
        analytics.trackAppClosed()
        analytics.endSession()
    }
}