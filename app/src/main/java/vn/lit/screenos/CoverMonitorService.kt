package vn.lit.screenos

import android.app.*
import android.content.*
import android.hardware.*
import android.hardware.display.DisplayManager
import android.os.*
import android.view.Display
import java.io.FileDescriptor
import java.io.PrintWriter

/** Watches display/fold signals and coordinates trusted accessibility overlays. */
class CoverMonitorService : Service(), DisplayManager.DisplayListener, SensorEventListener {
    var overlay: CoverOverlay? = null; private set
    private var owner: NavigationService? = null
    private var minimized = true
    private var samsungHome = true
    private var navigationVisible = false
    private var section = "apps"
    private var restoringRotation = false
    private var restoreGeneration = 0
    private var yieldedUntilSleep = false
    private var folded: Boolean? = null
    private var lidSensor: Sensor? = null
    private lateinit var displays: DisplayManager
    private lateinit var sensors: SensorManager
    private val handler = Handler(Looper.getMainLooper())
    private val update = Runnable { reconcile() }
    private val prefs by lazy { Platform.preferences(this) }

    override fun onCreate() {
        super.onCreate()
        CoverRotation.initialize(this)
        Platform.removeLegacyWidgetHost(this)
        current = this
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("cover", "Màn hình phụ", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, CoverMonitorService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, Notification.Builder(this, "cover").setSmallIcon(R.drawable.ic_status)
            .setContentTitle("LIT Screen OS · overlay màn hình ngoài")
            .setContentText("Chạm cạnh phải màn hình Samsung để mở ứng dụng · nhấn để thiết lập")
            .setContentIntent(open).addAction(Notification.Action.Builder(null, "Dừng", stop).build()).setOngoing(true).build())
        displays = getSystemService(DisplayManager::class.java)
        sensors = getSystemService(SensorManager::class.java)
        displays.registerDisplayListener(this, handler)
        // Observed SM-F711B lid sensor: 0=closed, 1=open. scrcpy's virtual
        // primary display must not be mistaken for an unfolded phone.
        lidSensor = sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
            ?: if (Build.MODEL.startsWith("SM-F711")) sensors.getSensorList(Sensor.TYPE_ALL).firstOrNull { it.type == SAMSUNG_LID_SENSOR } else null
        lidSensor?.let { if (!sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)) lidSensor = null }
        schedule()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP, "stop" -> {
                prefs.edit().putBoolean("monitor", false).apply()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> prefs.edit().putBoolean("monitor", true).remove("monitor_error").apply()
            ACTION_HOME -> { openDrawer(prefs.getString("app_mode", "all") ?: "all") }
            ACTION_MINIMIZE -> minimize()
            ACTION_YIELD -> yieldToStock()
            ACTION_SAMSUNG -> showSamsungHome()
            ACTION_ROTATE -> {
                val rotation = intent.getStringExtra("orientation") ?: "auto"
                Platform.overlayDisplay(this)?.let { CoverRotation.apply(this, it.displayId, rotation) }
            }
        }
        if (!prefs.getBoolean("monitor", false)) { stopSelf(); return START_NOT_STICKY }
        schedule()
        return START_STICKY
    }

    fun schedule() { handler.removeCallbacks(update); handler.postDelayed(update, 100) }
    override fun onDisplayAdded(id: Int) = schedule()
    override fun onDisplayRemoved(id: Int) = schedule()
    override fun onDisplayChanged(id: Int) = schedule()
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values.firstOrNull() ?: return
        folded = if (event.sensor.type == Sensor.TYPE_HINGE_ANGLE) value < 20f else value < .5f
        schedule()
    }

    fun minimize() {
        val leavingLauncher = overlay?.mode == CoverOverlay.Mode.DASHBOARD
        minimized = true; navigationVisible = false; samsungHome = false
        if (leavingLauncher) CoverRotation.clear()
        reconcile()
    }
    fun expand() { openDrawer(prefs.getString("app_mode", "all") ?: "all") }
    fun showNavigation() {
        if (samsungHome) { openDrawer(prefs.getString("app_mode", "all") ?: "all"); return }
        minimized = true; navigationVisible = true; reconcile()
    }
    fun showSamsungHome() {
        val cover = Platform.overlayDisplay(this) ?: return
        if (!Platform.isEmulatorOverlay(this) && !Platform.samsungHome(this, cover.displayId)) return
        samsungHome = true; minimized = true; navigationVisible = false; yieldedUntilSleep = false
        ++restoreGeneration; restoringRotation = false
        clearOverlay()
        CoverRotation.clear { if (current === this) reconcile() }
    }
    fun openDrawer(filter: String) {
        prefs.edit().putString("app_mode", filter).apply()
        section = "apps"; minimized = false; navigationVisible = false; yieldedUntilSleep = false; samsungHome = false
        clearOverlay()
        restoringRotation = true
        val generation = ++restoreGeneration
        fun showDrawer() {
            if (current === this && generation == restoreGeneration) { restoringRotation = false; reconcile() }
        }
        CoverRotation.clear {
            if (current !== this || generation != restoreGeneration) return@clear
            val cover = Platform.overlayDisplay(this)
            // Restoring a free user policy still lets the underlying activity
            // request portrait. Keep the launcher landscape while ADB is live;
            // leaving it or losing the helper restores the saved user policy.
            if (cover != null && CoverRotation.authorized && CoverRotation.available) {
                CoverRotation.apply(this, cover.displayId, "landscape") { showDrawer() }
            } else showDrawer()
        }
    }
    fun yieldToStock() { yieldedUntilSleep = true; CoverRotation.clear(); reconcile() }

    private fun reconcile() {
        if (restoringRotation) return
        if (!prefs.getBoolean("monitor", false)) { clearOverlay(); return }
        val cover = Platform.overlayDisplay(this)
        val accessibility = NavigationService.current
        if (cover == null || !cover.isValid) {
            clearOverlay(); report("Chưa tìm thấy màn hình ngoài", null); return
        }
        val emulatorPreview = Platform.isEmulatorOverlay(this)
        if (cover.state != Display.STATE_ON || (!emulatorPreview && folded == false)) {
            minimized = true; samsungHome = true; navigationVisible = false; yieldedUntilSleep = false; section = "apps"
            CoverRotation.clear()
            clearOverlay(); report("Đang chờ bật màn hình ngoài", null); return
        }
        if (accessibility == null) {
            CoverRotation.clear()
            clearOverlay(); report("Đang chờ quyền trợ năng LIT", "Bật dịch vụ trợ năng LIT Screen OS để hiển thị overlay")
            return
        }
        if (yieldedUntilSleep) { clearOverlay(); report("Đã nhường giao diện Samsung đến lần bật màn hình tiếp theo", null); return }
        try {
            if (overlay?.displayId != cover.displayId || owner !== accessibility) {
                clearOverlay()
                owner = accessibility
                overlay = CoverOverlay(accessibility, cover, ::minimize, ::expand, ::yieldToStock, ::showNavigation, { openDrawer("recent") }, section, ::showSamsungHome)
            }
            overlay!!.show(when {
                navigationVisible -> CoverOverlay.Mode.SIDEBAR
                !minimized -> CoverOverlay.Mode.DASHBOARD
                samsungHome -> CoverOverlay.Mode.HOME_EDGE
                else -> CoverOverlay.Mode.EDGE
            })
            report("Overlay ${overlay!!.mode} · display ${cover.displayId} · ${overlay!!.backend}", null)
        } catch (e: RuntimeException) {
            clearOverlay()
            report("Không hiển thị được overlay", "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun report(status: String, error: String?) {
        if (prefs.getString("monitor_status", null) != status || prefs.getString("monitor_error", null) != error) {
            prefs.edit().putString("monitor_status", status).apply {
                if (error == null) remove("monitor_error") else putString("monitor_error", error)
            }.apply()
        }
    }
    private fun clearOverlay() { overlay?.hide(); overlay = null; owner = null }

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>) {
        writer.println("engine=cover-overlay enabled=${prefs.getBoolean("monitor", false)}")
        writer.println("display=${overlay?.displayId} mode=${overlay?.mode ?: CoverOverlay.Mode.HIDDEN} backend=${overlay?.backend} folded=$folded lidSensor=${lidSensor?.type} samsungHome=$samsungHome")
        writer.println("rotationRequest=${CoverRotation.mode} actualRotation=${Platform.overlayDisplay(this)?.rotation} adbBridgeConfigured=${CoverRotation.authorized} adbBridgeAvailable=${CoverRotation.available}")
        writer.println("window=${overlay?.window?.attributes} status=${prefs.getString("monitor_status", "")} error=${prefs.getString("monitor_error", null)}")
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        displays.unregisterDisplayListener(this)
        sensors.unregisterListener(this)
        clearOverlay()
        CoverRotation.clear()
        if (current === this) current = null
        // Preserve the enabled setting if the system reclaims the process.
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
    companion object {
        const val ACTION_START = "vn.lit.screenos.START_COVER_OVERLAY"
        const val ACTION_STOP = "vn.lit.screenos.STOP_COVER_OVERLAY"
        const val ACTION_HOME = "vn.lit.screenos.SHOW_COVER_HOME"
        const val ACTION_MINIMIZE = "vn.lit.screenos.MINIMIZE_COVER_OVERLAY"
        const val ACTION_YIELD = "vn.lit.screenos.YIELD_COVER_OVERLAY"
        const val ACTION_ROTATE = "vn.lit.screenos.ROTATE_COVER_OVERLAY"
        const val ACTION_SAMSUNG = "vn.lit.screenos.SHOW_SAMSUNG_HOME"
        private const val SAMSUNG_LID_SENSOR = 65695
        var current: CoverMonitorService? = null; private set
        val running get() = current != null
    }
}
