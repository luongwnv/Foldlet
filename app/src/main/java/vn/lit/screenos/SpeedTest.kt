package vn.lit.screenos

import android.os.SystemClock
import java.io.Closeable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/** Picks the lowest-ping public server (Cloudflare or M-Lab) and measures it. Listener calls arrive on a worker thread. */
class SpeedTest(private val listener: (Snapshot) -> Unit) {
    enum class Phase { LATENCY, DOWNLOAD, UPLOAD, DONE, FAILED, STOPPED }

    data class Snapshot(
        val phase: Phase,
        val progress: Float,
        val download: Double? = null,
        val upload: Double? = null,
        val latency: Double? = null,
        val jitter: Double? = null,
        val loss: Double? = null,
        val pingDown: Double? = null,
        val pingUp: Double? = null,
        val server: String? = null,
        val isp: String? = null,
        val transferred: Long = 0,
    )

    private abstract inner class Backend(val host: String, val label: String, val streams: Int, val port: Int = 443) {
        val address: InetAddress by lazy { InetAddress.getByName(host) }
        abstract fun download(total: AtomicLong)
        abstract fun upload(total: AtomicLong)
    }

    private inner class Cloudflare(label: String) : Backend("speed.cloudflare.com", label, 6) {
        override fun download(total: AtomicLong) {
            val connection = open("$CLOUDFLARE/__down?bytes=$CHUNK")
            try {
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (!cancelled.get()) { val read = input.read(buffer); if (read < 0) break; total.addAndGet(read.toLong()) }
                }
            } finally { release(connection) }
        }

        override fun upload(total: AtomicLong) {
            val connection = open("$CLOUDFLARE/__up").apply {
                requestMethod = "POST"; doOutput = true
                setRequestProperty("Content-Type", "application/octet-stream")
                setFixedLengthStreamingMode(UPLOAD_CHUNK)
            }
            try {
                connection.outputStream.use { output ->
                    var sent = 0L
                    while (sent < UPLOAD_CHUNK && !cancelled.get()) {
                        val size = minOf(PAYLOAD.size.toLong(), UPLOAD_CHUNK - sent).toInt()
                        output.write(PAYLOAD, 0, size); sent += size; total.addAndGet(size.toLong())
                    }
                }
                if (!cancelled.get()) connection.responseCode
            } finally { release(connection) }
        }
    }

    /** Speedtest.net (Ookla) server, found through the same server list the Speedtest website uses. */
    private inner class Ookla(private val base: String, host: String, port: Int, label: String) : Backend(host, label, 6, port) {
        override fun download(total: AtomicLong) {
            val connection = open("$base/download?nocache=${System.nanoTime()}&size=$CHUNK")
            try {
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (!cancelled.get()) { val read = input.read(buffer); if (read < 0) break; total.addAndGet(read.toLong()) }
                }
            } finally { release(connection) }
        }

        override fun upload(total: AtomicLong) {
            val connection = open("$base/upload?nocache=${System.nanoTime()}").apply {
                requestMethod = "POST"; doOutput = true
                setRequestProperty("Content-Type", "application/octet-stream")
                setFixedLengthStreamingMode(UPLOAD_CHUNK)
            }
            try {
                connection.outputStream.use { output ->
                    var sent = 0L
                    while (sent < UPLOAD_CHUNK && !cancelled.get()) {
                        val size = minOf(PAYLOAD.size.toLong(), UPLOAD_CHUNK - sent).toInt()
                        output.write(PAYLOAD, 0, size); sent += size; total.addAndGet(size.toLong())
                    }
                }
                if (!cancelled.get()) connection.responseCode
            } finally { release(connection) }
        }
    }

    private fun ooklaServers(limit: Int): List<Backend> {
        val connection = (URL(OOKLA_SERVERS).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000; readTimeout = 4000; useCaches = false
            // The server list rejects requests that do not look like the Speedtest website.
            setRequestProperty("User-Agent", BROWSER)
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Referer", "https://www.speedtest.net/")
            setRequestProperty("Sec-Fetch-Dest", "empty"); setRequestProperty("Sec-Fetch-Mode", "cors"); setRequestProperty("Sec-Fetch-Site", "same-origin")
        }
        try {
            val servers = org.json.JSONArray(connection.inputStream.use { it.readBytes().decodeToString() })
            return (0 until servers.length()).mapNotNull { i ->
                val server = servers.getJSONObject(i)
                val host = server.optString("host").takeIf { it.contains(':') } ?: return@mapNotNull null
                val name = server.optString("name"); val sponsor = server.optString("sponsor")
                val label = listOf(sponsor, name).filter { it.isNotBlank() }.joinToString(", ").ifBlank { host }
                Ookla("https://$host", host.substringBeforeLast(':'), host.substringAfterLast(':').toIntOrNull() ?: 8080, label)
            }.take(limit)
        } finally { connection.disconnect() }
    }

    private inner class MLab(private val server: Ndt7.Server) : Backend(server.host, listOf("M-Lab", listOf(server.city, server.country).filter { it.isNotBlank() }.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" - "), 1) {
        override fun download(total: AtomicLong) {
            val socket = Ndt7.Socket(server.download).also { closers.add(it) }
            try {
                val buffer = ByteArray(64 * 1024)
                while (!cancelled.get()) { val read = socket.read(buffer); if (read < 0) break; total.addAndGet(read.toLong()) }
            } finally { closers.remove(socket); socket.close() }
        }

        override fun upload(total: AtomicLong) {
            val socket = Ndt7.Socket(server.upload).also { closers.add(it) }
            try {
                val buffer = ByteArray(64 * 1024)
                val started = SystemClock.elapsedRealtime()
                while (!cancelled.get() && SystemClock.elapsedRealtime() - started < UPLOAD_MS) {
                    socket.sendBinary(PAYLOAD, PAYLOAD.size); total.addAndGet(PAYLOAD.size.toLong())
                    socket.drain(buffer)
                }
            } finally { closers.remove(socket); socket.close() }
        }
    }

    private val cancelled = AtomicBoolean(false)
    private val closers = CopyOnWriteArrayList<Closeable>()
    private var worker: Thread? = null
    private var state = Snapshot(Phase.LATENCY, 0f)

    fun start() {
        worker = Thread({ run() }, "foldlet-speedtest").apply { isDaemon = true; start() }
    }

    fun cancel() {
        if (!cancelled.compareAndSet(false, true)) return
        closers.forEach { runCatching { it.close() } }
        worker?.interrupt()
    }

    private fun emit(next: Snapshot) { state = next; listener(next) }

    private fun run() {
        try {
            // A server can refuse tests (for example rate limits); fall back to the next lowest ping.
            for (backend in select()) {
                if (cancelled.get()) return emit(state.copy(phase = Phase.STOPPED))
                emit(Snapshot(Phase.LATENCY, .04f, server = backend.label, isp = state.isp))
                try { measure(backend); return } catch (_: IOException) { if (cancelled.get()) break }
            }
            emit(state.copy(phase = if (cancelled.get()) Phase.STOPPED else Phase.FAILED))
        } catch (_: Exception) {
            emit(state.copy(phase = if (cancelled.get()) Phase.STOPPED else Phase.FAILED))
        }
    }

    private fun measure(backend: Backend) {
        latency(backend)
        if (cancelled.get()) return emit(state.copy(phase = Phase.STOPPED))
        val download = transfer(backend, Phase.DOWNLOAD, DOWNLOAD_MS, DOWNLOAD_CAP, .1f, .55f) { backend.download(it) }
        if (cancelled.get()) return emit(state.copy(phase = Phase.STOPPED))
        emit(state.copy(download = download))
        val upload = transfer(backend, Phase.UPLOAD, UPLOAD_MS, UPLOAD_CAP, .55f, 1f) { backend.upload(it) }
        if (cancelled.get()) return emit(state.copy(phase = Phase.STOPPED))
        emit(state.copy(phase = Phase.DONE, progress = 1f, upload = upload))
    }

    /** Lowest TCP connect time wins, like choosing the nearest Speedtest server. */
    private fun select(): List<Backend> {
        var cloudflareLabel = "Cloudflare"
        var isp: String? = null
        runCatching {
            val connection = open("$CLOUDFLARE/meta").apply { setRequestProperty("Referer", "$CLOUDFLARE/") }
            try {
                val json = org.json.JSONObject(connection.inputStream.use { it.readBytes().decodeToString() })
                json.optJSONObject("colo")?.let { colo ->
                    val place = listOf(colo.optString("city"), colo.optString("iata").takeIf { it.isNotBlank() }?.let { "($it)" }).filter { !it.isNullOrBlank() }.joinToString(" ")
                    if (place.isNotBlank()) cloudflareLabel = "Cloudflare - $place"
                }
                isp = json.optString("asOrganization").takeIf { it.isNotBlank() }
            } finally { release(connection) }
        }
        emit(state.copy(isp = isp, progress = .02f))
        val candidates = mutableListOf<Backend>(Cloudflare(cloudflareLabel))
        runCatching { candidates.addAll(ooklaServers(5)) }
        runCatching { Ndt7.locate(3).forEach { candidates.add(MLab(it)) } }
        emit(state.copy(progress = .03f))
        val pings = java.util.concurrent.ConcurrentHashMap<Backend, Double>()
        candidates.map { backend ->
            Thread({ List(3) { ping(backend) }.filterNotNull().minOrNull()?.let { pings[backend] = it } }).apply { isDaemon = true; start() }
        }.forEach { it.join(6000) }
        val ranked = candidates.mapNotNull { backend -> pings[backend]?.let { backend to it } }
        if (ranked.isEmpty()) throw IOException("No reachable server")
        return ranked.sortedBy { it.second }.map { it.first }
    }

    private fun ping(backend: Backend): Double? = try {
        val address = backend.address
        Socket().use { socket ->
            val started = System.nanoTime()
            socket.connect(InetSocketAddress(address, backend.port), 1500)
            (System.nanoTime() - started) / 1_000_000.0
        }
    } catch (_: IOException) { null }

    private fun latency(backend: Backend) {
        val samples = ArrayList<Double>()
        var failed = 0
        for (i in 0 until PROBES) {
            if (cancelled.get()) return
            val ms = ping(backend)
            if (ms != null) samples.add(ms) else failed++
            val sorted = samples.sorted()
            emit(state.copy(phase = Phase.LATENCY, progress = .04f + .06f * (i + 1) / PROBES,
                latency = sorted.getOrNull(sorted.size / 2),
                jitter = if (samples.size > 1) samples.zipWithNext { a, b -> abs(a - b) }.average() else null,
                loss = failed * 100.0 / (i + 1)))
            SystemClock.sleep(100)
        }
        if (samples.isEmpty()) throw IOException("No latency samples")
    }

    private fun transfer(backend: Backend, phase: Phase, duration: Long, cap: Long, from: Float, to: Float, stream: (AtomicLong) -> Unit): Double {
        val total = AtomicLong()
        val started = SystemClock.elapsedRealtime()
        val deadline = started + duration
        val threads = List(backend.streams) {
            Thread({
                while (!cancelled.get() && SystemClock.elapsedRealtime() < deadline && total.get() < cap) {
                    try { stream(total) } catch (_: IOException) { if (!cancelled.get()) SystemClock.sleep(200) }
                }
            }, "foldlet-speedtest-$phase-$it").apply { isDaemon = true; start() }
        }
        // Loaded latency, measured while the transfer saturates the link.
        val loaded = java.util.Collections.synchronizedList(ArrayList<Double>())
        val pinger = Thread({
            while (!cancelled.get() && SystemClock.elapsedRealtime() < deadline) {
                ping(backend)?.let { loaded.add(it) }
                SystemClock.sleep(400)
            }
        }, "foldlet-speedtest-ping").apply { isDaemon = true; start() }
        fun loadedPing() = synchronized(loaded) { loaded.sorted().let { it.getOrNull(it.size / 2) } }
        val windows = ArrayList<Double>()
        var lastBytes = 0L
        var lastAt = started
        var speed = 0.0
        while (!cancelled.get()) {
            SystemClock.sleep(250)
            val now = SystemClock.elapsedRealtime()
            val bytes = total.get()
            if (now > lastAt) windows.add((bytes - lastBytes) * 8.0 / ((now - lastAt) / 1000.0) / 1_000_000.0)
            lastBytes = bytes; lastAt = now
            speed = trimmedMean(windows)
            val fraction = ((now - started).toFloat() / duration).coerceIn(0f, 1f)
            val value = if (phase == Phase.DOWNLOAD) state.copy(download = speed, pingDown = loadedPing() ?: state.pingDown)
                else state.copy(upload = speed, pingUp = loadedPing() ?: state.pingUp)
            emit(value.copy(phase = phase, progress = from + (to - from) * fraction, transferred = bytes))
            if (now >= deadline || bytes >= cap || threads.none { it.isAlive } || (bytes == 0L && now - started > 3000)) break
        }
        closers.forEach { runCatching { it.close() } }
        threads.forEach { it.join(1500) }
        pinger.join(1500)
        if (total.get() == 0L && !cancelled.get()) throw IOException("No data transferred")
        return speed
    }

    /** Like Speedtest: drop the slowest 30% and fastest 10% of sample windows, then average. */
    private fun trimmedMean(samples: List<Double>): Double {
        if (samples.isEmpty()) return 0.0
        val sorted = samples.sorted()
        val from = (sorted.size * .3).toInt(); val to = (sorted.size - (sorted.size * .1).toInt()).coerceAtLeast(from + 1)
        return sorted.subList(from, to.coerceAtMost(sorted.size)).average()
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 5000; readTimeout = 5000; useCaches = false
        setRequestProperty("User-Agent", "Foldlet")
        closers.add(Closeable { disconnect() })
    }

    private fun release(connection: HttpURLConnection) { connection.disconnect() }

    companion object {
        private const val CLOUDFLARE = "https://speed.cloudflare.com"
        private const val OOKLA_SERVERS = "https://www.speedtest.net/api/js/servers?engine=js&limit=10&https_functional=true"
        private const val BROWSER = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/157.0.0.0 Mobile Safari/537.36"
        private const val PROBES = 20
        private const val DOWNLOAD_MS = 10_000L
        private const val UPLOAD_MS = 10_000L
        private const val DOWNLOAD_CAP = 300_000_000L
        private const val UPLOAD_CAP = 150_000_000L
        private const val CHUNK = 25_000_000L
        private const val UPLOAD_CHUNK = 4_000_000L
        private val PAYLOAD = ByteArray(64 * 1024) { (it * 31 + 7).toByte() }
    }
}
