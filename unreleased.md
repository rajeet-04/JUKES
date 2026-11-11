# Unreleased

## Overview

This document tracks changes, features, and bug fixes implemented after the **v2.3.2-beta** release (2026-04-27).

## 🚀 Features & Enhancements

### Startup Performance

- **Lazy Analytics Init:** PostHog initialization deferred to after first frame (`MainActivity` post-draw callback) instead of `Application.onCreate`, reducing cold-start blocking work.
- **HomeViewModel Eager Load:** `HomeViewModel` now starts `loadHomeData()` in its `init` block with `isLoading = true` to eliminate empty placeholder frames on launch.
- **TTFD Reporting:** `ReportDrawnWhen { uiState.isReady }` added to `HomeScreen` to emit accurate Time-to-First-Draw metrics when the start destination finishes loading.

### Macrobenchmark Module

- **`:benchmark` Module Added:** New `com.android.test` module targeting `:app` with `StartupBenchmark.coldStartup()` using `StartupTimingMetric` for cold-start measurement.
- **Profileable Support:** Added `<profileable android:shell="true" />` to the app manifest and `androidx.profileinstaller` dependency for on-device benchmark runs.

### Security Hardening

- **PostHog Credentials Removed from Source:** Hardcoded PostHog API key and host removed; credentials now sourced exclusively from `local.properties` and injected via `BuildConfig` at compile time.

### UI Polish

- **Swipe Row Background Fix:** `LibraryTrackItem` and `SearchResultItem` swipeable rows now use `MaterialTheme.colorScheme.background` as the foreground container color, preventing swipe-action text from bleeding through at rest.

## 🔧 Bug Fixes

- **Shuffle Crash / OOM Fixed:** `PlaybackService` previously embedded full artwork bytes into every `MediaItem` via `setArtworkData`. Repeatedly shuffling cloned artwork-heavy `MediaMetadata` and could OOM. Artwork is now stored as `artworkUri` for both local and remote images, and shuffle reorders existing queue items in-place instead of rebuilding `MediaItem` objects.
- **LibraryScreen Shuffle Recomposition Fixed:** Playback-state reads (shuffle flag, download banner) in `LibraryScreen` are now scoped to small child composables via mapped flows instead of collecting the full `MusicViewModel.uiState` at the top level, preventing whole track-list recompositions on every playback event.
