package vn.lit.screenos

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.*

object HomeControlSettings {
    fun touchArea(activity: Activity,changed: ()->Unit) {
        var pending=HomeTouchConfig.read(activity)
        val content=SettingsUi.column(activity,16)
        val preview=AreaPreview(activity) { pending }
        content.addView(preview,LinearLayout.LayoutParams(-1,Ui.dp(activity,146)))
        content.addView(SettingsUi.text(activity,I18n.text(R.string.ui_touch_area_note),13f,SettingsUi.muted))
        content.addView(SettingsUi.row(activity,SettingsGlyph.Kind.POWER,I18n.text(R.string.ui_touch_area_enabled),null,SettingsUi.toggle(activity,pending.enabled,I18n.text(R.string.ui_touch_area_enabled)) { pending=pending.copy(enabled=it);preview.invalidate() }))
        fun slider(label: Int,min: Int,max: Int,value: Int,update: (Int)->Unit) {
            val title=SettingsUi.text(activity,"${I18n.text(label)}: $value%",14f,SettingsUi.ink,true)
            content.addView(title)
            content.addView(SeekBar(activity).apply {
                this.min=min;this.max=max;progress=value;contentDescription=I18n.text(label)
                setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?,progress: Int,fromUser: Boolean) {
                        if(fromUser) { val v=progress;title.text="${I18n.text(label)}: $v%";update(v);preview.invalidate() }
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?)=Unit
                    override fun onStopTrackingTouch(bar: SeekBar?)=Unit
                })
            },LinearLayout.LayoutParams(-1,Ui.dp(activity,40)))
        }
        slider(R.string.ui_touch_width,5,60,pending.widthPercent) { pending=pending.copy(widthPercent=it) }
        slider(R.string.ui_touch_height,10,100,pending.heightPercent) { pending=pending.copy(heightPercent=it) }
        slider(R.string.ui_touch_horizontal,0,100,pending.horizontal) { pending=pending.copy(horizontal=it) }
        slider(R.string.ui_touch_vertical,0,100,pending.vertical) { pending=pending.copy(vertical=it) }
        content.addView(SettingsUi.row(activity,SettingsGlyph.Kind.EYE,I18n.text(R.string.ui_touch_indicator),null,SettingsUi.toggle(activity,pending.indicator,I18n.text(R.string.ui_touch_indicator)) { pending=pending.copy(indicator=it) }))
        AlertDialog.Builder(activity).setTitle(I18n.text(R.string.ui_home_touch_area)).setView(ScrollView(activity).apply { addView(content) })
            .setPositiveButton(I18n.text(R.string.ui_save)) { _,_ -> pending.save(activity);changed() }
            .setNegativeButton(I18n.text(R.string.ui_cancel),null).show()
    }
    fun shortcuts(activity: Activity,changed: ()->Unit) {
        val available=QuickShortcuts.available(activity)
        val chosen=QuickShortcuts.selected(activity).map { it.id }.toMutableSet()
        val ordered=(QuickShortcuts.selected(activity)+available.filter { it.id !in chosen }).toMutableList()
        var enabled=Platform.preferences(activity).getBoolean("home_shortcuts_enabled",true)
        val content=SettingsUi.column(activity,16)
        content.addView(SettingsUi.row(activity,SettingsGlyph.Kind.SWIPE,I18n.text(R.string.ui_enable_shortcuts),null,SettingsUi.toggle(activity,enabled,I18n.text(R.string.ui_enable_shortcuts)) { enabled=it }))
        content.addView(SettingsUi.text(activity,I18n.text(R.string.ui_shortcuts_system_note),13f,SettingsUi.muted))
        content.addView(SettingsUi.text(activity,I18n.text(R.string.ui_shortcuts_drag),13f,SettingsUi.muted).apply { setPadding(0,Ui.dp(activity,8),0,Ui.dp(activity,8)) })
        val scroll=ScrollView(activity).apply { addView(content) }
        val list=ShortcutOrderList(activity,scroll) { from,to -> ordered.add(to,ordered.removeAt(from)) };content.addView(list)
        ordered.toList().forEach { action ->
                val row=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL }
                val checkbox=CheckBox(activity).apply {
                    text=I18n.text(action.title);textSize=15f;setTextColor(SettingsUi.ink);isChecked=action.id in chosen
                    buttonTintList=android.content.res.ColorStateList.valueOf(SettingsUi.accent)
                    setOnCheckedChangeListener { _,checked -> if(checked) chosen.add(action.id) else chosen.remove(action.id) }
                }
                row.addView(checkbox,LinearLayout.LayoutParams(0,Ui.dp(activity,52),1f))
                val handle=ShortcutOrderList.Handle(activity)
                row.addView(handle,LinearLayout.LayoutParams(Ui.dp(activity,48),Ui.dp(activity,48)))
                list.addShortcut(row,checkbox,handle)
        }
        AlertDialog.Builder(activity).setTitle(I18n.text(R.string.ui_home_shortcuts)).setView(scroll)
            .setPositiveButton(I18n.text(R.string.ui_save)) { _,_ ->
                Platform.preferences(activity).edit().putBoolean("home_shortcuts_enabled",enabled).putString("home_shortcuts",ordered.filter { it.id in chosen }.joinToString("|") { it.id }).apply();changed()
            }.setNegativeButton(I18n.text(R.string.ui_cancel),null).setOnDismissListener { list.finishDrag() }.show()
    }
    private class AreaPreview(context: Context,private val config: ()->HomeTouchConfig): View(context) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        init { contentDescription=I18n.text(R.string.ui_touch_preview) }
        override fun onDraw(canvas: Canvas) {
            val w=width-Ui.dp(context,16).toFloat();val h=w*260/512
            val x=(width-w)/2;val y=(height-h)/2
            paint.style=Paint.Style.FILL;paint.color=0xFF102431.toInt()
            canvas.drawRoundRect(x,y,x+w,y+h,Ui.dp(context,14).toFloat(),Ui.dp(context,14).toFloat(),paint)
            val area=config().bounds(512,260);val scale=w/512
            paint.color=if(config().enabled) 0xAA3289FF.toInt() else 0x8074818B.toInt()
            canvas.drawRoundRect(x+area.left*scale,y+area.top*scale,x+area.right*scale,y+area.bottom*scale,Ui.dp(context,5).toFloat(),Ui.dp(context,5).toFloat(),paint)
        }
    }
}
