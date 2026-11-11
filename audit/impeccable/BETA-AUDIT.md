# JUKE beta — Impeccable native audit

Date: 2026-10-03. Branch: `beta`. Commit: `d01d0ef2bf999f6b24f7f2437a0f68d9317d12f2`.

## Platform conformance verdict

**Pass for native foundation; release-quality gaps remain.** This is a native Compose/Material app, not a website port. Material navigation, SearchBar, sliders, dialogs, switches and lazy lists are established. The main violations are gesture-only interactions without accessible actions, inconsistent light/dark color roles, phone orientation restriction and bottom-only navigation on expanded windows.

## Scope and evidence

Fresh source audit of beta's Activity, home/search/library/settings/player screens, shared controls, theme and artwork extraction. Impeccable 4.3.1 native audit and Android platform rubric used. Source line references below refer to this commit. No application code was changed. No build, install, runtime screenshot, TalkBack session or performance trace was run in this audit. Scores are provisional source assessments, not measured accessibility compliance or performance results. The old main-based stash and its prior screenshots/tests were not applied or used as beta evidence.

Graphify query was attempted; `graphify-out/graph.json` does not exist in this checkout. Source search was used instead. PRODUCT.md and DESIGN.md are absent; existing beta code supplies the incumbent context.

## Audit health score

| Dimension | Score / 4 | Key evidence |
| --- | --- | --- |
| Accessibility | 2 | Gesture-only seek, hero playback and mini-player expansion; playback states not announced |
| Performance | 3 | Lazy lists and IO palette work, but repeated image loaders and non-lifecycle UI subscriptions |
| Appearance & theming | 1 | Dynamic light scheme overwritten with dark surfaces; extracted colors select dark scheme unconditionally |
| Platform conformance | 3 | Material foundation with custom interaction gaps |
| Adaptivity | 1 | Phone portrait lock, bottom-only navigation, fixed-height dialog content |
| **Total** | **10/20** | **Acceptable — significant work needed; source-only provisional score** |

## Executive summary

10 actionable findings: **P0: 0, P1: 4, P2: 6, P3: 0**. No source-confirmed P0 found; runtime task completion is untested.

First fix accessible player/track interactions, coherent theme selection, and expanded-window navigation. Then improve playback state announcements, orientation support, dialog sizing and lifecycle/image handling. Finish with first-use guidance and a bounded visual/device verification pass.

## Detailed findings

### 1. [P1] Seeking has no accessibility adjustment action

- **Location:** `ui/components/player/PlayerProgress.kt:40,84-115`.
- **Category:** Accessibility / platform conformance.
- **Evidence:** CustomSeekBar is a 32dp Box with tap and drag pointer handlers and a Canvas. No progress range, SetProgress action, keyboard adjustment or content description is defined.
- **Impact:** TalkBack and keyboard users cannot operate this seek control using standard adjustment actions. The custom Box also has no Material minimum-touch-target behavior.
- **Guideline:** Accessible controls must expose name, role, value and actions; Android interactive targets should be at least 48dp.
- **Recommendation:** Use Material Slider, or implement progress semantics, keyboard adjustment and a 48dp target. Clamp progress and disable seeking when duration is unavailable; preserve scrubbing feedback.
- **Command:** `$impeccable harden`.

### 2. [P1] Hero playback and mini-player expansion are gesture-only

- **Location:** `ui/components/HeroTrackCard.kt:44-55`; `ui/screens/HomeScreen.kt:307`; `ui/components/MiniPlayer.kt:213-242`.
- **Category:** Accessibility.
- **Evidence:** HeroTrackCard's non-clickable Card modifier uses detectTapGestures to play. MiniPlayer's non-clickable Card uses a pointer handler to expand. Neither defines an equivalent semantics onClick. The home carousel labels its page state, but does not add an accessible playback action to the child card.
- **Impact:** Touch users can play a recent track or open the player; assistive technology users lack the corresponding actionable control. Mini-player's explicit playback buttons do not expose the expand action.
- **Recommendation:** Use Card(onClick), or clickable with a labelled action and appropriate role; keep swipes as optional shortcuts and ensure nested controls remain independent. Add semantics-driven activation tests.
- **Command:** `$impeccable harden`.

### 3. [P1] Theme branches mix light and dark color roles

- **Location:** `ui/theme/Theme.kt:49-74`; `ui/screens/AudioSettingsScreen.kt:102-127`; `ui/screens/PurgeSelectionScreen.kt:84-107`.
- **Category:** Appearance & theming.
- **Evidence:** Extracted artwork always selects darkColorScheme regardless of darkTheme. Dynamic light colors retain light container/foreground roles while background/surface are overwritten black and onSurface white. Settings/purge use their own dark gradients and white text. This differs from a deliberate, coherent dark-only theme.
- **Impact:** System appearance preference is inconsistently respected, and controls from different role families can have mismatched colors. Exact contrast ratios require rendered measurements; this report does not assert a measured WCAG failure.
- **Recommendation:** Select one coherent base light/dark scheme first. Apply artwork accents with verified foreground contrast; use semantic surface/container/text roles across screens. Verify both schemes with and without extracted artwork.
- **Command:** `$impeccable colorize`.

### 4. [P1] Global navigation does not adapt to expanded windows

- **Location:** `MainActivity.kt:357-390`.
- **Category:** Adaptivity / platform conformance.
- **Evidence:** Root Scaffold always renders bottom NavigationBar. No rail, drawer or window-width navigation branch exists. Player has some tablet/height sizing, but global navigation remains the phone structure.
- **Impact:** Tablets and wider multi-window sessions retain stretched phone navigation instead of using available width and predictable Android navigation placement.
- **Recommendation:** Choose NavigationBar on compact windows and NavigationRail/drawer on expanded windows using current window size. Preserve tab restoration, detail back stacks and search reselect behavior.
- **Command:** `$impeccable adapt`.

### 5. [P2] Shuffle and repeat communicate state visually only

- **Location:** `ui/components/player/PlayerControls.kt:60-79,148-174`.
- **Category:** Accessibility.
- **Evidence:** Labels are always “Shuffle”/“Repeat”; active tint and repeat-one icon distinguish state. No selected/toggle semantics or stateDescription is defined.
- **Impact:** A screen-reader user can invoke the control but cannot reliably discover whether shuffle is enabled or repeat is off, all, or one.
- **Recommendation:** Use toggle semantics for shuffle and an explicit state description for repeat's three states. Verify announced state changes without focus loss.
- **Command:** `$impeccable harden`.

### 6. [P2] Phones are locked to portrait

- **Location:** `MainActivity.kt:143-147`.
- **Category:** Adaptivity.
- **Evidence:** smallestScreenWidthDp < 600 forces SCREEN_ORIENTATION_PORTRAIT.
- **Impact:** Users who mount or need to operate a phone in landscape cannot use their preferred orientation; this masks layout weaknesses rather than validating them.
- **Recommendation:** Remove the restriction after adapting player/header/dialog content to shorter windows and large text. Validate rotation and retained UI state.
- **Command:** `$impeccable adapt`.

### 7. [P2] Region dialog reserves a fixed 400dp list

- **Location:** `ui/screens/AudioSettingsScreen.kt:707-735`.
- **Category:** Adaptivity.
- **Evidence:** AlertDialog contains a TextField, title and LazyColumn(height(400.dp)), without a window-relative cap.
- **Impact:** Short or split-screen windows, especially with the IME or enlarged text, have insufficient room for the intended list and dialog actions. Actual clipping remains a device-test risk, not a screenshot-confirmed failure.
- **Recommendation:** Bound list height to available window/dialog space, retaining lazy scrolling and reachable actions. Verify filtering with keyboard open and 130%/200% text.
- **Command:** `$impeccable adapt`.

### 8. [P2] Artwork palette extraction creates a fresh image loader

- **Location:** `viewmodels/MusicViewModel.kt:488-506`.
- **Category:** Performance.
- **Evidence:** Each extractColors invocation constructs ImageLoader(getApplication()); request has no palette-specific target size. Palette generation is correctly on Dispatchers.IO.
- **Impact:** Loader/cache reuse is lost and palette decoding can consume more resources than needed as tracks change. Frame drops or memory impact have not been profiled.
- **Recommendation:** Reuse the app Coil loader and request a small software bitmap suitable for palette sampling; verify cache reuse and latest-track color ownership.
- **Command:** `$impeccable optimize`.

### 9. [P2] UI Flow subscriptions are not lifecycle-aware

- **Location:** `MainActivity.kt:156,178`; `ui/screens/PlayerScreen.kt:151-167`; `ui/screens/AudioSettingsScreen.kt:87-93`; `ui/screens/SearchScreen.kt:114-115`; `ui/components/MiniPlayer.kt:101-106`.
- **Category:** Performance.
- **Evidence:** UI uses collectAsState; no collectAsStateWithLifecycle usage found. Composition disposal still cancels subscriptions, but an existing composition can remain subscribed below STARTED.
- **Impact:** Background UI subscriptions may continue processing state unnecessarily. This is an efficiency gap, not evidence of a memory leak.
- **Recommendation:** Use lifecycle-runtime-compose for UI subscriptions while preserving service/background playback scopes. Separately inspect player polling and animations; changing subscription API alone does not stop all background work.
- **Command:** `$impeccable optimize`.

### 10. [P2] Empty home describes a next step without an inline action

- **Location:** `ui/screens/HomeScreen.kt:699-769`.
- **Category:** Platform conformance / adaptivity / onboarding.
- **Evidence:** Empty state says to search/download, but contains no search CTA; the duplicate Settings icon is its only explicit action. Centered content uses fixed 40dp padding without its own scroll behavior. Bottom Search navigation remains available.
- **Impact:** First-use activation requires interpreting the text and locating another control. Short windows or large text can crowd the centered block; overflow is untested.
- **Recommendation:** Add one clear “Find music” action to the existing Search route, remove redundant Settings placement, and allow the empty content to scroll in constrained windows. Keep native navigation available.
- **Command:** `$impeccable onboard`, then `$impeccable adapt`.

## Systemic patterns

- Accessible semantics are uneven: recent home cards have explicit roles/labels while older pointer-driven shared controls do not.
- Theme selection and screen-specific dark styling are separate sources of visual truth.
- Sizing responds to some player heights but not consistently to root window width, dialog space and text scaling.
- UI subscription and image-loader policies need shared conventions instead of per-screen decisions.

## Positive findings to preserve

- HomeHeader already has weighted text and a labelled Material Settings button; the old main header issue is not repeated here.
- Recently-played carousel has state labels, explicit navigation controls, accessibility-recommended timing and skips auto-advance during touch exploration (`HomeScreen.kt:239-390`). Do not replace these with the old main behavior.
- Home horizontal/favorite cards expose merged semantics and clickable roles (`HomeScreen.kt:500-517,624-634`).
- Search uses Material SearchBar/InputField, clear/back IconButtons and theme-aware placeholder colors (`SearchScreen.kt:169-239`). The old custom 48dp BasicTextField issue does not apply.
- Lazy containers support large collections; Material dialogs/sliders/switches provide useful native defaults.
- Artwork extraction runs off the main thread.
- Player has compact-height and tablet sizing (`PlayerScreen.kt:307-323`); extend this foundation rather than replacing it blindly.

Unused CompactTrackCard and custom EQ/booster source also contain pointer-only controls, but were not counted as live-screen defects: CompactTrackCard has no call sites, and current settings use Material sliders rather than the reusable EQ widgets. Material IconButton layouts sized 36–44dp deserve touch-bound verification, but automatic Compose hit-target expansion means visual size alone is not proof of a sub-48dp hit region.

## Recommended implementation order

1. **[P1] `$impeccable harden`** — restore semantic play/expand/seek actions; include shuffle/repeat state announcements (P2).
2. **[P1] `$impeccable colorize`** — establish coherent light/dark roles and safe artwork accents.
3. **[P1/P2] `$impeccable adapt`** — rail navigation, landscape, short-window dialogs, large-text/inset validation.
4. **[P2] `$impeccable optimize`** — shared/downsampled Coil palette requests and lifecycle-aware UI collections.
5. **[P2] `$impeccable onboard`** — direct empty-library search action.
6. **`$impeccable polish`** — bounded spacing/hierarchy and device verification after the above changes.

Before release, run beta's build/tests and semantics-driven interaction tests. Capture compact/expanded layouts, both appearances, large text, keyboard-open search/region dialog and populated player/queue/lyrics. Test TalkBack/Switch Access and navigation on a physical device; profile performance before assigning measured claims. Include Remove animations, RTL, multi-window and foldable posture in the follow-up matrix.

You can ask me to run these one at a time, all at once, or in any order you prefer. Re-run `$impeccable audit` after fixes to reassess the score.
