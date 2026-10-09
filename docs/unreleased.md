# Unreleased Changes

## [Unreleased]

### Added

- **Weighted cross-session variety for recommendations.** The same seed song used to give the same top-5 radio songs every session. A new `song_exposure` table (DB v11) keeps decaying per-song scores for plays (weight 1.0, half-life 3d), early skips (1.5, 7d) and recommendations (0.4, 2d). Candidates are drawn by weighted random sampling (radio rank × freshness × artist spread) instead of top-N. Favourites and songs on repeat today are exempt; early skips (<30s, forward) are recorded from the player; reserve refills pull up to two radios; the offline library fallback uses the same penalty. Scores survive stream purges and are pruned after 60 days.
- **Progressive playback, on by default.** Songs played immediately start on the backend's AAC HLS stream while the file downloads; the background save waits for the completed `audio_url`. Playlists are never downloaded; queued recommendations and downloads keep the completed-file path. A switch in Audio settings turns it off. HLS items start at 0:00 at 1x, playlists bypass the ExoPlayer disk cache, and `Progressive:` timing lines are logged (tag `JukesApi`). Adds `media3-exoplayer-hls`.
- **Spotify and YouTube links open in JUKES.** Share a link to JUKES or tap it in a browser: `open.spotify.com` track/album/artist/playlist (including `intl-xx` paths), `spotify:` URIs, `spotify.link` short links (resolved by following the redirect), `music.youtube.com/watch` and `youtu.be`. Spotify links resolve exactly; YouTube links search for the song named by the video (oEmbed title and channel). The bare `open.spotify.com` homepage does not open JUKES.
- **Faster cold start.** A baseline and startup profile (new `:baselineprofile` module, `profileinstaller`) ships in release builds. The HTTP client, Room database and stream cache are built on a background thread at launch, and PostHog setup is delayed 1.5 s. On the I2220, time to first frame went from ~477 ms (old debug) to ~155–215 ms (release).

### Fixed

- **Evicted/deleted stream audio is recovered instead of skipped.** `validateUpcomingStreams()` checks the current and next 3 queue items on every transition and after restore, re-downloading missing streams; the ENOENT handler re-downloads and resumes in place, skipping only if that fails. A shared `recoverMissingStream()` pins the queue and dedupes concurrent recoveries.
- **Recovery covers any cache clear, not just LRU eviction.** `LocalAudio.isMissing()` is the single on-disk check (replacing trust in the DB `localUri`); downloads are re-downloaded into the same record; `smartDownloadAndIndex` re-downloads when the row's file is gone (previously returned the stale path); generic source errors with a missing local file go through recovery; startup reconcile clears `localUri` for stream rows whose files were deleted outside the app.
- **Non-blocking queue restore.** `restorePlaybackState` loads the queue from the DB with no network; only the current track is re-resolved (15s cap, queue pinned). Unplayable tracks are dropped with the index remapped, and restore is skipped if the user already started playback. Stream LRU eviction now clears `local_uri` so the DB never points at deleted files.
- **Spotify token response no longer logged.** The OAuth response body (which contains the access token) and credential details were written to logcat.
