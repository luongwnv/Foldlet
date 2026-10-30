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
    private data class Tile(val action: QuickShortcuts.Action,val view: android.view.View,val icon: ImageView,val detail: TextView,var state: ShortcutStates.State?=null)
    private val tiles=mutableListOf<Tile>()
    private val icons=QuickSettingsIcons(context)
    private val handler=Handler(Looper.getMainLooper())
    private var radioState=RadioControls.Result(false)
    private var pendingRadio: String?=null
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
    override fun onDetachedFromWindow() {
        SamsungRoutineControls.cancel(this);handler.removeCallbacks(poll)
        for(kind in SamsungRoutineControls.keys) SamsungRoutineControls.view(context,kind)?.let { if(it.parent===body) body.removeView(it) }
        super.onDetachedFromWindow()
    }
    private fun refreshRadios() {
        if(!isAttachedToWindow || readingRadios) return
        updateTiles()
        readingRadios=true
        RadioControls.request(context) { state ->
            readingRadios=false
            if(isAttachedToWindow) {
                radioState=state;updateTiles()
                handler.removeCallbacks(poll);handler.postDelayed(poll,1000)
            }
        }
    }
    private fun updateTiles() {
        val states=ShortcutStates.read(context,radioState)
        tiles.forEach { tile ->
            val state=states[tile.action.id] ?: ShortcutStates.State()
            val changing=state.enabled==2 || pendingRadio==tile.action.id
            val color=if(state.enabled==1) 0xFF3D70DC.toInt() else 0xFF23262C.toInt()
            val background=(tile.view.background as android.graphics.drawable.RippleDrawable).getDrawable(0) as GradientDrawable
            if(background.color?.defaultColor!=color) background.setColor(color)
            tile.view.alpha=if(changing) .6f else 1f
            val detail=if(changing) I18n.text(R.string.ui_state_changing) else state.detail ?: I18n.text(when(state.enabled) {
                1 -> R.string.ui_on
                0 -> R.string.ui_off
                else -> R.string.ui_state_unavailable
            })
            tile.detail.text=detail
            tile.detail.visibility=if(detail.isEmpty()) android.view.View.INVISIBLE else android.view.View.VISIBLE
            val label=I18n.text(tile.action.title)
            tile.view.contentDescription=if(detail.isEmpty()) label else "$label, $detail"
            if(tile.state!=state) {
                tile.icon.setImageDrawable(icons.get(tile.action,state,Ui.dp(context,36)))
                tile.state=state
            }
        }
    }
    private fun openDetails(action: QuickShortcuts.Action) {
        if(QuickShortcuts.open(context,action,displayId)) launched() else openOnPhone(action)
    }
    private fun activate(action: QuickShortcuts.Action) {
        if(!Platform.unlocked(context)) { render();return }
        when(action.id) {
            "mobile","hotspot" -> {
                if(pendingRadio!=null) return
                pendingRadio=action.id;updateTiles()
                SamsungRoutineControls.toggle(this,context,action.id) { result ->
                    pendingRadio=null
                    if(!isAttachedToWindow) return@toggle
                    updateTiles();refreshRadios()
                    when(result) {
                        SamsungRoutineControls.Result.SUCCESS, SamsungRoutineControls.Result.CANCELED -> Unit
                        SamsungRoutineControls.Result.NOT_LINKED -> Platform.messageOnDisplay(context,displayId,I18n.text(R.string.ui_samsung_link_first))
                        else -> Platform.messageOnDisplay(context,displayId,I18n.text(R.string.ui_samsung_routine_no_change))
                    }
                }
            }
            "wifi","bluetooth" -> {
                if(pendingRadio!=null) return
                pendingRadio=action.id;updateTiles()
                RadioControls.request(context,action.id) { state ->
                    pendingRadio=null
                    if(!isAttachedToWindow) return@request
                    radioState=state;updateTiles()
                    if(!state.installed) Platform.message(context,I18n.text(R.string.ui_controls_install))
                    else if(!state.accepted) Platform.message(context,I18n.text(R.string.ui_toggle_unavailable))
                    handler.removeCallbacks(poll);handler.postDelayed(poll,300)
                }
            }
            "sound" -> {
                val audio=context.getSystemService(AudioManager::class.java)
                try { audio.ringerMode=if(audio.ringerMode==AudioManager.RINGER_MODE_NORMAL) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL }
                catch(_: SecurityException) { Platform.message(context,I18n.text(R.string.ui_toggle_unavailable)) }
                updateTiles()
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
        if(w!=oldw || h!=oldh) post { render() }
    }
    private fun render() {
        body.removeAllViews();tiles.clear()
        if(!Platform.unlocked(context)) {
            body.addView(Ui.text(context,I18n.text(R.string.ui_unlock_to_continue),17f).apply { gravity=Gravity.CENTER },FrameLayout.LayoutParams(-1,-1));return
        }
        val actions=QuickShortcuts.selected(context)
        if(actions.isEmpty()) {
            body.addView(Ui.text(context,I18n.text(R.string.ui_no_shortcuts),16f).apply { gravity=Gravity.CENTER },FrameLayout.LayoutParams(-1,-1));return
        }
        for(kind in SamsungRoutineControls.keys) SamsungRoutineControls.view(context,kind)?.let { widget ->
            (widget.parent as? android.view.ViewGroup)?.removeView(widget)
            widget.visibility=android.view.View.INVISIBLE
            widget.importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            body.addView(widget,FrameLayout.LayoutParams(Ui.dp(context,120),Ui.dp(context,120)))
        }
        val widthPx=width.takeIf { it>0 } ?: resources.displayMetrics.widthPixels
        val heightPx=height.takeIf { it>0 } ?: resources.displayMetrics.heightPixels
        val cell=(widthPx-Ui.dp(context,8))/4
        val visibleRows=((actions.size+3)/4).coerceIn(1,2)
        val cellHeight=((heightPx-Ui.dp(context,8))/visibleRows-Ui.dp(context,8)).coerceAtLeast(Ui.dp(context,88))
        val grid=GridLayout(context).apply { columnCount=4;setPadding(Ui.dp(context,4),Ui.dp(context,4),Ui.dp(context,4),Ui.dp(context,4)) }
        actions.forEachIndexed { index,action ->
            val tile=LinearLayout(context).apply {
                orientation=VERTICAL;gravity=Gravity.CENTER;isClickable=true;isFocusable=true
                tag="shortcut:${action.id}";contentDescription=I18n.text(action.title)
                background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x40FFFFFF),GradientDrawable().apply { setColor(0xFF1C252E.toInt());cornerRadius=Ui.dp(context,16).toFloat() },null)
                setOnClickListener { activate(action) }
                setOnLongClickListener { openDetails(action);true }
            }
            val icon=ImageView(context).apply { scaleType=ImageView.ScaleType.FIT_CENTER;importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            tile.addView(icon,LayoutParams(Ui.dp(context,36),Ui.dp(context,36)))
            tile.addView(Ui.text(context,I18n.text(action.title),14f).apply { gravity=Gravity.CENTER;maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END },LayoutParams(-1,-2).apply { topMargin=Ui.dp(context,6) })
            val detail=TextView(context).apply { textSize=12f;setTextColor(0xDDFFFFFF.toInt());includeFontPadding=false;gravity=Gravity.CENTER;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END }
            tile.addView(detail,LayoutParams(-1,Ui.dp(context,17)))
            grid.addView(tile,GridLayout.LayoutParams().apply { width=cell-Ui.dp(context,8);height=cellHeight;columnSpec=GridLayout.spec(index%4);rowSpec=GridLayout.spec(index/4);setMargins(Ui.dp(context,4),Ui.dp(context,4),Ui.dp(context,4),Ui.dp(context,4)) })
            tiles.add(Tile(action,tile,icon,detail))
        }
        body.addView(Ui.scroll(context,grid),FrameLayout.LayoutParams(-1,-1))
        updateTiles()
    }
    private fun openOnPhone(action: QuickShortcuts.Action) {
        if(!Platform.unlocked(context)) { render();return }
        body.removeAllViews();tiles.clear()
        val content=Ui.column(context,12)
        content.addView(Ui.text(context,I18n.text(R.string.ui_shortcut_phone_required),16f))
        content.addView(Ui.button(context,I18n.text(R.string.ui_open_on_phone)) {
            if(QuickShortcuts.open(context,action,0)) launched()
        })
        body.addView(content,FrameLayout.LayoutParams(-1,-1))
    }
}
