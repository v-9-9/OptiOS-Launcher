package com.optios.launcher

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.optios.launcher.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * OptiOS home (Milestone 2).
 *
 * Design contract:
 * - Huge English clock LEFT, weather box RIGHT (Arabic RTL mirrored naturally).
 * - Two hero glass tiles: YouTube + Cinemana (configurable in later milestones).
 * - Remote/keyboard (D-pad) FIRST: focus = visible glow; mouse works too.
 * - Ultra light: no threads, no services; one UI Handler for the clock.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.US)
    private val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.US)

    // Packages on the HY320 (verified installed via ADB).
    private val packageYouTube = "com.google.android.youtube.tv"
    private val packageCinemana = "com.shabakaty.cinemana"

    private val clockTicker = object : Runnable {
        override fun run() {
            val now = Date()
            binding.clockText.text = clockFormat.format(now)
            binding.dateText.text = dateFormat.format(now)
            clockHandler.postDelayed(this, 10_000L) // every 10s — cheap
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyImmersiveMode()

        binding.tileYouTube.setOnClickListener { launchPackage(packageYouTube) }
        binding.tileCinemana.setOnClickListener { launchPackage(packageCinemana) }

        binding.dockApps.setOnClickListener { toastInProgress("Apps") }
        binding.dockControls.setOnClickListener { toastInProgress("Controls") }
        binding.dockSettings.setOnClickListener { openSystemSettings() }

        // Initial focus for remote users: YouTube tile.
        binding.tileYouTube.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        clockHandler.post(clockTicker)
        applyImmersiveMode()
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockTicker)
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
        val pm = packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
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
        Toast.makeText(this, "$label — coming in next milestone", Toast.LENGTH_SHORT).show()
    }
}
