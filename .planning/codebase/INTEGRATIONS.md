# External Integrations

**Analysis Date:** 2026-04-16

## APIs & External Services

**Music Streaming & Metadata:**
- **Spotify Web API** - Music search, metadata, OAuth authentication
  - SDK/Client: Custom Ktor-based `SpotifyApi.kt`
  - Auth: OAuth 2.0 Client Credentials Flow
  - Endpoints: search, tracks, artists, albums, playlists

- **YouTube Music API** - Music recommendations via unofficial API
  - SDK/Client: Custom Ktor-based `RecommenderApi.kt`
  - Method: HTTP POST to internal scraper
  - Purpose: Radio queue generation, video matching

**Download Services:**
- **Spotmate (spotmate.online)** - Primary MP3 download source
  - SDK/Client: Custom HTTP client with CSRF handling
  - Auth: Cookie-based session
  - Fallback: Task polling for queued conversions

- **Gamepvz (gamepvz.com)** - Secondary MP3 download source
  - SDK/Client: Custom Ktor-based `ApiClient.kt`
  - Auth: User-Agent and Referer headers

- **mp3juice3.ninja** - YouTube data scraper
  - SDK/Client: Custom HTTP POST
  - Purpose: Video ID lookup

**Lyrics:**
- **LRCLib (lrclib.meek.workers.dev)** - Synced and plain lyrics
  - SDK/Client: Custom HTTP GET
  - Matching: Track name, artist, duration

**Analytics:**
- **PostHog** - Usage analytics
  - SDK: `com.posthog:posthog-android:3.40.2`
  - Host: `https://us.i.posthog.com`
  - API Key: Configured in `JukeApplication.kt`

## Data Storage

**Databases:**
- SQLite via Room 2.6.1
  - Database name: `music_database`
  - Entities: `TrackEntity`, `PlaylistEntity`, `PlaylistTrackEntity`
  - Version: 8

**File Storage:**
- **Local filesystem** (app-internal)
  - Audio files: `filesDir/music/*.mp3`
  - Thumbnails: `filesDir/music/*_thumb.jpg`
  - Stream cache: `cacheDir/stream_cache/` (ExoPlayer managed)
  - Stream files: `filesDir/stream_files/*.mp3`

## Authentication & Identity

**Spotify:**
- OAuth 2.0 Client Credentials Flow
- Credentials stored in `local.properties` (not committed)
- Loaded at build time via `BuildConfig`

**Analytics:**
- Anonymous user tracking via PostHog
- User ID generated on first install (UUID)
- Stored in SharedPreferences

## Monitoring & Observability

**Error Tracking:**
- Android Logcat (Log.e for errors)
- PostHog for crash/event analytics

**Logs:**
- `android.util.Log` throughout codebase
- Tagged loggers: `TAG` constants per class
- Levels: DEBUG (d), INFO (i), WARN (w), ERROR (e)

## CI/CD & Deployment

**Hosting:**
- GitHub Releases for APK distribution

**CI Pipeline:**
- None detected (manual builds)

## Environment Configuration

**Required env vars:**
- `sdk.dir` - Android SDK path
- `SPOTIFY_CLIENT_ID` - Spotify app client ID
- `SPOTIFY_CLIENT_SECRET` - Spotify app client secret

**Secrets location:**
- `local.properties` (gitignored, not committed)

## Webhooks & Callbacks

**Incoming:**
- None

**Outgoing:**
- None detected

---

*Integration audit: 2026-04-16*
