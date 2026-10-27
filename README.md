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

- **M1 (done):** buildable skeleton, CI builds a debug APK.
- **M2 (done):** glass home UI — clock, date, weather box, YouTube/Cinemana tiles, dock.
- **M3 (done):** default-Home support, refined tiles + animations, media "CD card"
  (album art / artist / progress, Palette accent), launcher-as-Home intent filter.
- **M3.5 (done):** LAN relay server (port 8692) — laptop → projector notifications,
  sleep/wake, status endpoint (CPU/GPU temps, free RAM); 60s-rotating 6-digit
  pairing code shown next to the weather box; notification history.
- **M3.6 (done):** in-launcher **Control Center** slide-up panel (relay on/off,
  sleep/wake, rotating code, projector IP — all operable from the projector
  remote), wider dock with dividers, bounds-safe entrance animations.
- Windows companion: `windows/OptiOS-Companion.ps1` (PowerShell WinForms, posts
  notifications / sleep / wake / status to the relay).

Fan control is deliberately **not** implemented: the fan sysfs node is
root-owned on this firmware. OptiOS reports temperatures instead of claiming
control it does not have.

See the roadmap in `ROADMAP.md`.
