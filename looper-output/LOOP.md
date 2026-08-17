# cherry-pro-minimax-player

Build a public Android video player app with Media3/ExoPlayer + Compose, push to GitHub with auto-release via Actions, verified by frame/seek perf benchmarks and strict code-review gates.

## Goal

Ship cherry-pro-minimax-player — a public Android app (Kotlin + Jetpack Compose + Media3/ExoPlayer 1.4+, min API 29) that plays local MP4/M4V/MOV and HTTP/HLS/DASH stream URLs, with global playback speed 0.25x-4x (configurable in settings, applies to every video), Picture-in-Picture, background audio + MediaSession notification, and gesture controls (volume, brightness, double-tap seek). Includes an in-app perf benchmark that measures dropped-frame count and seek-to-keyframe latency. The source is pushed to a new public GitHub repo; GitHub Actions builds a release APK on every merge to main. Optimize for zero-lag playback and a small APK.

## Definition of Done

- Public GitHub repo cherry-pro-minimax-player exists with all source. - gradle assembleDebug + assembleRelease both succeed (CI-verified). - Unit tests pass (settings persistence, speed clamping, gesture math). - In-app benchmark screen reports frame-drop count + seek latency. - Release APK is attached to a GitHub Release on every merge to main. - APK size budget (e.g. < 14 MB) is enforced in CI. - README.md in repo explains install + features + perf numbers.

## Verification

- `builds-debug` (programmatic)
- `builds-release` (programmatic)
- `unit-tests` (programmatic)
- `apk-size-budget` (programmatic)
- `github-actions-workflow` (programmatic)
- `code-quality` (judge)
- `optimization-credibility` (judge)
- `github-ready` (judge)

## Council

- `judge-1`: judge via in-session-agent (minimax-m3)

## Gates

- Plan gate: revise_until_clean
- Delivery gate: revise_until_clean

## Loop Control

- Max iterations: 8
- Budget: `{"tokens": 5000000, "wall_clock_min": 90}`
- No-progress: `{"action": "stop", "max_stalled_iterations": 2, "signals": ["same blocking issue repeats", "delivery artifact has no material change", "verifier output is unchanged"]}`

## Execution Boundary

- Mode: `in_session`
- Isolation: `current_workspace`
- Side effects: `{"duplicate_action_check": true, "notes": "Side effects: git init + push to a new public GitHub repo, GitHub Actions workflow creation, possibly `android` CLI calls for project scaffold, gradle builds. Idempotent where possible (gradle is, git push --force-with-lease is gated by duplicate_action_check).\n", "requires_approval": false}`

## Observability

- State file: `state.json`
- Run log: `run-log.md`
- Checkpoint granularity: `gate`

## Flow Preview

```text
+--------------------------------+
| 1. Goal + context              |
| read sources                   |
+--------------------------------+
               |
               v
+--------------------------------+
| 2. Draft plan.md               |
| state -> state.json            |
+--------------------------------+
               |
               v
+--------------------------------+
| 3. Plan gate                   |
| verdict: judge-1               |
+--------------------------------+
               | needs work -> revise <= 3 -> step 2
               | pass
               v
+--------------------------------+
| 4. Write delivery-N.md         |
| log -> run-log.md              |
+--------------------------------+
               |
               v
+--------------------------------+
| 5. Delivery gate               |
| verdict: judge-1               |
+--------------------------------+
               | needs work -> revise <= 4 -> step 4
               | pass
               v
+--------------------------------+
| 6. Final output                |
| all gates clean                |
+--------------------------------+

Stops: pass gates | max 8 iterations | no progress x2 | budget 90m, 5000000 tokens
```
