# Share links and Jam

No login anywhere: a link or a 6-character code is the access. Everything runs on the `spotdown-kv-worker` Cloudflare Worker (`spotdown-kv-worker/`), reachable at `https://j.rajeet.in` and `spotdown-kv-worker.meek.workers.dev`. Keep `workers_dev = true` in `wrangler.toml`: the app fetches `/spotify-token` from the workers.dev host.

## Playlist links
- App: `repositories/ShareRepository.kt` uploads a snapshot (`POST /share`) and opens `https://j.rajeet.in/p/<id>`; the recipient saves it as a playlist, looking tracks up on Spotify by id.
- Worker: snapshots live in D1 (`jukes-shares`, `migrations/0001_shares.sql`). Expiry slides by use (at most one write per day, 30-day cap); a daily cron deletes expired rows.
- Only http artwork is uploaded; local file paths are meaningless to others.

## Jam
Rooms are joined by code or link (`https://j.rajeet.in/j/<code>`, `juke://j/<code>`). Everyone has equal controls. The host is the longest-connected member and hands off automatically when they leave. The host's player queue seeds a fresh room.

### Worker
- `src/jam.ts`: `JamRoom` Durable Object (SQLite-backed, WebSocket Hibernation). One storage row holds the whole room (`rev`, queue, playback anchor, host). Ops are applied one at a time, written, then broadcast, so concurrent edits get a defined order and ops are idempotent by client-chosen item id.
- `src/jam-logic.ts`: pure room logic (unit tested in `src/jam-logic.test.ts`, run `bun test src`).
- Limits: 200 songs, 10 members, 30 ops per 10 s per socket, room deleted after 24 h idle. Creating a room needs `X-App-Secret` (`SPOTIFY_TOKEN_KEY`).
- Live check: `BASE=https://j.rajeet.in SECRET=<key> bun test/jam-smoke.ts`.
- Deploy with `bunx wrangler deploy` (Node is not needed; `wrangler dev` does not work under Bun, so test against the live Worker).

### Protocol (JSON over `wss://j.rajeet.in/jam/<code>/ws`)
- Client to server: `hello`, `ping`, `add`, `addMany`, `remove`, `removeMany`, `move`, `seed`, `play{itemId,posMs,regen}`, `pause`, `seek`, `ended`, `mix`.
- Server to client: `welcome`, `members`, op broadcasts carrying `rev`/`by`, `snapshot`, `pb`, `mix`, `pong`, `error`. A `rev` gap triggers a fresh `hello` and a full snapshot.
- Position is never streamed: the server keeps an anchor (`anchorPosMs`, `anchorServerMs`) and each phone computes the live position from it.

### App (`app/.../jam/`)
- `JamSession`: connection, reconnect with backoff, ordered outbox, clock sync (min RTT of 5 pings, refreshed every 30 s), saved session so the app rejoins on launch (24 h).
- `JamControls`: routes play/queue/Mix actions from Search, Library, Home, the player, mini player and notification to the room; `jamSingle` taps play one song instead of a whole list.
- `JamResolver`: songs without a Spotify id are matched on Spotify (title, artist, length within 6 s) or skipped; artwork comes from Spotify.
- `JamFollower`: starts the room's song, corrects drift (seek above 300 ms, 0.97/1.03 speed nudge for files, seek only for streams), reports `ended`.
- `JamRadio` (host only): keeps the queue topped up from the radio; Mix or playing a new song regenerates it. `JamWarmup` warms the next 4 songs.
- `PlaybackService.JamAwarePlayer` makes notification, headset and lock-screen next/previous drive the room.
- While in a Jam, `QueueManager.fillSuspended` stops local radio autofill.

### Known gaps
- Follower and radio loops run only while the app's UI exists; swiping the app away stops following.
- Radio runs only on the host; offline taps are refused rather than queued.
- Streamed (not downloaded) songs' sync accuracy is unmeasured; downloaded songs drift about 10-20 ms.
- Local-only files without a Spotify match cannot be shared.
- `QueueManager` radio memory is lost on app restart.

### Free-tier budget
D1: 100k rows written/day. Durable Objects: 100k requests/day, 20 WebSocket messages count as one request, hibernation avoids duration billing. KV is used only for the Spotify token.
