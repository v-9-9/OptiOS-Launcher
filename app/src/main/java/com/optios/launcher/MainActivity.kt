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
import com.optios.launcher.relay.LocalRelayServer
import com.optios.launcher.relay.RelayState
import com.optios.launcher.relay.SleepController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

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
            binding.clockText.text = clockFormat.format(now)
            binding.dateText.text = dateFormat.format(now)
            handler.postDelayed(this, 10_000L)
        }
    }

    private val mediaTicker = object : Runnable {
        override fun run() {
            updateMediaCard()
            handler.postDelayed(this, 1_000L)
        }
    }

    private val mediaStateListener = { updateMediaCard() }

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
        // Controls = toggle the local relay (laptop -> projector, opt-in)
        binding.dockControls.setOnClickListener { toggleRelay() }
        binding.dockSettings.setOnClickListener { openSystemSettings() }
        binding.tileYouTube.requestFocus()
        // Restore sleep state if it was left on:
        if (SleepController.isAsleep) showSleep(true)

        applyFocusAnimations()
        playEntranceAnimation()
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        handler.post(clockTicker)
        MediaCardState.updateListener = mediaStateListener
        updateMediaCard()
        handler.postDelayed(mediaTicker, 1_000L)
        hookRelayListeners()
        if (getPref(RELAY_ENABLED_KEY, false)) startRelay() else codePillState(false)
    }

    // ================= Relay (laptop -> projector) =================

    private val relayPrefs by lazy { getSharedPreferences("optios", MODE_PRIVATE) }
    private var relayServer: LocalRelayServer? = null
    private var codeTimer: Timer? = null

    private val overlayHandler = Handler(Looper.getMainLooper())

    private fun hookRelayListeners() {
        RelayState.overlayListener = { event ->
            overlayHandler.post {
                binding.relayTitle.text = event.title
                binding.relayBody.text = event.body.ifBlank { " " }
                binding.relayCard.alpha = 1f
                binding.relayCard.visibility = View.VISIBLE
                overlayHandler.postDelayed({
                    binding.relayCard.animate().alpha(0f).setDuration(300L)
                        .withEndAction { binding.relayCard.visibility = View.GONE }.start()
                }, 6_000L)
            }
        }
        SleepController.sleepListener = { asleep ->
            overlayHandler.post { showSleep(asleep) }
        }
    }

    private fun toggleRelay() {
        val now = !getPref(RELAY_ENABLED_KEY, false)
        setPref(RELAY_ENABLED_KEY, now)
        if (now) startRelay() else stopRelay()
        Toast.makeText(this, "Relay " + if (now) "ON — see code on screen" else "OFF", Toast.LENGTH_SHORT).show()
    }

    private fun startRelay() {
        if (relayServer != null) return
        RelayState.enabled = true
        RelayState.port = 8692
        relayServer = LocalRelayServer(RelayState.port) { }.apply { start() }
        val onScreen = ReadLocalIp.ipv4?.let { "$it:${RelayState.port}" } ?: "booting…"
        // rotate code immediately + every 60 seconds
        RelayState.rotateCode()
        codeTimer = Timer("optios-code").apply {
            schedule(object : TimerTask() {
                override fun run() = RelayState.rotateCode()
            }, 60_000L, 60_000L)
        }
        codePillState(true)
        binding.codePill.text = RelayState.code
        Toast.makeText(this, "Relay on: $onScreen", Toast.LENGTH_LONG).show()
    }

    private fun stopRelay() {
        RelayState.enabled = false
        relayServer?.shutdown()
        relayServer = null
        codeTimer?.cancel()
        codeTimer = null
        codePillState(false)
    }

    private fun codePillState(visible: Boolean) {
        binding.codePill.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            RelayState.rotateCode()
            binding.codePill.text = RelayState.code
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
        this.animate().alpha(targetAlpha).setDuration(250L).start()
        this.visibility = visibility
    }

    private fun getPref(key: String, def: Boolean): Boolean = relayPrefs.getBoolean(key, def)
    private fun setPref(key: String, value: Boolean) = relayPrefs.edit().putBoolean(key, value).apply()

    companion object {
        const val RELAY_ENABLED_KEY = "relay_enabled"
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockTicker)
        handler.removeCallbacks(mediaTicker)
        MediaCardState.updateListener = null
    }

    /** Focus glow + tiny scale pop — 180ms, DecelerateInterpolator (cheap on GPU). */
    private fun applyFocusAnimations() {
        val tiles = listOf(binding.tileYouTube, binding.tileCinemana)
        tiles.forEach { tile ->
            tile.setOnFocusChangeListener { v, hasFocus ->
                val target = if (hasFocus) 1.06f else 1.0f
                v.animate().scaleX(target).scaleY(target)
                    .setDuration(180L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
        listOf(binding.dockApps, binding.dockControls, binding.dockSettings).forEach { dock ->
            dock.setOnFocusChangeListener { v, hasFocus ->
                v.animate().scaleY(if (hasFocus) 1.08f else 1.0f)
                    .setDuration(150L).start()
            }
        }
    }

    private fun playEntranceAnimation() {
        val views = listOf<View>(binding.clockBlock, binding.tileYouTube, binding.tileCinemana, binding.dockRow)
        views.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = 24f
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(i * 60L)
                .setDuration(200L)
                .setInterpolator(DecelerateInterpolator())
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
