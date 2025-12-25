package com.example.juke.analytics

import com.example.juke.models.Track

/**
 * Analytics Integration Helper for PlayerViewModel
 * 
 * Add these methods to your PlayerViewModel to track playback events
 */

class PlayerAnalyticsHelper {
    
    private val analytics = AnalyticsManager.getInstance()
    private var currentSongStartTime: Long = 0
    private var currentSongId: String? = null
    private var currentSongDuration: Long = 0
    
    /**
     * Call this when a song starts playing
     */
    fun onSongStarted(track: Track, positionInQueue: Int) {
        // Use "Title - Artist" as identifier for better analytics
        currentSongId = "${track.title} - ${track.artist}"
        currentSongDuration = track.durationSec * 1000L // Convert to milliseconds
        currentSongStartTime = System.currentTimeMillis()
        
        analytics.trackSongPlayed(
            songId = "${track.title} - ${track.artist}",
            songTitle = track.title,
            songArtist = track.artist,
            songDuration = track.durationSec * 1000L, // Convert to milliseconds
            positionInQueue = positionInQueue
        )
    }
    
    /**
     * Call this when a song ends (either completed or skipped)
     */
    fun onSongEnded(currentPosition: Long) {
        currentSongId?.let { songId ->
            val playedDuration = minOf(currentPosition, currentSongDuration)
            
            analytics.trackSongEnd(
                songId = songId,
                playDuration = playedDuration,
                songDuration = currentSongDuration
            )
        }
        
        // Reset state
        currentSongId = null
        currentSongStartTime = 0
        currentSongDuration = 0
    }
    
    /**
     * Alternative: Call this on song completion (when ExoPlayer reaches end)
     */
    fun onSongCompleted() {
        onSongEnded(currentSongDuration)
    }
    
    /**
     * Alternative: Call this when user skips to next song
     */
    fun onSongSkipped(currentPosition: Long) {
        onSongEnded(currentPosition)
    }
}

/**
 * Example integration in PlayerViewModel:
 * 
 * class PlayerViewModel(application: Application) : AndroidViewModel(application) {
 *     private val analyticsHelper = PlayerAnalyticsHelper()
 *     
 *     fun playWithRecommendations(track: Track) {
 *         viewModelScope.launch {
 *             _currentTrack.value = track
 *             queueManager.initializeQueue(listOf(track))
 *             
 *             // Track song started
 *             analyticsHelper.onSongStarted(track, positionInQueue = 0)
 *         }
 *     }
 *     
 *     fun skipToNext() {
 *         viewModelScope.launch {
 *             // Get current playback position from ExoPlayer
 *             val currentPosition = exoPlayer.currentPosition
 *             
 *             // Track song skipped
 *             analyticsHelper.onSongSkipped(currentPosition)
 *             
 *             // Move to next song
 *             val nextTrack = queueManager.getNextTrack()
 *             nextTrack?.let {
 *                 _currentTrack.value = it
 *                 analyticsHelper.onSongStarted(it, positionInQueue = getCurrentQueuePosition())
 *             }
 *         }
 *     }
 *     
 *     // Listen to ExoPlayer state changes
 *     private fun setupExoPlayerListener() {
 *         exoPlayer.addListener(object : Player.Listener {
 *             override fun onPlaybackStateChanged(state: Int) {
 *                 when (state) {
 *                     Player.STATE_ENDED -> {
 *                         // Song completed naturally
 *                         analyticsHelper.onSongCompleted()
 *                     }
 *                 }
 *             }
 *             
 *             override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
 *                 // Track when moving to next song in queue
 *                 if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
 *                     // Auto transition (song completed)
 *                     analyticsHelper.onSongCompleted()
 *                 }
 *             }
 *         })
 *     }
 * }
 */
