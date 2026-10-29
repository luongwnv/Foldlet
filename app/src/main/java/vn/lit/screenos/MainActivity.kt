package vn.lit.screenos

import android.Manifest
import android.app.*
import android.content.*
import android.os.*
import android.provider.Settings

class MainActivity : Activity() {
    private lateinit var screen: SettingsScreen
    private val prefs by lazy { Platform.preferences(this) }
    private val resumeHandler = Handler(Looper.getMainLooper())
    private val restoreMonitor = Runnable {
        if (prefs.getBoolean("monitor", false) && !CoverMonitorService.running &&
            NavigationService.current != null && Settings.canDrawOverlays(this)) setMonitor(true)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CoverRotation.initialize(this)
        Platform.removeLegacyWidgetHost(this)
        screen = SettingsScreen(this, savedInstanceState)
    }
    override fun onResume() {
        super.onResume()
        // An APK update can stop the process while preserving the user's switch.
        // Allow time for Android to rebind the accessibility service.
        resumeHandler.post(restoreMonitor)
        resumeHandler.postDelayed(restoreMonitor, 750)
        resumeHandler.postDelayed(restoreMonitor, 1500)
        screen.render()
        CoverRotation.probe(this) { if (!isDestroyed && !isFinishing) screen.render() }
    }
    override fun onPause() {
        resumeHandler.removeCallbacks(restoreMonitor)
        super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) { screen.save(outState); super.onSaveInstanceState(outState) }
    fun setMonitor(enabled: Boolean, showApps: Boolean = false): Boolean {
        if (!enabled) {
            prefs.edit().putBoolean("monitor", false).apply()
            stopService(Intent(this, CoverMonitorService::class.java))
            refreshSoon(); return true
        }
        if (NavigationService.current == null) { accessibility(); return false }
        if (Build.MODEL.startsWith("SM-F711") && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this).setTitle(I18n.text(R.string.ui_allow_display_over_other_apps))
                .setMessage(I18n.text(R.string.ui_foldlet_needs_this_permission_to_show_its))
                .setPositiveButton(I18n.text(R.string.ui_open_settings)) { _, _ -> overlayPermission() }.setNegativeButton(I18n.text(R.string.ui_later), null).show()
            return false
        }
        prefs.edit().putBoolean("monitor", true).remove("monitor_error").apply()
        startForegroundService(Intent(this, CoverMonitorService::class.java).setAction(if (showApps) CoverMonitorService.ACTION_HOME else CoverMonitorService.ACTION_START))
        refreshSoon(); return true
    }
    fun openDrawer() {
        if (CoverMonitorService.running) CoverMonitorService.current?.expand() else setMonitor(true, showApps = true)
    }
    fun samsungHome() {
        if (CoverMonitorService.running) CoverMonitorService.current?.showSamsungHome()
        else Platform.cover(this)?.let { Platform.samsungHome(this, it.displayId) }
    }
    fun overlayPermission() { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))) }
    fun accessibility() {
        AlertDialog.Builder(this).setTitle(I18n.text(R.string.ui_enable_foldlet_service))
            .setMessage(I18n.text(R.string.ui_choose_foldlet_in_accessibility_settings_to_use))
            .setPositiveButton(I18n.text(R.string.ui_open_settings)) { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNegativeButton(I18n.text(R.string.ui_later), null).show()
    }
    fun notificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),20)
        else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
    }
    fun refreshSoon() { window.decorView.postDelayed({ if (!isDestroyed && !isFinishing) screen.render() },250) }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 20) screen.render()
    }
}
