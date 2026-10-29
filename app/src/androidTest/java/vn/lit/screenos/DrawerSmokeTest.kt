package vn.lit.screenos

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.os.*
import android.view.*
import android.widget.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DrawerSmokeTest(private val runner: Instrumentation) {
    fun run() = with(runner) {
        val result = Bundle()
        var code = Activity.RESULT_OK
        var activity: CoverActivity? = null
        val prefs = Platform.preferences(targetContext)
        val original = prefs.all
        try {
            check(BuildConfig.DEBUG && Build.MODEL.startsWith("sdk_gphone")) { "Drawer test only operates on the empty emulator" }
            prefs.edit().clear().putBoolean("emulator_overlay", true).apply()
            activity = startActivitySync(Intent(targetContext, CoverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CoverActivity
            fun dashboard(): CoverView = descendants(activity!!.window.decorView).filterIsInstance<CoverView>().first()
            runOnMainSync { dashboard().openSection("apps") }
            waitForIdleSync()
            fun toolbar(description: String) = runOnMainSync {
                descendants(activity!!.window.decorView).first { it.contentDescription?.toString() == description }.performClick()
            }
            fun names(): List<String> {
                var names = emptyList<String>()
                runOnMainSync { names = descendants(activity!!.window.decorView).filterIsInstance<FrameLayout>().mapNotNull { it.contentDescription?.toString() }.toList() }
                return names
            }
            val initial = names()
            val clock = initial.first { it.equals("Clock", ignoreCase = true) }
            check(initial.indexOfFirst { it == "Calendar" } < initial.indexOf(clock)) { "A-Z initial ordering is wrong" }
            runOnMainSync {
                val tiles = descendants(activity!!.window.decorView).filterIsInstance<FrameLayout>().filter { it.contentDescription != null }.toList()
                check(tiles.take(4).all { it.width == 128 }) { "Columns do not have a fixed 128px width" }
                check((tiles.first().getChildAt(0) as ImageView).width == 112) { "App icon must be 112px on the cover-sized emulator" }
                val buttons = descendants(activity!!.window.decorView).filterIsInstance<ImageButton>().toList()
                check(buttons.all { it.drawable.intrinsicWidth == it.drawable.intrinsicHeight }) { "Toolbar glyphs do not have square proportions" }
            }
            capture(activity!!.window, "drawer-grid")
            var scroller: ScrollView? = null
            var commandBar: View? = null
            runOnMainSync {
                scroller = descendants(activity!!.window.decorView).filterIsInstance<ScrollView>().first()
                commandBar = descendants(activity!!.window.decorView).first { it.contentDescription?.toString() == "Tìm kiếm" }.parent as View
                scroller!!.scrollTo(0, 140)
            }
            await("scroll down hides toolbar") { scroller!!.scrollY > 0 && commandBar!!.translationY <= -35f }
            capture(activity!!.window, "drawer-scrolled")
            runOnMainSync { scroller!!.scrollTo(0, 0) }
            await("scroll up restores toolbar") { commandBar!!.translationY == 0f }
            toolbar("Tìm kiếm")
            runOnMainSync { descendants(activity!!.window.decorView).filterIsInstance<EditText>().first().setText("cloc") }
            check(names() == listOf(clock)) { "Search did not filter to Clock: ${names()}" }
            capture(activity!!.window, "drawer-search")
            runOnMainSync { descendants(activity!!.window.decorView).filterIsInstance<EditText>().first().setText("___no_such_app___") }
            check(names().isEmpty()) { "An unmatched search still shows apps" }
            toolbar("Đóng tìm kiếm")
            check(names().size == initial.size) { "Closing search did not restore app list" }

            runOnMainSync { descendants(activity!!.window.decorView).first { it.contentDescription?.toString() == clock }.performLongClick() }
            toolbar("Yêu thích $clock")
            check(prefs.getStringSet("favorites", emptySet())!!.size == 1) { "Favorite was not saved" }
            toolbar("Bỏ chọn")
            toolbar("Yêu thích")
            check(names() == listOf(clock)) { "Favorites filter did not isolate selected app" }
            runOnMainSync {
                val tile = descendants(activity!!.window.decorView).filterIsInstance<FrameLayout>().first { it.contentDescription?.toString() == clock }
                val position = IntArray(2); tile.getLocationOnScreen(position)
                check(tile.width == 128 && position[0] == 0) { "Single favorite drifted out of the first grid column: x=${position[0]} width=${tile.width}" }
            }
            capture(activity!!.window, "drawer-favorite")
            toolbar("Yêu thích")

            toolbar("Sắp xếp")
            runOnMainSync { descendants(activity!!.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "Z → A" }.performClick() }
            check(names().indexOf(clock) < names().indexOf("Calendar")) { "Z-A sort failed" }
            toolbar("Sắp xếp")
            runOnMainSync { descendants(activity!!.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "A → Z" }.performClick() }
            check(names().indexOf("Calendar") < names().indexOf(clock)) { "A-Z sort failed" }

            runOnMainSync { descendants(activity!!.window.decorView).first { it.contentDescription?.toString() == clock }.performLongClick() }
            toolbar("Ẩn $clock")
            check(clock !in names()) { "Hidden app remains visible" }
            prefs.edit().remove("hidden").apply()
            toolbar("Sắp xếp")
            runOnMainSync { descendants(activity!!.window.decorView).filterIsInstance<Button>().first { it.text.toString() == "A → Z" }.performClick() }

            runOnMainSync { descendants(activity!!.window.decorView).first { it.contentDescription?.toString() == clock }.performClick() }
            check(!prefs.getString("recents", "").isNullOrBlank()) { "Successful launch was not added to recent apps" }
            activity = startActivitySync(Intent(targetContext, CoverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CoverActivity
            runOnMainSync { dashboard().openSection("apps") }
            toolbar("Gần đây")
            check(names() == listOf(clock)) { "Recents did not retain actual launched app" }
            capture(activity!!.window, "drawer-recent")
            check(prefs.getStringSet("favorites", emptySet())!!.size == 1) { "Favorite did not survive activity recreation" }
            result.putString("stream", "PASS: fixed 4-column grid with 112px icons; square toolbar glyphs; scroll down hides toolbar and scroll up restores it; single favorite stays in column 1; live search/no-results; favorites persist/filter; A-Z/Z-A; hiding; real Clock launch recorded in recents\n")
        } catch (error: Throwable) {
            code = Activity.RESULT_CANCELED
            result.putString("stream", "FAIL: ${error.stackTraceToString()}\n")
        } finally {
            runOnMainSync { activity?.finish(); CoverRotation.clear() }
            val edit = prefs.edit().clear()
            original.forEach { (key, value) -> when (value) {
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
            } }
            edit.apply()
        }
        finish(code, result)
    }
    private fun await(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            runner.runOnMainSync { ready = condition() }
            if (ready) return
            SystemClock.sleep(40)
        }
        error("Timed out: $label")
    }
    private fun descendants(view: View): Sequence<View> = sequence { yield(view); if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i))) }
    private fun capture(window: Window, name: String) {
        val frame = CountDownLatch(1)
        runner.runOnMainSync { window.decorView.viewTreeObserver.registerFrameCommitCallback { frame.countDown() }; window.decorView.invalidate() }
        check(frame.await(10, TimeUnit.SECONDS))
        val bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1); var status = -1
        runner.runOnMainSync { PixelCopy.request(window, bitmap, { status = it; copied.countDown() }, Handler(Looper.getMainLooper())) }
        check(copied.await(10, TimeUnit.SECONDS) && status == PixelCopy.SUCCESS)
        File(runner.targetContext.cacheDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
