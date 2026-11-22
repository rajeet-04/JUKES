# Handoff: partial screen redraws on the player

Status: open investigation. Last touched 2026-11-23, at release 3.2.1-stable (`20cea65` on beta).

## Goal

The player screen updates its position about every 300 ms while a song plays. Each update should redraw only the seek bar, the time labels and the lyric ticker. The artwork, glass controls and background should stay static and not be repainted.

Today every update repaints the **whole window**, including every glass surface. Glass (Haze 2.1 liquid glass) is the expensive part: forcing glass solid roughly halves per-frame RenderThread and GPU time.

## Current numbers

All measurements: release build on a vivo I2220 (Adreno, Skia GL pipeline) at 60 Hz, player open, a track without synced lyrics, 20 s.

| | Total frames | Janky | Frame p50 | GPU per frame | RenderThread per frame |
|---|---|---|---|---|---|
| Before seek/ticker layers | 142 | 62 (44%) | 23 ms | 9.8 ms | 8.8 ms |
| After (shipped in 3.2.1) | 160 | 62 (39%) | 21 ms | 7.9 ms | 9.1 ms |

Janky frames are about one per position update. A successful partial redraw should bring GPU time per update down to ~1–2 ms and should drop that janky count.

## What has been ruled out

1. **Recomposition is already isolated.** `MusicViewModel.position` is its own `StateFlow<Long>`, separate from `uiState`. Leaf composables take `position: () -> Long`, and lyric line indices use `derivedStateOf`. A position update recomposes only `PlayerProgress` and `LyricTicker`. The remaining cost is draw and render, not composition.
2. **`graphicsLayer()` on the changing parts doesn't confine damage.** It is shipped on the `PlayerProgress` root and the `LyricTicker` row. It saves re-recording the rest of the tree, but the window is still damaged in full.
3. **Hosting the seek bar in a nested `ComposeView` inside `AndroidView` doesn't help either.** Tried and reverted. Compose draws an embedded View inside its own draw tree, so the child's invalidate still invalidates and re-records the root.
4. **The device supports partial updates.** With the dirty-region overlay on, the system stopwatch app damages only its digits. Our app damages the whole window on the player and on Settings, even with glass forced solid.

## Working theory

All Compose content lives in one `AndroidComposeView`. Any layer invalidation calls `invalidate()` on that View. That View's RenderNode is then re-recorded, and HWUI's `DamageAccumulator` treats a re-recorded RenderNode as damaged over its full bounds. Classic Views avoid this because each widget has its own RenderNode, and a parent isn't re-recorded when a child changes.

This theory isn't proven. Confirm it before building on it. A Perfetto trace of one update should show whether the root RenderNode's display list is re-recorded and how the damage rect is computed.

## Ideas not yet tried

- **Sibling View overlay.** Put the seek strip in a separate View that is a sibling of the main `ComposeView` in the Activity's content `FrameLayout`, not a child of it. Position it every layout pass from the bounds of a placeholder in the Compose tree (`onGloballyPositioned`). This is the most likely to work and the most fragile:
  - insets, the player open/close transition and rotation all have to track the placeholder;
  - the glass doesn't sample this View, because it isn't in the haze source.
- **Check Compose/HWUI internals.** Look for a flag or newer behaviour that avoids re-recording the root when only child layers changed. Search the Compose issue tracker for partial damage or root re-record. We are on Compose 1.12.
- **`SurfaceView` / `TextureView` for the strip.** Gives true isolation, but the strip would need drawing by hand. Probably too much for a seek bar.
- **Accept full damage and make each frame cheaper.** Two options: cache static glass as bitmaps while nothing moves behind it, or skip glass refraction passes when their source hasn't changed. This may beat partial damage for less work.

## How to reproduce and measure

Requires a phone on adb and a release build. CLI release builds need `--no-configuration-cache`, or the APK can come out marked `testOnly`; check with `aapt2 dump xmltree <apk> --file AndroidManifest.xml | grep testOnly`.

```sh
./gradlew :app:assembleRelease --no-configuration-cache
adb install -r app/build/outputs/apk/release/app-release.apk
# Start a song, open the full player.
```

**Show damaged regions** (each redrawn area flashes red; restart the app after toggling):

```sh
adb shell setprop debug.hwui.show_dirty_regions true
adb shell am force-stop com.example.juke   # then reopen the player
# ...look, or capture frames with: adb exec-out screencap -p > f.png
adb shell setprop debug.hwui.show_dirty_regions false   # turn it off when done
```

Captured screenshots include the red tint, so a script can find the damaged bounding box by counting strongly red pixels. Take 10+ captures in a row: many land between updates and show no red.

**Per-phase frame cost** (20 s window; framestats holds only the last 120 frames):

```sh
adb shell dumpsys gfxinfo com.example.juke reset; sleep 20
adb shell dumpsys gfxinfo com.example.juke | grep -E 'Total frames|Janky frames:|50th perc|90th perc|50th gpu|90th gpu'
adb shell dumpsys gfxinfo com.example.juke framestats | awk -F, '
/^Flags,/ {for(i=1;i<=NF;i++) c[$i]=i; hdr=1; next}
/---PROFILEDATA---/ {hdr=0}
hdr && NF>20 && $c["AnimationStart"]>0 {n++;
 a+=($c["PerformTraversalsStart"]-$c["HandleInputStart"]);
 t+=($c["SyncQueued"]-$c["PerformTraversalsStart"]);
 r+=($c["SwapBuffers"]-$c["SyncStart"]);
 g+=($c["GpuCompleted"]-$c["SwapBuffers"]); }
END { if(n) printf "frames=%d anim=%.1fms trav=%.1fms rt=%.1fms gpu=%.1fms\n", n, a/n/1e6, t/n/1e6, r/n/1e6, g/n/1e6 }'
```

`anim` is Compose recomposition, `trav` is measure/layout/record, `rt` is RenderThread, `gpu` is GPU time. Compare runs on the same track: a word-synced lyrics track redraws every frame for the word fill, which makes its numbers incomparable with a plain track.

## Where the code is

- [PlayerProgress.kt](../../app/src/main/java/com/example/juke/ui/components/player/PlayerProgress.kt): seek bar and time labels; reads `position()`.
- [PlayerStage.kt](../../app/src/main/java/com/example/juke/ui/screens/PlayerStage.kt): `LyricTicker`, the 1–2 line rolling lyrics under the artwork.
- [PlayerScreen.kt](../../app/src/main/java/com/example/juke/ui/screens/PlayerScreen.kt):
  - `positionNow` lambda wiring;
  - the 300 ms poll while playing;
  - `ArtBackdrop`, the haze source for player glass.
- [KaraokeText.kt](../../app/src/main/java/com/example/juke/ui/components/player/KaraokeText.kt): `KaraokeLine` and `rememberFrameClock`.
- [Glass.kt](../../app/src/main/java/com/example/juke/ui/theme/Glass.kt): `liquidGlass`, `glassIsLive`. The desktop app has its own copy.
- [PlayerTransition.kt](../../app/src/main/java/com/example/juke/ui/components/PlayerTransition.kt): `hiddenUnderPlayer()` stops the screen under the open player from drawing.

## Constraints

- Lowering the poll rate isn't an option: the owner rejected it because the seek bar moves visibly rough.
- Run `:app:testDebugUnitTest` for Android-only changes.
- Test on a release build. Debug builds are much slower and give misleading frame numbers.
