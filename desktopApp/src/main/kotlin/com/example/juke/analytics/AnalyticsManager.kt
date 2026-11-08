package com.example.juke.analytics

import android.content.Context

/** Desktop build collects no analytics; the API stays so shared code compiles unchanged. */
class AnalyticsManager private constructor() {
    companion object {
        private val instance = AnalyticsManager()
        fun initialize(context: Context) {}
        fun getInstance(context: Context) = instance
        fun getInstance() = instance
        fun getIfInitialized(): AnalyticsManager? = instance
        fun log(eventName: String, properties: Map<String, Any> = emptyMap()) {}
        fun setup(context: Context) {}
    }
    fun songTransition(completed: Boolean) {}
    fun trackSongPlayed(songId: String, songTitle: String, songArtist: String, songDuration: Long, positionInQueue: Int = 0, isStream: Boolean = false) {}
    fun trackSearchQuery(searchTerm: String) {}
    fun trackAppOpened() {}
    fun trackAppClosed() {}
    fun installId(): String = "desktop"
    fun endSession() {}
    fun track(eventName: String, properties: Map<String, Any> = emptyMap()) {}
}
