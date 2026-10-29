package vn.lit.screenos

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class NavigationService : AccessibilityService() {
    override fun onServiceConnected() { super.onServiceConnected(); current = this; CoverMonitorService.current?.schedule() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
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
