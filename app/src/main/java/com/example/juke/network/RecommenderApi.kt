package com.example.juke.network

import android.util.Log
import com.example.juke.models.YouTubeRecommendation
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * YouTube Music Recommender API Service.
 */
object RecommenderApi {
    
    private const val TAG = "RecommenderApi"
    private const val SEARCH_URL = "https://mp3juice3.ninja/api/yt-data"
    private const val YT_MUSIC_URL = "https://music.youtube.com/youtubei/v1/next?prettyPrint=true"
    
    private val OFFICIAL_KEYWORDS = listOf(
        "official", "music video", "official video", "official music video",
        "lyric video", "official lyric video", "vevo", "official audio",
        "from the album", "single", "ep", "lp", "remastered",
        "anniversary edition", "deluxe edition"
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
    
    @Serializable
    private data class YTMusicContext(
        val client: YTMusicClient
    )
    
    @Serializable
    private data class YTMusicClient(
        val hl: String = "en-IN",
        val gl: String = "IN",
        val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36,gzip(gfe)",
        val clientName: String = "WEB_REMIX",
        val clientVersion: String = "1.20241220.01.00"
    )
    
    @Serializable
    private data class YTMusicRequestStep1(
        val videoId: String,
        val context: YTMusicContext
    )
    
    @Serializable
    private data class YTMusicRequestStep2(
        val tunerSettingValue: String = "AUTOMIX_SETTING_NORMAL",
        val videoId: String,
        val playlistId: String,
        val isAudioOnly: Boolean = true,
        val context: YTMusicContext
    )
    
    private fun getOfficialScore(title: String): Double {
        val lowerTitle = title.lowercase()
        var score = 0.0
        
        OFFICIAL_KEYWORDS.forEach { keyword ->
            if (lowerTitle.contains(keyword.lowercase())) {
                score += 1.0
            }
        }
        
        if (score > 1.0) {
            score += 0.5
        }
        
        return score
    }
    
    private fun calculateSimilarity(s1: String, s2: String): Double {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        
        if (longer.isEmpty()) return 1.0
        
        val longerLength = longer.length
        val distance = levenshteinDistance(longer, shorter)
        
        return (longerLength - distance) / longerLength.toDouble()
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1)
        
        for (j in costs.indices) costs[j] = j
        
        for (i in 1..s1.length) {
            costs[0] = i
            var nw = i - 1
            
            for (j in 1..s2.length) {
                val cj = minOf(
                    1 + minOf(costs[j], costs[j - 1]),
                    if (s1[i - 1] == s2[j - 1]) nw else nw + 1
                )
                nw = costs[j]
                costs[j] = cj
            }
        }
        
        return costs[s2.length]
    }
    
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
            
            items.forEach { item ->
                val officialScore = getOfficialScore(item.title)
                if (officialScore > 0.0) {
                    Log.d(TAG, "Official Match Found: ${item.title}, Official Score: $officialScore")
                    return item.id
                }
            }
            
            var bestMatchItem: SearchItem? = null
            var highestScore = 0.0
            
            items.forEach { item ->
                val similarityScore = calculateSimilarity(
                    songName.lowercase(),
                    item.title.lowercase()
                )
                
                if (similarityScore > highestScore) {
                    highestScore = similarityScore
                    bestMatchItem = item
                }
            }
            
            if (bestMatchItem != null && highestScore > 0.5) {
                Log.d(TAG, "Similarity Match: ${bestMatchItem!!.title}, Score: $highestScore")
                return bestMatchItem!!.id
            }
            
            Log.d(TAG, "No match found with sufficient similarity score")
            null
            
        } catch (e: Exception) {
            Log.e(TAG, "Search Network error: ${e.message}", e)
            null
        }
    }
    
    suspend fun getRecommendations(videoId: String): List<YouTubeRecommendation> {
        if (videoId.isEmpty()) return emptyList()
        
        Log.d(TAG, "getRecommendations called with videoId: $videoId")
        
        return try {
            val context = YTMusicContext(client = YTMusicClient())
            
            val payloadStep1 = YTMusicRequestStep1(
                videoId = videoId,
                context = context
            )
            
            val response1 = ApiClient.httpClient.post(YT_MUSIC_URL) {
                contentType(ContentType.Application.Json)
                setBody(payloadStep1)
            }
            
            val data1: JsonObject = response1.body()
            Log.d(TAG, "Step 1 - Received response")
            
            val tabs = data1["contents"]?.jsonObject
                ?.get("singleColumnMusicWatchNextResultsRenderer")?.jsonObject
                ?.get("tabbedRenderer")?.jsonObject
                ?.get("watchNextTabbedResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray
            
            val queueContent = tabs?.get(0)?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("musicQueueRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("playlistPanelRenderer")?.jsonObject
                ?.get("contents")?.jsonArray
            
            val radioPlaylistId = queueContent?.get(0)?.jsonObject
                ?.get("playlistPanelVideoRenderer")?.jsonObject
                ?.get("menu")?.jsonObject
                ?.get("menuRenderer")?.jsonObject
                ?.get("items")?.jsonArray
                ?.get(0)?.jsonObject
                ?.get("menuNavigationItemRenderer")?.jsonObject
                ?.get("navigationEndpoint")?.jsonObject
                ?.get("watchEndpoint")?.jsonObject
                ?.get("playlistId")?.jsonPrimitive?.content
            
            if (radioPlaylistId == null) {
                Log.d(TAG, "Could not extract Radio Playlist ID")
                return emptyList()
            }
            
            Log.d(TAG, "Radio Playlist ID: $radioPlaylistId")
            
            val payloadStep2 = YTMusicRequestStep2(
                videoId = videoId,
                playlistId = radioPlaylistId,
                context = context
            )
            
            val response2 = ApiClient.httpClient.post(YT_MUSIC_URL) {
                contentType(ContentType.Application.Json)
                setBody(payloadStep2)
            }
            
            val data2: JsonObject = response2.body()
            
            val tabs2 = data2["contents"]?.jsonObject
                ?.get("singleColumnMusicWatchNextResultsRenderer")?.jsonObject
                ?.get("tabbedRenderer")?.jsonObject
                ?.get("watchNextTabbedResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray
            
            val fullQueue = tabs2?.get(0)?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("musicQueueRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("playlistPanelRenderer")?.jsonObject
                ?.get("contents")?.jsonArray
            
            if (fullQueue == null) {
                Log.d(TAG, "No queue found in recommendations")
                return emptyList()
            }
            
            val recommendations = mutableListOf<YouTubeRecommendation>()
            
            fullQueue.forEach { item ->
                val node = item.jsonObject["playlistPanelVideoRenderer"]?.jsonObject
                if (node != null) {
                    val vId = node["videoId"]?.jsonPrimitive?.content ?: return@forEach
                    
                    val title = node["title"]?.jsonObject
                        ?.get("runs")?.jsonArray
                        ?.get(0)?.jsonObject
                        ?.get("text")?.jsonPrimitive?.content ?: "Unknown"
                    
                    val artist = node["longBylineText"]?.jsonObject
                        ?.get("runs")?.jsonArray
                        ?.get(0)?.jsonObject
                        ?.get("text")?.jsonPrimitive?.content ?: "Unknown"
                    
                    if (title != "Unknown") {
                        recommendations.add(
                            YouTubeRecommendation(
                                id = vId,
                                title = title,
                                artist = artist
                            )
                        )
                    }
                }
            }
            
            val finalRecs = recommendations.drop(1).take(3)
            Log.d(TAG, "Returning ${finalRecs.size} recommendations")
            
            finalRecs
            
        } catch (e: Exception) {
            Log.e(TAG, "Network Error in recommendations: ${e.message}", e)
            emptyList()
        }
    }
}
