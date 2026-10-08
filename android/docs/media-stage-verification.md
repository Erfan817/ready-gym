# Media stage verification

## Current outcome

One real licensed offline GIF is bundled for `rg-v1-bodyweight-squat`; five current movements are explicitly MISSING. All six have locally authored Chinese muscle labels, cues and cautions, explicitly unreviewed. No MainActivity, Planning.kt, app Gradle or manifest edits were made by the media worker.

Public Compose signature: `ExerciseDemo(stableId: String, modifier: Modifier = Modifier)` in package `com.readygym.app`. Use within `ReadyGymTheme`; the parent owns action selection/expansion and should compose only the selected demo. Metadata: `ExerciseMediaCatalog.find(stableId): ExerciseMedia?`, `ExerciseMediaCatalog.all: List<ExerciseMedia>`.

## Executed evidence

- `media-metadata-behavior-red.log`: actual JUnit assertion failure for empty metadata catalog before authored catalog implementation.
- `media-player-behavior-red.log`: actual Compose missing-state assertion failure before player implementation, plus absent available mapping failure.
- `media-assets-red.log`: actual file-contract failure before copying the verified original GIF into application assets.
- `media-assets-green.log`: Pillow file hash/dimensions/66 distinct frames/40ms per-frame/2640ms indefinite loop passed.
- `media-completed-verification.log`: final `:app:testDebugUnitTest --tests 'com.readygym.app.Exercise*'` BUILD SUCCESSFUL. XML parsed: **6 passed, 0 failures/errors, 1 explicitly ignored legacy Movie probe**. Summary: `build/verification/media/result.json`.
- Executed native-host tests cover user-initiated GIF loading, play/pause/resume, missing/unknown movement states, real API35 ImageDecoder animated type/dimensions/start-stop, and missing-file rejection.
- `build/verification/media/player-paused-offscreen.png`: actual offscreen native Robolectric window render. Visible nonblank squat imagery, pause state/resume control and Chinese instructions inspected. Not a phone screenshot or animation timing proof.
- `build/verification/media/squat-contact-sheet.png`: six sampled original GIF frames; visually reviewed as unweighted squat. Schematic outline is suitable for identification, not form/safety certification.

## Limits and follow-up

- No device/emulator execution, final integrated APK build or plan UI integration was performed by this worker; parent owns those.
- API26/27 fallback is implemented using Movie/time-driven View but **not runtime-verified**. Robolectric4.14.1 lacks native Movie shadow and returns null; the real failed probe is preserved in `media-final-green.log` (provisional filename notwithstanding, it failed). Test is explicitly ignored instead of faking a decoder. Device verification remains required.
- An extended verification attempt was interrupted by a tool timeout before test verdict. Only the completed final log is acceptance evidence.
- Small-phone/landscape/largest-font and reduced-motion device acceptance are not certified for this independent component. Default has no autoplay; explicit pause remains available.
- Missing movements: knee push-up, glute bridge, dead bug, bilateral bent-over dumbbell row, barbell row. None borrow similar movements or unlicensed Gym Visual GIFs.

All log/image paths above are relative to `/home/ubuntu/ready gym /android/`. Attribution/license URLs and exact original resource path are in `docs/media-attribution.md`.
