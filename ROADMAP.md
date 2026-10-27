# OptiOS Roadmap — small, testable milestones

1. **M1 (done):** Clean project skeleton that reliably builds a debug APK.
2. **M2 (done):** Launcher surface — full-screen dark background, large clock,
   D-pad focus management, YouTube/Cinemana glass tiles, dock.
3. **M3 (done):** Default-Home intent filter, refined tiles, entrance/focus/press
   animations, media card (album art/artist/progress + Palette accent color),
   notification history (NotificationListenerService, opt-in).
4. **M3.6–M3.9 (done):** In-launcher Control Center, dock dividers, USAGE donuts,
   smoothed CPU stats, entrance-once-per-session, warm desk edition.
5. **v4 (done):** LAN relay, pairing code and Windows companion **removed** by
   user decision — standalone on-device launcher; sleep/wake own overlay only.
6. **v5 (done):** Wallpaper engine (`/sdcard/Pictures/OptiOS`, picker in Control
   Center, memory-safe decode, fade-in), ADAPTIVE theme preset (Palette accent
   from wallpaper), 4 presets (Auto/Warm/Tokyo/Dark), macro recorder
   (Record → key → action, persisted JSON).
7. **M4 (next):** Apps drawer — list launchable apps + HDMI/source apps when the
   system exposes them; hide apps inside OptiOS (never system uninstall).
8. **M6:** Control Center — app-level brightness/color **overlays** (explicitly
   separate from hardware projector picture controls), volume, network/Bluetooth.
9. **M7:** Idle/screensaver screen with configurable wallpaper + clock
   (not a system lock-screen replacement).
10. **M8:** Local Wi-Fi phone uploads of wallpapers (opt-in, local only).
