package com.example.juke.analytics

import kotlinx.serialization.Serializable

@Serializable
data class AnalyticsEvent(
    val eventName: String,
    val properties: Map<String, String>,
    val timestamp: Long = System.currentTimeMillis()
)

// Event types
object EventType {
    const val SONG_PLAYED = "song_played"
    const val SONG_COMPLETED = "song_completed"
    const val SONG_SKIPPED = "song_skipped"
    const val SESSION_START = "session_start"
    const val SESSION_END = "session_end"
    const val SEARCH_QUERY = "search_query"
    const val APP_OPENED = "app_opened"
    const val APP_CLOSED = "app_closed"
}

// Properties
object EventProperty {
    const val SONG_ID = "song_id" // Format: "Title - Artist" for better analytics
    const val SONG_TITLE = "song_title"
    const val SONG_ARTIST = "song_artist"
    const val SONG_DURATION = "song_duration"
    const val PLAY_DURATION = "play_duration"
    const val COMPLETION_PERCENTAGE = "completion_percentage"
    const val POSITION_IN_QUEUE = "position_in_queue"
    const val SESSION_LENGTH = "session_length"
    const val SEARCH_TERM = "search_term"
    const val TIMESTAMP = "timestamp"
    const val USER_ID = "user_id"
    const val SKIPPED = "skipped"
    const val DURATION_SECONDS = "duration_seconds"
    const val FROM_BACKGROUND = "from_background"
    const val IS_NEW_USER = "is_new_user"
    
    // Device properties
    const val DEVICE_MANUFACTURER = "device_manufacturer"
    const val DEVICE_MODEL = "device_model"
    const val DEVICE_NAME = "device_name"
    const val DEVICE_TYPE = "device_type"
    
    // OS properties
    const val OS = "os"
    const val OS_NAME = "os_name"
    const val OS_VERSION = "os_version"
    
    // Network properties
    const val NETWORK_CARRIER = "network_carrier"
    const val NETWORK_CELLULAR = "network_cellular"
    const val NETWORK_WIFI = "network_wifi"
    
    // Location/Timezone (device-side)
    const val TIMEZONE = "timezone"
    
    // Note: GeoIP properties (country, city, lat/long, etc.) are automatically
    // captured by PostHog server-side from the IP address
}
