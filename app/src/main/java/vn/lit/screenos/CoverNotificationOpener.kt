package vn.lit.screenos

import android.app.ActivityOptions
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.view.Display

/** Only runs after the user taps a notification on the unlocked cover screen. */
object CoverNotificationOpener {
    private val main=Handler(Looper.getMainLooper())
    private var generation=0
    var status="idle";private set
    fun cancelPending() { generation++ }
    private fun allowed(context: Context,displayId: Int): Boolean {
        val display=Platform.overlayDisplay(context) ?: return false
        return display.displayId==displayId && display.state==Display.STATE_ON && Platform.unlocked(context) && CoverMonitorService.current!=null &&
            CoverMonitorService.current?.foldedForTaskControl()!=false
    }
    fun open(context: Context,entry: StatusBarNotification,displayId: Int,valid: ()->Boolean,opened: ()->Unit,failed: ()->Unit) {
        if(!allowed(context,displayId)) { failed();return }
        val active=CoverNotifications.current?.snapshot()?.firstOrNull { it.key==entry.key }
        if(active==null) { Platform.messageOnDisplay(context,displayId,I18n.text(R.string.ui_this_notification_has_expired));failed();return }
        val request=++generation
        val intent=context.packageManager.getLaunchIntentForPackage(active.packageName)
        val prefs=Platform.preferences(context)
        val rotation=intent?.component?.flattenToString()?.let { prefs.getString("orientation.$it",null) }
            ?: prefs.getString("orientation","auto") ?: "auto"
        CoverRotation.prepare(context,displayId,rotation) { ready ->
            if(request!=generation || !allowed(context,displayId) || !valid()) { failed();return@prepare }
            val pending=active.notification.contentIntent
            var sent=false
            var fallback=false
            if(pending!=null && Build.VERSION.SDK_INT>=31 && pending.isActivity) {
                sent=runCatching {
                    val options=ActivityOptions.makeBasic().apply {
                        launchDisplayId=displayId
                        if(Build.VERSION.SDK_INT>=34) setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    }
                    pending.send(context,0,null,null,null,null,options.toBundle())
                    true
                }.getOrDefault(false)
            }
            if(!sent && intent!=null) { sent=Platform.launch(context,intent,displayId);fallback=sent }
            if(!sent) {
                status="blocked"
                Platform.messageOnDisplay(context,displayId,I18n.text(R.string.ui_notification_cannot_open))
                failed();return@prepare
            }
            status=if(fallback) "app-fallback" else "notification-sent"
            opened()
            CoverRotation.apply(context,displayId,if(ready) rotation else "auto",ownerPackage=active.packageName)
            if(fallback) Platform.messageOnDisplay(context,displayId,I18n.text(R.string.ui_notification_opened_app))
            // Some apps redirect their PendingIntent to an inner-screen-only task.
            // Reopen their launcher on the cover only if the user is still on our
            // menu/SystemUI. Never override a different app the user has opened.
            if(!fallback && intent!=null) main.postDelayed({
                if(request!=generation || !allowed(context,displayId)) return@postDelayed
                val accessibility=NavigationService.current ?: return@postDelayed
                val focused=Platform.focusedCoverPackage(accessibility,displayId)
                if(focused==active.packageName) {
                    status="opened-on-cover"
                    if(active.isClearable && (active.notification.flags and Notification.FLAG_AUTO_CANCEL)!=0)
                        CoverNotifications.current?.cancelNotification(active.key)
                    return@postDelayed
                }
                if(focused!=null && focused !in setOf(context.packageName,"com.android.systemui","com.samsung.android.app.aodservice")) return@postDelayed
                if(Platform.launch(context,Intent(intent),displayId)) {
                    status="app-fallback"
                    Platform.messageOnDisplay(context,displayId,I18n.text(R.string.ui_notification_opened_app))
                }
            },700)
        }
    }
}
