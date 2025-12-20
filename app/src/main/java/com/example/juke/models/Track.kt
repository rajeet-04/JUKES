package com.example.juke.models

import kotlinx.serialization.Serializable

/**
 * Track data model representing a music track in the JUKE app.
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
 */
@Serializable
data class SpotdownSong(
    val title: String,
    val artist: String,
    val thumbnail: String,
    val url: String,
    val duration: String,
    val cached: Boolean = false
)

/**
 * Response from Spotdown search API.
 */
@Serializable
data class SpotdownSearchResponse(
    val songs: List<SpotdownSong>,
    val contentType: String
)

/**
 * Lyrics result from LRCLib API.
 */
@Serializable
data class LRCLibResult(
    val id: Int,
    val name: String,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val duration: Double,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?
)

/**
 * YouTube music recommendation from YouTube Music API.
 */
@Serializable
data class YouTubeRecommendation(
    val id: String,
    val title: String,
    val artist: String
)

/**
 * Download progress callback data.
 */
data class DownloadProgress(
    val totalBytes: Long,
    val downloadedBytes: Long,
    val progress: Double
)
