# Beta improvements and verification

2026-10-03. Applied directly to `beta`, starting at `d01d0ef2bf999f6b24f7f2437a0f68d9317d12f2`. The old main-based stash was not applied. The pre-change BETA-AUDIT.md remains historical; no new numerical compliance score is claimed.

## Implemented

All ten audit findings have an implementation. Existing beta search, carousel accessibility/timing, playback/service behavior, navigation restoration and detail back stacks were preserved.

- Material seek semantics, disabled seeking when duration is unknown, scrub haptics. At the user's request, styled with an always-visible **20dp circular thumb** and **6dp track**, without changing accessible adjustment or the control's normal touch area.
- Hero cards expose a labelled Play action; mini-player has a labelled Open player action while retaining swipe shortcuts and independent playback buttons.
- Shuffle state is announced; repeat announces off/all/one.
- Base theme follows system light/dark preference with Material dynamic/static fallback. Artwork adjusts the primary accent with contrast checks against the theme surface and selects a contrasting button foreground. Settings/purge/player/mini-player foreground and background roles now follow the theme.
- Compact windows retain bottom navigation; widths >=600dp use a rail sharing the same tab handler.
- Removed phone portrait lock; player content scrolls in short windows and explicit compact playback controls are at least 48dp.
- Region list maximum height is 40% of current window height; search field has a persistent label.
- Palette sampling uses the shared Coil loader and 128px software images; superseded jobs are cancelled and cancellation is not treated as a loading failure.
- UI Flow subscriptions use collectAsStateWithLifecycle; full/mini-player progress polling stops below STARTED. Background playback services remain separate.
- Empty library has a Find music action using the existing Search route, no duplicate Settings button, scroll support and a compact short-window arrangement. Search field surface and label are clearer; large placeholder text ellipsizes.

Added lifecycle-runtime-compose at the existing Lifecycle 2.8.7 version. Android test JUnit/Espresso versions updated to 1.3.0/3.7.0 for the Android 17 test environment. No release version or credentials were changed.

## Compile after each fix

Each row ran the repository wrapper's `:app:assembleDebug`, using the installed JDK. Failures encountered during implementation were corrected before proceeding; rows report the final successful compile of each fix. Build logs are local ignored artifacts.

| Audit finding | Change | Compile | Log |
| --- | --- | --- | --- |
| 1 | Accessible seeking | Pass (1m 2s) | `.gradle/beta-fix-01-seek.log` |
| 2 | Track play / mini-player expand actions | Pass (3s) | `.gradle/beta-fix-02-actions.log` |
| 3 | Coherent light/dark themes | Pass (21s) | `.gradle/beta-fix-03-theme.log` |
| 4 | Expanded navigation rail | Pass (7s) | `.gradle/beta-fix-04-navigation.log` |
| 5 | Shuffle/repeat state announcements | Pass (2s) | `.gradle/beta-fix-05-playback-states.log` |
| 6 | Rotation and scrollable player | Pass (13s) | `.gradle/beta-fix-06-orientation.log` |
| 7 | Window-relative region dialog | Pass (5s) | `.gradle/beta-fix-07-region-dialog.log` |
| 8 | Shared, downsampled artwork loading | Pass (15s) | `.gradle/beta-fix-08-palette.log` |
| 9 | Lifecycle-aware UI state and polling | Pass (9s) | `.gradle/beta-fix-09-lifecycle.log` |
| 10 | Direct first-use search action | Pass (2s) | `.gradle/beta-fix-10-empty-home.log` |
| — | Regression tests and seek haptics | Pass (4s) | `.gradle/beta-fix-11-regression-support.log` |
| — | Search visibility and landscape empty-state fit | Pass (4s) | `.gradle/beta-fix-12-visual-polish.log` |
| 1 refinement | Circular thumb and slim seek track | Pass (3s) | `.gradle/beta-fix-13-circular-seek.log` |

## Final checks

- `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest` — BUILD SUCCESSFUL (`.gradle/beta-final-build.log`).
- JVM suite: 1 test, zero failures.
- Final device suite on **Medium_Phone emulator, Android 17**: **OK (5 tests)** (`.gradle/beta-circular-device-tests.log`). Four new tests cover accessible seek adjustment, disabled unknown-duration seek, accessible recent-track activation, and light/dark artwork-accent contrast; one original instrumentation test also passes.
- The earlier complete Gradle connected suite also passed all five tests after visual polish (`.gradle/beta-final-tests.log`). An initial generic-device test run selected the connected Android 16 phone and was interrupted; final checks explicitly targeted emulator-5554.
- `git diff --check` passes.
- Installed final debug APK: `app/build/outputs/apk/debug/app-debug.apk`, version `2.3.2-beta-unreleased`.

## Visual evidence

Native emulator screenshots, not browser mockups:

- [Light home](beta-home-light.png).
- [Dark home at 130% text](beta-home-dark-large-text.png).
- [Search at 130% text](beta-search-dark-large-text.png).
- [Settings in dark](beta-settings-dark-large-text.png) and [light](beta-settings-light-large-text.png), both 130% text.
- [Expanded home with rail](beta-expanded-dark.png).
- [Short landscape home](beta-landscape-home.png), showing the direct search action after the compact layout fix.
- [Landscape settings](beta-landscape-settings.png).
- [Landscape region dialog](beta-region-landscape.png), with scrollable list and reachable Close action.
- [Final circular seek control](beta-circular-seek.png), captured from the native Compose regression test after accessible adjustment to 75%.

Display dimensions were simulated on the same emulator (1080x2400 compact, 1800x2400 expanded, 2400x1080 short landscape). Expanded captures are window-size coverage, not independent tablet hardware testing. One batched visual pass and a bounded confirmation pass were used; the user-requested circular-thumb refinement was then compiled and tested separately.

## Verification limits

Physical TalkBack/Switch Access review, populated full-player/queue/lyrics visual flows, foldable hinges, RTL, maximum font scales and performance traces were not verified. Theme tests check the named primary/surface/onPrimary/onSurface pairs, not every rendered color pair. The landscape keyboard capture was obscured by Gboard's font-update banner and is retained as diagnostic evidence (`beta-region-landscape-keyboard.png`), not a successful IME-layout verification. Further portrait-IME capture was interrupted when the emulator disconnected. Dialog list sizing itself was verified in short landscape without the keyboard.

Original emulator size, font scale 1.0, automatic night mode and permission prompt flags were restored after checks. Phone-state permission was not granted for visual inspection. Changes are local and uncommitted on beta; no push was requested.
