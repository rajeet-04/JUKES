# Desktop 3.2.0 Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the desktop app (macOS, Windows, Linux) to the same feature set as Android 3.2.0, starting with making it compile again.

**Architecture:** `desktopApp` compiles `app/src/main/java` directly (`stageAndroid` in `desktopApp/build.gradle.kts`) and replaces only the files listed in `androidOnly`. Shared UI, view models and queue logic reach desktop for free. The work is: (1) fix what 3.2.0 broke in the replaced files, (2) re-implement what is Android-only, (3) wire what `MainActivity` does for Android into `JukeApp.kt`.

**Tech Stack:** Kotlin 2.4, Compose Multiplatform, Haze 2.1 (`haze-blur`, `haze-glass` have JVM artifacts), Room (bundled SQLite), JavaFX Media, JNA.

**Spec:** none. Source of truth is the 3.2.0 section of `CHANGELOG.md` and `git diff v3.1.1-stable HEAD -- app/src/main/java`.

## Current state (measured 2026-11-21, beta at 9dcfb78)

`./gradlew :desktopApp:compileKotlin --no-configuration-cache` fails with 50 errors, three groups:

| Group | Errors | Cause |
|---|---|---|
| Ambient waves | ~30 | `ambient/AmbientWaves.kt` (BroadcastReceiver, PowerManager, Settings) and `ambient/LevelMeterProcessor.kt` (Media3 `BaseAudioProcessor`) are Android-only but not in `androidOnly` |
| Glass API | ~15 | desktop `ui/theme/Glass.kt` lacks `glassIsLive()`, `LocalGlassBoost`, and the `displacement`/`edge`/`fold`/`clear` parameters of `liquidGlass`/`glassPane` |
| Database | 3 | desktop `database/MusicDatabase.kt` is at version 12: no `MIGRATION_12_13`, no `TrackDao.findByVideoId` |

Also found: desktop `BuildConfig` says `3.1.0-stable` / 31; the desktop player is its own `DesktopPlayerScreen` (`DesktopChrome.kt:528`), so the new one-stage player does not arrive by itself; `JukeApp.kt` has no `GlassSheetLayer`, so in-window glass menus have no host; the "Desktop build" workflow (`.github/workflows/desktop.yml`) triggers on `app/src/main/java/**`, so it has been red since the first 3.2.0 commit.

## Global Constraints

- No Claude/AI attribution in commits (user's CLAUDE.md).
- Do not edit `CHANGELOG.md` or release notes without explicit approval; Task 8 only drafts text.
- Android sources stay Android-correct. Desktop differences go in `desktopApp/src/main/kotlin` or in `stageAndroid` filters, never as `#if`-style edits to shared files.
- Desktop DB schema must match Android's (version 13) so exported/synced data stays compatible.
- Verify with `bash ./gradlew :desktopApp:compileKotlin --no-configuration-cache` and `:desktopApp:test`; the Android app must still pass `:app:testDebugUnitTest`.
- Work on `beta`.

## Review Focus

- Existing desktop user opens 3.2.0 with a version-12 `juke.db`: data must survive (no destructive fallback; `fallbackToDestructiveMigration(dropAllTables = false)` is in the desktop DB too).
- Window with no Haze source or "solid glass" on: every glass surface still readable.
- Ambient waves on a machine with no audio playing / paused: static, near-zero CPU.
- Right-click on a song row: opens the glass menu where Android's long-press does, and closes on click-away and Esc.
- Resize to narrow and wide: Home feed rails/grids do not overflow, endless scroll still loads.

---

### Task 1: Database parity (unblocks 3 errors)

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/example/juke/database/MusicDatabase.kt`
- Test: `desktopApp/src/test/kotlin/MigrationTest.kt` (create)

**Interfaces:**
- Produces: `val MIGRATION_12_13: Migration` (same SQL as `app/.../MusicDatabase.kt`), `TrackDao.findByVideoId(videoId: String): TrackEntity?`.

- [ ] **Step 1: Failing test.** `MigrationTest.v12DatabaseKeepsRowsAfterUpgrade`: build a v12 file DB with the previous schema, insert one `tracks` row and one `song_exposure` row, open through `MusicDatabase` and assert both rows exist and `song_exposure` has `last_play_at`, `last_play_seq`, `last_skip_at` (null).
- [ ] **Step 2:** Run `bash ./gradlew :desktopApp:test --tests MigrationTest --no-configuration-cache`. Expected: FAIL (compile error until Task 2/3 land, or no such column). If the module cannot compile yet, run this task after Task 2 and 3.
- [ ] **Step 3:** Copy `MIGRATION_12_13`, bump `version = 13`, add it to `addMigrations(...)`, add `findByVideoId` with the same `@Query` as Android. Diff the two files afterwards; any other drift is a finding.
- [ ] **Step 4:** Rerun the test. Expected: PASS.
- [ ] **Step 5:** Commit `Desktop: database version 13 to match Android`.

### Task 2: Ambient waves on desktop (unblocks ~30 errors)

**Files:**
- Modify: `desktopApp/build.gradle.kts` (add `ambient/AmbientWaves.kt` and `ambient/LevelMeterProcessor.kt` to `androidOnly`)
- Create: `desktopApp/src/main/kotlin/com/example/juke/ambient/AmbientWaves.kt` (same package, same signature as Android: `AmbientWaves(playing: Boolean, colors: List<Color>, dark: Boolean)`)
- Modify: `desktopApp/src/main/kotlin/com/example/juke/services/PlaybackManager.kt`
- Test: `desktopApp/src/test/kotlin/AmbientLevelsTest.kt` (create)

**Interfaces:**
- Consumes: shared `AmbientLevels` / `LevelAnalyzer` / `Levels(energy, bass, treble)` (pure Kotlin, already compiles).
- Produces: desktop `AmbientWaves` composable; `PlaybackManager` publishes levels into the same `AmbientLevels` holder Android's `LevelMeterProcessor` feeds.

- [ ] **Step 1: Failing test** `AmbientLevelsTest.spectrumMagnitudesMapToBassAndTreble`: a function `levelsFromSpectrum(magnitudesDb: FloatArray, sampleRate: Float): Levels` returns `bass > treble` for energy in the low bins and the reverse for high bins, and `Levels.Silent` for all-minimum input.
- [ ] **Step 2:** Run it; expected FAIL (function missing).
- [ ] **Step 3:** Implement `levelsFromSpectrum` in `PlaybackManager.kt`; set `MediaPlayer.audioSpectrumListener` (JavaFX, ~20 Hz) to call it and push to `AmbientLevels`. Clear to `Silent` on pause/stop.
- [ ] **Step 4:** Desktop `AmbientWaves`: same drawing as Android, minus the battery-saver receiver and the `Settings` animation-scale read. Take "reduce motion" from a `GlassPrefs`-style desktop flag, default off.
- [ ] **Step 5:** Add `AmbientWaves(...)` and `GlassAmbient` to the backdrop Box in `JukeApp.kt` the way `MainActivity.kt:505-515` does (ambient layer at `zIndex = -1f` under the page `hazeSource`).
- [ ] **Step 6:** Test passes; compile has no `ambient/` errors. Commit `Desktop: ambient waves driven by the JavaFX spectrum`.

### Task 3: Glass API parity, compile-first (unblocks ~15 errors)

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/example/juke/ui/theme/Glass.kt`
- Test: `desktopApp/src/test/kotlin/UiRenderTest.kt` (extend)

**Interfaces:**
- Produces, matching Android's `ui/theme/Glass.kt` exactly: `val LocalGlassBoost = compositionLocalOf { false }`, `@Composable fun glassIsLive(): Boolean`, and `liquidGlass(shape, fill, blur, shadow, rimAlpha, source, strength, displacement: Dp = 64.dp, edge: Dp = 30.dp, fold: Float = 0.4f, clear: Boolean = true)` plus the same added parameters on `glassPane`.

- [ ] **Step 1:** Add the symbols with the new parameters accepted and (for now) ignored; `glassIsLive()` is `LocalHazeState.current != null && !GlassPrefs.solid`.
- [ ] **Step 2:** `bash ./gradlew :desktopApp:compileKotlin --no-configuration-cache`. Expected: exit 0 (this is the first green compile since 3.2.0). Fix any residual error from `MusicViewModel`.
- [ ] **Step 3:** Run `:desktopApp:test` and `:app:testDebugUnitTest`. Expected: all PASS. Report any pre-existing failure by name.
- [ ] **Step 4:** Commit `Desktop compiles again: glass API and database parity`. Push to beta so the "Desktop build" workflow runs on all three OSes.

> Tasks 1-3 are the minimum to produce a working 3.2.0 desktop build. Everything below is feature parity and can ship in a later desktop point release.

### Task 4: Real liquid glass on desktop (Haze 2.1)

**Files:**
- Modify: `gradle/libs.versions.toml` (desktop uses `haze2`, `haze2-blur`, `haze2-glass`), `desktopApp/build.gradle.kts` (replace `libs.haze`), `desktopApp/.../ui/theme/Glass.kt`, `desktopApp/.../desktop/JukeApp.kt` (imports)
- Test: `UiRenderTest` (extend)

- [ ] **Step 1: Failing test** `UiRenderTest.liquidGlassRendersRefractionOnSkia`: render a `Box().liquidGlass(...)` over a checkerboard source and assert the pixel at the lens edge differs from an unrefracted copy.
- [ ] **Step 2:** Run it. Expected: FAIL (Task 3 ignores the parameters).
- [ ] **Step 3:** Port Android's `liquidStyle`, `hazeLiquid`, `GlassStyle.shaped` and the `boost` handling to desktop `Glass.kt`. Skia accepts the same `RuntimeEffect` shader, so no per-OS branch is expected; if refraction needs a feature the JVM artifact lacks, keep the Haze 1 blur path as the fallback and ledger the ruling.
- [ ] **Step 4:** Test passes. Visually check the rail, mini player, song menu and player on macOS and one other OS.
- [ ] **Step 5:** Commit `Desktop: Haze 2.1 liquid glass`.

### Task 5: In-window glass menus and right-click

**Files:**
- Modify: `desktopApp/.../desktop/JukeApp.kt` (host `GlassSheetLayer`, as `MainActivity` does), shared `TrackMenu.kt` call sites only through existing hooks
- Test: `desktopApp/src/test/kotlin/GlassMenuTest.kt` (create)

- [ ] **Step 1: Failing test** `GlassMenuTest.rightClickOnTrackRowOpensMenuAndEscCloses`: Compose desktop test rule; secondary-click a `SearchResultItem`, assert the menu node exists; press Esc, assert gone.
- [ ] **Step 2:** Expected FAIL (no host, no secondary-click mapping).
- [ ] **Step 3:** Wrap the app content in the `GlassSheetLayer` host; map `PointerButton.Secondary` to the same callback as long-press on track rows (a `Modifier.onSecondaryClick` helper in `desktop/`, applied where Android uses `combinedClickable`).
- [ ] **Step 4:** Test passes. Commit `Desktop: glass song menus and right-click`.

### Task 6: Pointer-driven glass highlight

**Files:**
- Modify: `desktopApp/.../ui/theme/Glass.kt`
- Test: `UiRenderTest` (extend)

**Interfaces:**
- Produces: desktop `GlassLight` / `GlassTiltLight()` with the Android signatures; the light direction comes from the pointer position over the window (damped), not a sensor.

- [ ] **Step 1: Failing test** `UiRenderTest.glassLightFollowsPointer`: move the pointer left then right, assert the highlight x-offset changes sign.
- [ ] **Step 2:** FAIL, implement with `onPointerEvent(Move)` and the same damping constant as Android, PASS.
- [ ] **Step 3:** Commit `Desktop: glass highlight follows the pointer`.

### Task 7: Player and Home parity audit

Desktop shows shared `HomeScreen` and its own `DesktopPlayerScreen`. Decide per item, then implement what is missing.

**Files:**
- Modify: `desktopApp/.../desktop/DesktopChrome.kt` (player), `JukeApp.kt` (Home wiring)
- Test: `desktopApp/src/test/kotlin/HomeLayoutTest.kt`, `PlayerStageTest.kt` (create)

- [ ] **Step 1: Home.** At 1100x800 and 520x800, assert the mood chips, an endless-feed rail and a "Couldn't find" miss state render without overflow, and that scrolling to the end requests the next page (`FeedPager`). Fix layout in `HomeScreen` filters only if it breaks; otherwise nothing to change.
- [ ] **Step 2: Player.** Compare `DesktopPlayerScreen` against Android's `PlayerStage` (one stage, Mix preview, "Playing from", Play/Pause for playlists). Port the parts that make sense on a wide window: "Playing from" line, Mix preview, background waves, lyrics overlay timing. Keep desktop's side-by-side layout. Write the gap list into the ledger before coding.
- [ ] **Step 3: Queue behaviour.** Shared `QueueManager` (recommendations session, repeat policy, favourite cooldown) already compiles on desktop; add `QueuePolicyDesktopTest` asserting a desktop `PlaybackManager` run honours `RepeatPolicy` (a just-played song is not re-queued inside its cooldown) and that "Playing from" survives the `RestoreTest` restart path.
- [ ] **Step 4:** Commit per gap, `Desktop: <gap>`.

### Task 8: Version, update check, release

**Files:**
- Modify: `desktopApp/build.gradle.kts` (`VERSION_NAME`, `VERSION_CODE`), `desktopApp/.../services/UpdateManager.kt` only if it assumes the old tag form
- Propose (needs approval): `CHANGELOG.md` desktop lines

- [ ] **Step 1:** Set `VERSION_NAME = "3.2.0-stable"` (or the point release chosen below) and `VERSION_CODE = 32`; replace the hard-coded literals with values read from `app/build.gradle.kts` so they cannot drift again.
- [ ] **Step 2:** `UpdateManager` test: version string `3.2.0-stable` vs release tag `v3.2.0-stable` compares equal; `v3.2.1-stable` is newer.
- [ ] **Step 3:** Run the "Desktop build" workflow on beta; download the macOS DMG, Windows MSI and Linux deb artifacts; smoke test each (search, play, seek, queue, lyrics, restart restore) per `desktopApp/TODO.md`.
- [ ] **Step 4:** Attach installers to the GitHub release (see decision 1). Draft the changelog text for approval.

## Open decisions for the user

1. **Where do the desktop installers go?** `v3.2.0-stable` is already tagged on main at `21098f2`, and that commit does not compile for desktop. Options: (a) ship Tasks 1-3 as `v3.2.1-stable`, desktop installers plus the same APK rebuilt at 3.2.1; (b) merge Tasks 1-3 into main and attach installers to `v3.2.0-stable` as late assets (tag stays on the older commit, so the source for those installers is not what the tag points to). Recommend (a).
2. **Scope of the first desktop release.** Recommend Tasks 1-3 plus 5 (menus) as 3.2.1, and Tasks 4, 6, 7 as 3.3.0, because liquid glass on Skia needs eyes-on testing on three OSes.
3. **Windows/Linux.** `TODO.md` says only macOS has been built and tried. This plan does not change that; Task 8 step 3 is the first multi-OS run since 3.1.x.

## Self-review

- Spec coverage: every 3.2.0 Added/Changed line is either shared and arrives with Task 3 (endless Home, moods, artist page, download menus, favourite cooldown, Jam, DB 13) or has a task (ambient: 2, glass: 3/4/6, menus: 5, player and Mix: 7, version: 8). "Desktop lyrics timing" is already desktop-specific. Tab bar lens and neon ring are mobile-only; desktop keeps its rail, which gets the glass in Task 4.
- Interface consistency: `liquidGlass` parameters in Tasks 3, 4 and 6 are the Android names and defaults.
- Not verified: Task 4's claim that the JVM glass artifact renders refraction identically; the artifact exists (haze-glass-jvm 2.1.0 on Maven Central) but I have not run it.
