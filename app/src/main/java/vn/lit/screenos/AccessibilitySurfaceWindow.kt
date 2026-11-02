package vn.lit.screenos

import android.os.Binder
import android.view.*

/** Public Android 14 accessibility overlay, including on Samsung's Settings. */
@android.annotation.TargetApi(34)
class AccessibilitySurfaceWindow private constructor() {
    private var host: SurfaceControlViewHost?=null
    private var surface: SurfaceControlViewHost.SurfacePackage?=null
    private fun attach(service: NavigationService,display: Display,view: View,width: Int,height: Int,x: Int,y: Int) {
        val context=ContextThemeWrapper(service.createDisplayContext(display),R.style.CoverTheme)
        @Suppress("DEPRECATION")
        val value=SurfaceControlViewHost(context,display,Binder())
        host=value;value.setView(view,width,height)
        val pkg=requireNotNull(value.surfacePackage);surface=pkg
        service.attachAccessibilityOverlayToDisplay(display.displayId,pkg.surfaceControl)
        SurfaceControl.Transaction().use {
            it.setPosition(pkg.surfaceControl,x.toFloat(),y.toFloat()).setLayer(pkg.surfaceControl,800_000).setVisibility(pkg.surfaceControl,true).apply()
        }
    }
    fun close() {
        surface?.let { pkg ->
            runCatching { SurfaceControl.Transaction().use { it.reparent(pkg.surfaceControl,null).apply() } }
            pkg.release()
        }
        surface=null;host?.release();host=null
    }
    companion object {
        fun create(service: NavigationService,display: Display,view: View,width: Int,height: Int,x: Int,y: Int): AccessibilitySurfaceWindow? {
            val value=AccessibilitySurfaceWindow()
            return try { value.attach(service,display,view,width,height,x,y);value } catch(_: RuntimeException) { value.close();null }
        }
    }
}
