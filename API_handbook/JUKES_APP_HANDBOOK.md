# JUKES app integration handbook

A practical guide for wiring the JUKES Android app (Kotlin, Ktor client) to the new
backend. The exact field-by-field contract is in [`JUKES_API.md`](JUKES_API.md); this
handbook is the "how do I build it" companion: flows, a drop-in Kotlin client,
state handling, and a test checklist.

> Verified against the backend code and the JUKES beta source. **Not verified:**
> playback on a real device and the app's per-call timeout overrides. Test those.

---

## 1. Mental model (read this first)

```
search/hover ─▶ POST /v1/warmup        (speculative, fire-and-forget)
tap play     ─▶ POST /v1/audio/prepare ─▶ 200 ready | 202 queued/downloading
                 │ poll GET /v1/jobs/{id}  (1s,2s,3s,3s… ≤ ~2min)
                 ▼
               status=ready ─▶ play audio_url (ExoPlayer, byte ranges)
```

- The server downloads the **whole file first**, then serves it with `Content-Length`
  and ranges. There is no live stream; "preparing" is a real state your UI must show.
- One job per track, shared by everyone. Calling warmup/prepare repeatedly is safe
  and free when the file is cached.
- Anonymous by default. No login. Personalisation is optional (section 7).
- Two caches: **requested** (10 GB, LRU, no expiry) and **warmup** (1 GB, 2 h TTL).
  `prepare` promotes a warmup in place, even mid-download.

## 2. Configuration

| Item | Value |
|---|---|
| Base URL | your `PUBLIC_BASE_URL` (HTTPS, via Cloudflare) |
| Auth | none for audio/search. Optional `Authorization: Bearer <installation token>` |
| Content type | send `Content-Type: application/json` on POST |
| Durations | `/v1` = **milliseconds**; legacy `/audio/?duration=` = **seconds** |
| Key gate | The current app only enables the backend when URL **and** key are non-blank. The key value is ignored now, but keep `JUKE_BACKEND_KEY` non-empty (e.g. `"unused"`) until the gate is relaxed. |

Server limits to design around: 30 warmups/min, 60 new prepares/min, 120 polls/min
per client IP; token issuance 5/min. Duplicate requests for a track that is already
downloading/cached do not use the "new work" quota.

## 3. Kotlin client (drop-in)

Uses `kotlinx.serialization` and the app's existing `ApiClient.httpClient`.

```kotlin
@Serializable
data class JukesTrack(
    @SerialName("video_id") val videoId: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val artist: String = "",
    val album: String? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
)

@Serializable
data class JukesPrepared(
    @SerialName("video_id") val videoId: String,
    val title: String = "",
    val artists: List<String> = emptyList(),
    val artist: String = "",
    val album: String? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("job_id") val jobId: String,
    val status: String,                       // queued | downloading | ready | failed
    val pool: String,                         // warmup | requested
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("personalization_status") val personalizationStatus: String = "anonymous",
)

@Serializable data class JukesJob(
    @SerialName("job_id") val jobId: String,
    @SerialName("video_id") val videoId: String,
    val status: String,
    val pool: String = "requested",
    @SerialName("audio_url") val audioUrl: String? = null,
    val error: JukesJobError? = null,
)
@Serializable data class JukesJobError(val code: String, val retryable: Boolean)

@Serializable data class JukesErrorBody(val error: Detail) {
    @Serializable data class Detail(val code: String, val message: String = "", val retryable: Boolean = false)
}

sealed class JukesResult<out T> {
    data class Ok<T>(val value: T) : JukesResult<T>()
    data class Fail(
        val http: Int, val code: String, val retryable: Boolean, val retryAfterSec: Int?,
    ) : JukesResult<Nothing>()
}

class JukesApi(
    private val base: String,
    private val client: HttpClient = ApiClient.httpClient,
    private val tokenProvider: () -> String? = { null },   // installation token, if any
) {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend inline fun <reified T> call(
        method: HttpMethod, path: String, body: Any? = null,
    ): JukesResult<T> {
        val r = client.request("${base.trimEnd('/')}$path") {
            this.method = method
            tokenProvider()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
        }
        val text = r.bodyAsText()
        if (r.status.value in 200..299) return JukesResult.Ok(json.decodeFromString(text))
        val e = runCatching { json.decodeFromString<JukesErrorBody>(text).error }.getOrNull()
        return JukesResult.Fail(
            r.status.value, e?.code ?: "http_${r.status.value}", e?.retryable ?: false,
            r.headers[HttpHeaders.RetryAfter]?.toIntOrNull(),
        )
    }

    /** Speculative. Ignore the result; never block UI on it. */
    suspend fun warmupByTitle(title: String, artist: String, durationMs: Long?) =
        call<JukesPrepared>(HttpMethod.Post, "/v1/warmup", buildJsonObject {
            put("title", title); put("artist", artist); durationMs?.let { put("duration_ms", it) }
        })

    suspend fun prepareById(videoId: String) =
        call<JukesPrepared>(HttpMethod.Post, "/v1/audio/prepare", buildJsonObject { put("video_id", videoId) })

    suspend fun prepareByTitle(title: String, artist: String, durationMs: Long?) =
        call<JukesPrepared>(HttpMethod.Post, "/v1/audio/prepare", buildJsonObject {
            put("title", title); put("artist", artist); durationMs?.let { put("duration_ms", it) }
        })

    suspend fun job(jobId: String) = call<JukesJob>(HttpMethod.Get, "/v1/jobs/$jobId")

    suspend fun radio(videoId: String, limit: Int = 25) =
        call<JukesRadio>(HttpMethod.Post, "/v1/recommendations", buildJsonObject {
            put("video_id", videoId); put("limit", limit)
        })
}

@Serializable data class JukesRadio(
    val tracks: List<JukesTrack>,
    @SerialName("personalization_status") val personalizationStatus: String = "anonymous",
)
```

> `call` must send `buildJsonObject` as a `JsonObject` body (Ktor content negotiation
> with kotlinx.serialization). Set a body only on POST/PUT.

### Resolve → verify → play

```kotlin
suspend fun resolveForPlayback(t: Track, api: JukesApi): Result<String> {
    val first = api.prepareByTitle(t.title, t.mainArtist, t.durationMs)
    val prepared = (first as? JukesResult.Ok)?.value
        ?: return Result.failure(JukesException(first as JukesResult.Fail))

    // 1. VERIFY: the backend picks the best match; the app makes the final call.
    check(isSameSong(prepared, t)) { "backend matched '${prepared.title}' by ${prepared.artist}" }
    prepared.durationMs?.let { found ->
        t.durationMs?.let { want -> check(abs(found - want) <= max(8_000, want * 7 / 100)) }
    }

    // 2. WAIT for ready (poll with backoff)
    if (prepared.status == "ready" && prepared.audioUrl != null) return Result.success(prepared.audioUrl)
    return pollUntilReady(api, prepared.jobId)
}

suspend fun pollUntilReady(api: JukesApi, jobId: String, maxMs: Long = 120_000): Result<String> {
    val delays = longArrayOf(1000, 2000, 3000)
    var waited = 0L; var i = 0
    while (waited < maxMs) {
        when (val r = api.job(jobId)) {
            is JukesResult.Ok -> when (r.value.status) {
                "ready" -> return Result.success(r.value.audioUrl!!)
                "failed" -> return Result.failure(JukesJobFailed(r.value.error))
            }
            is JukesResult.Fail -> if (!r.retryable && r.http != 404) return Result.failure(JukesException(r))
            // 404 job_not_found: the job aged out. Not "audio deleted" — call prepare again.
        }
        val d = delays.getOrElse(i++) { 3000 }
        delay(d); waited += d
    }
    return Result.failure(TimeoutException("still preparing"))
}
```

## 4. Playing the audio (ExoPlayer)

- Pass `audio_url` straight to `MediaItem` / `ProgressiveMediaSource`. The server
  supports `Range`, `If-Range`, `ETag`, `Content-Length`; seeking works immediately.
- The content type is the **real container** (`audio/mp4` normally, `audio/webm`
  possible). Ignore any file-name suffix; do not force a MIME type from the URL.
- `GET /v1/audio/{id}` on a missing track returns **503 `pending`** with `Retry-After`
  (and starts the download). Treat it as "go poll", not as a failure. Never hold the
  socket open waiting.
- Segmented/parallel range downloads (for offline) are fine and cheap.
- Do not cache `audio_url` across app restarts as a promise the file exists: caches
  evict. On 404 `not_ready`, call `prepare` again.

## 5. Warmup rules of thumb

| Do | Don't |
|---|---|
| Debounce search (≥400 ms), warm the **single** likeliest result | Warm every search row |
| Warm the *next* track in a queue shortly before it is needed | Warm entire playlists at once (30/min limit, 1 GB pool) |
| Ignore warmup failures and 429s silently | Show errors to the user for a warmup |
| Still call `prepare` on tap (promotes the warmup) | Assume a warmup means "ready" |

## 6. Error handling table

Every error is `{"error":{"code","message","retryable"}}`. Retryable ones carry `Retry-After`.

| HTTP | `code` | What the app should do |
|---|---|---|
| 400 | `invalid_request` | Bug in the app. Log, don't retry. |
| 401 | `invalid_token` | Token revoked/expired. Drop it, re-register (section 7), reconnect. |
| 404 | `no_match` | No such song here. Fall through to the next provider. |
| 404 | `video_unavailable` | Dead/private video. Next provider. |
| 404 | `not_ready` (HEAD) | File not cached. Call prepare. |
| 413 | `track_too_large` / `warmup_too_large` | Skip this source. |
| 422 | `match_rejected` | Wrong version/duration. Next provider (or relax duration). |
| 422 | `public_audio_required` | Not public. Next provider. |
| 429 | `rate_limited` / `queue_full` | Wait `Retry-After`, then retry once. |
| 502 | `upstream_error`, `extraction_failed`, `extraction_timeout` | Retryable once; then next provider. |
| 503 | `pending` | Poll the job. |
| 503 | `cache_capacity`, `rate_limited` | Wait `Retry-After`, retry. |
| 503 | `personalization_unavailable` | Backend has no key. Continue anonymously. |

Job `failed` + `error.retryable=true` → issue a fresh `prepare` (a new job is created).

## 7. Optional personalisation

```kotlin
// 1) once per install
val token: String = (api.issueInstallation() as Ok).value.token   // POST /v1/installations (201)
secureStorage.put("jukes_token", token)                           // EncryptedSharedPreferences

// 2) after the user signs in to YouTube Music in the app's WebView
api.connectYoutube(cookieHeader, accountIndex = 0)                // PUT /v1/me/youtube

// 3) every radio call carries the bearer token via tokenProvider
```

- `PUT /v1/me/youtube` body is exactly `{"cookie": "...", "account_index": 0}`. The
  cookie string must contain `__Secure-3PAPISID`. Re-send it on every app-side
  session refresh. A failed validation (`422 session_rejected`) keeps the old session.
- `personalization_status` on prepare/radio responses:
  `anonymous` · `connected` · `reconnect_required` (expired: re-capture and PUT again)
  · `personalization_unavailable` (backend outage: keep the session, retry later).
- Disconnect: `DELETE /v1/me/youtube`. Delete account/token: `DELETE /v1/me`.
- **Never log the token or cookie.** Add them to your HTTP logger's redaction list.
- Lose the token ⇒ a new installation and a fresh connect (the old one idles out).

## 8. Radio / recommendations

```kotlin
val radio = (api.radio(currentVideoId, limit = 25) as Ok).value
// radio.tracks: YouTube Music order, seed first.
```

The backend does **not** filter, dedupe or reseed. Feed `radio.tracks` into the app's
existing fetch → filter → dedup → reseed engine in place of the old radio source.

## 9. Migrating from the old endpoints

| Old app call | New |
|---|---|
| `GET /audio/?q=…&duration=<sec>&info=1` | still works (JSON: `video_id,title,artist,duration_ms,cached,audio_url`). Prefer `POST /v1/audio/prepare`. |
| `GET /audio/?video_id=…&wait=1` | still works; waits ≤25 s then `503` + `Retry-After` (download continues). The app's socket timeout is 60 s, so this is safe. Prefer prepare + poll. |
| `/alexa/search`, `/get_stream`, `/proxy`, `/get_radio` | **removed** (JSON 404). The app only falls back to them when `/audio/` 404s with non-JSON, which no longer happens. |
| `X-Api-Key` header / `key` query param | ignored, never logged |

A suggested rollout: (1) ship prepare+poll behind a flag, keep `/audio/` as the
fallback; (2) add warmup; (3) switch radio; (4) optional personalisation.

## 10. Test checklist (do these on a real device)

- [ ] Cold play: tap → "preparing" UI → plays; seek works the moment it starts.
- [ ] Warmup then play: noticeably faster than cold.
- [ ] Airplane mode mid-poll → resumes polling after reconnect; no crash.
- [ ] Kill the app during preparing; relaunch; replay works (prepare is idempotent).
- [ ] `video_unavailable` → falls to next provider.
- [ ] 429 → honours `Retry-After`; no tight retry loop.
- [ ] `audio/mp4` and (if you can force one) `audio/webm` both play in ExoPlayer.
- [ ] Wrong-song guard: a deliberately mismatched title is rejected by `isSameSong`.
- [ ] Token/cookie never appear in `adb logcat`.
- [ ] Optional: connect → radio shows `connected`; disconnect → `anonymous`.

## 11. Quick `curl` reference

```bash
B=https://your-host
curl -s -X POST $B/v1/audio/prepare -H 'content-type: application/json' \
     -d '{"title":"Never Gonna Give You Up","artist":"Rick Astley","duration_ms":213000}'
curl -s $B/v1/jobs/<job_id>
curl -s -H 'Range: bytes=0-1023' -o /dev/null -w '%{http_code} %{content_type}\n' $B/v1/audio/lYBUbBu4W08
curl -s -X POST $B/v1/recommendations -H 'content-type: application/json' \
     -d '{"video_id":"lYBUbBu4W08","limit":10}'
curl -s "$B/audio/?q=never+gonna+give+you+up+rick+astley&duration=213&info=1"   # legacy
```
