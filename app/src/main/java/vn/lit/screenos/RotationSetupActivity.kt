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
            contentDescription="Quay lại"; background=SettingsUi.clickBackground(context); setOnClickListener { finish() }
        },LinearLayout.LayoutParams(SettingsUi.dp(this,44),SettingsUi.dp(this,44)))
        toolbar.addView(SettingsUi.text(this,"Kết nối USB",16f,SettingsUi.ink,true),LinearLayout.LayoutParams(-2,-2).apply { leftMargin=SettingsUi.dp(this@RotationSetupActivity,8) })
        root.addView(toolbar)
        val column=SettingsUi.column(this,20)
        column.addView(SettingsUi.text(this,"Xoay ứng dụng",30f,SettingsUi.ink,true))
        column.addView(SettingsUi.text(this,"Chọn hướng cho từng trải nghiệm",14f,SettingsUi.muted),LinearLayout.LayoutParams(-1,-2).apply { topMargin=SettingsUi.dp(this@RotationSetupActivity,8); bottomMargin=SettingsUi.dp(this@RotationSetupActivity,24) })
        val status=SettingsUi.card(this)
        status.addView(SettingsUi.row(this,if(CoverRotation.available) SettingsGlyph.Kind.CHECK else SettingsGlyph.Kind.USB,if(CoverRotation.available) "Máy tính đã kết nối" else "Chưa có kết nối ADB",if(CoverRotation.available) "Sẵn sàng xoay màn hình ngoài" else "Cắm cáp USB để bắt đầu",SettingsUi.pill(this,if(CoverRotation.available) "Sẵn sàng" else "USB",if(CoverRotation.available) SettingsUi.green else SettingsUi.accent,if(CoverRotation.available) SettingsUi.greenPale else SettingsUi.pale)))
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
        step(1,"Cắm điện thoại vào máy tính","Dùng kết nối USB có bật gỡ lỗi ADB.")
        step(2,"Chạy lệnh trên máy tính","Mở thư mục dự án LIT, thay DEVICE_SERIAL bằng mã thiết bị từ adb devices.")
        val code=SettingsUi.column(this,14).apply { background=SettingsUi.shape(0xFF183040.toInt(),16,context) }
        code.addView(SettingsUi.text(this,command,13f,0xFFE0F4FF.toInt()).apply { typeface=Typeface.MONOSPACE; setTextIsSelectable(true); setLineSpacing(SettingsUi.dp(context,4).toFloat(),1f) })
        code.addView(SettingsUi.row(this,SettingsGlyph.Kind.COPY,"Sao chép lệnh") {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Lệnh ADB LIT",command))
            Toast.makeText(this,"Đã sao chép lệnh",Toast.LENGTH_SHORT).show()
        }.apply { background=SettingsUi.clickBackground(context,SettingsUi.pale,12); minimumHeight=SettingsUi.dp(context,48); tag="copy-adb-command" },LinearLayout.LayoutParams(-1,-2).apply { topMargin=SettingsUi.dp(this@RotationSetupActivity,14) })
        steps.addView(code,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=SettingsUi.dp(this@RotationSetupActivity,20) })
        step(3,"Giữ kết nối khi sử dụng","Chọn Ngang hoặc Dọc trong menu LIT. Ngắt kết nối sẽ tự khôi phục chế độ xoay trước đó.")
        column.addView(steps)
        column.addView(SettingsUi.row(this,SettingsGlyph.Kind.USB,"Kiểm tra kết nối","Làm mới trạng thái máy tính") { CoverRotation.probe(this) { if(!isDestroyed && !isFinishing) render() } }.apply { background=SettingsUi.clickBackground(context,SettingsUi.pale,20) })
        column.addView(SettingsUi.text(this,"Chỉ điều khiển màn hình ngoài Z Flip3. Không cần Shizuku hoặc root.",13f,SettingsUi.muted).apply { setPadding(SettingsUi.dp(context,4),SettingsUi.dp(context,18),SettingsUi.dp(context,4),SettingsUi.dp(context,8)) })
        val scroller=ScrollView(this).apply { isVerticalScrollBarEnabled=false; addView(column) }; scroll=scroller
        root.addView(scroller,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root); SettingsUi.configureWindow(this,root)
        scroller.post { scroller.scrollTo(0,previous) }
    }
}
