# Docs capture harness

Instrumented harness that renders registered docs scenes on a real device and
produces the still frames and gesture clips published on the docs site. Scenes
live in the shared registry (`:app:docs:showcase`); this module hosts them in a
real app process so MediaStore-backed, Media3, panorama, MapLibre and ML
surfaces can be captured.

## Still capture

```bash
./gradlew :app:docs:capture:assembleDebugAndroidTest
REFRA_SERIAL=emulator-5554 ./testing/test-lab.sh docs-capture \
  "<comma-separated scene ids>" phone "<state or empty>" "light[,dark]"
```

`DocsCaptureTest` iterates the filtered scenes, mounts each in
`DocsSceneHost` at the exact logical size of the device preset
(`DeviceConfigurationOverride`), waits for `DocsScene.requiredReadyTag` when
declared (20s gate), lets `settleMillis` elapse for async decoders, then
freezes and captures into WebP under the app's
`docs-out/`. `test-lab.sh` pulls the tree and union-merges it into
`docs-out/`; `npm run manifest` (website) rebuilds `docs-manifest.json`.
Ordinary scenes use `captureToImage()`. Scenes with `captureWindow=true`
use a physical screenshot cropped to the measured frame, including modal
windows and native video or map surfaces.

The mount is a `Modifier.size` of the preset's logical size, so it coerces
into the host window's constraints. Without a `device` filter the test runs
every preset; on a phone-sized window (~412dp wide) the tablet and foldable
presets can never reach their logical size, so each such job is skipped with
a warning (8dp slack) and the run continues on the presets that fit. If no
preset fits the window the test fails fast with the window size and a pointer
to a matching emulator or an explicit `device=phone` filter.

`DocsScene.recordedStates` lists states that exist only as recorded clips —
`statesOf` excludes them from still capture and rejects them as explicit
filters, so a still run can never overwrite a clip frame.

## Direct launch (inspection + recording)

```bash
adb -s emulator-5554 shell am start \
  -n com.dot.gallery.docs.capture.test/com.dot.gallery.docs.capture.DocsCaptureActivity \
  --es scene <id> --es state <state> --es theme <light|dark> \
  --es device <phone|tablet|foldable> --ez recordBounds true
```

Unknown scene ids, undeclared states, and unknown presets finish the activity
with a log error — no fallback render. `recordBounds` writes the measured
on-screen `DocsSceneHost` rect (window bounds + window origin → physical
screen pixels) to `files/docs-frame-bounds.json` for the clip tooling.

## Recording a gesture clip

The host needs `adb`, `ffmpeg`, `ffprobe`, `cwebp`, and Node.js.

1. Hold instrumentation without a compose rule:
   ```bash
   adb -s emulator-5554 shell am instrument -w \
     -e class com.dot.gallery.docs.capture.DocsRecordingHoldTest#hold \
     -e holdSecs 300 \
     com.dot.gallery.docs.capture.test/androidx.test.runner.AndroidJUnitRunner
   ```
   `DocsRecordingHoldTest` is a plain `AndroidJUnit4` test on purpose —
   `createAndroidComposeRule` installs the pausable `MainTestClock` for the
   whole process, and a scene launched via `am start` would then compose on a
   clock nobody pumps: the pager freezes mid-transition and player state never
   reaches the UI while `screenrecord` runs.
   Run that command in a separate terminal or background shell. Without an
   explicit `holdSecs`, the helper skips itself in ordinary test runs.
2. `am start` the scene with `recordBounds`; verify it settled via screenshot.
3. Enable `show_touches` (save and restore the original system setting).
4. `screenrecord --size 960x2142 --time-limit 45 /data/local/tmp/<name>.mp4`
   from a persistent host-side `adb shell` — the emulator's native
   1280x2856/1248x2784 exceed the AVC macroblock ceiling (`err=-22`); run the
   shell detached so the recording survives the MCP session. Drive the real
   gesture via `uiautodev` (coordinates from a fresh screenshot, always with
   width/height), then SIGINT the exact `screenrecord` PID.
5. Pull the mp4 and `docs-frame-bounds.json`; confirm scene/state/theme match.
6. From `website/`:
   ```bash
   node scripts/encode-recording.mjs <raw.mp4> <bounds.json> <scene> <state> <theme>
   node scripts/register-recording.mjs <scene> <state> <theme>
   ```
   `encode-recording.mjs` crops to the measured bounds, emits H.264 mp4 plus a
   WebP poster into `docs-out/<scene>/<theme>/`; `register-recording.mjs` unions the
   `{file, poster}` record into `docs-manifest.json`. A video record requires
   its `.webp` poster inside docs-out; stills must not carry `poster`.

## Website contract

`docs.mjs collectScenes` validates mediaType/poster semantics;
`merge-manifest` copies `file` + `posterFile`; `check-registry` tracks both.
`PreviewCollection` renders `<video controls muted playsinline
preload="none">` with a "Recorded preview" badge — never autoplay/loop — and a
`play` event dispatches `pv:activate` so siblings pause and live iframes
unload. Live iframe readiness is the `docs:ready` postMessage; skiko renders
into an OffscreenCanvas worker, so no DOM `<canvas>` exists to assert.

## Fixture rules

All media/album/account fixtures are docs-owned (bundled `docs_*` assets,
fictional `*.example.com` accounts, in-memory callbacks). `DocsMediaStoreFixture`
owns its `Pictures/`/`Movies/ReFraDocsPreview/` rows and deletes only what it
inserted. The ML cutout scene stages exactly the two tracked SAM models into
the isolated test app's private files — never the full `ml-models-pack`.
