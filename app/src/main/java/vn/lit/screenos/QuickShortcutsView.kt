package vn.lit.screenos

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.*

class QuickShortcutsView(context: Context,private val displayId: Int,private val close: ()->Unit,private val launched: ()->Unit): LinearLayout(context) {
    private val body=FrameLayout(context)
    private val radioTiles=mutableMapOf<String,android.view.View>()
    private val handler=Handler(Looper.getMainLooper())
    private var radioState=RadioControls.Result(false)
    private var radioBusy=false
    private var readingRadios=false
    private val poll=Runnable { refreshRadios() }
    private var downX=0f
    private var downY=0f
    private var dismissCandidate=false
    private var dismissing=false
    private val swipeThreshold=ViewConfiguration.get(context).scaledTouchSlop.coerceAtLeast(Ui.dp(context,24))
    init {
        orientation=VERTICAL;setBackgroundColor(Color.BLACK)
        addView(body,LayoutParams(-1,-1))
        render()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow();refreshRadios() }
    override fun onDetachedFromWindow() { handler.removeCallbacks(poll);super.onDetachedFromWindow() }
    private fun refreshRadios() {
        if(!isAttachedToWindow || readingRadios) return
        readingRadios=true
        RadioControls.request(context) { state ->
            readingRadios=false
            if(isAttachedToWindow) {
                radioState=state;updateRadioTiles()
                handler.removeCallbacks(poll);handler.postDelayed(poll,1000)
            }
        }
    }
    private fun updateRadioTiles() {
        radioTiles.forEach { (kind,tile) ->
            val state=if(kind=="wifi") radioState.wifi else radioState.bluetooth
            ((tile.background as android.graphics.drawable.RippleDrawable).getDrawable(0) as GradientDrawable).setColor(if(state==1) 0xFF006F95.toInt() else 0xFF1C252E.toInt())
            tile.alpha=if(state==2 || radioBusy) .55f else 1f
            val label=I18n.text(if(kind=="wifi") R.string.ui_shortcut_wifi else R.string.ui_shortcut_bluetooth)
            tile.contentDescription=if(state in 0..1) "$label, ${I18n.text(if(state==1) R.string.ui_on else R.string.ui_off)}" else label
        }
    }
    private fun openDetails(action: QuickShortcuts.Action) {
        if(QuickShortcuts.open(context,action,displayId)) launched() else openOnPhone(action)
    }
    private fun activate(action: QuickShortcuts.Action) {
        if(!Platform.unlocked(context)) { render();return }
        when(action.id) {
            "wifi","bluetooth" -> {
                if(radioBusy) return
                radioBusy=true;updateRadioTiles()
                RadioControls.request(context,action.id) { state ->
                    radioBusy=false
                    if(!isAttachedToWindow) return@request
                    radioState=state;updateRadioTiles()
                    if(!state.installed) Platform.message(context,I18n.text(R.string.ui_controls_install))
                    else if(!state.accepted) Platform.message(context,I18n.text(R.string.ui_toggle_unavailable))
                    handler.removeCallbacks(poll);handler.postDelayed(poll,300)
                }
            }
            "sound" -> {
                val audio=context.getSystemService(AudioManager::class.java)
                try { audio.ringerMode=if(audio.ringerMode==AudioManager.RINGER_MODE_NORMAL) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL }
                catch(_: SecurityException) { Platform.message(context,I18n.text(R.string.ui_toggle_unavailable)) }
            }
            "display","settings" -> openDetails(action)
            else -> Platform.message(context,I18n.text(R.string.ui_toggle_restricted))
        }
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX=event.x;downY=event.y;dismissing=false
                dismissCandidate=event.y>=height-Ui.dp(context,48)
            }
            MotionEvent.ACTION_POINTER_DOWN,MotionEvent.ACTION_CANCEL -> dismissCandidate=false
        }
        if(dismissCandidate && !dismissing && (event.actionMasked==MotionEvent.ACTION_MOVE || event.actionMasked==MotionEvent.ACTION_UP)) {
            val dx=kotlin.math.abs(event.x-downX);val up=downY-event.y
            if(up>swipeThreshold && up>dx*1.25f) {
                dismissing=true
                // Cancel a pressed tile before consuming the gesture, so release
                // cannot launch its control or click through to Samsung home.
                val cancel=MotionEvent.obtain(event).apply { action=MotionEvent.ACTION_CANCEL }
                try { super.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
            } else if(dx>swipeThreshold || up < -swipeThreshold) dismissCandidate=false
        }
        if(dismissing) {
            if(event.actionMasked==MotionEvent.ACTION_UP) {
                val dismiss=dismissCandidate
                dismissing=false;dismissCandidate=false
                if(dismiss) close()
            } else if(event.actionMasked==MotionEvent.ACTION_CANCEL) dismissing=false
            return true
        }
        return super.dispatchTouchEvent(event)
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        if(w!=oldw) post { render() }
    }
    private fun render() {
        body.removeAllViews();radioTiles.clear()
        if(!Platform.unlocked(context)) {
            body.addView(Ui.text(context,I18n.text(R.string.ui_unlock_to_continue),17f).apply { gravity=Gravity.CENTER },FrameLayout.LayoutParams(-1,-1));return
        }
        val actions=QuickShortcuts.selected(context)
        if(actions.isEmpty()) {
            body.addView(Ui.text(context,I18n.text(R.string.ui_no_shortcuts),16f).apply { gravity=Gravity.CENTER },FrameLayout.LayoutParams(-1,-1));return
        }
        val widthPx=width.takeIf { it>0 } ?: resources.displayMetrics.widthPixels
        val cell=widthPx/4
        val grid=GridLayout(context).apply { columnCount=4;setPadding(Ui.dp(context,4),Ui.dp(context,4),Ui.dp(context,4),Ui.dp(context,48)) }
        actions.forEachIndexed { index,action ->
            val tile=LinearLayout(context).apply {
                orientation=VERTICAL;gravity=Gravity.CENTER;isClickable=true;isFocusable=true
                tag="shortcut:${action.id}";contentDescription=I18n.text(action.title)
                background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x40FFFFFF),GradientDrawable().apply { setColor(0xFF1C252E.toInt());cornerRadius=Ui.dp(context,16).toFloat() },null)
                setOnClickListener { activate(action) }
                setOnLongClickListener { openDetails(action);true }
            }
            tile.addView(ImageView(context).apply { setImageDrawable(Glyph(action.icon,false,Ui.dp(context,28)));scaleType=ImageView.ScaleType.CENTER_INSIDE },LayoutParams(Ui.dp(context,32),Ui.dp(context,32)))
            tile.addView(Ui.text(context,I18n.text(action.title),14f).apply { gravity=Gravity.CENTER;maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END },LayoutParams(-1,-2).apply { topMargin=Ui.dp(context,6) })
            grid.addView(tile,GridLayout.LayoutParams().apply { width=cell-Ui.dp(context,10);height=Ui.dp(context,88);columnSpec=GridLayout.spec(index%4);rowSpec=GridLayout.spec(index/4);setMargins(Ui.dp(context,4),Ui.dp(context,4),Ui.dp(context,4),Ui.dp(context,4)) })
            if(action.id in setOf("wifi","bluetooth")) radioTiles[action.id]=tile
        }
        body.addView(Ui.scroll(context,grid),FrameLayout.LayoutParams(-1,-1))
        updateRadioTiles()
    }
    private fun openOnPhone(action: QuickShortcuts.Action) {
        if(!Platform.unlocked(context)) { render();return }
        body.removeAllViews();radioTiles.clear()
        val content=Ui.column(context,12)
        content.addView(Ui.text(context,I18n.text(R.string.ui_shortcut_phone_required),16f))
        content.addView(Ui.button(context,I18n.text(R.string.ui_open_on_phone)) {
            if(QuickShortcuts.open(context,action,0)) launched()
        })
        body.addView(content,FrameLayout.LayoutParams(-1,-1))
    }
}
