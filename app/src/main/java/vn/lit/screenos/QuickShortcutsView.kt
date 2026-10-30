package vn.lit.screenos

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*

class QuickShortcutsView(context: Context,private val displayId: Int,private val close: ()->Unit,private val launched: ()->Unit): LinearLayout(context) {
    private val body=FrameLayout(context)
    init {
        orientation=VERTICAL;setBackgroundColor(Color.BLACK)
        val header=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(Glyph.button(context,Glyph.Kind.NAV_BACK,I18n.text(R.string.ui_return_to_samsung_home),action=close),LayoutParams(Ui.dp(context,40),-1))
        header.addView(Ui.text(context,I18n.text(R.string.ui_shortcuts),19f).apply { setTypeface(null,Typeface.BOLD) },LayoutParams(0,-2,1f))
        addView(header,LayoutParams(-1,Ui.dp(context,36)));addView(body,LayoutParams(-1,0,1f))
        render()
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        if(w!=oldw) post { render() }
    }
    private fun render() {
        body.removeAllViews()
        if(!Platform.unlocked(context)) {
            body.addView(Ui.text(context,I18n.text(R.string.ui_unlock_to_continue),17f).apply { gravity=Gravity.CENTER },FrameLayout.LayoutParams(-1,-1));return
        }
        val actions=QuickShortcuts.selected(context)
        if(actions.isEmpty()) {
            body.addView(Ui.text(context,I18n.text(R.string.ui_no_shortcuts),16f).apply { gravity=Gravity.CENTER },FrameLayout.LayoutParams(-1,-1));return
        }
        val widthPx=width.takeIf { it>0 } ?: resources.displayMetrics.widthPixels
        val cell=widthPx/4
        val grid=GridLayout(context).apply { columnCount=4;setPadding(Ui.dp(context,4),0,Ui.dp(context,4),Ui.dp(context,8)) }
        actions.forEachIndexed { index,action ->
            val tile=LinearLayout(context).apply {
                orientation=VERTICAL;gravity=Gravity.CENTER;isClickable=true;isFocusable=true
                tag="shortcut:${action.id}";contentDescription=I18n.text(action.title)
                background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x40FFFFFF),GradientDrawable().apply { setColor(0xFF1C252E.toInt());cornerRadius=Ui.dp(context,16).toFloat() },null)
                setOnClickListener { if(QuickShortcuts.open(context,action,displayId)) launched() else openOnPhone(action) }
            }
            tile.addView(ImageView(context).apply { setImageDrawable(Glyph(action.icon,false,Ui.dp(context,28)));scaleType=ImageView.ScaleType.CENTER_INSIDE },LayoutParams(Ui.dp(context,32),Ui.dp(context,32)))
            tile.addView(Ui.text(context,I18n.text(action.title),14f).apply { gravity=Gravity.CENTER;maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END },LayoutParams(-1,-2).apply { topMargin=Ui.dp(context,6) })
            grid.addView(tile,GridLayout.LayoutParams().apply { width=cell-Ui.dp(context,10);height=Ui.dp(context,88);columnSpec=GridLayout.spec(index%4);rowSpec=GridLayout.spec(index/4);setMargins(Ui.dp(context,4),Ui.dp(context,4),Ui.dp(context,4),Ui.dp(context,4)) })
        }
        body.addView(Ui.scroll(context,grid),FrameLayout.LayoutParams(-1,-1))
    }
    private fun openOnPhone(action: QuickShortcuts.Action) {
        if(!Platform.unlocked(context)) { render();return }
        body.removeAllViews()
        val content=Ui.column(context,12)
        content.addView(Ui.text(context,I18n.text(R.string.ui_shortcut_phone_required),16f))
        content.addView(Ui.button(context,I18n.text(R.string.ui_open_on_phone)) {
            if(QuickShortcuts.open(context,action,0)) launched()
        })
        content.addView(Ui.button(context,I18n.text(R.string.ui_back)) { render() })
        body.addView(content,FrameLayout.LayoutParams(-1,-1))
    }
}
