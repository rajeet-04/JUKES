# Endless Home Feed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the three-section Android home with a YT Music-style feed of varied section shapes that keeps loading new sections as the user scrolls.

**Architecture:** A pure `FeedPager` turns an ordered seed queue into pages of `HomeSection`s, deduplicating songs across the whole feed and deriving new seeds from songs it has already shown, so the feed never runs dry. Rows show YouTube radio picks straight away (title, artist, `i.ytimg.com` art) and only match on Spotify when a song is tapped, which removes the per-row Spotify cost that caps today's feed. Local-only sections (speed dial, forgotten favourites) come from Room with no network.

**Tech Stack:** Kotlin 2.4.21, Jetpack Compose (LazyColumn, LazyHorizontalGrid), Room, kotlinx.coroutines + kotlinx-coroutines-test, Haze 2.1 glass helpers in `ui/theme/Glass.kt`.

**Spec:** The design critique of 2026-11-20 (this session) and the user's YT Music reference: quick picks, speed dial, forgotten favourites, new releases, trending, from your library, mixed for you, fresh finds, daily discover, explore. "It needs to feel longer and more like unlimited."

## Global Constraints

- Android only (`app/`); `desktopApp/` keeps its current home.
- Zero-cost backend: no new paid services, no new backend endpoints. Data comes from `JukesApi.fullRadio`, `SpotifyApi`, the YTM innertube client already used in `SpotifyApi.kt` (`WEB_REMIX`), and Room.
- Debug builds only: verify with `sh ./gradlew :app:assembleDebug` and `sh ./gradlew :app:testDebugUnitTest`.
- Work on `beta`. Commits carry no AI attribution trailers.
- Glass: cards and section panes use `glassPane`; hero card uses `glassFloat`; nothing new is frosted beyond those helpers. Solid surfaces still apply below Android 13.
- No song appears twice anywhere in one feed session (key: `RecommenderApi.songKey(title, artist)`).
- Section titles are plain sentence case, no emoji, no trailing punctuation. Greeting loses the space before "!" ("After Dark!").

## Review Focus

- **Brand-new user with an empty library**: no seeds. Expect the existing `EmptyHomeState` plus the Trending section (Task 6) so the screen is not blank. Test in Task 2 (`empty seeds yield no radio pages`) and Task 7 (empty-state branch).
- **Offline mid-scroll**: `loadMore` fails. Expect sections already shown to stay and a single inline retry row at the bottom, never a spinner that loops forever. Test in Task 2 (`fetch failure ends page without losing earlier sections`).
- **Fast flinging past the end**: `loadMore` called repeatedly while a page is in flight. Expect exactly one fetch per page. Test in Task 2 (`concurrent loadMore runs one page`).
- **Tapping a row whose Spotify match fails**: expect a short snackbar "Couldn't find that song" and no stuck spinner. Test in Task 4 (`playRec reports failure when match is null`).
- **Pull to refresh after scrolling 10 pages**: expect the feed to reset to page 1 with fresh seeds and the dedupe set cleared. Test in Task 2 (`reset clears seen keys and seeds`).

---

## File Structure

| File | Responsibility |
|---|---|
| Create `app/src/main/java/com/example/juke/home/HomeSection.kt` | Sealed section model + `FeedSong` row model |
| Create `app/src/main/java/com/example/juke/home/LocalSections.kt` | Pure builders for speed dial and forgotten favourites |
| Create `app/src/main/java/com/example/juke/home/FeedPager.kt` | Seed queue, dedupe, page assembly, derived seeds |
| Create `app/src/main/java/com/example/juke/home/YtmCharts.kt` | Innertube `FEmusic_charts` fetch + parser for Trending |
| Modify `app/src/main/java/com/example/juke/viewmodels/HomeViewModel.kt` | Hold `sections`, drive `FeedPager`, expose `loadMore()` / `refresh()` |
| Modify `app/src/main/java/com/example/juke/viewmodels/MusicViewModel.kt` | `playRec(song: FeedSong, startRadio: Boolean)` lazy-match entry point |
| Create `app/src/main/java/com/example/juke/ui/screens/home/HomeSections.kt` | Composables per section shape |
| Modify `app/src/main/java/com/example/juke/ui/screens/HomeScreen.kt` | Render `sections`, infinite-scroll trigger, chips row |
| Tests under `app/src/test/java/com/example/juke/home/` | Pure-logic tests (JUnit4 + `runTest`, same style as `LibraryRemovalUndoTest`) |

Test command for all home logic: `sh ./gradlew :app:testDebugUnitTest --tests 'com.example.juke.home.*'`

---

### Task 1: Section model and local sections

**Files:**
- Create: `app/src/main/java/com/example/juke/home/HomeSection.kt`
- Create: `app/src/main/java/com/example/juke/home/LocalSections.kt`
- Test: `app/src/test/java/com/example/juke/home/LocalSectionsTest.kt`

**Interfaces:**
- Produces:
  - `data class FeedSong(val videoId: String, val title: String, val artist: String, val art: String, val durationSec: Int?)` with `companion fun from(rec: RecommenderApi.YouTubeRecommendation): FeedSong` (art = `https://i.ytimg.com/vi/$id/mqdefault.jpg`, duration via `SongTitle.clockSeconds`) and `val key: String get() = RecommenderApi.songKey(title, artist)`.
  - `sealed interface HomeSection { val id: String; val title: String }` with:
    - `QuickPicks(id, title, songs: List<FeedSong>, seed: Track?)`: 4-row paged grid
    - `SpeedDial(id, title, tracks: List<Track>)`: 3×3 tiles of local tracks
    - `SongRow(id, title, songs: List<FeedSong>, seed: Track?)`: horizontal cards ("Because you played X", "Fresh finds")
    - `LocalRow(id, title, tracks: List<Track>)`: "Forgotten favourites", "Back in rotation"
    - `Releases(id, title, albums: List<SpotifyAlbum>)`
    - `HeroMix(id, title, subtitle: String, songs: List<FeedSong>, seed: Track)`: one big card
    - `Ranked(id, title, songs: List<FeedSong>)`: numbered list (Trending)
  - `object LocalSections { fun speedDial(tracks: List<Track>): List<Track>; fun forgottenFavourites(tracks: List<Track>, now: Instant): List<Track> }`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `speed dial takes top 9 by play count, one per artist`() {
    val t = (1..12).map { Track(uuid = "$it", title = "s$it", artist = if (it <= 2) "A" else "B$it", durationSec = 180, playCount = 100 - it) }
    val dial = LocalSections.speedDial(t)
    assertEquals(9, dial.size)
    assertEquals(listOf("1", "3", "4"), dial.take(3).map { it.uuid }) // "2" dropped: same artist as "1"
}

@Test fun `forgotten favourites are favourites unplayed for 30 days, oldest first, max 12`() {
    val now = Instant.parse("2026-11-20T00:00:00Z")
    val fav = { id: String, last: String? -> Track(uuid = id, title = id, artist = id, durationSec = 1, isFavourite = true, lastPlayedAt = last) }
    val out = LocalSections.forgottenFavourites(listOf(
        fav("recent", "2026-11-10T00:00:00Z"), fav("old", "2026-09-01T00:00:00Z"),
        fav("older", "2026-01-01T00:00:00Z"), fav("never", null),
        Track(uuid = "notfav", title = "x", artist = "x", durationSec = 1, lastPlayedAt = "2025-01-01T00:00:00Z"),
    ), now)
    assertEquals(listOf("never", "older", "old"), out.map { it.uuid })
}
```

- [ ] **Step 2: Run, expect FAIL** (`Unresolved reference: LocalSections`)

- [ ] **Step 3: Implement the model and `LocalSections`.** Parse `lastPlayedAt` with `Instant.parse` inside `runCatching`; unparseable counts as never played. Speed dial: sort by `playCount` desc, `distinctBy { it.artist }`, take 9.

- [ ] **Step 4: Run tests, expect PASS**

- [ ] **Step 5: Commit** `Android home: section model and local sections`

---

### Task 2: FeedPager (endless paging, dedupe, derived seeds)

**Files:**
- Create: `app/src/main/java/com/example/juke/home/FeedPager.kt`
- Test: `app/src/test/java/com/example/juke/home/FeedPagerTest.kt`

**Interfaces:**
- Consumes: `FeedSong`, `HomeSection` (Task 1).
- Produces:

```kotlin
class FeedPager(
    private val radio: suspend (videoId: String) -> List<RecommenderApi.YouTubeRecommendation>,
    private val isSpam: (String) -> Boolean = RecommenderApi::isSpamTitle,
) {
    fun reset(seeds: List<Track>, knownKeys: Set<String>)
    suspend fun nextPage(): Page        // never runs two pages at once (Mutex)
    val exhausted: Boolean
    data class Page(val sections: List<HomeSection>, val failed: Boolean)
}
```

Rules the tests pin:
- Seeds are consumed in order, one per section. A page has 3 sections.
- Page 1's first section is `QuickPicks` (20 songs, title "Quick picks"); every 4th section after that is a `HeroMix` (title "Mixed for you", subtitle "Based on ${seed.artist}"); the rest are `SongRow` titled "Because you played ${seed.title}". A `SongRow` has 10–16 songs.
- A section with fewer than 4 songs left after dedupe is dropped and the next seed is tried, with at most 6 attempts per page.
- Every song shown is added to the seen set together with `knownKeys`, so library songs and earlier rows never repeat.
- Derived seeds: when the seed queue is empty, the first song of each earlier section becomes a seed (as `Track(uuid = "yt:$videoId", title, artist, durationSec = 0, ytVideoId = videoId)`), each used once. Sections from derived seeds are titled "Fresh finds". `exhausted` is true only when the queue and the derived pool are both empty.
- An exception from `radio` for one seed skips that seed. If every attempt in a page throws, return `Page(emptyList(), failed = true)` without consuming the derived pool.

- [ ] **Step 1: Write the failing tests** (fake `radio` returns synthetic recs keyed by seed id):
  - `first page starts with quick picks of 20`
  - `no song repeats across pages or from known keys`
  - `sections under 4 songs are skipped`
  - `fourth section is a hero mix`
  - `derived seeds keep the feed going after explicit seeds run out` (2 explicit seeds; page 3 still non-empty, titled "Fresh finds")
  - `empty seeds yield no radio pages` (`nextPage().sections.isEmpty()` and `exhausted`)
  - `fetch failure ends page without losing earlier sections` (radio throws on page 2 → `failed = true`; page 1 sections unchanged; next call retries the same seeds)
  - `concurrent loadMore runs one page` (two `async { nextPage() }` calls invoke `radio` for 3 seeds, not 6)
  - `reset clears seen keys and seeds`

- [ ] **Step 2: Run, expect FAIL**

- [ ] **Step 3: Implement `FeedPager`** with `Mutex` around `nextPage`. Fetch the 3 seeds of a page concurrently (`async`), then assemble in seed order so section order is stable.

- [ ] **Step 4: Run, expect PASS**

- [ ] **Step 5: Commit** `Android home: endless feed pager`

---

### Task 3: Trending from YTM charts

**Files:**
- Create: `app/src/main/java/com/example/juke/home/YtmCharts.kt`
- Create: `app/src/test/resources/ytm_charts.json` (fixture)
- Test: `app/src/test/java/com/example/juke/home/YtmChartsTest.kt`

**Interfaces:**
- Produces: `object YtmCharts { suspend fun trending(country: String = Locale.getDefault().country): List<FeedSong>; internal fun parse(json: String): List<FeedSong> }`

- [ ] **Step 1: Capture the fixture.** POST `https://music.youtube.com/youtubei/v1/browse` with body `{"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20260414.05.00","hl":"en","gl":"IN"}},"browseId":"FEmusic_charts"}`. Save the response as the fixture. Use the client version constant from `SpotifyApi.kt:1159`; if it has been bumped, share one constant.
- [ ] **Step 2: Write the failing test** `parse returns ranked songs with video ids`: at least 20 songs, all `videoId.length == 11`, no blank titles, and the first title equals the first chart entry you read from the fixture by hand.
- [ ] **Step 3: Run, expect FAIL**
- [ ] **Step 4: Implement.** Walk `musicCarouselShelfRenderer` / `musicResponsiveListItemRenderer` items with `kotlinx.serialization.json` (`JsonElement` navigation, no generated models). Take the "Trending" or "Top songs" shelf. The video id comes from `playlistItemData.videoId`, and title and artist from `flexColumns[0..1]` runs. `trending` uses `ApiClient.httpClient`, returns `emptyList()` on any failure, and caches results in memory for 6 h.
- [ ] **Step 5: Run, expect PASS**
- [ ] **Step 6: Commit** `Android home: trending songs from YTM charts`

---

### Task 4: Lazy match on tap

**Files:**
- Modify: `app/src/main/java/com/example/juke/viewmodels/MusicViewModel.kt` (next to `playInstant`, ~line 1038)
- Test: `app/src/test/java/com/example/juke/home/RecPlayTest.kt`

**Interfaces:**
- Produces: `fun playRec(song: FeedSong, startRadio: Boolean = true)` and `val recLoading: StateFlow<String?>` (the videoId being matched, for a row spinner). Core logic sits in a top-level `internal suspend fun resolveFeedSong(song: FeedSong, match: suspend (RecommenderApi.YouTubeRecommendation) -> SpotifyTrack?): SpotifyTrack?` so it is testable without the ViewModel.

- [ ] **Step 1: Write the failing tests:** `resolveFeedSong passes title artist and id to match`; `playRec reports failure when match is null` (the fake match returns null → result null; the ViewModel maps that to the snackbar "Couldn't find that song").
- [ ] **Step 2: Run, expect FAIL**
- [ ] **Step 3: Implement.** Match with `RecommenderApi.matchOnSpotify` then `SpotifyApi.getTrack(id)` (as `HomeViewModel.radioSection` does today), then `playInstant(SpotifyApi.spotifyTrackToSong(it))`, then `startRadio()` when `startRadio`. Ignore a second tap while `recLoading` is non-null. Clear `recLoading` in `finally`.
- [ ] **Step 4: Run, expect PASS**
- [ ] **Step 5: Commit** `Android home: play feed songs with lazy Spotify match`

---

### Task 5: HomeViewModel drives the feed

**Files:**
- Modify: `app/src/main/java/com/example/juke/viewmodels/HomeViewModel.kt`

**Interfaces:**
- Consumes: `FeedPager`, `LocalSections`, `YtmCharts.trending`.
- Produces on `HomeUiState`: `sections: List<HomeSection>`, `loadingMore: Boolean`, `endReached: Boolean`, `loadMoreFailed: Boolean`; `fun loadMore()`. Remove `becauseYouPlayed`, `likeFavorites`, `becausePending`, `likePending`, along with `radioSection` and its Spotify pre-matching.

- [ ] **Step 1: Assemble the head of the feed in `fetchData`.** Fixed order, each part skipped when empty:
  1. Page 1 from `FeedPager` (which begins with Quick picks)
  2. `SpeedDial` "Speed dial"
  3. `LocalRow` "Forgotten favourites"
  4. `Releases` "New releases" (existing `newReleasesFor`)
  5. `Ranked` "Trending songs"
  6. `LocalRow` "Back in rotation"

  Every later `loadMore()` appends a `FeedPager` page.
- [ ] **Step 2: Seeds.** Recent plays with `ytVideoId`, `distinctBy { artist }`, up to 8. Then most played, then favourites, deduped by uuid. `knownKeys` stays as it is today.
- [ ] **Step 3: Cache.** Replace the `DiscoveryCache.Snapshot` fields with `sections: List<HomeSection>`. Keep stale-while-revalidate and the existing `FRESH_MS`. Only the head is cached; paging restarts from page 2 after a cold start.
- [ ] **Step 4: `refresh()`** calls `pager.reset(...)` and rebuilds the head (Review Focus: refresh after scrolling).
- [ ] **Step 5: Verify** `sh ./gradlew :app:assembleDebug` and `sh ./gradlew :app:testDebugUnitTest`, both green.
- [ ] **Step 6: Commit** `Android home: view model builds the endless feed`

---

### Task 6: Section composables

**Files:**
- Create: `app/src/main/java/com/example/juke/ui/screens/home/HomeSections.kt`

**Interfaces:**
- Consumes: `HomeSection` types, `MusicViewModel.playRec`, `recLoading`.
- Produces: `@Composable fun HomeSectionView(section: HomeSection, musicViewModel: MusicViewModel, onAlbumClick: (SpotifyAlbum) -> Unit, onSeeAll: () -> Unit)`

Shapes (measurements fixed so the executor doesn't have to guess):
- **QuickPicks**: `LazyHorizontalGrid(rows = GridCells.Fixed(4))`, height 4 × 60dp, item width = screen width − 56dp so the next column peeks. Rows have 48dp art at 8dp radius, title and artist, and a 48dp overflow. The header has a "Play all" pill on the right that calls `playRec(first, startRadio = true)`.
- **SpeedDial**: 3×3 grid of square tiles, 8dp gaps, 12dp radius, with a title overlaid on a bottom scrim. Tap calls `setQueue(tracks, index)`.
- **SongRow / LocalRow**: reuse today's `RadioCard` / `LibraryCard` in `ArrowRow`, swapping `SpotifyTrack` for `FeedSong`.
- **HeroMix**: full-width `glassFloat` card, 16dp margin, with a 2×2 art collage (64dp each) on the left and the title, subtitle and a play button on the right. It lists the first 3 songs below.
- **Ranked**: numbered rows ("1"–"10") in one `glassPane` like today's `RadioList`, with a "Play all" pill.
- **Releases**: unchanged `ReleaseCard` row.
- While `recLoading == song.videoId`, the row's art shows a 20dp `CircularProgressIndicator` overlay.

- [ ] **Step 1: Write the composables.** Move `RadioList` / `RadioCard` / `ReleaseCard` / `LibraryCard` out of `HomeScreen.kt` into this file unchanged where they still fit.
- [ ] **Step 2: Verify** `sh ./gradlew :app:assembleDebug` green.
- [ ] **Step 3: Commit** `Android home: section shapes`

---

### Task 7: Endless HomeScreen

**Files:**
- Modify: `app/src/main/java/com/example/juke/ui/screens/HomeScreen.kt`

- [ ] **Step 1: Render the feed.** `HomeFeed` renders `items(state.sections, key = { it.id }, contentType = { it::class })` with `HomeSectionView`, 28dp after each section.
- [ ] **Step 2: Infinite trigger.** Use `derivedStateOf { lastVisibleIndex >= sections.size - 3 }`, then `LaunchedEffect(trigger)` calls `homeViewModel.loadMore()` when not `loadingMore`, `endReached` or `loadMoreFailed`. While loading, the footer shows two `ListSectionSkeleton()`. When `loadMoreFailed`, it shows `OfflineNotice(onRetry = loadMore)`.
- [ ] **Step 3: Chips row.** Add a horizontally scrolling row under the header: "Relax", "Energise", "Romance", "Feel good", "Focus", "Party". A tap opens Search with that text as the query (`onSearchClick(query)`; widen the callback to `(String?) -> Unit`). Mood browse pages are out of scope.
- [ ] **Step 4: Empty library.** When there are no seeds, keep `EmptyHomeState` and render the Trending section under it.
- [ ] **Step 5: Greeting.** In `HomeViewModel.buildGreeting`, drop the space before "!".
- [ ] **Step 6: Verify on device.** Run `sh ./gradlew :app:assembleDebug`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Fling to the bottom 5 times: new sections keep appearing, no song shows twice, tapping a quick pick plays within about 3 s, and airplane mode mid-scroll shows the retry row.
- [ ] **Step 7: Commit** `Android home: endless YT Music-style feed`

---

## Out of scope (later plans)

- Music videos row, community playlists, podcasts: these need YTM authenticated browse.
- The immersive background in the `GlassAmbient` slot.
- Desktop home.
