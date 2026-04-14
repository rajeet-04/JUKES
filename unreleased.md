# Unreleased

## Overview
This document tracks changes, features, and bug fixes that have been implemented post-v2.3.0-beta but are not yet part of an official release.

## 🚀 Features & Enhancements

### Ensemble Recommendation Engine Improvement
- **Robust Seed Pool Construction:** Redesigned the recommendation generation sequence. The seed pool is now dynamically constructed using up to 5 tracks (the currently playing track, the first track in the session, and up to 3 random tracks pulled directly from session history).
- **Session History Preservation:** Upgraded manual queue navigation, list restorations, and seeking to allow the `QueueManager` to retain full historical context. This grants the randomizer sufficient data to pull highly diverse YouTube intersections instead of starving the seed pool.
- **Parallel Network Intersections:** The ensemble actively fetches related items for all 5 seed tracks simultaneously using Kotlin Coroutines, intersecting the resulting 50-track lists to provide incredibly relevant dynamic radio queues based on frequency matching.

### Streaming Playback & Download Logic
- **Seamless Stream Promotion (Background Downloads):** Transitioning a song from a temporary stream to a permanent background download will no longer cause ExoPlayer timeline resets or audio stuttering if it is the actively playing track (via the `seamlessIfPlaying` atomic swap bypass).
- **Optimized Stream Source Routing:** First-click instantly selected search tracks are now routed exclusively to the faster Spotmate API, while subsequent queue stream fetches remain 50/50 load-balanced across both Gamepvz and Spotmate providers.

## 🔧 Bug Fixes

### Queue Race Condition & Duplication
- **Atomic Media Swapping:** Replaced discrete ExoPlayer controller commands (`removeMediaItem` + `addMediaItem`) with the atomic `replaceMediaItem()` function. This inherently eliminates intermediate ExoPlayer timeline "item dropped" broadcasts. ViewModels no longer get out of sync, definitively solving the bug where downloading an active stream appended a duplicate track recommendation at the bottom of the playlist.

### LRU Streaming Stability
- **Eviction Protection (ENOENT Fix):** Implemented pinned UUID protections within the `MusicService` ensuring that actively queued stream files cannot be prematurely targeted by background LRU disk deletion limits.
- **Redundant Download Prevention:** Addressed severe LRU cache churn and frequent `FileNotFoundException` crashes by fixing the state validation check so that manually swiping/removing a track doesn't aggressively trigger unnecessary re-downloads of its surrounding recommendations. 
- **Duplicate Stream Request Avoidance:** Implemented debouncing/check routines on the UI so impatient clicks on search terms do not spawn identical, duplicate network streaming requests while one is already in flight.
