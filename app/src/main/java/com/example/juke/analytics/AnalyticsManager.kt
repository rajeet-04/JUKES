package com.example.juke.analytics

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.edit
import com.example.juke.BuildConfig
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import java.util.Date
import java.util.TimeZone
import java.util.UUID

class AnalyticsManager private constructor(context: Context) {

    companion object {
        private const val TAG = "AnalyticsManager"
        private const val PREFS_NAME = "analytics_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_IS_NEW_USER = "is_new_user"
        private const val KEY_FIRST_INSTALL_TIME = "first_install_time"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: AnalyticsManager? = null

        @Volatile
        private var isPostHogInitialized = false

        fun initialize(context: Context) {
            getInstance(context)
        }

        fun getInstance(context: Context): AnalyticsManager {
            val appContext = context.applicationContext
            ensurePostHogInitialized(appContext)

            return instance ?: synchronized(this) {
                instance ?: AnalyticsManager(appContext).also { instance = it }
            }
        }

        fun getInstance(): AnalyticsManager {
            return instance ?: throw IllegalStateException("AnalyticsManager not initialized")
        }

        fun getIfInitialized(): AnalyticsManager? = instance

        /** Fire-and-forget from anywhere; does nothing before analytics is set up. */
        fun log(eventName: String, properties: Map<String, Any> = emptyMap()) {
            instance?.trackEvent(eventName, properties)
        }

        private fun ensurePostHogInitialized(context: Context) {
            if (isPostHogInitialized) return

            synchronized(this) {
                if (isPostHogInitialized) return

                val config = PostHogAndroidConfig(
                    apiKey = BuildConfig.POSTHOG_API_KEY,
                    host = BuildConfig.POSTHOG_HOST
                )
                PostHogAndroid.setup(context, config)
                isPostHogInitialized = true
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val userId: String = prefs.getString(KEY_USER_ID, null) ?: generateAndStoreUserId()
    private val isNewUser: Boolean = prefs.getBoolean(KEY_IS_NEW_USER, false)
    private var sessionStartTime: Long = 0
    private var isAppFromBackground = false

    init {
        // Sent with every event by the SDK, so events themselves only carry what is specific to them.
        mapOf(
            EventProperty.USER_ID to userId,
            EventProperty.DEVICE_MANUFACTURER to Build.MANUFACTURER,
            EventProperty.DEVICE_MODEL to Build.MODEL,
            EventProperty.DEVICE_NAME to Build.DEVICE,
            EventProperty.DEVICE_TYPE to if (context.resources.configuration.smallestScreenWidthDp >= 600) "tablet" else "phone",
            EventProperty.OS to "Android ${Build.VERSION.RELEASE}",
            EventProperty.OS_NAME to "Android",
            EventProperty.OS_VERSION to Build.VERSION.RELEASE,
            EventProperty.TIMEZONE to TimeZone.getDefault().id,
            EventProperty.APP_VERSION to BuildConfig.VERSION_NAME
        ).forEach { (k, v) -> PostHog.register(k, v) }
        // One id per install is the person in PostHog, so events from the same phone group together across sessions
        PostHog.identify(
            userId,
            userProperties = mapOf(EventProperty.APP_VERSION to BuildConfig.VERSION_NAME, EventProperty.DEVICE_MODEL to Build.MODEL),
            userPropertiesSetOnce = mapOf(EventProperty.FIRST_INSTALL_TIME to prefs.getLong(KEY_FIRST_INSTALL_TIME, System.currentTimeMillis()))
        )
        startSession()
    }

    private fun generateAndStoreUserId(): String {
        val newUserId = UUID.randomUUID().toString()
        prefs.edit {
            putString(KEY_USER_ID, newUserId)
                .putBoolean(KEY_IS_NEW_USER, true)
                .putLong(KEY_FIRST_INSTALL_TIME, System.currentTimeMillis())
        }
        return newUserId
    }

    private fun networkProperties(): Map<String, Any> {
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val cellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
        return mapOf(
            EventProperty.NETWORK_CARRIER to if (cellular) telephonyManager?.networkOperatorName ?: "Unknown" else "N/A",
            EventProperty.NETWORK_CELLULAR to cellular,
            EventProperty.NETWORK_WIFI to (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false)
        )
    }

    private fun startSession() {
        sessionStartTime = System.currentTimeMillis()
        trackEvent(
            EventType.SESSION_START,
            mapOf(
                EventProperty.FROM_BACKGROUND to isAppFromBackground,
                EventProperty.IS_NEW_USER to isNewUser
            ) + networkProperties()
        )
        if (isNewUser) prefs.edit { putBoolean(KEY_IS_NEW_USER, false) }
        isAppFromBackground = true // the next session in this process is a return from background
    }

    fun endSession() {
        val sessionLength = System.currentTimeMillis() - sessionStartTime
        trackEvent(
            EventType.SESSION_END,
            mapOf(EventProperty.SESSION_LENGTH to sessionLength, EventProperty.DURATION_SECONDS to sessionLength / 1000)
        )
        PostHog.flush()
    }

    private class Playing(val id: String, val title: String, val artist: String, val durationMs: Long, val startedAt: Long)

    @Volatile private var playing: Playing? = null

    /**
     * The song that was playing has been replaced: [completed] when it ran to its end, otherwise the listener moved on.
     * Listened time is wall-clock since it started, capped at the song's length, so a long pause makes it an upper bound.
     */
    fun songTransition(completed: Boolean) {
        val p = playing ?: return
        playing = null
        val listened = (System.currentTimeMillis() - p.startedAt).coerceAtLeast(0).let { if (p.durationMs > 0) minOf(it, p.durationMs) else it }
        trackEvent(
            if (completed) EventType.SONG_COMPLETED else EventType.SONG_SKIPPED,
            mapOf(
                EventProperty.SONG_ID to p.id,
                EventProperty.SONG_TITLE to p.title,
                EventProperty.SONG_ARTIST to p.artist,
                EventProperty.SONG_DURATION to p.durationMs,
                EventProperty.LISTENED_MS to listened,
                EventProperty.COMPLETION_PERCENTAGE to if (p.durationMs > 0) (listened * 100 / p.durationMs).toInt() else 0,
                EventProperty.IN_JAM to com.example.juke.jam.JamSession.state.value.active
            )
        )
    }

    fun trackSongPlayed(
        songId: String,
        songTitle: String,
        songArtist: String,
        songDuration: Long,
        positionInQueue: Int = 0,
        isStream: Boolean = false
    ) {
        playing = Playing(songId, songTitle, songArtist, songDuration, System.currentTimeMillis())
        trackEvent(
            EventType.SONG_PLAYED,
            mapOf(
                EventProperty.SONG_ID to songId,
                EventProperty.SONG_TITLE to songTitle,
                EventProperty.SONG_ARTIST to songArtist,
                EventProperty.SONG_DURATION to songDuration,
                EventProperty.POSITION_IN_QUEUE to positionInQueue,
                EventProperty.IS_STREAM to isStream,
                EventProperty.IN_JAM to com.example.juke.jam.JamSession.state.value.active
            )
        )
    }

    fun trackSearchQuery(searchTerm: String) {
        trackEvent(EventType.SEARCH_QUERY, mapOf(EventProperty.SEARCH_TERM to searchTerm))
    }

    fun trackAppOpened() = trackEvent(EventType.APP_OPENED, emptyMap())

    fun trackAppClosed() {
        trackEvent(EventType.APP_CLOSED, emptyMap())
        PostHog.flush()
    }

    /**
     * The SDK keeps its own on-disk queue, batches uploads and retries when offline, so events go straight to it
     * with their real time and their real types (numbers stay numbers, so they can be summed and averaged).
     */
    /** The anonymous per-install id (also the PostHog person), so a report can be matched to its events. */
    fun installId(): String = userId

    fun track(eventName: String, properties: Map<String, Any> = emptyMap()) = trackEvent(eventName, properties)

    private fun trackEvent(eventName: String, properties: Map<String, Any>) {
        try {
            PostHog.capture(event = eventName, properties = properties, timestamp = Date())
        } catch (e: Exception) {
            Log.w(TAG, "Could not record $eventName", e)
        }
    }
}
