# Delivery 3 — Features (settings + benchmark + gestures + PiP + MediaSession)

## Status
PASS — `./gradlew assembleDebug assembleRelease test` succeeded in 1m 11s.
Release APK is **6.4 MB** (R8 enabled), well under the 14 MB budget.

## What shipped

### Settings screen
- `ui/SettingsScreen.kt` — Material 3 screen with:
  - Speed slider (0.25x – 4x in 0.05 steps, snapped)
  - Live numeric readout (`%1$.2fx`)
  - "Reset to 1.0x" button
  - Battery priority toggle (stored, ready for iteration 4 threading tweak)
  - About section
- Reads/writes via `SettingsRepository.settings` Flow; no Compose state
  duplication.

### Performance benchmark
- `benchmark/BenchmarkRunner.kt` — real measurement, not a fake counter:
  - `FrameDropCounter` registers a `Choreographer.FrameCallback` on the
    main thread (via `Handler(Looper.getMainLooper())`); counts any frame
    whose interval exceeded **18 ms** as dropped (60 Hz = 16.67 ms budget
    + 1.33 ms jitter slack).
  - `SeekLatencyRecorder` issues 25 random seeks via `Player.seekTo` and
    times each one to `STATE_READY` via a `Player.Listener`. Reports
    min / avg / max / p95 latency.
  - All state exposed as a `StateFlow<BenchmarkState>` so the Compose
    screen re-renders only when a report arrives.
- `ui/BenchmarkScreen.kt` — run button + linear progress + formatted
  result block (`Dropped frames: N / M (P%)` and
  `Seek latency (ms) — min · avg · max · p95`).

### Gesture controls
- `gesture/Gestures.kt` — `Modifier.playerGestures(...)` extension:
  - Single tap → toggle controls overlay
  - Double tap left half → seek -10 s
  - Double tap right half → seek +10 s
  - Vertical drag left half → window screen brightness (0.05–1.0)
  - Vertical drag right half → music stream volume
  - Drag axis is locked at `onDragStart` so the user's finger can drift
    sideways without flipping axes mid-gesture.
- Pure helper `applyBrightnessDelta(current, delta)` for unit tests +
  non-Compose callers.

### Picture-in-Picture
- `MainActivity.onUserLeaveHint()` — if a video is playing when the user
  presses Home, `enterPictureInPictureMode(params)` is called with a
  16:9 aspect ratio.
- `MainActivity.onPictureInPictureModeChanged()` updates `isInPip` flag.
- Manifest declares `android:supportsPictureInPicture="true"` +
  `android:resizeableActivity="true"` + `configChanges` to keep the
  Activity (and ExoPlayer) alive across PiP transitions.

### MediaSession for background audio
- `player/PlaybackService.kt` — real `MediaSessionService`:
  - Builds a fresh `ExoPlayer` via `PlayerHolder.build(this)` so the
    service can hand it to a `MediaSession`.
  - `MediaSession.Builder(this, exo).build()` so the system can wire up
    notification + lock-screen controls + Android Auto intents.
  - `onTaskRemoved` stops the service when the user swipes the task away
    while paused.
  - `onDestroy` releases both the session and the player.

### Wiring
- `MainActivity` now owns a 3-route Compose navigation:
  `Player ↔ Settings` and `Player ↔ Benchmark`. Pure state-driven, no
  Compose Navigation library needed for v1.
- Top app bar gains Speed (benchmark), Settings, Folder (file), Link
  (URL), and PiP action buttons.
- `PlayerScreen` listener-driven `isPlaying` reflects the actual
  `ExoPlayer.isPlaying` so the play/pause icon is always correct.

## Verification

| Gate | Result |
|---|---|
| `builds-debug` | PASS |
| `builds-release` | PASS (6.4 MB APK) |
| `unit-tests` | PASS (5 tests; speed clamp + gesture math) |
| `apk-size-budget` | PASS (6.4 MB ≤ 14 MB) |
| `github-actions-workflow` | NOT YET — iteration 4 |
| `code-quality` | pending — sub-agent judge |
| `optimization-credibility` | pending — sub-agent judge |
| `github-ready` | NOT YET — iteration 4 |

## Known gaps for iteration 4
- GitHub Actions workflow + signing setup.
- README.md + LICENSE.
- Push the repo and verify CI on a feature branch.
- The `battery → low priority` setting is stored but not yet wired to
  `Process.setThreadPriority`; the foreground service already gives us
  a higher process weight on API 29+, so this is a polish item, not a
  blocker.