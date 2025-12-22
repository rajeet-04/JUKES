package com.example.juke.services

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.example.juke.database.MusicDatabase
import com.example.juke.models.Track
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri

/**
 * Media Playback Service using Media3 (ExoPlayer).
 */
class PlaybackService : MediaSessionService() {
    
    private val TAG = "PlaybackService"
    
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var database: MusicDatabase
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        
        database = MusicDatabase.getDatabase(applicationContext)
        
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true)

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> {
                        Log.d(TAG, "Playback ended")
                    }
                    Player.STATE_READY -> {
                        Log.d(TAG, "Player ready")
                    }
                    Player.STATE_BUFFERING -> {
                        Log.d(TAG, "Buffering...")
                    }
                    Player.STATE_IDLE -> {
                        Log.d(TAG, "Player idle")
                    }
                }
            }
            
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "Player error: ${error.message}", error)
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let {
                    val trackId = it.mediaId
                    Log.d(TAG, "Media item transition: $trackId")
                    
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
        })
        
        val sessionActivityPendingIntent = packageManager
            ?.getLaunchIntentForPackage(packageName)
            ?.let { sessionIntent ->
                PendingIntent.getActivity(
                    this,
                    0,
                    sessionIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
        
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent!!)
            .build()
        
        Log.d(TAG, "PlaybackService created")
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
    
    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
        Log.d(TAG, "PlaybackService destroyed")
    }
}

/**
 * Playback Manager for controlling media playback.
 * This connects to PlaybackService via MediaController to enable notification controls.
 */
class PlaybackManager(private val context: Context) {
    
    private val TAG = "PlaybackManager"
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
                                }

                                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                                    mediaItem?.let { item ->
                                        val trackId = item.mediaId
                                        _currentTrackId.value = trackId
                                        Log.d(TAG, "Media item transition: $trackId")
                                    }
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
                                        val playing = controller?.isPlaying == true
                                        if (playing != last) {
                                            _isPlaying.value = playing
                                            Log.d(TAG, "Polled controller isPlaying: $playing")
                                            last = playing
                                        }
                                        kotlinx.coroutines.delay(300)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Polling loop error: ${e.message}")
                                        break
                                    }
                                }
                            }
                },
                MoreExecutors.directExecutor()
            )
            
            Log.d(TAG, "PlaybackManager initialized")
        }
    }
    
    fun playTrack(track: Track) {
        if (track.localUri == null) {
            Log.e(TAG, "Cannot play track without local URI")
            return
        }
        
        initialize()
        
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.uuid)
            .setUri(track.localUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(track.thumbnailUri?.toUri())
                    .build()
            )
            .build()
        
        controller?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }
        
        // Emit the current track ID
        _currentTrackId.value = track.uuid
        
        Log.d(TAG, "Playing track: ${track.title}")
        // Update play count in DB
        scope.launch {
            try {
                val now = SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    Locale.US
                ).format(Date())
                database.trackDao().incrementPlayCount(track.uuid, now)
            } catch (e: Exception) {
                Log.e(TAG, "Error incrementing play count: ${e.message}", e)
            }
        }
    }
    
    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        initialize()
        
        val mediaItems = tracks.mapNotNull { track ->
            track.localUri?.let { uri ->
                MediaItem.Builder()
                    .setMediaId(track.uuid)
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setArtworkUri(track.thumbnailUri?.toUri())
                            .build()
                    )
                    .build()
            }
        }
        
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
        // Increment play count for the starting track
        tracks.getOrNull(startIndex)?.let { startTrack ->
            scope.launch {
                try {
                    val now = SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                        Locale.US
                    ).format(Date())
                    database.trackDao().incrementPlayCount(startTrack.uuid, now)
                } catch (e: Exception) {
                    Log.e(TAG, "Error incrementing play count for queue start: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Add tracks to the end of the current queue without interrupting playback.
     * 
     * @param tracks Tracks to add
     */
    fun addToQueue(tracks: List<Track>) {
        initialize()
        
        val mediaItems = tracks.mapNotNull { track ->
            track.localUri?.let { uri ->
                MediaItem.Builder()
                    .setMediaId(track.uuid)
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setArtworkUri(track.thumbnailUri?.toUri())
                            .build()
                    )
                    .build()
            }
        }
        
        if (mediaItems.isNotEmpty()) {
            controller?.addMediaItems(mediaItems)
            Log.d(TAG, "Added ${mediaItems.size} tracks to queue")
        }
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
    
    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        Log.d(TAG, "Seeked to $positionMs ms")
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
    fun moveInQueue(fromIndex: Int, toIndex: Int): Boolean {
        controller?.let { ctrl ->
            if (fromIndex < 0 || fromIndex >= ctrl.mediaItemCount ||
                toIndex < 0 || toIndex >= ctrl.mediaItemCount) {
                return false
            }

            // Use playlist move to minimize playback interruption
            ctrl.moveMediaItem(fromIndex, toIndex)
            Log.d(TAG, "Moved track from index $fromIndex to $toIndex via playlist API")
            return true
        }
        return false
    }
    
    fun release() {
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
}
