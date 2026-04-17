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

### Three-Stage Lyrics Fallback Chain

Significantly expanded lyrics coverage by wiring two YouTube sources as automatic fallbacks after LRCLib:

- **Stage 1 — LRCLib (unchanged):** Primary source; attempts exact track + artist match and an individual-artist fallback. Results are ranked by validation score, synced-lyrics availability, and duration proximity.
- **Stage 2 — YouTube Music Lyrics (new):** When LRCLib returns no match, a YTM-dedicated lyrics endpoint is queried via the two-step Innertube `next` + `browse` flow. Provides the cleanest, human-reviewed plain text lyrics for tracks where YTM has an official lyrics tab.
- **Stage 3 — YouTube Captions (new):** If the YTM lyrics tab is absent, YouTube's `player` endpoint is called to obtain a pre-signed timedtext URL (no signature reverse-engineering needed). Captions are fetched in JSON3 format and flattened to plain text.
- **On-the-Fly Video ID Resolution:** Each track stores a `ytVideoId`. If this field is not yet populated (e.g., newly indexed tracks), `RecommenderApi.getBestVideoMatch("$title $artist")` is called inline to resolve one before attempting Stages 2 and 3, eliminating the hard dependency on asynchronous pre-population.
- **No performance impact for LRCLib tracks:** YTM API calls are only made when LRCLib returns `null`, ensuring zero overhead for the majority of tracks already covered by LRCLib.
