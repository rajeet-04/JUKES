# External Integrations

**Analysis Date:** 2026-05-19

## APIs & External Services

**[Music Streaming]:**
- Spotify - Official music search and metadata retrieval
  - SDK/Client: Custom Ktor-based implementation in `SpotifyApi.kt`
  - Auth: OAuth 2.0 Client Credentials flow using `SPOTIFY_CLIENT_ID` and `SPOTIFY_CLIENT_SECRET` from BuildConfig
  - Endpoints: 
    - Accounts: `https://accounts.spotify.com/api/token` (token exchange)
    - Web API: `https://api.spotify.com/v1` (search, artist/album/track/playlist data)
  - Used in: `SpotifyApi.kt` for search, artist albums, top tracks, track/artist/album/playlist details

**[Lyrics]:**
- LRCLib - Synchronized and plain text lyrics
  - SDK/Client: Direct HTTP calls via Ktor in `SpotifyApi.kt` (`searchLyrics` function)
  - Auth: None required (public API)
  - Endpoint: `https://lrclib.meek.workers.dev`
  - Used in: `SpotifyApi.kt` for fetching lyrics with validation

**[YouTube]:**
- YouTube Music & YouTube - Lyrics and captions fallback
  - SDK/Client: Direct HTTP calls via Ktor in `SpotifyApi.kt` (`getYoutubeMusicLyrics`, `getYoutubeCaptions` functions)
  - Auth: None required (uses Innertube API with hardcoded client context)
  - Endpoints:
    - YouTube Music: `https://music.youtube.com/youtubei/v1/`
    - YouTube: `https://www.youtube.com/youtubei/v1/`
  - Used in: `SpotifyApi.kt` as fallback when LRCLib doesn't have lyrics

**[Audio Download]:**
- Spotmate - MP3 conversion and download service
  - SDK/Client: Direct HTTP calls via Ktor in `SpotifyApi.kt` (multiple functions)
  - Auth: None required (handles session/CSRF tokens dynamically)
  - Endpoint: `https://spotmate.online`
  - Used in: `SpotifyApi.kt` for getting stream URLs, download requests, and actual downloads

**[Audio Download]:**
- Gamepvz - Alternative MP3 download service
  - SDK/Client: Direct HTTP calls via Ktor in `SpotifyApi.kt` (multiple functions)
  - Auth: None required (requires specific User-Agent and Referer headers)
  - Endpoint: `https://gamepvz.com`
  - Used in: `SpotifyApi.kt` as fallback when Spotmate fails

**[Analytics]:**
- PostHog - Event tracking and analytics
  - SDK/Client: PostHog Android SDK (`com.posthog:posthog-android:3.40.2`)
  - Auth: API key from `POSTHOG_API_KEY` BuildConfig field
  - Host: Configurable via `POSTHOG_HOST` BuildConfig field
  - Used in: `AnalyticsManager.kt` for initialization and event capture

## Data Storage

**Databases:**
- SQLite via Room
  - Connection: Local database instance
  - Client: Room ORM with KSP (`androidx.room:room-runtime`, `androidx.room:room-ktx`, `androidx.room:room-compiler`)
  - Schema: Defined in `MusicDatabase.kt` and entity classes in `database/` package
  - Used for: Storing music metadata, playlists, user preferences

**File Storage:**
- Local filesystem only (internal app storage)
  - Used for: Caching downloaded MP3 files, analytics event queue persistence
  - Directories: App-specific internal storage accessed via Context methods

**Caching:**
- In-memory caches:
  - Spotify API token caching in `SpotifyApi.kt` (`accessToken`, `tokenExpiryTime`)
  - LRCLib results caching implied in `searchLyrics` function
  - Network response caching via OkHttp (configured in `ApiClient.kt`)
- No external caching service (Redis/Memcached) detected

## Authentication & Identity

**Auth Provider:**
- Spotify OAuth 2.0 (Client Credentials flow)
  - Implementation: Custom token management in `SpotifyApi.kt`
  - Token storage: Memory-only with automatic refresh before expiry
  - Scope: Limited to what's needed for search and metadata (no user-specific data)
  - Credentials: Stored in `local.properties` (never committed), loaded into BuildConfig

## Monitoring & Observability

**Error Tracking:**
- None detected (beyond basic Android logging)

**Logs:**
- Android Logcat with tag-based filtering:
  - AnalyticsManager: Uses "AnalyticsManager" tag
  - SpotifyApi: Uses "SpotifyApi" tag
  - Various levels: DEBUG, ERROR, WARNING based on BuildConfig.DEBUG
  - Configured in: `ApiClient.kt` (Ktor Logging plugin), `AnalyticsManager.kt` (android.util.Log)

## CI/CD & Deployment

**Hosting:**
- Google Play Store (implied by Android app)

**CI Pipeline:**
- GitHub Actions (inferred from `.github` directory presence)
- Specific workflows not examined in this analysis

## Environment Configuration

**Required env vars (in local.properties):**
- SPOTIFY_CLIENT_ID - Spotify API client ID
- SPOTIFY_CLIENT_SECRET - Spotify API client secret
- POSTHOG_API_KEY - PostHog project API key
- POSTHOG_HOST - PostHog instance host (optional, defaults to app.posthog.com)

**Secrets location:**
- `local.properties` file in project root (gitignored via `.gitignore`)
- Loaded at build time to generate BuildConfig constants
- Never committed to version control

## Webhooks & Callbacks

**Incoming:**
- None detected (no webhook servers or listeners implemented)

**Outgoing:**
- None detected (all integrations are client-initiated requests)

---

*Integration audit: 2026-05-19*