package vn.lit.screenos

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Saves the hotspot name and password shown on Samsung's Mobile Hotspot settings page. */
object HotspotWifiReader {
    const val PACKAGE = "com.android.settings"
    private val nameLabels = setOf("network name", "tên mạng", "ssid")
    private val passwordLabels = setOf("password", "mật khẩu")
    private val markers = listOf("$PACKAGE:id/mobile_data_used_progress_bar", "$PACKAGE:id/qr_code_button")
    private var hotspotWindow = -1
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun onEvent(service: AccessibilityService, event: AccessibilityEvent) {
        if (event.packageName?.toString() != PACKAGE) return
        pending?.let { handler.removeCallbacks(it) }
        // Scan after events settle so the final scroll position is always read.
        pending = Runnable { pending = null; scanWindows(service) }.also { handler.postDelayed(it, 300) }
    }

    private fun scanWindows(service: AccessibilityService) {
        val all = try { service.windowsOnAllDisplays } catch (_: RuntimeException) { return }
        for (i in 0 until all.size()) for (window in all.valueAt(i)) {
            val root = window.root ?: continue
            try { if (root.packageName?.toString() == PACKAGE) scan(service, window.id, root) } finally { @Suppress("DEPRECATION") root.recycle() }
        }
    }

    private fun scan(service: AccessibilityService, windowId: Int, root: AccessibilityNodeInfo) {
        if (markers.any { root.findAccessibilityNodeInfosByViewId(it).isNotEmpty() }) hotspotWindow = windowId
        else if (windowId != hotspotWindow) return
        var ssid: String? = null
        var password: String? = null
        for (title in ids("title").flatMap { root.findAccessibilityNodeInfosByViewId(it) }) {
            val label = title.text?.toString()?.trim()?.lowercase() ?: continue
            if (label !in nameLabels && label !in passwordLabels) continue
            val value = summary(title)?.trim() ?: continue
            if (value.isEmpty()) continue
            if (label in nameLabels) ssid = value
            else if (value.any { it !in "•*●" }) password = value
        }
        if (ssid == null && password == null) return
        val current = HotspotWifi.read(service)
        val updated = HotspotWifi(ssid ?: current.ssid, password ?: current.password)
        if (updated != current && updated.configured) {
            updated.save(service)
            CoverMonitorService.current?.reloadHomeControls()
        }
    }

    private fun ids(name: String) = listOf("android:id/$name", "$PACKAGE:id/$name")

    private fun summary(title: AccessibilityNodeInfo): String? {
        var parent = title.parent
        repeat(3) {
            val node = parent ?: return null
            ids("summary").firstNotNullOfOrNull { node.findAccessibilityNodeInfosByViewId(it).firstOrNull()?.text }?.let { return it.toString() }
            parent = node.parent
        }
        return null
    }
}
