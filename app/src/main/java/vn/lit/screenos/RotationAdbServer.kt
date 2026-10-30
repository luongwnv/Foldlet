package vn.lit.screenos

import android.os.Process
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.*
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Started by the user's tethered ADB session, with a separate owner-only lease. */
object RotationAdbServer {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size in 3..4)
        val port = args[0].toInt(); require(port in 1024..65535)
        val appToken = args[1]; val ownerToken = args[2]
        val targetDisplay = args.getOrNull(3)?.toInt() ?: 1
        require((targetDisplay == 1 && android.os.Build.MODEL.startsWith("SM-F711")) || (BuildConfig.DEBUG && targetDisplay == 0 && android.os.Build.MODEL.startsWith("sdk_gphone")))
        require(appToken.length >= 32 && ownerToken.length >= 32 && appToken != ownerToken)
        val leaseFile=RandomAccessFile("/data/local/tmp/lit-screenos-adb-rotation-${Process.myUid()}.lock","rw")
        val leaseLock=leaseFile.channel.tryLock() ?: run { leaseFile.close();error("A rotation helper is still stopping") }
        val rotation = RotationShellService(File("/data/local/tmp/lit-screenos-adb-rotation-${Process.myUid()}"))
        val running = AtomicBoolean(true)
        val lastHeartbeat = AtomicLong(SystemClock.elapsedRealtime())
        val leased = AtomicBoolean(false)
        val listener = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"),port)) }
        Runtime.getRuntime().addShutdownHook(Thread { try { rotation.restore() } catch (_: Exception) { } })
        fun equal(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(),b.toByteArray())
        Thread {
            while (running.get()) {
                Thread.sleep(250)
                val timeout = if (leased.get()) 4000 else 15000
                if (SystemClock.elapsedRealtime() - lastHeartbeat.get() > timeout) {
                    running.set(false); listener.close()
                }
            }
        }.apply { isDaemon = true; start() }
        println("LIT ADB bridge ready on localhost:$port")
        try {
            while (running.get()) {
                val socket = try { listener.accept() } catch (_: SocketException) { break }
                Thread {
                    socket.use { connection ->
                        connection.soTimeout = 3000
                        val response = try {
                            val reader = connection.getInputStream().bufferedReader()
                            val line = StringBuilder()
                            while (line.length < 2048) { val next = reader.read(); if (next < 0 || next == 10) break; line.append(next.toChar()) }
                            val request = JSONObject(line.toString())
                            val token = request.optString("auth")
                            val op = request.optString("op")
                            val owner = equal(token,ownerToken)
                            require(owner || equal(token,appToken))
                            when (op) {
                                "heartbeat" -> { require(owner); leased.set(true); lastHeartbeat.set(SystemClock.elapsedRealtime()); JSONObject().put("ok",true) }
                                "stop" -> { require(owner); synchronized(rotation) { running.set(false); rotation.restore() }; listener.close(); JSONObject().put("ok",true) }
                                "ping" -> JSONObject().put("ok",running.get()).put("sensorRotation",true)
                                "restore" -> synchronized(rotation) { check(running.get()); rotation.restore(); JSONObject().put("ok",true) }
                                "rotate" -> synchronized(rotation) { check(running.get()); JSONObject().put("ok",true).put("result",rotation.rotate(targetDisplay,request.getString("mode"),request.optInt("rotation",-1))) }
                                else -> error("Unsupported operation")
                            }
                        } catch (_: Exception) { JSONObject().put("ok",false).put("error","Request rejected") }
                        try { connection.getOutputStream().write((response.toString()+"\n").toByteArray()); connection.getOutputStream().flush() } catch (_: Exception) { }
                    }
                }.apply { isDaemon = true; start() }
            }
        } finally { running.set(false); listener.close();try { rotation.restore() } finally { leaseLock.release();leaseFile.close() } }
    }
}
