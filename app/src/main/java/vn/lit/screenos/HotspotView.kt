package vn.lit.screenos

import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import java.net.NetworkInterface
import java.util.Calendar
import java.util.concurrent.Executors

/** Pocket Wi-Fi style cover screen: live speed, data usage and hotspot status pages. */
class HotspotView(context: Context, private val onShortcuts: () -> Unit, private val onExit: () -> Unit) : FrameLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val pages = FrameLayout(context)
    private val dots = LinearLayout(context).apply { gravity = Gravity.CENTER }
    private var page = 0
    private val pageCount = 4
    private var networkLabel = ""
    private var displayCallback: Any? = null
    private val io = Executors.newSingleThreadExecutor()

    private val condensed = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val light = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val upload = Ui.text(context, "0.0", 64f).apply { typeface = condensed; includeFontPadding = false }
    private val download = Ui.text(context, "0.0", 64f).apply { typeface = condensed; includeFontPadding = false }
    private val carrier = Ui.text(context, "", 22f).apply { gravity = Gravity.CENTER }
    private val today = usage()
    private val month = usage()
    private val session = usage()
    private val hotspot = big()
    private val battery = big()
    private val signal = big()
    private val batteryIcon = BatteryIcon(context)
    private val batteryText = small("").apply { typeface = light; textSize = 19f }
    private val statusBar = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(Ui.dp(context, 30), Ui.dp(context, 4), Ui.dp(context, 30), 0)
        addView(batteryIcon, LinearLayout.LayoutParams(Ui.dp(context, 27), Ui.dp(context, 13)))
        addView(batteryText, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = Ui.dp(context, 5) })
        addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        addView(clock(19f, Color.WHITE, light))
    }

    private val aodEnabled = Platform.preferences(context).getBoolean(AOD_PREF, false)
    private var dimmed = false
    private var waking = false
    private val grayscale = Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f); postConcat(ColorMatrix().apply { setScale(AOD_LEVEL, AOD_LEVEL, AOD_LEVEL, 1f) }) })
    }
    private val dim = Runnable { setDimmed(true) }
    private val shift = object : Runnable {
        override fun run() {
            val range = Ui.dp(context, 4).toFloat()
            val x = (Math.random().toFloat() * 2 - 1) * range; val y = (Math.random().toFloat() * 2 - 1) * range
            for (view in listOf(statusBar, pages, dots)) { view.translationX = x; view.translationY = y }
            handler.postDelayed(this, 60_000)
        }
    }

    private var lastRx = -1L
    private var lastTx = -1L
    private var lastAt = 0L
    private var sessionRx = 0L
    private var sessionTx = 0L
    private var usageAt = 0L
    private var lastSource: String? = null

    private val tick = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 1000) }
    }

    init {
        setBackgroundColor(Color.BLACK)
        pages.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(speedPage(), LinearLayout.LayoutParams(0, -1, 1.15f))
            addView(View(context).apply { setBackgroundColor(0x33FFFFFF) }, LinearLayout.LayoutParams(1, -1).apply { setMargins(0, Ui.dp(context, 28), 0, Ui.dp(context, 18)) })
            addView(usagePage(), LinearLayout.LayoutParams(0, -1, 1f))
        })
        pages.addView(infoPage())
        pages.addView(SpeedTestView(context, onActivity = { scheduleDim() }))
        pages.addView(listPage(R.string.ui_hotspot_status, listOf(R.string.ui_hotspot to hotspot, R.string.ui_hotspot_battery to battery, R.string.ui_hotspot_signal to signal)))
        addView(pages, LayoutParams(-1, -1).apply { topMargin = Ui.dp(context, 26); bottomMargin = Ui.dp(context, 14) })
        addView(statusBar, LayoutParams(-1, Ui.dp(context, 32), Gravity.TOP))
        addView(dots, LayoutParams(-1, Ui.dp(context, 14), Gravity.BOTTOM))
        showPage(0, 0)
        isClickable = true
        isLongClickable = true
        setOnLongClickListener { onExit(); true }
    }

    private fun big() = Ui.text(context, "-", 30f).apply { typeface = condensed; gravity = Gravity.END }
    private fun small(value: String) = Ui.text(context, value, 16f).apply { includeFontPadding = false; setPadding(0, 0, 0, 0) }
    private fun clock(size: Float, color: Int, face: Typeface) = TextClock(context).apply {
        format12Hour = "h:mm"; format24Hour = "HH:mm"; textSize = size; setTextColor(color); typeface = face; includeFontPadding = false
    }

    private fun scheduleDim() {
        handler.removeCallbacks(dim)
        if (aodEnabled && !dimmed) handler.postDelayed(dim, AOD_DELAY_MS)
    }

    private fun setDimmed(value: Boolean) {
        if (dimmed == value) return
        dimmed = value
        setLayerType(if (value) LAYER_TYPE_HARDWARE else LAYER_TYPE_NONE, if (value) grayscale else null)
        handler.removeCallbacks(shift)
        if (value) handler.post(shift) else {
            for (view in listOf(statusBar, pages, dots)) { view.translationX = 0f; view.translationY = 0f }
            scheduleDim()
        }
        val params = rootView.layoutParams as? WindowManager.LayoutParams ?: return
        params.screenBrightness = if (value) AOD_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        runCatching { context.getSystemService(WindowManager::class.java).updateViewLayout(rootView, params) }
    }

    private fun usage() = Ui.text(context, "-", 26f).apply { typeface = condensed; includeFontPadding = false; setPadding(0, 0, 0, 0) }

    private fun infoPage(): View {
        val wifi = HotspotWifi.read(context)
        if (!wifi.configured) return Ui.text(context, I18n.text(R.string.ui_hotspot_wifi_setup), 18f, Ui.muted).apply {
            gravity = Gravity.CENTER; setPadding(Ui.dp(context, 36), 0, Ui.dp(context, 36), 0)
        }
        val qrSize = Ui.dp(context, 176)
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(Ui.dp(context, 24), 0, Ui.dp(context, 16), 0)
            addView(android.widget.ImageView(context).apply {
                setImageBitmap(runCatching { wifi.bitmap(qrSize) }.getOrNull()); contentDescription = wifi.ssid
            }, LinearLayout.LayoutParams(qrSize, qrSize))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
                fun field(label: Int, value: String) {
                    addView(Ui.text(context, I18n.text(label), 16f, Ui.muted).apply { setPadding(0, Ui.dp(context, 4), 0, 0) })
                    addView(Ui.text(context, value, 22f).apply { typeface = condensed; includeFontPadding = false; setPadding(0, 0, 0, 0) })
                }
                field(R.string.ui_hotspot_wifi_name, wifi.ssid)
                if (wifi.password.isNotEmpty()) field(R.string.ui_hotspot_wifi_password, wifi.password)
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = Ui.dp(context, 20) })
        }
    }

    private fun usagePage(): View {
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(Ui.dp(context, 22), 0, Ui.dp(context, 12), 0) }
        for ((label, value) in listOf(R.string.ui_hotspot_today to today, R.string.ui_hotspot_this_month to month, R.string.ui_hotspot_session to session)) {
            column.addView(Ui.text(context, I18n.text(label), 16f, Ui.muted).apply { setPadding(0, Ui.dp(context, 3), 0, 0); maxLines = 1 })
            column.addView(value)
        }
        return column
    }

    private fun speedPage(): View {
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        column.addView(carrier, LinearLayout.LayoutParams(-1, -2))
        fun row(value: TextView, up: Boolean) = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            addView(value)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                addView(Arrow(context, up), LinearLayout.LayoutParams(Ui.dp(context, 22), Ui.dp(context, 22)))
                addView(Ui.text(context, "Mbps", 15f, Ui.muted), LinearLayout.LayoutParams(-2, -2))
            }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = Ui.dp(context, 10) })
        }
        column.addView(row(upload, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(context, 2) })
        column.addView(row(download, false), LinearLayout.LayoutParams(-1, -2))
        return column
    }

    private fun listPage(title: Int, rows: List<Pair<Int, TextView>>): View {
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(Ui.dp(context, 36), 0, Ui.dp(context, 36), 0) }
        column.addView(Ui.text(context, I18n.text(title), 20f).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(context, 8) })
        for ((label, value) in rows) column.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(context, I18n.text(label), 17f, Ui.muted), LinearLayout.LayoutParams(0, -2, 1f))
            addView(value)
        }, LinearLayout.LayoutParams(-1, Ui.dp(context, 50)))
        return column
    }

    private fun showPage(index: Int, direction: Int) {
        if (index !in 0 until pageCount) return
        val previous = pages.getChildAt(page)
        page = index
        for (i in 0 until pages.childCount) pages.getChildAt(i).visibility = if (i == page) VISIBLE else GONE
        if (direction != 0) {
            val current = pages.getChildAt(page)
            current.translationX = direction * width.toFloat() * .35f; current.alpha = 0f
            current.animate().translationX(0f).alpha(1f).setDuration(180).start()
            if (previous !== current) previous.animate().cancel()
        }
        dots.removeAllViews()
        for (i in 0 until pageCount) dots.addView(View(context).apply {
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(if (i == page) Color.WHITE else 0x55FFFFFF) }
        }, LinearLayout.LayoutParams(Ui.dp(context, 6), Ui.dp(context, 6)).apply { setMargins(Ui.dp(context, 4), 0, Ui.dp(context, 4), 0) })
    }

    private var downX = 0f
    private var downY = 0f
    private var swiped = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.coerceAtLeast(Ui.dp(context, 24))

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && !dimmed) scheduleDim()
        return super.dispatchTouchEvent(event)
    }

    // Children such as the speed test buttons receive taps; swipes are taken over here.
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; swiped = false; return dimmed }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX; val dy = event.y - downY
                return (kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f) ||
                    (dy > slop && dy > kotlin.math.abs(dx) * 1.25f && downY < height / 3f)
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && dimmed) { waking = true; setDimmed(false) }
        if (waking) { if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) waking = false; return true }
        if (event.actionMasked == MotionEvent.ACTION_UP) scheduleDim()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; swiped = false }
            MotionEvent.ACTION_MOVE -> if (!swiped) {
                val dx = event.x - downX; val dy = event.y - downY
                if (kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f) {
                    swiped = true; cancelLongPress(); showPage(page + if (dx < 0) 1 else -1, if (dx < 0) 1 else -1)
                } else if (dy > slop && dy > kotlin.math.abs(dx) * 1.25f && downY < height / 3f) {
                    swiped = true; cancelLongPress(); onShortcuts()
                }
            }
        }
        return if (swiped) { if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) cancelLongPress(); true } else super.onTouchEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (android.os.Build.VERSION.SDK_INT >= 31) try {
            val callback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) { networkLabel = label(info); updateCarrier() }
            }
            telephony.registerTelephonyCallback(context.mainExecutor, callback)
            displayCallback = callback
        } catch (_: RuntimeException) {}
        handler.post(tick)
        scheduleDim()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        if (android.os.Build.VERSION.SDK_INT >= 31) (displayCallback as? TelephonyCallback)?.let { runCatching { telephony.unregisterTelephonyCallback(it) } }
        displayCallback = null
        io.shutdownNow()
        super.onDetachedFromWindow()
    }

    private fun label(info: TelephonyDisplayInfo) = when (info.overrideNetworkType) {
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G"
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "4G+"
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> "4G+"
        else -> when (info.networkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"
            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
            else -> ""
        }
    }

    private fun updateCarrier() {
        val name = telephony.networkOperatorName.orEmpty().ifBlank { telephony.simOperatorName.orEmpty() }
        carrier.text = listOf(name.ifBlank { I18n.text(R.string.ui_hotspot_no_service) }, networkLabel).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun refresh() {
        updateCarrier()
        val iface = hotspotInterface()
        var rx = TrafficStats.getMobileRxBytes(); var tx = TrafficStats.getMobileTxBytes()
        if (android.os.Build.VERSION.SDK_INT >= 31 && iface != null) {
            // Clients download what the hotspot interface transmits and upload what it receives.
            val down = TrafficStats.getTxBytes(iface); val up = TrafficStats.getRxBytes(iface)
            if (down >= 0 && up >= 0) { rx = down; tx = up }
        }
        val now = SystemClock.elapsedRealtime()
        if (iface != lastSource) { lastSource = iface; lastAt = 0L }
        if (rx >= 0 && tx >= 0) {
            if (lastAt != 0L && rx >= lastRx && tx >= lastTx) {
                val seconds = (now - lastAt).coerceAtLeast(1) / 1000f
                download.text = mbps((rx - lastRx) / seconds)
                upload.text = mbps((tx - lastTx) / seconds)
                sessionRx += rx - lastRx; sessionTx += tx - lastTx
            }
            lastRx = rx; lastTx = tx; lastAt = now
        }
        session.text = bytes(sessionRx + sessionTx)
        val on = iface != null || hotspotOn()
        hotspot.text = I18n.text(if (on) R.string.ui_hotspot_on else R.string.ui_hotspot_off)
        hotspot.setTextColor(if (on) GREEN else Color.WHITE)
        val level = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val percent = level?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: -1
        val charging = (level?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        battery.text = if (percent < 0) "-" else "$percent%" + if (charging) " +" else ""
        batteryText.text = if (percent < 0) "" else "$percent%"
        batteryIcon.update(percent, charging)
        val bars = runCatching { telephony.signalStrength?.level }.getOrNull()
        signal.text = if (bars == null) "-" else "$bars/4"
        if (now - usageAt > 30_000 || usageAt == 0L) { usageAt = now; loadUsage() }
    }

    private fun loadUsage() {
        if (!RecentApps.hasAccess(context)) { today.text = I18n.text(R.string.ui_hotspot_needs_usage_access); today.textSize = 15f; return }
        val stats = context.getSystemService(NetworkStatsManager::class.java)
        runCatching { io.execute {
            val calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            val dayStart = calendar.timeInMillis
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            val monthStart = calendar.timeInMillis
            fun total(start: Long) = runCatching { stats.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, start, System.currentTimeMillis()).let { it.rxBytes + it.txBytes } }.getOrNull()
            val day = total(dayStart); val all = total(monthStart)
            handler.post { today.text = day?.let(::bytes) ?: "-"; month.text = all?.let(::bytes) ?: "-" }
        } }
    }

    private class BatteryIcon(context: Context) : View(context) {
        private var level = -1
        private var charging = false
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fun update(percent: Int, plugged: Boolean) { if (percent != level || plugged != charging) { level = percent; charging = plugged; invalidate() } }
        override fun onDraw(canvas: Canvas) {
            val h = height.toFloat(); val nub = h * .18f; val w = width - nub; val line = h * .12f
            stroke.strokeWidth = line
            canvas.drawRoundRect(line / 2, line / 2, w - line / 2, h - line / 2, h * .2f, h * .2f, stroke)
            canvas.drawRect(w, h * .3f, w + nub, h * .7f, stroke.apply { style = Paint.Style.FILL })
            stroke.style = Paint.Style.STROKE
            fill.color = when { charging -> GREEN; level in 0..15 -> RED; else -> Color.WHITE }
            val inset = line * 2
            if (level > 0) canvas.drawRect(inset, inset, inset + (w - inset * 2) * level.coerceAtMost(100) / 100f, h - inset, fill)
        }
    }

    class Arrow(context: Context, private val up: Boolean) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (up) YELLOW else GREEN; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat(); paint.strokeWidth = w * .16f
            val tip = if (up) h * .15f else h * .85f; val tail = if (up) h * .85f else h * .15f
            val wing = h * .5f
            canvas.drawLine(w / 2, tail, w / 2, tip, paint)
            canvas.drawPath(Path().apply { moveTo(w * .18f, wing); lineTo(w / 2, tip); lineTo(w * .82f, wing) }, paint)
        }
    }

    companion object {
        const val PREF = "hotspot_mode"
        const val YELLOW = 0xFFFFC93C.toInt()
        const val GREEN = 0xFF4ADE80.toInt()
        private const val RED = 0xFFF87171.toInt()
        private const val AOD_LEVEL = .8f
        private const val AOD_BRIGHTNESS = .12f
        private const val AOD_DELAY_MS = 15_000L
        const val AOD_PREF = "hotspot_aod"

        fun mbps(bytesPerSecond: Float): String {
            val value = bytesPerSecond * 8 / 1_000_000f
            return if (value >= 100) "%.0f".format(value) else "%.1f".format(value)
        }

        fun bytes(value: Long): String = when {
            value >= 1_000_000_000L -> "%.2f GB".format(value / 1_000_000_000.0)
            value >= 1_000_000L -> "%.1f MB".format(value / 1_000_000.0)
            else -> "%.0f KB".format(value / 1_000.0)
        }

        fun hotspotOn() = hotspotInterface() != null

        fun hotspotInterface(): String? = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().firstOrNull { iface ->
                iface.isUp && (iface.name.startsWith("swlan") || iface.name.startsWith("ap") || iface.name == "wlan1") &&
                    iface.inetAddresses.toList().any { it is java.net.Inet4Address }
            }?.name
        } catch (_: Exception) { null }
    }
}
