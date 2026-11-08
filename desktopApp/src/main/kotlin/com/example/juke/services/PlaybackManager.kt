package com.example.juke.services

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.media3.common.Player
import com.example.juke.database.MusicDatabase
import com.example.juke.database.toEntity
import com.example.juke.database.toTrack
import com.example.juke.models.Track
import com.example.juke.network.SpotifyApi
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import javax.swing.SwingUtilities
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

/** JavaFX toolkit, started once in embedded mode so it coexists with Compose's AWT loop. */
object Fx {
    private val ready: Boolean by lazy {
        if (SwingUtilities.isEventDispatchThread()) javafx.embed.swing.JFXPanel() else SwingUtilities.invokeAndWait { javafx.embed.swing.JFXPanel() }
        Platform.setImplicitExit(false); true
    }
    fun run(block: () -> Unit) { check(ready); Platform.runLater { runCatching(block).onFailure { Log.e("Fx", "fx task failed", it) } } }
}

/**
 * Desktop playback: the same queue API the Android PlaybackManager exposes, over a JavaFX
 * MediaPlayer (AAC/m4a, MP3, HLS). The queue lives here; tracks are plain [Track]s.
 */
class PlaybackManager private constructor(private val context: Context) {
    companion object {
        @Volatile private var INSTANCE: PlaybackManager? = null
        fun getInstance(context: Context): PlaybackManager = INSTANCE ?: synchronized(this) { INSTANCE ?: PlaybackManager(context.applicationContext).also { INSTANCE = it } }
    }

    private val tag = "PlaybackManager"
    val currentTrackNow: Track? get() = synchronized(lock) { queue.getOrNull(index) }
    private val prefs = context.getSharedPreferences("playback_state_prefs", Context.MODE_PRIVATE)
    private val database: MusicDatabase by lazy { MusicDatabase.getDatabase(context) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val musicService: MusicService by lazy { MusicService(context) }
    private val queueManager: QueueManager by lazy { QueueManager.getInstance(context) }

    private val lock = Any()
    private val queue = ArrayList<Track>()
    private var index = -1
    private var player: MediaPlayer? = null
    private var wantPlay = false
    private var speed = 1f
    @Volatile private var posMs = 0L
    @Volatile private var durMs = 0L
    private val recovering = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val retried = HashSet<String>()

    private val _isPlaying = MutableStateFlow(false); val isPlayingFlow: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _currentTrackId = MutableStateFlow<String?>(null); val currentTrackIdFlow: StateFlow<String?> = _currentTrackId.asStateFlow()
    private val _hasRestoredState = MutableStateFlow(false); val hasRestoredState: StateFlow<Boolean> = _hasRestoredState.asStateFlow()
    private val _currentQueueIndex = MutableStateFlow(0); val currentQueueIndexFlow: StateFlow<Int> = _currentQueueIndex.asStateFlow()
    private val _queueFlow = MutableStateFlow<List<String>>(emptyList()); val queueFlow: StateFlow<List<String>> = _queueFlow.asStateFlow()
    private val _sleepTimerRemaining = MutableStateFlow<Long?>(null); val sleepTimerRemaining: StateFlow<Long?> = _sleepTimerRemaining.asStateFlow()
    private val _isShuffleEnabled = MutableStateFlow(false); val isShuffleEnabledFlow: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF); val repeatModeFlow: StateFlow<Int> = _repeatMode.asStateFlow()
    private val _volume = MutableStateFlow(prefs.getFloat("volume", 1f)); val volumeFlow: StateFlow<Float> = _volume.asStateFlow()
    fun setVolume(v: Float) { val c = v.coerceIn(0f, 1f); _volume.value = c; prefs.edit { putFloat("volume", c) }; Fx.run { player?.volume = c.toDouble() } }
    val audioEffectController: AudioEffectController by lazy { AudioEffectController.get(context) }
    private var sleepJob: Job? = null

    private val _favouriteChanged = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 8)
    val favouriteChangedFlow: SharedFlow<Pair<String, Boolean>> = _favouriteChanged.asSharedFlow()
    fun emitFavouriteChanged(uuid: String, isFavourite: Boolean) { _favouriteChanged.tryEmit(uuid to isFavourite) }
    private val _trackPromoted = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val trackPromotedFlow: SharedFlow<String> = _trackPromoted.asSharedFlow()
    fun emitTrackPromoted(uuid: String) { _trackPromoted.tryEmit(uuid) }

    /** Set by the Jam while it drives this player, so its own commands aren't re-routed back to the room. */
    @Volatile var jamApplying = false

    // ---- queue state -> flows ----------------------------------------------------------------
    private fun publish() {
        synchronized(lock) {
            _queueFlow.value = queue.map { it.uuid }
            _currentQueueIndex.value = index.coerceAtLeast(0)
            _currentTrackId.value = queue.getOrNull(index)?.uuid
        }
    }

    private fun missing(t: Track): Boolean {
        val u = t.localUri ?: return true
        return !u.startsWith("http", true) && LocalAudio.isMissing(u)
    }
    private fun uriOf(t: Track): String? {
        val u = t.localUri ?: return null
        if (u.startsWith("http", true)) { val c = StreamCache.cached(u) ?: return u; return Mp4Defrag.playable(c).toURI().toString() }
        return Mp4Defrag.playable(File(u.removePrefix("file://"))).toURI().toString()
    }

    // ---- engine ------------------------------------------------------------------------------
    /** Loads queue[index]. Must be called without holding [lock]. */
    private fun load(startMs: Long = 0, play: Boolean = wantPlay) {
        val track: Track
        synchronized(lock) { track = queue.getOrNull(index) ?: run { stopPlayer(); return } }
        wantPlay = play
        posMs = startMs; durMs = track.durationSec * 1000L
        Fx.run { disposePlayer() } // old song stops now, even if the new one still has to be fetched
        publish()
        if (missing(track)) { if (track.spotifyId != null) recoverThenLoad(track, startMs) else skipAfterFailure(track); return }
        val raw = track.localUri
        if (raw != null && StreamCache.needsFetch(raw)) { fetchThenLoad(track, raw, startMs); return }
        val uri = uriOf(track) ?: return skipAfterFailure(track)
        val myIndex = index
        Fx.run {
            disposePlayer()
            val mp = MediaPlayer(Media(uri))
            player = mp
            audioEffectController.attach(mp)
            mp.volume = _volume.value.toDouble()
            mp.setOnReady {
                if (player !== mp) return@setOnReady
                durMs = mp.totalDuration.toMillis().takeIf { it > 0 && !it.isNaN() }?.toLong() ?: durMs
                if (startMs > 0) mp.seek(Duration.millis(startMs.toDouble()))
                if (wantPlay) { mp.play(); mp.rate = speed.toDouble() }
            }
            mp.setOnPlaying { _isPlaying.value = true }
            mp.setOnPaused { _isPlaying.value = false }
            mp.setOnStopped { _isPlaying.value = false }
            mp.currentTimeProperty().addListener { _, _, t -> if (player === mp) posMs = t.toMillis().toLong() }
            mp.setOnEndOfMedia { if (player === mp) scope.launch { onEnded(myIndex) } }
            mp.setOnError { Log.e(tag, "Player error for ${track.title}: ${mp.error?.message}"); if (player === mp) scope.launch { onFailed(track) } }
        }
        saveQueueStructure()
        validateUpcoming()
        prefetchNext()
    }

    private fun disposePlayer() { player?.let { runCatching { it.stop(); it.dispose() } }; player = null; _isPlaying.value = false }
    private fun stopPlayer() { wantPlay = false; Fx.run { disposePlayer() } }

    private fun onEnded(endedIndex: Int) {
        if (endedIndex != index) return
        when {
            _repeatMode.value == Player.REPEAT_MODE_ONE -> load(0, true)
            index < queue.size - 1 -> { index++; load(0, true) }
            _repeatMode.value == Player.REPEAT_MODE_ALL && queue.isNotEmpty() -> { index = 0; load(0, true) }
            else -> { wantPlay = false; _isPlaying.value = false; posMs = durMs }
        }
    }

    private fun skipAfterFailure(track: Track) {
        Log.w(tag, "Skipping unplayable '${track.title}'")
        if (queue.getOrNull(index)?.uuid != track.uuid) return
        if (index < queue.size - 1) { index++; load(0, wantPlay) } else { wantPlay = false; _isPlaying.value = false }
    }

    /** Expired stream URL, bad file, dropped connection: re-fetch once, otherwise move on. */
    private suspend fun onFailed(track: Track) {
        if (!retried.add(track.uuid) || track.spotifyId == null) return skipAfterFailure(track)
        recoverThenLoad(track, posMs)
    }

    /** Streams play from a cached copy: fetch it, then load again if this song is still the current one. */
    private fun fetchThenLoad(track: Track, url: String, startMs: Long) {
        scope.launch {
            try { withContext(Dispatchers.IO) { StreamCache.fetch(url) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e(tag, "Fetch failed for ${track.title}: ${e.message}"); if (queue.getOrNull(index)?.uuid == track.uuid) onFailed(track); return@launch }
            if (queue.getOrNull(index)?.uuid == track.uuid) load(startMs, wantPlay)
        }
    }

    private fun recoverThenLoad(track: Track, startMs: Long) {
        scope.launch {
            val fresh = recoverAudio(track)
            if (fresh == null) { skipAfterFailure(track); return@launch }
            synchronized(lock) { val i = queue.indexOfFirst { it.uuid == track.uuid }; if (i >= 0) queue[i] = fresh }
            if (queue.getOrNull(index)?.uuid == track.uuid) load(startMs, wantPlay)
        }
    }

    private suspend fun recoverAudio(track: Track): Track? {
        val spotifyId = track.spotifyId ?: return null
        if (!recovering.add(track.uuid)) return null
        return try {
            withTimeout(if (track.isStream) 45_000L else 150_000L) {
                val song = SpotifyApi.spotifyTrackToSong(SpotifyApi.getTrack(spotifyId))
                val pinned = synchronized(lock) { queue.map { it.uuid }.toSet() }
                if (track.isStream) musicService.streamTrack(song, preferredUuid = track.uuid, pinnedUuids = pinned + track.uuid).also { database.trackDao().insertTrack(it.toEntity()) }
                else musicService.smartDownloadAndIndex(song)
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.e(tag, "Recovery failed for ${track.title}: ${e.message}"); null } finally { recovering.remove(track.uuid) }
    }

    /** The next song's stream is pulled into the cache while this one plays, so the change is instant. */
    private fun prefetchNext() {
        val url = synchronized(lock) { queue.getOrNull(index + 1)?.localUri } ?: return
        if (StreamCache.needsFetch(url)) scope.launch(Dispatchers.IO) { runCatching { StreamCache.fetch(url) } }
    }

    /** Re-fetch audio for the next few songs now so they're ready when the current one ends. */
    private fun validateUpcoming() {
        val next = synchronized(lock) { (index + 1..index + 3).mapNotNull { queue.getOrNull(it) }.filter { missing(it) && it.spotifyId != null } }
        next.forEach { t -> scope.launch { recoverAudio(t)?.let { fresh -> synchronized(lock) { val i = queue.indexOfFirst { it.uuid == t.uuid }; if (i >= 0) queue[i] = fresh } } } }
    }

    // ---- public API (mirrors the Android PlaybackManager) --------------------------------------
    fun initialize() {
        if (_hasRestoredState.value || synchronized(lock) { queue.isNotEmpty() }) return
        scope.launch { restore() }
    }

    fun playTrack(track: Track) = setQueue(listOf(track), 0)

    fun setQueue(tracks: List<Track>, startIndex: Int = 0, startPositionMs: Long = -1L, keepShuffleMode: Boolean = false) {
        if (!keepShuffleMode) _isShuffleEnabled.value = false
        synchronized(lock) { queue.clear(); queue += tracks.filter { it.localUri != null }; index = startIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0)) }
        retried.clear()
        load(startPositionMs.coerceAtLeast(0), true)
    }

    fun addToQueue(tracks: List<Track>, allowDuplicates: Boolean = false) {
        synchronized(lock) {
            val have = queue.mapTo(HashSet()) { it.uuid }
            queue += tracks.filter { it.localUri != null && (allowDuplicates || have.add(it.uuid)) }
        }
        publish(); saveQueueStructure()
    }

    suspend fun insertNext(track: Track): Boolean {
        if (track.localUri == null) return false
        synchronized(lock) { queue.add((index + 1).coerceIn(0, queue.size), track) }
        publish(); saveQueueStructure(); return true
    }

    suspend fun placeLibraryTracks(tracks: List<Track>, next: Boolean): Boolean {
        val currentId = synchronized(lock) { queue.getOrNull(index)?.uuid }
        val incoming = tracks.distinctBy { it.uuid }.filterNot { it.uuid == currentId }
        if (incoming.isEmpty() || incoming.any { it.localUri == null }) return false
        synchronized(lock) {
            incoming.forEachIndexed { placed, t ->
                val cur = queue.getOrNull(index)
                queue.removeAll { it.uuid == t.uuid && it !== cur }
                index = queue.indexOf(cur).coerceAtLeast(0)
                val at = if (next) (index + 1 + placed).coerceIn(0, queue.size) else queue.size
                queue.add(at, t)
            }
        }
        publish(); saveQueueStructure(); return true
    }

    // On macOS a rate change starts a paused player natively (no status event), so a paused player is
    // never touched: the speed is stored and applied right after play().
    fun setPlaybackSpeed(s: Float) { speed = s; if (_isPlaying.value) Fx.run { player?.rate = s.toDouble() } }

    fun togglePlayPause() {
        if (!jamApplying && com.example.juke.jam.JamControls.togglePlayPause()) return
        val playing = _isPlaying.value
        wantPlay = !playing
        if (player == null && !playing) { if (synchronized(lock) { queue.isNotEmpty() }) load(posMs, true); return }
        Fx.run { player?.let { if (playing) it.pause() else { it.play(); it.rate = speed.toDouble() } } }
    }

    fun shouldResumeAfterTrackChange(): Boolean = wantPlay || _isPlaying.value

    fun skipToNext() {
        if (!jamApplying && com.example.juke.jam.JamControls.next()) return
        val ok = synchronized(lock) { if (index < queue.size - 1) { index++; true } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && queue.isNotEmpty()) { index = 0; true } else false }
        if (ok) load(0, true)
    }

    fun skipToPrevious() {
        if (!jamApplying && com.example.juke.jam.JamControls.previous()) return
        if (posMs > 3000 || index <= 0) { seekToLocal(0); return }
        synchronized(lock) { index-- }; load(0, true)
    }

    fun toggleShuffle() {
        val on = !_isShuffleEnabled.value; _isShuffleEnabled.value = on
        if (!on) return
        synchronized(lock) { val from = index + 1; if (from in 1 until queue.size) { val tail = queue.subList(from, queue.size).shuffled(); for (i in tail.indices) queue[from + i] = tail[i] } }
        publish(); saveQueueStructure()
    }

    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE; Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL; else -> Player.REPEAT_MODE_OFF }
    }

    fun seekTo(positionMs: Long) {
        if (!jamApplying && com.example.juke.jam.JamControls.seekTo(positionMs)) return
        seekToLocal(positionMs)
    }
    private fun seekToLocal(ms: Long) { val t = ms.coerceAtLeast(0); posMs = t; Fx.run { player?.seek(Duration.millis(t.toDouble())) } }

    fun seekToIndex(i: Int) { val ok = synchronized(lock) { if (i in queue.indices) { index = i; true } else false }; if (ok) load(0, shouldResumeAfterTrackChange()) }
    fun getCurrentPosition(): Long = posMs
    fun getDuration(): Long = durMs

    fun keepOnlyCurrentTrack() {
        synchronized(lock) { val cur = queue.getOrNull(index) ?: return; queue.clear(); queue += cur; index = 0 }
        publish(); saveQueueStructure()
    }

    fun removeFromQueueAt(i: Int, mediaId: String): Boolean {
        if (synchronized(lock) { queue.getOrNull(i)?.uuid } != mediaId) return removeFromQueue(mediaId)
        return removeAt(i)
    }
    fun removeFromQueue(mediaId: String): Boolean { val i = synchronized(lock) { queue.indexOfFirst { it.uuid == mediaId } }; return i >= 0 && removeAt(i) }

    private fun removeAt(i: Int): Boolean {
        var reload = false
        synchronized(lock) {
            if (i !in queue.indices) return false
            queue.removeAt(i)
            if (i < index) index-- else if (i == index) { reload = true; if (index >= queue.size) index = queue.size - 1 }
        }
        if (reload) { if (synchronized(lock) { queue.isEmpty() }) { stopPlayer(); publish() } else load(0, wantPlay) } else publish()
        saveQueueStructure(); return true
    }

    fun replaceTrackInQueue(oldMediaId: String, newTrack: Track, seamlessIfPlaying: Boolean = false, restartFromStart: Boolean = false): Boolean {
        if (newTrack.localUri == null) return false
        var reload = false
        synchronized(lock) {
            val i = queue.indexOfFirst { it.uuid == oldMediaId }; if (i < 0) return false
            queue[i] = newTrack; reload = i == index
        }
        if (reload) load(if (seamlessIfPlaying && !restartFromStart) posMs else 0, wantPlay) else publish()
        saveQueueStructure(); return true
    }

    fun removeDeletedTrackFromQueue(trackUuid: String) {
        while (true) { val i = synchronized(lock) { queue.indexOfFirst { it.uuid == trackUuid } }; if (i < 0) break; removeAt(i) }
    }

    fun moveInQueue(from: Int, to: Int): Boolean {
        synchronized(lock) {
            if (from !in queue.indices || to !in queue.indices) return false
            val cur = queue.getOrNull(index); queue.add(to, queue.removeAt(from)); index = queue.indexOf(cur).coerceAtLeast(0)
        }
        publish(); saveQueueStructure(); return true
    }

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        sleepJob = scope.launch {
            var left = minutes * 60_000L
            while (left > 0) { _sleepTimerRemaining.value = left; delay(1000); left -= 1000 }
            _sleepTimerRemaining.value = null; wantPlay = false; Fx.run { player?.pause() }
        }
    }
    fun cancelSleepTimer() { sleepJob?.cancel(); sleepJob = null; _sleepTimerRemaining.value = null }

    fun release() { cancelSleepTimer(); saveQueueStructure(); stopPlayer() }

    // ---- persistence ---------------------------------------------------------------------------
    private fun saveQueueStructure() {
        val (ids, i) = synchronized(lock) { queue.map { it.uuid } to index }
        if (ids.isEmpty()) return
        prefs.edit { putString("queue_track_ids", ids.joinToString(",")); putInt("queue_start_index", i); putLong("playback_position", posMs); putBoolean("has_saved_state", true) }
    }
    fun savePosition() { prefs.edit { putLong("playback_position", posMs); putInt("queue_start_index", index) } }

    private suspend fun restore() {
        val t0 = System.currentTimeMillis(); fun lap(m: String) = println("RESTORE +${System.currentTimeMillis() - t0}ms $m")
        try {
            if (!prefs.getBoolean("has_saved_state", false)) return
            val ids = prefs.getString("queue_track_ids", "")?.split(",")?.filter { it.isNotEmpty() } ?: return
            val savedIndex = prefs.getInt("queue_start_index", 0); val savedPos = prefs.getLong("playback_position", 0L)
            lap("ids=${ids.size}")
            val all = ids.mapNotNull { database.trackDao().getTrackByUuid(it)?.toTrack() }
            lap("loaded ${all.size}")
            if (all.isEmpty()) return
            val start = savedIndex.coerceIn(0, all.size - 1)
            // keep songs we can fetch again; drop the rest. Restore at the first one that is on disk.
            val playable = all.filter { !missing(it) || it.spotifyId != null }
            val at = playable.indexOfFirst { it.uuid == all[start].uuid }.takeIf { it >= 0 } ?: playable.indexOfFirst { !missing(it) }
            lap("playable=${playable.size} at=$at")
            if (at < 0) return
            if (synchronized(lock) { queue.isNotEmpty() } || _isPlaying.value) return // user already started something
            synchronized(lock) { queue += playable; index = at }
            queueManager.updateHistory(playable.take(at)); lap("history")
            queueManager.initializeQueue(playable.drop(at), preserveHistory = true); lap("queue init")
            load(if (playable[at].uuid == all[start].uuid) savedPos else 0, false)
            _hasRestoredState.value = true; lap("done")
        } catch (e: Exception) { Log.e(tag, "Failed to restore playback state: ${e.message}", e) }
    }
}
