package vn.lit.screenos

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.*
import android.view.*
import android.widget.*

object SettingsUi {
    val background = Color.rgb(245,249,253)
    val surface = Color.WHITE
    val ink = Color.rgb(24,39,48)
    val muted = Color.rgb(93,111,123)
    val accent = Color.rgb(0,111,149)
    val pale = Color.rgb(217,240,250)
    val line = Color.rgb(230,237,242)
    val green = Color.rgb(42,135,78)
    val greenPale = Color.rgb(230,246,235)
    fun dp(c: Context, n: Int) = Ui.dp(c,n)
    fun shape(color: Int, radius: Int, c: Context, border: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius=dp(c,radius).toFloat()
        if (border != null) setStroke(dp(c,1),border)
    }
    fun clickBackground(c: Context, color: Int=Color.TRANSPARENT, radius: Int=20) = RippleDrawable(ColorStateList.valueOf(0x18006F95),shape(color,radius,c),shape(Color.WHITE,radius,c))
    fun text(c: Context, value: CharSequence, size: Float=16f, color: Int=ink, bold: Boolean=false) = TextView(c).apply {
        text=value; textSize=size; setTextColor(color); includeFontPadding=false
        if(bold) typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
    }
    fun column(c: Context, padding: Int=0) = LinearLayout(c).apply {
        orientation=LinearLayout.VERTICAL; setPadding(dp(c,padding),dp(c,padding),dp(c,padding),dp(c,padding))
    }
    fun icon(c: Context, kind: SettingsGlyph.Kind, color: Int=accent, size: Int=24) = ImageView(c).apply {
        setImageDrawable(SettingsGlyph(kind,color,dp(c,size))); scaleType=ImageView.ScaleType.CENTER_INSIDE
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun badge(c: Context, kind: SettingsGlyph.Kind, color: Int=accent, fill: Int=pale, size: Int=44) = FrameLayout(c).apply {
        background=shape(fill,14,c)
        addView(icon(c,kind,color),FrameLayout.LayoutParams(dp(c,24),dp(c,24),Gravity.CENTER))
        layoutParams=LinearLayout.LayoutParams(dp(c,size),dp(c,size))
    }
    fun card(c: Context) = column(c).apply {
        background=shape(surface,24,c)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(c,16) }
    }
    fun section(c: Context, label: String) = text(c,label,14f,accent,true).apply {
        setPadding(dp(c,4),dp(c,10),0,dp(c,12))
    }
    fun divider(c: Context) = View(c).apply {
        setBackgroundColor(line); layoutParams=LinearLayout.LayoutParams(-1,dp(c,1)).apply { leftMargin=dp(c,68); rightMargin=dp(c,16) }
    }
    fun pill(c: Context, value: String, color: Int=accent, fill: Int=pale) = text(c,value,13f,color,true).apply {
        background=shape(fill,12,c); setPadding(dp(c,11),dp(c,7),dp(c,11),dp(c,7)); gravity=Gravity.CENTER
    }
    fun row(c: Context, kind: SettingsGlyph.Kind, title: String, subtitle: String?=null, trailing: View?=null, action: (() -> Unit)?=null): LinearLayout {
        val row=LinearLayout(c).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
            minimumHeight=dp(c,76); setPadding(dp(c,16),dp(c,14),dp(c,16),dp(c,14))
            if(action!=null) { background=clickBackground(c); isFocusable=true; setOnClickListener { action() } }
        }
        row.addView(icon(c,kind,muted),LinearLayout.LayoutParams(dp(c,24),dp(c,24)).apply { rightMargin=dp(c,16) })
        val labels=column(c)
        labels.addView(text(c,title,16f,ink,true))
        if(!subtitle.isNullOrBlank()) labels.addView(text(c,subtitle,13f,muted).apply { setLineSpacing(dp(c,2).toFloat(),1f) },LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(c,5) })
        row.addView(labels,LinearLayout.LayoutParams(0,-2,1f).apply { rightMargin=dp(c,10) })
        if(trailing!=null) row.addView(trailing)
        else if(action!=null) row.addView(icon(c,SettingsGlyph.Kind.NEXT,muted,20),LinearLayout.LayoutParams(dp(c,20),dp(c,20)))
        return row
    }
    fun toggle(c: Context, checked: Boolean, label: String, changed: (Boolean)->Unit) = Switch(c).apply {
        isChecked=checked; contentDescription=label; showText=false
        thumbTintList=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(accent,Color.rgb(124,141,152)))
        trackTintList=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(Color.rgb(164,216,236),Color.rgb(221,228,233)))
        setOnCheckedChangeListener { _, value -> changed(value) }
    }
    fun configureWindow(activity: Activity, root: View) {
        activity.window.setDecorFitsSystemWindows(false)
        @Suppress("DEPRECATION")
        activity.window.statusBarColor=Color.TRANSPARENT
        @Suppress("DEPRECATION")
        activity.window.navigationBarColor=background
        activity.window.insetsController?.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        root.setOnApplyWindowInsetsListener { view,insets ->
            val bars=insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            WindowInsets.CONSUMED
        }
        root.requestApplyInsets()
    }
}
