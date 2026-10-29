package vn.lit.screenos

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.*

object Ui {
    val background = Color.BLACK
    val surface = Color.rgb(22, 22, 22)
    val accent = Color.WHITE
    val muted = Color.rgb(195, 195, 195)
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun column(context: Context, padding: Int = 12) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, padding), dp(context, padding), dp(context, padding), dp(context, padding))
        setBackgroundColor(Ui.background)
    }
    fun text(context: Context, value: CharSequence, size: Float = 16f, color: Int = Color.WHITE) = TextView(context).apply {
        text = value; textSize = size.coerceAtLeast(16f); setTextColor(color)
        setPadding(0, dp(context, 4), 0, dp(context, 4))
    }
    fun heading(context: Context, value: String, size: Float = 24f) = text(context, value, size, accent).apply {
        setTypeface(null, Typeface.BOLD)
    }
    fun button(context: Context, value: String, action: () -> Unit) = Button(context).apply {
        text = value; textSize = 16f; isAllCaps = false; setTextColor(accent)
        minHeight = dp(context, 44); minimumHeight = dp(context, 44)
        background = GradientDrawable().apply { setColor(surface); cornerRadius = dp(context, 12).toFloat() }
        setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(context, 3), 0, dp(context, 3)) }
        setOnClickListener { action() }
    }
    fun scroll(context: Context, content: View) = ScrollView(context).apply { addView(content) }
}
