package vn.lit.screenos

import android.app.Activity
import android.content.*
import android.graphics.Typeface
import android.os.Bundle
import android.view.*
import android.widget.*

class RotationSetupActivity : Activity() {
    private var scroll: ScrollView?=null
    private val command="python3 scripts/adb_rotation_bridge.py --serial DEVICE_SERIAL"
    override fun onCreate(state: Bundle?) { super.onCreate(state); CoverRotation.initialize(this) }
    override fun onResume() {
        super.onResume(); render()
        CoverRotation.probe(this) { if(!isDestroyed && !isFinishing) render() }
    }
    private fun render() {
        val previous=scroll?.scrollY ?: 0
        val root=SettingsUi.column(this).apply { setBackgroundColor(SettingsUi.background) }
        val toolbar=android.widget.LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(SettingsUi.dp(context,12),SettingsUi.dp(context,6),SettingsUi.dp(context,18),SettingsUi.dp(context,6)) }
        toolbar.addView(ImageButton(this).apply {
            setImageDrawable(SettingsGlyph(SettingsGlyph.Kind.BACK,SettingsUi.ink,SettingsUi.dp(context,24)))
            contentDescription=I18n.text(R.string.ui_back); background=SettingsUi.clickBackground(context); setOnClickListener { finish() }
        },LinearLayout.LayoutParams(SettingsUi.dp(this,44),SettingsUi.dp(this,44)))
        toolbar.addView(SettingsUi.text(this,I18n.text(R.string.ui_usb_connection),16f,SettingsUi.ink,true),LinearLayout.LayoutParams(-2,-2).apply { leftMargin=SettingsUi.dp(this@RotationSetupActivity,8) })
        root.addView(toolbar)
        val column=SettingsUi.column(this,20)
        column.addView(SettingsUi.text(this,I18n.text(R.string.ui_app_rotation),30f,SettingsUi.ink,true))
        column.addView(SettingsUi.text(this,I18n.text(R.string.ui_choose_the_orientation_for_each_app),14f,SettingsUi.muted),LinearLayout.LayoutParams(-1,-2).apply { topMargin=SettingsUi.dp(this@RotationSetupActivity,8); bottomMargin=SettingsUi.dp(this@RotationSetupActivity,24) })
        val status=SettingsUi.card(this)
        status.addView(SettingsUi.row(this,if(CoverRotation.available) SettingsGlyph.Kind.CHECK else SettingsGlyph.Kind.USB,if(CoverRotation.available) I18n.text(R.string.ui_computer_connected) else I18n.text(R.string.ui_no_adb_connection),if(CoverRotation.available) I18n.text(R.string.ui_ready_to_rotate_the_cover_screen) else I18n.text(R.string.ui_connect_a_usb_cable_to_get_started),SettingsUi.pill(this,if(CoverRotation.available) I18n.text(R.string.ui_ready) else "USB",if(CoverRotation.available) SettingsUi.green else SettingsUi.accent,if(CoverRotation.available) SettingsUi.greenPale else SettingsUi.pale)))
        column.addView(status)
        val steps=SettingsUi.card(this).apply { setPadding(SettingsUi.dp(context,18),SettingsUi.dp(context,18),SettingsUi.dp(context,18),SettingsUi.dp(context,18)) }
        fun step(number: Int,title: String,description: String) {
            val row=LinearLayout(this).apply { gravity=Gravity.TOP; setPadding(0,SettingsUi.dp(context,6),0,SettingsUi.dp(context,16)) }
            row.addView(SettingsUi.pill(this,number.toString()),LinearLayout.LayoutParams(SettingsUi.dp(this,34),SettingsUi.dp(this,34)))
            val labels=SettingsUi.column(this)
            labels.addView(SettingsUi.text(this,title,17f,SettingsUi.ink,true))
            labels.addView(SettingsUi.text(this,description,14f,SettingsUi.muted).apply { setLineSpacing(SettingsUi.dp(context,3).toFloat(),1f) },LinearLayout.LayoutParams(-1,-2).apply { topMargin=SettingsUi.dp(this@RotationSetupActivity,6) })
            row.addView(labels,LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=SettingsUi.dp(this@RotationSetupActivity,12) })
            steps.addView(row)
        }
        step(1,I18n.text(R.string.ui_connect_your_phone_to_a_computer),I18n.text(R.string.ui_use_usb_with_adb_debugging_enabled))
        step(2,I18n.text(R.string.ui_run_the_command_on_your_computer),I18n.text(R.string.ui_open_the_foldlet_project_folder_and_replace))
        val code=SettingsUi.column(this,14).apply { background=SettingsUi.shape(0xFF183040.toInt(),16,context) }
        code.addView(SettingsUi.text(this,command,13f,0xFFE0F4FF.toInt()).apply { typeface=Typeface.MONOSPACE; setTextIsSelectable(true); setLineSpacing(SettingsUi.dp(context,4).toFloat(),1f) })
        code.addView(SettingsUi.row(this,SettingsGlyph.Kind.COPY,I18n.text(R.string.ui_copy_command)) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(I18n.text(R.string.ui_foldlet_adb_command),command))
            Toast.makeText(this,I18n.text(R.string.ui_command_copied),Toast.LENGTH_SHORT).show()
        }.apply { background=SettingsUi.clickBackground(context,SettingsUi.pale,12); minimumHeight=SettingsUi.dp(context,48); tag="copy-adb-command" },LinearLayout.LayoutParams(-1,-2).apply { topMargin=SettingsUi.dp(this@RotationSetupActivity,14) })
        steps.addView(code,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=SettingsUi.dp(this@RotationSetupActivity,20) })
        step(3,I18n.text(R.string.ui_keep_the_connection_active),I18n.text(R.string.ui_choose_landscape_or_portrait_in_foldlet_disconnecting))
        column.addView(steps)
        column.addView(SettingsUi.row(this,SettingsGlyph.Kind.USB,I18n.text(R.string.ui_check_connection),I18n.text(R.string.ui_refresh_computer_status)) { CoverRotation.probe(this) { if(!isDestroyed && !isFinishing) render() } }.apply { background=SettingsUi.clickBackground(context,SettingsUi.pale,20) })
        column.addView(SettingsUi.text(this,I18n.text(R.string.ui_controls_only_the_z_flip3_cover_screen),13f,SettingsUi.muted).apply { setPadding(SettingsUi.dp(context,4),SettingsUi.dp(context,18),SettingsUi.dp(context,4),SettingsUi.dp(context,8)) })
        val scroller=ScrollView(this).apply { isVerticalScrollBarEnabled=false; addView(column) }; scroll=scroller
        root.addView(scroller,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root); SettingsUi.configureWindow(this,root)
        scroller.post { scroller.scrollTo(0,previous) }
    }
}
