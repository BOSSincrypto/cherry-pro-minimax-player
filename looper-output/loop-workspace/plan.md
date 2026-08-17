# Plan: cherry-pro-minimax-player

## Outcome
Ship a public Android video player app at
`github.com/BOSSincrypto/cherry-pro-minimax-player` with a working release
APK produced by GitHub Actions on every merge to `main`. The app must
play local MP4/M4V/MOV and HTTP/HLS/DASH URLs, honor a global playback
speed setting (0.25x-4x), support PiP, background audio, gestures,
and an in-app perf benchmark.

## Scope

### In scope (v1)
- Kotlin + Jetpack Compose + Material 3
- Media3 ExoPlayer 1.4+ (`androidx.media3:media3-exoplayer`,
  `media3-ui`, `media3-session`, `media3-common`, `media3-datasource`)
- min SDK 29, target SDK 35, compile SDK 35
- Single-module app (no separate library module) for fastest cold build
- Playback sources:
  - Local file via SAF `ACTION_OPEN_DOCUMENT` (returns content:// URI)
  - HTTP/HLS/DASH URLs typed into an in-app dialog
- Global playback speed persisted via DataStore Preferences; applied
  on every player creation via `PlaybackParameters(speed)`; clamped to
  0.25-4.0 in 0.05 increments
- Picture-in-Picture via `PlayerView.setUseController(false)` +
  `enterPictureInPictureMode(...)` triggered on user home-press or
  explicit button
- Background audio via `MediaSessionService` + `MediaSession` so
  playback continues when the activity stops; `MediaSession` shows a
  notification with play/pause/seek controls
- Gesture controls:
  - Double-tap right edge: seek +10s
  - Double-tap left edge: seek -10s
  - Vertical drag on right half: volume
  - Vertical drag on left half: brightness
  - Single tap: toggle controls overlay
- In-app perf benchmark (hidden — opened by long-press on the player
  title or via a deep link `cherryplayer://benchmark`):
  - Records frame-drop count via `Choreographer.FrameCallback`
    (compares `frameTimeNanos` against expected delta)
  - Records seek-to-keyframe latency by issuing N random seeks and
    timing `PlayerEventListener.onPlaybackStateChanged(STATE_READY)`
  - Reports min / avg / max / p95

### Out of scope (v1)
- Subtitles (.srt / .vtt / embedded) — not requested
- Casting (Chromecast / DLNA) — not requested
- Streaming services / DRM — not requested
- Custom themes, accent color pickers — Material 3 dynamic color only
- Multi-window / tablet layouts — phone portrait only
- Internalization beyond English

## Architecture

```
app/
  build.gradle.kts                AGP + Kotlin + Compose + Media3 + DataStore
  src/main/
    AndroidManifest.xml           declares MainActivity + PlaybackService
    java/io/cherry/player/
      MainActivity.kt             hosts Compose nav graph
      CherryApp.kt                Application class (not strictly needed)
      player/
        PlayerScreen.kt           Compose surface + gestures + controls
        PlayerViewModel.kt        owns ExoPlayer lifecycle, applies speed
        PlaybackService.kt        MediaSessionService for background audio
        PlaybackController.kt     seek/play/pause wrapper, used by VM
      ui/
        theme/Theme.kt            Material 3 theme (dynamic color on API 31+)
        theme/Color.kt
        theme/Type.kt
        SettingsScreen.kt         speed slider + reset + open benchmark
        BenchmarkScreen.kt        perf benchmark UI + run button
        OpenSourceDialog.kt       URL input dialog
        components/ControlsOverlay.kt
      data/
        SettingsRepository.kt     DataStore wrapper, suspend get/set
        SpeedPresets.kt
      gesture/
        Gestures.kt               pointerInput + detectTapGestures + drag
        GestureState.kt           volume / brightness mutable state
      benchmark/
        Benchmark.kt              FrameDropCounter + SeekLatencyRecorder
        BenchmarkReport.kt
    res/
      drawable/ic_launcher_*      adaptive icon
      values/strings.xml          app name, etc.
      values/themes.xml           AppTheme parent
      mipmap-anydpi-v26/          adaptive icon XML
      xml/data_extraction_rules.xml
      xml/backup_rules.xml
    test/                         JUnit + Turbine for VM + gesture math
  proguard-rules.pro              Media3 keep rules
build.gradle.kts                  root: AGP plugin versions
settings.gradle.kts               pluginManagement + dependencyResolutionManagement
gradle/libs.versions.toml         version catalog (single source of truth)
gradle.properties                 JVM args, AndroidX flags, R8
scripts/
  check-apk-size.sh               fails CI if APK > 14 MB
  check-workflow.sh               asserts .github/workflows/release.yml exists
.github/workflows/
  release.yml                     on push to main: assemble + test + release
  pr.yml                          on PR: assembleDebug + test
gradle/wrapper/                   gradle wrapper (8.13)
gradlew / gradlew.bat             wrapper scripts
README.md                         install + features + perf numbers
LICENSE                           MIT
.gitignore                        Android standard + .gradle/ + build/
```

### Why single-module?
A multi-module split (`:core`, `:player`, `:app`) saves incremental build
time on large codebases. For v1 this app is < 30 Kotlin files; the split
adds configuration overhead without measurable benefit. The dependency
catalog `libs.versions.toml` keeps Media3 versions honest.

### Why SurfaceView (not TextureView)?
TextureView allocates per-frame and goes through the GPU compositor —
fine for animations but adds 5-10 ms of latency on lower-end devices.
SurfaceView gives MediaCodec a direct path to the display surface, which
is exactly what zero-copy hardware decode wants. Media3's `PlayerView`
defaults to SurfaceView; we keep the default.

## Performance strategy

| Concern | Mitigation |
|---|---|
| Cold-start latency | ExoPlayer with `DefaultLoadControl` tuned for short buffers (`bufferForPlaybackMs=1500`, `bufferForPlaybackAfterRebufferMs=3000`) |
| Rebuffering on streams | `DefaultHttpDataSource` with `connectTimeoutMs=8000`, `readTimeoutMs=8000`; allow `keepBeforeMs=2000` |
| Decode jank | Hardware decoder (default), SurfaceView, no `setUseController(false)` flicker |
| UI thread stalls | `withContext(Dispatchers.IO)` for any IO; `remember { ... }` instead of `remember { mutableStateOf(...) }` inside hot recomposition; no logs in release |
| Recomposition | `derivedStateOf` for control visibility, `key()` blocks where the player view should reset, `Modifier.composed` only for gesture detectors |
| Binary size | Media3 only (no FFmpeg), Compose BOM (no Material library extras), R8 with `isMinifyEnabled=true` for release, no appcompat dependency |
| Network | `DefaultHttpDataSource.Factory(UserAgent)`, single connection per player |
| Memory | `Player.release()` in `onDispose()`, no capture of `Player` in lambdas |

### Settings speed clamping
```kotlin
fun clampSpeed(raw: Float): Float =
    raw.coerceIn(0.25f, 4.0f).let { Math.round(it * 20f) / 20f }
```
The 0.05 step comes from `Math.round(it * 20f) / 20f`. Unit-tested.

## CI/CD

### `.github/workflows/pr.yml`
- Triggers: pull_request
- Runs: `./gradlew assembleDebug test`
- Uses: `actions/checkout@v4`, `actions/setup-java@v4` (temurin 17),
  `./gradlew` with `--no-daemon`

### `.github/workflows/release.yml`
- Triggers: push to `main`
- Runs:
  1. `./gradlew assembleRelease test`
  2. `scripts/check-apk-size.sh` (fails if release APK > 14 MB)
  3. Generates a release keystore (committed under `keystore/` is .gitignored;
     CI uses `KEYSTORE_BASE64` from repo secrets; if absent, falls back
     to debug signing — documented in README)
  4. Uploads APK as a GitHub Release asset tagged `v<short-sha>-<unix-ts>`
     via `softprops/action-gh-release@v2`

### `scripts/check-apk-size.sh`
```bash
#!/usr/bin/env bash
set -euo pipefail
APK="$(find app/build/outputs/apk/release -name '*.apk' | head -n1)"
[ -z "$APK" ] && { echo "no release APK found"; exit 1; }
SIZE_MB=$(du -m "$APK" | cut -f1)
echo "release APK size: ${SIZE_MB} MB"
[ "$SIZE_MB" -le 14 ] || { echo "APK exceeds 14 MB budget"; exit 1; }
```

### `scripts/check-workflow.sh`
Asserts `.github/workflows/release.yml` exists, triggers on push to
`main`, and references `setup-java@v4`. Quick structural guard.

## Verification gates (per loop.yaml)

- `builds-debug` — `./gradlew assembleDebug` returns 0
- `builds-release` — `./gradlew assembleRelease` returns 0
- `unit-tests` — `./gradlew test` returns 0
- `apk-size-budget` — `scripts/check-apk-size.sh` returns 0
- `github-actions-workflow` — `scripts/check-workflow.sh` returns 0
- `code-quality` — judge sub-agent reviews Compose UI, Media3 wiring,
  gesture correctness, ProGuard rules, config-change behavior
- `optimization-credibility` — judge sub-agent checks the perf claims
  are real (LoadControl tuning, no allocations on hot path, R8 on,
  benchmark screen actually measuring)
- `github-ready` — judge sub-agent reviews repo description, README,
  LICENSE, Actions workflow triggers, signing setup, secrets hygiene

## Iteration sequence

1. **Scaffold** — root Gradle, version catalog, `app/` skeleton,
   `MainActivity` with empty Compose `Surface`, manifest, theme,
   proguard rules, gradle wrapper. `assembleDebug` must pass.

2. **Playback core** — `PlayerViewModel`, `PlayerScreen` with
   `AndroidView { PlayerView }`, file picker (SAF) + URL input,
   basic play/pause/seek. `assembleDebug` + unit tests must pass.

3. **Features** — settings screen + DataStore speed persistence,
   PiP wiring, `MediaSessionService` for background audio,
   gesture handlers, benchmark screen. All unit tests cover
   settings + speed clamping + gesture math.

4. **CI/CD + GitHub** — `.github/workflows/{pr,release}.yml`,
   `scripts/check-apk-size.sh`, `scripts/check-workflow.sh`,
   `keystore/` setup, README, LICENSE, `.gitignore`. Push to
   `github.com/BOSSincrypto/cherry-pro-minimax-player` via
   the GitHub MCP. Verify CI on a feature branch first, then
   merge to main and confirm release APK attaches.

## Risk register

| Risk | Mitigation |
|---|---|
| Gradle daemon vs CI | Use `--no-daemon` in CI; local can use daemon |
| Android SDK license | Already accepted (`$ANDROID_HOME/licenses/android-sdk-license` exists) |
| AGP / Gradle / Kotlin / Compose BOM version drift | All in `libs.versions.toml` |
| API 29 vs API 35 PiP differences | Native PiP available from API 26; we only use documented APIs |
| ExoPlayer background restrictions on API 29+ | `MediaSessionService` with `foregroundServiceType="mediaPlayback` in manifest |
| GitHub Actions minutes | Single workflow per push, no matrix, build cache via `actions/cache@v4` |
| Release APK not signing | Fallback to debug signing in CI if no `KEYSTORE_BASE64` secret; document in README |

## Delivery-time requirements (from plan-gate judge)

The plan-gate judge flagged 7 non-blocking gaps; these MUST be addressed
during iteration 2/3 delivery, not left as TODO:

1. **`network_security_config.xml`** — required for `http://` stream URLs on
   API 28+. Reference it from the manifest's `android:networkSecurityConfig`.
2. **AudioAttributes + audio focus** — `PlayerViewModel` must build
   `AudioAttributes(C.USAGE_MEDIA, C.CONTENT_TYPE_MOVIE)` and call
   `player.setAudioAttributes(...)` to request focus; Media3 will handle
   transient ducking.
3. **Decode thread priority** — in `PlaybackService`, raise the playback
   thread priority via `Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)`
   inside the service's `onCreate` (it stays scoped to the service process).
4. **PiP wiring is two separate things**: `enterPictureInPictureMode(...)`
   is the Activity call (override `onUserLeaveHint`); `PlayerView`'s
   controller hide is unrelated and only used to clean up the overlay.
5. **PlayerView lifecycle** — on `DisposableEffect.onDispose` of the
   AndroidView wrapper, call `playerView.player = null` (releases the
   surface reference) but DO NOT release the ExoPlayer instance here
   because the ViewModel owns it; release only on Activity finish via
   `PlayerViewModel.onCleared()`.
6. **`foregroundServiceType="mediaPlayback"`** — manifest entry, properly
   quoted (the plan had a typo).
7. **`keepBeforeMs` lives on `DefaultLoadControl.Builder`**, NOT on the
   data source. Don't reuse that property name in `DataSource` config.

## Stop conditions

- All 8 verification criteria green
- Or 8 delivery iterations (cumulative)
- Or same blocker repeats for 2 iterations
- Or 90 min wall-clock budget hit