package vn.lit.screenos

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import java.util.Locale
import kotlin.math.hypot

/** Compact, local keyboard shared by drawer search and write-only cover input. */
@android.annotation.SuppressLint("ViewConstructor")
class CoverKeyboardView(
    context: Context,
    private val target: CoverInputTarget,
    vocabulary: List<String> = emptyList(),
    private val doneLabel: String = I18n.text(R.string.ui_enter),
) : LinearLayout(context) {
    private val swipeEnabled=Platform.preferences(context).getBoolean("swipe_typing",false) && !target.privateInput && !target.numeric
    private val decoder by lazy { SwipeDecoder(context,vocabulary) }
    private val suggestions=LinearLayout(context)
    private var lastWord: String?=null
    private var symbols=target.numeric
    private var shift=false
    private var capsLock=false
    private var lastShift=0L
    private val keys=Keys(context)
    init {
        orientation=VERTICAL; setBackgroundColor(Color.BLACK)
        addView(suggestions,LayoutParams(-1,Ui.dp(context,24)))
        addView(keys,LayoutParams(-1,Ui.dp(context,112)))
        hint()
    }
    private fun hint() {
        suggestions.removeAllViews()
        suggestions.addView(TextView(context).apply {
            text=I18n.text(if(target.privateInput) R.string.ui_keyboard_private else if(swipeEnabled) R.string.ui_keyboard_swipe_hint else R.string.ui_keyboard_tap_hint)
            textSize=12f; gravity=Gravity.CENTER; setTextColor(0xFF9BA7B1.toInt())
        },LayoutParams(-1,-1))
        suggestions.visibility=if(swipeEnabled || target.privateInput) VISIBLE else GONE
    }
    private fun showChoices(values: List<String>,accent: Boolean=false) {
        suggestions.removeAllViews();suggestions.visibility=VISIBLE
        values.forEach { value -> suggestions.addView(TextView(context).apply {
            text=value;textSize=14f;gravity=Gravity.CENTER;setTextColor(Color.WHITE)
            isFocusable=false;contentDescription=value
            setOnClickListener {
                val success=if(accent) target.text(value) else lastWord?.let { target.replaceWord(it,value) } ?: false
                if(success) { lastWord=if(accent) null else value;shift=false;keys.invalidate();if(accent) hint() }
                else unavailable()
            }
        },LayoutParams(0,-1,1f)) }
    }
    private fun unavailable() {
        lastWord=null;hint()
        Platform.message(context,I18n.text(R.string.ui_keyboard_unavailable))
    }
    private fun emit(value: String) {
        lastWord=null;hint()
        val success=when(value) {
            "123" -> { symbols=true;keys.invalidate();true }
            "ABC" -> { symbols=false;keys.invalidate();true }
            "⇧" -> {
                val now=android.os.SystemClock.uptimeMillis()
                if(capsLock) { capsLock=false;shift=false }
                else if(shift && now-lastShift<350) capsLock=true
                else shift=!shift
                lastShift=now;keys.invalidate();true
            }
            "space" -> target.text(" ")
            "⌫" -> target.backspace()
            "enter" -> target.action()
            else -> target.text(if(shift) value.uppercase(Locale.ROOT) else value).also { if(!capsLock) shift=false;keys.invalidate() }
        }
        if(!success) unavailable()
    }
    override fun onDetachedFromWindow() { keys.cancel();lastWord=null;super.onDetachedFromWindow() }

    private data class Key(val value: String,val area: RectF)
    private inner class Keys(context: Context) : View(context) {
        private val handler=Handler(Looper.getMainLooper())
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        private val trace=ArrayList<PointF>()
        private val line=Path()
        private var pressed: Key?=null
        private var dragged=false
        private var longPress=false
        private var repeating=false
        private val longClick=Runnable {
            val key=pressed?.value ?: return@Runnable
            if(key=="⌫") { repeating=true;emit(key);handler.postDelayed(repeatDelete,75) }
            else {
                val accents=when(key) {
                    "a" -> listOf("a","á","à","ả","ã","ạ","ă","â")
                    "e" -> listOf("e","é","è","ẻ","ẽ","ẹ","ê")
                    "i" -> listOf("i","í","ì","ỉ","ĩ","ị")
                    "o" -> listOf("o","ó","ò","ỏ","õ","ọ","ô","ơ")
                    "u" -> listOf("u","ú","ù","ủ","ũ","ụ","ư")
                    "y" -> listOf("y","ý","ỳ","ỷ","ỹ","ỵ")
                    "d" -> listOf("d","đ")
                    else -> emptyList()
                }
                if(accents.isNotEmpty()) { longPress=true;showChoices(if(shift) accents.map { it.uppercase(Locale.ROOT) } else accents,true);performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
            }
        }
        private val repeatDelete=object: Runnable { override fun run() { if(repeating) { emit("⌫");handler.postDelayed(this,75) } } }
        init { isFocusable=false;contentDescription=I18n.text(R.string.ui_keyboard) }
        fun cancel() { handler.removeCallbacksAndMessages(null);pressed=null;trace.clear();repeating=false;invalidate() }
        private fun keyList(): List<Key> {
            if(width<=0 || height<=0) return emptyList()
            val rowHeight=height/4f;val keyWidth=width/10f
            val rows=if(symbols) listOf("1234567890","@#€%&*-+()",".,:;!?/'\"_") else listOf("qwertyuiop","asdfghjkl","zxcvbnm")
            val list=ArrayList<Key>()
            rows.forEachIndexed { y,row ->
                val offset=(width-row.length*keyWidth)/2
                row.forEachIndexed { x,c -> list.add(Key(c.toString(),RectF(offset+x*keyWidth,y*rowHeight,offset+(x+1)*keyWidth,(y+1)*rowHeight))) }
            }
            val footer=listOf((if(symbols) "ABC" else "123") to .12f,"⇧" to .12f,"space" to .40f,"⌫" to .14f,"enter" to .22f)
            var left=0f
            footer.forEach { (name,fraction) -> list.add(Key(name,RectF(left,rowHeight*3,left+width*fraction,height.toFloat())));left+=width*fraction }
            return list
        }
        private fun keyAt(x: Float,y: Float)=keyList().firstOrNull { it.area.contains(x,y) }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val gap=Ui.dp(context,2).toFloat()
            keyList().forEach { key ->
                paint.style=Paint.Style.FILL
                paint.color=if(key.value==pressed?.value || (key.value=="⇧" && shift)) 0xFF235E82.toInt() else 0xFF20262D.toInt()
                val r=key.area
                canvas.drawRoundRect(r.left+gap,r.top+gap,r.right-gap,r.bottom-gap,Ui.dp(context,5).toFloat(),Ui.dp(context,5).toFloat(),paint)
                paint.color=Color.WHITE;paint.textAlign=Paint.Align.CENTER
                paint.textSize=Ui.dp(context,if(key.value.length==1) 17 else 12).toFloat()
                val label=when(key.value) { "space" -> I18n.text(R.string.ui_space);"enter" -> doneLabel;else -> if(shift && !symbols) key.value.uppercase(Locale.ROOT) else key.value }
                val fit=(r.width()-gap*4)/paint.measureText(label).coerceAtLeast(1f)
                if(fit<1) paint.textSize*=fit
                canvas.drawText(label,r.centerX(),r.centerY()-(paint.ascent()+paint.descent())/2,paint)
            }
            if(swipeEnabled && dragged && !symbols && trace.size>1) {
                line.reset();line.moveTo(trace[0].x,trace[0].y);trace.drop(1).forEach { line.lineTo(it.x,it.y) }
                paint.color=0xCC72D6FF.toInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=Ui.dp(context,3).toFloat();paint.strokeCap=Paint.Cap.ROUND
                canvas.drawPath(line,paint)
            }
        }
        override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            keyList().forEachIndexed { index,key ->
                val label=when(key.value) {
                    "⇧" -> I18n.text(R.string.ui_shift)
                    "123" -> I18n.text(R.string.ui_symbols)
                    "ABC" -> I18n.text(R.string.ui_letters)
                    "space" -> I18n.text(R.string.ui_space)
                    "⌫" -> I18n.text(R.string.ui_delete)
                    "enter" -> doneLabel
                    else -> if(shift) key.value.uppercase(Locale.ROOT) else key.value
                }
                info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(0x01000000+index,label))
            }
        }
        override fun performAccessibilityAction(action: Int,arguments: android.os.Bundle?): Boolean {
            val key=keyList().getOrNull(action-0x01000000)
            if(key!=null) { emit(key.value);return true }
            return super.performAccessibilityAction(action,arguments)
        }
        override fun performClick(): Boolean { super.performClick();return true }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressed=keyAt(event.x,event.y);trace.clear();trace.add(PointF(event.x,event.y));dragged=false;longPress=false;repeating=false
                    handler.postDelayed(longClick,450);parent?.requestDisallowInterceptTouchEvent(true);invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    val start=trace.firstOrNull() ?: return true
                    if(hypot(event.x-start.x,event.y-start.y)>width/10f*.55f) { dragged=true;handler.removeCallbacks(longClick) }
                    if(trace.size<512) trace.add(PointF(event.x,event.y))
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacksAndMessages(null)
                    if(!longPress && !repeating) {
                        if(dragged && swipeEnabled && !symbols && pressed?.value?.singleOrNull() in 'a'..'z') {
                            trace.add(PointF(event.x,event.y))
                            val centers=keyList().mapNotNull { key -> key.value.singleOrNull()?.takeIf { it in 'a'..'z' }?.let { it to PointF(key.area.centerX(),key.area.centerY()) } }.toMap()
                            val values=decoder.decode(trace,centers,width/10f).map { if(shift) it.replaceFirstChar { c -> c.titlecase(Locale.ROOT) } else it }
                            if(values.isNotEmpty() && target.word(values[0])) { lastWord=values[0];showChoices(values);if(!capsLock) shift=false }
                        } else if(!dragged || !swipeEnabled) pressed?.value?.let { emit(it) }
                    }
                    repeating=false;pressed=null;trace.clear();performClick();invalidate();parent?.requestDisallowInterceptTouchEvent(false)
                }
                MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_POINTER_DOWN -> { cancel();parent?.requestDisallowInterceptTouchEvent(false) }
            }
            return true
        }
    }
}
