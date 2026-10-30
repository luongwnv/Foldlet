package vn.lit.screenos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.DragEvent
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.ScrollView

/** Local drag session: rows move with the finger and scroll at the dialog edges. */
class ShortcutOrderList(context: Context,private val scroll: ScrollView,private val moved: (Int,Int)->Unit): LinearLayout(context) {
    private val token=Any()
    private var dragging: View?=null
    private var originalRows=emptyList<View>()
    private var fingerY=0f
    private var scrollStep=0
    private val frame=object: Runnable {
        override fun run() {
            if(dragging==null || scrollStep==0) return
            scroll.scrollBy(0,scrollStep)
            reorderAtFinger()
            scroll.postOnAnimation(this)
        }
    }
    init {
        orientation=VERTICAL
        scroll.setOnDragListener { _,event ->
            if(event.localState!==token) false
            else {
                when(event.action) {
                    DragEvent.ACTION_DRAG_LOCATION,DragEvent.ACTION_DROP -> {
                        val position=IntArray(2);scroll.getLocationOnScreen(position)
                        fingerY=position[1]+event.y
                        reorderAtFinger()
                        val viewport=Rect();scroll.getGlobalVisibleRect(viewport)
                        val edge=Ui.dp(context,48)
                        scrollStep=when {
                            fingerY<viewport.top+edge -> -Ui.dp(context,6)
                            fingerY>viewport.bottom-edge -> Ui.dp(context,6)
                            else -> 0
                        }
                        scroll.removeCallbacks(frame)
                        if(event.action==DragEvent.ACTION_DRAG_LOCATION && scrollStep!=0) scroll.postOnAnimation(frame)
                    }
                    DragEvent.ACTION_DRAG_EXITED -> { scrollStep=0;scroll.removeCallbacks(frame) }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        if(!event.result) originalRows.forEachIndexed { index,row -> move(row,index) }
                        finishDrag()
                    }
                }
                true
            }
        }
    }
    fun addShortcut(row: View,vararg targets: View) {
        addView(row)
        (listOf(row)+targets).forEach { target ->
            target.setOnLongClickListener {
                originalRows=(0 until childCount).map { getChildAt(it) }
                dragging=row
                val started=row.startDragAndDrop(null,View.DragShadowBuilder(row),token,0)
                if(started) {
                    row.alpha=.35f
                    row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                } else finishDrag()
                started
            }
        }
        // Keep reordering available to accessibility services without visible arrow buttons.
        targets.firstOrNull()?.accessibilityDelegate=object: View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View,info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host,info)
                val index=indexOfChild(row)
                if(index>0) info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,I18n.text(R.string.ui_move_up,host.contentDescription ?: (host as? android.widget.TextView)?.text ?: "")))
                if(index<childCount-1) info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,I18n.text(R.string.ui_move_down,host.contentDescription ?: (host as? android.widget.TextView)?.text ?: "")))
            }
            override fun performAccessibilityAction(host: View,action: Int,args: Bundle?): Boolean {
                val delta=when(action) {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> -1
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> 1
                    else -> return super.performAccessibilityAction(host,action,args)
                }
                val target=indexOfChild(row)+delta
                if(target !in 0 until childCount) return false
                move(row,target)
                return true
            }
        }
    }
    private fun reorderAtFinger() {
        val row=dragging ?: return
        val position=IntArray(2);getLocationOnScreen(position)
        val y=fingerY-position[1]
        val index=(0 until childCount).count { val other=getChildAt(it);other!==row && y>(other.top+other.bottom)/2f }
        move(row,index)
    }
    private fun move(row: View,to: Int) {
        val from=indexOfChild(row)
        if(from<0 || from==to) return
        removeViewAt(from);addView(row,to);moved(from,to)
    }
    fun finishDrag() {
        scroll.removeCallbacks(frame);scrollStep=0
        dragging?.alpha=1f;dragging=null;originalRows=emptyList()
    }
    override fun onDetachedFromWindow() { finishDrag();super.onDetachedFromWindow() }

    class Handle(context: Context): View(context) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=SettingsUi.muted }
        init { contentDescription=I18n.text(R.string.ui_shortcuts_drag) }
        override fun onDraw(canvas: Canvas) {
            val gap=Ui.dp(context,6).toFloat();val radius=Ui.dp(context,1).toFloat()*1.5f
            for(x in listOf(-.5f,.5f)) for(y in -1..1) canvas.drawCircle(width/2f+x*gap,height/2f+y*gap,radius,paint)
        }
    }
}
