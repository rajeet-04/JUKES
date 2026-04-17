# Unreleased

## Overview

This document tracks changes, features, and bug fixes implemented after the **v2.3.1-beta** release (2026-04-15).

## 🚀 Features & Enhancements

### Premium Interactive Media Controls

- **Lyrics Sync Offset Extension:** Increased the sync offset limit from ±5s to ±12s within the `LyricsSyncControls` menu, allowing for correction of severely misaligned lyric tracks.
- **Mini-Player Smart Lyrics Fallback:** Added two intelligent conditions to prevent stale lyrics lingering in the mini-player:
    - **Music Symbol Filter:** Lines composed entirely of musical notation symbols (♪ ♫ ♬ 𝄞 ♯ ♭ ♮ etc.) are detected as instrumentation markers, causing the display to fallback to song metadata.
    - **Adaptive Gap Threshold:** Implemented a per-song threshold calculation (Median Gap × 2.2) to intelligently revert to metadata during silences, ensuring lyrics hold appropriately for slow ballads while falling back quickly for fast tracks.

### Spotmate Queue-Aware Download Recovery

- **Queued Convert Detection:** Spotmate `/convert` responses with `status: queued` or `status: processing` now raise a typed queue exception carrying `task_id`, instead of being treated as a hard failure.
- **Immediate Fallback Preservation:** When Spotmate queues a task, JUKE now immediately falls back to Gamepvz so playback/download does not stall waiting on Spotmate processing.
- **Deferred Spotmate Recovery:** If both direct providers fail, JUKE now polls `https://spotmate.online/tasks/{task_id}` and downloads from `result.download_url` once Spotmate reports `status: finished`.
- **Applied To Both Flows:** The queue-aware fallback logic is now active for both instant stream file resolution and full `smartDownloadAndIndex` downloads.

### Enhanced Search Experience

- **YouTube Music Autocomplete Integration:** Integrated a lightweight YT Music suggestion endpoint to provide real-time text suggestions while typing, significantly reducing latency compared to live Spotify API searches.
- **Split Search Architecture:**
    - **Typing:** Triggers a 300ms debounced fetch of simple text suggestions via Ktor, keeping the UI responsive.
    - **Submission:** Full Spotify metadata searches (tracks, artists, albums, playlists) are only executed upon keyboard "Enter" or clicking a specific suggestion/recent search.
- **Multi-Tap Bottom Navigation Logic:** Added advanced state management to the Search tab in `MainActivity`:
    - **Single Tap:** Navigate to the Search screen.
    - **Double Tap:** Reset for a new search (clear query, focus bar, open keyboard).
    - **Triple Tap:** Focus the bar and open the keyboard *without* clearing the current query, allowing for quick edits.
- **Precision Keyboard Management:**
    - **Scroll-to-Dismiss:** Implemented a nested scroll connection on the search results list. Dragging the list down by more than 5px automatically dismisses the keyboard, matching native Android system behavior (Google App pattern).
    - **Programmatic Focus:** Added 100ms delayed `FocusRequester` triggers to ensure reliable keyboard pops during navigation transitions.
- **Duration-Aware Search Results:** Replaced the unused "3 dots" icon in search results with the track duration (e.g., "3:45"), aligning the search results layout with the native library track format.
