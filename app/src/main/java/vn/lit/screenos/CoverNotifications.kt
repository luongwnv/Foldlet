package vn.lit.screenos

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.Handler
import android.os.Looper

// Notification content is obtained on demand and is never persisted.
class CoverNotifications : NotificationListenerService() {
    override fun onListenerConnected() { current = this;CoverMonitorService.current?.reloadHomeControls();changed() }
    override fun onListenerDisconnected() { if (current === this) current = null;CoverMonitorService.current?.reloadNotificationControls();changed() }
    override fun onDestroy() { if (current === this) current = null;changed();super.onDestroy() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { changed() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { changed() }
    private fun changed() { main.post { observers.toList().forEach { it() };CoverMonitorService.current?.schedule() } }
    fun snapshot(): List<StatusBarNotification> = try {
        activeNotifications?.filter { it.packageName != packageName }?.sortedByDescending { it.postTime } ?: emptyList()
    } catch (_: SecurityException) { emptyList() }
    companion object {
        private val main=Handler(Looper.getMainLooper())
        private val observers=linkedSetOf<()->Unit>()
        var current: CoverNotifications? = null; private set
        fun observe(observer: ()->Unit) { observers.add(observer) }
        fun removeObserver(observer: ()->Unit) { observers.remove(observer) }
    }
}
