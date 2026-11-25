package vn.lit.screenos

import android.os.SystemClock
import java.io.IOException
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Counts hotspot clients by probing the hotspot subnet. Android exposes no client list to ordinary apps,
 * so a device counts while it answered a ping or a TCP probe recently.
 */
object HotspotClients {
    private const val SEEN_MS = 30_000L
    private val ports = intArrayOf(62078, 445, 80)
    private val lastSeen = ConcurrentHashMap<String, Long>()
    private var subnet: String? = null

    /** Latest scan result, kept while the hotspot stays on so a new screen shows it immediately. */
    @Volatile var lastCount: Int? = null
        private set

    /** Blocking scan; returns null when the hotspot subnet cannot be determined. */
    fun scan(interfaceName: String): Int? {
        val network = runCatching { NetworkInterface.getByName(interfaceName) }.getOrNull() ?: return null
        val own = network.interfaceAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        val bytes = own.address.address
        val base = "${bytes[0].toInt() and 0xFF}.${bytes[1].toInt() and 0xFF}.${bytes[2].toInt() and 0xFF}"
        if (base != subnet) { lastSeen.clear(); subnet = base }
        val self = bytes[3].toInt() and 0xFF
        val pool = Executors.newFixedThreadPool(64)
        for (host in 1..254) if (host != self) pool.execute {
            val address = "$base.$host"
            if (alive(network, address)) lastSeen[address] = SystemClock.elapsedRealtime()
        }
        pool.shutdown()
        pool.awaitTermination(20, TimeUnit.SECONDS)
        val now = SystemClock.elapsedRealtime()
        lastSeen.entries.removeIf { now - it.value > SEEN_MS }
        lastCount = lastSeen.size
        return lastSeen.size
    }

    fun reset() { lastSeen.clear(); subnet = null; lastCount = null }

    private fun alive(network: NetworkInterface, address: String): Boolean {
        val target = runCatching { InetAddress.getByName(address) }.getOrNull() ?: return false
        if (runCatching { target.isReachable(network, 0, 500) }.getOrDefault(false)) return true
        for (port in ports) {
            try {
                Socket().use { it.connect(InetSocketAddress(target, port), 300) }
                return true
            } catch (error: ConnectException) {
                // Refused means a host answered with a reset: it is on the network.
                if (error.message.orEmpty().contains("refused", ignoreCase = true)) return true
            } catch (_: IOException) {}
        }
        return false
    }
}
