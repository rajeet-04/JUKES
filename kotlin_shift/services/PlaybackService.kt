package com.juke.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.juke.database.MusicDatabase
import com.juke.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Media Playback Service using Media3 (ExoPlayer).
 * 
 * This service handles:
 * 1. Audio playback with MediaSession
 * 2. Media notification controls
 * 3. Background playback
 * 4. Queue management
 * 5. Playback state tracking
 * 
 * Uses Android's new Media3 library for robust media playback.
 */
class PlaybackService : MediaSessionService() {
    
    private val TAG = "PlaybackService"
    
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var database: MusicDatabase
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        
        database = MusicDatabase.getDatabase(applicationContext)
        
        // Initialize ExoPlayer
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // Handle audio focus
            )
            .setHandleAudioBecomingNoisy(true) // Pause on headphone disconnect
            .build()
        
        // Add player listener
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> {
                        Log.d(TAG, "Playback ended")
                        // Handle end of track (auto-play next)
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
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let {
                    val trackId = it.mediaId
                    Log.d(TAG, "Media item transition: $trackId")
                    
                    // Increment play count in database
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
        
        // Create MediaSession
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
 * 
 * This class provides a high-level interface to the PlaybackService.
 * Use this in your ViewModels/Activities to control playback.
 */
class PlaybackManager(private val context: Context) {
    
    private val TAG = "PlaybackManager"
    private var player: ExoPlayer? = null
    
    /**
     * Initialize the player.
     */
    fun initialize() {
        if (player == null) {
            player = ExoPlayer.Builder(context)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
                .setHandleAudioBecomingNoisy(true)
                .build()
            
            Log.d(TAG, "Player initialized")
        }
    }
    
    /**
     * Load and play a track.
     * 
     * @param track Track to play
     */
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
                    .setArtworkUri(track.thumbnailUri?.let { android.net.Uri.parse(it) })
                    .build()
            )
            .build()
        
        player?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }
        
        Log.d(TAG, "Playing track: ${track.title}")
    }
    
    /**
     * Set queue of tracks and start playback.
     * 
     * @param tracks List of tracks
     * @param startIndex Index to start playback from
     */
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
                            .setArtworkUri(track.thumbnailUri?.let { android.net.Uri.parse(it) })
                            .build()
                    )
                    .build()
            }
        }
        
        player?.apply {
            setMediaItems(mediaItems, startIndex, 0)
            prepare()
            play()
        }
        
        Log.d(TAG, "Queue set with ${mediaItems.size} tracks, starting at index $startIndex")
    }
    
    /**
     * Add track to end of queue.
     * 
     * @param track Track to add
     */
    fun addToQueue(track: Track) {
        if (track.localUri == null) return
        
        initialize()
        
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.uuid)
            .setUri(track.localUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(track.thumbnailUri?.let { android.net.Uri.parse(it) })
                    .build()
            )
            .build()
        
        player?.addMediaItem(mediaItem)
        
        Log.d(TAG, "Added to queue: ${track.title}")
    }
    
    /**
     * Play/pause toggle.
     */
    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
                Log.d(TAG, "Paused")
            } else {
                it.play()
                Log.d(TAG, "Playing")
            }
        }
    }
    
    /**
     * Pause playback.
     */
    fun pause() {
        player?.pause()
        Log.d(TAG, "Paused")
    }
    
    /**
     * Resume playback.
     */
    fun play() {
        player?.play()
        Log.d(TAG, "Playing")
    }
    
    /**
     * Stop playback and clear queue.
     */
    fun stop() {
        player?.apply {
            stop()
            clearMediaItems()
        }
        Log.d(TAG, "Stopped")
    }
    
    /**
     * Skip to next track.
     */
    fun skipToNext() {
        player?.seekToNext()
        Log.d(TAG, "Skip to next")
    }
    
    /**
     * Skip to previous track.
     */
    fun skipToPrevious() {
        player?.let {
            if (it.currentPosition > 3000) {
                // If more than 3 seconds into song, restart
                it.seekTo(0)
            } else {
                it.seekToPrevious()
            }
        }
        Log.d(TAG, "Skip to previous")
    }
    
    /**
     * Seek to position in current track.
     * 
     * @param positionMs Position in milliseconds
     */
    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        Log.d(TAG, "Seeked to $positionMs ms")
    }
    
    /**
     * Get current playback position.
     * 
     * @return Current position in milliseconds
     */
    fun getCurrentPosition(): Long {
        return player?.currentPosition ?: 0L
    }
    
    /**
     * Get duration of current track.
     * 
     * @return Duration in milliseconds
     */
    fun getDuration(): Long {
        return player?.duration ?: 0L
    }
    
    /**
     * Check if currently playing.
     * 
     * @return True if playing
     */
    fun isPlaying(): Boolean {
        return player?.isPlaying ?: false
    }
    
    /**
     * Release player resources.
     * Call this when done with playback.
     */
    fun release() {
        player?.release()
        player = null
        Log.d(TAG, "Player released")
    }
}
