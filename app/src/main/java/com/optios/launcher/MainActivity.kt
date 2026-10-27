package com.optios.launcher

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.optios.launcher.databinding.ActivityMainBinding
import com.optios.launcher.media.MediaCardState
import com.optios.launcher.status.DeviceStats
import com.optios.launcher.status.StatusDonutsView
import com.optios.launcher.relay.SleepController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * OptiOS home (Milestone 3):
 * - Live clock + date (left), weather glass box (right).
 * - Refined liquid-glass tiles (YouTube / Cinemana) with focus/press animations.
 * - Media "CD card": appears when a music session is live (album art, title,
 *   artist, progress, accent color extracted from the art via Palette).
 * - Registers as a real Home launcher: pressing HOME on the device offers
 *   OptiOS as the default home choice (user selects it, nothing forced).
 * - Still ultra-light: zero services beyond the (opt-in) NotificationListener.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.US)
    private val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.US)

    private val packageYouTube = "com.google.android.youtube.tv"
    private val packageCinemana = "com.shabakaty.cinemana"

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tileYouTube.setOnClickListener { launchPackage(packageYouTube) }
        binding.tileCinemana.setOnClickListener { launchPackage(packageCinemana) }
        // Explicit D-pad/keyboard navigation (fixes focus issues on HID keyboards):
        binding.tileYouTube.nextFocusDownId = R.id.dockApps
        binding.tileCinemana.nextFocusDownId = R.id.dockControls
        binding.tileYouTube.nextFocusRightId = R.id.tileCinemana
        binding.tileCinemana.nextFocusLeftId = R.id.tileYouTube
        binding.dockApps.setOnClickListener { toastInProgress("Apps") }
        // Controls = natural in-launcher Control Center (no linking, no website)
        binding.dockControls.setOnClickListener { toggleControlPanel() }
        binding.dockSettings.setOnClickListener { openSystemSettings() }
        binding.tileYouTube.requestFocus()
        // Restore sleep state if it was left on:
        if (SleepController.isAsleep) showSleep(true)
        // Control Center panel:
        binding.btnPanelSleep.setOnClickListener {
            SleepController.setSleep(!SleepController.isAsleep)
        }

        applyFocusAnimations()
        if (!entrancePlayed) {
            playEntranceAnimation()
            getSharedPreferences("optios", MODE_PRIVATE).edit().putBoolean(ENTRANCE_PLAYED, true).apply()
        }
    }

    /** In-launcher Control Center: slide-up glass panel. */
    private fun toggleControlPanel() {
        if (binding.controlPanel.visibility == View.VISIBLE) {
            binding.controlPanel.animate().alpha(0f).translationY(40f).setDuration(220L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { binding.controlPanel.visibility = View.GONE }.start()
        } else {
            binding.controlPanel.visibility = View.VISIBLE
            binding.controlPanel.translationY = 40f
            binding.controlPanel.animate().alpha(1f).translationY(0f).setDuration(260L)
                .setInterpolator(DecelerateInterpolator()).start()
            binding.btnPanelSleep.requestFocus()
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
        hookOverlayListeners()
    }

    private val overlayHandler = Handler(Looper.getMainLooper())

    private fun hookOverlayListeners() {
        SleepController.sleepListener = { asleep ->
            overlayHandler.post { showSleep(asleep) }
        }
    }

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

    companion object {
        private const val ENTRANCE_PLAYED = "entrance_played"
    }

    /** Entrance animation only on the very first launch of the session. */
    private val entrancePlayed by lazy { getSharedPreferences("optios", MODE_PRIVATE).getBoolean(ENTRANCE_PLAYED, false) }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockTicker)
        handler.removeCallbacks(mediaTicker)
        handler.removeCallbacks(statusTicker)
        MediaCardState.updateListener = null
    }

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
        val overshoot = android.view.animation.OvershootInterpolator(0.85f)
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
            android.content.res.ColorStateList.valueOf(snap.accentColor)
    }

    /** Full keyboard/D-pad: arrows between focusables, ENTER clicks, BACK exits to system. */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Any key wakes from OptiOS sleep (remote / BT keyboard / mouse click).
        if (SleepController.isAsleep && event.action == android.view.KeyEvent.ACTION_DOWN) {
            SleepController.setSleep(false)
            return true
        }
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
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
}
