package vn.lit.screenos

import android.Manifest
import android.app.*
import android.content.*
import android.os.*
import android.provider.Settings

class MainActivity : Activity() {
    private lateinit var screen: SettingsScreen
    private val prefs by lazy { Platform.preferences(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CoverRotation.initialize(this)
        Platform.removeLegacyWidgetHost(this)
        screen = SettingsScreen(this, savedInstanceState)
    }
    override fun onResume() {
        super.onResume()
        screen.render()
        CoverRotation.probe(this) { if (!isDestroyed && !isFinishing) screen.render() }
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
            AlertDialog.Builder(this).setTitle("Cho phép hiển thị trên ứng dụng khác")
                .setMessage("LIT cần quyền này để hiển thị menu và vùng vuốt trên màn hình ngoài.")
                .setPositiveButton("Mở cài đặt") { _, _ -> overlayPermission() }.setNegativeButton("Để sau", null).show()
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
        AlertDialog.Builder(this).setTitle("Bật dịch vụ LIT Screen OS")
            .setMessage("Chọn LIT Screen OS trong cài đặt Trợ năng để dùng menu và điều hướng màn hình ngoài. LIT chỉ kiểm tra mã khung giao diện Samsung để phân biệt đồng hồ với widget; không đọc chữ, nội dung widget hoặc ghi thao tác.")
            .setPositiveButton("Mở cài đặt") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNegativeButton("Để sau", null).show()
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
