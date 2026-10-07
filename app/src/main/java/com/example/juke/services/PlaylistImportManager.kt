package com.example.juke.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.example.juke.database.MusicDatabase
import com.example.juke.database.PendingImportEntity
import com.example.juke.database.PlaylistEntity
import com.example.juke.database.PlaylistTrackEntity
import com.example.juke.models.SpotifyTrack
import com.example.juke.network.SpotifyApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

/** Progress of one import, for the greyed-out playlist in Library and the Search progress card. */
data class ImportStatus(
    val playlistId: String,
    val done: Int,
    val total: Int,
    val waitingForNetwork: Boolean
)

/**
 * Spotify playlist imports that outlive the screen, the ViewModel and the process.
 *
 * The to-do list is the `pending_imports` table: each track is deleted when it lands in the
 * playlist, so after the app is closed [resume] (called from Application.onCreate) just continues.
 * Downloads wait while offline and retry when the network returns; failed tracks are retried a
 * few times with backoff before being dropped so one bad track can't keep a playlist grey forever.
 */
class PlaylistImportManager private constructor(context: Context) {

    companion object {
        @Volatile private var instance: PlaylistImportManager? = null
        fun get(context: Context): PlaylistImportManager =
            instance ?: synchronized(this) {
                instance ?: PlaylistImportManager(context.applicationContext).also { instance = it }
            }

        private const val MAX_ATTEMPTS = 5
        private const val PARALLEL = 6
        private const val TAG = "PlaylistImport"
    }

    private val appContext = context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dao by lazy { MusicDatabase.getDatabase(appContext).playlistDao() }
    private val musicService by lazy { MusicService(appContext) }
    private val queueManager by lazy { QueueManager.getInstance(appContext) }
    private val json = Json { ignoreUnknownKeys = true }
    private val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val online = MutableStateFlow(isOnlineNow())
    private var job: Job? = null

    init {
        try {
            connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { online.value = true }
                override fun onLost(network: Network) { online.value = isOnlineNow() }
                // Leaving the app, Android (and vivo's battery manager) blocks its network; coming
                // back only lifts the block, with no onAvailable. Missing this kept the import
                // waiting for a network that was already there.
                override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                    online.value = !blocked && isOnlineNow()
                }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    online.value = isOnlineNow()
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "No network callback: ${e.message}")
        }
    }

    private fun isOnlineNow(): Boolean {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** One entry per playlist that still has tracks to download; empty when nothing is importing. */
    val status: StateFlow<Map<String, ImportStatus>> by lazy {
        combine(dao.pendingImportCounts(), online) { counts, isOnline ->
            counts.associate { c ->
                val done = dao.getPlaylistTrackCount(c.playlistId)
                c.playlistId to ImportStatus(c.playlistId, done, done + c.remaining, !isOnline)
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())
    }

    /** Save the playlist and its track list, then start (or continue) downloading. */
    suspend fun enqueue(playlist: PlaylistEntity, tracks: List<SpotifyTrack>) {
        dao.insertPlaylist(playlist)
        dao.insertPendingImports(
            tracks.mapIndexed { index, t ->
                PendingImportEntity(playlist.id, index, json.encodeToString(t))
            }
        )
        resume()
    }

    /** Safe to call any time; does nothing if a run is already active or nothing is pending. */
    fun resume() {
        online.value = isOnlineNow() // a stale "offline" must not keep a running import parked
        if (job?.isActive == true) return
        job = scope.launch { run() }
    }

    /** Wait for the network, re-checking now and then in case a connectivity callback never comes. */
    private suspend fun awaitOnline() {
        while (!online.value) {
            withTimeoutOrNull(15_000L.milliseconds) { online.first { it } }
            if (!online.value) online.value = isOnlineNow()
        }
    }

    private suspend fun run() {
        val permits = Semaphore(PARALLEL)
        val touched = mutableSetOf<String>()
        var failures = 0
        while (true) {
            val batch = dao.nextPendingImports(PARALLEL * 2)
            if (batch.isEmpty()) break
            awaitOnline() // paused until the network is back
            var failed = false
            coroutineScope {
                batch.map { item ->
                    async {
                        permits.withPermit {
                            touched += item.playlistId
                            if (!download(item)) failed = true
                        }
                    }
                }.awaitAll()
            }
            failures = if (failed) failures + 1 else 0
            if (failed) delay((3_000L * minOf(failures, 5)).milliseconds) // backoff, then retry what is left
        }
        touched.forEach { dao.updatePlaylistTrackCount(it, dao.getPlaylistTrackCount(it)) }
    }

    /** True on success or when the track was given up on; false when it should be retried. */
    private suspend fun download(item: PendingImportEntity): Boolean {
        val track = try {
            json.decodeFromString<SpotifyTrack>(item.trackJson)
        } catch (e: Exception) {
            dao.deletePendingImport(item.playlistId, item.position) // unreadable row, nothing to retry
            return true
        }
        val artist = track.artists.joinToString(", ") { it.name }
        queueManager.addDownloadTracking(track.name, artist, "playlist")
        return try {
            val downloaded = musicService.smartDownloadAndIndex(SpotifyApi.spotifyTrackToSong(track))
            dao.insertPlaylistTrack(PlaylistTrackEntity(item.playlistId, downloaded.uuid, item.position))
            dao.deletePendingImport(item.playlistId, item.position)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val network = e is IOException || e.cause is IOException || !isOnlineNow()
            Log.w(TAG, "Import of '${track.name}' failed (network=$network): ${e.message}")
            if (network) {
                online.value = isOnlineNow() // offline failures don't count against the track
            } else if (item.attempts + 1 >= MAX_ATTEMPTS) {
                dao.deletePendingImport(item.playlistId, item.position)
                return true
            } else {
                dao.bumpPendingAttempts(item.playlistId, item.position)
            }
            false
        } finally {
            queueManager.removeDownloadTracking(track.name, artist)
        }
    }
}
