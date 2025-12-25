package com.example.juke.analytics

/**
 * Example Usage of AnalyticsManager in your app
 * 
 * Add these calls in appropriate places in your existing code:
 */

// 1. In MainActivity.onCreate() - Track app opened
fun onAppOpened() {
    AnalyticsManager.getInstance().trackAppOpened()
}

// 2. In MainActivity.onDestroy() or onStop() - Track app closed and end session
fun onAppClosed() {
    val analytics = AnalyticsManager.getInstance()
    analytics.trackAppClosed()
    analytics.endSession()
}

// 3. When a song starts playing (in PlaybackService or PlayerViewModel)
fun onSongStarted(songId: String, title: String, artist: String, durationMs: Long, position: Int) {
    AnalyticsManager.getInstance().trackSongPlayed(
        songId = songId,
        songTitle = title,
        songArtist = artist,
        songDuration = durationMs,
        positionInQueue = position
    )
}

// 4. When a song ends or is skipped (in PlaybackService or PlayerViewModel)
fun onSongEnded(songId: String, playedDurationMs: Long, totalDurationMs: Long) {
    AnalyticsManager.getInstance().trackSongEnd(
        songId = songId,
        playDuration = playedDurationMs,
        songDuration = totalDurationMs
    )
}

// 5. When user performs a search (in SearchViewModel)
fun onSearchPerformed(query: String) {
    AnalyticsManager.getInstance().trackSearchQuery(query)
}

// 6. Get analytics data
fun getAnalyticsMetrics() {
    val analytics = AnalyticsManager.getInstance()
    
    val userId = analytics.getUserId()
    val totalSongs = analytics.getTotalSongsPlayed()
    val totalListeningTime = analytics.getTotalListeningTime()
    val songsInOrder = analytics.getSongsPlayedInOrder()
    val pendingEvents = analytics.getPendingEventsCount()
    
    println("User ID: $userId")
    println("Total Songs Played: $totalSongs")
    println("Total Listening Time: ${totalListeningTime / 1000 / 60} minutes")
    println("Pending Events: $pendingEvents")
}

// 7. Force sync (useful for testing or manual sync button)
fun forceSyncAnalytics() {
    AnalyticsManager.getInstance().forceSync()
}

/**
 * Integration Points in Your Existing Code:
 * 
 * MainActivity.kt:
 * - Add trackAppOpened() in onCreate()
 * - Add trackAppClosed() + endSession() in onDestroy() or onStop()
 * 
 * PlaybackService.kt or PlayerViewModel.kt:
 * - Add trackSongPlayed() when starting a song
 * - Add trackSongEnd() when song completes or user skips
 * 
 * SearchViewModel.kt:
 * - Add trackSearchQuery() when user performs a search
 * 
 * The analytics system will:
 * ✓ Generate a unique UUID for each user (persisted)
 * ✓ Store all events in memory and SharedPreferences
 * ✓ Automatically sync to PostHog when device comes online
 * ✓ Clear synced events to save space
 * ✓ Track DAU/MAU/Retention (handled by PostHog)
 * ✓ Calculate completion rates, session lengths, total listening time
 * ✓ Track songs in order of play
 * ✓ Store search queries
 */
