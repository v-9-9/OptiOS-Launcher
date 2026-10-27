package com.optios.launcher.media

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.media.app.NotificationCompat
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
        val isMedia = sbn.notification?.actions?.any { it.actionIntent != null } == true &&
            extras.get(Notification.EXTRA_TITLE) != null &&
            isMediaNotification(sbn)
        val entry = HistoryEntry(
            key = sbn.key ?: return,
            packageName = sbn.packageName ?: return,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            whenPosted = sbn.postTime,
        )
        if (entry.title.isNotBlank()) {
            synchronized(history) {
                history.removeIf { it.key == entry.key }
                history.addFirst(entry)
                while (history.size > MAX_HISTORY) history.removeLast()
            }
        }
        // Hand-off to the media card watcher.
        MediaCardState.onNotificationSeen(this, sbn, isMedia)
    }

    private fun isMediaNotification(sbn: StatusBarNotification): Boolean =
        NotificationCompat.isMediaNotification(sbn.notification) ||
        sbn.packageName.contains("music") ||
        sbn.packageName.contains("spotify") ||
        sbn.packageName.contains("soundcloud")

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        synchronized(history) {
            history.removeIf { it.key == (sbn.key ?: "") }
        }
    }
}
