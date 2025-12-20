package com.example.juke

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.juke.ui.components.MiniPlayer
import com.example.juke.ui.screens.AlbumDetailScreen
import com.example.juke.ui.screens.ArtistDetailScreen
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

sealed class Screen(val route: String, val title: String, val icon: @Composable () -> Unit) {
    object Home : Screen("home", "Home", { Icon(Icons.Default.Home, contentDescription = "Home") })
    object Search : Screen("search", "Search", { Icon(Icons.Default.Search, contentDescription = "Search") })
    object Library : Screen("library", "Library", { Icon(painter = painterResource(R.drawable.library_svgrepo_com), contentDescription = "Library") })
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JUKETheme {
                val navController = rememberNavController()
                val musicViewModel: MusicViewModel = viewModel()
                val searchViewModel: SearchViewModel = viewModel()
                val playlistDetailViewModel: PlaylistDetailViewModel = viewModel()
                val albumDetailViewModel: AlbumDetailViewModel = viewModel()
                var showPlayerModal by remember { mutableStateOf(false) }
                
                val items = listOf(
                    Screen.Home,
                    Screen.Search,
                    Screen.Library
                )
                
                Scaffold(
                    bottomBar = {
                        Column {
                            MiniPlayer(
                                musicViewModel = musicViewModel,
                                onExpand = { showPlayerModal = true }
                            )
                            
                            NavigationBar {
                                val navBackStackEntry by navController.currentBackStackEntryAsState()
                                val currentDestination = navBackStackEntry?.destination
                                
                                items.forEach { screen ->
                                    NavigationBarItem(
                                        icon = { screen.icon() },
                                        label = { Text(screen.title) },
                                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                        onClick = {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(musicViewModel = musicViewModel)
                        }
                        composable(Screen.Search.route) {
                            SearchScreen(
                                musicViewModel = musicViewModel,
                                onNavigateToArtist = { artist ->
                                    searchViewModel.loadArtistDetails(artist)
                                    navController.navigate("artist/${artist.id}")
                                },
                                onNavigateToPlaylist = { playlist ->
                                    playlistDetailViewModel.loadPlaylistDetails(playlist)
                                    navController.navigate("playlist/${playlist.id}")
                                }
                            )
                        }
                        composable(Screen.Library.route) {
                            LibraryScreen(musicViewModel = musicViewModel)
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
                                }
                            )
                        }
                        composable("playlist/{playlistId}") {
                            PlaylistDetailScreen(
                                playlistDetailViewModel = playlistDetailViewModel,
                                musicViewModel = musicViewModel,
                                onNavigateBack = {
                                    playlistDetailViewModel.clearPlaylistDetail()
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable("album/{albumId}") {
                            AlbumDetailScreen(
                                albumDetailViewModel = albumDetailViewModel,
                                musicViewModel = musicViewModel,
                                onNavigateBack = {
                                    albumDetailViewModel.clearAlbumDetail()
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
                
                // Player Modal
                if (showPlayerModal) {
                    PlayerScreen(
                        musicViewModel = musicViewModel,
                        onDismiss = { showPlayerModal = false }
                    )
                }
            }
        }
    }
}