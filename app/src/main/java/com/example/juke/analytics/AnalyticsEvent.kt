package com.example.juke.analytics

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
    const val JAM_CREATED = "jam_created"
    const val JAM_JOINED = "jam_joined"
    const val JAM_LEFT = "jam_left"
    const val JAM_MIX = "jam_mix"
    const val JAM_SONG_FAILED = "jam_song_failed"
    const val SHARE_LINK_CREATED = "share_link_created"
    const val SHARED_PLAYLIST_SAVED = "shared_playlist_saved"
    const val FAVORITE_TOGGLED = "favorite_toggled"
    const val PLAYLIST_CREATED = "playlist_created"
    const val STREAM_RECOVERY = "stream_recovery"
    const val SCREEN_VIEWED = "screen_viewed"
    const val ADDED_TO_PLAYLIST = "added_to_playlist"
    const val QUEUE_ADDED = "queue_added"
    const val QUEUE_REMOVED = "queue_removed"
    const val SLEEP_TIMER_STARTED = "sleep_timer_started"
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
    const val SEEDED = "seeded"
    const val SCREEN = "screen"
    const val MINUTES = "minutes"
    const val IN_JAM = "in_jam"
    const val IS_STREAM = "is_stream"
    const val LISTENED_MS = "listened_ms"
    const val VIA = "via"
    const val MEMBERS = "members"
    const val QUEUE_SIZE = "queue_size"
    const val SONG_COUNT = "song_count"
    const val IS_HOST = "is_host"
    const val SUCCESS = "success"
    const val ATTEMPT = "attempt"
    const val FAVORITE = "favorite"
    const val APP_VERSION = "app_version"
    const val FIRST_INSTALL_TIME = "first_install_time"
    
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
