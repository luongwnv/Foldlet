package vn.lit.screenos

import android.content.Context
import android.graphics.*
import android.view.*

/** Bounded home touch targets with separate tap and top-edge windows. */
class HomeGestureOverlay(
    private val context: Context,
    private val manager: WindowManager,
    private val base: WindowManager.LayoutParams,
    private val display: Display,
    private val onTap: () -> Unit,
    private val onNavigation: () -> Unit,
    private val onShortcuts: () -> Unit,
) {
    private val windows=ArrayList<View>()
    fun show() {
        val config=HomeTouchConfig.read(context)
        val size=Point();@Suppress("DEPRECATION") display.getRealSize(size)
        val area=config.bounds(size.x,size.y)
        val shortcuts=Platform.preferences(context).getBoolean("home_shortcuts_enabled",true)
        fun add(view: View,rect: Rect,title: String) {
            val params=WindowManager.LayoutParams().apply {
                copyFrom(base);width=rect.width();height=rect.height();x=rect.left;y=rect.top
                gravity=Gravity.TOP or Gravity.LEFT;setTitle(title)
                flags=flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            }
            // Keep pending roots too, so a page/fold change removes every window.
            windows.add(view);manager.addView(view,params)
        }
        if(config.enabled) {
            val target=object: View(context) {
                private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0x70FFFFFF }
                override fun onDraw(canvas: Canvas) {
                    if(!config.indicator) return
                    val thickness=Ui.dp(context,3).toFloat()
                    val length=Ui.dp(context,28).toFloat()
                    when {
                        config.horizontal>=75 -> canvas.drawRoundRect(width-thickness,(height-length)/2,width.toFloat(),(height+length)/2,2f,2f,paint)
                        config.horizontal<=25 -> canvas.drawRoundRect(0f,(height-length)/2,thickness,(height+length)/2,2f,2f,paint)
                        config.vertical>=50 -> canvas.drawRoundRect((width-length)/2,height-thickness,(width+length)/2,height.toFloat(),2f,2f,paint)
                        else -> canvas.drawRoundRect((width-length)/2,0f,(width+length)/2,thickness,2f,2f,paint)
                    }
                }
                override fun performClick(): Boolean { super.performClick();onTap();return true }
            }.apply { isClickable=true;contentDescription=I18n.text(R.string.ui_home_tap_apps) }
            target.setOnTouchListener(listener(context,{target.performClick()},onNavigation,if(shortcuts) onShortcuts else null))
            add(target,area,TAP_TITLE)
        }
        if(shortcuts) {
            val top=object: View(context) {
                override fun performClick(): Boolean { super.performClick();onShortcuts();return true }
            }.apply { isClickable=true;contentDescription=I18n.text(R.string.ui_home_swipe_shortcuts) }
            top.setOnTouchListener(listener(context,{ event -> if(config.enabled && area.contains(event.rawX.toInt(),event.rawY.toInt())) onTap() },onNavigation,onShortcuts))
            add(top,Rect(0,0,size.x,Ui.dp(context,40).coerceAtMost(size.y)),TOP_TITLE)
        }
    }
    fun hide() {
        windows.asReversed().forEach { view -> runCatching { manager.removeViewImmediate(view) } }
        windows.clear()
    }
    companion object {
        const val TAP_TITLE="Foldlet · home tap area"
        const val TOP_TITLE="Foldlet · home shortcuts edge"
        fun listener(context: Context,tap: (MotionEvent)->Unit,navigation: ()->Unit,shortcuts: (() -> Unit)?=null): View.OnTouchListener {
            var x=0f;var y=0f;var candidate=false;var consumed=false;var canceled=false
            val slop=ViewConfiguration.get(context).scaledTouchSlop.coerceAtLeast(Ui.dp(context,12))
            val threshold=Ui.dp(context,24)
            return View.OnTouchListener { _,e ->
                when(e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { x=e.rawX;y=e.rawY;candidate=true;consumed=false;canceled=false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx=e.rawX-x;val dy=e.rawY-y
                        if(kotlin.math.abs(dx)>slop || kotlin.math.abs(dy)>slop) candidate=false
                        if(!consumed && !canceled) {
                            if(dx < -threshold && -dx>kotlin.math.abs(dy)*1.25f) { consumed=true;navigation() }
                            else if(shortcuts!=null && dy>threshold && dy>kotlin.math.abs(dx)*1.25f) { consumed=true;shortcuts() }
                            else if(kotlin.math.abs(dy)>slop && kotlin.math.abs(dy)>kotlin.math.abs(dx)*1.25f && (dy<0 || shortcuts==null)) canceled=true
                        }
                    }
                    MotionEvent.ACTION_UP -> { if(candidate && !consumed && !canceled && kotlin.math.abs(e.rawX-x)<=slop && kotlin.math.abs(e.rawY-y)<=slop) tap(e);candidate=false }
                    MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_POINTER_DOWN -> { candidate=false;canceled=true }
                }
                true
            }
        }
    }
}
