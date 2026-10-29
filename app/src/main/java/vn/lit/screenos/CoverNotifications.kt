package vn.lit.screenos

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

// Notification content is obtained on demand and is never persisted.
class CoverNotifications : NotificationListenerService() {
    override fun onListenerConnected() { current = this }
    override fun onListenerDisconnected() { if (current === this) current = null }
    override fun onDestroy() { if (current === this) current = null; super.onDestroy() }
    fun snapshot(): List<StatusBarNotification> = try {
        activeNotifications?.filter { it.packageName != packageName }?.sortedByDescending { it.postTime } ?: emptyList()
    } catch (_: SecurityException) { emptyList() }
    companion object { var current: CoverNotifications? = null; private set }
}
