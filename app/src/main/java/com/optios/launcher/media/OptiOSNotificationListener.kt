package com.optios.launcher.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.ArrayDeque
import java.util.Deque

/**
 * Legitimate Android API use only:
 * - Receives notifications because the user grants "Notification access".
 * - Keeps a small in-memory history (max 20) for the OptiOS drawer; NEVER leaves the device.
 * - No network. No root. Uninstalling OptiOS removes everything.
 */
class OptiOSNotificationListener : NotificationListenerService() {

    data class HistoryEntry(
        val key: String,
        val packageName: String,
        val title: String,
        val text: String,
        val whenPosted: Long,
    )

    companion object {
        val history: Deque<HistoryEntry> = ArrayDeque()
        const val MAX_HISTORY = 20
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification?.extras ?: return
        val isMedia = isMediaNotification(sbn)
        val entry = HistoryEntry(
            key = sbn.key ?: return,
            packageName = sbn.packageName ?: return,
            title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty(),
            whenPosted = sbn.postTime,
        )
        if (entry.title.isNotBlank()) {
            synchronized(history) {
                history.removeIf { it.key == entry.key }
                history.addFirst(entry)
                while (history.size > MAX_HISTORY) history.removeLast()
            }
        }
        MediaCardState.onNotificationSeen(this, sbn, isMedia)
    }

    /**
     * Media-detection without androidx.media:
     * Android attaches a MediaSession token under the extra key
     * "android.mediaSession" for media-style notifications.
     */
    private fun isMediaNotification(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification?.extras ?: return false
        val hasSession = extras.get("android.mediaSession") != null
        val looksLikeMusic = sbn.packageName.contains("music") ||
            sbn.packageName.contains("spotify") ||
            sbn.packageName.contains("soundcloud") ||
            sbn.packageName.contains("youtube")
        return hasSession || (sbn.isOngoing && looksLikeMusic)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        synchronized(history) {
            history.removeIf { it.key == (sbn.key ?: "") }
        }
    }
}
