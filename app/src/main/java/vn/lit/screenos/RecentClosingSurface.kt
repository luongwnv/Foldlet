package vn.lit.screenos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.content.res.ColorStateList
import android.os.*
import android.view.*
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.ProgressBar

/** Keeps only Foldlet's own rendered menu visible during a native task action. */
@android.annotation.TargetApi(34)
class RecentClosingSurface private constructor(private val service: NavigationService,private val display: Display) {
    private val handler=Handler(Looper.getMainLooper())
    private var host: SurfaceControlViewHost?=null
    private var surfacePackage: SurfaceControlViewHost.SurfacePackage?=null
    private var image: ImageView?=null
    private var released=false
    private var ready=false
    private var readyCallback: (() -> Unit)?=null
    private var failedCallback: (() -> Unit)?=null
    private val timeout=Runnable { if(!ready && !released) fail() }
    private val privacy=object: Runnable {
        override fun run() {
            if(released) return
            if(!Platform.unlocked(service) || display.state!=Display.STATE_ON || CoverMonitorService.current?.foldedForTaskControl()==false) { if(readyCallback!=null) fail() else close();return }
            handler.postDelayed(this,100)
        }
    }
    fun whenReady(done: ()->Unit,failed: ()->Unit) {
        if(released) { failed();return }
        readyCallback=done;failedCallback=failed
        if(ready) { readyCallback=null;failedCallback=null;done() }
    }
    private fun attach(source: View,loadingButton: View?) {
        require(source.width>0 && source.height>0 && source.isAttachedToWindow)
        val bitmap=Bitmap.createBitmap(source.width,source.height,Bitmap.Config.ARGB_8888)
        val loadingBounds=Rect()
        if(loadingButton?.getGlobalVisibleRect(loadingBounds)==true) {
            val origin=IntArray(2);source.getLocationOnScreen(origin)
            loadingBounds.offset(-origin[0],-origin[1])
        }
        // The caller removes the pending action's glyph or label before rendering.
        // A separate animated ProgressBar replaces either X or Close all.
        source.draw(Canvas(bitmap)) // Own views only; no capture of other apps.
        val context=ContextThemeWrapper(service.createDisplayContext(display),R.style.CoverTheme)
        val view=ImageView(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            scaleType=ImageView.ScaleType.FIT_XY;setImageBitmap(bitmap)
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setOnTouchListener { _,_ -> true }
        }
        image=view
        val content=FrameLayout(context).apply {
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setOnTouchListener { _,_ -> true }
            addView(view,FrameLayout.LayoutParams(-1,-1))
            if(!loadingBounds.isEmpty) {
                val size=minOf(Ui.dp(context,24),loadingBounds.width(),loadingBounds.height())
                addView(ProgressBar(context,null,android.R.attr.progressBarStyleSmall).apply {
                    isIndeterminate=true;indeterminateTintList=ColorStateList.valueOf(android.graphics.Color.WHITE)
                    contentDescription=loadingButton?.stateDescription ?: I18n.text(R.string.ui_closing_app)
                },FrameLayout.LayoutParams(size,size).apply {
                    leftMargin=loadingBounds.centerX()-size/2;topMargin=loadingBounds.centerY()-size/2
                })
            }
        }
        @Suppress("DEPRECATION")
        val value=SurfaceControlViewHost(context,display,Binder())
        host=value;value.setView(content,source.width,source.height)
        val pkg=requireNotNull(value.surfacePackage);surfacePackage=pkg
        view.viewTreeObserver.registerFrameCommitCallback {
            handler.post {
                if(!released && !ready) {
                    ready=true;handler.removeCallbacks(timeout)
                    // Keep the original menu for another frame until this buffer
                    // is submitted, then let the native action start underneath.
                    view.postOnAnimation { if(!released) { val callback=readyCallback;readyCallback=null;failedCallback=null;callback?.invoke() } }
                }
            }
        }
        service.attachAccessibilityOverlayToDisplay(display.displayId,pkg.surfaceControl)
        SurfaceControl.Transaction().use { it.setLayer(pkg.surfaceControl,1_000_000).setVisibility(pkg.surfaceControl,true).apply() }
        handler.postDelayed(timeout,1200);handler.post(privacy)
    }
    private fun fail() { val callback=failedCallback;close();callback?.invoke() }
    fun close() {
        if(released) return
        released=true;handler.removeCallbacksAndMessages(null)
        readyCallback=null;failedCallback=null
        surfacePackage?.let { pkg -> runCatching { SurfaceControl.Transaction().use { it.reparent(pkg.surfaceControl,null).apply() } };pkg.release() }
        surfacePackage=null
        image?.setImageDrawable(null);image=null
        host?.release();host=null
    }
    companion object {
        var status="idle";private set
        fun create(service: NavigationService,display: Display,source: View,loadingButton: View?=null): RecentClosingSurface? {
            if(Build.VERSION.SDK_INT<34) return null
            val value=RecentClosingSurface(service,display)
            return try { value.attach(source,loadingButton);status="attached";value } catch(e: RuntimeException) { status="${e.javaClass.simpleName}: ${e.message}".take(200);value.close();null }
        }
    }
}
