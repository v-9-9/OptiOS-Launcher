package com.optios.launcher

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.optios.launcher.databinding.ActivityMainBinding
import com.optios.launcher.media.MediaCardState
import com.optios.launcher.relay.SleepController
import com.optios.launcher.status.DeviceStats
import com.optios.launcher.status.StatusDonutsView
import com.optios.launcher.wallpaper.MacroStore
import com.optios.launcher.wallpaper.ThemeEngine
import com.optios.launcher.wallpaper.WallpaperStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * OptiOS — warm glass home for the HY320 projector.
 *
 * - Centered serif clock, square glass tiles, USAGE donuts, dock.
 * - Wallpaper layer: any jpg/png in /sdcard/Pictures/OptiOS picked from the
 *   Control Center; theme (ADAPTIVE) derives its accent from the photo.
 * - Macro recorder: Record → press any key (e.g. F9) → choose an action
 *   (YouTube / Cinemana / switch / sleep). Bindings persist.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.US)
    private val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.US)

    private val packageYouTube = "com.google.android.youtube.tv"
    private val packageCinemana = "com.shabakaty.cinemana"
    private var lastLaunched: String? = null

    // ---- Wallpaper + theme state ----
    private var wallIndex = 0
    private var lastAccent: Int = 0xFFE8A15C.toInt()

    // ---- Macro recorder ----
    private var recordingKey = false
    private var macros: MutableMap<Int, String> = mutableMapOf()

    // ================================ Clock ================================

    private val clockTicker = object : Runnable {
        override fun run() {
            val now = Date()
            val hm = clockFormat.format(now)
            if (binding.clockText.text != hm) {
                binding.clockText.text = hm
                binding.dateText.text = dateFormat.format(now)
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    private val mediaTicker = object : Runnable {
        override fun run() {
            updateMediaCard()
            handler.postDelayed(this, 1_000L)
        }
    }

    private val mediaStateListener = { updateMediaCard() }

    private val statusTicker = object : Runnable {
        override fun run() {
            val s = DeviceStats.snapshot()
            binding.donutsView.update(
                StatusDonutsView.Data(s.ramUsedPct, s.cpuLoadPct, s.cpuTempC, s.gpuTempC)
            )
            handler.postDelayed(this, 2_000L)
        }
    }

    // ============================== Lifecycle ==============================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tileYouTube.setOnClickListener { launchPackage(packageYouTube) }
        binding.tileCinemana.setOnClickListener { launchPackage(packageCinemana) }
        binding.tileYouTube.nextFocusDownId = R.id.dockApps
        binding.tileCinemana.nextFocusDownId = R.id.dockControls
        binding.tileYouTube.nextFocusRightId = R.id.tileCinemana
        binding.tileCinemana.nextFocusLeftId = R.id.tileYouTube

        binding.dockApps.setOnClickListener { toastInProgress("Apps") }
        binding.dockControls.setOnClickListener { toggleControlCenter() }
        binding.dockSettings.setOnClickListener { openSystemSettings() }
        binding.tileYouTube.requestFocus()

        // Wallpapers need media-read (granted silently via adb at install; dialog as fallback).
        if (android.os.Build.VERSION.SDK_INT < 33 &&
            checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 100)
        }

        if (SleepController.isAsleep) showSleep(true)
        macros = MacroStore.load(this)

        // ---- Control Center wiring ----
        binding.controlCenter.btnPanelSleep.setOnClickListener {
            SleepController.setSleep(!SleepController.isAsleep)
        }
        binding.controlCenter.btnWallPrev.setOnClickListener { cycleWallpaper(-1) }
        binding.controlCenter.btnWallNext.setOnClickListener { cycleWallpaper(+1) }
        binding.controlCenter.btnWallDefault.setOnClickListener { applyWallpaper(null) }
        binding.controlCenter.btnThemeAuto.setOnClickListener { applyTheme(ThemeEngine.Preset.ADAPTIVE) }
        binding.controlCenter.btnThemeWarm.setOnClickListener { applyTheme(ThemeEngine.Preset.WARM_DESK) }
        binding.controlCenter.btnThemeTokyo.setOnClickListener { applyTheme(ThemeEngine.Preset.TOKYO_NIGHT) }
        binding.controlCenter.btnThemeDark.setOnClickListener { applyTheme(ThemeEngine.Preset.DARK_GLASS) }
        binding.controlCenter.btnRecord.setOnClickListener { startRecording() }
        binding.controlCenter.btnClearMacros.setOnClickListener {
            macros.clear(); MacroStore.save(this, macros)
            binding.controlCenter.macroStatus.text = "Shortcuts cleared"
        }

        applyFocusAnimations()
        if (!entrancePlayed) {
            playEntranceAnimation()
            getSharedPreferences("optios", MODE_PRIVATE).edit().putBoolean(ENTRANCE_PLAYED, true).apply()
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        handler.post(clockTicker)
        MediaCardState.updateListener = mediaStateListener
        updateMediaCard()
        handler.postDelayed(mediaTicker, 1_000L)
        handler.postDelayed(statusTicker, 500L)
        SleepController.sleepListener = { asleep ->
            overlayHandler.post { showSleep(asleep) }
        }
        // Re-apply theme + wallpaper (survives returning from apps):
        applyWallpaper(WallpaperStore.selected(this))
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockTicker)
        handler.removeCallbacks(mediaTicker)
        handler.removeCallbacks(statusTicker)
        MediaCardState.updateListener = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.firstOrNull() ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            applyWallpaper(WallpaperStore.selected(this))
        }
    }

    // ======================= Wallpaper + theme engine ======================

    /** Set `null` for the built-in gradient. Loads async, off the UI thread. */
    private fun applyWallpaper(file: File?) {
        WallpaperStore.setSelected(this, file?.absolutePath)
        Thread {
            val viewW = resources.displayMetrics.widthPixels
            val viewH = resources.displayMetrics.heightPixels
            val bmp = file?.let { WallpaperStore.decode(it, viewW, viewH) }
            handler.post {
                if (bmp != null) {
                    binding.wallpaperView.setImageBitmap(bmp)
                    // Fade-in so the wallpaper never pops harshly (smoothness first).
                    binding.wallpaperView.alpha = 0f
                    binding.wallpaperView.visibility = ImageView.VISIBLE
                    binding.wallpaperView.animate().alpha(1f)
                        .setDuration(280L).setInterpolator(DecelerateInterpolator()).start()
                    binding.scrimView.visibility = View.VISIBLE
                    // Square fit: scaleType centerCrop already set in layout.
                } else {
                    binding.wallpaperView.visibility = View.GONE
                    binding.scrimView.visibility = View.GONE
                }
                val preset = ThemeEngine.load(this)
                val theme = ThemeEngine.resolve(preset, file, viewW, viewH)
                lastAccent = theme.accent
                binding.controlCenter.macroStatus.setTextColor(theme.accent)
                binding.mediaProgress.progressTintList =
                    android.content.res.ColorStateList.valueOf(theme.accent)
                binding.donutsView.setAccent(theme.accent)
                binding.clockText.setShadowGlow(theme.accent)
            }
        }.start()
    }

    private fun cycleWallpaper(dir: Int) {
        val list = WallpaperStore.list()
        if (list.isEmpty()) {
            toast("Put jpg/png files in $WallpaperStore.DIR")
            return
        }
        val current = WallpaperStore.selected(this)
        var idx = list.indexOfFirst { it.absolutePath == current?.absolutePath }
        idx = if (idx < 0) 0 else (idx + dir + list.size) % list.size
        applyWallpaper(list[idx])
        binding.controlCenter.wallName.text = list[idx].name
    }

    private fun applyTheme(preset: ThemeEngine.Preset) {
        ThemeEngine.save(this, preset)
        applyWallpaper(WallpaperStore.selected(this))
        Toast.makeText(this, "Theme: ${preset.label}", Toast.LENGTH_SHORT).show()
    }

    private fun android.widget.TextView.setShadowGlow(color: Int) {
        val d = shadowDy
        setShadowLayer(shadowRadius, shadowDx, d, color)
    }

    // ========================= Control Center panel ========================

    private fun toggleControlCenter() {
        val cc = binding.controlCenter.root
        if (cc.visibility == View.VISIBLE) {
            cc.animate().alpha(0f).translationY(40f).setDuration(220L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { cc.visibility = View.GONE }.start()
        } else {
            refreshCcInfo()
            cc.visibility = View.VISIBLE
            cc.translationY = 40f
            cc.animate().alpha(1f).translationY(0f).setDuration(260L)
                .setInterpolator(DecelerateInterpolator()).start()
            binding.controlCenter.btnWallNext.requestFocus()
        }
    }

    private fun refreshCcInfo() {
        val walls = WallpaperStore.list()
        binding.controlCenter.wallName.text =
            if (walls.isEmpty()) "No wallpapers — drop files in $WallpaperStore.DIR"
            else "${WallpaperStore.selected(this)?.name ?: "(default gradient)"} • ${walls.size} available"
        binding.controlCenter.macroStatus.text =
            if (macros.isEmpty()) "No shortcuts recorded"
            else macros.entries.joinToString("  ") { "Key ${it.key} → ${MacroStore.ACTIONS[it.value] ?: it.value}" }
    }

    // ============================ Macro recorder ===========================

    private fun startRecording() {
        recordingKey = true
        binding.controlCenter.macroStatus.text = "● Recording — press any key now (e.g. F9)…"
        binding.controlCenter.macroStatus.requestFocus()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Wake from sleep on any key:
        if (SleepController.isAsleep && event.action == android.view.KeyEvent.ACTION_DOWN) {
            SleepController.setSleep(false)
            return true
        }
        // Capture key while recording:
        if (recordingKey && event.action == android.view.KeyEvent.ACTION_DOWN &&
            event.keyCode != android.view.KeyEvent.KEYCODE_BACK
        ) {
            recordingKey = false
            askMacroAction(event.keyCode)
            return true
        }
        // Run bound shortcut:
        if (event.action == android.view.KeyEvent.ACTION_DOWN && !recordingKey) {
            macros[event.keyCode]?.let { action ->
                runMacro(action)
                return true
            }
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER ->
                    currentFocus?.performClick()
                android.view.KeyEvent.KEYCODE_DPAD_UP,
                android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val next = currentFocus?.focusSearch(dirFor(event.keyCode))
                    if (next != null && next.isFocusable) next.requestFocus() else return false
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun askMacroAction(keyCode: Int) {
        val actions = MacroStore.ACTIONS.entries.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Key ${keyCode} — choose action")
            .setItems(actions.map { it.value }.toTypedArray()) { _, which ->
                macros[keyCode] = actions[which].key
                MacroStore.save(this, macros)
                refreshCcInfo()
            }
            .setNegativeButton("Cancel") { _, _ -> refreshCcInfo() }
            .show()
    }

    private fun runMacro(action: String) {
        when (action) {
            MacroStore.ACT_YOUTUBE -> launchPackage(packageYouTube)
            MacroStore.ACT_CINEMANA -> launchPackage(packageCinemana)
            MacroStore.ACT_SWITCH -> {
                val next = if (lastLaunched == packageYouTube) packageCinemana else packageYouTube
                launchPackage(next)
            }
            MacroStore.ACT_SLEEP -> SleepController.setSleep(!SleepController.isAsleep)
        }
    }

    // ============================ UI helpers ===============================

    private fun showSleep(asleep: Boolean) {
        if (asleep) {
            binding.sleepOverlay.fadeView(0.97f, View.VISIBLE)
            window.attributes = window.attributes.apply { screenBrightness = 0.01f }
        } else {
            binding.sleepOverlay.fadeView(0f, View.GONE)
            window.attributes = window.attributes.apply { screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }

    private fun View.fadeView(targetAlpha: Float, visibility: Int) {
        this.animate().alpha(targetAlpha).setDuration(320L).start()
        this.visibility = visibility
    }

    private val overlayHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val ENTRANCE_PLAYED = "entrance_played"
    }

    /** Entrance animation only on the very first launch of the session. */
    private val entrancePlayed by lazy { getSharedPreferences("optios", MODE_PRIVATE).getBoolean(ENTRANCE_PLAYED, false) }

    /** Focus glow + smooth scale — 200ms, DecelerateInterpolator (cheap on GPU). */
    private fun applyFocusAnimations() {
        val tiles = listOf(binding.tileYouTube, binding.tileCinemana)
        tiles.forEach { tile ->
            tile.setOnFocusChangeListener { v, hasFocus ->
                val target = if (hasFocus) 1.10f else 1.0f
                v.animate().scaleX(target).scaleY(target)
                    .setDuration(200L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
        listOf(binding.dockApps, binding.dockControls, binding.dockSettings).forEach { dock ->
            dock.setOnFocusChangeListener { v, hasFocus ->
                v.animate().scaleY(if (hasFocus) 1.10f else 1.0f)
                    .setDuration(170L).start()
            }
        }
    }

    private fun playEntranceAnimation() {
        val overshoot = OvershootInterpolator(0.85f)
        val views = listOf<View>(binding.clockBlock, binding.tileYouTube, binding.tileCinemana, binding.dockRow)
        views.forEachIndexed { i, v ->
            v.alpha = 0f
            v.scaleX = 0.92f
            v.scaleY = 0.92f
            v.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(i * 80L)
                .setDuration(300L)
                .setInterpolator(overshoot)
                .start()
        }
    }

    private fun updateMediaCard() {
        val snap = MediaCardState.current
        if (snap == null || snap.title.isBlank()) {
            if (binding.mediaCard.visibility == View.VISIBLE) {
                binding.mediaCard.animate().alpha(0f).setDuration(160L)
                    .withEndAction { binding.mediaCard.visibility = View.GONE }.start()
            }
            return
        }
        binding.mediaCard.alpha = 1f
        binding.mediaCard.visibility = View.VISIBLE
        snap.albumArt?.let { binding.mediaArt.setImageBitmap(it) }
        binding.mediaTitle.text = snap.title
        binding.mediaArtist.text = snap.artist
        binding.mediaProgress.progressTintList =
            android.content.res.ColorStateList.valueOf(if (snap.accentColor != 0) snap.accentColor else lastAccent)
    }

    /** Full keyboard/D-pad: arrows between focusables, ENTER clicks, BACK exits to system. */
    private fun dirFor(keyCode: Int): Int = when (keyCode) {
        android.view.KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
        else -> View.FOCUS_RIGHT
    }

    private fun applyImmersiveMode() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun launchPackage(packageName: String) {
        lastLaunched = packageName
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            ContextCompat.startActivity(this, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null)
        } else {
            Toast.makeText(this, R.string.app_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSystemSettings() {
        val intent = Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ContextCompat.startActivity(this, intent, null)
    }

    private fun toastInProgress(label: String) {
        Toast.makeText(this, "$label — next milestone", Toast.LENGTH_SHORT).show()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
