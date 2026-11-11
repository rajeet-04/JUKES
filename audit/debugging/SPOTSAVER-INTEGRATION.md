# Spotsaver provider integration — beta, 2026-10-04

Spotsaver is now primary for instant HTTP playback, stream-to-file recovery/prefetch, and permanent downloads. Existing Gamepvz/Spotmate resolution, legacy provider preference, and queued Spotmate recovery remain fallbacks. The new API uses title/artist lookup, then requests MP3 conversion and consumes downloadUrl. It requires successful JSON responses and an HTTPS URL without embedded credentials.

Instant URL resolution has an 8-second Spotsaver bound. Complete stream-file downloads have a 30-second primary bound; permanent downloads have a 45-second primary bound. Provider failures/timeouts fall back; cancellation of the caller propagates. Fallback regressions cover primary success, API error, timeout, and cancellation.

Spotsaver tunnel downloads skip HEAD and ranged segmentation and use one GET. Existing providers retain range probing. Completed files must satisfy size and MP3 header validation before indexing/persistence; background stream downloads reject invalid payloads. The existing lyrics hydration and background-after-PLAYING behavior are preserved.

Verification:
- New-provider selection and parser regressions failed against baseline before implementation.
- Final debug APK, Android test APK and full JVM suite passed: 18 tests.
- Default emulator device suite: 5 regression tests pass, 2 live tests skipped by default.
- Explicit live suite: two fresh API sessions plus a full production download pass.
- Final full download using the actual app ApiClient and FastDownloader: 6,726,008 bytes in 5,831 ms; Android MediaMetadataRetriever reads duration 168,150 ms. No HEAD/range probe. Temporary MP3 deleted after verification. Safe results: spotsaver-production-results.json.
- All tests used the emulator with -no-audio. No physical phone or audible playback.

Not verified: end-to-end playback startup timing for the new provider, every catalog entry, future provider access, tunnel range/seek support or server-side expiry behavior. Existing download duration validation remains in place. Live tests are opt-in with spotsaverLive=true; normal CI does not call the service.

Changes are local on beta, based on e117d47; not pushed in this step.
