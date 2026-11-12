# Calling YouTube’s Two Observed Lyrics Endpoints

## Scope and evidence

This guide documents two browser captures for the same track, “Ji Huzoor” by Sparsh. They are **different products and different data types**:

| Product surface | Captured endpoint | Result |
|---|---|---|
| YouTube Music Lyrics panel | `POST https://music.youtube.com/youtubei/v1/browse?prettyPrint=false` | LyricFind-attributed text shelf, without line timestamps in the captured response |
| Main YouTube player captions | `GET https://www.youtube.com/api/timedtext?...&fmt=json3` | Timed caption segments; the captured track used `caps=asr` |

These are observations from browser captures, not a stable public API contract. YouTube can change parameters, client versions, authentication requirements, or availability at any time. Use for personal experiments and respect YouTube’s terms and content rights.

## Important credential handling

The shared captures contained live-looking cookies, authorization hashes, visitor/session IDs, signed URL parameters, and tokens. They are deliberately **not reproduced here**. Treat those values as secrets. Do not commit them, paste them into public bug reports, or reuse the pasted session values. If they are still active, sign out or revoke the browser session as appropriate.

A browser request may work only with a current browser session. Never hard-code session cookies or tokens in a distributed app. For development, use DevTools “Copy as cURL” locally and keep the resulting command in a private scratch file. Redact `Cookie`, `Authorization`, `x-goog-visitor-id`, `pot`, `signature`, `ei`, and similar request-specific values before sharing.

---

## A. YouTube Music lyrics shelf (`browse`)

### Captured request

- **Method:** `POST`
- **URL:** `https://music.youtube.com/youtubei/v1/browse?prettyPrint=false`
- **Status:** `200 OK`
- **Body format:** JSON
- **Response renderer:** `contents.sectionListRenderer.contents[].musicDescriptionShelfRenderer`
- **Observed attribution:** `footer.runs[].text = "Source: LyricFind"`
- **Observed response tracking parameter:** CSI includes `GetBrowseTrackLyricsPage_rid`.
- **Observed response feedback metadata:** `responseContext.serviceTrackingParams` has `browse_id = MPLYt_CGrZEPEIiSv-1`.

The `browse_id` above was seen in **response** tracking metadata. It is not enough to establish the exact request `browseId`, since the pasted request body was truncated before its complete browse fields.

### Request body template

Use the **current full request payload from your own DevTools capture**. The safe, intentionally incomplete shape is:

```json
{
  "context": {
    "client": {
      "hl": "en-IN",
      "gl": "IN",
      "clientName": "WEB_REMIX",
      "clientVersion": "<current WEB_REMIX client version>",
      "platform": "DESKTOP"
    }
  },
  "browseId": "<copy from the captured request body, if present>"
}
```

This is a **shape/template, not a confirmed complete payload**. The browser request included additional context fields, and the exact `browseId` in the request was not visible in the provided excerpt. Do not assume the response tracking `browse_id` is the request value.

### cURL template

Fill in the request body and any browser-required headers from a fresh capture. The following makes no claim that the placeholders alone are sufficient:

```bash
curl 'https://music.youtube.com/youtubei/v1/browse?prettyPrint=false' \
  -X POST \
  -H 'content-type: application/json' \
  -H 'x-youtube-client-name: 67' \
  -H 'x-youtube-client-version: <current WEB_REMIX client version>' \
  -H 'authorization: <fresh browser-generated value, if required>' \
  -H 'cookie: <fresh browser cookies, if required>' \
  --data-raw '<JSON request body captured from this session>'
```

The capture showed same-origin browser requests and YouTube client headers. It also included cookies and an authorization header. Therefore, **unauthenticated direct calls are not confirmed**. Avoid manually constructing the authorization hash; use the browser’s fresh request or authenticated browser context for testing.

### Response parsing

Lyrics text and attribution can be extracted with JavaScript:

```js
function extractMusicLyrics(response) {
  const shelves = response?.contents?.sectionListRenderer?.contents ?? [];
  const shelf = shelves.find(x => x.musicDescriptionShelfRenderer)
    ?.musicDescriptionShelfRenderer;
  if (!shelf) return null;

  return {
    text: (shelf.description?.runs ?? []).map(run => run.text ?? '').join(''),
    source: (shelf.footer?.runs ?? []).map(run => run.text ?? '').join(''),
    maxCollapsedLines: shelf.maxCollapsedLines,
    maxExpandedLines: shelf.maxExpandedLines
  };
}
```

For the supplied capture, `text` was one long plain-text run and `source` was `Source: LyricFind`. No line-level timing fields appeared in that renderer. Treat this as unsynchronized text for that response.

---

## B. Main YouTube timed captions (`timedtext`)

### Captured request

- **Method:** `GET`
- **URL path:** `https://www.youtube.com/api/timedtext`
- **Captured query fields:** `v=18m9ovEsP6s`, `caps=asr`, `lang=en-IN`, `fmt=json3`, plus request-specific playback/client/signature parameters.
- **Status:** `200 OK`
- **Content type:** `application/json; charset=UTF-8`
- **Server header:** `video-timedtext`
- **Response format:** JSON3-like timed events

The complete signed URL is not a reusable template: it contains an expiring signature and other request-specific values. Use the active video player’s caption track selection/request in DevTools to obtain a fresh URL. At minimum, the captured URL tied the request to video ID `18m9ovEsP6s` and requested an ASR track in `en-IN`.

### URL template

```text
https://www.youtube.com/api/timedtext
  ?v=<VIDEO_ID>
  &lang=<LANGUAGE_TAG>
  &fmt=json3
  &caps=asr
  &<other fresh parameters copied from the player request>
```

Newlines and whitespace above are explanatory; build a properly URL-encoded query in code. Do not omit required fresh parameters based on this template. The captured URL included signed/expiring fields such as `ei`, `expire`, `sparams`, `signature`, and `pot`; those values must not be copied from the example capture.

### cURL template

```bash
curl --get 'https://www.youtube.com/api/timedtext' \
  --data-urlencode 'v=<VIDEO_ID>' \
  --data-urlencode 'lang=<LANGUAGE_TAG>' \
  --data-urlencode 'fmt=json3' \
  --data-urlencode 'caps=asr' \
  --data-urlencode '<other-required-key>=<fresh-value>' \
  -H 'referer: https://www.youtube.com/' \
  -H 'cookie: <fresh browser cookies, only if required>'
```

For reliability, copy a **fresh full request URL** from DevTools instead of rebuilding the signed query. The capture included cookies, visitor/client headers, and signed query values; direct unauthenticated access was not tested. The query itself may be sufficient in some circumstances, but this evidence does not establish that.

### Timed response shape

```json
{
  "wireMagic": "pb3",
  "events": [
    {
      "tStartMs": 8477,
      "dDurationMs": 2447,
      "segs": [
        { "utf8": "Hoya Hoya Ek Kasoor" }
      ]
    },
    {
      "tStartMs": 10924,
      "dDurationMs": 1076,
      "segs": [
        { "utf8": "Chadheya Chadheya" }
      ]
    }
  ]
}
```

Each event provides a start time and duration in milliseconds. Segment text is in `segs[].utf8`; join multiple segment strings in order when presenting one event.

### Find the active caption at a playback position

```js
function activeCaption(json3, playbackSeconds) {
  const nowMs = playbackSeconds * 1000;
  const event = (json3.events ?? []).find(e =>
    nowMs >= e.tStartMs && nowMs < e.tStartMs + (e.dDurationMs ?? 0)
  );
  return event
    ? (event.segs ?? []).map(s => s.utf8 ?? '').join('')
    : '';
}
```

For scrolling/highlighting, recompute against the player’s current time (for example on `timeupdate` or an animation frame), select the active event, and scroll the corresponding rendered row into view. Handle gaps where no event is active, overlapping events, empty text, and track changes.

---

## Browser-based capture workflow

1. Open the target video or song in the intended YouTube product.
2. Open DevTools → **Network**, enable **Preserve log**, and reload.
3. On YouTube Music, select Lyrics and filter requests for `youtubei/v1/browse`. Inspect the POST body and response renderer.
4. On main YouTube, enable captions and filter for `timedtext`. Inspect the query string and JSON3 response.
5. Save a local HAR only if needed. HAR files can contain account cookies, authorization values, signed URLs, and personal browsing details. Keep them private and redact before sharing.
6. Compare event timing with the player clock to verify synchronization. Do not infer lazy-loading or cache behavior from one request; repeat with reload, tab changes, and another track while watching Network.

## Verified versus unknown

**Verified in the provided captures:** endpoint paths, methods, response status/type, YouTube Music lyrics renderer and LyricFind attribution, timed-text query’s `v`, `caps=asr`, `lang`, and `fmt=json3`, and JSON3 event timing fields.

**Not verified from the supplied excerpts:** the complete YouTube Music POST body and its exact request `browseId`; whether either endpoint can be called without a logged-in browser session; which specific headers are strictly required; stable, public API guarantees; request caching/lazy-load rules; and complete behavior for other songs, languages, or caption tracks.
