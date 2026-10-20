# Unreleased Changes

## [Unreleased]

### Added

### Fixed

- Search no longer fails when Spotify returns a playlist with a null owner (`SpotifyUser` fields are now nullable).
- Restarting the app no longer fetches new recommendations when the restored queue already holds the lookahead count.

- Android Auto: list-style browse (Recently Played, Favorites, Downloaded), voice/search playback, resume on reconnect, and cover art. See `ANDROID_AUTO.md`.
- The search field no longer takes focus or brings up the keyboard while the full player is open.
