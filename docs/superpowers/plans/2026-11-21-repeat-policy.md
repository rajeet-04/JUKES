# Repeat Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recommendations remember what was heard across queue changes and restarts: non-favourites once per listening session, favourites after a song-count cooldown, 1 in 3 picks unheard.

**Architecture:** A pure `RepeatPolicy` object (blocked / weight / never-heard / ordering with fresh slots) is fed by a persisted listening session (SharedPreferences) and three new `song_exposure` columns. `QueueManager`'s online radio path and offline library path both filter and order through it; the "on repeat" machinery is deleted.

**Tech Stack:** Kotlin, Room (DB 12 → 13), JUnit4 unit tests.

**Spec:** `docs/superpowers/specs/2026-11-21-repeat-policy-design.md`

## Global Constraints

- Session gap: **1 hour** (`SESSION_GAP_MS = 3_600_000`).
- Favourite cooldown: default **20** songs, setting `favourite_cooldown` in `music_settings_prefs`, choices 10/15/20/30/40; `repeat_threshold` is ignored.
- Favourite ramp: ×0.25 at `cooldown` songs since last play, linear to ×1.0 at `2 × cooldown` (20 → 40 at the default).
- Favourite boost ×2.0. Favourite freshness uses skip + recommended scores only.
- Fresh slot: positions where `recSeq % 3 == 2`.
- No Claude attribution in commits. Debug-build tests (`./gradlew :app:testDebugUnitTest`); release build only at the end, testOnly checked with aapt2.

## Review Focus

1. First run after upgrade (no session prefs, null columns): nothing blocked, no crash in migration.
2. Everything in a radio already heard this session: Up Next must still fill from `heldBack`.
3. Listener away > 1 h mid-song (paused): the next pick starts a new session.
4. Favourite skipped this session: blocked even after its cooldown.
5. Offline library with only played songs: still queues (no `score > 0` cliff).

---

### Task 1: `RepeatPolicy` (pure)

**Files:** Create `app/src/main/java/com/example/juke/services/RepeatPolicy.kt`; Modify `database/SongExposure.kt` (entity fields only); Test `app/src/test/java/com/example/juke/services/RepeatPolicyTest.kt`

**Interfaces — Produces:**
- `data class ListeningSession(val start: Long, val lastActivity: Long, val songSeq: Long, val recSeq: Long)`
- `SongExposureEntity` gains `lastPlayAt: Long? = null`, `lastPlaySeq: Long? = null`, `lastSkipAt: Long? = null` (columns `last_play_at`, `last_play_seq`, `last_skip_at`).
- `object RepeatPolicy`:
  - `fun current(s: ListeningSession?, now: Long): ListeningSession` — new session (start = now, recSeq = 0, songSeq kept) when null or `now - lastActivity >= SESSION_GAP_MS`.
  - `fun blocked(e: SongExposureEntity?, favourite: Boolean, s: ListeningSession, cooldown: Int): Boolean`
  - `fun weight(e: SongExposureEntity?, favourite: Boolean, s: ListeningSession, cooldown: Int, now: Long, base: Double): Double`
  - `fun neverHeard(e: SongExposureEntity?, libraryPlays: Int = 0): Boolean`
  - `fun <T> order(items: List<T>, startRecSeq: Long, weightOf: (T) -> Double, fresh: (T) -> Boolean, artistOf: (T) -> String, random: Random = Random.Default): List<T>` — weighted draw of all items; at fresh-slot positions draw only from fresh items when any remain; same-artist factor `Variety.SAME_ARTIST_FACTOR` per earlier pick.

Tests (names and assertions):
- `gap of an hour starts a new session` — `current(s(last = t), t + 3_600_000).start == t + 3_600_000`, recSeq 0, songSeq kept; `t + 3_599_999` keeps the old start.
- `non favourite heard this session is blocked` / `heard before the session is not`.
- `skipped this session is blocked, favourite too`.
- `favourite inside cooldown is blocked, at cooldown it is not` (songSeq − lastPlaySeq = 19 → blocked, 20 → allowed).
- `favourite ramp` — weight ratio at gap 20 vs gap 40 is 0.25, gap 30 is 0.625 (same base, no exposure scores).
- `favourite outweighs non favourite` — ×2.0 with fresh rows.
- `never heard` — null row true; row with playScore > 0 false; libraryPlays > 0 false.
- `order puts fresh items in every third slot` — 6 heard + 3 fresh items, startRecSeq 0: positions 2, 5, 8 are fresh.
- `order keeps every item` and `zero weight items go last`.

- [ ] Write the tests; run `./gradlew :app:testDebugUnitTest --tests "*RepeatPolicyTest"` → FAIL (unresolved).
- [ ] Implement; run → PASS. Run `*VarietyTest` → PASS.
- [ ] Commit `Recommendations: repeat policy (session, cooldown, fresh slots)`.

### Task 2: Persisted session, exposure columns, migration

**Files:** Modify `database/SongExposure.kt`, `database/MusicDatabase.kt` (version 13, `MIGRATION_12_13`), `services/Variety.kt` (`record` gains `seq: Long = 0`), `services/QueueManager.kt` (session store, `recordQualifiedPlay`, `recordSkip`).

**Interfaces — Produces:** in `QueueManager`: `private fun session(now: Long): ListeningSession` (loads, applies `RepeatPolicy.current`, saves), `private fun favouriteCooldown(): Int`.

- `Variety.record(..., seq)`: PLAY sets `lastPlayAt = now, lastPlaySeq = seq`; SKIP sets `lastSkipAt = now`.
- `MIGRATION_12_13`: three `ALTER TABLE song_exposure ADD COLUMN … INTEGER` (nullable).
- Qualified play: `session(now)`, `songSeq += 1`, `lastActivity = now`, save, record PLAY with the new seq. Remove `daily_plays` prefs, `dailyPlayCount`, `indulgePlays`, `DEFAULT_INDULGE_PLAYS`.
- Test: `VarietyTest` `play records time and sequence`, `skip records time`.

- [ ] Test → FAIL; implement → PASS; `./gradlew :app:compileDebugKotlin` OK. Commit.

### Task 3: Online radio path

**Files:** Modify `services/QueueManager.kt` (`pullRadio`, `moveReserveToPending`, `queuedKeys`, `fillWindow`, `resetRecommendationSession`).

- Reserve entries carry rank: `data class RankedRec(val rec: YouTubeRecommendation, val rank: Int)`.
- `pullRadio`: one `exposureFor` + `favouriteKeys()` lookup per radio; drop the on-repeat bypass; policy-blocked songs go to `heldBack` (deque of `RankedRec`, cleared with the reserve).
- `moveReserveToPending`: weight = `RepeatPolicy.weight(base = Variety.relevance(rank))`, order with `RepeatPolicy.order(startRecSeq = session.recSeq)`, fresh = `neverHeard`; then `recSeq += count` saved.
- `queuedKeys`: queue + current + external only (played history is handled by the policy so favourites can return).
- `fillWindow`: when a refill added nothing, the reserve is empty and `pendingRecs.size < lookahead()`, move `heldBack` into pending, least recently played first, favourites still in cooldown last.
- `STALE_FRESHNESS` removed.

- [ ] `./gradlew :app:compileDebugKotlin` OK; full `testDebugUnitTest` PASS. Commit.

### Task 4: Offline path and setting

**Files:** Modify `services/QueueManager.kt` (`fetchOfflineRecommendations`), `ui/screens/PowerToolsScreen.kt`.

- Remove the favourite +10, play-today and freshness terms; filter `RepeatPolicy.blocked`; weight = `max(score, 1) × RepeatPolicy.weight(base = 1.0)`; select with `RepeatPolicy.order` (fresh = `playCount == 0 && neverHeard`), take `limit`; advance `recSeq`. Drop the `score > 0` filter. Keep the last-resort branch.
- Power tools: replace "Repeat threshold" with "Favourite cooldown" — "Songs before a favourite can come back in recommendations." chips 10/15/20/30/40 writing `favourite_cooldown`.

- [ ] Compile + full tests PASS. Commit.

### Task 5: Verify

- [ ] `./gradlew :app:testDebugUnitTest` all pass.
- [ ] Release build, aapt2 testOnly = 0, install, play a few songs, start a new queue: Up Next does not repeat what was just heard.
