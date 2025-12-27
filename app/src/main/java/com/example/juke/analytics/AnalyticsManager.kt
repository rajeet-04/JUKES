package com.example.juke.analytics

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.posthog.PostHog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.TimeZone
import java.util.UUID
import androidx.core.content.edit

class AnalyticsManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AnalyticsManager"
        private const val PREFS_NAME = "analytics_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_PENDING_EVENTS = "pending_events"
        private const val KEY_SESSION_START = "session_start_time"
        private const val KEY_TOTAL_SONGS_PLAYED = "total_songs_played"
        private const val KEY_TOTAL_LISTENING_TIME = "total_listening_time"
        private const val KEY_IS_NEW_USER = "is_new_user"
        private const val KEY_FIRST_INSTALL_TIME = "first_install_time"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: AnalyticsManager? = null

        fun initialize(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = AnalyticsManager(context.applicationContext)
                    }
                }
            }
        }

        fun getInstance(): AnalyticsManager {
            return instance ?: throw IllegalStateException("AnalyticsManager not initialized")
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eventQueue = mutableListOf<AnalyticsEvent>()
    private val mutex = Mutex()
    
    // User tracking
    private val userId: String = prefs.getString(KEY_USER_ID, null) ?: generateAndStoreUserId()
    private val isNewUser: Boolean = prefs.getBoolean(KEY_IS_NEW_USER, false)
    
    // Session tracking
    private var sessionStartTime: Long = 0
    private val songsPlayedInOrder = mutableListOf<SongPlay>()
    private var isAppFromBackground = false
    
    // Network monitoring
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private var isOnline = false
    
    // Device information (cached)
    private val deviceInfo = DeviceInfo(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        deviceName = Build.DEVICE,
        deviceType = if (context.resources.configuration.smallestScreenWidthDp >= 600) "tablet" else "phone",
        osName = "Android",
        osVersion = Build.VERSION.RELEASE,
        os = "Android ${Build.VERSION.RELEASE}"
    )

    init {
        loadPendingEvents()
        setupNetworkMonitoring()
        startSession()
    }

    private fun generateAndStoreUserId(): String {
        val newUserId = UUID.randomUUID().toString()
        val firstInstallTime = System.currentTimeMillis()
        prefs.edit {
            putString(KEY_USER_ID, newUserId)
                .putBoolean(KEY_IS_NEW_USER, true)
                .putLong(KEY_FIRST_INSTALL_TIME, firstInstallTime)
        }
        Log.d(TAG, "Generated new user ID: $newUserId (new installation)")
        return newUserId
    }
    
    private fun getDeviceTimezone(): String {
        return TimeZone.getDefault().id
    }
    
    private fun getNetworkInfo(): NetworkInfo {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
        val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
        val networkCarrier = if (isCellular) {
            telephonyManager?.networkOperatorName ?: "Unknown"
        } else {
            "N/A"
        }
        
        return NetworkInfo(
            carrier = networkCarrier,
            cellular = isCellular,
            wifi = isWifi
        )
    }

    private fun loadPendingEvents() {
        scope.launch {
            mutex.withLock {
                try {
                    val eventsJson = prefs.getString(KEY_PENDING_EVENTS, null)
                    if (!eventsJson.isNullOrEmpty()) {
                        val events = json.decodeFromString<List<AnalyticsEvent>>(eventsJson)
                        eventQueue.addAll(events)
                        Log.d(TAG, "Loaded ${events.size} pending events")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading pending events", e)
                }
            }
        }
    }

    private fun savePendingEvents() {
        scope.launch {
            mutex.withLock {
                try {
                    val eventsJson = json.encodeToString(eventQueue)
                    prefs.edit { putString(KEY_PENDING_EVENTS, eventsJson) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving pending events", e)
                }
            }
        }
    }

    private fun setupNetworkMonitoring() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "✓ Network connection detected")
                isOnline = true
                // Automatically sync all stored events when device comes online
                syncEvents()
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "✗ Network connection lost - events will be stored locally")
                isOnline = false
            }
        })

        // Check initial state
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        isOnline = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        
        if (isOnline) {
            Log.d(TAG, "AnalyticsManager initialized - Device ONLINE")
        } else {
            Log.d(TAG, "AnalyticsManager initialized - Device OFFLINE (events will be stored)")
        }
    }

    private fun startSession() {
        sessionStartTime = System.currentTimeMillis()
        prefs.edit { putLong(KEY_SESSION_START, sessionStartTime) }
        
        val networkInfo = getNetworkInfo()
        val properties = mutableMapOf<String, Any>(
            EventProperty.USER_ID to userId,
            EventProperty.TIMESTAMP to sessionStartTime,
            "from_background" to isAppFromBackground,
            "is_new_user" to isNewUser,
            "device_manufacturer" to deviceInfo.manufacturer,
            "device_model" to deviceInfo.model,
            "device_name" to deviceInfo.deviceName,
            "device_type" to deviceInfo.deviceType,
            "os" to deviceInfo.os,
            "os_name" to deviceInfo.osName,
            "os_version" to deviceInfo.osVersion,
            "timezone" to getDeviceTimezone(),
            "network_carrier" to networkInfo.carrier,
            "network_cellular" to networkInfo.cellular,
            "network_wifi" to networkInfo.wifi
        )
        
        trackEvent(EventType.SESSION_START, properties)
        
        // Mark as not new user after first session
        if (isNewUser) {
            prefs.edit { putBoolean(KEY_IS_NEW_USER, false) }
        }
        
        // Next session will be from background
        isAppFromBackground = true
    }

    fun endSession() {
        val sessionLength = System.currentTimeMillis() - sessionStartTime
        val sessionLengthSeconds = sessionLength / 1000
        
        trackEvent(EventType.SESSION_END, mapOf(
            EventProperty.USER_ID to userId,
            EventProperty.SESSION_LENGTH to sessionLength,
            "duration_seconds" to sessionLengthSeconds
        ))
        syncEvents()
    }

    /**
     * Track a song being played
     */
    fun trackSongPlayed(
        songId: String,
        songTitle: String,
        songArtist: String,
        songDuration: Long,
        positionInQueue: Int = 0
    ) {
        Log.d(TAG, "🎵 trackSongPlayed called: '$songTitle' by $songArtist (ID: $songId)")
        
        val songPlay = SongPlay(
            songId = songId,
            songTitle = songTitle,
            songArtist = songArtist,
            startTime = System.currentTimeMillis(),
            duration = songDuration,
            positionInQueue = positionInQueue
        )
        songsPlayedInOrder.add(songPlay)

        trackEvent(EventType.SONG_PLAYED, mapOf(
            EventProperty.USER_ID to userId,
            EventProperty.SONG_ID to songId,
            EventProperty.SONG_TITLE to songTitle,
            EventProperty.SONG_ARTIST to songArtist,
            EventProperty.SONG_DURATION to songDuration,
            EventProperty.POSITION_IN_QUEUE to positionInQueue
        ))

        // Update total songs played
        val totalSongs = prefs.getInt(KEY_TOTAL_SONGS_PLAYED, 0) + 1
        prefs.edit { putInt(KEY_TOTAL_SONGS_PLAYED, totalSongs) }
        Log.d(TAG, "📊 Total songs played: $totalSongs")
    }

    /**
     * Track a song completion or skip
     */
    fun trackSongEnd(songId: String, playDuration: Long, songDuration: Long) {
        val completionPercentage = (playDuration.toFloat() / songDuration.toFloat() * 100).toInt()
        val isCompleted = completionPercentage >= 80 // Consider 80%+ as completed
        val isSkipped = !isCompleted

        val eventType = if (isCompleted) EventType.SONG_COMPLETED else EventType.SONG_SKIPPED
        val icon = if (isCompleted) "✓" else "⏭"
        val status = if (isCompleted) "COMPLETED" else "SKIPPED"
        
        Log.d(TAG, "$icon SONG $status: '$songId' (${completionPercentage}% - ${playDuration/1000}s/${songDuration/1000}s)")

        trackEvent(eventType, mapOf(
            EventProperty.USER_ID to userId,
            EventProperty.SONG_ID to songId,
            EventProperty.PLAY_DURATION to playDuration,
            EventProperty.SONG_DURATION to songDuration,
            EventProperty.COMPLETION_PERCENTAGE to completionPercentage,
            "skipped" to isSkipped,
            "duration_seconds" to (playDuration / 1000)
        ))

        // Update total listening time
        val totalListeningTime = prefs.getLong(KEY_TOTAL_LISTENING_TIME, 0) + playDuration
        prefs.edit { putLong(KEY_TOTAL_LISTENING_TIME, totalListeningTime) }
        Log.d(TAG, "⏱ Total listening time: ${totalListeningTime/1000/60} minutes")
    }

    /**
     * Track search queries
     */
    fun trackSearchQuery(searchTerm: String) {
        Log.d(TAG, "🔍 SEARCH QUERY: '$searchTerm'")
        trackEvent(EventType.SEARCH_QUERY, mapOf(
            EventProperty.USER_ID to userId,
            EventProperty.SEARCH_TERM to searchTerm
        ))
    }

    /**
     * Track app opened
     */
    fun trackAppOpened() {
        Log.d(TAG, "📱 APP OPENED - User: ${userId.take(8)}...")
        trackEvent(EventType.APP_OPENED, mapOf(
            EventProperty.USER_ID to userId
        ))
    }

    /**
     * Track app closed
     */
    fun trackAppClosed() {
        Log.d(TAG, "📱 APP CLOSED - Syncing events before exit")
        trackEvent(EventType.APP_CLOSED, mapOf(
            EventProperty.USER_ID to userId
        ))
        syncEvents()
    }

    /**
     * Generic event tracking
     */
    private fun trackEvent(eventName: String, properties: Map<String, Any>) {
        scope.launch {
            mutex.withLock {
                // Convert all property values to strings for serialization
                val stringProperties = properties.mapValues { it.value.toString() }
                
                val event = AnalyticsEvent(
                    eventName = eventName,
                    properties = stringProperties
                )
                
                // Always add to queue and persist
                eventQueue.add(event)
                savePendingEvents()
                Log.d(TAG, "Event tracked: $eventName (Queue size: ${eventQueue.size})")
                
                // If online, immediately sync to PostHog
                if (isOnline) {
                    Log.d(TAG, "Device online - syncing events immediately")
                    syncEvents()
                } else {
                    Log.d(TAG, "Device offline - event stored for later sync")
                }
            }
        }
    }

    /**
     * Sync events to PostHog when online
     * Only sends events if device is connected to internet
     * After successful sync, clears sent events to free up space
     */
    private fun syncEvents() {
        if (!isOnline) {
            Log.d(TAG, "Device offline - skipping sync")
            return
        }

        scope.launch {
            mutex.withLock {
                if (eventQueue.isEmpty()) {
                    Log.d(TAG, "No events to sync")
                    return@withLock
                }

                try {
                    val eventsToSync = eventQueue.toList()
                    Log.d(TAG, "Device online - Syncing ${eventsToSync.size} events to PostHog...")

                    var successCount = 0
                    var failCount = 0

                    eventsToSync.forEach { event ->
                        try {
                            // Convert string properties back to appropriate types for PostHog
                            val properties = event.properties.toMutableMap<String, Any>()
                            properties["timestamp"] = event.timestamp
                            
                            PostHog.capture(
                                event = event.eventName,
                                properties = properties
                            )
                            successCount++
                        } catch (e: Exception) {
                            failCount++
                            Log.e(TAG, "Error sending event: ${event.eventName}", e)
                        }
                    }

                    // Clear synced events to free up storage space
                    eventQueue.clear()
                    prefs.edit { remove(KEY_PENDING_EVENTS) }
                    
                    Log.d(TAG, "Sync complete - Success: $successCount, Failed: $failCount")
                    Log.d(TAG, "Cleared ${eventsToSync.size} sent events from storage")

                } catch (e: Exception) {
                    Log.e(TAG, "Error during sync: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Get analytics metrics
     */
    fun getTotalSongsPlayed(): Int = prefs.getInt(KEY_TOTAL_SONGS_PLAYED, 0)

    fun getTotalListeningTime(): Long = prefs.getLong(KEY_TOTAL_LISTENING_TIME, 0)

    fun getUserId(): String = userId

    fun getSongsPlayedInOrder(): List<SongPlay> = songsPlayedInOrder.toList()

    fun getPendingEventsCount(): Int = eventQueue.size

    /**
     * Force sync - useful for testing or manual sync
     */
    fun forceSync() {
        syncEvents()
    }
}

data class SongPlay(
    val songId: String,
    val songTitle: String,
    val songArtist: String,
    val startTime: Long,
    val duration: Long,
    val positionInQueue: Int
)

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val deviceName: String,
    val deviceType: String,
    val osName: String,
    val osVersion: String,
    val os: String
)

data class NetworkInfo(
    val carrier: String,
    val cellular: Boolean,
    val wifi: Boolean
)
