package com.example.juke.jam

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

    enum class Status { IDLE, CONNECTING, CONNECTED, RECONNECTING }

    data class UiState(
        val status: Status = Status.IDLE,
        val code: String? = null,
        val you: String? = null,
        val members: List<JamMember> = emptyList(),
        val queue: List<JamItem> = emptyList(),
        val rev: Int = 0,
        /** Something to tell the user: an error, or why the Jam closed. */
        val message: String? = null
    ) {
        val active get() = status != Status.IDLE
    }

    @Serializable
    data class Info(val exists: Boolean = false, val members: Int = 0, val queue: Int = 0)

    @Serializable
    private data class Created(val code: String)

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
    @Volatile private var attempt = 0
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
        if (res.status.isSuccess()) res.body<Created>().code else null
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

    fun join(context: Context, code: String, name: String) {
        val app = context.applicationContext
        stop()
        saveName(app, name)
        val id = clientId(app)
        _state.value = UiState(status = Status.CONNECTING, code = code.uppercase())
        watchConnectivity(app)
        loop = scope.launch { run(code.uppercase(), id, name.trim().take(24).ifBlank { "Guest" }) }
    }

    fun leave() {
        stop()
        _state.value = UiState()
    }

    fun add(track: JamTrack, afterId: String? = null): Boolean = send(buildJsonObject {
        put("t", "add")
        put("id", UUID.randomUUID().toString().replace("-", ""))
        put("track", json.encodeToJsonElement(JamTrack.serializer(), track))
        put("afterId", afterId)
    })

    fun remove(id: String): Boolean = send(buildJsonObject { put("t", "remove"); put("id", id) })

    fun move(id: String, beforeId: String?): Boolean =
        send(buildJsonObject { put("t", "move"); put("id", id); put("beforeId", beforeId) })

    private fun send(msg: JsonObject): Boolean {
        val s = socket
        if (s == null || _state.value.status != Status.CONNECTED) {
            _state.update { it.copy(message = "Not connected to the Jam") }
            return false
        }
        scope.launch {
            try { s.send(Frame.Text(msg.toString())) } catch (e: Exception) { Log.w(TAG, "Send failed: ${e.message}") }
        }
        return true
    }

    private suspend fun run(code: String, clientId: String, name: String) {
        while (currentCoroutineContext().isActive) {
            try {
                client.webSocket("$WS_BASE/jam/$code/ws") {
                    socket = this
                    send(Frame.Text(buildJsonObject { put("t", "hello"); put("clientId", clientId); put("name", name) }.toString()))
                    for (frame in incoming) if (frame is Frame.Text) handle(frame.readText())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Socket ended: ${e.message}")
            }
            socket = null
            if (info(code)?.exists == false) {
                stop()
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
                _state.update {
                    it.copy(
                        status = Status.CONNECTED,
                        you = obj["you"]?.jsonPrimitive?.contentOrNull,
                        rev = s["rev"]!!.jsonPrimitive.int,
                        queue = json.decodeFromJsonElement(s["queue"]!!),
                        members = obj["members"]?.let { m -> json.decodeFromJsonElement<List<JamMember>>(m) } ?: emptyList(),
                        message = null
                    )
                }
            }
            "members" -> obj["members"]?.let { m -> _state.update { it.copy(members = json.decodeFromJsonElement(m)) } }
            "add", "remove", "move" -> onOp(obj)
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
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return
        val by = obj["by"]?.jsonPrimitive?.contentOrNull ?: ""
        val ref = { key: String -> (obj[key] as? JsonElement)?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull }
        val op = when (obj["t"]?.jsonPrimitive?.contentOrNull) {
            "add" -> JamOp.Add(id, json.decodeFromJsonElement(obj["track"] ?: return), ref("afterId"))
            "remove" -> JamOp.Remove(id)
            "move" -> JamOp.Move(id, ref("beforeId"))
            else -> return
        }
        _state.update { it.copy(rev = rev, queue = it.queue.applied(op, by)) }
    }

    private fun stop() {
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
