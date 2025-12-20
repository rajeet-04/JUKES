package com.example.juke.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Official Spotify Web API response models
 */

@Serializable
data class SpotifySearchResponse(
    val tracks: SpotifyTracksResponse
)

@Serializable
data class SpotifyTracksResponse(
    val href: String,
    val limit: Int,
    val next: String? = null,
    val offset: Int,
    val previous: String? = null,
    val total: Int,
    val items: List<SpotifyTrack>
)

@Serializable
data class SpotifyTrack(
    val album: SpotifyAlbum,
    val artists: List<SpotifyArtist>,
    @SerialName("duration_ms")
    val durationMs: Int,
    val explicit: Boolean,
    @SerialName("external_urls")
    val externalUrls: SpotifyExternalUrls,
    val href: String,
    val id: String,
    val name: String,
    val popularity: Int,
    @SerialName("preview_url")
    val previewUrl: String? = null,
    val uri: String
)

@Serializable
data class SpotifyAlbum(
    @SerialName("album_type")
    val albumType: String,
    val artists: List<SpotifyArtist>,
    @SerialName("external_urls")
    val externalUrls: SpotifyExternalUrls,
    val href: String,
    val id: String,
    val images: List<SpotifyImage>,
    val name: String,
    @SerialName("release_date")
    val releaseDate: String,
    @SerialName("total_tracks")
    val totalTracks: Int,
    val uri: String
)

@Serializable
data class SpotifyArtist(
    @SerialName("external_urls")
    val externalUrls: SpotifyExternalUrls,
    val href: String,
    val id: String,
    val name: String,
    val uri: String
)

@Serializable
data class SpotifyImage(
    val height: Int,
    val width: Int,
    val url: String
)

@Serializable
data class SpotifyExternalUrls(
    val spotify: String
)

@Serializable
data class SpotifyTokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String,
    @SerialName("expires_in")
    val expiresIn: Int
)
