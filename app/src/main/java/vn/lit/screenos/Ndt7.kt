package vn.lit.screenos

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.SecureRandom
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Minimal client for M-Lab's ndt7 protocol (RFC 6455 WebSocket over TLS). */
object Ndt7 {
    data class Server(val host: String, val city: String, val country: String, val download: String, val upload: String)

    private const val LOCATE = "https://locate.measurementlab.net/v2/nearest/ndt/ndt7?client_name=foldlet&client_version=${BuildConfig.VERSION_NAME}"
    private const val PROTOCOL = "net.measurementlab.ndt.v7"

    fun locate(limit: Int): List<Server> {
        val connection = URL(LOCATE).openConnection() as HttpURLConnection
        connection.connectTimeout = 4000; connection.readTimeout = 4000
        try {
            val results = org.json.JSONObject(connection.inputStream.use { it.readBytes().decodeToString() }).optJSONArray("results") ?: return emptyList()
            return (0 until results.length()).mapNotNull { i ->
                val result = results.getJSONObject(i)
                val urls = result.optJSONObject("urls") ?: return@mapNotNull null
                val download = urls.optString("wss:///ndt/v7/download").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val upload = urls.optString("wss:///ndt/v7/upload").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val location = result.optJSONObject("location")
                Server(URI(download).host, location?.optString("city").orEmpty(), location?.optString("country").orEmpty(), download, upload)
            }.take(limit)
        } finally { connection.disconnect() }
    }

    class Socket(url: String) : Closeable {
        private val uri = URI(url)
        private val socket = (SSLSocketFactory.getDefault().createSocket(uri.host, if (uri.port > 0) uri.port else 443) as SSLSocket).apply { soTimeout = 15_000 }
        private val input: DataInputStream
        private val output: OutputStream
        private val random = SecureRandom()

        init {
            try {
                socket.startHandshake()
                if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(uri.host, socket.session)) throw IOException("Hostname mismatch")
                input = DataInputStream(BufferedInputStream(socket.inputStream, 64 * 1024))
                output = socket.outputStream
                val key = Base64.getEncoder().encodeToString(ByteArray(16).also { random.nextBytes(it) })
                output.write(("GET ${uri.rawPath}?${uri.rawQuery} HTTP/1.1\r\nHost: ${uri.host}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: $PROTOCOL\r\nUser-Agent: foldlet/${BuildConfig.VERSION_NAME}\r\n\r\n").toByteArray())
                output.flush()
                val status = line()
                if (!status.startsWith("HTTP/1.1 101")) throw IOException("Upgrade refused: $status")
                while (line().isNotEmpty()) Unit
            } catch (error: Exception) { runCatching { socket.close() }; throw error }
        }

        private fun line(): String {
            val text = StringBuilder()
            while (true) {
                val c = input.read()
                if (c < 0) throw IOException("Closed")
                if (c == '\n'.code) return text.toString().trimEnd('\r')
                text.append(c.toChar())
            }
        }

        /** Reads one frame. Returns the binary byte count, or -1 when the server closed the test. */
        fun read(buffer: ByteArray): Int {
            val first = input.readUnsignedByte(); val second = input.readUnsignedByte()
            val opcode = first and 0x0F
            var length = (second and 0x7F).toLong()
            if (length == 126L) length = input.readUnsignedShort().toLong() else if (length == 127L) length = input.readLong()
            if (second and 0x80 != 0) input.skipBytes(4)
            var remaining = length
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) throw IOException("Closed")
                remaining -= read
            }
            return when (opcode) { 0x8 -> -1; 0x2, 0x0 -> length.toInt(); else -> 0 }
        }

        fun sendBinary(payload: ByteArray, size: Int) {
            val header = java.io.ByteArrayOutputStream(14)
            header.write(0x82)
            when {
                size < 126 -> header.write(0x80 or size)
                size < 65_536 -> { header.write(0x80 or 126); header.write(size ushr 8); header.write(size and 0xFF) }
                else -> { header.write(0x80 or 127); for (shift in 56 downTo 0 step 8) header.write(((size.toLong() ushr shift) and 0xFF).toInt()) }
            }
            val mask = ByteArray(4).also { random.nextBytes(it) }
            header.write(mask)
            val masked = ByteArray(size) { (payload[it].toInt() xor mask[it and 3].toInt()).toByte() }
            output.write(header.toByteArray()); output.write(masked)
        }

        fun drain(buffer: ByteArray) { while (input.available() > 0) read(buffer) }

        override fun close() {
            runCatching { output.write(byteArrayOf(0x88.toByte(), 0x80.toByte(), 0, 0, 0, 0)); output.flush() }
            runCatching { socket.close() }
        }
    }
}
