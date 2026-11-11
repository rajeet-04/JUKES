package com.example.juke.account

import android.content.Context
import android.util.Log
import com.example.juke.database.MusicDatabase
import com.example.juke.database.PlaylistEntity
import com.example.juke.database.PlaylistTrackEntity
import com.example.juke.database.TrackEntity
import com.example.juke.network.ApiClient
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import androidx.core.content.edit

/**
 * Keeps liked songs, playlists and settings the same on every device of an account.
 *
 * The account holds one document ([Doc]). Each sync is a three-way merge per item against the document this
 * device last agreed on (the "base", kept in a file): what changed here wins, else what changed elsewhere wins,
 * and something removed on one side (and untouched on the other) is removed. On the first sync after logging
 * in there is no base, so the account's copy wins and anything only on this device is added to it.
 *
 * Songs are keyed by Spotify id, else YouTube id, else uuid, so the same song saved separately on two devices
 * is one item. Both ids travel with each song. Audio files never sync: a song new to this device is added as a
 * stream (played online, downloadable here). Lyrics and play counts stay local.
 */
object LibrarySync {
    @Serializable
    data class Song(
        val u: String, val t: String, val a: String, val th: String? = null, val d: Int = 0,
        val yt: String? = null, val sp: String? = null, val al: String? = null, val ar: List<String>? = null, val fav: Boolean = false
    )

    @Serializable
    data class Playlist(val name: String, val desc: String? = null, val sp: String? = null, val created: Long = 0, val songs: List<String> = emptyList())

    @Serializable
    data class Doc(
        val v: Int = 1,
        val songs: Map<String, Song> = emptyMap(),
        val playlists: Map<String, Playlist> = emptyMap(),
        /** "file/key" -> value as text (a set is its items, sorted, joined by \u001f). Merged by value only. */
        val prefs: Map<String, String> = emptyMap(),
        /** "file/key" -> b, i, l, f or set, from Android (desktop keeps everything as text). Not merged: collected. */
        val types: Map<String, String> = emptyMap()
    )

    @Serializable private data class Remote(val rev: Int = 0, val doc: Doc? = null)

    /** Settings that mean the same on every device. Playback position, caches, update and device ids stay put. */
    private val PREF_FILES = listOf("music_settings_prefs", "audio_effects_prefs", "ui_prefs", "power_prefs", "artist_blacklist_prefs", "search_history")
    private val LOCAL_ONLY = Regex("(?i)path|uri|folder|dir|cache|last_|token|cookie|session|install|id$")

    private const val TAG = "LibrarySync"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val lock = Mutex()
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    fun key(t: TrackEntity) = t.spotifyId ?: t.ytVideoId ?: t.uuid

    /** Per-key three-way merge. Null [base] = first sync: [remote] wins conflicts. */
    fun <V> merge(base: Map<String, V>?, local: Map<String, V>, remote: Map<String, V>, both: (V?, V, V) -> V = { _, l, _ -> l }): Map<String, V> {
        val out = LinkedHashMap<String, V>()
        for (k in local.keys + remote.keys) {
            val l = local[k]; val r = remote[k]; val b = base?.get(k)
            val v = when {
                base == null -> r ?: l
                l == r -> l
                l == b -> r          // unchanged here: take theirs (including their removal)
                r == b -> l          // unchanged there: keep ours (including our removal)
                l != null && r != null -> both(b, l, r) // both edited it: [both] decides (default: ours)
                else -> l ?: r       // one edited, one removed: the edit wins
            }
            if (v != null) out[k] = v
        }
        return out
    }

    /** Both devices edited this playlist: details are three-way merged; tracks are too, so additions from both sides survive. */
    fun mergePlaylist(base: Playlist?, l: Playlist, r: Playlist): Playlist {
        fun <T> pick(b: T?, x: T, y: T) = if (x == b) y else x // ours unless only they changed it
        val bs = base?.songs.orEmpty().toSet()
        val songs = when {
            l.songs == r.songs || r.songs == base?.songs -> l.songs
            l.songs == base?.songs -> r.songs
            else -> { // our order; drop what they removed, add what they added; what we removed stays removed
                val rs = r.songs.toSet()
                (l.songs.filter { it !in bs || it in rs } + r.songs.filter { it !in bs }).distinct()
            }
        }
        return Playlist(pick(base?.name, l.name, r.name), pick(base?.desc, l.desc, r.desc), pick(base?.sp, l.sp, r.sp), pick(base?.created, l.created, r.created), songs)
    }

    fun merge(base: Doc?, local: Doc, remote: Doc) = Doc(
        songs = merge(base?.songs, local.songs, remote.songs), playlists = merge(base?.playlists, local.playlists, remote.playlists, ::mergePlaylist),
        prefs = merge(base?.prefs, local.prefs, remote.prefs), types = remote.types + local.types
    )

    /**
     * [this] with each song's details taken from [ref] where both have it; only the like (and an id [ref] lacks) is this
     * device's own. The same song has its own uuid, title and art on each device and [apply] never overwrites those,
     * so comparing them made two devices re-upload each other's copy forever.
     */
    fun Doc.informedBy(ref: Doc?): Doc = if (ref == null) this else copy(songs = songs.mapValues { (k, s) ->
        ref.songs[k]?.let { r -> r.copy(fav = s.fav, yt = r.yt ?: s.yt, sp = r.sp ?: s.sp) } ?: s
    })

    /**
     * A message when [merged] would drop most of this device's synced library, else null. Real removals come a few at a time;
     * losing half at once means a bug or a wiped device somewhere, and applying it here would spread it to every device.
     */
    fun lost(local: Doc, merged: Doc): String? {
        val songs = local.songs.keys.count { it !in merged.songs }
        val likes = local.songs.count { (k, s) -> s.fav && merged.songs[k]?.fav != true }
        val tracks = local.playlists.entries.sumOf { (id, p) -> (p.songs.toSet() - merged.playlists[id]?.songs.orEmpty().toSet()).size }
        val total = local.songs.size
        return if (total >= 20 && (songs * 2 > total || likes * 2 > maxOf(local.songs.count { it.value.fav }, 10) || tracks * 2 > maxOf(local.playlists.values.sumOf { it.songs.size }, 20)))
            "Sync paused: it would remove most of your library here ($songs songs, $likes likes). Nothing was changed."
        else null
    }

    private fun baseFile(c: Context) = File(c.filesDir, "library_sync_base.json")
    fun forgetBase(c: Context) { baseFile(c).delete() }

    /** Pull, merge into this device, push. Safe to call often: it writes only when something changed. */
    suspend fun sync(c: Context): Boolean = lock.withLock {
        com.example.juke.report.Breadcrumbs.add("sync")
        if (Account.token == null) return false
        withContext(Dispatchers.IO) {
            try {
                repeat(3) { // 409: another device saved in between; merge with theirs and try again
                    val raw = Account.getLibrary(c) ?: run { _status.value = "Couldn't sync. Check your connection."; return@withContext false }
                    val remote = json.decodeFromString<Remote>(raw)
                    val base = runCatching { json.decodeFromString<Doc>(baseFile(c).readText()) }.getOrNull()
                    val local = snapshot(c).informedBy(base)
                    val merged = merge(base, local, remote.doc ?: Doc())
                    lost(local, merged)?.let { _status.value = it; Log.w(TAG, it); return@withContext false }
                    apply(c, local, merged)
                    val now = snapshot(c).informedBy(merged).let { it.copy(types = merged.types + it.types) } // what this device holds now: what we agree on
                    if (remote.doc != now || remote.rev == 0) {
                        val text = json.encodeToString(now)
                        val res = ApiClient.httpClient.put("${Account.BASE}/account/library?rev=${remote.rev}") {
                            with(Account) { auth() }; contentType(ContentType.Application.Json); setBody(text)
                        }
                        if (res.status.value == 409) return@repeat
                        if (res.status.value == 413) { _status.value = "Your library is too large to sync"; return@withContext false }
                        if (res.status.value != 200) { _status.value = "Couldn't sync (${res.status.value})"; return@withContext false }
                        com.example.juke.jam.Connect.librarySaved()
                    }
                    baseFile(c).writeText(json.encodeToString(now))
                    _status.value = "Synced: ${now.songs.count { it.value.fav }} liked songs, ${now.playlists.size} playlists"
                    return@withContext true
                }
                _status.value = "Couldn't sync, try again"
                false
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w(TAG, "sync failed", e); _status.value = "Couldn't sync. Check your connection."; false
            }
        }
    }

    // ---- this device's side ----

    private fun TrackEntity.toSong() = Song(uuid, title, artist, thumbnailUri?.takeIf { it.startsWith("http") }, durationSec, ytVideoId, spotifyId, albumSpotifyId, artistSpotifyIds, isFavourite)

    /** Synced songs: liked ones and those in a playlist. */
    private suspend fun snapshot(c: Context): Doc {
        val db = MusicDatabase.getDatabase(c)
        val playlists = LinkedHashMap<String, Playlist>()
        val songs = LinkedHashMap<String, Song>()
        db.trackDao().getFavourites().forEach { songs[key(it)] = it.toSong() }
        for (p in db.playlistDao().getAllPlaylistsOnce()) {
            val tracks = db.playlistDao().getPlaylistTracks(p.id)
            tracks.forEach { songs.getOrPut(key(it)) { it.toSong() } }
            playlists[p.id] = Playlist(p.name, p.description, p.spotifyId, p.createdAt, tracks.map(::key))
        }
        val types = HashMap<String, String>()
        return Doc(songs = songs, playlists = playlists, prefs = prefs(c, types), types = types)
    }

    private fun typeOf(v: Any?) = when (v) { is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; is Set<*> -> "set"; else -> null }

    private fun prefs(c: Context, types: MutableMap<String, String>): Map<String, String> {
        val out = HashMap<String, String>()
        for (file in PREF_FILES) for ((k, v) in c.getSharedPreferences(file, Context.MODE_PRIVATE).all) {
            if (LOCAL_ONLY.containsMatchIn(k) || v == null) continue
            val fk = "$file/$k"
            typeOf(v)?.let { types[fk] = it }
            out[fk] = if (v is Set<*>) v.map { it.toString() }.sorted().joinToString("\u001f") else v.toString()
        }
        return out
    }

    /** Make this device match [merged], given it currently holds [local]. */
    private suspend fun apply(c: Context, local: Doc, merged: Doc) {
        val db = MusicDatabase.getDatabase(c)
        val trackDao = db.trackDao(); val playlistDao = db.playlistDao()

        // songs: find each by key among all rows (a liked song may already be here, saved but not liked)
        val all = (trackDao.getAllTracks() + trackDao.getStreamTracks())
        val byKey = HashMap<String, TrackEntity>().apply { all.forEach { put(key(it), it); it.ytVideoId?.let { yt -> putIfAbsent(yt, it) } } }
        val uuidOf = HashMap<String, String>()
        for ((k, s) in merged.songs) {
            val have = byKey[k] ?: s.yt?.let { byKey[it] } ?: trackDao.getTrackByUuid(s.u)
            if (have == null) {
                // new here: a stream entry pointing at its future file, which the player fetches by Spotify id (see Connect.pendingStream)
                trackDao.insertTrack(TrackEntity(uuid = s.u, title = s.t, artist = s.a, thumbnailUri = s.th, durationSec = s.d, ytVideoId = s.yt,
                    spotifyId = s.sp, albumSpotifyId = s.al, artistSpotifyIds = s.ar, isFavourite = s.fav, isStream = true,
                    localUri = s.sp?.let { File(c.filesDir, "stream_files/${s.u}_stream.mp3").absolutePath }))
                uuidOf[k] = s.u
            } else {
                uuidOf[k] = have.uuid
                val updated = have.copy(
                    isFavourite = s.fav, ytVideoId = have.ytVideoId ?: s.yt, spotifyId = have.spotifyId ?: s.sp,
                    albumSpotifyId = have.albumSpotifyId ?: s.al, artistSpotifyIds = have.artistSpotifyIds ?: s.ar, thumbnailUri = have.thumbnailUri ?: s.th
                )
                if (updated != have) trackDao.updateSyncedFields(have.uuid, updated.isFavourite, updated.ytVideoId, updated.spotifyId, updated.albumSpotifyId, updated.artistSpotifyIds, updated.thumbnailUri)
            }
        }
        // a liked song that left the synced set was unliked elsewhere (files and the row stay)
        for ((k, s) in local.songs) if (s.fav && merged.songs[k]?.fav != true) byKey[k]?.let { trackDao.updateTrackFavourite(it.uuid, false) }

        for ((id, p) in merged.playlists) {
            val old = local.playlists[id]
            if (old == p) continue
            val existing = playlistDao.getPlaylist(id)
            playlistDao.insertPlaylist(PlaylistEntity(id, p.name, p.desc, existing?.thumbnailUri, p.sp, p.created.takeIf { it > 0 } ?: System.currentTimeMillis(), p.songs.size))
            if (old?.songs != p.songs) {
                val now = System.currentTimeMillis()
                playlistDao.deletePlaylistTracks(id)
                playlistDao.insertPlaylistTracks(p.songs.mapNotNull { uuidOf[it] }.mapIndexed { i, u -> PlaylistTrackEntity(id, u, i, now) })
                playlistDao.updatePlaylistTrackCount(id, p.songs.size)
            }
        }
        for (id in local.playlists.keys - merged.playlists.keys) playlistDao.deletePlaylist(id)

        // settings, written in the type this device already uses for the key, else the type Android reported.
        // A key this device has never stored and nobody typed is skipped: writing text where code reads a number would crash.
        val changed = merged.prefs.filter { local.prefs[it.key] != it.value } + (local.prefs.keys - merged.prefs.keys).associateWith { null }
        for ((file, entries) in changed.entries.groupBy { it.key.substringBefore('/') }) {
            val sp = c.getSharedPreferences(file, Context.MODE_PRIVATE)
            val have = sp.all
            sp.edit {
                for ((fk, text) in entries) {
                    val k = fk.substringAfter('/')
                    if (text == null) {
                        remove(k); continue
                    }
                    val type = typeOf(have[k]) ?: merged.types[fk]
                    ?: if (have.containsKey(k) || Account.isDesktop) "s" else continue
                    runCatching {
                        when (type) {
                            "b" -> putBoolean(k, text.toBooleanStrict()); "i" -> putInt(
                            k,
                            text.toInt()
                        ); "l" -> putLong(k, text.toLong())
                            "f" -> putFloat(k, text.toFloat()); "set" -> putStringSet(
                            k,
                            text.split('\u001f').filter { it.isNotEmpty() }.toSet()
                        )

                            else -> putString(k, text)
                        }
                    }
                }
            }
        }
    }
}
