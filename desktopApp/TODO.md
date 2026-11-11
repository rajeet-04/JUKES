# Desktop TODO: Windows and Linux builds

macOS is the only platform built and tried so far. Do these when building for each OS. Packaging commands run on the target OS (Compose Desktop can't cross-package).

## Both Windows and Linux

- [ ] **First build and smoke test.** Build the release package on that OS, install it, and play a song end to end (search, play, seek, queue, lyrics, restore after restart).
- [ ] **Run the desktop tests** on that OS (`./gradlew :desktopApp:test`). `UiRenderTest`, `PlaybackSmokeTest` and `SeekAccuracyTest` assume JavaFX Media; check they run there.
- [ ] **`juke://` link handler.** The "Open in JUKES" button on shared playlist and Jam pages uses `juke://`. macOS registers it through `Info.plist`; the packaging tool has no setting for it here. Plan:
  - Register at first launch, per user, no admin rights.
  - Pass the link to the already-running copy and exit, because the OS starts a second process with the link as an argument. The first copy takes it through the existing `AppLinks.incoming`.
  - **Windows:** write `HKCU\Software\Classes\juke` (`URL Protocol`, `shell\open\command` pointing at the installed `.exe "%1"`).
  - **Linux:** write `~/.local/share/applications/juke-url.desktop` with `MimeType=x-scheme-handler/juke` and `Exec=<app> %u`, then run `xdg-mime default juke-url.desktop x-scheme-handler/juke`.
- [ ] **Media keys and the OS "now playing" panel.** `NowPlaying.kt` is macOS only (no-op elsewhere).
  - **Windows:** System Media Transport Controls.
  - **Linux:** an MPRIS2 service over D-Bus.
- [ ] **App icon, window title bar and drag area.** The title bar is hidden and the top bar acts as the drag and double-click-maximize area; check this behaves on Windows and Linux window managers. The Taskbar icon call is already guarded.
- [ ] **JavaFX behaviour.** Re-check seek accuracy and playback-speed handling; the whole-second seek floor and the rate-after-`play()` workarounds were macOS findings.
- [ ] **Size check.** Compare the installer size to macOS (~90 MB DMG) and trim modules or ProGuard rules if needed.
- [ ] **Keyboard shortcuts.** Cmd+K and the Cmd+arrow shortcuts use Ctrl on these systems. Check they don't clash with the OS or window manager.

## Windows only

- [ ] Package with `packageReleaseMsi` (or `packageReleaseExe`) on a Windows machine.
- [ ] Check the installer's start-menu entry, upgrade behaviour and the `.ico` icon.
- [ ] Code signing (an unsigned installer triggers SmartScreen).

## Linux only

- [ ] Package with `packageReleaseDeb` (and `packageReleaseRpm` or an AppImage if wanted).
- [ ] Check the `.desktop` entry and icon in a few desktop environments (GNOME, KDE).
- [ ] Confirm JavaFX Media's native libraries load (GStreamer dependencies on minimal installs).
- [ ] Check HiDPI scaling.

## Known gaps, any OS

- No volume boost above 100% (JavaFX equalizer approximation) and no skipping of leading or trailing silence.
- Search results from the top bar only appear after Enter, not while typing.
- Hover labels for collapsed library items.
- Handoff polls the Worker every few seconds while the app is open; fine for two devices.
