# Unreleased

## Overview

This document tracks changes, features, and bug fixes implemented after the **v2.3.1-beta** release (2026-04-15).

## 🚀 Features & Enhancements

### Premium Interactive Media Controls

- **Lyrics Sync Offset Extension:** Increased the sync offset limit from ±5s to ±12s within the `LyricsSyncControls` menu, allowing for correction of severely misaligned lyric tracks.
- **Mini-Player Smart Lyrics Fallback:** Added two intelligent conditions to prevent stale lyrics lingering in the mini-player:
    - **Music Symbol Filter:** Lines composed entirely of musical notation symbols (♪ ♫ ♬ 𝄞 ♯ ♭ ♮ etc.) are detected as instrumentation markers, causing the display to fallback to song metadata.
    - **Adaptive Gap Threshold:** Implemented a per-song threshold calculation (Median Gap × 2.2) to intelligently revert to metadata during silences, ensuring lyrics hold appropriately for slow ballads while falling back quickly for fast tracks.
