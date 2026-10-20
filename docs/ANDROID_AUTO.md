# Android Auto

`PlaybackService` is a `MediaLibraryService`; the browse tree and item building live in `services/AutoLibrary.kt`.

- **Browse tree:** root → Recently Played, Favorites, Downloaded (max `AutoLibrary.MAX_ITEMS` each). Folders are shown as list rows via the content-style hints returned from `onGetLibraryRoot`.
- **Light queries:** `TrackDao.get*Light` / `searchTracksLight` return rows with the lyrics columns nulled, so long lists can't overflow the CursorWindow.
- **Playing:** `onAddMediaItems` resolves bare media ids and voice/search queries into playable items (library first, then Spotify search + stream), then starts the radio with `QueueManager.initializeQueue`.
- **Resume:** a "recent" root request and `onPlaybackResumption` restore the last played song when the car reconnects.
- **Search:** `onSearch` / `onGetSearchResult` search the local library.
- **Cover art:** `ArtworkProvider` (`content://<applicationId>.artwork/<track uuid>`) serves a track's thumbnail read-only, because Auto runs in another process and can't open app files.

Tests: `AutoLibraryTest`.
