# Unreleased Changes

## [Unreleased]

### Added

- **Stream Playback Caching**: Integrated ExoPlayer `SimpleCache` with a 256MB LRU disk cache. Seeking back to already-buffered parts of a stream is now instant and doesn't require re-fetching from the network.
- **Artist Blacklist UI**:
  - Added "Blacklist Artist" option to the Player Screen more options menu.
  - Added a dedicated Blacklist toggle button on the Artist Detail screen.
  - Added a "Blacklisted Artists" management section in Audio Settings.

### Fixed

- **Radio Mode Pause**: Optimized `startRadio` to surgically trim the playback queue using `removeMediaItem` instead of resetting the entire player. This prevents the noticeable audio drop when starting radio mode.
- **Foreground Service Crash**: Added robust exception handling for `ForegroundServiceStartNotAllowedException` when the app is in the background on Android 12+.
- **Shuffle Logic**: Fixed a bug where the shuffle toggle failed to correctly update the playback queue or track sequencing.
- **Search Screen Crash**: Resolved `IllegalArgumentException` in the search results list caused by duplicate keys in `LazyColumn`.
- **Stream Seek Crash**: Fixed `MalformedURLException` (no protocol) when playing local files through the new `CacheDataSource` by using `DefaultDataSource` as the upstream factory.
