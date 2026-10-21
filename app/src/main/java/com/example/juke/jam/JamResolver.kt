package com.example.juke.jam

import com.example.juke.models.SpotdownSong
import com.example.juke.models.SpotifyTrack
import com.example.juke.models.Track
import com.example.juke.network.SpotifyApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** A song as this phone has it. [spotifyId] can be missing (older downloads, tracks that came from elsewhere). */
data class JamPick(val title: String, val artist: String, val durationSec: Int, val thumb: String?, val spotifyId: String?)

fun Track.toJamPick() = JamPick(title, artist, durationSec, thumbnailUri?.takeIf { it.startsWith("http") }, spotifyId)

fun SpotifyTrack.toJamPick() = JamPick(name, artists.joinToString(", ") { it.name }, durationMs / 1000, album.images.firstOrNull()?.url, id)

fun SpotdownSong.toJamPick() =
    JamPick(title, artist, SpotifyApi.parseDuration(duration), thumbnail.takeIf { it.startsWith("http") }, spotifyId)

/**
 * Every phone finds a Jam song by its Spotify id, so a song that only one phone has (no id) is matched on
 * Spotify first. A match must agree on title, artist and length; otherwise the song is skipped rather than
 * guessed, since a wrong song in front of everyone is worse than a missing one.
 */
object JamResolver {
    private const val DURATION_TOLERANCE_SEC = 6
    private const val PARALLEL_SEARCHES = 4

    data class Candidate(val id: String, val title: String, val artists: List<String>, val durationSec: Int, val thumb: String?)

    private val cache = ConcurrentHashMap<String, JamTrack>()
    private val NOT_ALNUM = Regex("[^\\p{L}\\p{N}]+")
    private val BRACKETS = Regex("\\([^)]*\\)|\\[[^]]*]")
    private val FEATURING = Regex("\\b(feat|ft|featuring)\\b.*")
    private val ARTIST_SPLIT = Regex("\\s*(,|&|;|/|\\bx\\b|\\band\\b|\\bfeat\\b\\.?|\\bft\\b\\.?)\\s*", RegexOption.IGNORE_CASE)

    /** Lower-case words only: no brackets, no "- Remastered" tail, no "feat." part, no punctuation. */
    fun normalize(text: String): String =
        text.lowercase().replace(BRACKETS, " ").substringBefore(" - ").replace(FEATURING, " ")
            .replace(NOT_ALNUM, " ").trim()

    private fun artistNames(text: String): Set<String> =
        text.split(ARTIST_SPLIT).map { normalize(it) }.filter { it.isNotEmpty() }.toSet()

    fun bestMatch(candidates: List<Candidate>, pick: JamPick): Candidate? {
        val title = normalize(pick.title)
        val artists = artistNames(pick.artist)
        if (title.isEmpty() || artists.isEmpty()) return null
        return candidates.filter { c ->
            val t = normalize(c.title)
            val titleOk = t == title || (minOf(t.length, title.length) >= 4 && (t.startsWith("$title ") || title.startsWith("$t ")))
            val artistOk = c.artists.any { normalize(it) in artists }
            val lengthOk = pick.durationSec <= 0 || c.durationSec <= 0 || abs(c.durationSec - pick.durationSec) <= DURATION_TOLERANCE_SEC
            titleOk && artistOk && lengthOk
        }.minByOrNull { if (pick.durationSec > 0) abs(it.durationSec - pick.durationSec) else 0 }
    }

    /** The song in the form the room needs, or null if no confident match exists (or Spotify can't be reached). */
    suspend fun resolve(pick: JamPick): JamTrack? {
        pick.spotifyId?.let { return JamTrack(it, pick.title, pick.artist, pick.durationSec, pick.thumb) }
        val key = normalize(pick.title) + "|" + normalize(pick.artist) + "|" + pick.durationSec / 3
        cache[key]?.let { return it }
        val found = try {
            SpotifyApi.search("${pick.title} ${pick.artist}", listOf("track")).tracks?.items.orEmpty().mapNotNull { t ->
                t.id?.let { Candidate(it, t.name, t.artists.map { a -> a.name }, t.durationMs / 1000, t.album.images.firstOrNull()?.url) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        val best = bestMatch(found, pick) ?: return null
        return JamTrack(best.id, best.title, best.artists.joinToString(", "), best.durationSec, best.thumb).also { cache[key] = it }
    }

    private val artwork = ConcurrentHashMap<String, String>()

    /**
     * Cover art for Spotify ids, 50 per request. A song added from a library has only a file on that phone as
     * its artwork, which means nothing on other phones, so the room gets Spotify's image instead.
     */
    suspend fun artworkFor(ids: List<String>): Map<String, String> {
        val missing = ids.filter { !artwork.containsKey(it) }.distinct()
        missing.chunked(50).forEach { chunk ->
            try {
                SpotifyApi.getTracks(chunk).forEach { t ->
                    val url = t.album.images.firstOrNull()?.url
                    if (t.id != null && url != null) artwork[t.id] = url
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { /* no artwork is better than no song */ }
        }
        return ids.mapNotNull { id -> artwork[id]?.let { id to it } }.toMap()
    }

    /** [picks] resolved in order; entries that couldn't be matched come back as null in their place. */
    suspend fun resolveAll(picks: List<JamPick>): List<JamTrack?> {
        val art = artworkFor(picks.filter { it.spotifyId != null && it.thumb == null }.mapNotNull { it.spotifyId })
        val filled = picks.map { p -> if (p.spotifyId != null && p.thumb == null) p.copy(thumb = art[p.spotifyId]) else p }
        val limit = Semaphore(PARALLEL_SEARCHES)
        return coroutineScope { filled.map { p -> async { limit.withPermit { resolve(p) } } }.awaitAll() }
    }
}
