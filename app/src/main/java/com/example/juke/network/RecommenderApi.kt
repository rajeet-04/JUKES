package com.example.juke.network

import android.util.Log
import com.example.juke.models.SpotifyTrack
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * YouTube Music Recommender API Service.
 * 
 * Provides:
 * 1. Best video match search for songs
 * 2. Full radio queue recommendations from YouTube Music
 * 3. Spotify validation and filtering
 */
object RecommenderApi {

    private const val TAG = "RecommenderApi"
    private const val SEARCH_URL = "https://mp3juice3.ninja/api/yt-data"
    private const val YT_MUSIC_API_URL =
        "https://music.youtube.com/youtubei/v1/next?prettyPrint=true"

    private val gson = Gson()

    private val OFFICIAL_KEYWORDS = listOf(
        "official", "official video", "official music video", "official lyric video",
        "music video", "vevo", "official audio", "audio", "visualizer", "official visualizer",
        "from the album", "album version", "single", "ep", "lp",
        "remastered", "anniversary edition", "deluxe edition",
        "radio edit", "clean", "explicit",
        "prod by", "produced by", "ft.", "feat.", "featuring",
        "original", "original song", "original soundtrack", "ost", "soundtrack",
        "theme", "title track", "lead single", "debut single",
        "official performance", "official live video", "session", "studio version"
    )


    private val SPAM_KEYWORDS = listOf(
        "remix", "cover", "fan made", "fanmade", "ai cover", "ai version", "voice model",
        "karaoke", "instrumental", "no vocals", "vocals removed",
        "8d", "8d audio", "slowed", "reverb", "nightcore", "bass boosted",
        "sped up", "speed up", "pitch shifted", "chipmunk",
        "live", "acoustic", "tutorial", "how to", "reaction", "review",
        "mashup", "mix", "lyrics", "lyric video", "english translation",
        "reaction video", "reactionplus", "mashup reaction",
        "tiktok", "shorts", "edit", "edit audio", "overlay", "transition",
        "loop", "extended", "hour version", "1 hour", "10 hour", "24/7",
        "background music", "study", "sleep", "relaxing", "meditation", "ambience", "ambient",
        "stem", "stems", "multitrack", "isolation",
        "behind the scenes", "bts", "making of", "explained", "breakdown",
        "teaser", "trailer", "preview", "snippet",
        "leak", "leaked", "unreleased", "demo", "rough mix", "work in progress", "wip"
    )


    @Serializable
    private data class SearchRequest(val query: String)

    @Serializable
    private data class SearchItem(
        val id: String,
        val title: String
    )

    @Serializable
    private data class SearchResponse(val items: List<SearchItem> = emptyList())

    /**
     * YouTube Music recommendation result.
     */
    @Serializable
    data class YouTubeRecommendation(
        val id: String,
        val title: String,
        val artist: String,
        val duration: String? = null // Duration in "MM:SS" format
    )

    private fun getOfficialScore(title: String): Double {
        val lowerTitle = title.lowercase()
        var score = 0.0

        OFFICIAL_KEYWORDS.forEach { keyword ->
            val kwLower = keyword.lowercase()
            // Use word boundaries to prevent substring matches (e.g. "ost" in "lost", "ep" in "deep")
            val pattern = if (kwLower.all { it.isLetterOrDigit() }) {
                Regex("\\b${Regex.escape(kwLower)}\\b")
            } else {
                // For keywords with special chars (like "ft.", "prod by"), match standalone
                Regex("(?:^|\\s)${Regex.escape(kwLower)}(?:\\s|$)")
            }
            if (pattern.containsMatchIn(lowerTitle)) {
                score += 1.0
            }
        }

        if (score > 1.0) {
            score += 0.5
        }

        return score
    }

    /**
     * Check if a song title contains spam/unwanted keywords.
     */
    private fun isSpamOrVariant(title: String): String? {
        val lowerTitle = title.lowercase()
        for (keyword in SPAM_KEYWORDS) {
            val kwLower = keyword.lowercase()
            // Use word boundaries to prevent substring matches (e.g. "mix" in "mixed signals")
            val pattern = Regex("\\b${Regex.escape(kwLower)}\\b")
            if (pattern.containsMatchIn(lowerTitle)) {
                return keyword
            }
        }
        return null
    }

    /**
     * Parse artist names from a comma/and-separated string.
     * 
     * Examples:
     * "Artist A, Artist B, Artist C" → ["Artist A", "Artist B", "Artist C"]
     * "Artist A and Artist B" → ["Artist A", "Artist B"]
     * "Artist A, Artist B and Artist C" → ["Artist A", "Artist B", "Artist C"]
     * "Artist A feat. Artist B" → ["Artist A feat. Artist B"] (keeps feat. together)
     */
    private fun parseArtists(artistString: String): List<String> {
        if (artistString.isEmpty()) return emptyList()

        // Replace " and " with a comma for uniform parsing
        val normalized = artistString.replace(" and ", ", ")

        // Split by comma and trim whitespace
        return normalized
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Compare two lists of artists and return a weighted similarity score.
     * 
     * Considers:
     * - How many artists match between the two lists
     * - Similarity of artist names using Levenshtein distance
     * - Gives higher weight to matching artists
     * 
     * @param spotifyArtists Artists from Spotify (original song)
     * @param youtubeArtists Artists from YouTube recommendation
     * @return Similarity score from 0.0 to 1.0
     */
    private fun artistListSimilarity(
        spotifyArtists: List<String>,
        youtubeArtists: List<String>
    ): Double {
        if (spotifyArtists.isEmpty() || youtubeArtists.isEmpty()) {
            return 0.0
        }

        var totalScore = 0.0
        var matchCount = 0

        // Check each Spotify artist against YouTube artists
        for (spotifyArtist in spotifyArtists) {
            var bestMatch = 0.0

            for (youtubeArtist in youtubeArtists) {
                val artistSim = similarity(spotifyArtist, youtubeArtist)
                if (artistSim > bestMatch) {
                    bestMatch = artistSim
                }
            }

            // If we found a good match (>60%), count it
            if (bestMatch > 0.6) {
                matchCount++
                totalScore += bestMatch
            }
        }

        // Calculate final score based on:
        // 1. How many artists matched
        // 2. Average similarity of matched artists
        // 3. Bonus for matching multiple artists

        if (matchCount == 0) {
            return 0.0 // No artist matches
        }

        val averageMatch = totalScore / spotifyArtists.size
        val matchRatio = matchCount.toDouble() / spotifyArtists.size

        // Combine: 70% weight to average match, 30% weight to match ratio
        // This gives preference to recommendations with multiple matching artists
        return (averageMatch * 0.7) + (matchRatio * 0.3)
    }

    /**
     * Calculate similarity between two strings using Levenshtein distance.
     */
    private fun similarity(a: String, b: String): Double {
        val dist = levenshtein(a.lowercase(), b.lowercase())
        return 1.0 - dist.toDouble() / max(a.length, b.length).coerceAtLeast(1)
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                )
            }
        }
        return dp[a.length][b.length]
    }

    /**
     * Clean title by removing content in parentheses or brackets to improve matching.
     * e.g., "Song Name (feat. Artist)" -> "Song Name"
     */
    private fun cleanTitle(title: String): String {
        return title.replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), "").trim()
    }

    /**
     * Extract all artist text from JSON runs array.
     * 
     * The YouTube Music API returns artist names in multiple runs separated by commas/and.
     * This function concatenates all runs to get the complete artist string.
     * 
     * Example JSON structure:
     * "shortBylineText": {
     *   "runs": [
     *     {"text": "Artist A"},
     *     {"text": ", "},
     *     {"text": "Artist B"},
     *     {"text": " and "},
     *     {"text": "Artist C"}
     *   ]
     * }
     * 
     * Result: "Artist A, Artist B and Artist C"
     */
    private fun extractArtistFromRuns(runsArray: com.google.gson.JsonArray?): String {
        if (runsArray == null) return "Unknown"

        return try {
            runsArray
                .map { run ->
                    run.asJsonObject
                        .get("text")
                        ?.asString
                        ?.trim()
                        ?: ""
                }
                .filter { it.isNotEmpty() }
                .joinToString("")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract artist from runs: ${e.message}")
            "Unknown"
        }
    }

    /**
     * Parse duration string to seconds.
     */
    private fun parseDurationToSeconds(durationStr: String?): Int? {
        if (durationStr.isNullOrBlank()) return null

        return try {
            val parts = durationStr.split(":")
            when (parts.size) {
                2 -> { // MM:SS
                    val minutes = parts[0].toIntOrNull() ?: 0
                    val seconds = parts[1].toIntOrNull() ?: 0
                    minutes * 60 + seconds
                }

                3 -> { // HH:MM:SS
                    val hours = parts[0].toIntOrNull() ?: 0
                    val minutes = parts[1].toIntOrNull() ?: 0
                    val seconds = parts[2].toIntOrNull() ?: 0
                    hours * 3600 + minutes * 60 + seconds
                }

                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse duration: $durationStr")
            null
        }
    }

    /**
     * Calculate duration similarity (returns 1.0 for exact match, 0.0 for large difference).
     * More strict than before to ensure high confidence in duration matching.
     */
    private fun durationSimilarity(youtubeDuration: Int?, spotifyDuration: Int?): Double {
        if (youtubeDuration == null || spotifyDuration == null) return 0.3 // Lower neutral score

        val diff = abs(youtubeDuration - spotifyDuration)
        val maxDuration = maxOf(youtubeDuration, spotifyDuration)

        // Calculate similarity as percentage difference
        1.0 - (diff.toDouble() / maxDuration.toDouble())

        // Apply stricter thresholds
        return when {
            diff <= 15 -> 1.0    // Within 15 seconds = perfect match
            diff <= 30 -> 0.9    // Within 30 seconds = excellent match
            diff <= 45 -> 0.8    // Within 45 seconds = very good match
            diff <= 60 -> 0.7    // Within 1 minute = good match
            diff <= 90 -> 0.6    // Within 1.5 minutes = reasonable match
            diff <= 120 -> 0.4   // Within 2 minutes = marginal match
            diff <= 180 -> 0.2   // Within 3 minutes = poor match
            else -> 0.0          // Too different
        }
    }

    /**
     * Get the best video match for a song query.
     * 
     * Searches YouTube and returns the most relevant video ID,
     * prioritizing official releases and using advanced scoring.
     */
    suspend fun getBestVideoMatch(songName: String): String? {
        Log.d(TAG, "getBestVideoMatch called with songName: \"$songName\"")

        return try {
            val response = ApiClient.httpClient.post(SEARCH_URL) {
                contentType(ContentType.Application.Json)
                setBody(SearchRequest(query = songName))
            }

            val searchResponse: SearchResponse = response.body()
            val items = searchResponse.items

            Log.d(TAG, "API Response received with ${items.size} items")

            if (items.isEmpty()) {
                Log.d(TAG, "No results found for: $songName")
                return null
            }

            // Score all items — title similarity is the primary gate.
            // Official keywords are only rewarded when the item already looks like a match.
            var bestMatchItem: SearchItem? = null
            var highestScore = 0.0

            val queryLower = songName.lowercase()
            val queryWords = queryLower.split("\\s+".toRegex()).filter { it.length > 1 }

            items.forEachIndexed { index, item ->
                val titleLower = item.title.lowercase()

                // Step 1: Skip spam/variant content regardless of anything else
                if (isSpamOrVariant(item.title) != null) {
                    Log.d(TAG, "Skipping spam item: ${item.title}")
                    return@forEachIndexed
                }

                var score = 0.0

                // Step 2: Title similarity — must be computed first
                val rawTitleSim = similarity(queryLower, titleLower)
                val cleanTitleSim = similarity(cleanTitle(queryLower), cleanTitle(titleLower))
                val titleSimilarity = maxOf(rawTitleSim, cleanTitleSim)
                score += titleSimilarity * 50.0 // Dominant factor to ensure the right song wins

                // Step 3: Word-match bonus (query words present in title)
                var wordMatches = 0
                queryWords.forEach { word ->
                    if (titleLower.contains(word)) {
                        wordMatches++
                    }
                }
                val matchRatio = if (queryWords.isNotEmpty()) wordMatches.toDouble() / queryWords.size else 0.0
                score += matchRatio * 30.0 // Heavily reward containing the exact requested words

                // Step 4: Title-length similarity bonus
                val titleWords = titleLower.split("\\s+".toRegex()).size
                val lengthDiff = abs(queryWords.size - titleWords)
                score += maxOf(0.0, 5.0 - lengthDiff)

                // Step 5: Official-keyword bonus — ONLY if the title already resembles the query.
                // Scale the bonus by matchRatio so an unrelated song by the same artist 
                // doesn't win just by having "Official" in its title.
                if (titleSimilarity >= 0.3 || matchRatio >= 0.5) {
                    val officialBonus = getOfficialScore(item.title)
                    if (officialBonus > 0.0) {
                        val scaledBonus = officialBonus * 5.0 * matchRatio
                        score += scaledBonus
                        Log.d(TAG, "Official bonus ($scaledBonus) applied to '${item.title}'")
                    }
                }

                // Step 6: No-bad-indicator bonus (clean original, no remixes/covers etc.)
                val badIndicators = listOf(
                    "lyrics", "remix", "cover", "live", "acoustic",
                    "slowed", "reverb", "8d", "reaction", "tutorial"
                )
                val hasBadIndicator = badIndicators.any { titleLower.contains(it) }
                if (!hasBadIndicator) {
                    score += 5.0
                }

                // Step 7: Position bonus (earlier API results are slightly preferred)
                val positionBonus = maxOf(0.0, 5.0 - index * 0.5)
                score += positionBonus

                Log.d(
                    TAG,
                    "Item [$index] '${item.title}' - Score: ${score.toInt()}, TitleSim: ${(titleSimilarity * 100).toInt()}%"
                )

                if (score > highestScore) {
                    highestScore = score
                    bestMatchItem = item
                }
            }

            if (bestMatchItem != null && highestScore > 15.0) { // Minimum threshold
                Log.d(
                    TAG,
                    "Best Match Selected: ${bestMatchItem?.title}, Final Score: ${highestScore.toInt()}"
                )
                return bestMatchItem?.id
            }

            Log.d(TAG, "No suitable match found (best score: ${highestScore.toInt()})")
            null

        } catch (e: Exception) {
            Log.e(TAG, "Search Network error: ${e.message}", e)
            null
        }
    }

    /**
     * Fetch full radio queue recommendations from YouTube Music.
     * 
     * This performs a 2-step process:
     * 1. Get the radio playlist ID for the given video
     * 2. Fetch the full queue (up to 50 songs)
     * 
     * @param videoId YouTube video ID to base recommendations on
     * @return List of recommended songs (index 1 to ~49)
     */
    suspend fun fetchFullRadioQueue(videoId: String): List<YouTubeRecommendation> {
        Log.d(TAG, "fetchFullRadioQueue called with videoId: $videoId")

        return try {
            val context = mapOf(
                "client" to mapOf(
                    "hl" to "en-IN",
                    "gl" to "IN",
                    "clientName" to "WEB_REMIX",
                    "clientVersion" to "1.20251203.02.00"
                )
            )

            // STEP 1: Get Radio Playlist ID
            Log.d(TAG, "Step 1: Getting radio playlist ID")
            val step1Payload = mapOf(
                "videoId" to videoId,
                "context" to context
            )

            val step1Response = ApiClient.httpClient.post(YT_MUSIC_API_URL) {
                contentType(ContentType.Application.Json)
                setBody(gson.toJson(step1Payload))
                header("User-Agent", "Mozilla/5.0")
            }

            val step1Body: ByteArray = step1Response.body()
            val root = gson.fromJson(step1Body.decodeToString(), JsonObject::class.java)

            val playlistId = try {
                val watchEndpoint = root.getAsJsonObject("contents")
                    .getAsJsonObject("singleColumnMusicWatchNextResultsRenderer")
                    .getAsJsonObject("tabbedRenderer")
                    .getAsJsonObject("watchNextTabbedResultsRenderer")
                    .getAsJsonArray("tabs")[0].asJsonObject
                    .getAsJsonObject("tabRenderer")
                    .getAsJsonObject("content")
                    .getAsJsonObject("musicQueueRenderer")
                    .getAsJsonObject("content")
                    .getAsJsonObject("playlistPanelRenderer")
                    .getAsJsonArray("contents")[0].asJsonObject
                    .getAsJsonObject("playlistPanelVideoRenderer")
                    .getAsJsonObject("menu")
                    .getAsJsonObject("menuRenderer")
                    .getAsJsonArray("items")[0].asJsonObject
                    .getAsJsonObject("menuNavigationItemRenderer")
                    .getAsJsonObject("navigationEndpoint")
                    .getAsJsonObject("watchEndpoint")

                watchEndpoint.get("playlistId").asString
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract playlist ID: ${e.message}", e)
                return emptyList()
            }

            Log.d(TAG, "Got playlist ID: $playlistId")

            // STEP 2: Fetch Full Queue
            Log.d(TAG, "Step 2: Fetching full queue")
            val step2Payload = mapOf(
                "videoId" to videoId,
                "playlistId" to playlistId,
                "isAudioOnly" to true,
                "tunerSettingValue" to "AUTOMIX_SETTING_NORMAL",
                "context" to context
            )

            val step2Response = ApiClient.httpClient.post(YT_MUSIC_API_URL) {
                contentType(ContentType.Application.Json)
                setBody(gson.toJson(step2Payload))
                header("User-Agent", "Mozilla/5.0")
            }

            val step2Body: ByteArray = step2Response.body()
            val queueRoot = gson.fromJson(step2Body.decodeToString(), JsonObject::class.java)

            val tracks = try {
                queueRoot.getAsJsonObject("contents")
                    .getAsJsonObject("singleColumnMusicWatchNextResultsRenderer")
                    .getAsJsonObject("tabbedRenderer")
                    .getAsJsonObject("watchNextTabbedResultsRenderer")
                    .getAsJsonArray("tabs")[0].asJsonObject
                    .getAsJsonObject("tabRenderer")
                    .getAsJsonObject("content")
                    .getAsJsonObject("musicQueueRenderer")
                    .getAsJsonObject("content")
                    .getAsJsonObject("playlistPanelRenderer")
                    .getAsJsonArray("contents")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract tracks: ${e.message}", e)
                return emptyList()
            }

            val recommendations = mutableListOf<YouTubeRecommendation>()

            // Skip index 0 (the original song), get 1 to last
            tracks.forEachIndexed { index, el ->
                if (index == 0) return@forEachIndexed // Skip first track

                if (!el.asJsonObject.has("playlistPanelVideoRenderer")) return@forEachIndexed

                try {
                    val node = el.asJsonObject.getAsJsonObject("playlistPanelVideoRenderer")

                    val videoIdRec = node.get("videoId")?.asString ?: return@forEachIndexed

                    val title = node.getAsJsonObject("title")
                        ?.getAsJsonArray("runs")?.get(0)?.asJsonObject
                        ?.get("text")?.asString ?: return@forEachIndexed

                    // Extract artist from shortBylineText runs (all runs combined)
                    // Path: shortBylineText.runs[*].text → join all text
                    val artist = extractArtistFromRuns(
                        node.getAsJsonObject("shortBylineText")
                            ?.getAsJsonArray("runs")
                    )

                    // Extract duration from lengthText
                    val duration = try {
                        node.getAsJsonObject("lengthText")
                            ?.getAsJsonArray("runs")?.get(0)?.asJsonObject
                            ?.get("text")?.asString
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not extract duration for ${title}: ${e.message}")
                        null
                    }

                    recommendations.add(
                        YouTubeRecommendation(
                            id = videoIdRec,
                            title = title,
                            artist = artist,
                            duration = duration
                        )
                    )

                    Log.d(TAG, "[$index] $title — $artist (${duration ?: "unknown duration"})")

                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing track at index $index: ${e.message}")
                }
            }

            Log.d(TAG, "Successfully fetched ${recommendations.size} recommendations")
            recommendations

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching radio queue: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Validate and filter recommendations using Spotify search.
     *
     * Batched async validation with composite scoring:
     * 1. Pre-filter spam and negative songs (queue/recently played)
     * 2. Validate in batches of 5 concurrent Spotify searches
     * 3. Early return when enough tracks are validated (≥ maxResults)
     * 4. Score using composite: 60% YouTube position + 40% Spotify confidence
     * 5. Apply artist diversity caps (max 2 per seed artist, max 2 per other artist)
     *
     * @param recommendations List of YouTube recommendations (in YT order)
     * @param originalArtists Artists from the original song (comma-separated)
     * @param maxResults Maximum number of validated results to return (default 10)
     * @param negativeSongs Set of "title-artist" keys to exclude before validation
     * @return List of validated recommendations sorted by composite score
     */
    suspend fun validateAndFilterWithSpotify(
        recommendations: List<YouTubeRecommendation>,
        originalArtists: String = "",
        maxResults: Int = 10,
        negativeSongs: Set<String> = emptySet()
    ): List<ValidatedRecommendation> {
        Log.d(TAG, "Validating ${recommendations.size} recommendations with Spotify (batch mode)")
        Log.d(TAG, "Original track artists: $originalArtists")
        Log.d(TAG, "Negative songs count: ${negativeSongs.size}")

        // Parse original track's artists for diversity tracking
        val seedArtistNames = parseArtists(originalArtists).map { it.lowercase() }

        // ── Pre-filter: remove spam and negative songs before any API calls ──
        val candidatesWithIndex = recommendations.mapIndexedNotNull { index, rec ->
            // Skip spam/variants
            val spamKeyword = isSpamOrVariant(rec.title)
            if (spamKeyword != null) {
                Log.d(TAG, "Pre-filter: skipping spam '${rec.title}' (matched: $spamKeyword)")
                return@mapIndexedNotNull null
            }
            // Skip negative songs (already in queue / recently played / external downloads)
            val negKey = "${rec.title.lowercase()}-${rec.artist.lowercase()}"
            if (negativeSongs.contains(negKey)) {
                Log.d(TAG, "Pre-filter: skipping negative song '${rec.title}' by '${rec.artist}'")
                return@mapIndexedNotNull null
            }
            Pair(index, rec) // Preserve original YT index
        }

        Log.d(TAG, "After pre-filter: ${candidatesWithIndex.size} candidates (from ${recommendations.size})")

        if (candidatesWithIndex.isEmpty()) return emptyList()

        // ── Batched async validation ──
        val BATCH_SIZE = 5
        val allValidated = mutableListOf<ValidatedRecommendation>()
        val batches = candidatesWithIndex.chunked(BATCH_SIZE)

        for ((batchIdx, batch) in batches.withIndex()) {
            if (allValidated.size >= maxResults) {
                Log.d(TAG, "Early return: collected ${allValidated.size} validated (≥ $maxResults) after ${batchIdx} batches")
                break
            }

            Log.d(TAG, "[Batch ${batchIdx + 1}/${batches.size}] Validating ${batch.size} tracks concurrently")

            val batchResults = coroutineScope {
                batch.map { (ytIndex, rec) ->
                    async {
                        validateSingleRecommendation(rec, ytIndex, recommendations.size)
                    }
                }.awaitAll()
            }

            // Collect non-null results
            val validInBatch = batchResults.filterNotNull()
            allValidated.addAll(validInBatch)
            Log.d(TAG, "[Batch ${batchIdx + 1}] Validated ${validInBatch.size} / ${batch.size}")
        }

        Log.d(TAG, "Total validated: ${allValidated.size} out of ${recommendations.size} recommendations")

        if (allValidated.isEmpty()) return emptyList()

        // ── Composite scoring: sort by (60% YT position + 40% confidence) ──
        val scored = allValidated.map { rec ->
            val ytPositionScore = 1.0 - (rec.ytIndex.toDouble() / recommendations.size.coerceAtLeast(1))
            val normalizedConf = rec.confidence.coerceAtMost(1.15) // Cap boosted confidence
            val composite = (ytPositionScore * 0.6) + (normalizedConf * 0.4)
            rec.copy(composite = composite)
        }.sortedByDescending { it.composite }

        Log.d(TAG, "Composite scored & sorted. Top: '${scored.firstOrNull()?.title}' (composite=${scored.firstOrNull()?.composite?.let { "%.3f".format(it) }})")

        // ── Artist diversity: cap per-artist representation ──
        val MAX_PER_SEED_ARTIST = 2
        val MAX_PER_OTHER_ARTIST = 2
        val artistCounts = mutableMapOf<String, Int>()
        val diverse = mutableListOf<ValidatedRecommendation>()

        for (rec in scored) {
            if (diverse.size >= maxResults) break

            val recArtistKey = rec.artist.lowercase().trim()
            val isSeedArtist = seedArtistNames.any { seed ->
                similarity(seed, recArtistKey) > 0.75
            }
            val limit = if (isSeedArtist) MAX_PER_SEED_ARTIST else MAX_PER_OTHER_ARTIST
            val currentCount = artistCounts.getOrDefault(recArtistKey, 0)

            if (currentCount >= limit) {
                Log.d(TAG, "Diversity cap: skipping '${rec.title}' by '${rec.artist}' (count=$currentCount/$limit)")
                continue
            }

            artistCounts[recArtistKey] = currentCount + 1
            diverse.add(rec)
        }

        Log.d(TAG, "After diversity filter: ${diverse.size} final recommendations")
        diverse.forEachIndexed { i, rec ->
            Log.d(TAG, "  [$i] '${rec.title}' by '${rec.artist}' (composite=${"%.3f".format(rec.composite)}, conf=${(rec.confidence * 100).toInt()}%, ytIdx=${rec.ytIndex})")
        }

        return diverse
    }

    /**
     * Validate a single YouTube recommendation against Spotify.
     * Returns a ValidatedRecommendation if the match is accepted, null otherwise.
     *
     * @param rec The YouTube recommendation to validate
     * @param ytIndex The original index in the YouTube recommendations list
     * @param totalRecs Total number of recommendations (for logging context)
     */
    private suspend fun validateSingleRecommendation(
        rec: YouTubeRecommendation,
        ytIndex: Int,
        totalRecs: Int
    ): ValidatedRecommendation? {
        try {
            // Search Spotify
            val query = "${rec.title} ${rec.artist}"
            val searchResponse = try {
                SpotifyApi.search(query, listOf("track"))
            } catch (offlineEx: OfflineException) {
                Log.w(TAG, "Device offline while validating '${rec.title}'. Stopping.")
                throw offlineEx
            }

            val spotifyResults = searchResponse.tracks?.items ?: emptyList()
            if (spotifyResults.isEmpty()) {
                Log.d(TAG, "No Spotify results for: $query")
                return null
            }

            // Try top 5 Spotify results for best matching
            var bestMatch: SpotifyTrack? = null
            var bestConfidence = 0.0
            var bestTitleSimilarity = 0.0
            var bestDurationSimilarity = 0.0

            for (spotifyTrack in spotifyResults.take(5)) {
                val rawTitleSim = similarity(rec.title, spotifyTrack.name)
                val cleanTitleSim = similarity(cleanTitle(rec.title), cleanTitle(spotifyTrack.name))
                val titleSimilarity = maxOf(rawTitleSim, cleanTitleSim)

                val spotifyArtists = parseArtists(spotifyTrack.artists.joinToString(", ") { it.name })
                val youtubeArtists = parseArtists(rec.artist)
                val artistSimilarity = artistListSimilarity(spotifyArtists, youtubeArtists)

                val youtubeDurationSec = parseDurationToSeconds(rec.duration)
                val spotifyDurationSec = spotifyTrack.durationMs / 1000
                val durSim = durationSimilarity(youtubeDurationSec, spotifyDurationSec)

                // Text confidence: 70% artist, 30% title
                val textConfidence = (titleSimilarity * 0.3) + (artistSimilarity * 0.7)
                var overallConfidence = (textConfidence * 0.6) + (durSim * 0.4)

                // Boost for excellent artist matches
                if (artistSimilarity >= 0.9) {
                    overallConfidence += 0.15
                } else if (artistSimilarity >= 0.7) {
                    overallConfidence += 0.1
                } else if (artistSimilarity >= 0.5) {
                    overallConfidence += 0.05
                }

                if (overallConfidence > bestConfidence) {
                    bestConfidence = overallConfidence
                    bestMatch = spotifyTrack
                    bestTitleSimilarity = titleSimilarity
                    bestDurationSimilarity = durSim
                }
            }

            // Acceptance criteria
            val shouldAccept = when {
                bestTitleSimilarity >= 0.8 && bestDurationSimilarity >= 0.8 && bestConfidence >= 0.75 -> true
                bestTitleSimilarity >= 0.7 && bestDurationSimilarity >= 0.6 && bestConfidence >= 0.65 -> true
                bestDurationSimilarity >= 0.95 && bestConfidence >= 0.5 && bestTitleSimilarity >= 0.05 -> true
                bestConfidence >= 0.7 -> true
                else -> false
            }

            if (bestMatch != null && shouldAccept && bestMatch.externalUrls.spotify != null) {
                val officialScore = getOfficialScore(rec.title)

                Log.d(
                    TAG,
                    "✓ Validated [$ytIndex]: ${bestMatch.name} by ${bestMatch.artists.first().name} (Title: ${(bestTitleSimilarity * 100).toInt()}%, Duration: ${(bestDurationSimilarity * 100).toInt()}%, Conf: ${(bestConfidence * 100).toInt()}%)"
                )

                return ValidatedRecommendation(
                    youtubeVideoId = rec.id,
                    title = bestMatch.name,
                    artist = bestMatch.artists.joinToString(", ") { it.name },
                    spotifyUrl = bestMatch.externalUrls.spotify!!,
                    confidence = bestConfidence,
                    isOfficial = officialScore > 0.0,
                    durationSec = (bestMatch.durationMs / 1000).toInt(),
                    ytIndex = ytIndex
                )
            } else {
                Log.d(
                    TAG,
                    "✗ Rejected [$ytIndex]: ${rec.title} (Title ${(bestTitleSimilarity * 100).toInt()}%, Duration ${(bestDurationSimilarity * 100).toInt()}%, Conf ${(bestConfidence * 100).toInt()}%)"
                )
            }
        } catch (e: OfflineException) {
            throw e // Propagate offline to stop all batches
        } catch (e: Exception) {
            Log.e(TAG, "Error validating ${rec.title}: ${e.message}", e)
        }
        return null
    }


    /**
     * Test method to debug video selection scoring.
     * Returns all items with their scores for analysis.
     */
    suspend fun debugVideoSelection(songName: String): List<Triple<String, String, Double>> {
        Log.d(TAG, "debugVideoSelection called with songName: \"$songName\"")

        return try {
            val response = ApiClient.httpClient.post(SEARCH_URL) {
                contentType(ContentType.Application.Json)
                setBody(SearchRequest(query = songName))
            }

            val searchResponse: SearchResponse = response.body()
            val items = searchResponse.items

            val queryLower = songName.lowercase()
            val queryWords = queryLower.split("\\s+".toRegex()).filter { it.length > 1 }

            val scoredItems = items.mapIndexed { index, item ->
                val titleLower = item.title.lowercase()

                // Skip spam content
                if (isSpamOrVariant(item.title) != null) {
                    return@mapIndexed Triple(item.id, item.title, -1.0)
                }

                // Calculate comprehensive score
                var score = 0.0

                // Position bonus (earlier results are better)
                val positionBonus = maxOf(0.0, 10.0 - index * 0.5)
                score += positionBonus

                // Title similarity
                val rawTitleSim = similarity(queryLower, titleLower)
                val cleanTitleSim = similarity(cleanTitle(queryLower), cleanTitle(titleLower))
                val titleSimilarity = maxOf(rawTitleSim, cleanTitleSim)
                score += titleSimilarity * 30.0

                // Word match bonus
                var wordMatches = 0
                queryWords.forEach { word ->
                    if (titleLower.contains(word)) {
                        wordMatches++
                    }
                }
                score += wordMatches * 5.0

                // Length similarity bonus
                val titleWords = titleLower.split("\\s+".toRegex()).size
                val lengthDiff = abs(queryWords.size - titleWords)
                score += maxOf(0.0, 5.0 - lengthDiff)

                // Prefer titles that look like official music videos
                if (titleLower.contains("official") || titleLower.contains("music video") ||
                    titleLower.contains("prod by") || titleLower.contains("ft.")
                ) {
                    score += 10.0
                }

                // Prefer titles without extra qualifiers
                val badIndicators = listOf(
                    "lyrics", "remix", "cover", "live", "acoustic",
                    "slowed", "reverb", "8d", "reaction", "tutorial"
                )
                val hasBadIndicator = badIndicators.any { titleLower.contains(it) }
                if (!hasBadIndicator) {
                    score += 5.0
                }

                Triple(item.id, item.title, score)
            }

            scoredItems.sortedByDescending { it.third }

        } catch (e: Exception) {
            Log.e(TAG, "Debug search error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Validated recommendation with Spotify link.
     */
    @Serializable
    data class ValidatedRecommendation(
        val youtubeVideoId: String,
        val title: String,
        val artist: String,
        val spotifyUrl: String,
        val confidence: Double,
        val isOfficial: Boolean,
        val durationSec: Int,
        val ytIndex: Int = 0,
        val composite: Double = 0.0
    )

}