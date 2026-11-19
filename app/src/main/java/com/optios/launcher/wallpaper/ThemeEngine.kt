package com.optios.launcher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import androidx.palette.graphics.Palette

/**
 * Full theme engine. A Theme drives EVERYTHING visual: background gradient,
 * glass fill/border, focus ring, text/glow colors and the accent family.
 * "Auto" derives the accent from the current wallpaper (Palette).
 * Accent overrides are picked independently of the preset.
 */
object ThemeEngine {

    enum class Preset(val label: String) {
        ADAPTIVE("Auto (from wallpaper)"),
        WARM_DESK("Warm Desk"),
        TOKYO_NIGHT("Tokyo Night"),
        DARK_GLASS("Dark Glass"),
    }

    /** User-selectable accent swatches (independent of the preset). */
    val ACCENTS = listOf(
        0xFFE8A15C.toInt(), // amber
        0xFF7AA2F7.toInt(), // neon blue
        0xFF8BE89B.toInt(), // mint
        0xFFBB9AF7.toInt(), // violet
        0xFFF778BA.toInt(), // rose
        0xFFEDEDED.toInt(), // ice white
    )

    data class Theme(
        val preset: Preset,
        val accent: Int,
        val surfaceTop: Int,
        val surfaceCenter: Int,
        val surfaceBottom: Int,
        val glassFill: Int,   // ARGB incl. alpha
        val glassBorder: Int,
        val focusBorder: Int,
        val dimScrim: Int,
        val glow: Int,        // clock/ambient glow color
    )

    private const val KEY_PRESET = "theme_preset"
    private const val KEY_ACCENT = "accent_override"

    fun load(ctx: Context): Preset {
        val n = ctx.getSharedPreferences("optios", Context.MODE_PRIVATE)
            .getInt(KEY_PRESET, Preset.ADAPTIVE.ordinal)
        return Preset.entries.getOrElse(n) { Preset.ADAPTIVE }
    }

    fun savePreset(ctx: Context, p: Preset) =
        ctx.getSharedPreferences("optios", Context.MODE_PRIVATE).edit()
            .putInt(KEY_PRESET, p.ordinal).apply()

    fun accentOverride(ctx: Context): Int? =
        ctx.getSharedPreferences("optios", Context.MODE_PRIVATE)
            .getInt(KEY_ACCENT, -1).takeIf { it != -1 }

    fun saveAccent(ctx: Context, color: Int?) =
        ctx.getSharedPreferences("optios", Context.MODE_PRIVATE).edit()
            .putInt(KEY_ACCENT, color ?: -1).apply()

    /** Build a complete theme from preset + optional accent override + wallpaper. */
    fun resolve(
        preset: Preset,
        accentOverride: Int?,
        wallpaperFile: java.io.File?,
        viewW: Int,
        viewH: Int,
    ): Theme {
        val base = when (preset) {
            Preset.WARM_DESK -> base(
                preset, 0xFFE8A15C.toInt(),
                0xFF1B1512.toInt(), 0xFF221A14.toInt(), 0xFF120D0A.toInt(),
                glassFill = 0x2EF5EFE6.toInt(), border = 0xFFE8A15C.toInt(),
                glow = 0xFFE8A15C.toInt(),
            )
            Preset.TOKYO_NIGHT -> base(
                preset, 0xFF7AA2F7.toInt(),
                0xFF16161E.toInt(), 0xFF1A1B26.toInt(), 0xFF0F1017.toInt(),
                glassFill = 0x24C4CEF6.toInt(), border = 0xFF7AA2F7.toInt(),
                glow = 0xFF7AA2F7.toInt(),
            )
            Preset.DARK_GLASS -> base(
                preset, 0xFF9AB8D9.toInt(),
                0xFF0B0E13.toInt(), 0xFF10151D.toInt(), 0xFF080B10.toInt(),
                glassFill = 0x26AFC6E8.toInt(), border = 0xFF9AB8D9.toInt(),
                glow = 0xFF9AB8D9.toInt(),
            )
            Preset.ADAPTIVE -> {
                val derived = deriveFromWallpaper(wallpaperFile)
                base(
                    preset, derived,
                    0xFF141110.toInt(), 0xFF1A1613.toInt(), 0xFF0E0B09.toInt(),
                    glassFill = 0x2EF5EFE6.toInt(), border = derived,
                    glow = derived,
                )
            }
        }
        return if (accentOverride != null) {
            base.copy(accent = accentOverride, glassBorder = accentOverride,
                focusBorder = accentOverride, glow = accentOverride)
        } else base
    }

    private data class Base(
        val accent: Int, val surfaceTop: Int, val surfaceCenter: Int,
        val surfaceBottom: Int, val glassFill: Int, val border: Int,
        val glow: Int,
    )

    private fun base(
        preset: Preset,
        accent: Int, top: Int, center: Int, bottom: Int,
        glassFill: Int, border: Int, glow: Int,
    ) = Theme(
        preset, accent, top, center, bottom, glassFill, border,
        border, (bottom and 0xFFFFFF) or 0xB3000000.toInt(), glow,
    )

    /** Dominant accent pulled from the photo; amber if extraction fails. */
    fun deriveFromWallpaper(file: java.io.File?): Int {
        file ?: return 0xFFE8A15C.toInt()
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, o)
            var s = 1
            while (o.outWidth / (s * 2) >= 192 && o.outHeight / (s * 2) >= 108) s *= 2
            val bm = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = s })
                ?: return 0xFFE8A15C.toInt()
            val sw = Palette.from(bm).generate()
            val accent = sw.vibrantSwatch?.rgb ?: sw.mutedSwatch?.rgb
                ?: sw.dominantSwatch?.rgb ?: 0xFFE8A15C.toInt()
            bm.recycle()
            accent
        } catch (_: Exception) { 0xFFE8A15C.toInt() }
    }

    // ---------------------------------------------------------------- drawables

    fun backgroundGradient(ctx: Context, t: Theme): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
            t.surfaceTop, t.surfaceCenter, t.surfaceBottom
        ))

    private fun lighten(argb: Int, f: Float): Int {
        fun ch(c: Int) = (c * f).toInt().coerceIn(0, 255)
        val a = argb shr 24 and 0xFF
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return (a shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    fun glass(ctx: Context, t: Theme, radiusDp: Float, fillBoost: Float = 1.0f): GradientDrawable {
        val r = radiusDp * ctx.resources.displayMetrics.density
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
            lighten(t.glassFill, 1.12f * fillBoost), lighten(t.glassFill, fillBoost),
            lighten(t.glassFill, 0.85f)
        )).apply {
            cornerRadius = r
            setStroke((1.2f * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(1), t.glassBorder)
        }
    }

    /** Focus-aware selector: plain glass by default, brighter + accent ring on focus. */
    fun selector(ctx: Context, t: Theme, radiusDp: Float): StateListDrawable {
        val focused = glass(ctx, t, radiusDp, fillBoost = 1.35f).apply {
            setStroke((2.2f * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(2), t.focusBorder)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), glass(ctx, t, radiusDp))
        }
    }
}
