package com.example.juke.services

import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionToken
import com.example.juke.analytics.AnalyticsManager
import com.example.juke.database.MusicDatabase
import com.example.juke.database.toTrack
import com.example.juke.models.Track
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper

/**
 * Media Playback Service using Media3 (ExoPlayer) with Android Auto support.
 */
class PlaybackService : MediaLibraryService() {
    
    private val TAG = "PlaybackService"
    
    private var mediaSession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private lateinit var database: MusicDatabase
    val audioEffectController: AudioEffectController by lazy { AudioEffectController(this) }
    
    private var wasPlayingBeforeCall = false
    private var wasPlayingBeforeFocusLoss = false
    private lateinit var audioManager: android.media.AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var resumeRunnable: Runnable? = null

    // 1. Define the Receiver
    private val callStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                val state = intent.getStringExtra(android.telephony.TelephonyManager.EXTRA_STATE)
                Log.d(TAG, "Phone state changed: $state")
                
                when (state) {
                    android.telephony.TelephonyManager.EXTRA_STATE_RINGING -> {
                        // Call coming in: Pause and save state
                        if (player.isPlaying) {
                            wasPlayingBeforeCall = true
                            player.pause()
                            Log.d(TAG, "Paused playback due to incoming call")
                        }
                    }
                    android.telephony.TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        // Call active (or outgoing call started)
                        // If user makes an outgoing call while music is playing, pause and save state
                        if (player.isPlaying) {
                            wasPlayingBeforeCall = true
                            player.pause()
                            Log.d(TAG, "Paused playback due to active/outgoing call")
                        }
                    }
                    android.telephony.TelephonyManager.EXTRA_STATE_IDLE -> {
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
    private val audioFocusChangeListener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss (another app took focus permanently)
                if (player.isPlaying) {
                    player.pause()
                    Log.d(TAG, "Audio focus lost permanently - paused")
                }
                wasPlayingBeforeFocusLoss = false
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporary loss (notification, alarm, etc.)
                if (player.isPlaying) {
                    wasPlayingBeforeFocusLoss = true
                    player.pause()
                    Log.d(TAG, "Audio focus lost temporarily - paused")
                }
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Can duck (lower volume) - we'll just pause for simplicity
                if (player.isPlaying) {
                    wasPlayingBeforeFocusLoss = true
                    player.pause()
                    Log.d(TAG, "Audio focus ducked - paused")
                }
            }
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
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
        // Fix: Check if app is in background before attempting to start foreground service
        // This prevents ForegroundServiceStartNotAllowedException on Android 12+
        var isAppInForeground = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val currentState = androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
                isAppInForeground = currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                if (!isAppInForeground) {
                     Log.w(TAG, "App is in background, suppressing initial startForeground to avoid crash")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check app lifecycle state: ${e.message}")
            }
        }

        if (isAppInForeground) {
            try {
                val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // Use platform MediaStyle for proper notification display
                    android.app.Notification.Builder(this, "media_playback")
                        .setContentTitle("Juke")
                        .setContentText("Ready to play")
                        .setSmallIcon(android.R.drawable.ic_media_play)
                        .setOngoing(true)
                        .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
                        .apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                setStyle(android.app.Notification.MediaStyle()
                                    .setShowActionsInCompactView()
                                )
                            }
                        }
                        .build()
                } else {
                    android.app.Notification.Builder(this, "media_playback")
                        .setContentTitle("Juke")
                        .setContentText("Ready to play")
                        .setSmallIcon(android.R.drawable.ic_media_play)
                        .setOngoing(true)
                        .build()
                }

                // Start foreground with proper service type for Android 14+
                if (Build.VERSION.SDK_INT >= 34) { // Android 14+
                    startForeground(
                        1,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(1, notification)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
                // Handle Android 12+ background restrictions
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    e is android.app.ForegroundServiceStartNotAllowedException
                ) {
                    Log.w(TAG, "Cannot start foreground service from background")
                    return START_NOT_STICKY
                }
            }
        }

        // Let Media3 handle the rest (it will replace our basic notification with the proper one)
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

        // Validate artwork URI
        track.thumbnailUri?.takeIf { it.isNotEmpty() }?.let { uriString ->
            try {
                val uri = uriString.toUri()
                if (!uriString.startsWith("http")) { 
                    val file = java.io.File(uri.path ?: "")
                    if (file.exists() && file.canRead() && file.length() > 0) {
                        metadataBuilder.setArtworkUri(uri)
                    }
                } else {
                     metadataBuilder.setArtworkUri(uri)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Invalid artwork URI for ${track.title}: ${e.message}")
            }
        }

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
            mediaItem?.let {
                val trackId = it.mediaId
                Log.d(TAG, "Media item transition: $trackId, reason: $reason")

                // Force notification metadata update for Android 16 compatibility
                try {
                    mediaSession?.let { session ->
                        val currentItem = player.currentMediaItem
                        currentItem?.let { item ->
                            // Create fresh metadata with validated artwork
                            serviceScope.launch {
                                try {
                                    // Delay to force notification repaint
                                    kotlinx.coroutines.delay(500)
                                    val track = database.trackDao().getTrackByUuid(trackId)?.toTrack()
                                    if (track != null) {
                                        val validatedItem = createValidatedMediaItem(track)
                                        validatedItem?.let { newItem ->
                                            // Replace current item with validated metadata
                                            val currentIndex = player.currentMediaItemIndex
                                            player.replaceMediaItem(currentIndex, newItem)
                                            Log.d(TAG, "Updated media item with validated metadata for ${track.title}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to update media item metadata: ${e.message}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to refresh player metadata: ${e.message}")
                }

                serviceScope.launch {
                    try {
                        val now = SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                            Locale.US
                        ).format(Date())

                        database.trackDao().incrementPlayCount(trackId, now)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating play count: ${e.message}", e)
                    }
                }
            }
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "Is playing: $isPlaying")
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
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media playback controls"
                setShowBadge(false)
            }
            getSystemService(android.app.NotificationManager::class.java)
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
        audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true)

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, false) // Keep FALSE to allow manual call control
            .setHandleAudioBecomingNoisy(true)
            .build()
        
        // Request audio focus when player starts playing
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioManager.requestAudioFocus(
                            android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
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
                            android.media.AudioManager.STREAM_MUSIC,
                            android.media.AudioManager.AUDIOFOCUS_GAIN
                        )
                    }
                    
                    if (result != android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
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
                sessionIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
            val filter = IntentFilter(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            registerReceiver(callStateReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register call state receiver: ${e.message}")
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

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // On Android 12+ (API 31), starting a foreground service from the background is restricted
        // and throws ForegroundServiceStartNotAllowedException.
        if (startInForegroundRequired && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                // Check if the app is effectively in the background
                val currentState = androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
                val isAppInForeground = currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                
                if (!isAppInForeground) {
                    Log.w(TAG, "App is in background, skipping notification update to avoid ForegroundServiceStartNotAllowedException")
                    // CRITICAL: Do NOT call super.onUpdateNotification. 
                    // Media3's default implementation will try to start the service in foreground even if we pass false,
                    // or it uses startForegroundService which crashes.
                    // By returning here, we suppress the crash at the cost of not updating the notification 
                    // until the app is foregrounded again.
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check app lifecycle state: ${e.message}")
            }
        }

        try {
            super.onUpdateNotification(session, startInForegroundRequired)
        } catch (e: Exception) {
            // Catch synchronous failures as a fallback
            Log.w(TAG, "Failed to update notification/start foreground: ${e.message}")
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
        
        super.onDestroy()
        Log.d(TAG, "PlaybackService destroyed")
    }
    
    /**
     * MediaLibrarySession callback for Android Auto browsing support
     */
    private inner class MediaLibrarySessionCallback : MediaLibrarySession.Callback {
        
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

            // Validate and set artwork URI only if file exists and is accessible
            track.thumbnailUri?.takeIf { it.isNotEmpty() }?.let { uriString ->
                try {
                    val uri = uriString.toUri()
                    // Check if the file actually exists
                    val file = java.io.File(uri.path ?: "")
                    if (file.exists() && file.canRead() && file.length() > 0) {
                        metadataBuilder.setArtworkUri(uri)
                        Log.d(TAG, "Set artwork URI for ${track.title}: $uriString")
                    } else {
                        Log.w(TAG, "Artwork file not accessible for ${track.title}: $uriString")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid artwork URI for track ${track.title}: $uriString - ${e.message}")
                }
            }

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
        @Volatile
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
    
    // Flow to emit current track UUID changes
    private val _currentTrackId = MutableStateFlow<String?>(null)
    val currentTrackIdFlow: StateFlow<String?> = _currentTrackId.asStateFlow()
    
    // Flow to indicate if restored state is available
    private val _hasRestoredState = MutableStateFlow(false)
    val hasRestoredState: StateFlow<Boolean> = _hasRestoredState.asStateFlow()
    
    // Sleep timer state
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private val _sleepTimerRemaining = MutableStateFlow<Long?>(null)
    val sleepTimerRemaining: StateFlow<Long?> = _sleepTimerRemaining.asStateFlow()
    
    // Audio effect controller
    val audioEffectController: AudioEffectController by lazy { AudioEffectController(context) }
    
    // Shuffle state
    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabledFlow: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()
    
    // Queue manager for recommendations
    private val queueManager: QueueManager by lazy { QueueManager.getInstance(context) }
    
    /**
     * Helper function to create validated MediaItem with artwork checking
     */
    private fun createValidatedMediaItem(track: Track): MediaItem? {
        if (track.localUri == null) return null

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)

        // Validate artwork URI
        track.thumbnailUri?.takeIf { it.isNotEmpty() }?.let { uriString ->
            try {
                val uri = uriString.toUri()
                val file = java.io.File(uri.path ?: "")
                if (file.exists() && file.canRead() && file.length() > 0) {
                    metadataBuilder.setArtworkUri(uri)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Invalid artwork URI for ${track.title}: ${e.message}")
            }
        }

        return MediaItem.Builder()
            .setMediaId(track.uuid)
            .setUri(track.localUri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }
    
    fun initialize() {
        if (controllerFuture == null) {
            val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture?.addListener(
                {
                            controller = controllerFuture?.get()
                            Log.d(TAG, "MediaController connected to PlaybackService")
                            // Add a Player.Listener on the controller's underlying player
                            playerListener = object : Player.Listener {
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
                                            causeMessage.contains("FileNotFoundException")) {
                                            
                                            Log.w(TAG, "Track file not found (likely deleted), skipping to next track")
                                            
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
                                        }
                                    }
                                }

                                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                                    mediaItem?.let { item ->
                                        val trackId = item.mediaId
                                        _currentTrackId.value = trackId
                                        Log.d(TAG, "Media item transition: $trackId")
                                        savePlaybackState()
                                        
                                        // Save queue structure to persist auto-added songs
                                        scope.launch {
                                            saveQueueStructure()
                                        }
                                        
                                        // Capture position in queue on main thread before launching coroutine
                                        val positionInQueue = controller?.currentMediaItemIndex ?: 0
                                        
                                        // Track song play in analytics
                                        scope.launch {
                                            try {
                                                Log.d(TAG, "Analytics: Looking up track with ID: $trackId")
                                                val track = database.trackDao().getTrackByUuid(trackId)?.toTrack()
                                                if (track != null) {
                                                    Log.d(TAG, "Analytics: Found track '${track.title}' by ${track.artist}, calling trackSongPlayed")
                                                    AnalyticsManager.getInstance().trackSongPlayed(
                                                        songId = "${track.title} - ${track.artist}",
                                                        songTitle = track.title,
                                                        songArtist = track.artist,
                                                        songDuration = track.durationSec * 1000L,
                                                        positionInQueue = positionInQueue
                                                    )
                                                } else {
                                                    Log.w(TAG, "Analytics: Track not found in database for ID: $trackId")
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error tracking song play: ${e.message}", e)
                                            }
                                        }
                                    }
                                }
                                
                                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                                    _isShuffleEnabled.value = shuffleModeEnabled
                                    Log.d(TAG, "Shuffle mode changed: $shuffleModeEnabled")
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

        controller?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }

        // Emit the current track ID
        _currentTrackId.value = track.uuid

        Log.d(TAG, "Playing track: ${track.title}")
    }
    
    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        initialize()

        val mediaItems = tracks.mapNotNull { track -> createValidatedMediaItem(track) }

        controller?.apply {
            setMediaItems(mediaItems, startIndex, 0)
            prepare()
            play()
        }

        // Emit the initial track ID
        tracks.getOrNull(startIndex)?.let { startTrack ->
            _currentTrackId.value = startTrack.uuid
        }

        Log.d(TAG, "Queue set with ${mediaItems.size} tracks, starting at index $startIndex")

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

    fun skipToNext() {
        controller?.seekToNext()
        Log.d(TAG, "Skip to next")
    }
    
    fun skipToPrevious() {
        controller?.let {
            if (it.currentPosition > 3000) {
                it.seekTo(0)
            } else {
                it.seekToPrevious()
            }
        }
        Log.d(TAG, "Skip to previous")
    }
    
    fun toggleShuffle() {
        controller?.let {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
            _isShuffleEnabled.value = it.shuffleModeEnabled
            Log.d(TAG, "Shuffle toggled: ${it.shuffleModeEnabled}")
        }
    }
    
    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        Log.d(TAG, "Seeked to $positionMs ms")
    }
    
    fun seekToIndex(index: Int) {
        controller?.seekTo(index, 0L)
        Log.d(TAG, "Seeked to index $index")
    }
    
    fun getCurrentPosition(): Long {
        return controller?.currentPosition ?: 0L
    }
    
    fun getDuration(): Long {
        return controller?.duration ?: 0L
    }

    /**
     * Remove a track from the queue by its media ID.
     * 
     * @param mediaId The media ID of the track to remove
     * @return true if the track was removed, false otherwise
     */
    fun removeFromQueue(mediaId: String): Boolean {
        controller?.let { ctrl ->
            val index = (0 until ctrl.mediaItemCount).firstOrNull { i ->
                ctrl.getMediaItemAt(i).mediaId == mediaId
            } ?: return false

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
    fun replaceTrackInQueue(oldMediaId: String, newTrack: Track): Boolean {
        controller?.let { ctrl ->
            val index = (0 until ctrl.mediaItemCount).firstOrNull { i ->
                ctrl.getMediaItemAt(i).mediaId == oldMediaId
            } ?: return false

            val newMediaItem = createValidatedMediaItem(newTrack)
            if (newMediaItem == null) {
                Log.w(TAG, "Cannot replace track without valid media item: ${newTrack.title}")
                return false
            }

            // Check if this is the currently playing track
            val isCurrentTrack = ctrl.currentMediaItemIndex == index
            val currentPosition = if (isCurrentTrack) ctrl.currentPosition else 0L

            // Replace: remove old, insert new at same position
            ctrl.removeMediaItem(index)
            ctrl.addMediaItem(index, newMediaItem)

            // If it was the current track, seek back to maintain position
            if (isCurrentTrack) {
                ctrl.seekTo(index, currentPosition)
                _currentTrackId.value = newTrack.uuid
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
                toIndex < 0 || toIndex >= ctrl.mediaItemCount) {
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
        } catch (_: Exception) {}
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
            val currentIndex = withContext(Dispatchers.Main) { controller?.currentMediaItemIndex ?: 0 }
            val currentPosition = withContext(Dispatchers.Main) { controller?.currentPosition ?: 0L }
            
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
            
            // Load tracks from database
            val tracks = withContext(Dispatchers.IO) {
                ids.mapNotNull { id ->
                    try {
                        database.trackDao().getTrackByUuid(id)?.toTrack()
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
            
            Log.d(TAG, "Restoring playback state: ${tracks.size} tracks, index=$savedIndex, position=$savedPosition")
            
            // Restore queue
            val mediaItems = tracks.mapNotNull { track -> createValidatedMediaItem(track) }
            
            // MediaController methods must be called on main thread
            withContext(Dispatchers.Main) {
                controller?.apply {
                    setMediaItems(mediaItems, savedIndex.coerceIn(0, mediaItems.size - 1), savedPosition)
                    prepare()
                    // Don't auto-play, just prepare to paused state
                }
                
                tracks.getOrNull(savedIndex)?.let { track ->
                    _currentTrackId.value = track.uuid
                }
                
                // Update QueueManager with restored queue
                queueManager.initializeQueue(tracks)
                
                _hasRestoredState.value = true
                Log.d(TAG, "Playback state restored successfully")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore playback state: ${e.message}", e)
        }
    }
}
