package com.example.juke.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String, // e.g., "v1.0.x"
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("body") val body: String? = null,
    @SerialName("prerelease") val isPrerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null
)
