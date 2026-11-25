package vn.lit.screenos

import android.content.Context
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import java.net.Inet4Address

/**
 * Connected Wi-Fi name without Location: read from Samsung's Wi-Fi settings page through Accessibility,
 * then shown only while the phone stays on the same network.
 */
object WifiName {
    private const val NAME = "wifi_name"
    private const val NETWORK = "wifi_name_network"
    private const val FINGERPRINT = "wifi_name_fingerprint"

    private data class Identity(val network: String, val fingerprint: String)

    private fun identity(context: Context): Identity? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return null
        if (connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return null
        val link = connectivity.getLinkProperties(network) ?: return Identity(network.toString(), "")
        val gateway = link.routes.filter { it.isDefaultRoute }.mapNotNull { it.gateway?.hostAddress }.sorted().joinToString(",")
        val prefix = link.linkAddresses.filter { it.address is Inet4Address }.joinToString(",") { it.prefixLength.toString() }
        val dhcp = if (Build.VERSION.SDK_INT >= 30) link.dhcpServerAddress?.hostAddress.orEmpty() else ""
        return Identity(network.toString(), "$gateway|$prefix|$dhcp")
    }

    fun current(context: Context): String? {
        val now = identity(context) ?: return null
        val prefs = Platform.preferences(context)
        val name = prefs.getString(NAME, null) ?: return null
        val fingerprint = prefs.getString(FINGERPRINT, "").orEmpty()
        val sameNetwork = prefs.getString(NETWORK, null) == now.network || (fingerprint.length > 2 && fingerprint == now.fingerprint)
        return name.takeIf { sameNetwork }
    }

    /** Reads the name under Samsung's "Current network" header. */
    fun scan(context: Context, root: AccessibilityNodeInfo, ids: (String) -> List<String>) {
        val category = root.findAccessibilityNodeInfosByViewId("${HotspotWifiReader.PACKAGE}:id/connected_network_category").firstOrNull() ?: return
        val header = Rect().also { category.getBoundsInScreen(it) }
        val limit = header.height() * 3
        val title = ids("title").flatMap { root.findAccessibilityNodeInfosByViewId(it) }
            .map { node -> node to Rect().also { node.getBoundsInScreen(it) } }
            .filter { (_, bounds) -> bounds.top >= header.bottom && bounds.top - header.bottom < limit }
            .minByOrNull { it.second.top }?.first ?: return
        val name = title.text?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val now = identity(context) ?: return
        val prefs = Platform.preferences(context)
        if (prefs.getString(NAME, null) == name && prefs.getString(NETWORK, null) == now.network) return
        prefs.edit().putString(NAME, name).putString(NETWORK, now.network).putString(FINGERPRINT, now.fingerprint).apply()
    }
}
