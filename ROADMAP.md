# OptiOS Roadmap — small, testable milestones

1. **M1 (done):** Clean project skeleton that reliably builds a debug APK.
2. **M2 (done):** Launcher surface — full-screen dark background, large English clock
   (left) + weather box (right), D-pad focus management, YouTube/Cinemana glass tiles, dock.
3. **M3 (done):** Default-Home intent filter, refined tiles, entrance/focus/press
   animations, media card (album art/artist/progress + Palette accent color),
   notification history (NotificationListenerService, opt-in).
4. **M3.5 (done):** LAN relay on port 8692 (laptop → projector): notifications,
   sleep/wake, status; 60s-rotating 6-digit pairing code next to the weather box.
5. **M3.6 (done):** In-launcher Control Center slide-up panel (relay/sleep/code/IP,
   remote-navigable), wider dock with dividers, bounds-safe entrance animations;
   Windows companion app (`windows/OptiOS-Companion.ps1`).
6. **M4:** Apps drawer — list launchable apps + HDMI/source apps when the
   system exposes them; hide apps inside OptiOS (never system uninstall).
7. **M5:** Wallpaper picker — preview, crop, persistence, favorites.
8. **M6:** Control Center — app-level brightness/color **overlays** (explicitly
   separate from hardware projector picture controls), volume, network/Bluetooth.
   (Partially delivered in M3.6 panel.)
9. **M7:** Idle/screensaver screen with configurable wallpaper + clock
   (not a system lock-screen replacement).
10. **M8:** Local Wi-Fi phone uploads of wallpapers (opt-in, local only).
