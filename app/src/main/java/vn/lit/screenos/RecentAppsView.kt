package vn.lit.screenos

import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import java.util.concurrent.Executors

/** Recent applications across the phone, opened on this cover without a computer. */
class RecentAppsView(context: Context, private val displayId: Int, private val close: () -> Unit) : LinearLayout(context) {
    private val body = FrameLayout(context)
    private var generation = 0
    private var unlocked = false
    private val clearAll = label(I18n.text(R.string.ui_close_all),16f,true).apply {
        gravity=Gravity.CENTER; setPadding(Ui.dp(context,12),0,Ui.dp(context,12),0)
        isClickable=true; isFocusable=true; tag="recent-clear-all"
        setOnClickListener { if(unlocked && RecentApps.hasAccess(context)) { RecentApps.clear(context); refresh() } }
    }
    private val privacy = object : Runnable {
        override fun run() {
            val current = Platform.unlocked(context)
            if (current != unlocked) { unlocked = current; refresh() }
            postDelayed(this,500)
        }
    }
    init {
        orientation = VERTICAL; setBackgroundColor(Color.BLACK)
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(Glyph.button(context, Glyph.Kind.NAV_BACK, I18n.text(R.string.ui_close_recent_apps), action = close), LayoutParams(Ui.dp(context,40),-1))
        header.addView(label(I18n.text(R.string.ui_recent_apps),19f,true), LayoutParams(0,-2,1f))
        header.addView(clearAll,LayoutParams(-2,-1))
        addView(header,LayoutParams(-1,Ui.dp(context,40)))
        addView(body,LayoutParams(-1,0,1f))
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); unlocked=Platform.unlocked(context); refresh(); post(privacy) }
    override fun onDetachedFromWindow() { generation++; removeCallbacks(privacy); super.onDetachedFromWindow() }
    private fun refresh() {
        generation++
        body.removeAllViews()
        clearAll.isEnabled=unlocked && RecentApps.hasAccess(context)
        if (!unlocked) {
            body.addView(label(I18n.text(R.string.ui_unlock_your_phone_to_see_recent_apps),16f).apply { gravity=Gravity.CENTER },FrameLayout.LayoutParams(-1,-1)); return
        }
        if (!RecentApps.hasAccess(context)) {
            val content = Ui.column(context,14)
            content.addView(label(I18n.text(R.string.ui_allow_usage_access),18f,true))
            content.addView(label(I18n.text(R.string.ui_open_your_phone_to_allow_access_once),15f))
            content.addView(Ui.button(context,I18n.text(R.string.ui_allow_access)) { RecentApps.openAccessSettings(context) }.apply { tag="recent-usage-access" })
            body.addView(content); return
        }
        body.addView(label(I18n.text(R.string.ui_loading),16f).apply { gravity=Gravity.CENTER },FrameLayout.LayoutParams(-1,-1))
        val request = ++generation
        worker.execute {
            val entries = try { RecentApps.load(context.applicationContext) } catch (_: Exception) { emptyList() }
            post { if (isAttachedToWindow && generation==request) render(entries) }
        }
    }
    private fun render(entries: List<RecentApps.Entry>) {
        body.removeAllViews()
        if (entries.isEmpty()) {
            body.addView(label(I18n.text(R.string.ui_no_recent_apps),16f).apply { gravity=Gravity.CENTER },FrameLayout.LayoutParams(-1,-1)); return
        }
        val row = LinearLayout(context).apply { setPadding(Ui.dp(context,6),0,Ui.dp(context,6),0) }
        entries.forEach { entry ->
            val card = LinearLayout(context).apply {
                orientation=VERTICAL; gravity=Gravity.CENTER
                setPadding(Ui.dp(context,10),Ui.dp(context,10),Ui.dp(context,10),Ui.dp(context,10))
                background=GradientDrawable().apply { setColor(0xFF1C1C1C.toInt()); cornerRadius=Ui.dp(context,18).toFloat() }
                contentDescription=I18n.text(R.string.ui_open, entry.label); tag="recent:${entry.component.packageName}"; isClickable=true; isFocusable=true
                setOnClickListener { launch(entry) }
            }
            card.addView(ImageView(context).apply { setImageDrawable(entry.icon); scaleType=ImageView.ScaleType.FIT_CENTER },LayoutParams(Ui.dp(context,64),Ui.dp(context,64)))
            card.addView(label(entry.label,16f,true).apply { gravity=Gravity.CENTER; maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END },LayoutParams(-1,-2).apply { topMargin=Ui.dp(context,12) })
            val minutes = ((System.currentTimeMillis()-entry.lastUsed).coerceAtLeast(0)/60_000).toInt()
            val age = when { minutes<1 -> I18n.text(R.string.ui_just_used); minutes<60 -> resources.getQuantityString(R.plurals.minutes_ago,minutes,minutes); minutes<1440 -> resources.getQuantityString(R.plurals.hours_ago,minutes/60,minutes/60); else -> resources.getQuantityString(R.plurals.days_ago,minutes/1440,minutes/1440) }
            card.addView(label(age,12f).apply { gravity=Gravity.CENTER; setTextColor(0xFFAFAFAF.toInt()) },LayoutParams(-1,-2).apply { topMargin=Ui.dp(context,7) })
            row.addView(card,LayoutParams(Ui.dp(context,140),-1).apply { setMargins(Ui.dp(context,5),Ui.dp(context,8),Ui.dp(context,5),Ui.dp(context,8)) })
        }
        body.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled=false; addView(row,FrameLayout.LayoutParams(-2,-1)) },FrameLayout.LayoutParams(-1,-1))
    }
    private fun launch(entry: RecentApps.Entry) {
        if (!Platform.launch(context,Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(entry.component),displayId)) return
        close()
        val prefs=Platform.preferences(context)
        val rotation=prefs.getString("orientation.${entry.component.flattenToString()}",prefs.getString("orientation","auto")) ?: "auto"
        if (CoverRotation.available) CoverRotation.apply(context,displayId,rotation)
    }
    private fun label(value: String,size: Float,bold: Boolean=false) = TextView(context).apply {
        text=value; textSize=size; setTextColor(Color.WHITE)
        if(bold) setTypeface(null,Typeface.BOLD)
    }
    companion object { private val worker = Executors.newSingleThreadExecutor() }
}
