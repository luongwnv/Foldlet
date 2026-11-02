package vn.lit.screenos

import android.app.Notification
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*

/** On-device notification list, shown in place of Samsung's cover-only list. */
@android.annotation.SuppressLint("ViewConstructor")
class CoverNotificationView(context: Context,private val displayId: Int,private val home: ()->Unit,private val opened: ()->Unit): LinearLayout(context) {
    private val body=FrameLayout(context)
    private var opening=false
    private val handler=Handler(Looper.getMainLooper())
    private val changed: ()->Unit={ if(isAttachedToWindow && !opening) render() }
    private val privacy=object: Runnable {
        override fun run() {
            if(!Platform.unlocked(context)) { body.removeAllViews();home();return }
            handler.postDelayed(this,200)
        }
    }
    init {
        orientation=VERTICAL;setBackgroundColor(Color.BLACK)
        addView(body,LayoutParams(-1,0,1f))
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow();CoverNotifications.observe(changed);render();handler.post(privacy) }
    override fun onDetachedFromWindow() { opening=false;CoverNotifications.removeObserver(changed);handler.removeCallbacksAndMessages(null);body.removeAllViews();super.onDetachedFromWindow() }
    private fun render() {
        val scrollY=body.getChildAt(0)?.scrollY ?: 0
        body.removeAllViews()
        if(!Platform.unlocked(context)) return
        val listener=CoverNotifications.current
        if(listener==null) { body.addView(text(I18n.text(R.string.ui_allow_notification_access_on_the_main_screen),16f));return }
        val entries=listener.snapshot()
        if(entries.isEmpty()) { body.addView(text(I18n.text(R.string.ui_no_notifications),17f).apply { gravity=Gravity.CENTER },FrameLayout.LayoutParams(-1,-1));return }
        val column=LinearLayout(context).apply { orientation=VERTICAL;setPadding(Ui.dp(context,8),0,Ui.dp(context,8),Ui.dp(context,8)) }
        val scroll=ScrollView(context).apply { isVerticalScrollBarEnabled=false;addView(column) }
        body.addView(scroll,FrameLayout.LayoutParams(-1,-1))
        scroll.post { if(scroll.isAttachedToWindow) scroll.scrollTo(0,scrollY) }
        entries.forEach { entry ->
            val info=runCatching { context.packageManager.getApplicationInfo(entry.packageName,0) }.getOrNull()
            val label=runCatching { info?.loadLabel(context.packageManager)?.toString() }.getOrNull() ?: entry.packageName
            val extras=entry.notification.extras
            val title=extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val message=(extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
            val card=LinearLayout(context).apply {
                orientation=HORIZONTAL;gravity=Gravity.TOP
                setPadding(Ui.dp(context,12),Ui.dp(context,10),Ui.dp(context,12),Ui.dp(context,10))
                background=GradientDrawable().apply { setColor(0xFF1C1C1C.toInt());cornerRadius=Ui.dp(context,16).toFloat() }
                isClickable=true;isFocusable=true;contentDescription="$label: $title"
                tag="notification:${entry.key}"
            }
            val icon=runCatching { info?.loadIcon(context.packageManager) }.getOrNull()
            card.addView(ImageView(context).apply { setImageDrawable(icon);scaleType=ImageView.ScaleType.FIT_CENTER },LayoutParams(Ui.dp(context,36),Ui.dp(context,36)).apply { rightMargin=Ui.dp(context,12) })
            val words=LinearLayout(context).apply { orientation=VERTICAL }
            words.addView(text(label,12f).apply { setTextColor(0xFFABB3BB.toInt()) })
            if(title.isNotBlank()) words.addView(text(title,17f,true).apply { maxLines=2 })
            if(message.isNotBlank()) words.addView(text(message,15f).apply { maxLines=3 })
            card.addView(words,LayoutParams(0,-2,1f))
            card.setOnClickListener {
                if(opening || !isAttachedToWindow) return@setOnClickListener
                opening=true;card.isEnabled=false
                CoverNotificationOpener.open(context,entry,displayId,valid={ isAttachedToWindow && opening },opened={ if(isAttachedToWindow) opened() },failed={ opening=false;if(isAttachedToWindow) render() })
            }
            column.addView(card,LayoutParams(-1,-2).apply { topMargin=Ui.dp(context,6) })
        }
    }
    private fun text(value: String,size: Float,bold: Boolean=false)=TextView(context).apply {
        text=value;textSize=size;setTextColor(Color.WHITE)
        if(bold) setTypeface(null,Typeface.BOLD)
    }
    private var downX=0f
    private var downY=0f
    private var edge=false
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if(event.actionMasked==MotionEvent.ACTION_DOWN) { downX=event.x;downY=event.y;edge=downX<Ui.dp(context,24) || downX>width-Ui.dp(context,24) }
        if(event.actionMasked==MotionEvent.ACTION_POINTER_DOWN) edge=false
        if(edge && event.actionMasked==MotionEvent.ACTION_MOVE) {
            val dx=event.x-downX;val dy=event.y-downY
            if(kotlin.math.abs(dy)>Ui.dp(context,12) && kotlin.math.abs(dy)>kotlin.math.abs(dx)) edge=false
            else if(kotlin.math.abs(dx)>Ui.dp(context,48) && kotlin.math.abs(dx)>kotlin.math.abs(dy)*1.3f) {
                MotionEvent.obtain(event).also { it.action=MotionEvent.ACTION_CANCEL;super.dispatchTouchEvent(it);it.recycle() }
                edge=false;home();return true
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
