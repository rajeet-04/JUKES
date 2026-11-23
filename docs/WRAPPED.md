# Year-end Wrapped

How listening gets from the phone to a Wrapped, and how to deploy the Worker side.

## Data flow

1. **Phone, while playing.** The playback service uses `PlayLog` (`services/PlayLog.kt`) to time listening while audio is playing, including when the UI is closed. Each `play_events` row stores a song snapshot, a playback source, and the identity at the start of the listen. That identity is the account email or the anonymous install ID.
2. **Phone, once a local day has closed.** `DailyReportUploader` (`analytics/DailyReport.kt`) sends `daily_listening_report` events directly to PostHog (`POSTHOG_HOST/batch/`) on app open. Each batch freezes its row IDs before sending and uses those same IDs to generate its UUID on retry. After PostHog acknowledges it, the uploader deletes the reported rows before clearing the batch record. Late listens get a separate batch.
3. **Worker, on request.** `GET /account/wrapped?year=` (`src/wrapped.ts`) queries the account's current PostHog person, aggregates reports in HogQL, and adds artist and album names and genres from Spotify. The current year's D1 snapshot refreshes after six hours, or after one hour for an empty result. A past year refreshes after rollover and can become final after two weeks.
4. **Delete account.** `/account/delete` also clears the cache and deletes the person and their events in PostHog (best effort).

The phone never holds a PostHog read key; the personal key lives only in the Worker.

## Privacy

- Analytics can be turned off in Power Tools. That stops capture and reports and clears the saved rows.
- Email is the PostHog distinct id when logged in. Logging out resets PostHog and rotates the install id.

## Retention

The PostHog project keeps events for 12 months. A January refresh keeps the latest snapshot that still covers previously observed listening days. Starting February 1, the Worker serves the stored snapshot for the prior year, or returns HTTP 410 if it never saved one. [PostHog retention and cleanup](https://posthog.com/docs/privacy/data-storage#when-expired-events-are-deleted)

## Deploy the Worker

Run from `spotdown-kv-worker/`. Node is not needed.

```bash
bun test                                                        # unit tests
bunx wrangler deploy --dry-run --outdir "$TMPDIR/wr-out"        # bundles and checks bindings, uploads nothing
bunx wrangler d1 migrations list jukes-shares --remote          # what is pending
bunx wrangler d1 migrations apply jukes-shares --remote         # applies 0005_wrapped_cache.sql
bunx wrangler secret put POSTHOG_PERSONAL_API_KEY               # PostHog personal API key, query read + person write
bunx wrangler deploy
```

`POSTHOG_API_HOST` and `POSTHOG_PROJECT_ID` are plain vars in `wrangler.toml`. Until the secret is set, `/account/wrapped` answers 503 and everything else works as before.

Create the personal API key in PostHog under your account settings. It needs read access to queries and persons, and write access to persons only for account deletion. Scope it to the JUKE project.

`wrangler dev` does not work under Bun, so check a deployed route with a real login token:

```bash
curl -s -H "X-App-Secret: $APP_SECRET" -H "Authorization: Bearer $TOKEN" \
  "https://spotdown-kv-worker.meek.workers.dev/account/wrapped?year=2026"
```
