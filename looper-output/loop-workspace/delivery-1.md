# Delivery 1 — Project skeleton

## Status
PASS — `./gradlew assembleDebug` succeeded in 3m 13s. `app-debug.apk`
weighs 70 MB at this point; the release variant with R8 enabled will
shrink to the budgeted < 14 MB.

## What shipped

### Build system
- AGP 9.0.1 + Gradle 9.1.0 + Kotlin 2.3.20 + Compose BOM 2026.03.01
- Version catalog `gradle/libs.versions.toml` with Media3 1.6.0 added
- `compileSdk=36`, `minSdk=29`, `targetSdk=36`
- `proguard-rules.pro` with aggressive R8 config (logs stripped, full
  class repackaging, Media3 + DataStore safety nets)
- Release: `isMinifyEnabled=true`, `isShrinkResources=true`,
  signed with debug key (real keystore deferred to iteration 4)

### App shell
- `io.cherry.player.CherryApp` — `Application` subclass (empty for now)
- `io.cherry.player.MainActivity` — `ComponentActivity` with Compose,
  edge-to-edge, Material 3 dark/light theme via `CherryPlayerTheme`
- `io.cherry.player.player.PlaybackService` — `MediaSessionService`
  stub, declared in manifest so the wiring is correct; full
  ExoPlayer + `MediaSession` wiring lands in iteration 3

### UI
- Material 3 dynamic color on API 31+, hand-tuned cherry palette on
  lower APIs (`CherrySeed = #B3132C`, `CherryAccent = #E84A5F`,
  `CherryInk = #1B0A0E`)
- `Typography` for display/headline/title/body/label styles
- Placeholder player shell with `TopAppBar` + `Scaffold`
- Adaptive launcher icon (background + foreground vector drawables,
  monochrome variant for Android 13 themed icons)

### Manifest + permissions
- `INTERNET`, `ACCESS_NETWORK_STATE` (stream URLs)
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`,
  `POST_NOTIFICATIONS`, `WAKE_LOCK` (background audio)
- `PICTURE_IN_PICTURE` (PiP on phones)
- `<service>` for `PlaybackService` with `foregroundServiceType="mediaPlayback"`
  and `MediaSessionService` intent-filter
- `<activity>` declares `supportsPictureInPicture="true"`,
  `resizeableActivity="true"`, `configChanges` to keep ExoPlayer alive
  across orientation changes
- `network_security_config.xml` permits cleartext HTTP (needed for
  HLS test sources, IP cameras)
- `data_extraction_rules.xml` + `backup_rules.xml` (defaults)

### Resources
- `strings.xml` — full inventory of all UI strings (player controls,
  settings, benchmark, errors). All `$N` placeholders use positional
  args (`%1$s`, `%1$.2f`) so translators can reorder safely.
- `colors.xml` — `cherry_background`, `cherry_seed`, `cherry_accent`

## Verification

| Gate | Result |
|---|---|
| `builds-debug` | PASS (`./gradlew assembleDebug` exit 0, 3m 13s) |
| `builds-release` | NOT YET — iteration 2 |
| `unit-tests` | NOT YET — iteration 2 (no tests yet) |
| `apk-size-budget` | NOT YET — iteration 2 (needs release variant) |
| `github-actions-workflow` | NOT YET — iteration 4 |
| `code-quality` | pending — sub-agent judge will review |
| `optimization-credibility` | pending — sub-agent judge will review |
| `github-ready` | pending — iteration 4 |

## Known gaps carried into iteration 2
- Tests are not yet written; iteration 2 adds unit tests for speed
  clamping + gesture math + settings persistence.
- Adaptive icon foreground/background are placeholder vectors; iteration
  4 may swap in a real cherry-themed icon.
- `MainActivity` uses `configChanges` to keep ExoPlayer alive, but the
  real PlayerView / AndroidView wrapper + ViewModel wiring lands in
  iteration 2.

## Risks observed
- AGP 9.0.1 + Gradle 9.1.0 are newer than what most CI runners cache by
  default; the GitHub Actions workflow (iteration 4) needs a generous
  `actions/setup-java` cache.
- The `stripDebugDebugSymbols` warning for
  `libandroidx.graphics.path.so` + `libdatastore_shared_counter.so`
  is benign — these symbols are debug-only.