# HY320 Device Report (read-only diagnostics)

Device: HY320 projector
ADB: `192.168.100.92:5555` (Wi-Fi, authorized)

| Property | Value |
|---|---|
| Model | adt3 (Allwinner sun50iw12p1) |
| Android | 11 (API 30) |
| ABI | armeabi-v7a |
| Firmware | UI_Veng.projector.20241226.174652 |
| RAM | ~940 MB total, ~340 MB available |
| Storage | ~2.35 GB free (after cleanup, 2026-10-27) |
| System locale | was ar-EG → set to en-US via `settings put system system_locales` (applies fully after device restart) |
| Launcher (system) | com.htc.htclauncherhighenglishd08 — untouched |

## Changes made to device (all approved by owner, 2026-10-27)
Uninstalled (user-installed only, `adb uninstall`):
- com.android.chrome, com.kick.mobile, com.netflix.mediaclient,
  com.yablio.sendfilestotv, com.cloudflare.onedotonedotonedotone,
  com.duckduckgo.mobile.android, cm.aptoide.pt, com.spocky.projengmenu

Kept: com.google.android.youtube.tv, com.shabakaty.cinemana,
      system launcher + all system packages (never touched).

## OptiOS design constraints derived from this device
- Target ABI: armeabi-v7a (add arm64 only if firmware ever moves).
- Memory budget: launcher should stay under ~120 MB RSS.
- No background services by default; animations short and cheap.
- minSdk 23 is fine (device is API 30).
