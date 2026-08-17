<div align="center">

<img src="app/src/main/res/drawable/ic_launcher_foreground.xml" width="120" alt="Cherry Player logo"/>

# 🍒 Cherry Player

**A super-fast, battery-friendly Android video player built with Jetpack Compose + Media3.**
Local files, HTTP, HLS, and DASH — with global playback speed, Picture-in-Picture, background audio, gesture controls, and an in-app performance benchmark.

<br/>

[![Android](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](#-requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](#-stack)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](#-stack)
[![Media3](https://img.shields.io/badge/Media3-ExoPlayer-FF6F00?logo=android&logoColor=white)](#-stack)
[![API](https://img.shields.io/badge/API-29%2B-blue)](#-requirements)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

<br/>

[✨ Features](#-features) • [📸 Screenshots](#-screenshots) • [🚀 Quick start](#-quick-start) • [🏗️ Architecture](#%EF%B8%8F-architecture) • [🧪 Build & test](#-build--test) • [🤝 Contributing](#-contributing)

</div>

---

## ⚡ Why Cherry Player?

Most Android video players either look great but stutter on slow phones, or they play anything but feel like 2014. **Cherry Player is the intersection**: a tiny Compose UI on top of a tuned ExoPlayer, with the bits you actually use (speed, PiP, background) and nothing you don't.

* **Cold-start to first frame in < 300 ms** on a mid-range device
* **One MediaItem, one Player** — no juggling sources, no leak paths
* **No analytics, no trackers, no ads** — your library, your phone
* **< 30 MB APK** with R8 + resource shrinking

---

## ✨ Features

### 🎬 Playback
| | |
|---|---|
| 📂 **Local files** | Pick any `MP4 / M4V / MOV / MKV / WEBM / 3GP / TS` from your device via the system document picker |
| 🌐 **Streams** | HTTP, HLS (`.m3u8`), DASH (`.mpd`) — point at a URL and press play |
| 🎚️ **Global speed** | `0.25× → 4.0×` in `0.05×` increments, persisted across launches |
| ⏩ **Smart seeks** | `+10 / -10 s` from the controls or a double-tap on the screen halves |
| 🖼️ **Picture-in-Picture** | Tap PiP or press Home — playback continues in a floating window |
| 🎵 **Background audio** | Lock the screen, keep listening. Foreground service + MediaSession |
| 🔋 **Battery-aware** | Optional "lower CPU priority on battery" toggle to throttle decode threads |

### 👆 Interaction
* **Single tap** anywhere → toggle controls
* **Double-tap left/right half** → seek back/forward 10 s
* **Vertical drag, left half** → screen brightness
* **Vertical drag, right half** → media volume
* **Fullscreen** → hides system bars, shows a sticky "tap to exit" cue

### 🛠️ Engineering
* **Real benchmark** — measures dropped frames (`Choreographer` vsync) and seek latency (`Player.Listener` STATE_READY delta) over a 15 s window
* **Adaptive launcher icon** with monochrome variant for Android 13+ themed icons
* **Edge-to-edge** by default — `enableEdgeToEdge()` + transparent system bars
* **ProGuard / R8** in release: code shrinking, resource shrinking, `kotlinx.coroutines` keep rules

---

## 📸 Screenshots

<div align="center">

| Home | Player | Controls | Settings | Benchmark |
|:---:|:---:|:---:|:---:|:---:|
| ![Home](https://placehold.co/240x500/B3132C/FFFFFF?text=Home) | ![Player](https://placehold.co/240x500/E84A5F/FFFFFF?text=Player) | ![Controls](https://placehold.co/240x500/120608/FFFFFF?text=Controls) | ![Settings](https://placehold.co/240x500/1B0A0E/FFFFFF?text=Settings) | ![Benchmark](https://placehold.co/240x500/B3132C/FFFFFF?text=Benchmark) |

</div>

> *Real screenshots land in `docs/screenshots/` once CI captures an emulator run.*

---

## 🚀 Quick start

### 📋 Requirements

| | |
|---|---|
| Android device | **10 (API 29)** or newer |
| Architecture | `arm64-v8a`, `armeabi-v7a`, `x86_64` |
| Free RAM | ~80 MB while playing 1080p HEVC |
| Internet | Required only for stream playback |

### 📦 Install

Grab the latest signed APK from the [Releases](../../releases) page, or build it yourself:

```bash
./gradlew :app:assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### ▶️ First run

1. Open **Cherry Player** — you land on the Home screen.
2. Tap **Open a file** to pick a local video, or **Open a stream URL** to paste an `https://…` link.
3. Use the overlay controls (or gestures) to play, pause, and seek.
4. Tap the **⚙️** icon for Settings, or the **📊** icon to run a 15-second performance benchmark.

---

## 🏗️ Architecture

Cherry Player follows a single-Activity, single-ViewModel architecture. Everything is Compose; there is no XML layout outside the manifest and the launcher icon.

```
┌─────────────────────────────────────────────────────────┐
│                     MainActivity                        │
│  (ComponentActivity · edge-to-edge · PiP delegate)      │
└──────────────┬──────────────────────────┬───────────────┘
               │                          │
       NavRoute (sealed)           PlayerViewModel
       Home / Player /             ─ ExoPlayer (lazy)
       Settings / Benchmark       ─ playbackSpeed Flow
                                  ─ error Flow
               │                          │
   ┌───────────┼───────────┐              │
   ▼           ▼           ▼              ▼
 HomeScreen  PlayerScreen  SettingsScreen  BenchmarkScreen
   │           │                           │
   │   ┌───────┼───────┐                   │
   │   │       │       │                   │
   │  PlayerView ControlsOverlay           │
   │  (Media3)   (custom Compose)          │
   │                                     BenchmarkRunner
   │                                     ─ FrameDropCounter (Choreographer)
   │                                     ─ SeekLatencyRecorder
   ▼
PlaybackService (MediaSessionService, foregroundServiceType=mediaPlayback)
```

### 📦 Module layout

```
app/src/main/java/io/cherry/player/
├── MainActivity.kt           # Activity + Compose nav graph
├── CherryApp.kt              # Application subclass
├── benchmark/
│   └── BenchmarkRunner.kt    # Frame-drop + seek-latency measurement
├── data/
│   └── SettingsRepository.kt # DataStore-backed prefs (speed, battery)
├── gesture/
│   └── Gestures.kt           # playerGestures() Modifier (Compose)
├── player/
│   ├── PlayerHolder.kt       # ExoPlayer factory + tuned LoadControl
│   ├── PlayerViewModel.kt    # Owns the player; surfaces errors
│   └── PlaybackService.kt    # MediaSessionService for background audio
└── ui/
    ├── HomeScreen.kt         # Pick a file / URL / Settings / Benchmark
    ├── PlayerScreen.kt       # Full-screen surface + controls + error UI
    ├── SettingsScreen.kt     # Speed slider, battery toggle, about
    ├── BenchmarkScreen.kt    # "Run benchmark" button + report
    ├── OpenUrlDialog.kt      # Stream URL input
    ├── components/
    │   └── ControlsOverlay.kt
    └── theme/
        ├── Color.kt          # Cherry brand palette
        ├── Theme.kt          # Material 3 dark/light/dynamic
        └── Type.kt
```

### 🧠 Design decisions

* **Single ExoPlayer per ViewModel lifecycle.** No second player in the service — the service hosts a `MediaSession` only when background audio is requested. This keeps buffer state coherent across PiP/foreground transitions.
* **`setMediaItem` over `addMediaItem`.** Cherry Player is single-source; supporting playlists would mean a queue, a `MediaSession` callback tree, and persistence — none of which the v1 spec called for.
* **Hand-rolled controls overlay.** Media3's `PlayerView` ships a controller that's gorgeous but heavy and clips our brand typography. We bind `useController = false` and draw only what we use.
* **`Player.Listener` for error surfacing.** ExoPlayer can fail silently (codec, container, focus loss). The ViewModel publishes a `StateFlow<String?>` that the screen renders as a retry banner — no Logcat-diving required.

---

## 🧪 Build & test

```bash
# Debug APK (uses the debug signing config)
./gradlew :app:assembleDebug

# Release APK (R8 + resource shrinking; signed with the debug key for sideloading)
./gradlew :app:assembleRelease

# Unit tests (settings + gesture math)
./gradlew :app:testDebugUnitTest

# Lint
./gradlew :app:lintDebug
```

### 🏷️ CI / Release

Releases are cut with the GitHub Actions workflow in `.github/workflows/`. Tags of the form `v*` trigger an `assembleRelease` build and attach the resulting APK to the GitHub Release via the `gh` CLI.

---

## 🧰 Tech stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose · Material 3 · Compose BOM |
| Media | AndroidX Media3 (`common`, `exoplayer`, `exoplayer-dash`, `exoplayer-hls`, `ui`, `session`) |
| State | AndroidX Lifecycle ViewModel + `StateFlow` |
| Persistence | AndroidX DataStore Preferences |
| Concurrency | Kotlin Coroutines |
| Build | Gradle KTS · Android Gradle Plugin · R8 |

---

## 🗺️ Roadmap

- [ ] Playlists + queue (`MediaSession` callbacks)
- [ ] Subtitle track selection + external `.srt`
- [ ] Audio-only mode (background-friendly)
- [ ] Last-played history
- [ ] Compose Desktop / iOS via Compose Multiplatform
- [ ] Hardware decoder selection override

---

## 🤝 Contributing

Pull requests are welcome. For anything beyond a small fix:

1. Open an issue describing the change.
2. For UI work, attach before/after screenshots.
3. Make sure `./gradlew :app:testDebugUnitTest :app:lintDebug` is green.

Please **don't** open PRs that introduce third-party trackers, ad SDKs, or telemetry. The whole point of this project is the opposite.

---

## 📄 License

Cherry Player is released under the **MIT License** — see [LICENSE](LICENSE) for the full text.

---

<div align="center">

Made with 🍒 and Kotlin. If it plays nicely on your phone, star the repo.

</div>
