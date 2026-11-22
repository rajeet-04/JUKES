# Handoff: partial screen redraws on the player

Status: experiment done 2026-11-23. Damage can be confined to the seek strip, but frame cost didn't drop. Not shipped; see "Experiment result".

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

### What the phone trace established

On the vivo I2220 (API 36, Skia GL, 60 Hz), the full player was already open, playback was running and the dirty-region overlay was off. Two 12-second Perfetto captures gave these results.

| Track and player state | HWUI draws | Draws with a 1080 × 2400 dirty rectangle | `AndroidOwner:draw` calls |
|---|---:|---:|---:|
| Until Always, no synced lyrics | 40 | 40 | 40 |
| Feels Like Home, synced lyric ticker visible | 721 | 721 | 720 |

On Until Always, the median interval between Compose owner draws was 299.7 ms. Every `AndroidOwner:draw` was directly inside `Record View#draw()`. Both HWUI's `prepare` and `Drawing` slices reported the full window each time. This establishes that the Compose host records drawing commands at the seek-update cadence and full-window damage reaches HWUI. The trace does not identify the first RenderNode that expanded the rectangle or establish that each glass shader ran again.

A separate 20-second sample of Until Always, taken after tracing stopped, added 67 frames to the process counters. HWUI counted all 67 as janky. Median intended-vsync to frame-completed time was 35.3 ms. The earlier 3.2.1 benchmark used different playback conditions, so use a fresh same-track baseline for any patch.

### First experiment

Compose UI 1.12.1 has two relevant paths in `GraphicsLayerOwnerLayer.android.kt`. Changing a layer's drawing commands calls `ownerView.invalidate()`. Updating an existing layer's transform calls `triggerRepaint()`, which uses `onDescendantInvalidated` on this phone. Moving a pre-recorded thumb can therefore exercise a different path from wrapping the current Material slider in another layer.

For a release A/B on a plain track, keep the 300 ms poll. In the test variant, draw the thumb and fill once at fixed sizes, then read position only in layer-property lambdas to move them inside a bounded strip. Derive the elapsed and remaining label strings independently so their text changes only when their displayed second changes. Compare subsecond updates separately from label changes. The first success criterion is a smaller HWUI dirty rectangle between label changes, followed by a reduction in frame cost. Preserve drag, tap-to-seek, accessibility actions, RTL direction and the thumb shadow before shipping. This experiment tests the ordinary player only; the karaoke sweep changes drawing commands independently.

The capture command on this device is `adb shell -T 'perfetto -o - -t 12s -b 32mb -a com.example.juke gfx view 2>/dev/null' > player.pftrace`. In Perfetto, select the app's UI thread for `Record View#draw()` and `AndroidOwner:draw`, and its RenderThread for `prepare->` and `Drawing`. These last two slice names include the rectangle on this vivo build. Keep tracing off for the separate frame-time comparison.

### Experiment result

The experiment is in the working tree version of `PlayerProgress.kt` (uncommitted, about 320 lines). It does three things:

- `LayeredSeekSlider`: fill and thumb drawn once; position is read only in `graphicsLayer` lambdas (`translationX`). The fill slides inside the clipped track.
- `boundSeekDamage()`: records the whole strip into an `android.graphics.RenderNode` with `clipToBounds = true` and draws it with `drawRenderNode`. HWUI on API 29+ stops descendant damage at such a node. Compose 1.12 maps even a rectangular `graphicsLayer` clip to `clipToOutline`, which doesn't do this.
- `ClockDigit`: each time digit is a pre-drawn 0–9 column moved behind a clip, so the clocks show exact time without new text layout.

Steps and what each showed (Perfetto, Until Always):

| Variant | Result |
|---|---|
| Layered slider only | 16 ticks skipped `AndroidOwner:draw`, but all 41 frames still had full-window damage |
| Labels frozen (diagnostic) | All 40 ticks skipped host recording; all 40 still full-window |
| + `boundSeekDamage()` | Ticks without a label change confined to the strip |
| + rolling digits (final) | All 39 GPU draws confined to 966 × 166 (6% of the screen) |

Drag, tap-to-seek, the accessibility set-progress action, the minute rollover and RTL (app locale `ar`) passed. The accessibility value follows position changes.

**Frame cost did not improve.** Alternating release runs on one track (Maahiya), no tracing, thermal status 0, 20 s each:

| Run | Build | Janky / 67 | Frame p50 | RenderThread p50 | GPU p50 |
|---|---|---:|---:|---:|---:|
| 1 | stock 3.2.1 | 60 | 28.2 ms | 5.3 ms | 14.6 ms |
| 2 | experiment | 55 | 21.9 ms | 4.9 ms | 13.1 ms |
| 3 | stock 3.2.1 | 64 | 22.0 ms | 4.9 ms | 12.7 ms |
| 4 | experiment | 59 | 22.5 ms | 5.2 ms | 13.6 ms |

Run-to-run noise (runs 1 and 3, same build) is larger than any difference between builds. An earlier stock-then-experiment pair showed 32 → 22 ms frame p50; the alternating runs suggest that was ordering noise.

So GPU time per update stays around 13–14 ms even when only 6% of the window is damaged. The cost is not proportional to the damaged area. Next suspects, untested:

- Offscreen layers (Haze glass `RenderEffect`s, layers with alpha) may be re-rendered on every frame regardless of the damage rect. Check Perfetto for `RenderNode` layer updates and Haze slices on a confined frame.
- The GPU may clock down between 300 ms updates, so even a small draw takes ~13 ms of wall time. The GPU histogram measures elapsed time, not work.

Recommendation: don't ship the experiment as is. It replaces the Material slider with ~320 lines of custom input, semantics and drawing for no measured gain. Keep it as a reference if the glass/offscreen question is answered and the gain appears.

Scripts and traces from these runs are in `/private/tmp/jukes-seek-ab/` (temporary): `ab.sh` (alternating runs), `summarize.py`, `frames.sql` (trace processor query), and the `probe/` accessibility/input checker.

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
