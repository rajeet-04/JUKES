Implement Instant Playback (Streaming)
Goal Description
Enable "Instant Playback" so that songs start playing immediately via streaming while they are being downloaded. The downloaded data should be cached and saved to the permanent storage (music/ directory) upon completion.

User Review Required
IMPORTANT

This plan shifts the primary "Instant Play" source to Spotmate because it provides a direct GET URL (https://...) compatible with standard streaming players. Spotdown requires a POST request with a JSON body, which is complex to adapt for standard ExoPlayer streaming without a proxy.

Proposed Changes
1. Network Layer (SpotifyApi.kt)
Expose the 
Spotmate
 download URL extraction logic as a separate function: getStreamUrl(spotifyUrl: String): String.
Returns the direct MP3 link.
2. Service Layer (PlaybackService.kt)
Initialize ExoPlayer Cache:
Add SimpleCache and LeastRecentlyUsedCacheEvictor to 
PlaybackService
.
Configure DefaultDataSource.Factory to use CacheDataSource.
Modify Media Item Creation:
Update 
createValidatedMediaItem
 (or add a new path) to handle Remote URIs.
If the URI is http/https, use the CacheDataSource preventing "double download" if the user replays it.
3. Application Processing (MusicService/ViewModel)
Hybrid Download/Play Flow:
When 
playTrack
 is called for a non-downloaded song:
Fetch the Stream URL (via Spotmate).
Create a temporary 
Track
 object (or pseudo-track) with this Remote URI.
Tell 
PlaybackManager
 to play this Remote URI immediately.
Simultaneously: Trigger a background "Persistence Task".
Persistence Task:
Since ExoPlayer is caching the file to its internal cache directory (e.g., cache/downloads), we need to "finalize" it to music/{uuid}.mp3 to match existing app logic.
Option A (Simpler): Change app to read from Cache for these songs?
No, we want consistency.
Option B (Selected): Use a DownloadManager or monitor the cache.
Refined Approach:
Actually, we can use DataSpec with CacheWriter to download into the cache ahead of the player if needed, or just let the Player populate the cache.
Once the song is fully played (or fully buffered), export the file from the Cache to the music/ directory and update the Database.
Detailed Steps
Refactor SpotifyApi: Extract getSpotmateDownloadUrl.
Setup Cache in 
PlaybackService
:
Create StandaloneDatabaseProvider.
Create SimpleCache(file, evictor, databaseProvider).
Inject CacheDataSource.Factory into ExoPlayer.Builder.
Update 
MusicViewModel
:
If song not local:
Get URL.
playbackManager.playRemote(url, metadata).
musicService.queuePersistence(url, uuid) -> waits for download to finish (or uses Ktor to download in parallel if we don't trust ExoPlayer cache export).
Wait, downloading twice is wasteful.
Better Path:
Use Ktor to download to music/{uuid}.mp3 (as we do now).
BUT, PLAY from a FileDataSource that handles growing files? No, ExoPlayer struggles with that.
Best Path:
Use ExoPlayer to play the Network URL.
Enable Caching.
When 
Player
 reports STATE_BUFFERING -> finished? No.
Maybe just keep using Ktor for "Download" and use ExoPlayer for "Stream". If the user streams, we accept double data usage for that session?
User Request: "starts instant playback from received data in cache, downloaded song is saved in storage". This explicitly asks for Single Data Stream.
Single Stream Solution:
Use ExoPlayer CacheDataSource.
Play stream.
Once file is fully cached, it lives in cacheDir.
We can leave it there? The app expects localUri. We can point localUri to the cached file path?
ExoPlayer cache implementation fragments files. We cannot point localUri to a single file.
Action: We must use CacheUtil (or CacheWriter) to copy the cached data to a single .mp3 file after playback/buffering completes.
Verification Plan
Streaming: Click a new song. Should start playing in <2s (depending on network) instead of waiting for full download.
Caching: Disconnect network mid-song. Playback should continue if buffered.
Persistence: After song finishes (or is fully buffered), check music/ folder. The .mp3 file should appear and database should update from "Network" to "Local".