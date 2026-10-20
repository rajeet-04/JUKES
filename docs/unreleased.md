# Unreleased Changes

## [Unreleased]

### Added

### Fixed

- Search no longer fails when Spotify returns a playlist with a null owner (`SpotifyUser` fields are now nullable).
- Restarting the app no longer fetches new recommendations when the restored queue already holds the lookahead count.

- Android Auto: list-style browse (Recently Played, Favorites, Downloaded), voice/search playback, resume on reconnect, and cover art. See `ANDROID_AUTO.md`.
- The search field no longer takes focus or brings up the keyboard while the full player is open.
- Spotify credentials now live in a Cloudflare Worker (`spotdown-kv-worker`, token cached in KV for 3300 s). The app no longer ships the client id/secret; it needs `SPOTIFY_TOKEN_KEY` instead.
- Lyrics are fetched from `lrclib.net` directly instead of through the shared Cloudflare worker, which was hitting lrclib IP rate limits (HTTP 429).
- Synced lyrics keep the active line at its reading position; the auto-scroll is no longer cancelled partway by position updates.
- Player: with synced lyrics, the artwork rides up and the current lyric line shows under it (tap opens full lyrics).
- Lyrics: inline word-sync timestamps no longer show inside lyric lines; the player lyric line is left-aligned and bold.
- Unsynced lyrics are left-aligned and larger, clear of the close button, and the scroll position is remembered per song when the lyrics are closed and reopened.
- Playlists: new "Custom order" sort. Drag the handle on a song to reorder it; the order is saved per playlist and kept across restarts. Opening a playlist starts in its custom order; other sorts remain available.
- Playlists no longer get the same song twice (playlist import and saving the queue skip a song already in the playlist); existing repeats are cleaned up once on update.
- Fixed a crash when reordering a playlist that held the same song twice.
- Queue: dragging a song now slides the other rows aside to make room, like in playlists; the queue is changed once on drop.
