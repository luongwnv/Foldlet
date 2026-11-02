package vn.lit.screenos

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** Inspect only Samsung cover layout IDs/visibility, never text or widget content. */
object SamsungCoverPage {
    var diagnostic = "unknown"; private set
    var isSamsungPage = false; private set
    var isNotifications = false; private set
    private var coverWindowId = -1
    private var scrollingUntil = 0L

    fun onEvent(event: AccessibilityEvent) {
        if (event.windowId == coverWindowId && event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED)
            scrollingUntil = SystemClock.uptimeMillis() + 250
    }

    fun isClock(service: NavigationService, displayId: Int): Boolean {
        isSamsungPage = false
        isNotifications = false
        if (SystemClock.uptimeMillis() < scrollingUntil) { diagnostic = "scrolling"; return false }
        val windows = try { service.windowsOnAllDisplays[displayId] } catch (_: RuntimeException) { null }
        if (windows == null) { diagnostic = "no-cover-windows"; return false }
        try {
            // Cached Samsung windows can remain behind Settings or Foldlet.
            // They must never classify a foreground application as a widget page.
            val focused=Platform.focusedCoverPackage(service,displayId)
            if(focused!=null && !samsung(focused)) { diagnostic="not-subhome";return false }
            for (window in windows) {
                if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
                val root = window.root ?: continue
                try {
                    val owner = root.packageName?.toString()
                    if (!samsung(owner)) continue
                    // Only inspect known SystemUI layout IDs, not notification text.
                    val notificationLists=root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/notification_list_recyclerview")
                    val notificationPage=try { notificationLists.any { it.isVisibleToUser && !bounds(it).isEmpty } }
                        finally { notificationLists.forEach(::recycle) }
                    if(notificationPage) {
                        isSamsungPage=true;isNotifications=true;coverWindowId=window.id
                        diagnostic="notifications";return false
                    }
                    var profileSeen = false
                    var clockFrame = false
                    var clockContent = false
                    var visited = 0
                    fun visit(node: AccessibilityNodeInfo) {
                        if (clockFrame && clockContent || ++visited > 128) return
                        if (!samsung(node.packageName?.toString())) return
                        // AOD's resources are loaded as "app" in this firmware;
                        // direct find-by-ID on the SystemUI root cannot resolve them.
                        val id = node.viewIdResourceName?.substringAfter(":id/")
                        if (id == "sub_home_default_page_clock_container" || id == "sub_ui_home_horizontal_vp") profileSeen = true
                        if (!node.isVisibleToUser || bounds(node).isEmpty) return
                        if (id == "sub_home_default_page_clock_container") clockFrame = true
                        if (id == "sub_clock_content_container") clockContent = true
                        // Widgets live below this pager. Their descendants are
                        // unnecessary for clock detection and must not be inspected.
                        if (id == "sub_ui_home_horizontal_vp") return
                        for (i in 0 until node.childCount) {
                            val child = node.getChild(i) ?: continue
                            try { visit(child) } finally { recycle(child) }
                        }
                    }
                    visit(root)
                    if (!profileSeen) continue
                    isSamsungPage = true
                    coverWindowId = window.id
                    val clock = clockFrame && clockContent
                    diagnostic = if (clock) "clock" else "samsung-other"
                    return clock
                } finally { recycle(root) }
            }
            diagnostic = "not-subhome"
            return false
        } catch (_: RuntimeException) {
            diagnostic = "layout-unavailable"
            return false
        }
    }

    private fun samsung(pkg: String?) = pkg == "com.android.systemui" || pkg == "com.samsung.android.app.aodservice"

    /** Move from the left notification page back to Samsung's clock. IDs only. */
    fun showClock(service: NavigationService,displayId: Int) {
        if(isClock(service,displayId) || !isNotifications) return
        runCatching {
            service.windowsOnAllDisplays[displayId]?.forEach { window ->
                val root=window.root ?: return@forEach
                try {
                    if(!samsung(root.packageName?.toString())) return@forEach
                    var visited=0
                    fun visit(node: AccessibilityNodeInfo): Boolean {
                        if(++visited>128) return false
                        if(node.viewIdResourceName?.substringAfter(":id/")=="sub_ui_home_horizontal_vp")
                            return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        for(i in 0 until node.childCount) {
                            val child=node.getChild(i) ?: continue
                            try { if(visit(child)) return true } finally { recycle(child) }
                        }
                        return false
                    }
                    if(visit(root)) return
                } finally { recycle(root) }
            }
        }
    }

    private fun bounds(node: AccessibilityNodeInfo) = Rect().also { node.getBoundsInScreen(it) }
    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo) = node.recycle()
}
