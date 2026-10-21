package com.example.juke.jam

import com.example.juke.analytics.AnalyticsManager
import com.example.juke.analytics.EventProperty
import com.example.juke.analytics.EventType
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.juke.BuildConfig
import com.example.juke.models.SpotifyTrack
import com.example.juke.models.Track
import com.example.juke.network.ApiClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.isSuccess
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * The Jam the user is in, if any: a room on the Worker (see spotdown-kv-worker/src/jam.ts) that keeps one
 * shared queue. The server orders every edit and echoes it back; this class only applies what it hears.
 * Nothing here stores anything on the phone except a nickname and a random client id.
 */
object JamSession {
    private const val TAG = "JamSession"
    private const val PREFS = "jam_prefs"
    private const val ROOM_MAX_IDLE_MS = 24 * 3600 * 1000L // the Worker deletes a room idle this long

    enum class Status { IDLE, CONNECTING, CONNECTED, RECONNECTING }

    data class UiState(
        val status: Status = Status.IDLE,
        val code: String? = null,
        val you: String? = null,
        /** The longest-connected member; passes on when they leave. Same powers as everyone else. */
        val hostId: String? = null,
        val members: List<JamMember> = emptyList(),
        val queue: List<JamItem> = emptyList(),
        val rev: Int = 0,
        val playback: JamPlayback? = null,
        /** Something to tell the user: an error, or why the Jam closed. */
        val message: String? = null
    ) {
        val active get() = status != Status.IDLE
    }

    @Serializable
    data class Info(val exists: Boolean = false, val members: Int = 0, val queue: Int = 0)

    @Serializable
    private data class Created(val code: String)

    @Serializable
    private data class WireAdd(val id: String, val track: JamTrack)

    /** The host's player when starting: current song and upcoming queue become the room's. */
    class Seed(
        val items: List<Pair<String, JamTrack>>,
        val current: Int?,
        val posMs: Long,
        val playing: Boolean,
        val capturedAt: Long = System.currentTimeMillis(),
        /** Ids of the items the host's radio put in its queue (not ones the host queued by hand). */
        val radioIds: Set<String> = emptySet()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** Whether the Jam sheet is showing; any screen can open it. */
    val sheetOpen = MutableStateFlow(false)

    private val BASE = BuildConfig.SHARE_BASE_URL.trimEnd('/')
    private val WS_BASE = BASE.replaceFirst("http", "ws") // https -> wss

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // The shared client's default read timeout would drop an idle socket; OkHttp pings keep it alive instead.
    private val client by lazy {
        HttpClient(OkHttp) {
            engine {
                preconfigured = ApiClient.okHttp.newBuilder().readTimeout(0, TimeUnit.SECONDS).pingInterval(25, TimeUnit.SECONDS).build()
            }
            install(WebSockets)
        }
    }

    private var loop: Job? = null
    @Volatile private var socket: WebSocketSession? = null
    @Volatile private var outbox: Channel<String>? = null
    @Volatile private var attempt = 0
    /** Pinged whenever the room's playback changes, so the follower reacts at once instead of on its next tick. */
    val playbackChanged = Channel<Unit>(Channel.CONFLATED)
    /** Someone pressed Mix: the host starts a fresh radio from the current song. */
    val mixRequested = Channel<Unit>(Channel.CONFLATED)

    /** The songs still to come when this user leaves on purpose, so the app can keep them as the local queue. */
    val leftWithQueue = Channel<List<JamItem>>(Channel.CONFLATED)
    private val nudge = Channel<Unit>(Channel.CONFLATED) // wakes a waiting reconnect (network back, app in front)
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var lifecycleHooked = false

    fun link(code: String) = "$BASE/j/$code"

    fun savedName(context: Context): String =
        prefs(context).getString("name", null)?.takeIf { it.isNotBlank() } ?: Build.MODEL.take(24)

    fun saveName(context: Context, name: String) = prefs(context).edit().putString("name", name.trim().take(24)).apply()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun clientId(context: Context): String =
        prefs(context).getString("client_id", null) ?: UUID.randomUUID().toString().replace("-", "").also {
            prefs(context).edit().putString("client_id", it).apply()
        }

    /** Asks the Worker for a new room; null if that failed. */
    suspend fun create(): String? = try {
        val res = ApiClient.httpClient.post("$BASE/jam") { header("X-App-Secret", BuildConfig.SPOTIFY_TOKEN_KEY) }
        if (res.status.isSuccess()) res.body<Created>().code.also { AnalyticsManager.log(EventType.JAM_CREATED) } else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Create failed: ${e.message}")
        null
    }

    /** How the room looks right now, or null if the check itself failed (offline). */
    suspend fun info(code: String): Info? = try {
        val res = ApiClient.httpClient.get("$BASE/jam/$code")
        if (res.status.isSuccess()) res.body<Info>() else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    @Volatile private var pendingSeed: Seed? = null

    /** [seed] only counts if this phone ends up as host of a brand-new room. */
    fun join(context: Context, code: String, name: String, seed: Seed? = null, via: String = "code") {
        val app = context.applicationContext
        joinedAt = System.currentTimeMillis()
        AnalyticsManager.log(EventType.JAM_JOINED, mapOf(EventProperty.VIA to via, EventProperty.SEEDED to (seed != null)))
        stop()
        pendingSeed = seed
        saveName(app, name)
        rememberSession(app, code.uppercase(), name.trim().take(24).ifBlank { "Guest" })
        val id = clientId(app)
        _state.value = UiState(status = Status.CONNECTING, code = code.uppercase())
        watchConnectivity(app)
        loop = scope.launch { run(code.uppercase(), id, name.trim().take(24).ifBlank { "Guest" }) }
    }

    @Volatile private var joinedAt = 0L

    fun leave() {
        val st = _state.value
        AnalyticsManager.log(
            EventType.JAM_LEFT,
            mapOf(
                EventProperty.DURATION_SECONDS to (System.currentTimeMillis() - joinedAt) / 1000,
                EventProperty.MEMBERS to st.members.size,
                EventProperty.QUEUE_SIZE to st.queue.size,
                EventProperty.IS_HOST to (st.hostId != null && st.hostId == st.you)
            )
        )
        val idx = st.queue.indexOfFirst { it.id == st.playback?.itemId }
        val upNext = if (idx >= 0) st.queue.drop(idx + 1) else st.queue
        if (upNext.isNotEmpty()) leftWithQueue.trySend(upNext)
        stop()
        appContext?.let { clearSession(it) } // leaving on purpose: don't rejoin next time the app opens
        _state.value = UiState()
    }

    /** The Jam is remembered so closing the app (or having it killed) doesn't drop you out of it. */
    private fun rememberSession(context: Context, code: String, name: String) =
        prefs(context).edit().putString("session_code", code).putString("session_name", name).putLong("session_at", System.currentTimeMillis()).apply()

    private fun clearSession(context: Context) =
        prefs(context).edit().remove("session_code").remove("session_name").remove("session_at").apply()

    @Volatile private var resumed = false

    /**
     * Called when the app opens: if this phone was in a Jam and didn't leave it, go back in. The room keeps its
     * queue on the server (24 hours idle), so the queue and what's playing come straight back. If the room is gone,
     * the normal "Jam ended" path clears this.
     */
    fun resume(context: Context) {
        if (_state.value.active) return
        val p = prefs(context)
        val code = p.getString("session_code", null) ?: return
        if (System.currentTimeMillis() - p.getLong("session_at", 0) > ROOM_MAX_IDLE_MS) { clearSession(context); return }
        resumed = true
        join(context, code, p.getString("session_name", null) ?: savedName(context), via = "resume")
    }

    fun add(track: JamTrack, afterId: String? = null): Boolean = send(buildJsonObject {
        put("t", "add")
        put("id", UUID.randomUUID().toString().replace("-", ""))
        put("track", json.encodeToJsonElement(JamTrack.serializer(), track))
        put("afterId", afterId)
    })

    /** Adds [tracks] as one run after [afterId] (or at the end); returns their new item ids in order, empty if not sent. */
    fun addMany(tracks: List<JamTrack>, afterId: String?, label: String? = null): List<String> {
        val items = tracks.take(MAX_QUEUE).map { UUID.randomUUID().toString().replace("-", "") to it }
        if (items.isEmpty()) return emptyList()
        val sent = send(buildJsonObject {
            put("t", "addMany")
            put("afterId", afterId)
            if (label != null) put("label", label)
            put("items", kotlinx.serialization.json.buildJsonArray {
                items.forEach { (id, track) ->
                    add(buildJsonObject { put("id", id); put("track", json.encodeToJsonElement(JamTrack.serializer(), track)) })
                }
            })
        })
        return if (sent) items.map { it.first } else emptyList()
    }

    /** Runs [block] on the Jam's own scope (it outlives screens and view models). */
    fun launch(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) = scope.launch(block = block)

    /** A short message on screen, from anywhere. */
    fun toast(text: String) {
        val c = appContext ?: return
        Handler(Looper.getMainLooper()).post { android.widget.Toast.makeText(c, text, android.widget.Toast.LENGTH_SHORT).show() }
    }

    fun remove(id: String): Boolean = send(buildJsonObject { put("t", "remove"); put("id", id) })

    fun move(id: String, beforeId: String?): Boolean =
        send(buildJsonObject { put("t", "move"); put("id", id); put("beforeId", beforeId) })

    /** [regenerate]: a new song was started on purpose, so the radio songs queued after it are replaced by a fresh radio. */
    fun play(itemId: String? = null, posMs: Long? = null, regenerate: Boolean = false): Boolean =
        send(buildJsonObject {
            put("t", "play"); put("itemId", itemId); put("posMs", posMs)
            if (regenerate) put("regen", true)
        })

    /** "Mix": a fresh radio from the current song for the whole room. */
    fun mix(): Boolean = AnalyticsManager.log(EventType.JAM_MIX).let { send(buildJsonObject { put("t", "mix") }) }

    fun pause(): Boolean = send(buildJsonObject { put("t", "pause") })

    /** This phone reached the end of [itemId]; the room moves on once, however many phones say so. */
    fun ended(itemId: String): Boolean = send(buildJsonObject { put("t", "ended"); put("itemId", itemId) })

    private fun sendSeedIfHost() {
        val seed = pendingSeed ?: return
        pendingSeed = null
        val st = _state.value
        if (st.hostId == null || st.hostId != st.you || st.rev != 0 || st.queue.isNotEmpty()) return // only a fresh room's host seeds
        val elapsed = if (seed.playing) System.currentTimeMillis() - seed.capturedAt else 0L // the song kept playing while we connected
        send(buildJsonObject {
            put("t", "seed")
            put("items", kotlinx.serialization.json.buildJsonArray {
                seed.items.forEach { (id, track) ->
                    add(buildJsonObject {
                        put("id", id); put("track", json.encodeToJsonElement(JamTrack.serializer(), track))
                        if (id in seed.radioIds) put("radio", true)
                    })
                }
            })
            put("current", seed.current)
            put("posMs", seed.posMs + elapsed)
            put("playing", seed.playing)
        })
    }

    fun seek(posMs: Long): Boolean = send(buildJsonObject { put("t", "seek"); put("posMs", posMs) })

    // Clock sync: NTP-style, keep the sample with the lowest round trip.
    @Volatile var clockOffsetMs = 0L
        private set
    @Volatile var clockRttMs = Long.MAX_VALUE
        private set
    private var clockJob: Job? = null

    /** The server's clock, as best we know it. */
    fun serverNow(): Long = System.currentTimeMillis() + clockOffsetMs

    private fun startClockSync() {
        clockJob?.cancel()
        clockRttMs = Long.MAX_VALUE
        clockJob = scope.launch {
            while (isActive) {
                repeat(5) {
                    socket?.let { s -> try { s.send(Frame.Text(buildJsonObject { put("t", "ping"); put("t0", System.currentTimeMillis()) }.toString())) } catch (_: Exception) {} }
                    kotlinx.coroutines.delay(250)
                }
                kotlinx.coroutines.delay(30_000)
                clockRttMs = Long.MAX_VALUE // let a fresh, possibly slower, sample replace the old best
            }
        }
    }

    private fun onPong(obj: JsonObject) {
        val now = System.currentTimeMillis()
        val t0 = obj["t0"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return
        val server = obj["serverTime"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return
        val rtt = now - t0
        if (rtt in 0 until clockRttMs) {
            clockRttMs = rtt
            clockOffsetMs = server + rtt / 2 - now
        }
    }

    private fun send(msg: JsonObject): Boolean {
        val box = outbox
        if (socket == null || box == null || _state.value.status != Status.CONNECTED) {
            Log.w(TAG, "send dropped (${msg["t"]}): socket=${socket != null} status=${_state.value.status}")
            _state.update { it.copy(message = "Not connected to the Jam") }
            return false
        }
        // One unbounded outbox per connection, drained by a single writer: messages leave in the order they were sent
        // and none can be dropped (the socket's own outgoing channel can refuse a send, and that was easy to miss).
        val result = box.trySend(msg.toString())
        if (result.isFailure) Log.w(TAG, "outbox refused ${msg["t"]}: $result")
        return result.isSuccess
    }

    private suspend fun run(code: String, clientId: String, name: String) {
        while (currentCoroutineContext().isActive) {
            try {
                client.webSocket("$WS_BASE/jam/$code/ws") {
                    socket = this
                    val box = Channel<String>(Channel.UNLIMITED)
                    outbox = box
                    val writer = launch { for (text in box) send(Frame.Text(text)) }
                    try {
                        send(Frame.Text(buildJsonObject { put("t", "hello"); put("clientId", clientId); put("name", name) }.toString()))
                        for (frame in incoming) if (frame is Frame.Text) handle(frame.readText())
                    } finally {
                        outbox = null
                        box.close()
                        writer.cancel()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Socket ended: ${e.message}")
            }
            socket = null
            if (info(code)?.exists == false) {
                stop()
                appContext?.let { clearSession(it) }
                _state.value = UiState(message = "This Jam has ended")
                return
            }
            _state.update { it.copy(status = Status.RECONNECTING) }
            val wait = minOf(30_000L, 1000L shl minOf(attempt++, 5))
            withTimeoutOrNull(wait) { nudge.receive() }
        }
    }

    private fun handle(text: String) {
        val obj = try { json.parseToJsonElement(text).jsonObject } catch (e: Exception) { return }
        when (obj["t"]?.jsonPrimitive?.contentOrNull) {
            "welcome" -> {
                val s = obj["state"]!!.jsonObject
                attempt = 0
                if (resumed) { resumed = false; toast("Back in Jam ${_state.value.code}") }
                startClockSync()
                _state.update {
                    it.copy(
                        status = Status.CONNECTED,
                        you = obj["you"]?.jsonPrimitive?.contentOrNull,
                        hostId = obj["hostId"]?.jsonPrimitive?.contentOrNull,
                        rev = s["rev"]!!.jsonPrimitive.int,
                        queue = json.decodeFromJsonElement(s["queue"]!!),
                        playback = obj["playback"]?.takeIf { p -> p !is JsonNull }?.let { p -> json.decodeFromJsonElement<JamPlayback>(p) },
                        members = obj["members"]?.let { m -> json.decodeFromJsonElement<List<JamMember>>(m) } ?: emptyList(),
                        message = null
                    )
                }
                playbackChanged.trySend(Unit)
                sendSeedIfHost()
            }
            "members" -> obj["members"]?.let { m ->
                _state.update { it.copy(members = json.decodeFromJsonElement(m), hostId = obj["hostId"]?.jsonPrimitive?.contentOrNull) }
            }
            "snapshot" -> { // the host's starting queue arrived (or a full resync)
                val s = obj["state"]?.jsonObject ?: return
                _state.update {
                    it.copy(
                        rev = s["rev"]!!.jsonPrimitive.int,
                        queue = json.decodeFromJsonElement(s["queue"]!!),
                        playback = obj["playback"]?.takeIf { p -> p !is JsonNull }?.let { p -> json.decodeFromJsonElement<JamPlayback>(p) }
                    )
                }
                playbackChanged.trySend(Unit)
            }
            in OP_TYPES -> onOp(obj)
            "mix" -> mixRequested.trySend(Unit)
            "pb" -> _state.update { st ->
                st.copy(playback = obj["playback"]?.takeIf { p -> p !is JsonNull }?.let { p -> json.decodeFromJsonElement<JamPlayback>(p) })
            }.also { playbackChanged.trySend(Unit) }
            "pong" -> onPong(obj)
            "error" -> _state.update {
                it.copy(message = when (obj["code"]?.jsonPrimitive?.contentOrNull) {
                    "queue_full" -> "The Jam queue is full"
                    "rate_limited" -> "Slow down a little"
                    else -> "That didn't work"
                })
            }
        }
    }

    private fun onOp(obj: JsonObject) {
        val rev = obj["rev"]?.jsonPrimitive?.int ?: return
        val current = _state.value.rev
        if (rev <= current) return // already in the snapshot we have
        if (rev > current + 1) { // missed an edit: reconnect, the welcome carries the full queue
            scope.launch { try { socket?.close(CloseReason(CloseReason.Codes.NORMAL, "resync")) } catch (_: Exception) {} }
            return
        }
        val by = obj["by"]?.jsonPrimitive?.contentOrNull ?: ""
        val op = parseOp(obj) ?: return
        _state.update { it.copy(rev = rev, queue = it.queue.applied(op, by)) }
    }

    /** Every kind of queue change the room can send; the dispatcher and [parseOp] share this so none is ignored. */
    internal val OP_TYPES = setOf("add", "addMany", "remove", "removeMany", "move")

    /** A queue-changing message from the room as an op, or null if it is malformed. */
    internal fun parseOp(obj: JsonObject): JamOp? {
        val ref = { key: String -> (obj[key] as? JsonElement)?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull }
        val id = obj["id"]?.jsonPrimitive?.contentOrNull
        return try {
            when (obj["t"]?.jsonPrimitive?.contentOrNull) {
                "add" -> JamOp.Add(id ?: return null, json.decodeFromJsonElement(obj["track"] ?: return null), ref("afterId"))
                "addMany" -> JamOp.AddMany(
                    json.decodeFromJsonElement<List<WireAdd>>(obj["items"] ?: return null).map { it.id to it.track },
                    ref("afterId"),
                    ref("label")
                )
                "remove" -> JamOp.Remove(id ?: return null)
                "removeMany" -> JamOp.RemoveMany(json.decodeFromJsonElement<List<String>>(obj["ids"] ?: return null))
                "move" -> JamOp.Move(id ?: return null, ref("beforeId"))
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun stop() {
        clockJob?.cancel()
        loop?.cancel()
        loop = null
        val s = socket
        socket = null
        attempt = 0
        if (s != null) scope.launch { try { s.close() } catch (_: Exception) {} }
        netCallback?.let { cb ->
            try { appContext?.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) } catch (_: Exception) {}
        }
        netCallback = null
    }

    private var appContext: Context? = null

    /** Retry at once when the network returns or the app comes to the front, instead of waiting out the backoff. */
    private fun watchConnectivity(app: Context) {
        appContext = app
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { nudge.trySend(Unit) }
        }
        try {
            app.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(cb)
            netCallback = cb
        } catch (e: Exception) {
            Log.w(TAG, "No network callback: ${e.message}")
        }
        if (!lifecycleHooked) {
            lifecycleHooked = true
            Handler(Looper.getMainLooper()).post {
                ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) { nudge.trySend(Unit) }
                })
            }
        }
    }

    /** The code in `https://<host>/j/<code>` or `juke://j/<code>`, else null. */
    fun parseCode(text: String): String? = LINK.find(text)?.groupValues?.get(1)?.uppercase()

    private val LINK = Regex("""(?:${Regex.escape(BuildConfig.SHARE_BASE_URL.substringAfter("://").trimEnd('/'))}|juke:/)/j/([A-HJKMNP-Za-hjkmnp-z2-9]{6})\b""")
}

fun SpotifyTrack.toJamTrack(): JamTrack? {
    val id = id ?: return null
    return JamTrack(id, name, artists.joinToString(", ") { it.name }, durationMs / 1000, album.images.firstOrNull()?.url)
}

/** Only songs with a Spotify id can be found again on another phone. */
fun Track.toJamTrack(): JamTrack? =
    spotifyId?.let { JamTrack(it, title, artist, durationSec, thumbnailUri?.takeIf { u -> u.startsWith("http") }) }

fun com.example.juke.models.SpotdownSong.toJamTrack(): JamTrack? =
    spotifyId?.let { JamTrack(it, title, artist, com.example.juke.network.SpotifyApi.parseDuration(duration), thumbnail.takeIf { u -> u.startsWith("http") }) }

/** A Jam entry as the queue sheet draws it. The uuid is the Jam item id, so taps and removals map straight back. */
fun JamItem.toTrack(): com.example.juke.models.Track =
    com.example.juke.models.Track(uuid = id, title = title, artist = artist, thumbnailUri = thumb, durationSec = durationSec, spotifyId = spotifyId, isStream = true)
