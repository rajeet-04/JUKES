# Playback startup — beta, 2026-10-03

## Evidence

The supplied device log shows 6.888 seconds resolving a URL through sequential Spotmate failure and Gamepvz fallback. It ends during buffering and does not establish actual PLAYING time.

Medium_Phone emulator, live network, same Gulabi Aankhen/Sanam search result, uncached app data:

| Run | URL resolution | URL-to-PLAYING | Total |
| --- | ---: | ---: | ---: |
| Concurrent providers, proxy stream | 2.635 s | 12.531 s | 15.167 s |
| Concurrent providers, CDN stream, concurrent background download | 2.258 s | 18.153 s | 20.413 s |
| Final: CDN stream, background download deferred | 1.738 s | 7.876 s | 9.616 s |

Final timestamps: tap path begins 20:52:31.999; URL resolves 20:52:33.739; HTTP connection completes 20:52:40.777; first bytes 20:52:40.790; PLAYING 20:52:41.615; background download starts 20:52:41.639. Media session confirmed PLAYING and subsequently PAUSED. Sanitized boundary logs are in EMULATOR-STARTUP-TIMINGS.log.

This is a small live-network sample, not a controlled benchmark. Provider latency and the returned CDN host vary. Final HTTP connection alone took 6.953 s; near-instant uncached playback is not established. Initial host curl probes measured proxy first response 11.501 s versus embedded CDN 1.205 s, but emulator results show substantial variability.

## Changes

- Instant playback resolves both existing providers concurrently; first success wins, losers cancel. Each provider has a 10-second bound. Cancellation and queued full-download recovery are retained.
- Recognized Gamepvz wrappers use their signed HTTPS CDN URL for instant streaming. Invalid/unknown envelopes retain the proxy. Allowed destinations use cdn-spotify host names under zm.io.vn and /download/ paths, without user information. Normal download provider ordering is unchanged.
- Background file persistence waits up to 60 seconds for the selected UUID to reach PLAYING, avoiding a competing full-download request during startup.
- HTTP connection and first-byte timing logs separate upstream latency from decoder/buffering time without logging signed URLs.

## Validation and sound

Sequential resolution failed the stalled-primary regression before the fix. Proxy selection failed the CDN regression before the fix. Final debug build and full JVM suite pass (8 tests). Emulator instrumentation suite passes all 5 tests (.gradle/playback-device-tests.log, OK in 5.944 seconds).

The emulator did not retain media volume zero through app startup: it reported volume 5 after attempts to set 0. Playback was paused on discovery. Subsequent emulator runs used -no-audio, disabling host output regardless of Android volume. Final playback was paused before device regression tests. No physical phone was used.

Changes remain local on beta; no push or release notes edits. Existing user Gradle/IDE changes were preserved.
