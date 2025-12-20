package com.juke.models

import kotlinx.serialization.Serializable

/**
 * Track data model representing a music track in the JUKE app.
 * 
 * This is the primary data structure for music tracks, containing all metadata,
 * file locations, lyrics, and user interaction data.
 *
 * @property uuid Unique identifier for the track (generated UUID)
 * @property title Song title
 * @property artist Artist/band name
 * @property thumbnailUri Local file URI for the album artwork/thumbnail
 * @property durationSec Duration of the track in seconds
 * @property localUri Local file URI where the MP3 is stored
 * @property ytVideoId YouTube video ID for fetching recommendations
 * @property syncedLyrics LRC format synced lyrics (with timestamps)
 * @property plainLyrics Plain text lyrics without timestamps
 * @property isFavourite Whether user has marked this track as favorite
 * @property playCount Number of times this track has been played
 * @property lastPlayedAt ISO 8601 timestamp of when track was last played
 */
@Serializable
data class Track(
    val uuid: String,
    val title: String,
    val artist: String,
    val thumbnailUri: String? = null,
    val durationSec: Int,
    val localUri: String? = null,
    val ytVideoId: String? = null,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
    val isFavourite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: String? = null
)

/**
 * Spotdown song search result from Spotify.
 * 
 * This represents a song found via Spotify search API,
 * used before downloading and converting to a Track.
 *
 * @property title Song title from Spotify
 * @property artist Artist name from Spotify
 * @property thumbnail Album artwork URL (HTTP link)
 * @property url Spotify track URL (e.g., https://open.spotify.com/track/...)
 * @property duration Duration string in format "MM:SS" (e.g., "3:45")
 */
@Serializable
data class SpotdownSong(
    val title: String,
    val artist: String,
    val thumbnail: String,
    val url: String,
    val duration: String
)

/**
 * Response from Spotdown search API.
 *
 * @property songs List of songs matching the search query
 * @property contentType Response content type (usually "application/json")
 */
@Serializable
data class SpotdownSearchResponse(
    val songs: List<SpotdownSong>,
    val contentType: String
)

/**
 * Lyrics result from LRCLib API.
 * 
 * LRCLib provides both plain and synced lyrics for songs.
 *
 * @property id Unique ID from LRCLib
 * @property name Song name
 * @property trackName Track name (usually same as name)
 * @property artistName Artist name
 * @property albumName Album name
 * @property duration Track duration in seconds
 * @property instrumental Whether the track is instrumental (no lyrics)
 * @property plainLyrics Plain text lyrics
 * @property syncedLyrics LRC format synced lyrics with timestamps
 */
@Serializable
data class LRCLibResult(
    val id: Int,
    val name: String,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val duration: Int,
    val instrumental: Boolean,
    val plainLyrics: String,
    val syncedLyrics: String
)

/**
 * YouTube music recommendation from YouTube Music API.
 * 
 * Used for auto-playing similar songs after current track finishes.
 *
 * @property id YouTube video ID
 * @property title Song title from YouTube
 * @property artist Artist name from YouTube
 */
@Serializable
data class YouTubeRecommendation(
    val id: String,
    val title: String,
    val artist: String
)

/**
 * Download progress callback data.
 *
 * @property totalBytes Total file size in bytes
 * @property downloadedBytes Bytes downloaded so far
 * @property progress Progress percentage (0.0 to 1.0)
 */
data class DownloadProgress(
    val totalBytes: Long,
    val downloadedBytes: Long,
    val progress: Double
)
