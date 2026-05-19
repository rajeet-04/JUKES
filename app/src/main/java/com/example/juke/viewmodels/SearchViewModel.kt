package com.example.juke.viewmodels

import android.app.Application
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.sqlite.db.SimpleSQLiteQuery
import coil.Coil
import coil.request.ImageRequest
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
import com.example.juke.network.ApiClient
import com.example.juke.network.SpotifyApi
import com.example.juke.services.QueueManager
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
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
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class SearchUiState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val isShowingSuggestions: Boolean = false,
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

    companion object {
        private const val LIVE_SUGGESTION_DEBOUNCE_MS = 100L
        private const val MIN_SUGGESTION_QUERY_LENGTH = 2
        private const val SUGGESTION_CACHE_MAX_ENTRIES = 64
        private const val YT_SUGGESTIONS_URL =
            "https://music.youtube.com/youtubei/v1/music/get_search_suggestions?prettyPrint=false"
        private const val YT_CLIENT_NAME = "WEB_REMIX"
        private const val YT_CLIENT_VERSION = "1.20260421.03.01"
        private const val YT_CLIENT_NAME_HEADER = "67"
    }

    private val database = MusicDatabase.getDatabase(application)
    private val trackDao = database.trackDao()
    private val playlistDao = database.playlistDao()
    private val queueManager = QueueManager.getInstance(application)
    private val searchPrefs =
        application.getSharedPreferences("search_history", android.content.Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        SearchUiState(
            recentSearches = loadRecentSearches()
        )
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _artistDetailState = MutableStateFlow(ArtistDetailUiState())
    val artistDetailState: StateFlow<ArtistDetailUiState> = _artistDetailState.asStateFlow()

    private var searchJob: Job? = null
    private val suggestionRequestNonce = AtomicLong(0L)
    private val warmupRequestNonce = AtomicLong(0L)
    private val suggestionPrefixCache =
        LinkedHashMap<String, List<String>>(SUGGESTION_CACHE_MAX_ENTRIES)

    fun updateQuery(query: String) {
        // Skip duplicate consecutive input values (distinctUntilChanged behavior).
        if (query == _uiState.value.query) return

        // Typing always puts us in suggestion mode
        _uiState.value = _uiState.value.copy(
            query = query,
            isShowingSuggestions = true
        )

        searchJob?.cancel()
        val requestNonce = suggestionRequestNonce.incrementAndGet()

        if (query.length >= MIN_SUGGESTION_QUERY_LENGTH) {
            // Serve an immediate best-effort prefix hit while a fresh request is in-flight.
            getCachedSuggestions(query)?.let { cached ->
                _uiState.value = _uiState.value.copy(
                    suggestions = cached
                )
            }

            searchJob = viewModelScope.launch {
                delay(LIVE_SUGGESTION_DEBOUNCE_MS) // Fast debounce for near-immediate typing suggestions
                if (requestNonce != suggestionRequestNonce.get()) return@launch
                fetchSuggestions(query, requestNonce)
            }
        } else if (query.isNotBlank()) {
            // For short inputs, keep typing mode active but avoid remote calls.
            _uiState.value = _uiState.value.copy(
                suggestions = emptyList()
            )
        } else {
            // Clear everything when the field is emptied
            _uiState.value = _uiState.value.copy(
                suggestions = emptyList(),
                isShowingSuggestions = false,
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

    fun warmSuggestionsConnection() {
        // Run exactly once per process to warm DNS/TLS/HTTP connection for suggestion endpoint.
        if (!warmupRequestNonce.compareAndSet(0L, 1L)) return

        viewModelScope.launch {
            try {
                val warmupPayload = """
                    {
                        "input": "a",
                        "context": {
                            "client": {
                                "clientName": "WEB_REMIX",
                                "clientVersion": "1.20260414.05.00",
                                "hl": "en-US",
                                "gl": "US"
                            }
                        }
                    }
                """.trimIndent()

                ApiClient.httpClient.post(YT_SUGGESTIONS_URL) {
                    header("Referer", "https://music.youtube.com/")
                    header("X-Origin", "https://music.youtube.com")
                    contentType(ContentType.Application.Json)
                    setBody(warmupPayload)
                }.bodyAsText()

                Log.d("SearchViewModel", "YT suggestions warm-up completed")
            } catch (e: Exception) {
                Log.w("SearchViewModel", "YT suggestions warm-up failed", e)
            }
        }
    }

    private fun normalizeSuggestionKey(query: String): String {
        return query.trim().lowercase(Locale.ROOT)
    }

    private fun getCachedSuggestions(query: String): List<String>? {
        val key = normalizeSuggestionKey(query)
        var bestPrefix: String? = null

        for (cachedKey in suggestionPrefixCache.keys) {
            if (key.startsWith(cachedKey) && (bestPrefix == null || cachedKey.length > bestPrefix.length)) {
                bestPrefix = cachedKey
            }
        }

        val prefix = bestPrefix ?: return null
        val source = suggestionPrefixCache[prefix] ?: return null
        return source.filter { it.startsWith(query, ignoreCase = true) }
    }

    private fun putCachedSuggestions(query: String, suggestions: List<String>) {
        val key = normalizeSuggestionKey(query)
        suggestionPrefixCache.remove(key)
        suggestionPrefixCache[key] = suggestions

        while (suggestionPrefixCache.size > SUGGESTION_CACHE_MAX_ENTRIES) {
            val oldestKey = suggestionPrefixCache.keys.firstOrNull() ?: break
            suggestionPrefixCache.remove(oldestKey)
        }
    }

    private suspend fun fetchSuggestions(query: String, requestNonce: Long) {
        try {
            val locale = Locale.getDefault()
            val languageTag = locale.toLanguageTag().ifBlank { "en-US" }
            val region = locale.country.ifBlank { "US" }
            val payload = """
                {
                    "input": "${query.replace("\"", "\\\"")}",
                    "context": {
                        "client": {
                            "clientName": "$YT_CLIENT_NAME",
                            "clientVersion": "$YT_CLIENT_VERSION",
                            "hl": "$languageTag",
                            "gl": "$region",
                            "platform": "DESKTOP"
                        }
                    }
                }
            """.trimIndent()

            val response = ApiClient.httpClient.post(YT_SUGGESTIONS_URL) {
                header("Accept", "*/*")
                header("Accept-Language", "$languageTag,en;q=0.9")
                header("Referer", "https://music.youtube.com/")
                header("Origin", "https://music.youtube.com")
                header("X-Origin", "https://music.youtube.com")
                header("x-youtube-client-name", YT_CLIENT_NAME_HEADER)
                header("x-youtube-client-version", YT_CLIENT_VERSION)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            val responseBody = response.bodyAsText()

            val json = JSONObject(responseBody)
            val results = mutableListOf<String>()
            val contents = json.optJSONArray("contents")

            if (contents != null) {
                for (i in 0 until contents.length()) {
                    val sectionContents = contents.getJSONObject(i)
                        .optJSONObject("searchSuggestionsSectionRenderer")
                        ?.optJSONArray("contents") ?: continue

                    for (j in 0 until sectionContents.length()) {
                        val suggestionRenderer = sectionContents.getJSONObject(j)
                            .optJSONObject("searchSuggestionRenderer")

                        val runs = suggestionRenderer
                            ?.optJSONObject("suggestion")
                            ?.optJSONArray("runs")
                        if (runs != null) {
                            val textBuilder = StringBuilder()
                            for (k in 0 until runs.length()) {
                                textBuilder.append(runs.getJSONObject(k).optString("text", ""))
                            }
                            val text = textBuilder.toString().trim()
                            if (text.isNotEmpty()) results.add(text)
                        }
                    }
                }
            }

            // Drop stale responses if a newer query was typed while this request was in-flight.
            if (requestNonce != suggestionRequestNonce.get()) return
            if (_uiState.value.query != query || !_uiState.value.isShowingSuggestions) return

            putCachedSuggestions(query, results)

            _uiState.value = _uiState.value.copy(
                suggestions = results
            )
        } catch (e: Exception) {
            Log.e("SearchViewModel", "Failed to fetch suggestions", e)
            _uiState.value = _uiState.value.copy(
                suggestions = emptyList()
            )
        }
    }

    fun search(query: String) {
        val trimmedQuery = query.trim()
        searchJob?.cancel() // Cancel any pending suggestion fetch
        // Flip out of suggestion mode immediately so results can render
        _uiState.value = _uiState.value.copy(
            isShowingSuggestions = false,
            suggestions = emptyList(),
            query = trimmedQuery
        )
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
        AnalyticsManager.getInstance(getApplication()).trackSearchQuery(trimmedQuery)
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
                    val queryTokens =
                        trimmedQuery.split("\\s+".toRegex()).filter { it.isNotBlank() }

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
                        trackDao.searchTracksRaw(
                            SimpleSQLiteQuery(
                                queryBuilder.toString(),
                                args.toArray()
                            )
                        ).map { it.toTrack() }
                    }
                    _uiState.value = _uiState.value.copy(localTracks = localResults)

                    // Then fetch Spotify results
                    val response = SpotifyApi.search(trimmedQuery)
                    // Filter out Spotify tracks that are already in local results (by title+artist match)
                    val localTitles =
                        localResults.map { it.title.lowercase() to it.artist.lowercase() }.toSet()
                    val filteredSpotifyTracks =
                        (response.tracks?.items ?: emptyList()).filter { st ->
                            val key =
                                st.name.lowercase() to st.artists.firstOrNull()?.name?.lowercase()
                                    .orEmpty()
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

                    // Pre-warm thumbnail cache so images are in-flight when the list renders.
                    val ctx = getApplication<Application>()
                    val imageLoader = Coil.imageLoader(ctx)
                    filteredSpotifyTracks.forEach { track ->
                        track.album.images.lastOrNull()?.url?.let { url ->
                            imageLoader.enqueue(
                                ImageRequest.Builder(ctx).data(url).build()
                            )
                        }
                    }
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
        val updated = current.take(15) // keep only last 15
        searchPrefs.edit { putString("recent_searches", updated.joinToString("|||")) }
        _uiState.value = _uiState.value.copy(recentSearches = updated)
    }

    fun removeRecentSearch(query: String) {
        val current = loadRecentSearches().toMutableList()
        current.remove(query)
        searchPrefs.edit { putString("recent_searches", current.joinToString("|||")) }
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
                    trackCount = playlist.tracks?.total ?: 0
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
                                Log.e(
                                    "SearchViewModel",
                                    "Failed to download track: ${track.name}",
                                    e
                                )
                                progressCounter.incrementAndGet()
                                _uiState.value =
                                    _uiState.value.copy(importProgress = progressCounter.get())
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
