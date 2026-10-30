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
    private var keyboardVisible=false
    private var keyboardGeneration=-1L
    private var dismissedKeyboard=-1L
    private var section = "apps"
    private var closingRecentTask=false
    private var recentClosingSurface: RecentClosingSurface?=null
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
        startForeground(1, foregroundNotification())
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

    private fun foregroundNotification(): Notification {
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("cover", I18n.text(R.string.ui_cover_screen), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, CoverMonitorService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "cover").setSmallIcon(R.drawable.ic_status)
            .setContentTitle(I18n.text(R.string.ui_foldlet_cover_screen_controls))
            .setContentText(I18n.text(R.string.ui_tap_the_right_edge_of_samsung_home))
            .setContentIntent(open).addAction(Notification.Action.Builder(null, I18n.text(R.string.ui_stop), stop).build()).setOngoing(true).build()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        getSystemService(NotificationManager::class.java).notify(1,foregroundNotification())
        clearOverlay(); reconcile()
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
            ACTION_RECENTS -> showSystemRecents()
            ACTION_BACK -> navigateBack()
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
        if(keyboardVisible) dismissedKeyboard=NavigationService.current?.inputGeneration ?: -1L
        keyboardVisible=false
        val homeNavigation=overlay?.mode==CoverOverlay.Mode.SIDEBAR && samsungHome
        val leavingLauncher = overlay?.mode == CoverOverlay.Mode.DASHBOARD || overlay?.mode == CoverOverlay.Mode.RECENTS || overlay?.mode==CoverOverlay.Mode.SHORTCUTS
        minimized = true; navigationVisible = false; samsungHome = homeNavigation
        if (leavingLauncher) CoverRotation.clear()
        reconcile()
    }
    fun expand() { openDrawer(prefs.getString("app_mode", "all") ?: "all") }
    fun navigateBack() {
        if(samsungHome && overlay?.mode==CoverOverlay.Mode.SIDEBAR) { minimize();return }
        val cover = Platform.overlayDisplay(this) ?: return
        val service = NavigationService.current ?: return
        if (cover.state != Display.STATE_ON || (!Platform.isEmulatorOverlay(this) && folded == false)) return
        if (!Platform.unlocked(this)) { Platform.messageOnDisplay(this,cover.displayId,I18n.text(R.string.ui_unlock_your_phone_to_continue)); return }
        if (!Platform.coverHasFocus(service, cover.displayId)) { Platform.messageOnDisplay(this,cover.displayId,I18n.text(R.string.ui_tap_the_app_on_the_cover_screen)); return }
        when(service.sendCoverBack(cover.displayId)) {
            NavigationService.BackResult.LAUNCHER_ROOT -> { showSamsungHome(); return }
            NavigationService.BackResult.UNAVAILABLE -> {
                Platform.messageOnDisplay(this,cover.displayId,I18n.text(R.string.ui_use_the_back_control_inside_this_app)); return
            }
            NavigationService.BackResult.SENT -> Unit
        }
        minimized = true; navigationVisible = false; samsungHome = false
        clearOverlay()
        handler.postDelayed({ if (current === this) reconcile() },150)
    }
    fun showSystemRecents() {
        val cover = Platform.overlayDisplay(this) ?: return
        if (cover.state != Display.STATE_ON || (!Platform.isEmulatorOverlay(this) && folded == false)) return
        if (!Platform.unlocked(this)) { Platform.messageOnDisplay(this,cover.displayId,I18n.text(R.string.ui_unlock_your_phone_to_continue)); return }
        openPanel("system-recent")
    }
    fun foldedForTaskControl()=folded
    fun closeRecentTask(entry: RecentApps.Entry?) {
        val cover=Platform.overlayDisplay(this) ?: return
        if(closingRecentTask || !Platform.unlocked(this) || cover.state!=Display.STATE_ON) return
        val accessibility=NavigationService.current ?: return
        val source=overlay?.takeIf { it.mode==CoverOverlay.Mode.RECENTS }?.view ?: return
        val loadingButton=entry?.let { source.findViewWithTag<android.widget.ImageButton>("recent-dismiss:${it.component.packageName}") }
        val loadingIcon=loadingButton?.drawable
        loadingButton?.apply { setImageDrawable(null);isEnabled=false;stateDescription=I18n.text(R.string.ui_closing_app) }
        recentClosingSurface?.close()
        val surface=RecentClosingSurface.create(accessibility,cover,source,loadingButton)
        if(surface==null) { loadingButton?.apply { setImageDrawable(loadingIcon);isEnabled=true;stateDescription=null };Platform.messageOnDisplay(this,cover.displayId,I18n.text(R.string.ui_recent_task_surface_unavailable));return }
        recentClosingSurface=surface;closingRecentTask=true
        surface.whenReady({
            if(current!==this || recentClosingSurface!==surface) return@whenReady
            minimized=true;navigationVisible=false;keyboardVisible=false;samsungHome=false
            clearOverlay()
            SamsungRecentTasks.close(this,cover.displayId,entry) { result ->
                closingRecentTask=false
                if(result==SamsungRecentTasks.Result.CLOSED) {
                    if(entry==null) RecentApps.clear(this) else RecentApps.dismiss(this,entry.component.packageName)
                }
                if(current!==this) { surface.close();return@close }
                if(Platform.cover(this)?.state!=Display.STATE_ON || folded==false || !Platform.unlocked(this)) { surface.close();recentClosingSurface=null;schedule();return@close }
                Platform.samsungHome(this,cover.displayId)
                handler.postDelayed({
                    if(current===this && !closingRecentTask) {
                        showSystemRecents()
                        releaseRecentSurfaceWhenMenuAttached(surface)
                        if(result!=SamsungRecentTasks.Result.CLOSED && result!=SamsungRecentTasks.Result.CANCELED)
                            Platform.messageOnDisplay(this,cover.displayId,I18n.text(if(result==SamsungRecentTasks.Result.NOT_FOUND) R.string.ui_recent_task_not_found else R.string.ui_recent_task_not_closed))
                    }
                },250)
            }
        },{
            loadingButton?.apply { setImageDrawable(loadingIcon);isEnabled=true;stateDescription=null }
            closingRecentTask=false;surface.close();if(recentClosingSurface===surface) recentClosingSurface=null
            Platform.messageOnDisplay(this,cover.displayId,I18n.text(R.string.ui_recent_task_surface_unavailable))
        })
    }
    private fun releaseRecentSurfaceWhenMenuAttached(surface: RecentClosingSurface,attempt: Int=0) {
        if(current!==this || recentClosingSurface!==surface) { surface.close();return }
        val view=overlay?.takeIf { it.mode==CoverOverlay.Mode.RECENTS }?.view
        if(view?.isAttachedToWindow==true || attempt>=40) {
            // Rotation restoration can be asynchronous. Wait for the replacement
            // menu window instead of exposing Samsung while that work finishes.
            handler.postDelayed({ surface.close();if(recentClosingSurface===surface) recentClosingSurface=null },180)
        } else handler.postDelayed({ releaseRecentSurfaceWhenMenuAttached(surface,attempt+1) },50)
    }
    fun showNavigation() {
        keyboardVisible=false
        if (samsungHome) {
            val cover = Platform.overlayDisplay(this) ?: return
            val service = NavigationService.current ?: return
            if (!Platform.isEmulatorOverlay(this) && !SamsungCoverPage.isClock(service, cover.displayId)) { reconcile(); return }
        }
        minimized = true; navigationVisible = true; reconcile()
    }
    private fun homeGestureAllowed(): Boolean {
        val cover=Platform.overlayDisplay(this) ?: return false
        val service=NavigationService.current ?: return false
        if(cover.state!=Display.STATE_ON || (!Platform.isEmulatorOverlay(this) && folded==false)) return false
        if(!Platform.isEmulatorOverlay(this) && !SamsungCoverPage.isClock(service,cover.displayId)) { reconcile();return false }
        return true
    }
    fun openDrawerFromHome() {
        if(homeGestureAllowed()) openDrawer(prefs.getString("app_mode","all") ?: "all")
    }
    fun openHomeShortcuts() {
        if(prefs.getBoolean("home_shortcuts_enabled",true) && homeGestureAllowed()) openPanel("shortcuts")
    }
    fun reloadHomeControls() {
        if(overlay?.mode in setOf(CoverOverlay.Mode.HOME_EDGE,CoverOverlay.Mode.SHORTCUTS) || samsungHome) clearOverlay()
        schedule()
    }
    fun showSamsungHome() {
        val cover = Platform.overlayDisplay(this) ?: return
        if (!Platform.isEmulatorOverlay(this) && !Platform.samsungHome(this, cover.displayId)) return
        keyboardVisible=false;dismissedKeyboard=NavigationService.current?.inputGeneration ?: -1L
        samsungHome = true; minimized = true; navigationVisible = false; yieldedUntilSleep = false
        ++restoreGeneration; restoringRotation = false
        clearOverlay()
        CoverRotation.clear { if (current === this) reconcile() }
    }
    fun openDrawer(filter: String) {
        prefs.edit().putString("app_mode", filter).apply()
        openPanel("apps")
    }
    private fun openPanel(targetSection: String) {
        keyboardVisible=false
        section = targetSection; minimized = false; navigationVisible = false; yieldedUntilSleep = false; samsungHome = false
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
    fun yieldToStock() { keyboardVisible=false; yieldedUntilSleep = true; CoverRotation.clear(); reconcile() }

    fun reloadDrawer() {
        if(overlay?.mode==CoverOverlay.Mode.DASHBOARD) { clearOverlay();reconcile() }
    }
    fun reloadKeyboard() {
        if(keyboardVisible) { clearOverlay();reconcile() }
    }
    fun showKeyboard() {
        val cover=Platform.overlayDisplay(this) ?: return
        val service=NavigationService.current ?: return
        if(!service.hasCoverEditor(cover.displayId)) { Platform.messageOnDisplay(this,cover.displayId,I18n.text(R.string.ui_keyboard_focus));return }
        dismissedKeyboard=-1L;keyboardGeneration=service.inputGeneration;keyboardVisible=true
        minimized=true;navigationVisible=false;samsungHome=false
        clearOverlay();reconcile()
    }
    private fun reconcile() {
        if (restoringRotation) return
        if (closingRecentTask) {
            if(!Platform.unlocked(this) || folded==false || Platform.cover(this)?.state!=Display.STATE_ON) { recentClosingSurface?.close();recentClosingSurface=null }
            return
        }
        if (!prefs.getBoolean("monitor", false)) { clearOverlay(); return }
        val cover = Platform.overlayDisplay(this)
        val accessibility = NavigationService.current
        if (cover == null || !cover.isValid) {
            clearOverlay(); report(I18n.text(R.string.ui_cover_screen_not_found), null); return
        }
        val emulatorPreview = Platform.isEmulatorOverlay(this)
        if (cover.state != Display.STATE_ON || (!emulatorPreview && folded == false)) {
            keyboardVisible=false;dismissedKeyboard=-1L
            minimized = true; samsungHome = true; navigationVisible = false; yieldedUntilSleep = false; section = "apps"
            CoverRotation.clear()
            clearOverlay(); report(I18n.text(R.string.ui_waiting_for_the_cover_screen_to_turn), null); return
        }
        if (accessibility == null) {
            CoverRotation.clear()
            clearOverlay(); report(I18n.text(R.string.ui_waiting_for_foldlet_accessibility_access), I18n.text(R.string.ui_enable_the_foldlet_accessibility_service_to_show))
            return
        }
        val forceKeyboard=prefs.getBoolean("force_cover_keyboard",false)
        val editor=forceKeyboard && accessibility.hasCoverEditor(cover.displayId)
        if(keyboardVisible && (!editor || keyboardGeneration!=accessibility.inputGeneration)) { keyboardVisible=false;clearOverlay() }
        if(forceKeyboard && minimized && !navigationVisible && !yieldedUntilSleep) {
            handler.removeCallbacks(update);handler.postDelayed(update,150)
            if(editor && dismissedKeyboard!=accessibility.inputGeneration && !keyboardVisible) {
                keyboardVisible=true;keyboardGeneration=accessibility.inputGeneration;samsungHome=false;clearOverlay()
            }
        }
        if (yieldedUntilSleep) { clearOverlay(); report(I18n.text(R.string.ui_samsung_has_control_until_the_next_screen), null); return }
        if (minimized && !keyboardVisible && !emulatorPreview && (!navigationVisible || samsungHome)) {
            // Events remove the handle while the page moves; polling also covers
            // firmware that omits a final accessibility scroll event.
            handler.removeCallbacks(update); handler.postDelayed(update, 200)
            val clock = SamsungCoverPage.isClock(accessibility, cover.displayId)
            // Back inside an app can return to Samsung without our Home button.
            if (SamsungCoverPage.isSamsungPage) samsungHome = true
            if (samsungHome && !clock) {
                navigationVisible=false
                clearOverlay(); report(I18n.text(R.string.ui_samsung_widget_or_page_has_control), null); return
            }
        }
        try {
            if (overlay?.displayId != cover.displayId || owner !== accessibility) {
                clearOverlay()
                owner = accessibility
                overlay = CoverOverlay(accessibility, cover, ::minimize, ::expand, ::yieldToStock, ::showNavigation, ::showSystemRecents, section, ::showSamsungHome, ::navigateBack, ::showKeyboard, ::openDrawerFromHome, ::openHomeShortcuts)
            }
            overlay!!.show(when {
                keyboardVisible -> CoverOverlay.Mode.KEYBOARD
                navigationVisible -> CoverOverlay.Mode.SIDEBAR
                !minimized -> when(section) { "system-recent" -> CoverOverlay.Mode.RECENTS;"shortcuts" -> CoverOverlay.Mode.SHORTCUTS;else -> CoverOverlay.Mode.DASHBOARD }
                samsungHome -> CoverOverlay.Mode.HOME_EDGE
                else -> CoverOverlay.Mode.EDGE
            })
            report("Overlay ${overlay!!.mode} · display ${cover.displayId} · ${overlay!!.backend}", null)
        } catch (e: RuntimeException) {
            clearOverlay()
            report(I18n.text(R.string.ui_could_not_show_the_overlay), "${e.javaClass.simpleName}: ${e.message}")
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
        writer.println("rotationSensorActive=${CoverRotation.sensorActive} sensorAngle=${CoverRotation.sensorAngle}")
        writer.println("samsungPage=${SamsungCoverPage.diagnostic}")
        writer.println("backRoute=${NavigationService.current?.backRoute}")
        writer.println("samsungRoutineControl=${SamsungRoutineControls.status}")
        writer.println("samsungRecentTask=${SamsungRecentTasks.status} closing=$closingRecentTask")
        writer.println("recentClosingSurface=${recentClosingSurface!=null} status=${RecentClosingSurface.status}")
        writer.println("keyboardEnabled=${prefs.getBoolean("force_cover_keyboard",false)} keyboardVisible=$keyboardVisible inputSession=${NavigationService.current?.inputGeneration}")
        writer.println("window=${overlay?.window?.attributes} status=${prefs.getString("monitor_status", "")} error=${prefs.getString("monitor_error", null)}")
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        displays.unregisterDisplayListener(this)
        sensors.unregisterListener(this)
        clearOverlay()
        recentClosingSurface?.close();recentClosingSurface=null
        CoverRotation.clear()
        if (current === this) { current = null;SamsungRecentTasks.cancel() }
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
        const val ACTION_RECENTS = "vn.lit.screenos.SHOW_SYSTEM_RECENTS"
        const val ACTION_BACK = "vn.lit.screenos.SYSTEM_BACK"
        private const val SAMSUNG_LID_SENSOR = 65695
        var current: CoverMonitorService? = null; private set
        val running get() = current != null
    }
}
