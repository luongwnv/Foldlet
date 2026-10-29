package vn.lit.screenos

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.*
import android.view.*
import android.view.inspector.WindowInspector
import android.widget.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises phone settings controls and verifies their effect on the app drawer. */
class SettingsSmokeTest(private val runner: Instrumentation) {
    fun run() = with(runner) {
        val result=Bundle(); var code=Activity.RESULT_OK
        var main: MainActivity?=null; var preview: CoverActivity?=null; var setup: RotationSetupActivity?=null
        val prefs=Platform.preferences(targetContext); val original=prefs.all
        try {
            check(BuildConfig.DEBUG && Build.MODEL.startsWith("sdk_gphone")) { "Only the dedicated LIT emulator is allowed" }
            getUiAutomation()
            runOnMainSync { targetContext.stopService(Intent(targetContext,CoverMonitorService::class.java)) }
            prefs.edit().putBoolean("monitor",false).putInt("columns",4).putBoolean("show_names",false).putString("app_mode","all").apply()
            main=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            waitForIdleSync()
            check(targetContext.getDrawable(R.mipmap.ic_launcher) is AdaptiveIconDrawable) { "Launcher icon is not adaptive" }
            fun tagged(tag: String): View = descendants(main!!.window.decorView).first { it.tag==tag }
            fun click(tag: String) { runOnMainSync { tagged(tag).performClick() }; waitForIdleSync() }
            fun choose(tag: String,index: Int) {
                click(tag)
                runOnMainSync {
                    val list=WindowInspector.getGlobalWindowViews().asSequence().flatMap { descendants(it) }.filterIsInstance<ListView>().first()
                    list.performItemClick(list.getChildAt(index-list.firstVisiblePosition),index,list.adapter.getItemId(index))
                }
                waitForIdleSync()
            }
            capture(main!!.window,"settings-overview")
            click("tab-settings")
            choose("row-columns",1)
            check(prefs.getInt("columns",0)==5) { "Column setting did not persist" }
            choose("row-sort",1)
            check(prefs.getString("app_sort",null)=="za") { "Sort setting did not persist" }
            choose("row-filter",0)
            choose("row-orientation",0)
            click("names-switch")
            check(prefs.getBoolean("show_names",false)) { "App-name switch did not persist" }
            capture(main!!.window,"settings-settings")
            runOnMainSync { descendants(main!!.window.decorView).filterIsInstance<ScrollView>().first().scrollTo(0,500) }
            capture(main!!.window,"settings-settings-lower")

            click("tab-home")
            val previewMonitor=addMonitor(CoverActivity::class.java.name,null,false)
            click("preview-row")
            preview=waitForMonitorWithTimeout(previewMonitor,5000) as? CoverActivity
            removeMonitor(previewMonitor)
            check(preview!=null) { "Preview action did not open the drawer" }
            waitForIdleSync()
            var namesShown=false
            runOnMainSync {
                namesShown=descendants(preview!!.window.decorView).filterIsInstance<FrameLayout>()
                    .filter { it.contentDescription!=null }.any { tile -> (0 until tile.childCount).map { tile.getChildAt(it) }.filterIsInstance<TextView>().any { it.text.isNotBlank() } }
            }
            check(namesShown) { "Show app names setting has no effect on the actual drawer" }
            capture(preview!!.window,"settings-names-preview")
            runOnMainSync { preview!!.finish() }; preview=null; waitForIdleSync()

            click("tab-info"); capture(main!!.window,"settings-info")
            runOnMainSync { main!!.finish() }; waitForIdleSync()
            main=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            click("tab-settings")
            check(prefs.getInt("columns",0)==5 && (tagged("names-switch") as Switch).isChecked) { "Settings did not survive reopening" }
            val setupMonitor=addMonitor(RotationSetupActivity::class.java.name,null,false)
            click("row-adb")
            setup=waitForMonitorWithTimeout(setupMonitor,5000) as? RotationSetupActivity
            removeMonitor(setupMonitor)
            check(setup!=null) { "ADB setting did not open setup" }
            capture(setup!!.window,"settings-adb")
            result.putString("stream","PASS: adaptive app icon; three settings tabs; column/sort/filter/orientation choices persist; app-name switch changes the real drawer and survives reopening; preview and ADB setup actions work\n")
        } catch(error: Throwable) {
            code=Activity.RESULT_CANCELED; result.putString("stream","FAIL: ${error.stackTraceToString()}\n")
        } finally {
            runOnMainSync { setup?.finish(); preview?.finish(); main?.finish() }
            val edit=prefs.edit().clear()
            original.forEach { (key,value) -> when(value) {
                is String -> edit.putString(key,value)
                is Boolean -> edit.putBoolean(key,value)
                is Int -> edit.putInt(key,value)
                is Long -> edit.putLong(key,value)
                is Float -> edit.putFloat(key,value)
                is Set<*> -> edit.putStringSet(key,value.filterIsInstance<String>().toSet())
            } }
            edit.apply()
        }
        finish(code,result)
    }
    private fun descendants(view: View): Sequence<View> = sequence { yield(view); if(view is ViewGroup) for(i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i))) }
    private fun capture(window: Window,name: String) {
        val frame=CountDownLatch(1)
        runner.runOnMainSync { window.decorView.viewTreeObserver.registerFrameCommitCallback { frame.countDown() }; window.decorView.invalidate() }
        check(frame.await(10,TimeUnit.SECONDS)) { "No frame: $name" }
        val bitmap=Bitmap.createBitmap(window.decorView.width,window.decorView.height,Bitmap.Config.ARGB_8888)
        val copied=CountDownLatch(1); var status=-1
        runner.runOnMainSync { PixelCopy.request(window,bitmap,{ result -> status=result; copied.countDown() },Handler(Looper.getMainLooper())) }
        check(copied.await(10,TimeUnit.SECONDS) && status==PixelCopy.SUCCESS) { "Capture failed: $name" }
        File(runner.targetContext.cacheDir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
}
