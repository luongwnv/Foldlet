package vn.lit.screenos

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.os.*
import android.view.*
import android.view.inspector.WindowInspector
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Verifies actual overlay type, size, input interception/pass-through and teardown. */
class OverlaySmokeTest(private val runner: Instrumentation) {
    fun run() = with(runner) {
        val result = Bundle()
        var code = Activity.RESULT_OK
        var underlay: OverlayUnderlayActivity? = null
        val prefs = Platform.preferences(targetContext)
        val oldMonitor = prefs.getBoolean("monitor", false)
        val oldPreview = prefs.getBoolean("emulator_overlay", false)
        val oldMode = prefs.getString("app_mode", null)
        val oldFavorites = prefs.getStringSet("favorites", null)?.toSet()
        try {
            check(BuildConfig.DEBUG && Build.MODEL.startsWith("sdk_gphone")) { "Overlay input test is only for the owned emulator" }
            getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            prefs.edit().putBoolean("emulator_overlay", true).apply()
            underlay = startActivitySync(Intent(targetContext, OverlayUnderlayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as OverlayUnderlayActivity
            File(targetContext.cacheDir, "overlay-await-accessibility").writeText("ready")
            await("accessibility connection") { NavigationService.current != null }
            runOnMainSync {
                targetContext.startForegroundService(Intent(targetContext, CoverMonitorService::class.java).setAction(CoverMonitorService.ACTION_START))
            }
            await("Samsung default edge") { CoverMonitorService.current?.overlay?.mode == CoverOverlay.Mode.EDGE && CoverMonitorService.current?.overlay?.view?.width == 18 }
            capture("overlay-samsung-default", requireNotNull(CoverMonitorService.current?.overlay?.window))
            swipeLeftFromRightEdge()
            await("dashboard") { CoverMonitorService.current?.overlay?.mode == CoverOverlay.Mode.DASHBOARD && CoverMonitorService.current?.overlay?.view?.width == 512 }
            val engine = requireNotNull(CoverMonitorService.current)
            var leakedRoot = false
            runOnMainSync {
                // addView registers a ViewRoot before the first traversal attaches
                // its decor. A rapid mode change must remove that pending root too.
                val extra = CoverOverlay(requireNotNull(NavigationService.current), requireNotNull(engine.overlay?.view?.display), {}, {}, {}, {}, {})
                repeat(3) {
                    extra.show(CoverOverlay.Mode.EDGE)
                    val decor = requireNotNull(extra.view)
                    check(decor in WindowInspector.getGlobalWindowViews()) { "Overlay root was not registered" }
                    extra.hide()
                    leakedRoot = leakedRoot || decor in WindowInspector.getGlobalWindowViews()
                }
            }
            check(!leakedRoot) { "Overlay root leaked before its first frame" }
            runOnMainSync {
                val window = requireNotNull(engine.overlay?.window)
                check(window.attributes.type == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) { "Not an accessibility overlay" }
                check(window.decorView.display.displayId == 0) { "Wrong preview display" }
                check(window.decorView.width == 512 && window.decorView.height == 260) { "Wrong overlay bounds" }
            }
            capture("overlay-dashboard", requireNotNull(engine.overlay?.window))
            val historyBeforeSwipes = prefs.all.filterKeys { it == "recents" || it.startsWith("launches.") }
            swipe(2f, 80f, 82f, 80f)
            await("left menu edge returns to Samsung") { engine.overlay?.mode == CoverOverlay.Mode.EDGE && engine.overlay?.view?.width == 18 }
            capture("overlay-samsung-edge", requireNotNull(engine.overlay?.window))
            swipeLeftFromRightEdge()
            await("reopen after left menu edge") { engine.overlay?.mode == CoverOverlay.Mode.DASHBOARD && engine.overlay?.view?.width == 512 }
            capture("overlay-dashboard", requireNotNull(engine.overlay?.window))
            swipeLeftFromRightEdge()
            await("right menu edge returns to Samsung") { engine.overlay?.mode == CoverOverlay.Mode.EDGE && engine.overlay?.view?.width == 18 }
            capture("overlay-samsung-edge", requireNotNull(engine.overlay?.window))
            swipeLeftFromRightEdge()
            await("reopen after right menu edge") { engine.overlay?.mode == CoverOverlay.Mode.DASHBOARD && engine.overlay?.view?.width == 512 }
            capture("overlay-dashboard", requireNotNull(engine.overlay?.window))
            prefs.edit().putStringSet("favorites", emptySet()).apply()
            runOnMainSync { engine.openDrawer("favorite") }
            await("empty favorites menu") { engine.overlay?.mode == CoverOverlay.Mode.DASHBOARD && engine.overlay?.view?.width == 512 }
            capture("overlay-empty-favorites", requireNotNull(engine.overlay?.window))
            swipeLeftFromRightEdge()
            await("empty menu edge returns to Samsung") { engine.overlay?.mode == CoverOverlay.Mode.EDGE && engine.overlay?.view?.width == 18 }
            capture("overlay-samsung-edge", requireNotNull(engine.overlay?.window))
            prefs.edit().putStringSet("favorites", oldFavorites).apply()
            runOnMainSync { engine.openDrawer(oldMode ?: "all") }
            await("restore menu after empty favorites") { engine.overlay?.mode == CoverOverlay.Mode.DASHBOARD && engine.overlay?.view?.width == 512 }
            capture("overlay-dashboard", requireNotNull(engine.overlay?.window))
            swipe(8f, 220f, 8f, 80f)
            waitForIdleSync()
            check(engine.overlay?.mode == CoverOverlay.Mode.DASHBOARD) { "Vertical edge scroll exited the menu" }
            runOnMainSync { engine.expand() }
            await("menu toolbar after scroll") { engine.overlay?.mode == CoverOverlay.Mode.DASHBOARD && engine.overlay?.view?.width == 512 }
            capture("overlay-dashboard", requireNotNull(engine.overlay?.window))
            tap(2f, 10f)
            check(engine.overlay?.mode == CoverOverlay.Mode.DASHBOARD) { "An edge tap exited the menu" }
            check(prefs.all.filterKeys { it == "recents" || it.startsWith("launches.") } == historyBeforeSwipes) { "An edge swipe accidentally launched an app" }
            tap(256f, 10f)
            waitForIdleSync()
            check(underlay.taps == 0) { "Full overlay allowed a tap to reach the underlying app" }

            runOnMainSync { engine.minimize() }
            await("right edge strip") { engine.overlay?.mode == CoverOverlay.Mode.EDGE && engine.overlay?.view?.width == 18 }
            runOnMainSync {
                check(requireNotNull(engine.overlay?.window).attributes.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
            }
            tap(400f, 120f)
            waitForIdleSync()
            check(underlay.taps == 1) { "Floating handle blocked taps outside its bounds" }
            capture("overlay-underlay", underlay.window)
            capture("overlay-edge", requireNotNull(engine.overlay?.window))

            swipeLeftFromRightEdge()
            await("right navigation") { engine.overlay?.mode == CoverOverlay.Mode.SIDEBAR && engine.overlay?.view?.width == 48 }
            capture("overlay-navigation", requireNotNull(engine.overlay?.window))
            tap(488f, 97f)
            await("return from navigation") { engine.overlay?.mode == CoverOverlay.Mode.DASHBOARD && engine.overlay?.view?.width == 512 && engine.overlay?.view?.isAttachedToWindow == true }
            capture("overlay-restored-dashboard", requireNotNull(engine.overlay?.window))
            tap(256f, 10f)
            waitForIdleSync()
            check(underlay.taps == 1) { "Restored dashboard did not intercept touches" }

            runOnMainSync { engine.showSamsungHome() }
            await("Samsung home edge") { engine.overlay?.mode == CoverOverlay.Mode.EDGE && engine.overlay?.view?.width == 18 }
            capture("overlay-samsung-edge", requireNotNull(engine.overlay?.window))
            swipeLeftFromRightEdge()
            await("Samsung home edge reopens drawer") { engine.overlay?.mode == CoverOverlay.Mode.DASHBOARD && engine.overlay?.view?.width == 512 }

            runOnMainSync { engine.yieldToStock(); engine.schedule() }
            await("yield to stock") { engine.overlay == null }
            waitForIdleSync()
            tap(400f, 120f)
            waitForIdleSync()
            check(underlay.taps == 2) { "Yielding did not release the overlay window" }
            runOnMainSync { engine.expand() }
            await("explicit restore") { engine.overlay?.mode == CoverOverlay.Mode.DASHBOARD }
            runOnMainSync { targetContext.startService(Intent(targetContext, CoverMonitorService::class.java).setAction(CoverMonitorService.ACTION_STOP)) }
            await("overlay teardown") { CoverMonitorService.current == null && engine.overlay == null }
            result.putString("stream", "PASS: Samsung default keeps only 18px edge; edge swipe opens drawer; BOTH menu edge swipes return to Samsung without launching apps; empty favorites edge also exits; vertical edge scroll and edge tap stay in menu; pending roots removed; real type=2032 overlay 512x260; dashboard intercepts input; outside touches pass through; app edge opens 48px navigation; drawer and Samsung buttons switch modes; yield/stop remove windows; underlay taps=2\n")
        } catch (e: Throwable) {
            code = Activity.RESULT_CANCELED
            result.putString("stream", "FAIL: ${e.stackTraceToString()}\n")
        } finally {
            runOnMainSync {
                targetContext.stopService(Intent(targetContext, CoverMonitorService::class.java))
                prefs.edit().putBoolean("monitor", oldMonitor).putBoolean("emulator_overlay", oldPreview)
                    .putString("app_mode", oldMode).putStringSet("favorites", oldFavorites).apply()
                underlay?.finish()
                File(targetContext.cacheDir, "overlay-await-accessibility").delete()
            }
        }
        finish(code, result)
    }

    private fun await(label: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            runner.runOnMainSync { ready = predicate() }
            if (ready) return
            SystemClock.sleep(40)
        }
        error("Timed out waiting for $label; status=${Platform.preferences(runner.targetContext).getString("monitor_status", null)}; error=${Platform.preferences(runner.targetContext).getString("monitor_error", null)}")
    }
    private fun tap(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0).let { runner.sendPointerSync(it); it.recycle() }
        MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0).let { runner.sendPointerSync(it); it.recycle() }
    }
    private fun swipeLeftFromRightEdge() = swipe(510f, 130f, 430f, 130f)
    private fun swipe(startX: Float, startY: Float, endX: Float, endY: Float) {
        val now = SystemClock.uptimeMillis()
        MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, startX, startY, 0).let { runner.sendPointerSync(it); it.recycle() }
        for (step in 1..6) MotionEvent.obtain(now, now + step * 20, MotionEvent.ACTION_MOVE, startX + (endX - startX) * step / 6, startY + (endY - startY) * step / 6, 0).let { runner.sendPointerSync(it); it.recycle() }
        MotionEvent.obtain(now, now + 140, MotionEvent.ACTION_UP, endX, endY, 0).let { runner.sendPointerSync(it); it.recycle() }
    }
    private fun capture(name: String, window: Window) {
        val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        val frame = CountDownLatch(1)
        runner.runOnMainSync {
            window.decorView.viewTreeObserver.registerFrameCommitCallback { frame.countDown() }
            window.decorView.invalidate()
        }
        check(frame.await(10, TimeUnit.SECONDS)) { "No rendered frame: $name" }
        val copied = CountDownLatch(1)
        var status = PixelCopy.ERROR_UNKNOWN
        runner.runOnMainSync { PixelCopy.request(window, bitmap, { status = it; copied.countDown() }, Handler(Looper.getMainLooper())) }
        check(copied.await(10, TimeUnit.SECONDS) && status == PixelCopy.SUCCESS) { "PixelCopy failed: $name" }
        File(runner.targetContext.cacheDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
