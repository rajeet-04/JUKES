package com.example.juke.jam

import android.app.Application
import android.util.Log
import com.example.juke.BuildConfig
import com.example.juke.account.Account
import com.example.juke.account.LibrarySync
import com.example.juke.database.MusicDatabase
import com.example.juke.database.toTrack
import com.example.juke.models.Track
import com.example.juke.network.ApiClient
import com.example.juke.utils.playFromLine
import com.example.juke.viewmodels.MusicViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * Spotify Connect between the devices of one account (spotdown-kv-worker/src/connect.ts). Every logged-in device
 * keeps a socket to the account's hub and reports its own playback (song, queue, position). Playback is independent:
 * each device plays its own music, and any device can see and remote-control each of the others on its own
 * (play/pause, next, seek, a song in its queue). "Play on this device" starts another device's queue here at the
 * same spot, and "Play there" does the reverse; the device it came from keeps playing.
 * Also runs [LibrarySync] while logged in: at login, every 10 minutes, and when another device saved changes.
 */
object Connect {
    /** A song as it travels between devices: both ids, so the receiver can find it locally or stream it. */
    @Serializable
    data class Song(val title: String, val artist: String, val durationSec: Int = 0, val thumb: String? = null, val spotifyId: String? = null, val yt: String? = null)

    @Serializable
    data class State(
        /** The device this state belongs to (named for the older one-active-device model). */
        val activeId: String = "", val activeName: String = "", val track: Song? = null, val queue: List<Song> = emptyList(),
        val index: Int = 0, val posMs: Long = 0, val playing: Boolean = false, val at: Long = 0
    )

    @Serializable data class Device(val deviceId: String, val name: String, val kind: String = "phone")

    @Serializable
    private data class In(
        val t: String, val now: Long = 0, val state: State? = null, val states: List<State>? = null, val devices: List<Device>? = null,
        val op: String? = null, val posMs: Long? = null, val index: Int? = null, val by: String? = null, val from: String? = null, val code: String? = null
    )

    @Serializable
    private data class Out(
        val t: String, val deviceId: String? = null, val name: String? = null, val kind: String? = null,
        val track: Song? = null, val queue: List<Song>? = null, val index: Int? = null, val posMs: Long? = null, val playing: Boolean? = null,
        val op: String? = null, val target: String? = null
    )

    private const val TAG = "Connect"
    private const val QUEUE_SENT = 50
    private const val SYNC_EVERY_MS = 10 * 60_000L
    private const val DRIFT_MS = 3000L // a jump this big from where we said we'd be is a seek: tell the others
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
    private val WS = Account.BASE.replaceFirst("http", "ws")

    private val _states = MutableStateFlow<Map<String, State>>(emptyMap())
    /** Each device's playback, by device id (this one's included). */
    val states: StateFlow<Map<String, State>> = _states
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message
    @Volatile var me: String = ""; private set
    @Volatile private var serverOffset = 0L // server clock minus ours
    @Volatile private var outbox: Channel<String>? = null
    @Volatile private var queueBase = 0     // the local queue index our published queue starts at
    private val syncNow = Channel<Unit>(Channel.CONFLATED)

    private val client by lazy {
        HttpClient(OkHttp) {
            engine { preconfigured = ApiClient.okHttp.newBuilder().readTimeout(0, TimeUnit.SECONDS).pingInterval(25, TimeUnit.SECONDS).build() }
            install(WebSockets)
        }
    }

    /** Where that device is in its song now, by the server's clock. */
    fun livePos(s: State): Long = if (s.playing) s.posMs + (System.currentTimeMillis() + serverOffset - s.at) else s.posMs

    private fun send(o: Out) { outbox?.trySend(json.encodeToString(o)) }

    // ---- remote control, from the UI ----
    /** A remote control press for [deviceId]: toggle, next, prev, seek ([posMs]) or index (a song in its published queue). */
    fun command(deviceId: String, op: String, posMs: Long? = null, index: Int? = null) = send(Out("cmd", op = op, posMs = posMs, index = index, target = deviceId))
    /** Move this device's playback to [deviceId]: it starts there, and this one pauses once that device reports playing. */
    fun playOn(deviceId: String) {
        handingTo = deviceId; handingSince = System.currentTimeMillis()
        send(Out("cmd", op = "take", target = deviceId))
    }
    @Volatile private var handingTo: String? = null
    @Volatile private var handingSince = 0L
    private const val HANDOFF_WAIT_MS = 30_000L // it never started there: keep playing here
    @Volatile private var joinRequest: Pair<String, Boolean>? = null
    /** Start [deviceId]'s queue here at its position. Join: it keeps playing. [move]: it pauses once this one plays. */
    fun playHere(deviceId: String, move: Boolean) { joinRequest = deviceId to move; joinNow.trySend(Unit) }
    private val joinNow = Channel<Unit>(Channel.CONFLATED)
    /** Called after this device saved the library, so the others fetch it now. */
    fun librarySaved() = send(Out("synced"))
    fun requestSync() { syncNow.trySend(Unit) }

    private fun Track.toSong() = Song(title, artist, durationSec, thumbnailUri?.takeIf { it.startsWith("http") }, spotifyId, ytVideoId)

    /** Started once from the app root; runs for the life of the app. */
    suspend fun run(vm: MusicViewModel) = coroutineScope {
        val c = vm.getApplication<Application>()
        Account.load(c)
        me = c.getSharedPreferences("account", android.content.Context.MODE_PRIVATE).getString("device_id", null)
            ?: UUID.randomUUID().toString().replace("-", "").also { c.getSharedPreferences("account", android.content.Context.MODE_PRIVATE).edit().putString("device_id", it).apply() }
        launch { // library sync
            while (true) {
                Account.email.first { it != null }
                LibrarySync.sync(c)
                kotlinx.coroutines.withTimeoutOrNull(SYNC_EVERY_MS.milliseconds) { syncNow.receive() }
            }
        }
        launch(Dispatchers.Main) { // "play on this device": the other device's latest state, started here
            for (u in joinNow) {
                val (id, move) = joinRequest ?: continue
                val s = _states.value[id]?.takeIf { it.track != null } ?: continue
                if (take(vm, s, s.activeName) && move) command(id, "pause")
            }
        }
        var attempt = 0
        while (true) {
            Account.email.first { it != null }
            try {
                client.webSocket("$WS/connect/ws", request = {
                    header("X-App-Secret", BuildConfig.SPOTIFY_TOKEN_KEY); header("Authorization", "Bearer ${Account.token}")
                }) {
                    val box = Channel<String>(Channel.UNLIMITED)
                    outbox = box
                    val writer = launch { for (text in box) send(Frame.Text(text)) }
                    // the player (Media3's controller on Android) only works on the main thread; the socket runs on OkHttp's
                    val publisher = launch(Dispatchers.Main) { publishLoop(vm) }
                    try {
                        send(Frame.Text(json.encodeToString(Out("hello", deviceId = me, name = Account.deviceName(), kind = if (Account.isDesktop) "desktop" else "phone"))))
                        _connected.value = true; attempt = 0
                        for (frame in incoming) if (frame is Frame.Text) { val text = frame.readText(); withContext(Dispatchers.Main) { handle(vm, text) } }
                    } catch (e: Throwable) {
                        Log.w(TAG, "socket failed", e); throw e
                    } finally {
                        outbox = null; _connected.value = false
                        box.close(); writer.cancel(); publisher.cancel()
                    }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w(TAG, "socket ended: ${e.message}")
                if (e.message?.contains("401") == true) LibrarySync.sync(c) // a dead login gets cleared by the next library call
            }
            _connected.value = false
            delay(minOf(30_000L, 1000L shl minOf(attempt++, 5)).milliseconds)
        }
    }

    /** Keep the hub current about this device's own playback: on a new song, play/pause, a queue move or a seek. */
    private suspend fun publishLoop(vm: MusicViewModel) {
        val pm = vm.playbackManager
        var last: Out? = null; var lastAt = 0L
        while (true) {
            delay(1000.milliseconds)
            val ui = vm.uiState.value
            val cur = ui.currentTrack ?: continue
            val playing = pm.isPlayingFlow.value
            val pos = pm.getCurrentPosition()
            val song = cur.toSong()
            val expected = last?.let { (it.posMs ?: 0) + if (it.playing == true) System.currentTimeMillis() - lastAt else 0 } ?: 0
            val changed = last == null || last.track != song || last.playing != playing || last.index != ui.queueIndex || (playing && abs(pos - expected) > DRIFT_MS)
            if (!changed) continue
            val base = maxOf(ui.queueIndex, 0)
            queueBase = base
            val queue = (if (ui.queue.isEmpty()) listOf(cur) else ui.queue.drop(base)).take(QUEUE_SENT).map { it.toSong() }
            val out = Out("state", track = song, queue = queue, index = ui.queueIndex, posMs = pos, playing = playing)
            send(out); last = out; lastAt = System.currentTimeMillis()
        }
    }

    private suspend fun handle(vm: MusicViewModel, text: String) {
        val m = runCatching { json.decodeFromString<In>(text) }.onFailure { Log.w(TAG, "unreadable message (${text.take(200)}): ${it.message}") }.getOrNull() ?: return
        if (m.now > 0) serverOffset = m.now - System.currentTimeMillis()
        val pm = vm.playbackManager
        when (m.t) {
            "welcome" -> _states.value = m.states.orEmpty().associateBy { it.activeId }
            "devices" -> _devices.value = m.devices.orEmpty()
            "state" -> m.state?.let { s ->
                _states.value = _states.value + (s.activeId to s)
                // "Play there" took (that device now plays our song, not what it had before): stop here
                if (s.activeId == handingTo && s.playing && s.track?.title == _states.value[me]?.track?.title) {
                    handingTo = null
                    if (System.currentTimeMillis() - handingSince < HANDOFF_WAIT_MS && pm.isPlayingFlow.value) pm.togglePlayPause()
                }
            }
            "synced" -> requestSync()
            "error" -> _message.value = when (m.code) {
                "device_gone" -> "That device went offline"; "nothing_playing" -> "Nothing is playing on this device"; else -> "Something went wrong"
            }
            "cmd" -> when (m.op) { // a remote control press from another device, for this one
                "toggle" -> pm.togglePlayPause()
                "play" -> if (!pm.isPlayingFlow.value) pm.togglePlayPause()
                "pause" -> if (pm.isPlayingFlow.value) pm.togglePlayPause()
                "next" -> vm.skipToNext()
                "prev" -> vm.skipToPrevious()
                "seek" -> m.posMs?.let { vm.seekTo(it) }
                "index" -> m.index?.let { i -> val q = vm.uiState.value.queue; q.getOrNull(queueBase + i)?.let { vm.playTrackFromQueue(it, queueBase + i) } }
            }
            "take" -> { take(vm, m.state ?: return, m.from.orEmpty()) }
        }
    }

    /** Start the other device's queue here at its position. */
    private suspend fun take(vm: MusicViewModel, s: State, from: String): Boolean {
        val c = vm.getApplication<Application>()
        val tracks = playable(c, s.queue)
        val first = tracks.firstOrNull() ?: run { _message.value = "Couldn't play that queue here"; return false }
        _message.value = "Playing here from ${from.ifBlank { s.activeName }}…"
        val startAt = livePos(s)
        val sentAt = System.currentTimeMillis()
        vm.playFromLine(first, startAt) { at -> vm.setQueue(tracks, 0, startPositionMs = at + (System.currentTimeMillis() - sentAt)) }
        _message.value = ""
        return true
    }

    /**
     * Songs from another device as tracks this one can play: the local copy when the song is saved here,
     * else a stream entry the player fetches when it gets to it (needs a Spotify id; others are left out).
     */
    suspend fun playable(c: android.content.Context, songs: List<Song>): List<Track> {
        val dao = MusicDatabase.getDatabase(c).trackDao()
        val local = dao.getAllTracks() + dao.getStreamTracks()
        val bySp = local.filter { it.spotifyId != null }.associateBy { it.spotifyId }
        val byYt = local.filter { it.ytVideoId != null }.associateBy { it.ytVideoId }
        return songs.mapNotNull { q ->
            (q.spotifyId?.let { bySp[it] } ?: q.yt?.let { byYt[it] })?.toTrack()
                ?: q.spotifyId?.let { pendingStream(c, UUID.randomUUID().toString().replace("-", ""), q.title, q.artist, q.durationSec, q.thumb, it, q.yt) }
        }
    }

    /**
     * A stream song not fetched yet. It points at the file the stream will be saved to; both players fetch a
     * missing file by its Spotify id when they reach it (the same path a restored queue takes).
     */
    fun pendingStream(c: android.content.Context, uuid: String, title: String, artist: String, durationSec: Int, thumb: String?, spotifyId: String, yt: String?) =
        Track(uuid = uuid, title = title, artist = artist, thumbnailUri = thumb, durationSec = durationSec,
            localUri = java.io.File(c.filesDir, "stream_files/${uuid}_stream.mp3").absolutePath, ytVideoId = yt, spotifyId = spotifyId, isStream = true)
}
