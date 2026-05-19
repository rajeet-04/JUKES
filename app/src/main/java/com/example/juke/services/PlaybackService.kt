package com.example.juke.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.example.juke.R
import com.example.juke.analytics.AnalyticsManager
import com.example.juke.database.MusicDatabase
import com.example.juke.database.toEntity
import com.example.juke.database.toTrack
import com.example.juke.models.Track
import com.example.juke.network.SpotifyApi
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Media Playback Service using Media3 (ExoPlayer) with Android Auto support.
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {

    private val TAG = "PlaybackService"

    companion object {
        private const val CUSTOM_COMMAND_TOGGLE_FAVORITE_ACTION_ID =
            "CUSTOM_COMMAND_TOGGLE_FAVORITE"
        private const val CUSTOM_COMMAND_DOWNLOAD_TRACK_ACTION_ID =
            "CUSTOM_COMMAND_DOWNLOAD_TRACK"

        /**
         * Resolve artwork for a track into MediaMetadata.
         *
         * Use artwork URIs for both remote and local images so queue operations do not clone large
         * embedded byte arrays for every MediaItem. The session bitmap loader can resolve the URI
         * lazily when artwork is actually needed for the active item/notification.
         */
        internal fun applyArtwork(metadataBuilder: MediaMetadata.Builder, thumbnailUri: String?) {
            thumbnailUri?.takeIf { it.isNotEmpty() }?.let { uriString ->
                try {
                    val artworkUri = when {
                        uriString.startsWith("http", ignoreCase = true) -> uriString.toUri()
                        uriString.startsWith("file://", ignoreCase = true) -> uriString.toUri()
                        uriString.startsWith("content://", ignoreCase = true) -> uriString.toUri()
                        else -> {
                            val file = java.io.File(uriString)
                            if (file.exists() && file.canRead() && file.length() > 0) {
                                file.toUri()
                            } else {
                                null
                            }
                        }
                    }

                    artworkUri?.let(metadataBuilder::setArtworkUri)
                } catch (e: Exception) {
                    // Silently ignore artwork errors — notification will just show no art
                }
            }
        }
    }

    /**
     * Singleton that owns the ExoPlayer stream cache for the process lifetime.
     * Removed 'private' so PlaybackManager can trigger explicit cleanup rules.
     */
    @UnstableApi
    object StreamCacheManager { // <-- Removed 'private' modifier
        private const val MAX_CACHE_BYTES = 256L * 1024 * 1024 // 256 MB

        @Volatile
        private var cache: SimpleCache? = null

        fun getCache(context: Context): SimpleCache {
            return cache ?: synchronized(this) {
                cache ?: run {
                    val cacheDir = java.io.File(context.cacheDir, "stream_cache")
                    val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
                    val databaseProvider =
                        androidx.media3.database.StandaloneDatabaseProvider(context)
                    SimpleCache(cacheDir, evictor, databaseProvider).also { cache = it }
                }
            }
        }

        // Add this to clear everything (e.g. queue replacement)
        fun clearAllCache() {
            synchronized(this) {
                cache?.let { c ->
                    c.keys.toList().forEach { key ->
                        c.removeResource(key)
                    }
                }
            }
        }

        // Add this to clear specific tracks
        fun removeTrackCache(uri: String?) {
            if (uri == null) return
            synchronized(this) {
                cache?.removeResource(uri)
            }
        }

        fun release() {
            synchronized(this) {
                cache?.release()
                cache = null
            }
        }
    }

    private var mediaSession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private lateinit var database: MusicDatabase
    private val musicService by lazy { MusicService(applicationContext) }
    private val queueManager by lazy { QueueManager.getInstance(applicationContext) }
    val audioEffectController: AudioEffectController by lazy { AudioEffectController(this) }

    // For Stream Mode cleanup and progress tracking
    private var previousTrackId: String? = null

    // Track which songs have reached 50% during this playback session
    private val tracksPlayCountedThisSession = mutableSetOf<String>()
    private var currentPlayingTrackId: String? = null
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (::player.isInitialized && player.isPlaying) {
                queueManager.checkPreFetch(player.currentPosition, player.duration)
                // Check if current track has reached 50% of total duration
                this@PlaybackService.checkPlayCountThreshold()
                progressHandler.postDelayed(this, 1000)
            }
        }
    }

    // Preference listener for skip silence 
    private val audioSettingsListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "skip_silence_enabled") {
                val isEnabled = prefs.getBoolean("skip_silence_enabled", false)
                if (::player.isInitialized) {
                    player.skipSilenceEnabled = isEnabled
                    Log.d(TAG, "Skip silence enabled: $isEnabled")
                }
            }
        }

    private var wasPlayingBeforeCall = false
    private var wasPlayingBeforeFocusLoss = false
    private lateinit var audioManager: AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var resumeRunnable: Runnable? = null

    // 1. Define the Receiver
    private val callStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                Log.d(TAG, "Phone state changed: $state")

                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        // Call coming in: Pause and save state
                        if (player.isPlaying) {
                            wasPlayingBeforeCall = true
                            player.pause()
                            Log.d(TAG, "Paused playback due to incoming call")
                        }
                    }

                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        // Call active (or outgoing call started)
                        // If user makes an outgoing call while music is playing, pause and save state
                        if (player.isPlaying) {
                            wasPlayingBeforeCall = true
                            player.pause()
                            Log.d(TAG, "Paused playback due to active/outgoing call")
                        }
                    }

                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        // Call ended: Auto resume if we were playing before
                        // IMPORTANT: Post the resume with a delay to allow the app to come to foreground
                        // This prevents ForegroundServiceStartNotAllowedException when Media3 tries to update the notification
                        if (wasPlayingBeforeCall) {
                            wasPlayingBeforeCall = false
                            // Remove any pending resume
                            resumeRunnable?.let { mainHandler.removeCallbacks(it) }
                            // Post resume with 500ms delay to ensure app is in foreground
                            resumeRunnable = Runnable {
                                if (!player.isPlaying) {
                                    try {
                                        player.play()
                                        Log.d(TAG, "Auto-resumed playback after call (delayed)")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to resume after call: ${e.message}", e)
                                    }
                                }
                            }
                            mainHandler.postDelayed(resumeRunnable!!, 500)
                        }
                    }
                }
            }
        }
    }

    // Audio focus listener to handle other apps playing audio
    private val audioFocusChangeListener =
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    // Permanent loss (another app took focus permanently)
                    if (player.isPlaying) {
                        player.pause()
                        Log.d(TAG, "Audio focus lost permanently - paused")
                    }
                    wasPlayingBeforeFocusLoss = false
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    // Temporary loss (notification, alarm, etc.)
                    if (player.isPlaying) {
                        wasPlayingBeforeFocusLoss = true
                        player.pause()
                        Log.d(TAG, "Audio focus lost temporarily - paused")
                    }
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    // Can duck (lower volume) - we'll just pause for simplicity
                    if (player.isPlaying) {
                        wasPlayingBeforeFocusLoss = true
                        player.pause()
                        Log.d(TAG, "Audio focus ducked - paused")
                    }
                }

                AudioManager.AUDIOFOCUS_GAIN -> {
                    // Regained focus - resume if we were playing before
                    // Post with a small delay to avoid conflicts with call state handling
                    if (wasPlayingBeforeFocusLoss && !wasPlayingBeforeCall) {
                        resumeRunnable?.let { mainHandler.removeCallbacks(it) }
                        resumeRunnable = Runnable {
                            try {
                                if (!player.isPlaying) {
                                    player.play()
                                    Log.d(TAG, "Audio focus regained - resumed (delayed)")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to resume on audio focus gain: ${e.message}", e)
                            }
                        }
                        mainHandler.postDelayed(resumeRunnable!!, 100)
                    }
                    wasPlayingBeforeFocusLoss = false
                }
            }
        }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Fix: Check if app is in background before attempting anything that might require foreground
        // This prevents ForegroundServiceStartNotAllowedException on Android 12+
        var isAppInForeground = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val currentState =
                    androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
                isAppInForeground =
                    currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                if (!isAppInForeground) {
                    Log.w(
                        TAG,
                        "App is in background, suppressing initial startForeground to avoid crash"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check app lifecycle state: ${e.message}")
            }
        }

        // We DO NOT startForeground here with a placeholder anymore.
        // We let MediaLibraryService (Media3) handle notification and foreground promotion 
        // when playback actually starts or a notification is explicitly requested by the session.

        return try {
            super.onStartCommand(intent, flags, startId)
        } catch (e: Exception) {
            Log.e(TAG, "Error in super.onStartCommand: ${e.message}", e)
            START_STICKY
        }
    }

    /**
     * Helper function to create validated MediaItem with artwork checking
     */
    private fun createValidatedMediaItem(track: Track): MediaItem? {
        if (track.localUri == null) return null

        // Check if it's a remote URL (http/https)
        val isRemote = track.localUri.startsWith("http", ignoreCase = true)

        // Only validate file existence if it's a local path
        if (!isRemote) {
            try {
                val uri = track.localUri.toUri()
                val file = java.io.File(uri.path ?: "")
                if (!file.exists() || !file.canRead() || file.length() <= 0) {
                    // Start of workaround for content:// URIs
                    if (!track.localUri.startsWith("content://")) {
                        return null
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error validating local file: ${e.message}")
            }
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)

        applyArtwork(metadataBuilder, track.thumbnailUri)

        return MediaItem.Builder()
            .setMediaId(track.uuid)
            .setUri(track.localUri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> Log.d(TAG, "Playback ended")
                Player.STATE_READY -> Log.d(TAG, "Player ready")
                Player.STATE_BUFFERING -> Log.d(TAG, "Buffering...")
                Player.STATE_IDLE -> Log.d(TAG, "Player idle")
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "Player error: ${error.message}", error)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Prevent infinite loops from metadata updates
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                // Clear the set of counted tracks when queue changes to ensure fresh counting
                tracksPlayCountedThisSession.clear()
                return
            }

            mediaItem?.let {
                val trackId = it.mediaId
                currentPlayingTrackId = trackId
                Log.d(TAG, "Media item transition: $trackId, reason: $reason")
                // Note: Play count is now incremented only when track reaches 50% via checkPlayCountThreshold()

                // Update custom layout (Notification Button)
                serviceScope.launch {
                    try {
                        val track = database.trackDao().getTrackByUuid(trackId)?.toTrack()
                        if (track != null) {
                            updateCustomLayout(track.isFavourite, track.isStream)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating custom layout: ${e.message}")
                    }
                }

                // Delayed metadata re-push to fix notification artwork lag.
                // Media3's DefaultMediaNotificationProvider fetches artwork asynchronously
                // from the MediaItem's artworkUri at the moment of transition. If the
                // thumbnail file isn't ready yet (e.g., stream track with HTTP thumb,
                // or thumb was still downloading), the notification shows blank/stale art.
                // Re-pushing the same metadata 3 seconds later forces a redraw with the
                // correct thumbnail once it's available.
                serviceScope.launch {
                    try {
                        kotlinx.coroutines.delay(3_000L)
                        // Only refresh if this track is still playing (user hasn't skipped)
                        if (currentPlayingTrackId != trackId) return@launch
                        val track = database.trackDao().getTrackByUuid(trackId)?.toTrack()
                            ?: return@launch
                        val index = (0 until player.mediaItemCount).firstOrNull { i ->
                            player.getMediaItemAt(i).mediaId == trackId
                        } ?: return@launch
                        val refreshedItem = createValidatedMediaItem(track) ?: return@launch
                        // replaceMediaItem triggers onTimelineChanged → notification redraw
                        player.replaceMediaItem(index, refreshedItem)
                        Log.d(TAG, "Refreshed notification metadata for: ${track.title}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Delayed metadata refresh failed: ${e.message}")
                    }
                }

                // Cleanup previous track if it was a stream
                val oldTrackId = previousTrackId
                previousTrackId = trackId

                if (oldTrackId != null && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    serviceScope.launch {
                        try {
                            // Try QueueManager's in-memory queue first (works for streams not in DB)
                            val oldTrack =
                                queueManager.currentQueue.value.find { it.uuid == oldTrackId }
                                    ?: database.trackDao().getTrackByUuid(oldTrackId)?.toTrack()

                            if (oldTrack != null && oldTrack.isStream) {
                                // Wait 3 seconds before cleaning up the finished stream file.
                                // ExoPlayer's CacheDataSource may still be draining its read
                                // handle on the old file (closing buffers) at transition time.
                                kotlinx.coroutines.delay(3_000L)

                                // Clear ExoPlayer's overlay cache entry for this URI
                                StreamCacheManager.removeTrackCache(oldTrack.localUri)
                                Log.d(
                                    TAG,
                                    "Cleared ExoPlayer cache for finished stream: ${oldTrack.title}"
                                )
                                // Note: Stream file deletion is handled by LRU eviction in MusicService
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error cleaning up stream track: ${e.message}")
                        }
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "Is playing: $isPlaying")
            if (isPlaying) {
                progressHandler.post(progressRunnable)
            } else {
                progressHandler.removeCallbacks(progressRunnable)
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Create notification channel for Android 8+
        // IMPORTANT: Must be created before Media3 initializes to avoid notification conflicts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "media_playback",
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media playback controls"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        // Configure Media3 to use the same Notification ID and Channel
        // This prevents notification conflicts on Samsung and other devices
        val notificationProvider = DefaultMediaNotificationProvider.Builder(applicationContext)
            .setNotificationId(1) // IMPORTANT: Must match the ID in onStartCommand
            .setChannelId("media_playback")
            .build()
        setMediaNotificationProvider(notificationProvider)

        database = MusicDatabase.getDatabase(applicationContext)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true)

        // Build a CacheDataSource.Factory for HTTP streams only.
        // IMPORTANT: Local file URIs (stream files already on disk) must NOT be routed
        // through ExoPlayer's cache layer — doing so causes stale cache hits after the
        // stream file is rewritten on a 403 refresh, which manifests as playback pausing
        // or reading corrupted/old data. FLAG_IGNORE_CACHE_FOR_UNRECOGNIZED_CONTENT_TYPE
        // combined with FLAG_IGNORE_CACHE_ON_ERROR ensures we fall-through cleanly.
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
        val upstreamDataSourceFactory =
            DefaultDataSource.Factory(applicationContext, httpDataSourceFactory)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(StreamCacheManager.getCache(applicationContext))
            .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
            // Cache errors are non-fatal — fall through to the network.
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)

        // Balanced LoadControl: 30s min buffer / 120s max buffer.
        // The previous 600s max was causing ExoPlayer to stall — it attempted to buffer
        // 10 minutes ahead but couldn't fill it from a local file fast enough, causing
        // the player to enter STATE_BUFFERING and appear to "pause" with no content.
        // For local file playback 30–120s is more than sufficient and stays responsive.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000,  // minBufferMs
                120_000, // maxBufferMs (2 minutes ahead — enough without stalling)
                1_500,   // bufferForPlaybackMs
                3_000    // bufferForPlaybackAfterRebufferMs
            )
            .setBackBuffer(
                30_000, // backBufferDurationMs: 30s back-buffer for smooth seeking
                true    // retainBackBufferFromKeyframe
            )
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, false) // Keep FALSE to allow manual call control
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl) // <-- Apply the LoadControl here
            .build()

        // Initialize Skip Silence from Preferences
        val prefs = getSharedPreferences("audio_effects_prefs", MODE_PRIVATE)
        player.skipSilenceEnabled = prefs.getBoolean("skip_silence_enabled", false)
        prefs.registerOnSharedPreferenceChangeListener(audioSettingsListener)

        // Request audio focus when player starts playing
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioManager.requestAudioFocus(
                            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                                .setAudioAttributes(
                                    android.media.AudioAttributes.Builder()
                                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                        .build()
                                )
                                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                                .build()
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.requestAudioFocus(
                            audioFocusChangeListener,
                            AudioManager.STREAM_MUSIC,
                            AudioManager.AUDIOFOCUS_GAIN
                        )
                    }

                    if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        Log.w(TAG, "Audio focus not granted")
                    }
                }
            }
        })

        // Listen for audio session ID changes to attach audio effects
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                Log.d(TAG, "Audio session ID changed: $audioSessionId")
                audioEffectController.attachToAudioSession(audioSessionId)
            }
        })

        player.addListener(playerListener)

        val sessionActivityPendingIntent = packageManager
            ?.getLaunchIntentForPackage(packageName)
            ?.let { sessionIntent ->
                // FIX: Add these flags to prevent the app from restarting
                sessionIntent.flags =
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                sessionIntent.putExtra("open_player", true)
                PendingIntent.getActivity(
                    this,
                    0,
                    sessionIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

        val bitmapLoader = DataSourceBitmapLoader(this)

        mediaSession = MediaLibrarySession.Builder(this, player, MediaLibrarySessionCallback())
            .apply {
                sessionActivityPendingIntent?.let { setSessionActivity(it) }
            }
            .setBitmapLoader(bitmapLoader)
            .setShowPlayButtonIfPlaybackIsSuppressed(true)
            .build()

        Log.d(TAG, "PlaybackService created")

        // 2. Register the Receiver safely
        try {
            val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            registerReceiver(callStateReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register call state receiver: ${e.message}")
        }
        // Collect favourite changes from the shared bus and update the notification icon.
        // This fires whether the toggle came from the notification itself OR from the player UI.
        serviceScope.launch {
            PlaybackManager.getInstance(applicationContext).favouriteChangedFlow.collect { (_, isFavourite) ->
                withContext(Dispatchers.Main) {
                    updateCustomLayout(isFavourite, isStream = false)
                }
            }
        }

        // Collect track promotions (stream → download) triggered from the in-app player UI.
        // Updates the notification layout to hide the Download button for the current track.
        serviceScope.launch {
            PlaybackManager.getInstance(applicationContext).trackPromotedFlow.collect { uuid ->
                val currentId = player.currentMediaItem?.mediaId
                if (currentId == uuid) {
                    val track = withContext(Dispatchers.IO) {
                        database.trackDao().getTrackByUuid(uuid)?.toTrack()
                    }
                    if (track != null) {
                        withContext(Dispatchers.Main) {
                            updateCustomLayout(isFavorite = track.isFavourite, isStream = false)
                        }
                    }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "App removed from recents, stopping service and playback")
        player.pause()
        player.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    @OptIn(UnstableApi::class)
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // On Android 12+ (API 31), starting a foreground service from the background is
        // restricted and throws ForegroundServiceStartNotAllowedException.
        //
        // Media3's default onUpdateNotification() → MediaNotificationManager.startForeground()
        // calls BOTH ContextCompat.startForegroundService() AND Service.startForeground() —
        // even when startInForegroundRequired=false. Both are fatal when in the background.
        //
        // Strategy: return early (skip super) when app is in background.
        // The existing notification remains visible; it will be refreshed when the user
        // brings the app back to the foreground and normal playback resumes.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && startInForegroundRequired) {
            try {
                val currentState =
                    androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
                val isAppInForeground =
                    currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)

                if (!isAppInForeground) {
                    Log.w(
                        TAG,
                        "App is in background — skipping onUpdateNotification to prevent ForegroundServiceStartNotAllowedException"
                    )
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check app lifecycle state: ${e.message}")
            }
        }

        try {
            super.onUpdateNotification(session, startInForegroundRequired)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is android.app.ForegroundServiceStartNotAllowedException
            ) {
                Log.w(
                    TAG,
                    "Caught ForegroundServiceStartNotAllowedException in onUpdateNotification — suppressing"
                )
            } else {
                Log.w(TAG, "Failed to update notification: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        // Clean up pending resume operations
        resumeRunnable?.let { mainHandler.removeCallbacks(it) }
        resumeRunnable = null

        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        audioEffectController.release()

        // 3. Unregister to prevent leaks
        try {
            unregisterReceiver(callStateReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }

        // Release the stream cache
        StreamCacheManager.release()

        super.onDestroy()
        Log.d(TAG, "PlaybackService destroyed")
    }

    override fun startForegroundService(service: Intent?): ComponentName? {
        return try {
            super.startForegroundService(service)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is android.app.ForegroundServiceStartNotAllowedException
            ) {
                Log.e(
                    TAG,
                    "Caught ForegroundServiceStartNotAllowedException in startForegroundService",
                    e
                )
                null
            } else {
                throw e
            }
        }
    }

    override fun startService(service: Intent?): ComponentName? {
        return try {
            super.startService(service)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is android.app.ForegroundServiceStartNotAllowedException
            ) {
                Log.e(TAG, "Caught ForegroundServiceStartNotAllowedException in startService", e)
                null
            } else if (e is IllegalStateException && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.e(TAG, "Caught IllegalStateException in startService (background app)", e)
                null
            } else {
                throw e
            }
        }
    }

    /**
     * Helper to update custom layout (Favorite or Download button)
     */
    private fun updateCustomLayout(isFavorite: Boolean, isStream: Boolean) {
        val button = if (isStream) {
            CommandButton.Builder()
                .setDisplayName("Download")
                .setIconResId(R.drawable.baseline_download_24)
                .setSessionCommand(
                    SessionCommand(
                        CUSTOM_COMMAND_DOWNLOAD_TRACK_ACTION_ID,
                        Bundle.EMPTY
                    )
                )
                .build()
        } else {
            val iconResId =
                if (isFavorite) R.drawable.baseline_favorite_24 else R.drawable.baseline_favorite_border_24
            CommandButton.Builder()
                .setDisplayName("Favorite")
                .setIconResId(iconResId)
                .setSessionCommand(
                    SessionCommand(
                        CUSTOM_COMMAND_TOGGLE_FAVORITE_ACTION_ID,
                        Bundle.EMPTY
                    )
                )
                .build()
        }

        mediaSession?.setCustomLayout(listOf(button))
    }

    /**
     * Check if the current track has reached 50% of its duration and increment play count if so.
     * Each track is only counted once per playback session.
     */
    private fun checkPlayCountThreshold() {
        val trackId = currentPlayingTrackId ?: return
        // Skip if already counted in this session
        if (trackId in tracksPlayCountedThisSession) return

        val duration = player.duration
        val position = player.currentPosition

        // Check if duration is known and position exceeds 50%
        if (duration > 0 && position > 0 && position >= duration / 2) {
            tracksPlayCountedThisSession.add(trackId)
            serviceScope.launch {
                try {
                    val now = SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                        Locale.US
                    ).format(Date())
                    database.trackDao().incrementPlayCount(trackId, now)
                    Log.d(TAG, "Play count incremented at 50% threshold for track: $trackId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error incrementing play count at 50% threshold: ${e.message}", e)
                }
            }
        }
    }

    /**
     * MediaLibrarySession callback for Android Auto browsing support
     */
    private inner class MediaLibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    .add(
                        SessionCommand(
                            CUSTOM_COMMAND_TOGGLE_FAVORITE_ACTION_ID,
                            Bundle.EMPTY
                        )
                    )
                    .add(
                        SessionCommand(
                            CUSTOM_COMMAND_DOWNLOAD_TRACK_ACTION_ID,
                            Bundle.EMPTY
                        )
                    )
                    .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                )
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CUSTOM_COMMAND_TOGGLE_FAVORITE_ACTION_ID) {
                val currentTrackId = player.currentMediaItem?.mediaId
                if (currentTrackId != null) {
                    serviceScope.launch {
                        try {
                            val track =
                                database.trackDao().getTrackByUuid(currentTrackId)?.toTrack()
                            if (track != null) {
                                val newStatus = !track.isFavourite
                                database.trackDao().updateTrackFavourite(track.uuid, newStatus)
                                // Emit to the shared bus so both the notification icon AND
                                // MusicViewModel UI state are updated in real time.
                                PlaybackManager.getInstance(applicationContext)
                                    .emitFavouriteChanged(track.uuid, newStatus)
                                Log.d(TAG, "Toggled favorite via notification: $newStatus")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing favorite command: ${e.message}")
                        }
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            } else if (customCommand.customAction == CUSTOM_COMMAND_DOWNLOAD_TRACK_ACTION_ID) {
                val currentTrackId = player.currentMediaItem?.mediaId
                if (currentTrackId != null) {
                    serviceScope.launch {
                        try {
                            val track =
                                database.trackDao().getTrackByUuid(currentTrackId)?.toTrack()
                            if (track != null && track.isStream) {
                                val promotedTrack = withContext(Dispatchers.IO) {
                                    musicService.promoteStreamToDownload(track)
                                }
                                // Update ExoPlayer queue and QueueManager — same as promoteTrackToDownload in MusicViewModel
                                PlaybackManager.getInstance(applicationContext)
                                    .replaceTrackInQueue(track.uuid, promotedTrack, seamlessIfPlaying = true)
                                queueManager.replaceTrackInQueue(track.uuid, promotedTrack)
                                updateCustomLayout(isFavorite = promotedTrack.isFavourite, isStream = false)
                                // Signal MusicViewModel so it re-fetches from DB and updates _uiState
                                PlaybackManager.getInstance(applicationContext)
                                    .emitTrackPromoted(track.uuid)
                                Log.d(TAG, "Promoted stream to download from notification: ${promotedTrack.title}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error promoting track from notification: ${e.message}")
                        }
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(
                LibraryResult.ofItem(
                    MediaItem.Builder()
                        .setMediaId("root")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .setTitle("JUKE")
                                .build()
                        )
                        .build(),
                    params
                )
            )
        }

        @OptIn(UnstableApi::class)
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return when (parentId) {
                "root" -> {
                    // Root menu categories
                    val items = ImmutableList.of(
                        buildBrowsableItem("recent", "Recently Played"),
                        buildBrowsableItem("favorites", "Favorites"),
                        buildBrowsableItem("all_tracks", "All Tracks")
                    )
                    Futures.immediateFuture(LibraryResult.ofItemList(items, params))
                }

                "recent" -> loadRecentTracks(params)
                "favorites" -> loadFavorites(params)
                "all_tracks" -> loadAllTracks(params)
                else -> Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            }
        }

        @OptIn(UnstableApi::class)
        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return serviceScope.async {
                try {
                    val track = database.trackDao().getTrackByUuid(mediaId)?.toTrack()
                    if (track != null) {
                        LibraryResult.ofItem(buildPlayableMediaItem(track), null)
                    } else {
                        LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting item: ${e.message}")
                    LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                }
            }.asListenableFuture()
        }

        private fun buildBrowsableItem(mediaId: String, title: String): MediaItem {
            return MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle(title)
                        .build()
                )
                .build()
        }

        private fun buildPlayableMediaItem(track: Track): MediaItem {
            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setIsBrowsable(false)
                .setIsPlayable(true)

            applyArtwork(metadataBuilder, track.thumbnailUri)

            return MediaItem.Builder()
                .setMediaId(track.uuid)
                .setUri(track.localUri)
                .setMediaMetadata(metadataBuilder.build())
                .build()
        }

        @OptIn(UnstableApi::class)
        private fun loadRecentTracks(params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceScope.async {
                try {
                    val tracks = database.trackDao().getRecentlyPlayed(20)
                    val items = tracks.map { buildPlayableMediaItem(it.toTrack()) }
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading recent tracks: ${e.message}")
                    LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                }
            }.asListenableFuture()
        }

        @OptIn(UnstableApi::class)
        private fun loadFavorites(params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceScope.async {
                try {
                    val tracks = database.trackDao().getFavourites()
                    val items = tracks.map { buildPlayableMediaItem(it.toTrack()) }
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading favorites: ${e.message}")
                    LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                }
            }.asListenableFuture()
        }

        @OptIn(UnstableApi::class)
        private fun loadAllTracks(params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceScope.async {
                try {
                    val tracks = database.trackDao().getDownloadedTracks()
                    val items = tracks.map { buildPlayableMediaItem(it.toTrack()) }
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading all tracks: ${e.message}")
                    LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                }
            }.asListenableFuture()
        }

        /**
         * Bridges coroutine-based library queries to the Media3 callback API.
         *
         * MediaLibrarySession callbacks are expected to return `ListenableFuture`, while the
         * implementation uses coroutines internally.
         */
        @kotlin.OptIn(ExperimentalCoroutinesApi::class)
        private fun <T> kotlinx.coroutines.Deferred<T>.asListenableFuture(): ListenableFuture<T> {
            val deferred = this
            return com.google.common.util.concurrent.SettableFuture.create<T>().apply {
                deferred.invokeOnCompletion { exception ->
                    if (exception != null) {
                        setException(exception)
                    } else {
                        set(deferred.getCompleted())
                    }
                }
            }
        }
    }
}

/**
 * Playback Manager for controlling media playback.
 * This connects to PlaybackService via MediaController to enable notification controls.
 */
class PlaybackManager private constructor(private val context: Context) {

    companion object {
        @SuppressLint("StaticFieldLeak")
        @field:Volatile
        private var INSTANCE: PlaybackManager? = null

        fun getInstance(context: Context): PlaybackManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlaybackManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val TAG = "PlaybackManager"
    private val prefs = context.getSharedPreferences("playback_state_prefs", Context.MODE_PRIVATE)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var playerListener: Player.Listener? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val database: MusicDatabase = MusicDatabase.getDatabase(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val musicService: MusicService by lazy { MusicService(context) }

    // Flow to emit current track UUID changes
    private val _currentTrackId = MutableStateFlow<String?>(null)
    val currentTrackIdFlow: StateFlow<String?> = _currentTrackId.asStateFlow()

    // Flow to indicate if restored state is available
    private val _hasRestoredState = MutableStateFlow(false)
    val hasRestoredState: StateFlow<Boolean> = _hasRestoredState.asStateFlow()

    // Flow to emit current queue index
    private val _currentQueueIndex = MutableStateFlow(0)
    val currentQueueIndexFlow: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    // Flow to emit current queue structure (list of Media IDs/UUIDs)
    private val _queueFlow = MutableStateFlow<List<String>>(emptyList())
    val queueFlow: StateFlow<List<String>> = _queueFlow.asStateFlow()

    // Sleep timer state
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private val _sleepTimerRemaining = MutableStateFlow<Long?>(null)
    val sleepTimerRemaining: StateFlow<Long?> = _sleepTimerRemaining.asStateFlow()

    // Audio effect controller
    val audioEffectController: AudioEffectController by lazy { AudioEffectController(context) }

    // Shuffle state
    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabledFlow: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    // Repeat state
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatModeFlow: StateFlow<Int> = _repeatMode.asStateFlow()

    // Shared bus for favourite toggle events (uuid to newIsFavourite).
    // Both PlaybackService (notification) and MusicViewModel (player UI) emit here,
    // and both collect here, so they stay in sync without polling the DB.
    private val _favouriteChanged =
        MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 8)
    val favouriteChangedFlow: SharedFlow<Pair<String, Boolean>> = _favouriteChanged.asSharedFlow()

    fun emitFavouriteChanged(uuid: String, isFavourite: Boolean) {
        _favouriteChanged.tryEmit(uuid to isFavourite)
    }

    // Shared bus for stream-to-download promotions (emits promoted track UUID).
    // Notification handler emits here so MusicViewModel can update _uiState.
    // MusicViewModel emits here so PlaybackService can update the notification layout.
    private val _trackPromoted = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val trackPromotedFlow: SharedFlow<String> = _trackPromoted.asSharedFlow()

    fun emitTrackPromoted(uuid: String) {
        _trackPromoted.tryEmit(uuid)
    }

    // Queue manager for recommendations
    private val queueManager: QueueManager by lazy { QueueManager.getInstance(context) }
    private val streamRecoveryAttempts = mutableMapOf<String, Int>()
    private val maxStreamRecoveryAttempts = 2

    /**
     * Helper function to create validated MediaItem with artwork checking
     */
    @OptIn(UnstableApi::class)
    private fun createValidatedMediaItem(track: Track): MediaItem? {
        if (track.localUri == null) return null

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)

        PlaybackService.applyArtwork(metadataBuilder, track.thumbnailUri)

        return MediaItem.Builder()
            .setMediaId(track.uuid)
            .setUri(track.localUri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    @OptIn(UnstableApi::class)
    fun initialize() {
        if (controllerFuture == null) {
            val sessionToken =
                SessionToken(context, ComponentName(context, PlaybackService::class.java))
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture?.addListener(
                {
                    controller = controllerFuture?.get()
                    Log.d(TAG, "MediaController connected to PlaybackService")

                    // Immediately sync UI with current background state
                    controller?.let { ctrl ->
                        _isPlaying.value = ctrl.isPlaying
                        val count = ctrl.mediaItemCount
                        val currentQueue =
                            (0 until count).map { i -> ctrl.getMediaItemAt(i).mediaId }
                        _queueFlow.value = currentQueue
                        _currentQueueIndex.value = ctrl.currentMediaItemIndex
                        _currentTrackId.value = ctrl.currentMediaItem?.mediaId
                    }

                    // Add a Player.Listener on the controller's underlying player
                    playerListener = object : Player.Listener {
                        override fun onTimelineChanged(
                            timeline: androidx.media3.common.Timeline,
                            reason: Int
                        ) {
                            super.onTimelineChanged(timeline, reason)
                            // Update queue flow when timeline changes (add/remove/move)
                            controller?.let { ctrl ->
                                val count = ctrl.mediaItemCount
                                val newScan = (0 until count).map { i ->
                                    ctrl.getMediaItemAt(i).mediaId
                                }
                                _queueFlow.value = newScan

                                // Also update queue index as it might have shifted
                                val currentIndex = ctrl.currentMediaItemIndex
                                if (currentIndex != _currentQueueIndex.value) {
                                    _currentQueueIndex.value = currentIndex
                                }

                                Log.d(
                                    TAG,
                                    "Timeline changed (reason=$reason), updated queue flow with ${newScan.size} items"
                                )
                            }
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _isPlaying.value = isPlaying
                            Log.d(TAG, "PlayerListener onIsPlayingChanged: $isPlaying")
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            Log.d(TAG, "PlayerListener playbackStateChanged: $playbackState")
                            if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                                savePlaybackState()
                            }
                        }

                        override fun onPlayerErrorChanged(error: androidx.media3.common.PlaybackException?) {
                            if (error != null) {
                                Log.e(TAG, "Player error: ${error.message}", error)

                                // Check if it's a file not found error (deleted track)
                                val errorMessage = error.message ?: ""
                                val causeMessage = error.cause?.message ?: ""

                                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                                    errorMessage.contains("ENOENT") ||
                                    errorMessage.contains("FileNotFoundException") ||
                                    errorMessage.contains("No such file or directory") ||
                                    causeMessage.contains("ENOENT") ||
                                    causeMessage.contains("FileNotFoundException")
                                ) {

                                    Log.w(
                                        TAG,
                                        "Track file not found (likely deleted), skipping to next track"
                                    )

                                    // Skip to next track if available
                                    controller?.let { ctrl ->
                                        if (ctrl.hasNextMediaItem()) {
                                            ctrl.seekToNext()
                                            ctrl.prepare()
                                            ctrl.play()
                                        } else {
                                            // No next track, stop playback
                                            ctrl.stop()
                                            Log.d(TAG, "No next track available, stopping playback")
                                        }
                                    }

                                } else if (
                                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS &&
                                    (causeMessage.contains("403") || errorMessage.contains("403"))
                                ) {
                                    val trackId = controller?.currentMediaItem?.mediaId ?: return
                                    Log.w(
                                        TAG,
                                        "Stream URL expired (403) for track $trackId, refreshing..."
                                    )

                                    scope.launch {
                                        try {
                                            val trackEntity =
                                                database.trackDao().getTrackByUuid(trackId)
                                            val track = trackEntity?.toTrack()

                                            if (track == null) {
                                                Log.w(
                                                    TAG,
                                                    "Cannot handle 403: track $trackId not found in DB — skipping"
                                                )
                                                withContext(Dispatchers.Main) {
                                                    controller?.let { ctrl ->
                                                        if (ctrl.hasNextMediaItem()) {
                                                            ctrl.seekToNext()
                                                            ctrl.prepare()
                                                            ctrl.play()
                                                        } else ctrl.stop()
                                                    }
                                                }
                                                return@launch
                                            }

                                            if (!track.isStream) {
                                                // ──────────────────────────────────────────────────────────
                                                // NON-STREAM track: the file was supposed to be local/
                                                // downloaded but it 403'd (file missing or remote URL
                                                // expired). Re-fetch from Spotify if online, else skip.
                                                // ──────────────────────────────────────────────────────────
                                                Log.w(
                                                    TAG,
                                                    "Non-stream track '${track.title}' got 403 (file missing?). " +
                                                            "spotifyId=${track.spotifyId}"
                                                )

                                                val isOffline = run {
                                                    val cm =
                                                        context.getSystemService(Context.CONNECTIVITY_SERVICE)
                                                                as android.net.ConnectivityManager
                                                    val network = cm.activeNetwork
                                                    val caps =
                                                        if (network != null) cm.getNetworkCapabilities(
                                                            network
                                                        ) else null
                                                    caps == null || !caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                                }
                                                if (isOffline) {
                                                    // Offline — can't re-download, skip gracefully
                                                    Log.w(
                                                        TAG,
                                                        "Device offline — skipping '${track.title}'"
                                                    )
                                                    withContext(Dispatchers.Main) {
                                                        controller?.let { ctrl ->
                                                            if (ctrl.hasNextMediaItem()) {
                                                                ctrl.seekToNext()
                                                                ctrl.prepare()
                                                                ctrl.play()
                                                            } else ctrl.stop()
                                                        }
                                                    }
                                                    return@launch
                                                }

                                                if (track.spotifyId == null) {
                                                    Log.w(
                                                        TAG,
                                                        "No spotifyId for '${track.title}' — skipping"
                                                    )
                                                    withContext(Dispatchers.Main) {
                                                        controller?.let { ctrl ->
                                                            if (ctrl.hasNextMediaItem()) {
                                                                ctrl.seekToNext()
                                                                ctrl.prepare()
                                                                ctrl.play()
                                                            } else ctrl.stop()
                                                        }
                                                    }
                                                    return@launch
                                                }

                                                try {
                                                    Log.d(
                                                        TAG,
                                                        "Re-downloading '${track.title}' (spotifyId=${track.spotifyId})"
                                                    )
                                                    val song = SpotifyApi.spotifyTrackToSong(
                                                        SpotifyApi.getTrack(track.spotifyId)
                                                    )
                                                    val redownloadedTrack =
                                                        musicService.smartDownloadAndIndex(song)
                                                    Log.d(
                                                        TAG,
                                                        "Re-downloaded '${track.title}' successfully"
                                                    )

                                                    withContext(Dispatchers.Main) {
                                                        replaceTrackInQueue(
                                                            track.uuid,
                                                            redownloadedTrack
                                                        )
                                                        controller?.prepare()
                                                        controller?.play()
                                                    }
                                                } catch (downloadEx: Exception) {
                                                    Log.e(
                                                        TAG,
                                                        "Re-download failed for '${track.title}': ${downloadEx.message}"
                                                    )
                                                    withContext(Dispatchers.Main) {
                                                        controller?.let { ctrl ->
                                                            if (ctrl.hasNextMediaItem()) {
                                                                ctrl.seekToNext()
                                                                ctrl.prepare()
                                                                ctrl.play()
                                                            } else ctrl.stop()
                                                        }
                                                    }
                                                }
                                                return@launch
                                            }

                                            // ──────────────────────────────────────────────────────────
                                            // STREAM track: refresh the expired URL, then optionally
                                            // promote to a local download if it belongs to a playlist.
                                            // ──────────────────────────────────────────────────────────
                                            if (track.spotifyId == null) {
                                                Log.w(
                                                    TAG,
                                                    "Cannot refresh stream: missing spotifyId for '${track.title}' — skipping"
                                                )
                                                withContext(Dispatchers.Main) {
                                                    controller?.let { ctrl ->
                                                        if (ctrl.hasNextMediaItem()) {
                                                            ctrl.seekToNext()
                                                            ctrl.prepare()
                                                            ctrl.play()
                                                        } else ctrl.stop()
                                                    }
                                                }
                                                return@launch
                                            }

                                            val refreshedSong = SpotifyApi.spotifyTrackToSong(
                                                SpotifyApi.getTrack(track.spotifyId)
                                            )
                                            val refreshedTrack = musicService.streamTrack(
                                                refreshedSong,
                                                preferredUuid = track.uuid
                                            )
                                            Log.d(
                                                TAG,
                                                "Rebuilt Spotmate stream file for ${track.title}"
                                            )

                                            // Swap the media item in the queue and resume (must be on main thread)
                                            withContext(Dispatchers.Main) {
                                                replaceTrackInQueue(track.uuid, refreshedTrack)
                                                controller?.prepare()
                                                controller?.play()
                                            }

                                            // If this track belongs to a playlist, promote it to a
                                            // local download in the background so it's offline-ready
                                            val playlists = database.playlistDao()
                                                .getPlaylistsForTrack(track.uuid)
                                            if (playlists.isNotEmpty()) {
                                                Log.d(
                                                    TAG,
                                                    "Track is in ${playlists.size} playlist(s) — scheduling background download"
                                                )
                                                scope.launch {
                                                    try {
                                                        val downloadedTrack =
                                                            musicService.promoteStreamToDownload(
                                                                refreshedTrack
                                                            )
                                                        withContext(Dispatchers.Main) {
                                                            replaceTrackInQueue(
                                                                track.uuid,
                                                                downloadedTrack
                                                            )
                                                        }
                                                        Log.d(
                                                            TAG,
                                                            "Promoted stream to download: ${downloadedTrack.title}"
                                                        )
                                                    } catch (e: Exception) {
                                                        Log.e(
                                                            TAG,
                                                            "Background download after stream refresh failed: ${e.message}"
                                                        )
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(
                                                TAG,
                                                "Failed to handle 403 for track $trackId: ${e.message}",
                                                e
                                            )
                                            // Fall back to skipping to the next track
                                            withContext(Dispatchers.Main) {
                                                controller?.let { ctrl ->
                                                    if (ctrl.hasNextMediaItem()) {
                                                        ctrl.seekToNext()
                                                        ctrl.prepare()
                                                        ctrl.play()
                                                    } else {
                                                        ctrl.stop()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val trackId = controller?.currentMediaItem?.mediaId
                                    if (trackId == null) return

                                    scope.launch {
                                        try {
                                            val trackEntity =
                                                database.trackDao().getTrackByUuid(trackId)
                                            val track = trackEntity?.toTrack() ?: return@launch

                                            if (!track.isStream || track.spotifyId == null) {
                                                return@launch
                                            }

                                            val attempts =
                                                (streamRecoveryAttempts[trackId] ?: 0) + 1
                                            if (attempts > maxStreamRecoveryAttempts) {
                                                Log.w(
                                                    TAG,
                                                    "Stream recovery exceeded for ${track.title}, skipping to next"
                                                )
                                                withContext(Dispatchers.Main) {
                                                    controller?.let { ctrl ->
                                                        if (ctrl.hasNextMediaItem()) {
                                                            ctrl.seekToNext()
                                                            ctrl.prepare()
                                                            ctrl.play()
                                                        } else {
                                                            ctrl.stop()
                                                        }
                                                    }
                                                }
                                                return@launch
                                            }

                                            streamRecoveryAttempts[trackId] = attempts
                                            Log.w(
                                                TAG,
                                                "Recovering stream after source error for ${track.title} (attempt $attempts/$maxStreamRecoveryAttempts)"
                                            )

                                            val refreshedSong = SpotifyApi.spotifyTrackToSong(
                                                SpotifyApi.getTrack(track.spotifyId)
                                            )
                                            val refreshedTrack = musicService.streamTrack(
                                                refreshedSong,
                                                preferredUuid = track.uuid
                                            )

                                            withContext(Dispatchers.Main) {
                                                replaceTrackInQueue(track.uuid, refreshedTrack)
                                                controller?.prepare()
                                                controller?.play()
                                            }
                                        } catch (recoveryEx: Exception) {
                                            Log.e(
                                                TAG,
                                                "Generic stream recovery failed: ${recoveryEx.message}",
                                                recoveryEx
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            mediaItem?.let { item ->
                                val trackId = item.mediaId
                                _currentTrackId.value = trackId
                                streamRecoveryAttempts.remove(trackId)

                                // Update the queue index immediately
                                val currentIndex = controller?.currentMediaItemIndex ?: 0
                                _currentQueueIndex.value = currentIndex

                                Log.d(TAG, "Media item transition: $trackId at index $currentIndex")
                                savePlaybackState()

                                // Save queue structure to persist auto-added songs
                                scope.launch {
                                    saveQueueStructure()
                                }


                                // Track song play in analytics
                                scope.launch {
                                    try {
                                        Log.d(TAG, "Analytics: Looking up track with ID: $trackId")
                                        val track =
                                            database.trackDao().getTrackByUuid(trackId)?.toTrack()
                                        if (track != null) {
                                            Log.d(
                                                TAG,
                                                "Analytics: Found track '${track.title}' by ${track.artist}, calling trackSongPlayed"
                                            )
                                            AnalyticsManager.getInstance(context).trackSongPlayed(
                                                songId = "${track.title} - ${track.artist}",
                                                songTitle = track.title,
                                                songArtist = track.artist,
                                                songDuration = track.durationSec * 1000L,
                                                positionInQueue = currentIndex
                                            )
                                        } else {
                                            Log.w(
                                                TAG,
                                                "Analytics: Track not found in database for ID: $trackId"
                                            )
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error tracking song play: ${e.message}", e)
                                    }
                                }
                            }
                        }

                        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                            // ExoPlayer's native shuffle is never intentionally enabled.
                            // Shuffle is handled by pre-shuffling the track list before setQueue.
                            Log.d(TAG, "Shuffle mode changed (internal): $shuffleModeEnabled")
                        }

                        override fun onRepeatModeChanged(repeatMode: Int) {
                            _repeatMode.value = repeatMode
                            Log.d(TAG, "Repeat mode changed: $repeatMode")
                        }
                    }
                    try {
                        controller?.addListener(playerListener!!)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to add player listener: ${e.message}")
                    }

                    // Start a small polling loop as a fallback to ensure external changes
                    scope.launch {
                        var last = _isPlaying.value
                        while (controller != null) {
                            try {
                                val playing = withContext(Dispatchers.Main) {
                                    controller?.isPlaying == true
                                }

                                if (playing != last) {
                                    _isPlaying.value = playing
                                    Log.d(TAG, "Polled controller isPlaying: $playing")
                                    last = playing
                                }

                                // Also poll index to ensure sync
                                val currentIndex = withContext(Dispatchers.Main) {
                                    controller?.currentMediaItemIndex ?: 0
                                }
                                if (currentIndex != _currentQueueIndex.value) {
                                    _currentQueueIndex.value = currentIndex
                                    Log.d(TAG, "Polled controller index updated: $currentIndex")
                                }

                                // Save position every 5 seconds when playing
                                if (playing) {
                                    withContext(Dispatchers.Main) {
                                        savePlaybackState()
                                    }
                                }
                                kotlinx.coroutines.delay(5000)
                            } catch (e: Exception) {
                                Log.w(TAG, "Polling loop error: ${e.message}")
                                break
                            }
                        }
                    }

                    // Restore saved playback state if exists
                    scope.launch {
                        restorePlaybackState()
                    }
                },
                MoreExecutors.directExecutor()
            )

            Log.d(TAG, "PlaybackManager initialized")
        }
    }

    fun playTrack(track: Track) {
        val mediaItem = createValidatedMediaItem(track)
        if (mediaItem == null) {
            Log.e(TAG, "Cannot play track without local URI")
            return
        }

        initialize()
        _isShuffleEnabled.value = false

        controller?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }

        // Emit the current track ID
        _currentTrackId.value = track.uuid
        _currentQueueIndex.value = 0

        Log.d(TAG, "Playing track: ${track.title}")
    }

    @OptIn(UnstableApi::class)
    fun setQueue(
        tracks: List<Track>,
        startIndex: Int = 0,
        startPositionMs: Long = C.TIME_UNSET,
        keepShuffleMode: Boolean = false
    ) {
        initialize()

        if (!keepShuffleMode) {
            _isShuffleEnabled.value = false
        }

        // Clear all disk cache when a brand new queue/song is played
        PlaybackService.StreamCacheManager.clearAllCache()

        val mediaItems = tracks.mapNotNull { track -> createValidatedMediaItem(track) }

        controller?.apply {
            // When starting a fresh queue the caller is responsible for ordering the tracks
            // (pre-shuffling in Kotlin when shuffle is on). Disabling ExoPlayer's own shuffle
            // prevents double-shuffling where ExoPlayer would override the intended playback
            // order with its own random permutation, causing auto-advance to skip to the
            // wrong track when a song ends naturally.
            if (!keepShuffleMode) {
                shuffleModeEnabled = false
            }
            setMediaItems(mediaItems, startIndex, startPositionMs)
            prepare()
            play()
        }

        // Emit the initial track ID
        tracks.getOrNull(startIndex)?.let { startTrack ->
            _currentTrackId.value = startTrack.uuid
            _currentQueueIndex.value = startIndex
        }

        Log.d(
            TAG,
            "Queue set with ${mediaItems.size} tracks, starting at index $startIndex pos $startPositionMs"
        )

        // Save queue to preferences
        scope.launch {
            saveQueueStructure()
        }
    }

    /**
     * Add tracks to the end of the current queue without interrupting playback.
     *
     * @param tracks Tracks to add
     */
    fun addToQueue(tracks: List<Track>) {
        initialize()

        val mediaItems = tracks.mapNotNull { track -> createValidatedMediaItem(track) }

        if (mediaItems.isNotEmpty()) {
            controller?.addMediaItems(mediaItems)
            Log.d(TAG, "Added ${mediaItems.size} tracks to queue")

            // Save updated queue structure
            scope.launch {
                saveQueueStructure()
            }
        }
    }

    /**
     * Insert a track at a specific position in the current queue without interrupting playback.
     */
    fun addToQueueAt(track: Track, index: Int): Boolean {
        val mediaItem = createValidatedMediaItem(track)
        if (mediaItem == null) {
            Log.w(TAG, "Cannot enqueue track without local URI: ${track.title}")
            return false
        }

        initialize()

        controller?.let { ctrl ->
            val targetIndex = index.coerceIn(0, ctrl.mediaItemCount)
            ctrl.addMediaItem(targetIndex, mediaItem)
            Log.d(TAG, "Inserted track ${track.title} at index $targetIndex")

            // Save updated queue structure
            scope.launch {
                saveQueueStructure()
            }
            return true
        }

        return false
    }

    /**
     * Insert a list of tracks at a specific position in the current queue without interrupting playback.
     */
    fun addToQueueAt(tracks: List<Track>, index: Int): Boolean {
        val mediaItems = tracks.mapNotNull { createValidatedMediaItem(it) }
        if (mediaItems.isEmpty()) return false

        initialize()

        controller?.let { ctrl ->
            val targetIndex = index.coerceIn(0, ctrl.mediaItemCount)
            ctrl.addMediaItems(targetIndex, mediaItems)
            Log.d(TAG, "Inserted ${mediaItems.size} tracks at index $targetIndex")

            // Save updated queue structure
            scope.launch {
                saveQueueStructure()
            }
            return true
        }

        return false
    }

    fun togglePlayPause() {
        controller?.let {
            if (it.isPlaying) {
                it.pause()
                Log.d(TAG, "Paused")
            } else {
                it.play()
                Log.d(TAG, "Playing")
            }
        }
    }

    private fun runTrackChangePreservingPlayState(action: (Player) -> Unit) {
        controller?.let { ctrl ->
            val shouldPlayWhenReady = ctrl.playWhenReady
            action(ctrl)
            ctrl.playWhenReady = shouldPlayWhenReady
        }
    }

    fun shouldResumeAfterTrackChange(): Boolean {
        return controller?.playWhenReady ?: _isPlaying.value
    }

    fun skipToNext() {
        runTrackChangePreservingPlayState { it.seekToNext() }
        Log.d(TAG, "Skip to next")
    }

    fun skipToPrevious() {
        runTrackChangePreservingPlayState {
            if (it.currentPosition > 3000) {
                it.seekTo(0)
            } else {
                it.seekToPrevious()
            }
        }
        Log.d(TAG, "Skip to previous")
    }

    fun toggleShuffle() {
        val isNowEnabled = !_isShuffleEnabled.value
        _isShuffleEnabled.value = isNowEnabled

        if (!isNowEnabled) {
            Log.d(TAG, "Shuffle disabled")
            return
        }

        var reordered = false
        controller?.let { ctrl ->
            val totalItems = ctrl.mediaItemCount
            val firstShuffleIndex = ctrl.currentMediaItemIndex + 1

            if (firstShuffleIndex in 1 until totalItems) {
                val shuffledIds = (firstShuffleIndex until totalItems)
                    .map { ctrl.getMediaItemAt(it).mediaId }
                    .shuffled()

                shuffledIds.forEachIndexed { offset, mediaId ->
                    val targetIndex = firstShuffleIndex + offset
                    var sourceIndex = targetIndex

                    while (
                        sourceIndex < ctrl.mediaItemCount &&
                        ctrl.getMediaItemAt(sourceIndex).mediaId != mediaId
                    ) {
                        sourceIndex++
                    }

                    if (sourceIndex < ctrl.mediaItemCount && sourceIndex != targetIndex) {
                        ctrl.moveMediaItem(sourceIndex, targetIndex)
                        reordered = true
                    }
                }
            }
        }

        if (reordered) {
            scope.launch {
                saveQueueStructure()
            }
        }

        Log.d(TAG, if (reordered) "Shuffle enabled" else "Shuffle enabled (nothing to reorder)")
    }

    fun toggleRepeatMode() {
        controller?.let {
            val nextMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_OFF
            }
            it.repeatMode = nextMode
            _repeatMode.value = nextMode
            Log.d(TAG, "Repeat mode toggled to: $nextMode")
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        Log.d(TAG, "Seeked to $positionMs ms")
    }

    fun seekToIndex(index: Int) {
        runTrackChangePreservingPlayState { it.seekTo(index, 0L) }
        Log.d(TAG, "Seeked to index $index")
    }

    fun getCurrentPosition(): Long {
        return controller?.currentPosition ?: 0L
    }

    fun getDuration(): Long {
        return controller?.duration ?: 0L
    }

    /**
     * Shrinks the queue to only the currently playing item without stopping playback.
     *
     * Stream cache entries for removed items are explicitly evicted so a manual queue clear
     * (for example when switching modes) does not leave stale media on disk.
     */
    @OptIn(UnstableApi::class)
    fun keepOnlyCurrentTrack() {
        controller?.let { ctrl ->
            val currentIndex = ctrl.currentMediaItemIndex
            val totalItems = ctrl.mediaItemCount

            if (totalItems <= 1 || currentIndex < 0) return

            // Remove items after current track
            for (i in totalItems - 1 downTo currentIndex + 1) {
                // Clear disk cache for explicit queue clear (e.g. Radio mode)
                PlaybackService.StreamCacheManager.removeTrackCache(ctrl.getMediaItemAt(i).localConfiguration?.uri?.toString())
                ctrl.removeMediaItem(i)
            }

            // Remove items before current track
            for (i in currentIndex - 1 downTo 0) {
                // Clear disk cache for explicit queue clear
                PlaybackService.StreamCacheManager.removeTrackCache(ctrl.getMediaItemAt(i).localConfiguration?.uri?.toString())
                ctrl.removeMediaItem(i)
            }

            Log.d(TAG, "Kept only current track at original index $currentIndex")

            // Update local state flows to reflect the new state immediately
            if (_currentQueueIndex.value != 0) {
                _currentQueueIndex.value = 0
            }

            scope.launch {
                saveQueueStructure()
            }
        }
    }

    /**
     * Correctly calculates remaining tracks in the current playback functionality.
     * Returns how many tracks remain after the current one in the queue.
     * ExoPlayer's native shuffle is always disabled (pre-shuffled lists are used instead),
     * so remaining tracks = total − current − 1.
     */
    fun getRemainingTracksCount(): Int {
        val ctrl = controller ?: return 0
        val current = ctrl.currentMediaItemIndex
        val total = ctrl.mediaItemCount
        return if (current in 0 until total) total - current - 1 else 0
    }

    /**
     * Remove a track from the queue by its media ID.
     *
     * @param mediaId The media ID of the track to remove
     * @return true if the track was removed, false otherwise
     */
    @OptIn(UnstableApi::class)
    fun removeFromQueue(mediaId: String): Boolean {
        controller?.let { ctrl ->
            val index = (0 until ctrl.mediaItemCount).firstOrNull { i ->
                ctrl.getMediaItemAt(i).mediaId == mediaId
            } ?: return false

            // Clear cache when song is explicitly swiped/removed from queue
            PlaybackService.StreamCacheManager.removeTrackCache(ctrl.getMediaItemAt(index).localConfiguration?.uri?.toString())

            // Use playlist API to avoid full re-prepare and reduce playback hiccup
            ctrl.removeMediaItem(index)
            Log.d(TAG, "Removed track $mediaId from queue at index $index")

            // Save updated queue structure
            scope.launch {
                saveQueueStructure()
            }
            return true
        }
        return false
    }

    /**
     * Replace a track in the queue with a new track (e.g., replacing streaming with downloaded version).
     * Useful for seamlessly transitioning from streaming to offline playback.
     *
     * @param oldMediaId The media ID of the track to replace
     * @param newTrack The new track to replace it with
     * @return true if the track was replaced, false otherwise
     */
    fun replaceTrackInQueue(
        oldMediaId: String,
        newTrack: Track,
        seamlessIfPlaying: Boolean = false
    ): Boolean {
        controller?.let { ctrl ->
            val index = (0 until ctrl.mediaItemCount).firstOrNull { i ->
                ctrl.getMediaItemAt(i).mediaId == oldMediaId
            } ?: return false

            val newMediaItem = createValidatedMediaItem(newTrack)
            if (newMediaItem == null) {
                Log.w(TAG, "Cannot replace track without valid media item: ${newTrack.title}")
                return false
            }

            val isCurrentTrack = ctrl.currentMediaItemIndex == index
            val currentPosition = if (isCurrentTrack) ctrl.currentPosition else 0L

            if (isCurrentTrack && seamlessIfPlaying) {
                // Background download: Keep playing the temporary file to prevent stuttering.
                Log.d(
                    TAG,
                    "Track $oldMediaId is playing. Skipping ExoPlayer swap for seamless audio."
                )
            } else {
                // Atomic replacement prevents the timeline "blip" that causes queue duplication
                ctrl.replaceMediaItem(index, newMediaItem)
                if (isCurrentTrack) {
                    ctrl.seekTo(index, currentPosition)
                    _currentTrackId.value = newTrack.uuid
                    _currentQueueIndex.value = index
                }
            }

            Log.d(TAG, "Replaced track $oldMediaId with ${newTrack.uuid} at index $index")

            // Save updated queue structure
            scope.launch {
                saveQueueStructure()
            }
            return true
        }
        return false
    }

    /**
     * Move a track to a new position in the queue.
     *
     * @param fromIndex Current index of the track
     * @param toIndex New index for the track
     * @return true if the move was successful, false otherwise
     */
    /**
     * Remove a deleted track from the queue to prevent playback errors.
     * Should be called when a track is deleted from the library.
     *
     * @param trackUuid UUID of the deleted track
     */
    @OptIn(UnstableApi::class)
    fun removeDeletedTrackFromQueue(trackUuid: String) {
        controller?.let { ctrl ->
            // Find all instances of this track in the queue
            val indicesToRemove = mutableListOf<Int>()
            for (i in 0 until ctrl.mediaItemCount) {
                if (ctrl.getMediaItemAt(i).mediaId == trackUuid) {
                    indicesToRemove.add(i)
                }
            }

            if (indicesToRemove.isEmpty()) {
                return
            }

            val currentIndex = ctrl.currentMediaItemIndex
            val isCurrentTrack = indicesToRemove.contains(currentIndex)

            // Remove from queue (remove in reverse order to maintain indices)
            indicesToRemove.sortedDescending().forEach { index ->
                PlaybackService.StreamCacheManager.removeTrackCache(ctrl.getMediaItemAt(index).localConfiguration?.uri?.toString())
                ctrl.removeMediaItem(index)
                Log.d(TAG, "Removed deleted track from queue at index $index")
            }

            // If the deleted track was playing, skip to next
            if (isCurrentTrack) {
                Log.w(TAG, "Deleted track was currently playing, skipping to next")
                if (ctrl.hasNextMediaItem()) {
                    ctrl.prepare()
                    ctrl.play()
                } else {
                    ctrl.stop()
                    _currentTrackId.value = null
                    Log.d(TAG, "No next track available after deletion, stopping playback")
                }
            }

            // Save updated queue
            scope.launch {
                saveQueueStructure()
            }
        }
    }

    fun moveInQueue(fromIndex: Int, toIndex: Int): Boolean {
        controller?.let { ctrl ->
            if (fromIndex < 0 || fromIndex >= ctrl.mediaItemCount ||
                toIndex < 0 || toIndex >= ctrl.mediaItemCount
            ) {
                return false
            }

            // Use playlist move to minimize playback interruption
            ctrl.moveMediaItem(fromIndex, toIndex)
            Log.d(TAG, "Moved track from index $fromIndex to $toIndex via playlist API")

            // Save updated queue structure
            scope.launch {
                saveQueueStructure()
            }
            return true
        }
        return false
    }

    /**
     * Start sleep timer that will pause playback after specified minutes.
     * @param minutes Duration in minutes
     */
    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()

        val durationMs = minutes * 60 * 1000L
        sleepTimerJob = scope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                _sleepTimerRemaining.value = remaining
                kotlinx.coroutines.delay(1000)
                remaining -= 1000
            }

            // Timer finished - pause playback
            _sleepTimerRemaining.value = null
            controller?.pause()
            Log.d(TAG, "Sleep timer finished - paused playback")
        }

        Log.d(TAG, "Sleep timer started for $minutes minutes")
    }

    /**
     * Cancel active sleep timer.
     */
    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemaining.value = null
        Log.d(TAG, "Sleep timer cancelled")
    }

    fun release() {
        cancelSleepTimer()
        savePlaybackState() // Save state before releasing
        audioEffectController.release()
        MediaController.releaseFuture(controllerFuture ?: return)
        // remove player listener if attached
        try {
            playerListener?.let { controller?.removeListener(it) }
        } catch (_: Exception) {
        }
        playerListener = null
        controller = null
        controllerFuture = null
        Log.d(TAG, "PlaybackManager released")
    }

    private fun savePlaybackState() {
        try {
            val position = controller?.currentPosition ?: 0L
            val currentIndex = controller?.currentMediaItemIndex ?: -1

            prefs.edit().apply {
                putLong("playback_position", position)
                putInt("queue_start_index", currentIndex)
                apply()
            }
            Log.d(TAG, "Saved playback state: position=$position, index=$currentIndex")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save playback state: ${e.message}")
        }
    }

    private suspend fun saveQueueStructure() {
        try {
            val trackIds = withContext(Dispatchers.Main) {
                val ctrl = controller ?: return@withContext emptyList<String>()
                val count = ctrl.mediaItemCount
                (0 until count).map { i ->
                    ctrl.getMediaItemAt(i).mediaId
                }
            }

            if (trackIds.isEmpty()) return

            val idsString = trackIds.joinToString(",")
            val currentIndex =
                withContext(Dispatchers.Main) { controller?.currentMediaItemIndex ?: 0 }
            val currentPosition =
                withContext(Dispatchers.Main) { controller?.currentPosition ?: 0L }

            prefs.edit().apply {
                putString("queue_track_ids", idsString)
                putInt("queue_start_index", currentIndex)
                putLong("playback_position", currentPosition)
                putBoolean("has_saved_state", true)
                apply()
            }
            Log.d(TAG, "Saved queue structure: ${trackIds.size} tracks")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save queue structure: ${e.message}")
        }
    }

    private suspend fun restorePlaybackState() {
        try {
            if (!prefs.getBoolean("has_saved_state", false)) {
                Log.d(TAG, "No saved playback state found")
                return
            }

            val trackIds = prefs.getString("queue_track_ids", "") ?: ""
            if (trackIds.isEmpty()) {
                Log.d(TAG, "No saved queue found")
                return
            }

            val ids = trackIds.split(",")
            val savedIndex = prefs.getInt("queue_start_index", 0)
            val savedPosition = prefs.getLong("playback_position", 0L)

            // Load tracks from database, re-resolving stream files if needed
            val tracks = withContext(Dispatchers.IO) {
                ids.mapNotNull { id ->
                    try {
                        val entity =
                            database.trackDao().getTrackByUuid(id) ?: return@mapNotNull null
                        var track = entity.toTrack()

                        // For stream tracks whose file was evicted, re-resolve the stream
                        if (track.isStream && track.spotifyId != null) {
                            val fileExists = track.localUri?.let { uri ->
                                try {
                                    java.io.File(uri).let { it.exists() && it.length() > 0 }
                                } catch (_: Exception) {
                                    false
                                }
                            } ?: false

                            if (!fileExists) {
                                Log.d(
                                    TAG,
                                    "Stream file missing for '${track.title}', re-resolving..."
                                )
                                try {
                                    "https://open.spotify.com/track/${track.spotifyId}"
                                    val song =
                                        SpotifyApi.spotifyTrackToSong(SpotifyApi.getTrack(track.spotifyId!!))
                                    val refreshed =
                                        musicService.streamTrack(song, preferredUuid = track.uuid)
                                    // Update DB with new localUri
                                    database.trackDao().insertTrack(refreshed.toEntity())
                                    track = refreshed
                                    Log.d(TAG, "Re-resolved stream for '${track.title}'")
                                } catch (e: Exception) {
                                    Log.w(
                                        TAG,
                                        "Failed to re-resolve stream for '${track.title}': ${e.message}"
                                    )
                                    // Keep the track in the queue anyway for metadata display;
                                    // playback will trigger error recovery which re-fetches the stream
                                    return@mapNotNull track
                                }
                            }
                        }

                        track
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load track $id: ${e.message}")
                        null
                    }
                }
            }

            if (tracks.isEmpty()) {
                Log.d(TAG, "No tracks found in database for saved queue")
                return
            }

            Log.d(
                TAG,
                "Restoring playback state: ${tracks.size} tracks, index=$savedIndex, position=$savedPosition"
            )

            // Restore queue — use createValidatedMediaItem but fall back to URI-less items
            // for stream tracks that couldn't be re-resolved (they'll trigger error recovery)
            val mediaItems = tracks.mapNotNull { track -> createValidatedMediaItem(track) }

            if (mediaItems.isEmpty()) {
                Log.d(TAG, "No playable media items could be created from saved queue")
                return
            }

            // MediaController methods must be called on main thread
            withContext(Dispatchers.Main) {
                controller?.apply {
                    setMediaItems(
                        mediaItems,
                        savedIndex.coerceIn(0, mediaItems.size - 1),
                        savedPosition
                    )
                    prepare()
                    // Don't auto-play, just prepare to paused state
                }

                tracks.getOrNull(savedIndex)?.let { track ->
                    _currentTrackId.value = track.uuid
                }

                // Set the restored index
                _currentQueueIndex.value = savedIndex

                // Update QueueManager with remaining tracks from current position
                // QueueManager treats index 0 as "current track", so we pass only tracks from savedIndex onwards
                // This prevents state desync between ExoPlayer's position and QueueManager's internal state
                val remainingTracks = tracks.drop(savedIndex)
                if (remainingTracks.isNotEmpty()) {
                    queueManager.initializeQueue(remainingTracks)
                }

                _hasRestoredState.value = true
                Log.d(TAG, "Playback state restored successfully")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore playback state: ${e.message}", e)
        }
    }
}
