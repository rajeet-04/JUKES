# Year-end Wrapped

How listening gets from the phone to a Wrapped, and how to deploy the Worker side.

## Data flow

1. **Phone, while playing.** `PlayLog` (`services/PlayLog.kt`) times what is actually heard, pauses excluded, and writes one `play_events` row per listen (Room, `database/PlayEvent.kt`). Each row keeps a snapshot of the song, the account email that was listening (null when anonymous), and `source`: `playlist`, `album`, `artist`, `library`, `radio`, `jam`, `home`, `search` or `direct`.
2. **Phone, once a local day has closed.** `DailyReportUploader` (`analytics/DailyReport.kt`) sends one `daily_listening_report` event per day and owner **directly to PostHog** (`POSTHOG_HOST/batch/`). A day's rows are deleted only after PostHog answers with a success status. It runs on app open and never reports today. Rows whose day failed stay for the next run.
   - The event uuid is fixed per email, device and day, so a retry is not counted twice and a phone and a desktop do not collide.
   - Reports go under the account email when logged in, otherwise under the install id.
3. **Worker, on request.** `GET /account/wrapped?year=` (`src/wrapped.ts`) reads that account's reports back from PostHog's private API with a personal API key, aggregates them in HogQL, adds artist and album names and genres from Spotify, and caches one row per account and year in D1 (`wrapped_cache`). A closed year is never recomputed; the current year is refreshed after 6 hours.
4. **Delete account.** `/account/delete` also clears the cache and deletes the person and their events in PostHog (best effort).

The phone never holds a PostHog read key; the personal key lives only in the Worker.

## Privacy

- Analytics can be turned off in Power Tools. That stops capture and reports and clears the saved rows.
- Email is the PostHog distinct id when logged in. Logging out resets PostHog and rotates the install id.

## Retention

The PostHog project keeps events for 12 months (the plan's maximum). Compute Wrapped in December, while that year's January events still exist; the D1 cache keeps the result after that.

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
