# External Integrations

**Analysis Date:** 2026-03-08

## APIs & External Services

**Music Streaming:**
- Spotify Web API - Music metadata and search
  - SDK/Client: Ktor HTTP client
  - Auth: SPOTIFY_CLIENT_ID, SPOTIFY_CLIENT_SECRET env vars

**Music Download:**
- Spotdown API - MP3 download service
  - SDK/Client: Custom Ktor integration
  - Auth: SPOTDOWN_WORKER_URL for API key retrieval

**Lyrics:**
- LRCLib API - Plain text and synced lyrics
  - SDK/Client: Ktor HTTP client
  - Auth: Anonymous access

**Analytics:**
- PostHog - User behavior tracking
  - SDK/Client: PostHog Android SDK
  - Auth: POSTHOG_API_KEY constant

**GitHub:**
- GitHub API - Release and update checking
  - SDK/Client: Custom Ktor integration
  - Auth: Anonymous access

## Data Storage

**Databases:**
- SQLite (via Room ORM)
  - Connection: Embedded in app
  - Client: Room persistence library

**File Storage:**
- Local filesystem
  - Connection: Android Storage APIs
  - Client: Android File and MediaStore APIs

**Caching:**
- In-memory caches (Coroutine-based)
- Image cache (Coil library)

## Authentication & Identity

**Auth Provider:**
- Spotify - OAuth2 Client Credentials Flow
  - Implementation: Custom Ktor-based implementation in SpotifyApi.kt

## Monitoring & Observability

**Error Tracking:**
- PostHog - Event tracking and analytics
  - Implementation: AnalyticsManager with custom events

**Logs:**
- Android Log - Standard Android logging facility

## CI/CD & Deployment

**Hosting:**
- Google Play Store - Primary distribution platform

**CI Pipeline:**
- GitHub Actions - Automated builds and testing

## Environment Configuration

**Required env vars:**
- SPOTIFY_CLIENT_ID - Spotify developer app client ID
- SPOTIFY_CLIENT_SECRET - Spotify developer app client secret
- SPOTDOWN_WORKER_URL - Cloudflare worker URL for Spotdown API key

**Secrets location:**
- local.properties file (not committed to version control)

## Webhooks & Callbacks

**Incoming:**
- Media session callbacks - System media controls integration
- Notification intents - User interaction with playback notifications

**Outgoing:**
- Spotify API webhooks - Metadata and streaming integration
- Analytics events - User behavior tracking to PostHog

---

*Integration audit: 2026-03-08*