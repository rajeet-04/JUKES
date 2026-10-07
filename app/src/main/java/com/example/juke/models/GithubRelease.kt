package com.example.juke.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String, // e.g., "v1.0.x"
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("body") val body: String? = null,
    @SerialName("prerelease") val isPrerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("draft") val isDraft: Boolean = false,
    @SerialName("assets") val assets: List<GithubReleaseAsset> = emptyList()
) {
    /** Critical only when the maintainer says so in the tag, never from words in the notes. */
    val isCritical: Boolean
        get() = tagName.contains("emergency", true) || tagName.contains("hotfix", true)
}

@Serializable
data class GithubReleaseAsset(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("size") val size: Long = 0
)
