package com.example.juke.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Official Spotify Web API response models
 */

@Serializable
data class SpotifySearchResponse(
    val tracks: SpotifyTracksResponse? = null,
    val artists: SpotifyArtistsResponse? = null,
    val playlists: SpotifyPlaylistsResponse? = null,
    val albums: SpotifyAlbumsResponse? = null
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
    val href: String? = null,
    val id: String? = null,
    val name: String,
    val popularity: Int = 0,
    @SerialName("preview_url")
    val previewUrl: String? = null,
    val uri: String,
    @SerialName("is_local")
    val isLocal: Boolean = false
)

@Serializable
data class SpotifySimplifiedTrack(
    val artists: List<SpotifyArtist>,
    @SerialName("duration_ms")
    val durationMs: Int,
    val explicit: Boolean,
    @SerialName("external_urls")
    val externalUrls: SpotifyExternalUrls,
    val href: String? = null,
    val id: String? = null,
    val name: String,
    @SerialName("preview_url")
    val previewUrl: String? = null,
    @SerialName("track_number")
    val trackNumber: Int? = null,
    val uri: String
)

@Serializable
data class SpotifyAlbum(
    @SerialName("album_type")
    val albumType: String? = null,
    val artists: List<SpotifyArtist>,
    @SerialName("external_urls")
    val externalUrls: SpotifyExternalUrls,
    val href: String? = null,
    val id: String? = null,
    val images: List<SpotifyImage>,
    val name: String,
    @SerialName("release_date")
    val releaseDate: String? = null,
    @SerialName("total_tracks")
    val totalTracks: Int? = null,
    val uri: String? = null
)

@Serializable
data class SpotifyArtist(
    @SerialName("external_urls")
    val externalUrls: SpotifyExternalUrls,
    val href: String? = null,
    val id: String? = null,
    val name: String,
    val uri: String? = null,
    val images: List<SpotifyImage> = emptyList(),
    val genres: List<String> = emptyList(),
    val popularity: Int = 0,
    val followers: SpotifyFollowers? = null
)

@Serializable
data class SpotifyFollowers(
    val href: String? = null,
    val total: Int
)

@Serializable
data class SpotifyArtistsResponse(
    val href: String,
    val limit: Int,
    val next: String? = null,
    val offset: Int,
    val previous: String? = null,
    val total: Int,
    val items: List<SpotifyArtist>
)

@Serializable
data class SpotifyPlaylist(
    val collaborative: Boolean,
    val description: String?,
    @SerialName("external_urls")
    val externalUrls: SpotifyExternalUrls,
    val href: String,
    val id: String,
    val images: List<SpotifyImage> = emptyList(),
    val name: String,
    val owner: SpotifyUser,
    val public: Boolean? = null,
    @SerialName("snapshot_id")
    val snapshotId: String,
    val tracks: SpotifyPlaylistTracks? = null,
    val uri: String
)

@Serializable
data class SpotifyUser(
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("external_urls")
    val externalUrls: SpotifyExternalUrls,
    val href: String,
    val id: String,
    val uri: String
)

@Serializable
data class SpotifyPlaylistTracks(
    val href: String,
    val total: Int
)

@Serializable
data class SpotifyPlaylistItem(
    @SerialName("added_at")
    val addedAt: String,
    val track: SpotifyTrack?
)

@Serializable
data class SpotifyPlaylistTracksResponse(
    val href: String,
    val limit: Int,
    val next: String? = null,
    val offset: Int,
    val previous: String? = null,
    val total: Int,
    val items: List<SpotifyPlaylistItem>
)

@Serializable
data class SpotifyPlaylistsResponse(
    val href: String,
    val limit: Int,
    val next: String? = null,
    val offset: Int,
    val previous: String? = null,
    val total: Int,
    val items: List<SpotifyPlaylist?>
)

@Serializable
data class SpotifyAlbumsResponse(
    val href: String,
    val limit: Int,
    val next: String? = null,
    val offset: Int,
    val previous: String? = null,
    val total: Int,
    val items: List<SpotifyAlbum>
)

@Serializable
data class SpotifyTopTracksResponse(
    val tracks: List<SpotifyTrack>
)

@Serializable
data class SpotifyAlbumTracksResponse(
    val href: String,
    val limit: Int,
    val next: String? = null,
    val offset: Int,
    val previous: String? = null,
    val total: Int,
    val items: List<SpotifySimplifiedTrack>
)

@Serializable
data class SpotifyImage(
    val height: Int?,
    val width: Int?,
    val url: String
)

@Serializable
data class SpotifyExternalUrls(
    val spotify: String? = null
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
