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

    private val mediaStateListener = Runnable { updateMediaCard() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tileYouTube.setOnClickListener { launchPackage(packageYouTube) }
        binding.tileCinemana.setOnClickListener { launchPackage(packageCinemana) }
        binding.dockApps.setOnClickListener { toastInProgress("Apps") }
        binding.dockControls.setOnClickListener { toastInProgress("Controls") }
        binding.dockSettings.setOnClickListener { openSystemSettings() }
        binding.tileYouTube.requestFocus()

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
