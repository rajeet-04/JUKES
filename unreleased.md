# Unreleased

## Overview

This document tracks changes, features, and bug fixes that have been implemented post-v2.3.0-beta but are not yet part of an official release.

## 🚀 Features & Enhancements

### Ensemble Recommendation Engine Improvement

- **Robust Seed Pool Construction:** Redesigned the recommendation generation sequence. The seed pool is now dynamically constructed using up to 5 tracks (the currently playing track, the first track in the session, and up to 3 random tracks pulled directly from session history).
- **Session History Preservation:** Upgraded manual queue navigation, list restorations, and seeking to allow the `QueueManager` to retain full historical context. This grants the randomizer sufficient data to pull highly diverse YouTube intersections instead of starving the seed pool.
- **Parallel Network Intersections:** The ensemble actively fetches related items for all 5 seed tracks simultaneously using Kotlin Coroutines, intersecting the resulting 50-track lists to provide incredibly relevant dynamic radio queues based on frequency matching.
- **Heuristic Offline Fallback Scoring:** Implemented an intelligent scoring algorithm for local library recommendations when offline. Tracks are weighted by primary artist (+10), shared collaborators (+5), album similarity (+8), favorite status (+10), and popularity, while penalized for recent playback to ensure fresh listening sessions.

### Streaming Playback & Download Logic

- **Seamless Stream Promotion (Background Downloads):** Transitioning a song from a temporary stream to a permanent background download will no longer cause ExoPlayer timeline resets or audio stuttering if it is the actively playing track (via the `seamlessIfPlaying` atomic swap bypass).
- **Optimized Stream Source Routing:** First-click instantly selected search tracks are now routed exclusively to the faster Spotmate API, while subsequent queue stream fetches remain 50/50 load-balanced across both Gamepvz and Spotmate providers.
- **Async Lyrics Hydration for Instant Playback:** Decoupled LRCLib lyrics fetching from the instant stream critical path. Playback now starts immediately after stream file preparation, while lyrics are fetched in a detached background coroutine and applied to the active track/queue once available.
- **Async YT Video ID Hydration for Stream Start:** Decoupled `RecommenderApi.getBestVideoMatch(...)` from the instant stream critical path. Stream playback now starts without waiting for YouTube ID resolution, then hydrates `ytVideoId` asynchronously into DB/UI/queue state for recommendation seeding.
- **Graceful Lyrics Overlay Loading State:** Updated lyrics overlay rendering to handle delayed lyric arrival with a transient "Loading lyrics..." indicator, then a clean fallback message when lyrics are unavailable.

### Premium Interactive Media Controls

- **Animated Play/Pause Transition:** Implemented a high-performance "Pro" animation combo for the main media controls and mini-player. Instead of abrupt icon swaps, the buttons now utilize a synchronized scale bounce (1.0 → 0.85 → 1.0) and a 180ms `Crossfade` transition, mimicking the premium feel of native morphing with zero SVG path overhead.
- **Material 3 Player Options Menu Refresh:** Restyled the player 3-dots menu with rounded M3 surfaces, elevated tonal container color, and semantic leading icons for Sleep Timer, Album, Refresh Lyrics, Romanized toggle, and Block Artist actions.
- **Bottom Action Pill Container:** Grouped Queue, Radio, and Share into a cohesive floating pill surface using tonal `surfaceVariant` styling, circle affordances, and improved visual grounding at the bottom of the player.
- **Lyrics Sync Controls Overhaul:** Redesigned sync offset controls into a rounded elevated card with richer typography, dedicated Reset/Done actions, and ratchet-style `haptic.tick()` feedback while scrubbing the sync slider.
- **Floating Pill Mini-Player Redesign:** Upgraded the mini-player from a full-width sharp block to a rounded floating Material 3 card with side margins, elevated depth, softened album art corners, stronger text hierarchy, and an integrated clipped bottom progress bar.
- **Animated Dancing Glass Background:** Added a frosted glassmorphism layer with animated, color-extracted mesh lights (`DancingGlassBackground`) that reacts to playback state and album palette for a premium, music-reactive mini-player surface.

### Haptics & Micro-interactions

- **Premium Lyrics Toggle Haptic:** Added a crisp haptic `click` when toggling lyrics by tapping the album artwork (`PlayerArtwork`) to provide a satisfying tactile response when opening/closing the lyrics overlay.
- **Haptics Engine Upgrade:** Replaced the legacy haptic helper with a `VibrationEffect`-based engine (`JukeHaptics`) with safe fallbacks, and mapped semantic haptics (click, heavyClick, tick, toggle, confirm, reject) across core player UI components. Added the `VIBRATE` permission.

## 🔧 Bug Fixes

### Queue Race Condition & Duplication

- **Atomic Media Swapping:** Replaced discrete ExoPlayer controller commands (`removeMediaItem` + `addMediaItem`) with the atomic `replaceMediaItem()` function. This inherently eliminates intermediate ExoPlayer timeline "item dropped" broadcasts. ViewModels no longer get out of sync, definitively solving the bug where downloading an active stream appended a duplicate track recommendation at the bottom of the playlist.

### LRU Streaming Stability

- **Eviction Protection (ENOENT Fix):** Implemented pinned UUID protections within the `MusicService` ensuring that actively queued stream files cannot be prematurely targeted by background LRU disk deletion limits.
- **Redundant Download Prevention:** Addressed severe LRU cache churn and frequent `FileNotFoundException` crashes by fixing the state validation check so that manually swiping/removing a track doesn't aggressively trigger unnecessary re-downloads of its surrounding recommendations.
- **Duplicate Stream Request Avoidance:** Implemented debouncing/check routines on the UI so impatient clicks on search terms do not spawn identical, duplicate network streaming requests while one is already in flight.
