# Super 8

An Android camera app with a real-time Super-8 / 8mm film look, applied live
to both the viewfinder and the recorded video (not a post-processing filter
you apply afterward — what you see while shooting is what gets saved).

## How the look is made

`app/src/main/java/com/alejandro/super8cam/gl/FilmShader.kt` is a GLSL
fragment shader applied to every camera frame through CameraX's
`CameraEffect` / `SurfaceProcessor` API (`FilmEffectProcessor.kt`). It adds,
entirely procedurally (no external footage or stock textures):

- Film grain (re-rolled every simulated frame)
- An elliptical vignette
- A warm, slightly desaturated grade
- Gate weave (a tiny per-frame position jitter)
- Brightness flicker (lamp/exposure instability)
- An animated light leak
- Occasional vertical scratches and dust specks

The simulated frame rate for grain/weave/flicker is quantized to ~18 fps
(`SIMULATED_FILM_FPS` in `FilmEffectProcessor.kt`) regardless of the sensor's
actual capture rate, which is what gives the characteristic judder.

The retro UI (`ui/FilmFrameOverlayView.kt`, `MainActivity.kt`) adds a gate
border, sprocket-hole lane, a footage counter, a shutter button that toggles
between a circle and a stop-square, and two synthesized sound effects
(a shutter click and a looping motor whir — see `scripts/make_sounds.py`;
both are generated from scratch with no third-party audio).

## Building

This project can't be compiled inside the sandbox that wrote it (no access
to Google's Maven/SDK servers there), so it's built here via GitHub Actions
on every push to `main` — see `.github/workflows/build-apk.yml`. Download
the finished `app-debug.apk` from the workflow run's **Artifacts** section
and install it on your phone (you'll need to allow "install unknown apps"
for whichever app you download it with).

To build locally in Android Studio instead: open this folder, let Gradle
sync, and run on a device or emulator (minSdk 26 / Android 8.0+).

## Known rough edges / next steps

- Only the back camera is bound; a flip-to-front-camera control would be a
  natural addition.
- One film look is implemented. Swapping in alternate presets (cooler,
  higher-contrast, black & white) would mean adding more uniforms/branches
  to `FilmShader.kt`, or a small set of alternate fragment shaders, and a
  preset picker in the UI.
- No zoom, exposure, or focus controls yet — it binds the camera's defaults.
- Tested only by review, not on an actual device (this project was built in
  an environment with no Android phone attached) — the first real build is
  worth a careful look, especially recording stability and audio/video sync.
