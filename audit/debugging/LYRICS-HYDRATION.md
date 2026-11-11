# Automatic lyrics hydration — beta, 2026-10-04

## Causes found

Automatic hydration was only invoked in the new HTTP stream path, leaving local/cached plays and restored/next-track transitions without a reliable trigger. The completed-file callback also persisted the original HTTP track snapshot; lyrics fetched while the download was running could be overwritten with nulls. The reproduction test failed on that loss before the fix.

## Changes

- Observe current-track UUID changes and automatically fetch when both lyrics formats are missing; remove the duplicate instant-stream-specific trigger.
- Merge only the completed local file URI into the latest persisted track under a Room transaction. Preserve lyrics, video ID, favourites and offsets.
- Serialize lyrics and video metadata read/modify/write with Room transactions to avoid overwriting each other's results.
- Coalesce in-flight refreshes per UUID; retry an empty result once after 1.5 seconds using the latest persisted metadata. Preserve existing useful lyrics when a provider omits a format. Instrumental results do not retry.
- Propagate cancellation from refresh jobs.

## Verification

Stale-snapshot unit regression failed before fixing and passed afterward. Retry regressions failed before implementation and passed afterward. Full JVM suite: 12 tests pass. Build and Android test APK compilation passed. Emulator device suite: 5 tests pass (5.003 seconds).

Live restored-song check on Medium_Phone emulator with -no-audio: Gulabi Aankhen was paused, had missing lyrics in the old persisted state, automatically refreshed at 00:22:48.893 and found lyrics at 00:22:50.204 (1.311 seconds). Opened the player and lyrics overlay without pressing Refresh Lyrics; Hindi lyric lines were visible. Screenshot: automatic-lyrics-visible.png. No physical phone used; playback remained paused.

Providers can still lack lyrics or remain unavailable; the bounded retry does not guarantee coverage for every song.

## Footer request

Restored a transparent-to-opaque vertical gradient behind Home/Search/Library, ending in the theme surface color. NavigationBar is transparent with zero tonal elevation so it does not cover the gradient. The gradient spans the footer height; expanded navigation rail behavior is unchanged. Compile passed; native light-theme screenshot: ../impeccable/beta-footer-gradient.png.

All changes local on beta; no push or release notes edits.
