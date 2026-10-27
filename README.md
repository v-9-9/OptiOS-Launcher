# OptiOS

Lean Android launcher for the **HY320 projector** (`com.optios.launcher`).

- Landscape-first, remote-control / D-pad navigation
- Dark, calm "Liquid Glass"-inspired UI (restrained transparency + gradients)
- Arabic RTL where appropriate; large English clock on the left, weather on the right
- Configurable tiles for YouTube and Cinemana; app drawer; wallpapers; control center
- Low CPU/RAM/GPU footprint; no background services unless strictly needed

## Building

```bash
./gradlew assembleDebug
# APK -> app/build/outputs/apk/debug/
```

CI: `.github/workflows/build.yml` builds a debug APK and uploads it as an artifact.

## Safety model

Only ordinary, Android-permitted operations are exposed. OptiOS **never**
uninstalls/disables system packages and never claims to replace the system's
secure lock screen.

## Status

Milestone 1 — buildable shell (this commit). See the roadmap in `ROADMAP.md`.
