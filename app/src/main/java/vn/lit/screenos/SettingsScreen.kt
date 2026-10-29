package vn.lit.screenos

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import kotlin.math.min

/** Phone settings: grouped controls and live state, independent of cover styling. */
class SettingsScreen(private val activity: MainActivity, state: Bundle?) {
    private enum class Tab(val label: String, val icon: SettingsGlyph.Kind) {
        HOME("Tổng quan",SettingsGlyph.Kind.HOME), SETTINGS("Cài đặt",SettingsGlyph.Kind.SETTINGS), INFO("Thông tin",SettingsGlyph.Kind.INFO)
    }
    private val c get() = activity
    private val prefs get() = Platform.preferences(c)
    private var tab = Tab.entries.firstOrNull { it.name == state?.getString("settings_tab") } ?: Tab.HOME
    private val positions = Tab.entries.associateWith { state?.getInt("scroll_${it.name}") ?: 0 }.toMutableMap()
    private var scroll: ScrollView? = null
    private var diagnosticExpanded = state?.getBoolean("diagnostic_expanded") ?: false
    fun save(out: Bundle) {
        positions[tab]=scroll?.scrollY ?: 0
        out.putString("settings_tab",tab.name); out.putBoolean("diagnostic_expanded",diagnosticExpanded)
        positions.forEach { (key,value) -> out.putInt("scroll_${key.name}",value) }
    }
    fun render() {
        scroll?.let { positions[tab]=it.scrollY }
        val root=SettingsUi.column(c).apply {
            background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0xFFC1EAFB.toInt(),SettingsUi.background,SettingsUi.background))
        }
        val content=SettingsUi.column(c,18)
        when(tab) { Tab.HOME -> overview(content); Tab.SETTINGS -> settings(content); Tab.INFO -> information(content) }
        val scroller=ScrollView(c).apply { isVerticalScrollBarEnabled=false; addView(content); isFillViewport=true }
        scroll=scroller
        root.addView(scroller,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(navigation(),LinearLayout.LayoutParams(-1,SettingsUi.dp(c,80)))
        c.setContentView(root); SettingsUi.configureWindow(c,root)
        val targetScroll=positions[tab] ?: 0
        scroller.post { scroller.scrollTo(0,targetScroll) }
    }
    private fun navigate(target: Tab) {
        if(tab==target) return
        positions[tab]=scroll?.scrollY ?: 0; scroll=null; tab=target; render()
    }
    private fun logo(size: Int) = ImageView(c).apply {
        setImageResource(R.mipmap.ic_launcher); contentDescription="Biểu tượng LIT Screen OS"
        layoutParams=LinearLayout.LayoutParams(SettingsUi.dp(c,size),SettingsUi.dp(c,size)).apply { gravity=Gravity.CENTER_HORIZONTAL }
    }
    private fun title(content: LinearLayout, text: String, subtitle: String) {
        content.addView(SettingsUi.text(c,text,32f,SettingsUi.ink,true).apply { setPadding(2,SettingsUi.dp(c,12),0,0) })
        content.addView(SettingsUi.text(c,subtitle,14f,SettingsUi.muted),LinearLayout.LayoutParams(-1,-2).apply { topMargin=SettingsUi.dp(c,8); bottomMargin=SettingsUi.dp(c,24) })
    }
    private fun overview(content: LinearLayout) {
        val running=CoverMonitorService.running
        val ready=NavigationService.current!=null && (Settings.canDrawOverlays(c) || Platform.isEmulatorOverlay(c))
        val status=SettingsUi.card(c).apply { setPadding(SettingsUi.dp(c,16),SettingsUi.dp(c,20),SettingsUi.dp(c,16),SettingsUi.dp(c,20)) }
        val header=LinearLayout(c).apply { gravity=Gravity.CENTER_VERTICAL }
        val labels=SettingsUi.column(c)
        labels.addView(SettingsUi.text(c,if(running && ready) "Đang hoạt động" else if(running) "Cần hoàn tất thiết lập" else if(ready) "Sẵn sàng sử dụng" else "Bắt đầu với LIT",18f,SettingsUi.ink,true))
        labels.addView(SettingsUi.text(c,if(running && ready) "Samsung vẫn là màn hình chính" else "Bật menu cho màn hình ngoài",13f,SettingsUi.muted),LinearLayout.LayoutParams(-1,-2).apply { topMargin=SettingsUi.dp(c,5) })
        header.addView(labels,LinearLayout.LayoutParams(0,-2,1f).apply { rightMargin=SettingsUi.dp(c,8) })
        header.addView(SettingsUi.toggle(c,running,"Bật LIT Screen OS") { enabled ->
            c.setMonitor(enabled); c.refreshSoon()
        }.apply { tag="engine-switch" })
        status.addView(header); content.addView(status)

        val actions=LinearLayout(c)
        actions.addView(actionTile(SettingsGlyph.Kind.GRID,"Mở menu","Ứng dụng trên màn hình ngoài") { c.openDrawer() },LinearLayout.LayoutParams(0,-2,1f).apply { rightMargin=SettingsUi.dp(c,6) })
        actions.addView(actionTile(SettingsGlyph.Kind.PHONE,"Samsung","Trở về màn hình ngoài") { c.samsungHome() },LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=SettingsUi.dp(c,6) })
        content.addView(actions,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=SettingsUi.dp(c,16) })

        content.addView(SettingsUi.section(c,"QUYỀN TRUY CẬP"))
        val permissions=SettingsUi.card(c)
        permissions.addView(permissionRow(SettingsGlyph.Kind.OVERLAY,"Hiển thị trên ứng dụng khác",Settings.canDrawOverlays(c)) { c.overlayPermission() })
        permissions.addView(SettingsUi.divider(c))
        permissions.addView(permissionRow(SettingsGlyph.Kind.ACCESS,"Dịch vụ trợ năng",NavigationService.current!=null) { c.accessibility() })
        if(Build.VERSION.SDK_INT>=33) {
            permissions.addView(SettingsUi.divider(c))
            permissions.addView(permissionRow(SettingsGlyph.Kind.BELL,"Thông báo nút Dừng",c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) { c.notificationPermission() })
        }
        content.addView(permissions)
        val connection=SettingsUi.card(c)
        connection.addView(SettingsUi.row(c,SettingsGlyph.Kind.USB,"Xoay ứng dụng qua USB",if(CoverRotation.available) "Máy tính đã kết nối" else "Kết nối máy tính khi cần xoay",SettingsUi.pill(c,if(CoverRotation.available) "Sẵn sàng" else "ADB")) { c.startActivity(Intent(c,RotationSetupActivity::class.java)) })
        content.addView(connection)
        content.addView(SettingsUi.row(c,SettingsGlyph.Kind.PLAY,"Xem thử giao diện", "Mở bản xem trước trên màn hình này") { c.startActivity(Intent(c,CoverActivity::class.java)) }.apply { tag="preview-row" })
    }
    private fun actionTile(kind: SettingsGlyph.Kind, title: String, subtitle: String, action: ()->Unit) = SettingsUi.column(c,16).apply {
        background=SettingsUi.clickBackground(c,SettingsUi.pale,22); minimumHeight=SettingsUi.dp(c,132); isFocusable=true; setOnClickListener { action() }
        addView(SettingsUi.icon(c,kind,SettingsUi.accent,28),LinearLayout.LayoutParams(SettingsUi.dp(c,28),SettingsUi.dp(c,28)))
        addView(SettingsUi.text(c,title,17f,SettingsUi.ink,true),LinearLayout.LayoutParams(-1,-2).apply { topMargin=SettingsUi.dp(c,12) })
        addView(SettingsUi.text(c,subtitle,12f,SettingsUi.muted),LinearLayout.LayoutParams(-1,-2).apply { topMargin=SettingsUi.dp(c,5) })
    }
    private fun permissionRow(kind: SettingsGlyph.Kind,title: String,granted: Boolean,action: ()->Unit) = SettingsUi.row(c,kind,title,if(granted) "Đã cho phép" else "Nhấn để thiết lập",SettingsUi.icon(c,if(granted) SettingsGlyph.Kind.CHECK else SettingsGlyph.Kind.NEXT,if(granted) SettingsUi.green else SettingsUi.muted,22),action)

    private fun settings(content: LinearLayout) {
        title(content,"Cài đặt","Cá nhân hóa menu màn hình ngoài")
        content.addView(SettingsUi.section(c,"MÀN HÌNH NGOÀI"))
        val display=SettingsUi.card(c)
        display.addView(SettingsUi.row(c,SettingsGlyph.Kind.HOME,"Trang chính Samsung","Giữ đồng hồ và widget gốc",SettingsUi.pill(c,"Mặc định")) { c.samsungHome() })
        display.addView(SettingsUi.divider(c))
        display.addView(SettingsUi.row(c,SettingsGlyph.Kind.SWIPE,"Cử chỉ cạnh","Vuốt trong menu để về Samsung") { gestureGuide() })
        content.addView(display)
        content.addView(SettingsUi.section(c,"MENU ỨNG DỤNG"))
        val menu=SettingsUi.card(c)
        val columns=prefs.getInt("columns",4).coerceIn(4,6)
        menu.addView(GridPreview(c,columns),LinearLayout.LayoutParams(-1,SettingsUi.dp(c,146)))
        menu.addView(SettingsUi.row(c,SettingsGlyph.Kind.GRID,"Số cột","Icon tự co theo bố cục",value("$columns cột")) {
            choices("Số cột",listOf("4 cột","5 cột","6 cột"),columns-4) { prefs.edit().putInt("columns",it+4).apply(); refreshDrawer(); render() }
        }.apply { tag="row-columns" })
        menu.addView(SettingsUi.divider(c))
        menu.addView(SettingsUi.row(c,SettingsGlyph.Kind.LABEL,"Hiện tên ứng dụng","Tên bên dưới mỗi icon",SettingsUi.toggle(c,prefs.getBoolean("show_names",false),"Hiện tên ứng dụng") { enabled -> prefs.edit().putBoolean("show_names",enabled).apply(); refreshDrawer(); render() }.apply { tag="names-switch" }))
        menu.addView(SettingsUi.divider(c))
        val sorts=listOf("az","za","used","custom"); val sortLabels=listOf("A → Z","Z → A","Dùng nhiều","Tự sắp xếp")
        val sortIndex=sorts.indexOf(prefs.getString("app_sort","az")).coerceAtLeast(0)
        menu.addView(SettingsUi.row(c,SettingsGlyph.Kind.SORT,"Sắp xếp",null,value(sortLabels[sortIndex])) {
            choices("Sắp xếp ứng dụng",sortLabels,sortIndex) { prefs.edit().putString("app_sort",sorts[it]).apply(); refreshDrawer(); render() }
        }.apply { tag="row-sort" })
        menu.addView(SettingsUi.divider(c))
        val filters=listOf("all","favorite","recent"); val filterLabels=listOf("Tất cả","Yêu thích","Gần đây")
        val filterIndex=filters.indexOf(prefs.getString("app_mode","all")).coerceAtLeast(0)
        menu.addView(SettingsUi.row(c,SettingsGlyph.Kind.FILTER,"Bộ lọc ứng dụng",null,value(filterLabels[filterIndex])) {
            choices("Bộ lọc ứng dụng",filterLabels,filterIndex) { prefs.edit().putString("app_mode",filters[it]).apply(); refreshDrawer(); render() }
        }.apply { tag="row-filter" })
        menu.addView(SettingsUi.divider(c))
        val orientations=listOf("auto","landscape","portrait"); val orientationLabels=listOf("Tự động","Ngang","Dọc")
        val orientIndex=orientations.indexOf(prefs.getString("orientation","auto")).coerceAtLeast(0)
        menu.addView(SettingsUi.row(c,SettingsGlyph.Kind.ROTATE,"Hướng mở ứng dụng","Ép hướng ngang/dọc cần kết nối ADB",value(orientationLabels[orientIndex])) {
            choices("Hướng mở ứng dụng",orientationLabels,orientIndex) { prefs.edit().putString("orientation",orientations[it]).apply(); render() }
        }.apply { tag="row-orientation" })
        menu.addView(SettingsUi.divider(c))
        val hidden=prefs.getStringSet("hidden",emptySet())!!.size
        menu.addView(SettingsUi.row(c,SettingsGlyph.Kind.EYE,"Ứng dụng đã ẩn",if(hidden==0) "Không có ứng dụng bị ẩn" else "$hidden ứng dụng",SettingsUi.pill(c,hidden.toString())) { hiddenApps() })
        content.addView(menu)
        content.addView(SettingsUi.row(c,SettingsGlyph.Kind.USB,"Thiết lập xoay qua USB",if(CoverRotation.available) "Đã kết nối với máy tính" else "Xem hướng dẫn kết nối ADB") { c.startActivity(Intent(c,RotationSetupActivity::class.java)) }.apply { tag="row-adb" })
    }
    private fun value(label: String) = LinearLayout(c).apply {
        gravity=Gravity.CENTER_VERTICAL; background=SettingsUi.shape(SettingsUi.background,12,c,SettingsUi.line)
        setPadding(SettingsUi.dp(c,12),SettingsUi.dp(c,10),SettingsUi.dp(c,10),SettingsUi.dp(c,10))
        addView(SettingsUi.text(c,label,14f,SettingsUi.accent,true))
        addView(SettingsUi.icon(c,SettingsGlyph.Kind.NEXT,SettingsUi.muted,16).apply { rotation=90f },LinearLayout.LayoutParams(SettingsUi.dp(c,16),SettingsUi.dp(c,16)).apply { leftMargin=SettingsUi.dp(c,8) })
    }
    private fun choices(title: String,labels: List<String>,selected: Int,choose: (Int)->Unit) {
        AlertDialog.Builder(c).setTitle(title).setSingleChoiceItems(labels.toTypedArray(),selected) { dialog,which -> dialog.dismiss(); choose(which) }.setNegativeButton("Đóng",null).show()
    }
    private fun refreshDrawer() {
        CoverMonitorService.current?.let { if(it.overlay?.mode==CoverOverlay.Mode.DASHBOARD) it.openDrawer(prefs.getString("app_mode","all") ?: "all") }
    }
    private fun hiddenApps() {
        val hidden=prefs.getStringSet("hidden",emptySet())!!.toList().sorted()
        if(hidden.isEmpty()) { AlertDialog.Builder(c).setTitle("Ứng dụng đã ẩn").setMessage("Nhấn giữ một icon trong menu LIT để ẩn hoặc thêm vào yêu thích.").setPositiveButton("Đã hiểu",null).show(); return }
        val selected=hidden.toMutableSet()
        val labels=hidden.map { key -> try { c.packageManager.getActivityInfo(ComponentName.unflattenFromString(key)!!,0).loadLabel(c.packageManager).toString() } catch(_:Exception) { key.substringBefore('/') } }
        AlertDialog.Builder(c).setTitle("Chọn ứng dụng cần ẩn").setMultiChoiceItems(labels.toTypedArray(),BooleanArray(hidden.size){true}) { _,which,checked -> if(checked) selected.add(hidden[which]) else selected.remove(hidden[which]) }
            .setPositiveButton("Lưu") { _,_ -> prefs.edit().putStringSet("hidden",selected).apply(); refreshDrawer(); render() }.setNegativeButton("Hủy",null).show()
    }
    private fun information(content: LinearLayout) {
        title(content,"Thông tin","LIT Screen OS · ${BuildConfig.VERSION_NAME}")
        val about=SettingsUi.card(c).apply { gravity=Gravity.CENTER_HORIZONTAL; setPadding(SettingsUi.dp(c,20),SettingsUi.dp(c,24),SettingsUi.dp(c,20),SettingsUi.dp(c,24)) }
        about.addView(logo(64)); about.addView(SettingsUi.text(c,"Dành cho màn hình nhỏ",20f,SettingsUi.ink,true),LinearLayout.LayoutParams(-2,-2).apply { topMargin=SettingsUi.dp(c,16) })
        about.addView(SettingsUi.text(c,"Mở ứng dụng trên màn hình ngoài, đồng hành cùng giao diện Samsung.",14f,SettingsUi.muted).apply { gravity=Gravity.CENTER; setLineSpacing(SettingsUi.dp(c,3).toFloat(),1f) },LinearLayout.LayoutParams(-1,-2).apply { topMargin=SettingsUi.dp(c,10) })
        content.addView(about)
        content.addView(SettingsUi.section(c,"THIẾT BỊ & KẾT NỐI"))
        val device=SettingsUi.card(c)
        device.addView(SettingsUi.row(c,SettingsGlyph.Kind.PHONE,if(Build.MODEL.startsWith("SM-F711")) "Galaxy Z Flip3" else Build.MODEL,"Android ${Build.VERSION.RELEASE} · ${Build.MODEL}"))
        device.addView(SettingsUi.divider(c))
        val cover=Platform.cover(c); val size=Point(); @Suppress("DEPRECATION") cover?.getRealSize(size)
        device.addView(SettingsUi.row(c,SettingsGlyph.Kind.OVERLAY,"Màn hình ngoài",if(cover!=null) "${size.x} × ${size.y} · ${Platform.stateName(cover.state)}" else "Chưa tìm thấy màn hình ngoài"))
        device.addView(SettingsUi.divider(c))
        device.addView(SettingsUi.row(c,SettingsGlyph.Kind.USB,"Kết nối xoay ADB",if(CoverRotation.available) "Sẵn sàng qua USB" else "Chưa kết nối") { c.startActivity(Intent(c,RotationSetupActivity::class.java)) })
        content.addView(device)
        val help=SettingsUi.card(c)
        help.addView(SettingsUi.row(c,SettingsGlyph.Kind.SWIPE,"Hướng dẫn cử chỉ","Mở menu, về Samsung và điều hướng") { gestureGuide() })
        help.addView(SettingsUi.divider(c))
        help.addView(SettingsUi.row(c,SettingsGlyph.Kind.BELL,"Thông báo & media","Quyền tùy chọn cho tính năng thông báo") { c.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        help.addView(SettingsUi.divider(c))
        help.addView(SettingsUi.row(c,SettingsGlyph.Kind.INFO,"Chẩn đoán",if(diagnosticExpanded) "Nhấn để thu gọn" else "Xem trạng thái dịch vụ và màn hình") { diagnosticExpanded=!diagnosticExpanded; render() })
        if(diagnosticExpanded) {
            val details=buildString {
                append("Dịch vụ: ${if(CoverMonitorService.running) "đang chạy" else "đã dừng"}\n")
                append(prefs.getString("monitor_status","Chưa bật LIT"))
                prefs.getString("monitor_error",null)?.let { append("\n$it") }
                Platform.displays(c).forEach { display -> val p=Point(); @Suppress("DEPRECATION") display.getRealSize(p); append("\nDisplay ${display.displayId}: ${p.x} × ${p.y} · ${Platform.stateName(display.state)}") }
            }
            help.addView(SettingsUi.text(c,details,13f,SettingsUi.muted).apply { setPadding(SettingsUi.dp(c,20),0,SettingsUi.dp(c,20),SettingsUi.dp(c,20)) })
        }
        if(BuildConfig.DEBUG && Build.MODEL.startsWith("sdk_gphone")) {
            help.addView(SettingsUi.divider(c))
            help.addView(SettingsUi.row(c,SettingsGlyph.Kind.PHONE,"Mô phỏng màn hình ngoài","Chỉ dùng trên máy ảo LIT",SettingsUi.toggle(c,Platform.isEmulatorOverlay(c),"Mô phỏng màn hình ngoài") { checked -> prefs.edit().putBoolean("emulator_overlay",checked).apply(); if(CoverMonitorService.running) CoverMonitorService.current?.schedule(); render() }))
        }
        content.addView(help)
        content.addView(SettingsUi.text(c,"LIT / LABS  ·  ${BuildConfig.VERSION_NAME}",12f,SettingsUi.muted).apply { gravity=Gravity.CENTER; setPadding(0,SettingsUi.dp(c,12),0,SettingsUi.dp(c,16)) })
    }
    private fun gestureGuide() {
        AlertDialog.Builder(c).setTitle("Cử chỉ màn hình ngoài")
            .setMessage("Trên Samsung: vuốt cạnh phải để mở menu LIT.\n\nTrong menu: vuốt từ cạnh trái hoặc phải vào trong để về Samsung. Vuốt lên/xuống để cuộn ứng dụng.\n\nTrong ứng dụng: vuốt cạnh phải để hiện thanh điều hướng.")
            .setPositiveButton("Đã hiểu",null).show()
    }
    private fun navigation() = LinearLayout(c).apply {
        setPadding(SettingsUi.dp(c,8),SettingsUi.dp(c,8),SettingsUi.dp(c,8),SettingsUi.dp(c,6)); setBackgroundColor(0xFFEBF1F6.toInt())
        Tab.entries.forEach { item ->
            val selected=item==tab
            val button=SettingsUi.column(c).apply {
                gravity=Gravity.CENTER; tag="tab-${item.name.lowercase()}"; contentDescription=item.label; isFocusable=true
                background=SettingsUi.clickBackground(c); setOnClickListener { navigate(item) }
            }
            val iconHolder=FrameLayout(c).apply { if(selected) background=SettingsUi.shape(0xFFCDE8F7.toInt(),18,c) }
            iconHolder.addView(SettingsUi.icon(c,item.icon,if(selected) 0xFF143A50.toInt() else SettingsUi.muted),FrameLayout.LayoutParams(SettingsUi.dp(c,24),SettingsUi.dp(c,24),Gravity.CENTER))
            button.addView(iconHolder,LinearLayout.LayoutParams(SettingsUi.dp(c,72),SettingsUi.dp(c,34)))
            button.addView(SettingsUi.text(c,item.label,12f,if(selected) SettingsUi.ink else SettingsUi.muted,selected),LinearLayout.LayoutParams(-2,-2).apply { topMargin=SettingsUi.dp(c,5) })
            addView(button,LinearLayout.LayoutParams(0,-1,1f))
        }
    }
    @android.annotation.SuppressLint("ViewConstructor") // Code-only preview with explicit column count.
    private class GridPreview(context: Context,private val columns: Int) : View(context) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        private val colors=intArrayOf(0xFF82C7FC.toInt(),0xFFFFCD87.toInt(),0xFFA6DCCC.toInt(),0xFFE1BAF5.toInt(),0xFFF1AFA8.toInt(),0xFFACB9FB.toInt())
        init { contentDescription="Xem trước lưới $columns cột" }
        override fun onDraw(canvas: Canvas) {
            val d=resources.displayMetrics.density
            val w=min(width-48*d,264*d); val h=w*260/512; val x=(width-w)/2; val y=(height-h)/2
            paint.color=0xFF172A3A.toInt(); canvas.drawRoundRect(x,y,x+w,y+h,16*d,16*d,paint)
            paint.color=0xFFADC9D7.toInt(); canvas.drawCircle(x+12*d,y+12*d,2*d,paint); canvas.drawCircle(x+20*d,y+12*d,2*d,paint)
            val step=(w-24*d)/columns; val diameter=min(step*.7f,(h-30*d)/2-7*d)
            for(row in 0..1) for(col in 0 until columns) {
                val cx=x+12*d+step*(col+.5f); val cy=y+30*d+(h-30*d)*(row+.5f)/2
                paint.color=colors[(row*columns+col)%colors.size]
                canvas.drawRoundRect(cx-diameter/2,cy-diameter/2,cx+diameter/2,cy+diameter/2,diameter*.3f,diameter*.3f,paint)
                paint.color=0x99FFFFFF.toInt(); canvas.drawCircle(cx,cy,diameter*.16f,paint)
            }
        }
    }
}
