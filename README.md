# OptiOS

Lean Android launcher for the **HY320 projector** (`com.optios.launcher`).

- Landscape-first, remote-control / D-pad navigation
- Dark "Liquid Glass" UI over a **real wallpaper layer**
- Centered serif clock, square glass tiles, USAGE donuts (RAM/CPU rings + temps)
- **Wallpaper engine (v5):** drop any `jpg/png` in `/sdcard/Pictures/OptiOS`,
  pick it in the Control Center; the **ADAPTIVE** preset derives the accent
  color from the photo (Palette), so the theme always matches the wallpaper
- **Theme presets (v5):** Auto (from wallpaper) / Warm Desk / Tokyo Night / Dark Glass
- **Macro recorder (v5):** Record → press any remote/keyboard key (e.g. F9) →
  choose an action (open YouTube, open Cinemana, toggle between them, sleep/wake);
  bindings persist across restarts
- Media "CD card" (album art / artist / progress) via NotificationListenerService
- In-launcher **Control Center**: wallpaper picker, themes, macro recorder, sleep/wake
- Low CPU/RAM/GPU footprint; no background services unless strictly needed

## Building

```bash
./gradlew assembleDebug
# APK -> app/build/outputs/apk/debug/
```

CI: `.github/workflows/build.yml` builds a debug APK and uploads it as an artifact.

## Storage note

Reading wallpapers uses `READ_EXTERNAL_STORAGE` (media read only, granted at
install via `adb pm grant`). OptiOS never writes to shared storage.

## Safety model

Only ordinary, Android-permitted operations are exposed. OptiOS **never**
uninstalls/disables system packages and never claims to replace the system's
secure lock screen.

## Status

- **M1–M3 (done):** skeleton, glass home, default-Home, refined tiles/animations,
  media card, notification history.
- **M3.6–M3.9 (done):** Control Center panel, day/night clock, USAGE donuts,
  smoothed stats, warm desk edition (serif centered clock, square tiles).
- **v4 (done):** removed the LAN relay, pairing code and Windows companion —
  OptiOS is now fully standalone, on-device only.
- **v5 (done):** wallpaper engine + ADAPTIVE theme + 4 presets + macro recorder.
- Fan control is deliberately **not** implemented: the fan sysfs node is
  root-owned on this firmware. OptiOS reports temperatures instead of claiming
  control it does not have.

See the roadmap in `ROADMAP.md`.
