# Run `cherry-pro-minimax-player` In This Session

Use this prompt when the user wants to run the Looper-designed loop in the current LLM session.
This is the default/easy execution path. The Python runner is the advanced path for running later or outside the session.

## Operator Instructions

You are executing a Looper-designed loop in this current session.
Follow the resolved spec below, write handoff files into the workspace, and enforce the caps manually.
Do not use `run-loop.py` unless the user explicitly asks for the advanced external runner.

1. Create the workspace directory if it does not exist.
2. Read the context sources before drafting the plan.
3. Draft `plan.md` in the workspace.
4. Run the plan gate. Apply programmatic checks when available. For judge criteria, use the configured judge only after consent for any non-local egress; otherwise ask the user to approve a human/current-session substitute.
5. Revise until the gate passes or `max_revisions` is reached.
6. Produce `delivery-N.md` in the workspace.
7. Run the delivery gate after each delivery.
8. Stop when all delivery criteria pass, a cap is reached, or the user stops the loop.
9. Keep `state.json` current with status, iteration, last gate, consent, and blockers.
10. Append a compact entry to `run-log.md` after every context read, model call, check, gate verdict, revision, blocker, and stop decision.
11. Compare each blocker against the previous blocker. If the same blocker repeats for the configured no-progress window, stop or ask for the configured human checkpoint instead of revising again.
12. Treat token and USD budgets as operator limits in this session: if exact accounting is unavailable, stop and ask before continuing when the loop appears likely to exceed them.

## Files

- Source spec: `loop.yaml`
- Human summary: `LOOP.md`
- Resolved spec: `loop.resolved.json`
- Workspace: `./loop-workspace`
- State file: `state.json`
- Run log: `run-log.md`

## Goal

Ship cherry-pro-minimax-player — a public Android app (Kotlin + Jetpack Compose + Media3/ExoPlayer 1.4+, min API 29) that plays local MP4/M4V/MOV and HTTP/HLS/DASH stream URLs, with global playback speed 0.25x-4x (configurable in settings, applies to every video), Picture-in-Picture, background audio + MediaSession notification, and gesture controls (volume, brightness, double-tap seek). Includes an in-app perf benchmark that measures dropped-frame count and seek-to-keyframe latency. The source is pushed to a new public GitHub repo; GitHub Actions builds a release APK on every merge to main. Optimize for zero-lag playback and a small APK.

## Definition Of Done

- Public GitHub repo cherry-pro-minimax-player exists with all source. - gradle assembleDebug + assembleRelease both succeed (CI-verified). - Unit tests pass (settings persistence, speed clamping, gesture math). - In-app benchmark screen reports frame-drop count + seek latency. - Release APK is attached to a GitHub Release on every merge to main. - APK size budget (e.g. < 14 MB) is enforced in CI. - README.md in repo explains install + features + perf numbers.

## Context Sources

- Read file `./inputs/goal.md`

## Verification Criteria

- `builds-debug` programmatic: run `["bash", "./gradlew", "assembleDebug"]` and expect `exit_zero`
- `builds-release` programmatic: run `["bash", "./gradlew", "assembleRelease"]` and expect `exit_zero`
- `unit-tests` programmatic: run `["bash", "./gradlew", "test"]` and expect `exit_zero`
- `apk-size-budget` programmatic: run `["bash", "scripts/check-apk-size.sh"]` and expect `exit_zero`
- `github-actions-workflow` programmatic: run `["bash", "scripts/check-workflow.sh"]` and expect `exit_zero`
- `code-quality` judge rubric: Compose UI follows Material 3 guidelines; no deprecated APIs; Media3 wiring is correct (Player, MediaSession, PiP); gesture handlers don't fight scroll; no allocation in hot paths; build cache and ProGuard rules are sane. App survives configuration changes without re-initializing ExoPlayer. Surface is SurfaceView (not TextureView) for zero-copy decode where possible.

- `optimization-credibility` judge rubric: Optimization claims are real, not cosmetic. Pre-buffer on stream URLs (LoadControl), background thread priority sane, no sync on UI thread, no log calls in release, R8/ProGuard enabled for release. Benchmark screen actually measures frame drops and seek latency (Choreographer or VideoFrameMetadataListener), not a fake counter.

- `github-ready` judge rubric: Repo description + topics set, .gitignore correct for Android, LICENSE present, README has install + screenshots + perf numbers, Actions workflow triggers on push to main + pull_request, signs APK with a debug or release keystore (even if generated), uploads APK as a GitHub Release asset. Branch protection guidance noted in README. No secrets committed to history.


## Council

- `judge-1` judge via `["__spawn_agent__", "general-purpose"]` (local; timeout 600s)

## Gates

### plan_gate

- When: `after_plan`
- Policy: `revise_until_clean`
- Verdict source: `judge-1`
- Criteria: `code-quality, optimization-credibility`
- Max revisions: `3`

### delivery_gate

- When: `after_each_delivery`
- Policy: `revise_until_clean`
- Verdict source: `judge-1`
- Criteria: `builds-debug, builds-release, unit-tests, apk-size-budget, github-actions-workflow, code-quality, optimization-credibility, github-ready`
- Max revisions: `4`

## Loop Control

- Max iterations: `8`
- Budget: `{"tokens": 5000000, "wall_clock_min": 90}`
- No-progress: `{"action": "stop", "max_stalled_iterations": 2, "signals": ["same blocking issue repeats", "delivery artifact has no material change", "verifier output is unchanged"]}`
- Human checkpoints: `none`
- Stop conditions:
  - all deliveries pass their gate clean
  - max_iterations reached
  - same blocker repeats for 2 iterations
  - wall-clock 90 minutes exceeded

## Execution Boundary

- Mode: `in_session`
- Isolation: `current_workspace`
- Side effects: `{"duplicate_action_check": true, "notes": "Side effects: git init + push to a new public GitHub repo, GitHub Actions workflow creation, possibly `android` CLI calls for project scaffold, gradle builds. Idempotent where possible (gradle is, git push --force-with-lease is gated by duplicate_action_check).\n", "requires_approval": false}`

If the loop needs scheduled runs, child-agent lifecycle management, concurrency control, or restart-safe step retries, stop and tell the user this Looper spec should be handed to a durable orchestrator.

## Observability

- State file: `state.json`
- Run log: `run-log.md`
- Checkpoint granularity: `gate`

Use `state.json` for the latest resumable status and `run-log.md` for the append-only history of what happened.

## Privacy

- Before sending `plan, deliveries` to `judge-1`, confirm consent and apply redactions `.env, .env.*, secrets/**, **/*.key, **/*.keystore, **/*.jks`.

## Start Now

If the user asked to run now, begin at step 1 under Operator Instructions and keep going until a stop condition is reached.
