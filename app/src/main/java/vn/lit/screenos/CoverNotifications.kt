package vn.lit.screenos

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.os.Handler
import android.os.Looper

// Notification content is obtained on demand and is never persisted.
class CoverNotifications : NotificationListenerService() {
    var rawCount=0;private set
    var visibleCount=0;private set
    private val deliverChanges=Runnable { observers.toList().forEach { it() };CoverMonitorService.current?.schedule() }
    override fun onListenerConnected() { current = this;CoverMonitorService.current?.reloadHomeControls();changed() }
    override fun onListenerDisconnected() { if (current === this) current = null;CoverMonitorService.current?.reloadNotificationControls();changed() }
    override fun onDestroy() { if (current === this) current = null;changed();super.onDestroy() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { changed() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { changed() }
    private fun changed() { if(!main.hasCallbacks(deliverChanges)) main.postDelayed(deliverChanges,120) }
    fun snapshot(): List<StatusBarNotification> = try {
        val raw=activeNotifications?.filter { it.packageName != packageName }?.distinctBy { it.key }?.sortedByDescending { it.postTime } ?: emptyList()
        val keys=NotificationListPolicy.visible(raw.map(::metadata)).map { it.key }.toSet()
        val visible=raw.filter { it.key in keys }
        rawCount=raw.size;visibleCount=visible.size
        visible
    } catch (_: SecurityException) { emptyList() }
    fun packageCounts(): Map<String,Int> = NotificationListPolicy.counts(snapshot().map(::metadata))
    companion object {
        private val main=Handler(Looper.getMainLooper())
        private val observers=linkedSetOf<()->Unit>()
        var current: CoverNotifications? = null; private set
        fun observe(observer: ()->Unit) { observers.add(observer) }
        fun removeObserver(observer: ()->Unit) { observers.remove(observer) }
        internal fun metadata(entry: StatusBarNotification)=NotificationListPolicy.Item(entry.key,entry.packageName,entry.groupKey,
            (entry.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY)!=0,entry.postTime)
    }
}
