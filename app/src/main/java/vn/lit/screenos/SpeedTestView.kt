package vn.lit.screenos

import android.content.Context
import android.graphics.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

/** Cover-sized speed test with server, loaded ping and on-device history. */
class SpeedTestView(
    context: Context,
    private val autoStart: Boolean = false,
    private val onClose: (() -> Unit)? = null,
    private val onActivity: () -> Unit = {},
) : LinearLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val bold = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private var test: SpeedTest? = null
    private var testNetwork = ""
    private var saved = false
    private var showingHistory = false

    private val header = small("", 13f, Ui.muted).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val upload = big()
    private val download = big()
    private val ping = stat()
    private val pingDown = stat()
    private val pingUp = stat()
    private val jitter = stat()
    private val loss = stat()
    private val results = LinearLayout(context)
    private val history = LinearLayout(context).apply { orientation = VERTICAL }
    private val historyScroll = ScrollView(context).apply { addView(history); visibility = GONE; isVerticalScrollBarEnabled = false }
    private val status = small("", 14f, ORANGE).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val bar = ChevronBar(context)
    private val action = RoundButton(context, RoundButton.Icon.PLAY) { toggle() }
    private val historyButton = RoundButton(context, RoundButton.Icon.HISTORY) { toggleHistory() }

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.BLACK)
        setPadding(Ui.dp(context, 18), Ui.dp(context, 4), Ui.dp(context, 16), Ui.dp(context, 4))
        addView(header, LayoutParams(-1, -2))
        val buttons = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER }
        fun button(view: View) = buttons.addView(view, LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44)).apply { setMargins(0, Ui.dp(context, 3), 0, Ui.dp(context, 3)) })
        button(action)
        button(historyButton)
        onClose?.let { close -> button(RoundButton(context, RoundButton.Icon.CLOSE) { test?.cancel(); close() }) }
        val speeds = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER }
        speeds.addView(speedRow(upload, true, R.string.ui_speedtest_upload))
        speeds.addView(speedRow(download, false, R.string.ui_speedtest_download))
        results.addView(speeds, LayoutParams(0, -1, 1f))
        results.addView(LinearLayout(context).apply {
            orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL
            addView(statRow(R.string.ui_speedtest_ping, ping, "ms"))
            addView(statRow(R.string.ui_speedtest_ping_down, pingDown, "ms"))
            addView(statRow(R.string.ui_speedtest_ping_up, pingUp, "ms"))
            addView(statRow(R.string.ui_speedtest_jitter, jitter, "ms"))
            addView(statRow(R.string.ui_speedtest_loss, loss, "%"))
        }, LayoutParams(Ui.dp(context, 150), -1))
        addView(LinearLayout(context).apply {
            addView(buttons, LayoutParams(Ui.dp(context, 50), -1))
            addView(FrameLayout(context).apply {
                addView(results, FrameLayout.LayoutParams(-1, -1))
                addView(historyScroll, FrameLayout.LayoutParams(-1, -1))
            }, LayoutParams(0, -1, 1f))
        }, LayoutParams(-1, 0, 1f))
        addView(status, LayoutParams(-1, -2).apply { topMargin = Ui.dp(context, 2) })
        addView(bar, LayoutParams(-1, Ui.dp(context, 12)).apply { topMargin = Ui.dp(context, 3); bottomMargin = Ui.dp(context, 2) })
        render(null)
    }

    private fun small(text: String, size: Float, color: Int) = TextView(context).apply {
        this.text = text; textSize = size; setTextColor(color); includeFontPadding = false
    }

    private fun big() = Ui.text(context, "-", 54f).apply { typeface = bold; includeFontPadding = false; setPadding(0, 0, 0, 0) }

    private fun stat() = small("-", 17f, Color.WHITE).apply { typeface = bold }

    private fun speedRow(value: TextView, up: Boolean, label: Int) = LinearLayout(context).apply {
        gravity = Gravity.CENTER; contentDescription = I18n.text(label)
        addView(value)
        addView(LinearLayout(context).apply {
            orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            addView(HotspotView.Arrow(context, up), LayoutParams(Ui.dp(context, 20), Ui.dp(context, 20)))
            addView(small("Mbps", 14f, Ui.muted), LayoutParams(-2, -2))
        }, LayoutParams(-2, -2).apply { leftMargin = Ui.dp(context, 8) })
    }

    private fun statRow(label: Int, value: TextView, unit: String) = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(small(I18n.text(label), 13f, Ui.muted).apply { maxLines = 1 }, LayoutParams(0, -2, 1f))
        addView(value)
        addView(small(unit, 12f, Ui.muted), LayoutParams(Ui.dp(context, 22), -2).apply { leftMargin = Ui.dp(context, 3) })
        setPadding(0, Ui.dp(context, 3), 0, Ui.dp(context, 3))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (autoStart && test == null) toggle()
    }

    override fun onDetachedFromWindow() {
        test?.cancel(); test = null
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    private fun toggle() {
        onActivity()
        val running = test
        if (running != null) { running.cancel(); return }
        val network = network()
        if (network == null) { status.text = I18n.text(R.string.ui_speedtest_no_network); return }
        if (showingHistory) toggleHistory()
        testNetwork = network; saved = false
        val next = SpeedTest { snapshot -> handler.post { if (test != null) render(snapshot) } }
        test = next
        keepScreenOn = true
        render(SpeedTest.Snapshot(SpeedTest.Phase.LATENCY, 0f))
        next.start()
    }

    private fun toggleHistory() {
        onActivity()
        showingHistory = !showingHistory
        results.visibility = if (showingHistory) GONE else VISIBLE
        historyScroll.visibility = if (showingHistory) VISIBLE else GONE
        historyButton.active = showingHistory
        if (showingHistory) fillHistory()
    }

    private fun fillHistory() {
        history.removeAllViews()
        val entries = SpeedTestHistory.read(context)
        if (entries.isEmpty()) { history.addView(small(I18n.text(R.string.ui_speedtest_history_empty), 15f, Ui.muted).apply { setPadding(0, Ui.dp(context, 12), 0, 0) }); return }
        val time = DateFormat.getTimeInstance(DateFormat.SHORT); val date = DateFormat.getDateInstance(DateFormat.SHORT)
        for (entry in entries) history.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(0, Ui.dp(context, 5), 0, Ui.dp(context, 5))
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                addView(small("${time.format(Date(entry.time))}  ${date.format(Date(entry.time))}", 14f, Color.WHITE))
                addView(small(listOfNotNull(entry.network, entry.server).joinToString(" - "), 12f, Ui.muted).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            }, LayoutParams(0, -2, 1f))
            addView(small("↓ ${format(entry.download)}  ↑ ${format(entry.upload)}", 17f, Color.WHITE).apply { typeface = bold })
            addView(small(entry.ping?.let { "  ${format(it)} ms" } ?: "", 13f, Ui.muted))
        })
    }

    private fun network(): String? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return null
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> I18n.text(R.string.ui_speedtest_wifi)
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> I18n.text(R.string.ui_speedtest_mobile)
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> I18n.text(R.string.ui_speedtest_network)
        }
    }

    private fun format(value: Double?, decimals: Int = 1) = value?.let { if (it >= 100) "%.0f".format(it) else "%.${decimals}f".format(it) } ?: "-"

    private fun render(snapshot: SpeedTest.Snapshot?) {
        upload.text = format(snapshot?.upload)
        download.text = format(snapshot?.download)
        ping.text = format(snapshot?.latency)
        pingDown.text = format(snapshot?.pingDown)
        pingUp.text = format(snapshot?.pingUp)
        jitter.text = format(snapshot?.jitter)
        loss.text = format(snapshot?.loss, 0)
        header.text = listOfNotNull(snapshot?.server ?: I18n.text(R.string.ui_speedtest_nearest), snapshot?.isp).joinToString(" - ")
        val phase = snapshot?.phase
        val finished = phase == null || phase == SpeedTest.Phase.DONE || phase == SpeedTest.Phase.FAILED || phase == SpeedTest.Phase.STOPPED
        if (finished && snapshot != null) { test = null; keepScreenOn = false }
        if (phase == SpeedTest.Phase.DONE && !saved && snapshot.download != null && snapshot.upload != null) {
            saved = true
            SpeedTestHistory.add(context, SpeedTestHistory.Entry(System.currentTimeMillis(), testNetwork, snapshot.server, snapshot.download, snapshot.upload, snapshot.latency))
        }
        action.icon = when { phase == null -> RoundButton.Icon.PLAY; finished -> RoundButton.Icon.RESTART; else -> RoundButton.Icon.STOP }
        status.setTextColor(if (finished) Ui.muted else ORANGE)
        status.text = when (phase) {
            null -> I18n.text(R.string.ui_speedtest_ready, network() ?: I18n.text(R.string.ui_speedtest_network))
            SpeedTest.Phase.LATENCY -> I18n.text(R.string.ui_speedtest_latency_phase)
            SpeedTest.Phase.DOWNLOAD -> I18n.text(R.string.ui_speedtest_download_phase) + " - " + HotspotView.bytes(snapshot.transferred)
            SpeedTest.Phase.UPLOAD -> I18n.text(R.string.ui_speedtest_upload_phase) + " - " + HotspotView.bytes(snapshot.transferred)
            SpeedTest.Phase.DONE -> I18n.text(R.string.ui_speedtest_done, testNetwork)
            SpeedTest.Phase.FAILED -> I18n.text(R.string.ui_speedtest_failed)
            SpeedTest.Phase.STOPPED -> I18n.text(R.string.ui_speedtest_stopped)
        }
        bar.update(snapshot?.progress ?: 0f, when (phase) {
            SpeedTest.Phase.LATENCY -> .1f
            SpeedTest.Phase.DOWNLOAD -> .55f
            SpeedTest.Phase.UPLOAD -> 1f
            else -> 0f
        })
        if (!finished) onActivity()
    }

    private class ChevronBar(context: Context) : View(context) {
        private var progress = 0f
        private var phaseEnd = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        fun update(value: Float, end: Float) { progress = value; phaseEnd = end; invalidate() }
        override fun onDraw(canvas: Canvas) {
            val h = height.toFloat(); val step = h * .62f
            val count = (width / step).toInt().coerceAtLeast(1)
            for (i in 0 until count) {
                val position = (i + .5f) / count
                paint.color = when {
                    position < progress -> BLUE
                    position < progress + 1f / count && phaseEnd > 0f -> ORANGE
                    position < phaseEnd -> ORANGE_LIGHT
                    else -> GRAY
                }
                val x = i * step
                path.reset()
                path.moveTo(x, 0f); path.lineTo(x + step * .55f, 0f); path.lineTo(x + step, h / 2)
                path.lineTo(x + step * .55f, h); path.lineTo(x, h); path.lineTo(x + step * .45f, h / 2); path.close()
                canvas.drawPath(path, paint)
            }
        }
    }

    private class RoundButton(context: Context, initial: Icon, action: () -> Unit) : View(context) {
        enum class Icon { PLAY, STOP, RESTART, CLOSE, HISTORY }
        var icon = initial
            set(value) { if (field != value) { field = value; invalidate(); contentDescription = value.name } }
        var active = false
            set(value) { field = value; invalidate() }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        init { isClickable = true; contentDescription = initial.name; setOnClickListener { action() } }
        override fun onDraw(canvas: Canvas) {
            val r = minOf(width, height) / 2f; val cx = width / 2f; val cy = height / 2f
            ring.strokeWidth = r * .08f; ring.color = if (active) BLUE else 0xFF5A5A5A.toInt()
            canvas.drawCircle(cx, cy, r - ring.strokeWidth, ring)
            val s = r * .36f
            glyph.strokeWidth = r * .12f
            when (icon) {
                Icon.PLAY -> { glyph.style = Paint.Style.FILL; canvas.drawPath(Path().apply { moveTo(cx - s * .6f, cy - s); lineTo(cx + s, cy); lineTo(cx - s * .6f, cy + s); close() }, glyph) }
                Icon.STOP -> { glyph.style = Paint.Style.FILL; canvas.drawRect(cx - s * .7f, cy - s * .8f, cx - s * .2f, cy + s * .8f, glyph); canvas.drawRect(cx + s * .2f, cy - s * .8f, cx + s * .7f, cy + s * .8f, glyph) }
                Icon.RESTART -> {
                    val radius = s * .95f
                    glyph.style = Paint.Style.STROKE
                    canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius, 20f, 280f, false, glyph)
                    // Arrowhead along the clockwise tangent at the arc's end.
                    val angle = Math.toRadians(300.0)
                    val cos = kotlin.math.cos(angle).toFloat(); val sin = kotlin.math.sin(angle).toFloat()
                    val px = cx + radius * cos; val py = cy + radius * sin
                    val head = s * .6f
                    glyph.style = Paint.Style.FILL
                    canvas.drawPath(Path().apply {
                        moveTo(px - sin * head, py + cos * head)
                        lineTo(px + cos * head * .75f, py + sin * head * .75f)
                        lineTo(px - cos * head * .75f, py - sin * head * .75f)
                        close()
                    }, glyph)
                }
                Icon.CLOSE -> { glyph.style = Paint.Style.STROKE; canvas.drawLine(cx - s * .7f, cy - s * .7f, cx + s * .7f, cy + s * .7f, glyph); canvas.drawLine(cx + s * .7f, cy - s * .7f, cx - s * .7f, cy + s * .7f, glyph) }
                Icon.HISTORY -> {
                    glyph.style = Paint.Style.STROKE
                    canvas.drawCircle(cx, cy, s, glyph)
                    canvas.drawLine(cx, cy, cx, cy - s * .6f, glyph); canvas.drawLine(cx, cy, cx + s * .45f, cy + s * .25f, glyph)
                }
            }
        }
    }

    companion object {
        private const val BLUE = 0xFF3D6BE0.toInt()
        private const val ORANGE = 0xFFE8892B.toInt()
        private const val ORANGE_LIGHT = 0x66E8892B
        private const val GRAY = 0xFF3A3A3A.toInt()
    }
}
