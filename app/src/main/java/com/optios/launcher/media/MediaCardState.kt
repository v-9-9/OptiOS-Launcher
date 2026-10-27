package com.optios.launcher.media

import android.graphics.Bitmap
import android.service.notification.StatusBarNotification
import androidx.palette.graphics.Palette
import android.content.Context

/**
 * Shared state + color extraction for the media card.
 * Palette extracts the dominant color of the album art — exactly the
 * Spotify/SoundCloud-style "CD card" look requested.
 */
object MediaCardState {

    data class Snapshot(
        val albumArt: Bitmap?,
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val accentColor: Int,
        val progressMs: Long,
        val durationMs: Long,
    )

    @Volatile var current: Snapshot? = null
        private set
    @Volatile var updateListener: (() -> Unit)? = null

    fun onNotificationSeen(context: Context, sbn: StatusBarNotification, isMedia: Boolean) {
        if (!isMedia) return
        val extras = sbn.notification.extras
        val art = extras.get(android.app.Notification.EXTRA_LARGE_ICON) as? Bitmap
            ?: extras.get(android.app.Notification.EXTRA_PICTURE) as? Bitmap
        val accent = art?.let { dominantColor(it) } ?: 0xFF63B3F7.toInt()
        val snapshot = Snapshot(
            albumArt = art,
            title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty(),
            artist = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty(),
            isPlaying = sbn.isOngoing,
            accentColor = accent,
            progressMs = 0L,
            durationMs = 0L,
        )
        current = snapshot
        updateListener?.invoke()
    }

    fun dominantColor(bitmap: Bitmap): Int {
        val swatch = Palette.from(bitmap).maximumColorCount(8).generate().dominantSwatch
        return swatch?.rgb ?: 0xFF63B3F7.toInt()
    }

    fun clear() {
        current = null
        updateListener?.invoke()
    }
}
