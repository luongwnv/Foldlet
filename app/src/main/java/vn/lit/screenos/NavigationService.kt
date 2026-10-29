package vn.lit.screenos

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class NavigationService : AccessibilityService() {
    enum class BackResult { SENT, LAUNCHER_ROOT, UNAVAILABLE }
    var backRoute = "none"; private set
    fun sendCoverBack(displayId: Int): BackResult {
        val focusedPackage = Platform.focusedCoverPackage(this, displayId)
        if (Build.VERSION.SDK_INT >= 33 && focusedPackage != null) {
            val ime = inputMethod
            val connection = ime?.currentInputConnection
            if (connection != null && ime.currentInputStarted && ime.currentInputEditorInfo?.packageName == focusedPackage) {
                val now = SystemClock.uptimeMillis()
                connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0, 0, -1, 0, KeyEvent.FLAG_SOFT_KEYBOARD, InputDevice.SOURCE_KEYBOARD))
                connection.sendKeyEvent(KeyEvent(now, now + 30, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0, 0, -1, 0, KeyEvent.FLAG_SOFT_KEYBOARD, InputDevice.SOURCE_KEYBOARD))
                backRoute = "accessibility-input"
                return BackResult.SENT
            }
        }
        if (clickAppBack(displayId)) { backRoute="app-navigation"; return BackResult.SENT }
        if (focusedPackage != null && RecentApps.isLauncherRoot(this,focusedPackage)) {
            backRoute="launcher-root"; return BackResult.LAUNCHER_ROOT
        }
        backRoute="no-back-target"; return BackResult.UNAVAILABLE
    }
    private fun clickAppBack(displayId: Int): Boolean {
        val root = windowsOnAllDisplays[displayId]?.firstOrNull { it.isFocused }?.root ?: return false
        @Suppress("DEPRECATION")
        try {
            if(root.actionList.any { it.id == android.view.accessibility.AccessibilityNodeInfo.ACTION_DISMISS } && root.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_DISMISS)) return true
            var visited=0
            fun visit(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
                if(++visited>128 || !node.isVisibleToUser) return false
                val id=node.viewIdResourceName
                val role=node.className?.toString().orEmpty()
                val navigationId=id=="android:id/home" || id?.endsWith(":id/action_bar_up")==true
                val button=role.endsWith("ImageButton") || role.endsWith("Button")
                val label=if(button) node.contentDescription?.toString()?.trim()?.lowercase(java.util.Locale.ROOT) else null
                if(node.isClickable && (navigationId || label in setOf("navigate up","back","quay lại","trở về")) && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return true
                for(i in 0 until node.childCount) {
                    val child=node.getChild(i) ?: continue
                    try { if(visit(child)) return true } finally { child.recycle() }
                }
                return false
            }
            return visit(root)
        } finally { root.recycle() }
    }
    override fun onServiceConnected() { super.onServiceConnected(); current = this; CoverMonitorService.current?.schedule() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() in setOf("com.android.systemui", "com.samsung.android.app.aodservice") || event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            SamsungCoverPage.onEvent(event)
            CoverMonitorService.current?.schedule()
        }
    }
    override fun onInterrupt() = Unit
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (current === this) current = null
        CoverMonitorService.current?.schedule()
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        if (current === this) current = null
        CoverMonitorService.current?.schedule()
        super.onDestroy()
    }
    companion object { var current: NavigationService? = null; private set }
}
