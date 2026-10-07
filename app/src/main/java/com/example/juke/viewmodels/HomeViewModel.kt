package com.example.juke.viewmodels

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.juke.database.MusicDatabase
import com.example.juke.database.toTrack
import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.SpotifyTrack
import com.example.juke.models.Track
import com.example.juke.network.JukesApi
import com.example.juke.network.RecommenderApi
import com.example.juke.network.SpotifyApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds

/** Songs recommended from one played [seed]; [tracks] are already matched to Spotify. */
data class RadioSection(val seed: Track, val tracks: List<SpotifyTrack>)

data class HomeUiState(
    val greeting: String = "",
    val recentlyPlayed: List<Track> = emptyList(),
    val mostPlayed: List<Track> = emptyList(),
    val favorites: List<Track> = emptyList(),
    val newReleases: List<SpotifyAlbum> = emptyList(),
    val becauseYouPlayed: List<RadioSection> = emptyList(),
    val likeFavorites: RadioSection? = null,
    /** Sections still waiting on the network; each shows its own skeleton until it lands. */
    val releasesPending: Boolean = false,
    val becausePending: Int = 0,
    val likePending: Boolean = false,
    val discoveryOffline: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false
) {
    val isDiscoveryLoading: Boolean get() = releasesPending || becausePending > 0 || likePending
}

/** Process-wide copy of the last feed so Home paints instantly and revalidates in the background. */
private object DiscoveryCache {
    class Snapshot(
        val key: Int,
        val at: Long,
        val releases: List<SpotifyAlbum>,
        val because: List<RadioSection?>,
        val like: RadioSection?
    )

    @Volatile
    var snapshot: Snapshot? = null
    const val FRESH_MS = 30 * 60_000L
    val SECTION_TIMEOUT = 15.seconds
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MusicDatabase.getDatabase(application)
    private val trackDao = database.trackDao()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private fun buildGreeting(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        calendar.getDisplayName(
            Calendar.DAY_OF_WEEK,
            Calendar.LONG,
            java.util.Locale.getDefault()
        )
        return when (hour) {
            in 5..11 -> "First Light Sounds !"
            in 12..16 -> "Afternoon Drift !"
            in 17..20 -> "The Golden Hour !"
            else -> "After Dark !"
        }
    }

    fun loadHomeData() {
        viewModelScope.launch {
            // Only block on the skeleton for the very first paint; local history loads in a blink.
            if (_uiState.value.greeting.isEmpty()) _uiState.value =
                _uiState.value.copy(isLoading = true)
            fetchData(force = false)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            fetchData(force = true)
        }
    }

    private var discoveryJob: Job? = null
    private var lastHistory: Triple<List<Track>, List<Track>, List<Track>>? = null

    private fun isOnline(): Boolean {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
            ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Called when connectivity returns; fills the feed if it is still empty. */
    fun onNetworkAvailable() {
        val h = lastHistory ?: return
        val st = _uiState.value
        if (st.discoveryOffline || (!st.isDiscoveryLoading && st.newReleases.isEmpty() &&
                    st.becauseYouPlayed.isEmpty() && st.likeFavorites == null)
        ) {
            startDiscovery(h.first, h.second, h.third, force = true)
        }
    }

    private suspend fun fetchData(force: Boolean) {
        val recentlyPlayed = trackDao.getRecentlyPlayed(10).map { it.toTrack() }
        val mostPlayed = trackDao.getMostPlayed(10).map { it.toTrack() }
        val favorites = trackDao.getFavourites().take(10).map { it.toTrack() }

        _uiState.value = _uiState.value.copy(
            greeting = buildGreeting(),
            recentlyPlayed = recentlyPlayed,
            mostPlayed = mostPlayed,
            favorites = favorites,
            isLoading = false,
            isRefreshing = false
        )
        startDiscovery(recentlyPlayed, mostPlayed, favorites, force)
    }

    private fun startDiscovery(
        recent: List<Track>,
        mostPlayed: List<Track>,
        favorites: List<Track>,
        force: Boolean
    ) {
        lastHistory = Triple(recent, mostPlayed, favorites)
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch { loadDiscovery(recent, mostPlayed, favorites, force) }
    }

    /**
     * History-driven feed. Cached sections paint immediately; the network pass then replaces each
     * section independently as it arrives. A section that can't load stays empty and hides.
     */
    private suspend fun loadDiscovery(
        recent: List<Track>,
        mostPlayed: List<Track>,
        favorites: List<Track>,
        force: Boolean
    ) = coroutineScope {
        val known = (recent + mostPlayed + favorites)
        val knownKeys = known.mapTo(HashSet()) { RecommenderApi.songKey(it.title, it.artist) }

        val seeds = (recent + mostPlayed).filter { it.ytVideoId != null }
            .distinctBy { it.artist }.take(2)
        val favSeed = favorites.filter { it.ytVideoId != null }
            .firstOrNull { f -> seeds.none { it.uuid == f.uuid } }
        val key = listOf(seeds.map { it.uuid }, favSeed?.uuid, known.map { it.uuid }).hashCode()

        val cached = DiscoveryCache.snapshot
        if (cached != null) {
            _uiState.update {
                it.copy(
                    newReleases = cached.releases,
                    becauseYouPlayed = cached.because.filterNotNull(),
                    likeFavorites = cached.like,
                    releasesPending = false, becausePending = 0, likePending = false,
                    discoveryOffline = false
                )
            }
            val fresh =
                cached.key == key && System.currentTimeMillis() - cached.at < DiscoveryCache.FRESH_MS
            if (fresh && !force) return@coroutineScope
        }

        if (!isOnline()) {
            _uiState.update {
                it.copy(
                    releasesPending = false, becausePending = 0, likePending = false,
                    discoveryOffline = cached == null
                )
            }
            return@coroutineScope
        }

        _uiState.update {
            it.copy(
                discoveryOffline = false,
                releasesPending = cached == null,
                becausePending = if (cached == null) seeds.size else 0,
                likePending = cached == null && favSeed != null
            )
        }

        val because = Array(seeds.size) { cached?.because?.getOrNull(it) }
        val becauseDone = BooleanArray(seeds.size)
        fun publishBecause() = _uiState.update { state ->
            val pending = if (cached == null) becauseDone.count { !it } else 0
            state.copy(becauseYouPlayed = because.filterNotNull(), becausePending = pending)
        }

        var releasesFinal: List<SpotifyAlbum> = cached?.releases.orEmpty()
        var likeFinal: RadioSection? = cached?.like

        val jobs = mutableListOf<Job>()
        jobs += launch {
            val r = withTimeoutOrNull(DiscoveryCache.SECTION_TIMEOUT) { newReleasesFor(known) }
            if (r != null) releasesFinal = r
            _uiState.update { it.copy(newReleases = r ?: it.newReleases, releasesPending = false) }
        }
        seeds.forEachIndexed { i, seed ->
            jobs += launch {
                val s = withTimeoutOrNull(DiscoveryCache.SECTION_TIMEOUT) {
                    radioSection(
                        seed,
                        knownKeys
                    )
                }
                because[i] = s ?: cached?.because?.getOrNull(i)
                becauseDone[i] = true
                publishBecause()
            }
        }
        if (favSeed != null) {
            jobs += launch {
                val s = withTimeoutOrNull(DiscoveryCache.SECTION_TIMEOUT) {
                    radioSection(
                        favSeed,
                        knownKeys
                    )
                }
                likeFinal = s ?: cached?.like
                _uiState.update { it.copy(likeFavorites = likeFinal, likePending = false) }
            }
        } else {
            likeFinal = null
            _uiState.update { it.copy(likeFavorites = null) }
        }
        jobs.joinAll()

        DiscoveryCache.snapshot = DiscoveryCache.Snapshot(
            key, System.currentTimeMillis(), releasesFinal, because.toList(), likeFinal
        )
    }

    /** Matches radio picks on Spotify concurrently and stops as soon as enough have landed. */
    private suspend fun radioSection(seed: Track, knownKeys: Set<String>): RadioSection? = try {
        val videoId = seed.ytVideoId ?: return null
        // Direct YouTube Music radio first; the shared backend only as a fallback, so one heavy
        // listener's history never skews everyone else's feed.
        val radio = RecommenderApi.fetchFullRadioQueue(videoId).takeIf { it.isNotEmpty() }
            ?: JukesApi.radio(videoId) ?: emptyList()
        val candidates = radio.drop(1)
            .filter { !RecommenderApi.isSpamTitle(it.title) }
            .filter { RecommenderApi.songKey(it.title, it.artist) !in knownKeys }
            .take(10)
        val gate = Semaphore(5)
        val results = Channel<SpotifyTrack?>(candidates.size)
        val picked = ArrayList<SpotifyTrack>()
        coroutineScope {
            candidates.forEach { rec ->
                launch {
                    val t = gate.withPermit {
                        runCatching {
                            RecommenderApi.matchOnSpotify(rec)?.let {
                                SpotifyApi.getTrack(
                                    it.spotifyUrl.substringAfterLast("/").substringBefore("?")
                                )
                            }
                        }.getOrNull()
                    }
                    results.trySend(t)
                }
            }
            repeat(candidates.size) {
                results.receive()?.let { t -> if (picked.none { it.id == t.id }) picked += t }
                if (picked.size >= 6) {
                    coroutineContext.cancelChildren()
                    return@coroutineScope
                }
            }
        }
        picked.takeIf { it.size >= 3 }?.let { RadioSection(seed, it.toList()) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("HomeViewModel", "Radio for ${seed.title} failed: ${e.message}")
        null
    }

    /** Albums and singles from the artists played most, released in the last 120 days. */
    private suspend fun newReleasesFor(known: List<Track>): List<SpotifyAlbum> = try {
        val artistIds = known.flatMap { it.artistSpotifyIds.orEmpty() }
            .groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.take(6).map { it.key }
        val cutoff = LocalDate.now().minusDays(120)
        coroutineScope {
            artistIds.map { id ->
                async {
                    runCatching {
                        SpotifyApi.getArtistAlbums(
                            id,
                            limit = 10
                        ).items
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()
        }.flatten()
            .asSequence()
            .mapNotNull { album ->
                val date = album.releaseDate?.takeIf { it.length == 10 }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                if (date != null && date >= cutoff && album.id != null && album.albumType != "compilation") album to date else null
            }
            .distinctBy { it.first.id }
            .sortedByDescending { it.second }
            .take(12).map { it.first }.toList()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("HomeViewModel", "New releases failed: ${e.message}")
        emptyList()
    }
}
