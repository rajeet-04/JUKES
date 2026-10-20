# Unreleased Changes

## [Unreleased]

### Added

### Fixed

- Search no longer fails when Spotify returns a playlist with a null owner (`SpotifyUser` fields are now nullable).
- Restarting the app no longer fetches new recommendations when the restored queue already holds the lookahead count.
