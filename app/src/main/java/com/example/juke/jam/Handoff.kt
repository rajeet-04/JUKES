package com.example.juke.jam

import android.content.Context
import com.example.juke.BuildConfig
import com.example.juke.network.ApiClient
import com.example.juke.repositories.SharedTrack
import com.example.juke.repositories.toSpotifyTrack
import com.example.juke.viewmodels.MusicViewModel
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Hand playback to your other device, Spotify Connect style. Two devices pair once with a short code; after
 * that either can "play on the other device" (send the queue + position) or "bring playback here" (ask the
 * other one to send). The messages go through a one-slot mailbox on the Worker (see spotdown-kv-worker/src/hand.ts).
 * The sender keeps playing until the receiver is up and acks, so there is no silent gap.
 */
object Handoff {
    /** What sits in the mailbox: an offer (id + queue + position), its [ack], or a [pull] request. */
    @Serializable
    data class Msg(
        val id: String = "", val from: String = "", val fromName: String = "",
        val tracks: List<JamTrack> = emptyList(), val posMs: Long = 0, val playing: Boolean = true,
        val ack: String? = null, val pull: String? = null
    )

    @Serializable
    private data class Resp(val now: Long = 0, val at: Long = 0, val box: Msg? = null)

    private const val MAX_SONGS = 30
    private const val OFFER_FRESH_MS = 120_000L // an older offer is leftovers, not a request
    private const val ACK_WAIT_MS = 45_000L
    private const val LOAD_WAIT_MS = 60_000L
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val BASE = BuildConfig.SHARE_BASE_URL.trimEnd('/')

    private val _code = MutableStateFlow<String?>(null)
    val code: StateFlow<String?> = _code
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    @Volatile private var wake = false // an action just happened: poll again at once

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("handoff", Context.MODE_PRIVATE)
    private fun me(c: Context) = prefs(c).getString("id", null) ?: UUID.randomUUID().toString().replace("-", "").also { prefs(c).edit().putString("id", it).apply() }

    fun load(c: Context) { _code.value = prefs(c).getString("code", null) }
    private fun save(c: Context, code: String?) { prefs(c).edit().putString("code", code).apply(); _code.value = code }

    fun pairNew(c: Context) = save(c, (1..6).map { ALPHABET.random() }.joinToString(""))
    /** False if [input] isn't a pairing code. */
    fun pairWith(c: Context, input: String): Boolean {
        val code = input.trim().uppercase()
        if (code.length != 6 || code.any { it !in ALPHABET }) return false
        save(c, code); return true
    }
    fun unpair(c: Context) { save(c, null); _status.value = "" }

    private suspend fun read(code: String): Resp? = try {
        val res = ApiClient.httpClient.get("$BASE/hand/$code") { header("X-App-Secret", BuildConfig.SPOTIFY_TOKEN_KEY) }
        if (res.status.isSuccess()) json.decodeFromString<Resp>(res.body<String>()) else null
    } catch (e: CancellationException) { throw e } catch (e: Exception) { null }

    private suspend fun write(code: String, msg: Msg): Boolean = try {
        ApiClient.httpClient.put("$BASE/hand/$code") {
            header("X-App-Secret", BuildConfig.SPOTIFY_TOKEN_KEY)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(msg))
        }.status.isSuccess()
    } catch (e: CancellationException) { throw e } catch (e: Exception) { false }

    // ponytail: exactly two devices per code; a third paired device would also answer. Add a target id if that matters.
    private var pendingId: String? = null   // our offer, waiting for the ack
    private var pendingAt = 0L
    private var pullUntil = 0L              // we asked to pull; give up after this
    private var lastHandled = ""            // offers already received
    private var lastPull = ""               // pull requests already answered

    /** "Play on the other device": send what's playing now. */
    suspend fun send(vm: MusicViewModel) {
        val c = vm.getApplication<android.app.Application>(); val code = _code.value ?: return
        val st = vm.uiState.value
        val cur = st.currentTrack ?: run { _status.value = "Nothing is playing"; return }
        val queue = if (st.queue.isEmpty()) listOf(cur) else st.queue.drop(maxOf(st.queueIndex, 0))
        val tracks = JamResolver.resolveAll(queue.take(MAX_SONGS).map { it.toJamPick() })
        if (tracks.firstOrNull() == null) { _status.value = "Couldn't match \"${cur.title}\" to send it"; return }
        val pm = vm.playbackManager
        val msg = Msg(UUID.randomUUID().toString(), me(c), JamSession.savedName(c), tracks.filterNotNull(), pm.getCurrentPosition(), pm.isPlayingFlow.value)
        if (!write(code, msg)) { _status.value = "Couldn't reach the other device. Check your connection."; return }
        pendingId = msg.id; pendingAt = System.currentTimeMillis(); wake = true
        _status.value = "Sending…"
    }

    /** "Bring playback here": ask the other device to send. */
    suspend fun pull(c: Context) {
        val code = _code.value ?: return
        if (!write(code, Msg(pull = me(c)))) { _status.value = "Couldn't reach the other device. Check your connection."; return }
        pullUntil = System.currentTimeMillis() + 20_000; wake = true
        _status.value = "Asking your other device…"
    }

    /** Polls the mailbox while the app runs; started once from the app root. */
    suspend fun run(vm: MusicViewModel) {
        val c = vm.getApplication<android.app.Application>()
        load(c)
        val pm = vm.playbackManager
        while (true) {
            var waited = 0
            while (!wake && waited < (if (pendingId != null) 1000 else 4000)) { delay(100); waited += 100 }
            wake = false
            val code = _code.value ?: continue
            val r = read(code) ?: continue
            val box = r.box ?: continue
            val now = System.currentTimeMillis()
            val fresh = r.now - r.at < OFFER_FRESH_MS

            pendingId?.let { id ->
                if (box.ack == id) {
                    pendingId = null
                    if (pm.isPlayingFlow.value) pm.togglePlayPause()
                    _status.value = "Now playing on your other device"
                } else if (now - pendingAt > ACK_WAIT_MS) {
                    pendingId = null; _status.value = "The other device didn't respond. Is the app open there?"
                }
            }
            if (pullUntil in 1..now && pendingId == null && box.pull == me(c)) { pullUntil = 0; _status.value = "Nothing is playing on your other device" }

            if (box.pull != null && box.pull != me(c) && box.pull != lastPull && fresh) {
                lastPull = box.pull
                if (vm.uiState.value.currentTrack != null) send(vm)
            } else if (box.id.isNotEmpty() && box.from != me(c) && box.ack != box.id && box.id != lastHandled && fresh) {
                lastHandled = box.id; pullUntil = 0
                receive(vm, code, box, r.now - r.at)
            }
        }
    }

    private suspend fun receive(vm: MusicViewModel, code: String, box: Msg, ageMs: Long) {
        val first = box.tracks.firstOrNull() ?: return
        val songs = box.tracks.mapNotNull { SharedTrack(it.title, it.artist, it.durationSec, it.spotifyId, null, it.thumb).toSpotifyTrack() }
        if (songs.isEmpty()) return
        _status.value = "Receiving from ${box.fromName}…"
        val started = System.currentTimeMillis()
        vm.setQueueFromSpotifyTracks(songs, 0)
        val pm = vm.playbackManager
        // wait for the first song to be loaded and playing, then land on the sender's position
        while (System.currentTimeMillis() - started < LOAD_WAIT_MS) {
            val cur = vm.uiState.value.currentTrack
            if (cur != null && JamFollower.sameSong(cur, JamItem("", first.spotifyId, first.title, first.artist)) && pm.getDuration() > 0 && pm.isPlayingFlow.value) break
            delay(250)
        }
        if (!JamFollower.sameSong(vm.uiState.value.currentTrack, JamItem("", first.spotifyId, first.title, first.artist))) {
            _status.value = "Couldn't load \"${first.title}\" here"; return
        }
        val elapsed = if (box.playing) ageMs + (System.currentTimeMillis() - started) else 0L
        pm.seekTo(box.posMs + elapsed)
        if (!box.playing && pm.isPlayingFlow.value) pm.togglePlayPause()
        write(code, box.copy(ack = box.id)) // the sender can stop now
        _status.value = "Playing here, from ${box.fromName}"
    }
}
