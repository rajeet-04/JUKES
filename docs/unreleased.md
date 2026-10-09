# Unreleased Changes

## [Unreleased]

### Added

- **Weighted cross-session variety for recommendations.** The same seed song used to give the same top-5 radio songs every session. A new `song_exposure` table (DB v11) keeps decaying per-song scores for plays (weight 1.0, half-life 3d), early skips (1.5, 7d) and recommendations (0.4, 2d). Candidates are drawn by weighted random sampling (radio rank × freshness × artist spread) instead of top-N. Favourites and songs on repeat today are exempt; early skips (<30s, forward) are recorded from the player; reserve refills pull up to two radios; the offline library fallback uses the same penalty. Scores survive stream purges and are pruned after 60 days.
- **Experimental progressive playback** (Power Tools → Experimental → Progressive playback, off by default). Songs played immediately start on the backend's AAC HLS stream while the file downloads; the background save waits for the completed `audio_url`. Playlists are never downloaded; queued recommendations and downloads keep the completed-file path. HLS items start at 0:00 at 1x, playlists bypass the ExoPlayer disk cache, and `Progressive:` timing lines are logged (tag `JukesApi`) for on-device comparison. Adds `media3-exoplayer-hls`.

### Fixed

- **Evicted/deleted stream audio is recovered instead of skipped.** `validateUpcomingStreams()` checks the current and next 3 queue items on every transition and after restore, re-downloading missing streams; the ENOENT handler re-downloads and resumes in place, skipping only if that fails. A shared `recoverMissingStream()` pins the queue and dedupes concurrent recoveries.
- **Recovery covers any cache clear, not just LRU eviction.** `LocalAudio.isMissing()` is the single on-disk check (replacing trust in the DB `localUri`); downloads are re-downloaded into the same record; `smartDownloadAndIndex` re-downloads when the row's file is gone (previously returned the stale path); generic source errors with a missing local file go through recovery; startup reconcile clears `localUri` for stream rows whose files were deleted outside the app.
- **Non-blocking queue restore.** `restorePlaybackState` loads the queue from the DB with no network; only the current track is re-resolved (15s cap, queue pinned). Unplayable tracks are dropped with the index remapped, and restore is skipped if the user already started playback. Stream LRU eviction now clears `local_uri` so the DB never points at deleted files.
