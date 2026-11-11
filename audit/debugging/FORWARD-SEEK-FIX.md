# Forward seeking after streaming — beta, 2026-10-04

The completed-download callback intentionally skipped replacing the current HTTP media item for seamless audio. UI/DB metadata pointed at the local file, but the player continued using the remote tunnel. The seek handler only forwarded seekTo to that remote item.

The player now retains a completed local source for the current track. On explicit seek it rebuilds the existing playlist with that local item at the requested initial position, preserving queue order and playWhenReady. For a remote item that reports itself unseekable before its local file is ready, the requested position is deferred and applied at file completion. Switching songs cancels that deferred request. Ordinary local-file seeking remains unchanged.

Verification:
- Baseline handoff state failed three unit regressions before implementation.
- Full JVM suite passes 22 tests; final debug and Android test APK compilation pass.
- Live real-player test on emulator with -no-audio: resolve/download Jasmine, start its HTTP stream, deliver completed local source, seek to 120000 ms, verify PLAYING within 120000–125000 ms, verify actual current URI is the local file and the two-item queue is preserved. Pause, seek to 60000 ms, verify the position while still paused. Passed in 7.710 seconds including network/setup.
- Default device suite passes five regressions; both opt-in live tests are skipped by default.
- Safe live report: seek-forward-results.json; transition proof: seek-forward-timings.log. Temporary MP3 and test queue were cleared after testing.

The final unit suite also covers cancelling a deferred seek when changing tracks. Pending-before-completion behavior is unit-tested; the live test covers a seek after completion. No physical phone and no audible playback. Changes are local on beta, not pushed.
