package com.optios.launcher.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.palette.graphics.Palette

/**
 * Theme presets. ADAPTIVE derives the accent from the current wallpaper
 * (Palette dominant swatch), so the interface always matches the photo.
 */
object ThemeEngine {

    enum class Preset(val label: String) {
        ADAPTIVE("Auto (from wallpaper)"),
        WARM_DESK("Warm Desk"),
        TOKYO_NIGHT("Tokyo Night"),
        DARK_GLASS("Dark Glass"),
    }

    data class Theme(
        val preset: Preset,
        val accent: Int,          // used for glows / progress / focus border
        val surfaceTop: Int,      // fallback gradient (when no wallpaper)
        val surfaceCenter: Int,
        val surfaceBottom: Int,
    )

    private const val KEY = "theme_preset"

    fun load(ctx: android.content.Context): Preset {
        val n = ctx.getSharedPreferences("optios", android.content.Context.MODE_PRIVATE).getInt(KEY, Preset.ADAPTIVE.ordinal)
        return Preset.entries.getOrElse(n) { Preset.ADAPTIVE }
    }

    fun save(ctx: android.content.Context, p: Preset) {
        ctx.getSharedPreferences("optios", android.content.Context.MODE_PRIVATE).edit().putInt(KEY, p.ordinal).apply()
    }

    fun resolve(preset: Preset, wallpaperFile: java.io.File?, viewW: Int, viewH: Int): Theme {
        return when (preset) {
            Preset.WARM_DESK -> Theme(
                preset, 0xFFE8A15C.toInt(),
                0xFF1B1512.toInt(), 0xFF221A14.toInt(), 0xFF120D0A.toInt()
            )
            Preset.TOKYO_NIGHT -> Theme(
                preset, 0xFF7AA2F7.toInt(),
                0xFF16161E.toInt(), 0xFF1A1B26.toInt(), 0xFF0F1017.toInt()
            )
            Preset.DARK_GLASS -> Theme(
                preset, 0xFF9AB8D9.toInt(),
                0xFF0B0E13.toInt(), 0xFF10151D.toInt(), 0xFF080B10.toInt()
            )
            Preset.ADAPTIVE -> {
                val file = wallpaperFile
                if (file == null) return WARM_FALLBACK
                val bm = decodeSmall(file) ?: return WARM_FALLBACK
                val sw = Palette.from(bm).generate()
                val accent = sw.vibrantSwatch?.rgb
                    ?: sw.mutedSwatch?.rgb
                    ?: sw.dominantSwatch?.rgb
                    ?: 0xFFE8A15C.toInt()
                bm.recycle()
                // Darken the surface family so text stays readable over any photo.
                Theme(preset, accent, 0xFF141110.toInt(), 0xFF1A1613.toInt(), 0xFF0E0B09.toInt())
            }
        }
    }

    private val WARM_FALLBACK = Theme(
        Preset.WARM_DESK, 0xFFE8A15C.toInt(),
        0xFF1B1512.toInt(), 0xFF221A14.toInt(), 0xFF120D0A.toInt()
    )

    private fun decodeSmall(file: java.io.File): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        var s = 1
        while (o.outWidth / (s * 2) >= 192 && o.outHeight / (s * 2) >= 108) s *= 2
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (_: Exception) { null }
}
