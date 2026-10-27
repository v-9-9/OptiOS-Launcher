package com.optios.launcher

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.optios.launcher.databinding.ActivityMainBinding

/**
 * Milestone 1 shell: landscape-first Activity that builds a premium dark
 * surface and proves the packaging pipeline works on the HY320 form factor.
 *
 * All OptiOS later UI (clock/weather bar, YouTube/Cinemana tiles, app drawer,
 * wallpapers, control center) will mount inside this Activity's root.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hide system bars: projectors are driven by remote control, not touch.
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        binding.osVersionText.text =
            getString(R.string.os_version_line, android.os.Build.VERSION.RELEASE)
    }
}
