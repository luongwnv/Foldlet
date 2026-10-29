package vn.lit.screenos

import android.app.*
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.PixelCopy
import android.widget.Button
import android.widget.TextView
import android.graphics.Bitmap
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises real Samsung display policy and the dashboard under the app UID. */
class HardwareSmokeTest : Instrumentation() {
    private var preview = false
    private var overlay = false
    private var drawer = false
    private var settings = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        preview = arguments?.getString("mode") == "preview"
        overlay = arguments?.getString("mode") == "overlay"
        drawer = arguments?.getString("mode") == "drawer"
        settings = arguments?.getString("mode") == "settings"
        start()
    }
    override fun onStart() {
        if (overlay) { OverlaySmokeTest(this).run(); return }
        if (drawer) { DrawerSmokeTest(this).run(); return }
        if (settings) { SettingsSmokeTest(this).run(); return }
        val result = Bundle()
        var activity: Activity? = null
        var resultCode = Activity.RESULT_OK
        try {
            val inventory = Platform.displays(targetContext).joinToString { "id=${it.displayId} name=${it.name} flags=${it.flags}" }
            result.putString("displays", inventory)
            val target = if (preview) Platform.displays(targetContext).first { it.displayId == 0 }
                else Platform.cover(targetContext) ?: error("No built-in cover display. Visible displays: $inventory")
            if (!preview) check(target.displayId != 0) { "Selected primary display" }
            val launch = Intent(targetContext, CoverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            check(targetContext.getSystemService(ActivityManager::class.java)
                .isActivityStartAllowedOnDisplay(targetContext, target.displayId, launch)) { "App UID cannot launch on cover display" }
            activity = startActivitySync(launch, Platform.displayOptions(target.displayId))
            waitForIdleSync()
            check(activity.display?.displayId == target.displayId) { "Activity routed to another display" }
            val dashboard = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as CoverView
            val visited = mutableListOf<String>()
            listOf("App", "Tin", "Nhạc", "Nhanh").forEachIndexed { index, label ->
                val frame = CountDownLatch(1)
                runOnMainSync {
                    if (preview) dashboard.viewTreeObserver.registerFrameCommitCallback { frame.countDown() }
                    dashboard.openSection(label)
                    if (!Platform.unlocked(targetContext)) {
                        check(descendants(dashboard).filterIsInstance<TextView>().any { it.text.toString() == "Mở khóa để tiếp tục" }) { "Locked device exposes protected page $label" }
                    }
                    visited.add(label)
                    dashboard.invalidate()
                }
                if (preview) check(frame.await(10, TimeUnit.SECONDS)) { "No rendered frame for $label" }
                waitForIdleSync()
                if (preview) {
                    // Copy the committed app surface; a display screenshot can
                    // still contain the compositor's previous frame on emulators.
                    val bitmap = Bitmap.createBitmap(dashboard.width, dashboard.height, Bitmap.Config.ARGB_8888)
                    val copied = CountDownLatch(1)
                    var copyResult = PixelCopy.ERROR_UNKNOWN
                    runOnMainSync {
                        PixelCopy.request(activity.window, bitmap, { status -> copyResult = status; copied.countDown() }, Handler(Looper.getMainLooper()))
                    }
                    check(copied.await(10, TimeUnit.SECONDS) && copyResult == PixelCopy.SUCCESS) { "Could not copy rendered page $label: $copyResult" }
                    File(targetContext.cacheDir, "smoke-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
            runOnMainSync {
                check(dashboard.width > 0 && dashboard.height > 0) { "Dashboard not laid out" }
            }
            if (preview) check(dashboard.width == 512 && dashboard.height == 260) { "Preview must match Z Flip3 cover dimensions: ${dashboard.width}x${dashboard.height}" }
            result.putString("stream", "PASS: app UID launches on display ${target.displayId}; actual dashboard ${dashboard.width}x${dashboard.height}; pages=$visited; locked=${!Platform.unlocked(targetContext)}; mode=${if (preview) "emulator preview" else "hardware"}\n")
        } catch (e: Throwable) {
            result.putString("stream", "FAIL: ${e.stackTraceToString()}\n")
            resultCode = Activity.RESULT_CANCELED
        } finally {
            activity?.let { screen -> runOnMainSync { screen.finish() } }
        }
        finish(resultCode, result)
    }
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
}
