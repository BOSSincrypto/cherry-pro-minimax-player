# Goal summary for cherry-pro-minimax-player

## What
A public Android video player app.

## Stack
- Language: Kotlin
- UI: Jetpack Compose + Material 3
- Player engine: Media3 / ExoPlayer 1.4+
- Min API: 29 (Android 10)
- Source: local MP4/M4V/MOV + HTTP/HLS/DASH URLs
- Build: Gradle (AGP latest stable)
- Target: real Android device or emulator (API 29+)

## Features
- Open local files via SAF (Storage Access Framework) or open HTTP/HLS/DASH URLs.
- Playback speed configurable in Settings, range 0.25x - 4x in 0.05 steps, applies
  to every video, persisted via DataStore.
- Picture-in-Picture on API 29+ (native PiP, Media3 supports it).
- Background audio playback when user leaves the app; MediaSession + notification.
- Gesture controls: double-tap right/left to seek forward/back, vertical drag on
  right side of screen to change volume, vertical drag on left side to change
  brightness.
- In-app perf benchmark screen (hidden, opened by long-press on the title bar):
  reports dropped-frame count (Choreographer) and seek-to-keyframe latency.

## Repo
- Public GitHub repo at github.com/<authenticated user>/cherry-pro-minimax-player.
- Push via the GitHub MCP. Use the user's authenticated GitHub account.

## CI/CD
- GitHub Actions workflow: build assembleDebug + assembleRelease + test on PRs,
  build release APK on push to main, attach the APK to a GitHub Release tagged
  with the short SHA.

## Optimization targets
- SurfaceView (not TextureView) where possible for zero-copy decode.
- Hardware decoder (default for Media3 + H.264/H.265).
- LoadControl tuned for low start-up latency and minimal rebuffering.
- Background thread priority raised for the decode thread, not the UI thread.
- No logs in release builds.
- R8 / ProGuard enabled for release with Media3 keep rules.
- Minimal dependencies: just Media3 + Compose + Coroutines + DataStore.
- No analytics, no crash reporting — keep binary small.

## Done state
- Public repo exists at the chosen URL.
- GitHub Actions status is green on main, a Release artifact is attached.
- All programmatic gates pass (build + test + APK size + workflow check).
- Judge gates pass (code quality + optimization credibility + GitHub-ready).
- README in repo explains install + features + perf numbers from the
  in-app benchmark.