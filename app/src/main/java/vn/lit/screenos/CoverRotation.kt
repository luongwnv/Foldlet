package vn.lit.screenos

import android.content.Context
import android.os.*
import android.view.Display
import org.json.JSONObject
import java.net.*
import java.util.concurrent.Executors

/** Authenticated localhost channel to the limited helper started over tethered ADB. */
object CoverRotation {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var generation = 0
    private var appContext: Context? = null
    var mode = "auto"; private set
    var available = false; private set
    val authorized get() = appContext?.let { Platform.preferences(it).getString("adb_bridge_token",null) != null } ?: false
    fun initialize(context: Context) { appContext = context.applicationContext }
    fun invalidate() { generation++; available = false; mode = "auto" }

    private fun exchange(context: Context, op: String, requested: String? = null): Boolean {
        val prefs = Platform.preferences(context)
        val port = prefs.getInt("adb_bridge_port",0)
        val token = prefs.getString("adb_bridge_token",null) ?: return false
        if (port !in 1024..65535) return false
        return try {
            Socket().use { socket ->
                // No host/IP is accepted from preferences or IPC: loopback only.
                socket.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"),port),700)
                socket.soTimeout = 6000
                val message = JSONObject().put("auth",token).put("op",op)
                if (requested != null) message.put("mode",requested)
                socket.getOutputStream().write((message.toString()+"\n").toByteArray())
                socket.getOutputStream().flush()
                val reader = socket.getInputStream().bufferedReader()
                val response = StringBuilder()
                while (response.length < 2048) { val next = reader.read(); if (next < 0 || next == 10) break; response.append(next.toChar()) }
                JSONObject(response.toString()).optBoolean("ok",false)
            }
        } catch (_: Exception) { false }
    }
    fun probe(context: Context, complete: (Boolean) -> Unit = {}) {
        initialize(context)
        io.execute { val ready = exchange(context.applicationContext,"ping"); main.post { available=ready; complete(ready) } }
    }
    fun prepare(context: Context, displayId: Int, requested: String, complete: (Boolean) -> Unit) {
        initialize(context)
        if (requested == "auto") { complete(true); return }
        if (displayId != 1 && !Platform.isEmulatorOverlay(context)) { complete(false); return }
        val request = generation
        probe(context) { ready ->
            if (request != generation) { complete(false); return@probe }
            if (!ready) Platform.message(context,"Cắm máy tính và chạy adb_rotation_bridge.py để dùng xoay ứng dụng")
            complete(ready)
        }
    }
    fun apply(context: Context, displayId: Int, requested: String, complete: (Boolean) -> Unit = {}) {
        initialize(context)
        if (requested !in setOf("auto","portrait","landscape")) { complete(false); return }
        if (displayId == Display.DEFAULT_DISPLAY && !Platform.isEmulatorOverlay(context)) { complete(false); return }
        val request = ++generation
        if (requested == "auto" && !authorized) { mode="auto"; complete(true); return }
        io.execute {
            if (request != generation) { main.post { complete(false) }; return@execute }
            val success = exchange(context.applicationContext,if (requested == "auto") "restore" else "rotate",requested)
            main.post {
                if (request != generation) { complete(false); return@post }
                available = success
                if (success) mode = requested else if (requested != "auto") Platform.message(context,"Kết nối ADB hỗ trợ xoay đã ngắt")
                complete(success)
            }
        }
    }
    fun clear(complete: () -> Unit = {}) {
        val request = ++generation
        val context = appContext
        if (context == null || !authorized) { mode="auto"; complete(); return }
        io.execute {
            val success = exchange(context,"restore")
            main.post { if (request == generation && success) mode="auto"; available=success; complete() }
        }
    }
}
