package com.example.juke.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.juke.analytics.AnalyticsManager
import com.example.juke.database.MusicDatabase
import com.example.juke.database.PlaylistEntity
import com.example.juke.database.PlaylistTrackEntity
import com.example.juke.database.toTrack
import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.SpotifyArtist
import com.example.juke.models.SpotifyPlaylist
import com.example.juke.models.SpotifyTrack
import com.example.juke.models.Track
import com.example.juke.network.SpotifyApi
import com.example.juke.services.QueueManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.sqlite.db.SimpleSQLiteQuery
import java.util.concurrent.atomic.AtomicInteger

data class SearchUiState(
    val query: String = "",
    val tracks: List<SpotifyTrack> = emptyList(),
    val localTracks: List<Track> = emptyList(),
    val artists: List<SpotifyArtist> = emptyList(),
    val playlists: List<SpotifyPlaylist> = emptyList(),
    val albums: List<SpotifyAlbum> = emptyList(),
    val isSearching: Boolean = false,
    val downloadingId: String? = null,
    val error: String? = null,
    val isPlaylistUrl: Boolean = false,
    val playlistId: String? = null,
    val isImportingPlaylist: Boolean = false,
    val importProgress: Int = 0,
    val importTotal: Int = 0,
    val recentSearches: List<String> = emptyList()
)

data class ArtistDetailUiState(
    val artist: SpotifyArtist? = null,
    val albums: List<SpotifyAlbum> = emptyList(),
    val topTracks: List<SpotifyTrack> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MusicDatabase.getDatabase(application)
    private val trackDao = database.trackDao()
    private val playlistDao = database.playlistDao()
    private val queueManager = QueueManager.getInstance(application)
    private val searchPrefs = application.getSharedPreferences("search_history", android.content.Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SearchUiState(
        recentSearches = loadRecentSearches()
    ))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _artistDetailState = MutableStateFlow(ArtistDetailUiState())
    val artistDetailState: StateFlow<ArtistDetailUiState> = _artistDetailState.asStateFlow()

    private var searchJob: Job? = null

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)

        // Cancel previous search job
        searchJob?.cancel()

        // Start new search job with debounce
        if (query.isNotBlank()) {
            searchJob = viewModelScope.launch {
                delay(769) // 0.769 second debounce
                search(query)
            }
        } else {
            // Clear results immediately when query is empty
            _uiState.value = _uiState.value.copy(
                tracks = emptyList(),
                localTracks = emptyList(),
                artists = emptyList(),
                playlists = emptyList(),
                albums = emptyList(),
                isPlaylistUrl = false,
                playlistId = null
            )
        }
    }

    fun search(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            _uiState.value = _uiState.value.copy(
                tracks = emptyList(),
                localTracks = emptyList(),
                artists = emptyList(),
                playlists = emptyList(),
                albums = emptyList()
            )
            return
        }

        // Track search query
        AnalyticsManager.getInstance().trackSearchQuery(trimmedQuery)
        // Save to recent searches
        saveRecentSearch(trimmedQuery)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, error = null)

            try {
                // Check if query is a Spotify URL
                val urlInfo = parseSpotifyUrl(trimmedQuery)

                if (urlInfo != null) {
                    // Handle URL-based search
                    when (urlInfo.type) {
                        "track" -> {
                            val track = SpotifyApi.getTrack(urlInfo.id)
                            _uiState.value = _uiState.value.copy(
                                tracks = listOf(track),
                                localTracks = emptyList(),
                                artists = emptyList(),
                                playlists = emptyList(),
                                albums = emptyList(),
                                isSearching = false,
                                isPlaylistUrl = false,
                                playlistId = null
                            )
                        }

                        "artist" -> {
                            val artist = SpotifyApi.getArtist(urlInfo.id)
                            _uiState.value = _uiState.value.copy(
                                tracks = emptyList(),
                                localTracks = emptyList(),
                                artists = listOf(artist),
                                playlists = emptyList(),
                                albums = emptyList(),
                                isSearching = false,
                                isPlaylistUrl = false,
                                playlistId = null
                            )
                        }

                        "playlist" -> {
                            val playlist = SpotifyApi.getPlaylist(urlInfo.id)
                            _uiState.value = _uiState.value.copy(
                                tracks = emptyList(),
                                localTracks = emptyList(),
                                artists = emptyList(),
                                playlists = listOf(playlist),
                                albums = emptyList(),
                                isSearching = false,
                                isPlaylistUrl = true,
                                playlistId = urlInfo.id
                            )
                        }

                        "album" -> {
                            val album = SpotifyApi.getAlbum(urlInfo.id)
                            _uiState.value = _uiState.value.copy(
                                tracks = emptyList(),
                                localTracks = emptyList(),
                                artists = emptyList(),
                                playlists = emptyList(),
                                albums = listOf(album),
                                isSearching = false,
                                isPlaylistUrl = false,
                                playlistId = null
                            )
                        }
                    }
                } else {
                    // Search local DB immediately for instant results
                    // Dynamic query builder for partial matching (e.g. "Linkin Numb" -> matches "Linkin Park - Numb")
                    val queryTokens = trimmedQuery.split("\\s+".toRegex()).filter { it.isNotBlank() }
                    
                    val localResults = if (queryTokens.isEmpty()) {
                        emptyList()
                    } else {
                        val queryBuilder = StringBuilder("SELECT * FROM tracks WHERE ")
                        val args = ArrayList<Any>()
                        
                        queryTokens.forEachIndexed { index, token ->
                            if (index > 0) queryBuilder.append(" AND ")
                            queryBuilder.append("(LOWER(title) LIKE '%' || LOWER(?) || '%' OR LOWER(artist) LIKE '%' || LOWER(?) || '%')")
                            args.add(token)
                            args.add(token)
                        }
                        
                        queryBuilder.append(" ORDER BY last_played_at DESC")
                        trackDao.searchTracksRaw(SimpleSQLiteQuery(queryBuilder.toString(), args.toArray())).map { it.toTrack() }
                    }
                    _uiState.value = _uiState.value.copy(localTracks = localResults)

                    // Then fetch Spotify results
                    val response = SpotifyApi.search(trimmedQuery)
                    // Filter out Spotify tracks that are already in local results (by title+artist match)
                    val localTitles = localResults.map { it.title.lowercase() to it.artist.lowercase() }.toSet()
                    val filteredSpotifyTracks = (response.tracks?.items ?: emptyList()).filter { st ->
                        val key = st.name.lowercase() to st.artists.firstOrNull()?.name?.lowercase().orEmpty()
                        key !in localTitles
                    }

                    _uiState.value = _uiState.value.copy(
                        tracks = filteredSpotifyTracks,
                        artists = response.artists?.items ?: emptyList(),
                        playlists = response.playlists?.items?.filterNotNull() ?: emptyList(),
                        albums = response.albums?.items ?: emptyList(),
                        isSearching = false,
                        isPlaylistUrl = false,
                        playlistId = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = e.message ?: "Search failed"
                )
            }
        }
    }

    private fun loadRecentSearches(): List<String> {
        val json = searchPrefs.getString("recent_searches", null) ?: return emptyList()
        return try {
            json.split("|||")
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = loadRecentSearches().toMutableList()
        current.remove(trimmed) // remove duplicate
        current.add(0, trimmed) // add to front
        val updated = current.take(5) // keep only last 5
        searchPrefs.edit().putString("recent_searches", updated.joinToString("|||")).apply()
        _uiState.value = _uiState.value.copy(recentSearches = updated)
    }

    fun removeRecentSearch(query: String) {
        val current = loadRecentSearches().toMutableList()
        current.remove(query)
        searchPrefs.edit().putString("recent_searches", current.joinToString("|||")).apply()
        _uiState.value = _uiState.value.copy(recentSearches = current)
    }

    private data class SpotifyUrlInfo(val type: String, val id: String)

    private fun parseSpotifyUrl(query: String): SpotifyUrlInfo? {
        // Match Spotify URLs in different formats:
        // https://open.spotify.com/track/6rqhFgbbKwnb9MLmUQDhG6
        // https://open.spotify.com/artist/4Z8W4fKeB5YxbusRsdQVPb
        // https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M
        // https://open.spotify.com/album/6DEjYFkNZh67HP7R9PSZvv
        // spotify:track:6rqhFgbbKwnb9MLmUQDhG6

        val httpRegex =
            """https?://open\.spotify\.com/(track|artist|playlist|album)/([a-zA-Z0-9]+)""".toRegex()
        val uriRegex = """spotify:(track|artist|playlist|album):([a-zA-Z0-9]+)""".toRegex()

        httpRegex.find(query)?.let {
            return SpotifyUrlInfo(it.groupValues[1], it.groupValues[2])
        }

        uriRegex.find(query)?.let {
            return SpotifyUrlInfo(it.groupValues[1], it.groupValues[2])
        }

        return null
    }

    fun loadArtistDetails(artist: SpotifyArtist) {
        viewModelScope.launch {
            _artistDetailState.value = ArtistDetailUiState(
                artist = artist,
                isLoading = true
            )

            try {
                if (artist.id != null) {
                    val albums = SpotifyApi.getArtistAlbums(artist.id)
                    val topTracks = SpotifyApi.getArtistTopTracks(artist.id)

                    _artistDetailState.value = _artistDetailState.value.copy(
                        albums = albums.items,
                        topTracks = topTracks.tracks,
                        isLoading = false
                    )
                } else {
                    _artistDetailState.value = _artistDetailState.value.copy(
                        isLoading = false,
                        error = "Invalid artist ID"
                    )
                }
            } catch (e: Exception) {
                _artistDetailState.value = _artistDetailState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load artist details"
                )
            }
        }
    }

    fun loadArtistDetailsById(artistId: String) {
        viewModelScope.launch {
            _artistDetailState.value = ArtistDetailUiState(isLoading = true)

            try {
                // Fetch artist details first
                val artist = SpotifyApi.getArtist(artistId)
                // Then proceed with loading other details
                loadArtistDetails(artist)
            } catch (e: Exception) {
                _artistDetailState.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    fun setDownloading(songId: String?) {
        _uiState.value = _uiState.value.copy(downloadingId = songId)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearArtistDetail() {
        _artistDetailState.value = ArtistDetailUiState()
    }

    fun importPlaylist(playlistId: String, onTrackDownloaded: suspend (SpotifyTrack) -> Track) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isImportingPlaylist = true,
                importProgress = 0,
                importTotal = 0,
                error = null
            )

            try {
                // Fetch playlist info
                val playlist = SpotifyApi.getPlaylist(playlistId)

                // Save playlist to database
                val playlistEntity = PlaylistEntity(
                    id = playlist.id,
                    name = playlist.name,
                    description = playlist.description,
                    thumbnailUri = playlist.images.firstOrNull()?.url,
                    spotifyId = playlist.id,
                    trackCount = playlist.tracks.total
                )
                playlistDao.insertPlaylist(playlistEntity)

                // Fetch all playlist tracks
                val response = SpotifyApi.getPlaylistTracks(playlistId)
                val tracks = response.items.mapNotNull { it.track }

                _uiState.value = _uiState.value.copy(importTotal = tracks.size)

                // Download tracks in parallel — max 6 concurrent (3 per API)
                // Each track is inserted into the playlist DB immediately on download,
                // so the playlist updates in real-time and survives app closure.
                val semaphore = Semaphore(6)
                val progressCounter = AtomicInteger(0)
                val successCount = AtomicInteger(0)
                tracks.mapIndexed { index, track ->
                    async {
                        semaphore.withPermit {
                            try {
                                queueManager.addDownloadTracking(
                                    track.name,
                                    track.artists.joinToString(", ") { it.name },
                                    "playlist"
                                )

                                val downloadedTrack = onTrackDownloaded(track)

                                queueManager.removeDownloadTracking(
                                    track.name,
                                    track.artists.joinToString(", ") { it.name }
                                )

                                // Insert immediately so the playlist reflects this track right away
                                val playlistTrack = PlaylistTrackEntity(
                                    playlistId = playlist.id,
                                    trackUuid = downloadedTrack.uuid,
                                    position = index
                                )
                                playlistDao.insertPlaylistTrack(playlistTrack)
                                successCount.incrementAndGet()

                                val progress = progressCounter.incrementAndGet()
                                _uiState.value = _uiState.value.copy(importProgress = progress)
                            } catch (e: Exception) {
                                queueManager.removeDownloadTracking(
                                    track.name,
                                    track.artists.joinToString(", ") { it.name }
                                )
                                Log.e("SearchViewModel", "Failed to download track: ${track.name}", e)
                                progressCounter.incrementAndGet()
                                _uiState.value = _uiState.value.copy(importProgress = progressCounter.get())
                            }
                        }
                    }
                }.awaitAll()

                // Update playlist track count with however many succeeded
                playlistDao.updatePlaylistTrackCount(playlist.id, successCount.get())

                _uiState.value = _uiState.value.copy(
                    isImportingPlaylist = false,
                    importProgress = 0,
                    importTotal = 0
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isImportingPlaylist = false,
                    error = "Failed to import playlist: ${e.message}"
                )
            }
        }
    }
}
