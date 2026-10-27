package com.optios.launcher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Wallpapers live in /sdcard/Pictures/OptiOS — drop any jpg/png there and it
 * appears in the Control Center picker. Selection persists across restarts.
 */
object WallpaperStore {

    const val DIR = "/sdcard/Pictures/OptiOS"
    private const val KEY = "wallpaper_path"

    fun list(): List<File> {
        val dir = File(DIR)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.isFile && (f.name.endsWith(".jpg") || f.name.endsWith(".jpeg") || f.name.endsWith(".png")) }
            ?.sortedBy { it.name } ?: emptyList()
    }

    fun hasChoice(ctx: Context): Boolean =
        ctx.getSharedPreferences("optios", Context.MODE_PRIVATE).contains(KEY)

    fun selected(ctx: Context): File? {
        val path = ctx.getSharedPreferences("optios", Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        val f = File(path)
        return if (f.exists()) f else null
    }

    fun setSelected(ctx: Context, path: String?) {
        // Empty string = explicit "Default gradient" choice; never distinguishable from "never chosen".
        ctx.getSharedPreferences("optios", Context.MODE_PRIVATE).edit().putString(KEY, path ?: "").apply()
    }

    /** Memory-safe decode to roughly the screen size. */
    fun decode(file: File, viewW: Int, viewH: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sample = 1
            while (opts.outWidth / (sample * 2) >= viewW && opts.outHeight / (sample * 2) >= viewH) sample *= 2
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) { null }
    }
}
