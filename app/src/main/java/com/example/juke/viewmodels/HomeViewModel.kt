package com.example.juke.viewmodels

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.juke.database.MusicDatabase
import com.example.juke.database.toTrack
import com.example.juke.home.FeedPager
import com.example.juke.home.HomeSection
import com.example.juke.home.LocalSections
import com.example.juke.home.YtmCharts
import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.Track
import com.example.juke.network.JukesApi
import com.example.juke.network.RecommenderApi
import com.example.juke.network.SpotifyApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds

data class HomeUiState(
    val greeting: String = "",
    val recentlyPlayed: List<Track> = emptyList(),
    val mostPlayed: List<Track> = emptyList(),
    val favorites: List<Track> = emptyList(),
    /** The whole feed in display order: the head, then every page loaded by scrolling. */
    val sections: List<HomeSection> = emptyList(),
    /** The first radio page is still on its way; Home shows a skeleton where it will land. */
    val headPending: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val discoveryOffline: Boolean = false,
    /** YouTube Music's "Moods & moments", shown as chips; picking one turns Home into that mood. */
    val moods: List<com.example.juke.home.YtmMoods.Mood> = emptyList(),
    val selectedMood: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false
)

/**
 * Process-wide copy of the feed, pager included, so returning to Home paints instantly, keeps
 * every page already scrolled through and carries on from there.
 */
private object DiscoveryCache {
    class Snapshot(
        val key: Int,
        val at: Long,
        val pager: FeedPager,
        val page1: List<HomeSection>,
        val releases: List<SpotifyAlbum>,
        val trending: List<com.example.juke.home.FeedSong>,
        val tail: List<HomeSection>,
    )

    @Volatile
    var snapshot: Snapshot? = null
    const val FRESH_MS = 30 * 60_000L
    val SECTION_TIMEOUT = 15.seconds
    val PAGE_TIMEOUT = 25.seconds
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MusicDatabase.getDatabase(application)
    private val trackDao = database.trackDao()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private fun buildGreeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "First Light Sounds!"
        in 12..16 -> "Afternoon Drift!"
        in 17..20 -> "The Golden Hour!"
        else -> "After Dark!"
    }

    fun loadHomeData() {
        viewModelScope.launch {
            // Only block on the skeleton for the very first paint; local history loads in a blink.
            if (_uiState.value.greeting.isEmpty()) _uiState.update { it.copy(isLoading = true) }
            fetchData(force = false)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            fetchData(force = true)
        }
    }

    private var discoveryJob: Job? = null
    private var moreJob: Job? = null
    private var lastForce = false

    // The parts the head is assembled from; each lands on its own.
    private var local = Local()
    private var page1: List<HomeSection> = emptyList()
    private var releases: List<SpotifyAlbum> = emptyList()
    private var trending: List<com.example.juke.home.FeedSong> = emptyList()
    private var tail: List<HomeSection> = emptyList()
    private var pager: FeedPager? = null

    // Mood mode: its own pager and sections; the "For you" feed above stays intact underneath.
    private var mood: com.example.juke.home.YtmMoods.Mood? = null
    private var moodPager: com.example.juke.home.MoodPager? = null
    private var moodSections: List<HomeSection> = emptyList()
    private var moodJob: Job? = null

    private data class Local(
        val speedDial: List<Track> = emptyList(),
        val forgotten: List<Track> = emptyList(),
        val rotation: List<Track> = emptyList(),
    )

    private fun isOnline(): Boolean {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
            ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Called when connectivity returns; fills the feed if the network parts never landed. */
    fun onNetworkAvailable() {
        val st = _uiState.value
        if (st.discoveryOffline || (!st.headPending && page1.isEmpty() && trending.isEmpty())) {
            viewModelScope.launch { fetchData(force = true) }
        } else if (st.loadMoreFailed) {
            loadMore()
        }
    }

    private suspend fun fetchData(force: Boolean) {
        val recentlyPlayed = trackDao.getRecentlyPlayed(10).map { it.toTrack() }
        val mostPlayed = trackDao.getMostPlayed(40).map { it.toTrack() }
        val favorites = trackDao.getFavourites().map { it.toTrack() }

        local = Local(
            speedDial = LocalSections.speedDial(mostPlayed),
            forgotten = LocalSections.forgottenFavourites(favorites, Instant.now()),
            rotation = (recentlyPlayed + mostPlayed.take(10)).distinctBy { it.uuid }.take(12),
        )
        _uiState.update {
            it.copy(
                greeting = buildGreeting(),
                recentlyPlayed = recentlyPlayed,
                mostPlayed = mostPlayed.take(10),
                favorites = favorites.take(10),
                isLoading = false,
                isRefreshing = false,
            )
        }
        publish()
        if (_uiState.value.moods.isEmpty()) viewModelScope.launch {
            val moods = com.example.juke.home.YtmMoods.moods()
            _uiState.update { it.copy(moods = moods) }
        }
        mood?.let { if (force) { startMood(it); return } }
        lastForce = force
        discoveryJob?.cancel()
        // A page that was loading belongs to the old pager; drop it and its flags with it.
        moreJob?.cancel()
        _uiState.update { it.copy(loadingMore = false, loadMoreFailed = false) }
        discoveryJob = viewModelScope.launch { loadDiscovery(recentlyPlayed, mostPlayed, favorites, force) }
    }

    /** Head order alternates shapes so the eye never meets the same row twice in a row. */
    private fun publish() {
        if (mood != null) {
            _uiState.update { it.copy(sections = moodSections) }
            return
        }
        val head = buildList {
            page1.getOrNull(0)?.let(::add)
            if (local.speedDial.size >= 4) add(HomeSection.SpeedDial("speed_dial", "Speed dial", local.speedDial))
            page1.getOrNull(1)?.let(::add)
            if (local.forgotten.size >= 3) add(HomeSection.LocalRow("forgotten", "Forgotten favourites", local.forgotten))
            if (releases.isNotEmpty()) add(HomeSection.Releases("releases", "New releases", releases))
            page1.getOrNull(2)?.let(::add)
            if (trending.isNotEmpty()) add(HomeSection.Ranked("trending", "Trending songs", trending.take(10)))
            if (local.rotation.isNotEmpty()) add(HomeSection.LocalRow("rotation", "Back in rotation", local.rotation))
        }
        _uiState.update { it.copy(sections = head + tail) }
    }

    private fun newPager() = FeedPager(radio = { videoId ->
        withTimeoutOrNull(DiscoveryCache.SECTION_TIMEOUT) { JukesApi.fullRadio(videoId) }
            ?: error("radio timed out")
    })

    private suspend fun loadDiscovery(
        recent: List<Track>,
        mostPlayed: List<Track>,
        favorites: List<Track>,
        force: Boolean
    ) = coroutineScope {
        val known = recent + mostPlayed + favorites
        val knownKeys = known.mapTo(HashSet()) { RecommenderApi.songKey(it.title, it.artist) }
        val seeds = (recent.filter { it.ytVideoId != null }.distinctBy { it.artist }.take(8) +
                mostPlayed.filter { it.ytVideoId != null } +
                favorites.filter { it.ytVideoId != null }).distinctBy { it.uuid }
        val key = listOf(seeds.take(8).map { it.uuid }, known.map { it.uuid }.sorted()).hashCode()

        val cached = DiscoveryCache.snapshot
        if (cached != null && !force) {
            pager = cached.pager
            page1 = cached.page1; releases = cached.releases; trending = cached.trending; tail = cached.tail
            if (mood == null) _uiState.update { it.copy(headPending = false, discoveryOffline = false, endReached = cached.pager.exhausted && cached.tail.isNotEmpty()) }
            publish()
            val fresh = cached.key == key && System.currentTimeMillis() - cached.at < DiscoveryCache.FRESH_MS
            if (fresh) return@coroutineScope
        }

        if (!isOnline()) {
            _uiState.update { it.copy(headPending = false, discoveryOffline = cached == null) }
            return@coroutineScope
        }

        val fresh = newPager().also { it.reset(seeds, knownKeys) }
        if (mood == null) _uiState.update {
            it.copy(discoveryOffline = false, headPending = cached == null || force, loadMoreFailed = false, endReached = false)
        }

        val pageJob = async {
            val p = withTimeoutOrNull(DiscoveryCache.PAGE_TIMEOUT) { fresh.nextPage() }
            if (p != null && !p.failed) {
                pager = fresh
                page1 = p.sections
                tail = emptyList()
            }
            if (mood == null) _uiState.update { it.copy(headPending = false) }
            publish()
        }
        val releasesJob = async {
            withTimeoutOrNull(DiscoveryCache.SECTION_TIMEOUT) { newReleasesFor(known) }?.let { releases = it }
            publish()
        }
        val trendingJob = async {
            withTimeoutOrNull(DiscoveryCache.SECTION_TIMEOUT) { YtmCharts.trending() }
                ?.takeIf { it.isNotEmpty() }?.let { trending = it }
            publish()
        }
        awaitAll(pageJob, releasesJob, trendingJob)

        pager?.let { DiscoveryCache.snapshot = DiscoveryCache.Snapshot(key, System.currentTimeMillis(), it, page1, releases, trending, tail) }
        if (page1.isEmpty() && trending.isEmpty() && releases.isEmpty()) {
            _uiState.update { it.copy(discoveryOffline = true) }
        }
    }

    /** Picks a mood, or goes back to the "For you" feed when it is the mood already picked (or null). */
    fun selectMood(next: com.example.juke.home.YtmMoods.Mood?) {
        moodJob?.cancel()
        moreJob?.cancel()
        if (next == null || next.title == mood?.title) {
            mood = null; moodPager = null; moodSections = emptyList()
            _uiState.update {
                it.copy(selectedMood = null, headPending = false, loadingMore = false, loadMoreFailed = false,
                    endReached = false)
            }
            publish()
            return
        }
        startMood(next)
    }

    private fun startMood(next: com.example.juke.home.YtmMoods.Mood) {
        moodJob?.cancel()
        mood = next; moodPager = null; moodSections = emptyList()
        _uiState.update {
            it.copy(selectedMood = next.title, headPending = true, loadingMore = false, loadMoreFailed = false,
                endReached = false, isRefreshing = false)
        }
        publish()
        moodJob = viewModelScope.launch {
            val shelves = com.example.juke.home.YtmMoods.shelves(next)
            val p = com.example.juke.home.MoodPager(com.example.juke.home.YtmMoods::playlistSongs).also { it.reset(shelves) }
            val page = if (shelves.isEmpty()) null else withTimeoutOrNull(DiscoveryCache.PAGE_TIMEOUT) { p.nextPage() }
            if (page == null || page.failed) {
                _uiState.update { it.copy(headPending = false, loadMoreFailed = true) }
                return@launch
            }
            moodPager = p
            moodSections = page.sections
            _uiState.update { it.copy(headPending = false, endReached = p.exhausted) }
            publish()
        }
    }

    /** Appends the next page; called as the listener nears the bottom, and by the retry row. */
    fun loadMore() {
        mood?.let { m ->
            val mp = moodPager ?: return startMood(m) // the mood never loaded: retry from the top
            val st = _uiState.value
            if (st.loadingMore || st.endReached || st.headPending) return
            _uiState.update { it.copy(loadingMore = true, loadMoreFailed = false) }
            moreJob = viewModelScope.launch {
                val page = withTimeoutOrNull(DiscoveryCache.PAGE_TIMEOUT) { mp.nextPage() }
                if (page == null || page.failed) {
                    _uiState.update { it.copy(loadingMore = false, loadMoreFailed = true) }
                    return@launch
                }
                moodSections = moodSections + page.sections
                _uiState.update { it.copy(loadingMore = false, endReached = mp.exhausted) }
                publish()
            }
            return
        }
        val p = pager ?: return
        val st = _uiState.value
        if (st.loadingMore || st.endReached || st.headPending) return
        _uiState.update { it.copy(loadingMore = true, loadMoreFailed = false) }
        moreJob = viewModelScope.launch {
            val page = withTimeoutOrNull(DiscoveryCache.PAGE_TIMEOUT) { p.nextPage() }
            if (page == null || page.failed) {
                _uiState.update { it.copy(loadingMore = false, loadMoreFailed = true) }
                return@launch
            }
            tail = tail + page.sections
            DiscoveryCache.snapshot?.takeIf { it.pager === p }?.let {
                DiscoveryCache.snapshot = DiscoveryCache.Snapshot(it.key, it.at, p, page1, releases, trending, tail)
            }
            _uiState.update { it.copy(loadingMore = false, endReached = page.sections.isEmpty() && p.exhausted) }
            publish()
        }
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
