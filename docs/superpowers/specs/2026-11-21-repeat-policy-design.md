# Repeat policy for recommendations: design

Date: 2026-11-21 · Branch: beta · Status: draft for approval

## Intent

Recommendations repeat the wrong songs. Favourites are exempt from every repeat penalty and come
back again and again. A song played twice in a day counts as "on repeat", skips the seen-check and
is recommended more, which feeds itself. And the only real anti-repeat memory (the in-memory
`seen` / `seenSongs` sets in `QueueManager`) is wiped by `resetRecommendationSession()` whenever the
listener picks a new song or a new queue loads, so the next radio hands back the same songs.

The listener wants persistent behaviour: favourites get higher preference but are not repeated too
often, every other song plays once per listening session, and Up Next has more songs they have
never heard.

## Goals

- A listening session survives queue changes and app restarts. It ends only after a gap with
  nothing playing of **1 hour or more**.
- A non-favourite song is recommended **at most once per session**, once it has been heard.
- A favourite may come back within a session, but only after a **cooldown of 20 songs**, after
  which its chance rises gradually rather than jumping back to full.
- Time away counts: after a gap of 1 hour or more a new session starts, so favourites' cooldowns
  and the once-per-session rule are cleared.
- **1 in every 3** recommendations is a song the listener has never heard, when one is available.
- Favourites score higher than other songs.
- One set of rules, in one module, used by both the online radio path and the offline library path.

## Non-goals

- Songs the listener picks or queues themselves are never blocked or reordered. The rules apply only
  to what the app adds.
- No change to where candidates come from (YouTube Music radio, backend radio, offline library).
- No server-side or account-synced state; this is per device.
- Home feed sections are out of scope.

## Anti-goals (a failure even if it technically works)

- Up Next running dry because the rules blocked everything. When the filters leave too few songs,
  relax in this order: allow songs heard in the session (oldest first) before showing nothing.
- A favourite coming back within a few songs of its last play.
- The same artist filling Up Next.
- The radio's relevance being ignored: a draw that picks songs unrelated to the seed.

## Today (what changes)

- `services/Variety.kt`: decaying play / skip / recommended scores, `freshness()`, `relevance()`
  and a weighted draw `pick()` that is written and tested but never called.
- `database/SongExposure.kt`: `song_exposure` table keyed by song key, DB version 12.
- `services/QueueManager.kt`:
  - `pullRadio` blocks by the session `seen` sets, with the "on repeat today" bypass;
  - `moveReserveToPending` keeps radio order and only moves stale songs (freshness < 0.5,
    favourites and on-repeat exempt) to the back;
  - `fetchOfflineRecommendations` has its own points, with +10 for favourites, +5 for on repeat
    and a -25 freshness penalty favourites are exempt from;
  - `dailyPlayCount` / `daily_plays` prefs and `repeat_threshold` exist only for "on repeat".
- `PlaybackService` calls `recordQualifiedPlay` and `recordSkip`.

## Approaches considered

1. **One repeat-policy module used by both paths (picked).** A pure-Kotlin policy decides whether a
   candidate is allowed and gives it points; picks are a weighted draw with every third slot for
   an unheard song. Persistent state lives in the exposure table and a small session record.
   Biggest change, Up Next follows the radio order a bit less strictly, but the rules live in one
   tested place.
2. Patch the same rules into `pullRadio`, `moveReserveToPending` and the offline scorer
   separately. Smaller, but this scattering is how the inconsistent favourite exemptions came about.
3. Keep radio order and only filter plus swap every third song for an unheard one. Simplest, but no
   real point system, so favourites can't get "higher preference".

The listener picked approach 1.

## Design

### Listening session (persistent)

Stored in SharedPreferences (`listening_session`): `sessionStart` (ms), `lastActivityAt` (ms) and
`songSeq` (a running count of qualified plays, never reset).

- On every qualified play and on playback start, if `now - lastActivityAt >= 1 hour`, start a new
  session (`sessionStart = now`). Then set `lastActivityAt = now`.
- On a qualified play, `songSeq += 1`.
- Picking a new song or loading a new queue does **not** start a new session.
  `resetRecommendationSession()` still drops the reserve, in-flight work and radio seeds, but no
  longer forgets what was heard: that comes from the persistent state below.

### Per-song state

Add to `song_exposure` (migration 12 → 13, new nullable columns):

- `last_play_at` (ms) and `last_play_seq` (`songSeq` at that play), set on qualified play.
- `last_skip_at` (ms), set on early skip.

`PRUNE_AFTER_MS` (60 days) stays. A pruned row means "never heard" again.

### Rules (`RepeatPolicy`, new file `services/RepeatPolicy.kt`)

Pure functions over (song state, session, isFavourite, now, songSeq), no Android types, so it is
unit tested like `Variety`.

**Blocked:**
- A non-favourite with `last_play_at >= sessionStart` or `last_skip_at >= sessionStart`
  (heard or skipped this session).
- A favourite with `last_play_at >= sessionStart` and `songSeq - last_play_seq < 20`.
- A favourite skipped this session.

**Points** (allowed songs only), multiplied:
- relevance: `Variety.relevance(rank)` for radio songs. The offline path keeps its own
  library-based terms (artist and album match, play count, artist affinity) as the base score,
  converted to a positive weight.
- favourite: ×2.0.
- favourite ramp after its cooldown: ×0.25 at 20 songs since its last play, rising linearly to ×1.0
  at 40 songs. Songs not played this session get ×1.0.
- freshness: `Variety.freshness()`. For favourites only the skip and recommended scores count,
  since the cooldown already handles their plays.
- artist spread: `Variety.SAME_ARTIST_FACTOR` per earlier pick by the same artist, as in `pick()`.

**Never heard** = no exposure row with a play (`last_play_at == null` and `play_score == 0`) and, for
a library track, `playCount == 0`.

### Picking

- Online: `moveReserveToPending` filters the incoming reserve through the policy, then orders it by
  weighted draw (`Variety.pick` with the policy weight). Every 3rd position (3, 6, 9, …) is drawn
  only from never-heard candidates; if none is left, that slot is drawn from everything.
- `pullRadio` drops the `seen` / `seenSongs` and on-repeat checks for "heard" and uses the
  policy's blocked rule. It keeps the in-session dedupe of the same video/upload so one radio
  doesn't list a song twice.
- Offline: `fetchOfflineRecommendations` filters through the policy, removes its own favourite,
  on-repeat and freshness terms, applies the policy multipliers to its score, and uses the same
  every-3rd-unheard rule.
- Fallback when the filtered pool is too small to fill the lookahead: allow songs heard this
  session, least recently played first. Favourites inside their 20-song cooldown stay blocked
  unless nothing else is left.

### Removed

- "On repeat" logic: `dailyPlayCount`, the `daily_plays` prefs, `indulgePlays`, `STALE_FRESHNESS`.
- The `repeat_threshold` setting in Power tools → Advanced is replaced by **"Favourite
  cooldown"**, a number of songs from 10 to 40, default 20 (stored as `favourite_cooldown`).

## Constraints

- Kotlin, Room, existing coroutine scopes in `QueueManager`. Room migration 12 → 13 must keep data.
- The policy runs on IO/Default, never the main thread (search jank was fixed this way recently).
- Debug builds by default; tests with `./gradlew test`.

## Calls I made (left to change)

- Session gap of 1 hour, favourite cooldown 20 songs, ramp to full at 40 songs, favourite ×2.0.
- Skipped songs count as heard for the once-per-session rule.
- A song that was recommended but never played does not count as heard; it may come back later in
  the session (its recommended score still lowers its weight).
- Repurposing the `repeat_threshold` setting into "Favourite cooldown".

## Settled after the builder check (my calls)

- **Where blocked songs wait for the fallback.** `pullRadio` puts policy-blocked songs in a
  separate `heldBack` deque (cleared with the reserve) instead of dropping them. The fallback
  applies when, after a refill attempt that added nothing, `pendingRecs.size < lookahead()`. It then
  moves `heldBack` songs into Up Next, least recently played first.
- **Counting "every 3rd".** A counter of songs the app has added this listening session, stored
  with the session record (`recSeq`) and reset when a new session starts. Positions where
  `recSeq % 3 == 2` (the 3rd, 6th, …) take an unheard song. Songs the listener queues don't count.
  Offline uses the same counter.
- **Radio rank.** Each reserve entry carries its index in the radio it came from
  (`RankedRec(rec, rank)`). Each radio, including one from a later seed, starts at rank 0.
- **Offline weight.** `max(score, 1).toDouble()` after removing the old favourite, on-repeat and
  freshness terms, then multiplied by the policy factors. The `score > 0` filter goes; the policy's
  blocked rule takes its place.
- **After the upgrade.** No backfill. Existing rows get null `last_play_at`, so the first session
  after updating starts with nothing blocked. A row with `play_score > 0` still counts as heard
  for the "never heard" test.
- **Old setting value.** `repeat_threshold` is ignored. `favourite_cooldown` starts at 20.
- **Recommended events.** Still recorded once on `deliver`, as today.

## Left to the builder

- Exact Room migration SQL and DAO queries.
- Whether `RepeatPolicy` takes a small data class for the session or separate arguments.
- Log lines.
