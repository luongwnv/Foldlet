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
    private enum class Tab(private val labelId: Int, val icon: SettingsGlyph.Kind) {
        HOME(R.string.ui_overview,SettingsGlyph.Kind.HOME), SETTINGS(R.string.ui_settings,SettingsGlyph.Kind.SETTINGS), INFO(R.string.ui_about,SettingsGlyph.Kind.INFO);
        val label get() = I18n.text(labelId)
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
        setImageResource(R.mipmap.ic_launcher); contentDescription=I18n.text(R.string.ui_foldlet_icon)
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
        labels.addView(SettingsUi.text(c,if(running && ready) I18n.text(R.string.ui_active) else if(running) I18n.text(R.string.ui_finish_setup) else if(ready) I18n.text(R.string.ui_ready_to_use) else I18n.text(R.string.ui_get_started_with_foldlet),18f,SettingsUi.ink,true))
        labels.addView(SettingsUi.text(c,if(running && ready) I18n.text(R.string.ui_samsung_remains_your_home_screen) else I18n.text(R.string.ui_enable_the_cover_screen_menu),13f,SettingsUi.muted),LinearLayout.LayoutParams(-1,-2).apply { topMargin=SettingsUi.dp(c,5) })
        header.addView(labels,LinearLayout.LayoutParams(0,-2,1f).apply { rightMargin=SettingsUi.dp(c,8) })
        header.addView(SettingsUi.toggle(c,running,I18n.text(R.string.ui_enable_foldlet)) { enabled ->
            c.setMonitor(enabled); c.refreshSoon()
        }.apply { tag="engine-switch" })
        status.addView(header); content.addView(status)

        val actions=LinearLayout(c)
        actions.addView(actionTile(SettingsGlyph.Kind.GRID,I18n.text(R.string.ui_open_menu),I18n.text(R.string.ui_apps_on_the_cover_screen)) { c.openDrawer() },LinearLayout.LayoutParams(0,-2,1f).apply { rightMargin=SettingsUi.dp(c,6) })
        actions.addView(actionTile(SettingsGlyph.Kind.PHONE,"Samsung",I18n.text(R.string.ui_return_to_the_cover_home_screen)) { c.samsungHome() },LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=SettingsUi.dp(c,6) })
        content.addView(actions,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=SettingsUi.dp(c,16) })

        content.addView(SettingsUi.section(c,I18n.text(R.string.ui_permissions)))
        val permissions=SettingsUi.card(c)
        permissions.addView(permissionRow(SettingsGlyph.Kind.OVERLAY,I18n.text(R.string.ui_display_over_other_apps),Settings.canDrawOverlays(c)) { c.overlayPermission() })
        permissions.addView(SettingsUi.divider(c))
        permissions.addView(permissionRow(SettingsGlyph.Kind.ACCESS,I18n.text(R.string.ui_accessibility_service),NavigationService.current!=null) { c.accessibility() })
        permissions.addView(SettingsUi.divider(c))
        permissions.addView(permissionRow(SettingsGlyph.Kind.GRID,I18n.text(R.string.ui_usage_access_recent_apps),RecentApps.hasAccess(c)) { RecentApps.openAccessSettings(c) })
        if(Build.VERSION.SDK_INT>=33) {
            permissions.addView(SettingsUi.divider(c))
            permissions.addView(permissionRow(SettingsGlyph.Kind.BELL,I18n.text(R.string.ui_notification_with_stop_button),c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) { c.notificationPermission() })
        }
        content.addView(permissions)
        val connection=SettingsUi.card(c)
        connection.addView(SettingsUi.row(c,SettingsGlyph.Kind.USB,I18n.text(R.string.ui_app_rotation_over_usb),if(CoverRotation.available) I18n.text(R.string.ui_computer_connected) else I18n.text(R.string.ui_connect_a_computer_when_you_need_rotation),SettingsUi.pill(c,if(CoverRotation.available) I18n.text(R.string.ui_ready) else "ADB")) { c.startActivity(Intent(c,RotationSetupActivity::class.java)) })
        content.addView(connection)
        content.addView(SettingsUi.row(c,SettingsGlyph.Kind.PLAY,I18n.text(R.string.ui_preview_drawer), I18n.text(R.string.ui_open_a_preview_on_this_screen)) { c.startActivity(Intent(c,CoverActivity::class.java)) }.apply { tag="preview-row" })
    }
    private fun actionTile(kind: SettingsGlyph.Kind, title: String, subtitle: String, action: ()->Unit) = SettingsUi.column(c,16).apply {
        background=SettingsUi.clickBackground(c,SettingsUi.pale,22); minimumHeight=SettingsUi.dp(c,132); isFocusable=true; setOnClickListener { action() }
        addView(SettingsUi.icon(c,kind,SettingsUi.accent,28),LinearLayout.LayoutParams(SettingsUi.dp(c,28),SettingsUi.dp(c,28)))
        addView(SettingsUi.text(c,title,17f,SettingsUi.ink,true),LinearLayout.LayoutParams(-1,-2).apply { topMargin=SettingsUi.dp(c,12) })
        addView(SettingsUi.text(c,subtitle,12f,SettingsUi.muted),LinearLayout.LayoutParams(-1,-2).apply { topMargin=SettingsUi.dp(c,5) })
    }
    private fun permissionRow(kind: SettingsGlyph.Kind,title: String,granted: Boolean,action: ()->Unit) = SettingsUi.row(c,kind,title,if(granted) I18n.text(R.string.ui_allowed) else I18n.text(R.string.ui_tap_to_set_up),SettingsUi.icon(c,if(granted) SettingsGlyph.Kind.CHECK else SettingsGlyph.Kind.NEXT,if(granted) SettingsUi.green else SettingsUi.muted,22),action)

    private fun settings(content: LinearLayout) {
        title(content,I18n.text(R.string.ui_settings),I18n.text(R.string.ui_personalize_your_cover_screen_menu))
        content.addView(SettingsUi.section(c,I18n.text(R.string.ui_cover_screen_2)))
        val display=SettingsUi.card(c)
        display.addView(SettingsUi.row(c,SettingsGlyph.Kind.HOME,I18n.text(R.string.ui_samsung_home_screen),I18n.text(R.string.ui_keep_the_native_clock_and_widgets),SettingsUi.pill(c,I18n.text(R.string.ui_default))) { c.samsungHome() })
        display.addView(SettingsUi.divider(c))
        display.addView(SettingsUi.row(c,SettingsGlyph.Kind.SWIPE,I18n.text(R.string.ui_edge_gestures),I18n.text(R.string.ui_swipe_in_the_menu_to_return_to)) { gestureGuide() })
        content.addView(display)
        content.addView(SettingsUi.section(c,I18n.text(R.string.ui_app_drawer)))
        val menu=SettingsUi.card(c)
        val columns=prefs.getInt("columns",4).coerceIn(4,6)
        menu.addView(GridPreview(c,columns),LinearLayout.LayoutParams(-1,SettingsUi.dp(c,146)))
        menu.addView(SettingsUi.row(c,SettingsGlyph.Kind.GRID,I18n.text(R.string.ui_columns),I18n.text(R.string.ui_icons_adapt_to_the_layout),value(I18n.text(R.string.ui_columns_2, columns))) {
            choices(I18n.text(R.string.ui_columns),listOf(I18n.text(R.string.ui_4_columns),I18n.text(R.string.ui_5_columns),I18n.text(R.string.ui_6_columns)),columns-4) { prefs.edit().putInt("columns",it+4).apply(); refreshDrawer(); render() }
        }.apply { tag="row-columns" })
        menu.addView(SettingsUi.divider(c))
        menu.addView(SettingsUi.row(c,SettingsGlyph.Kind.LABEL,I18n.text(R.string.ui_show_app_names),I18n.text(R.string.ui_names_below_each_icon),SettingsUi.toggle(c,prefs.getBoolean("show_names",false),I18n.text(R.string.ui_show_app_names)) { enabled -> prefs.edit().putBoolean("show_names",enabled).apply(); refreshDrawer(); render() }.apply { tag="names-switch" }))
        menu.addView(SettingsUi.divider(c))
        val sorts=listOf("az","za","used","custom"); val sortLabels=listOf("A → Z","Z → A",I18n.text(R.string.ui_most_used),I18n.text(R.string.ui_custom_order))
        val sortIndex=sorts.indexOf(prefs.getString("app_sort","az")).coerceAtLeast(0)
        menu.addView(SettingsUi.row(c,SettingsGlyph.Kind.SORT,I18n.text(R.string.ui_sort),null,value(sortLabels[sortIndex])) {
            choices(I18n.text(R.string.ui_sort_apps),sortLabels,sortIndex) { prefs.edit().putString("app_sort",sorts[it]).apply(); refreshDrawer(); render() }
        }.apply { tag="row-sort" })
        menu.addView(SettingsUi.divider(c))
        val filters=listOf("all","favorite","recent"); val filterLabels=listOf(I18n.text(R.string.ui_all),I18n.text(R.string.ui_favorites),I18n.text(R.string.ui_recent_apps))
        val filterIndex=filters.indexOf(prefs.getString("app_mode","all")).coerceAtLeast(0)
        menu.addView(SettingsUi.row(c,SettingsGlyph.Kind.FILTER,I18n.text(R.string.ui_app_filter),null,value(filterLabels[filterIndex])) {
            choices(I18n.text(R.string.ui_app_filter),filterLabels,filterIndex) { prefs.edit().putString("app_mode",filters[it]).apply(); refreshDrawer(); render() }
        }.apply { tag="row-filter" })
        menu.addView(SettingsUi.divider(c))
        val orientations=listOf("auto","landscape","portrait"); val orientationLabels=listOf(I18n.text(R.string.ui_auto),I18n.text(R.string.ui_landscape),I18n.text(R.string.ui_portrait))
        val orientIndex=orientations.indexOf(prefs.getString("orientation","auto")).coerceAtLeast(0)
        menu.addView(SettingsUi.row(c,SettingsGlyph.Kind.ROTATE,I18n.text(R.string.ui_app_orientation),I18n.text(R.string.ui_forcing_orientation_requires_an_adb_connection),value(orientationLabels[orientIndex])) {
            choices(I18n.text(R.string.ui_app_orientation),orientationLabels,orientIndex) { prefs.edit().putString("orientation",orientations[it]).apply(); render() }
        }.apply { tag="row-orientation" })
        menu.addView(SettingsUi.divider(c))
        val hidden=prefs.getStringSet("hidden",emptySet())!!.size
        menu.addView(SettingsUi.row(c,SettingsGlyph.Kind.EYE,I18n.text(R.string.ui_hidden_apps),if(hidden==0) I18n.text(R.string.ui_no_hidden_apps) else I18n.text(R.string.ui_apps, hidden),SettingsUi.pill(c,hidden.toString())) { hiddenApps() })
        content.addView(menu)
        content.addView(SettingsUi.row(c,SettingsGlyph.Kind.USB,I18n.text(R.string.ui_set_up_usb_rotation),if(CoverRotation.available) I18n.text(R.string.ui_connected_to_a_computer) else I18n.text(R.string.ui_view_adb_connection_instructions)) { c.startActivity(Intent(c,RotationSetupActivity::class.java)) }.apply { tag="row-adb" })
    }
    private fun value(label: String) = LinearLayout(c).apply {
        gravity=Gravity.CENTER_VERTICAL; background=SettingsUi.shape(SettingsUi.background,12,c,SettingsUi.line)
        setPadding(SettingsUi.dp(c,12),SettingsUi.dp(c,10),SettingsUi.dp(c,10),SettingsUi.dp(c,10))
        addView(SettingsUi.text(c,label,14f,SettingsUi.accent,true))
        addView(SettingsUi.icon(c,SettingsGlyph.Kind.NEXT,SettingsUi.muted,16).apply { rotation=90f },LinearLayout.LayoutParams(SettingsUi.dp(c,16),SettingsUi.dp(c,16)).apply { leftMargin=SettingsUi.dp(c,8) })
    }
    private fun choices(title: String,labels: List<String>,selected: Int,choose: (Int)->Unit) {
        AlertDialog.Builder(c).setTitle(title).setSingleChoiceItems(labels.toTypedArray(),selected) { dialog,which -> dialog.dismiss(); choose(which) }.setNegativeButton(I18n.text(R.string.ui_close),null).show()
    }
    private fun refreshDrawer() {
        CoverMonitorService.current?.let { if(it.overlay?.mode==CoverOverlay.Mode.DASHBOARD) it.openDrawer(prefs.getString("app_mode","all") ?: "all") }
    }
    private fun hiddenApps() {
        val hidden=prefs.getStringSet("hidden",emptySet())!!.toList().sorted()
        if(hidden.isEmpty()) { AlertDialog.Builder(c).setTitle(I18n.text(R.string.ui_hidden_apps)).setMessage(I18n.text(R.string.ui_hold_an_icon_in_foldlet_to_hide)).setPositiveButton(I18n.text(R.string.ui_got_it),null).show(); return }
        val selected=hidden.toMutableSet()
        val labels=hidden.map { key -> try { c.packageManager.getActivityInfo(ComponentName.unflattenFromString(key)!!,0).loadLabel(c.packageManager).toString() } catch(_:Exception) { key.substringBefore('/') } }
        AlertDialog.Builder(c).setTitle(I18n.text(R.string.ui_choose_apps_to_hide)).setMultiChoiceItems(labels.toTypedArray(),BooleanArray(hidden.size){true}) { _,which,checked -> if(checked) selected.add(hidden[which]) else selected.remove(hidden[which]) }
            .setPositiveButton(I18n.text(R.string.ui_save)) { _,_ -> prefs.edit().putStringSet("hidden",selected).apply(); refreshDrawer(); render() }.setNegativeButton(I18n.text(R.string.ui_cancel),null).show()
    }
    private fun information(content: LinearLayout) {
        title(content,I18n.text(R.string.ui_about),"Foldlet · ${BuildConfig.VERSION_NAME}")
        val about=SettingsUi.card(c).apply { gravity=Gravity.CENTER_HORIZONTAL; setPadding(SettingsUi.dp(c,20),SettingsUi.dp(c,24),SettingsUi.dp(c,20),SettingsUi.dp(c,24)) }
        about.addView(logo(64)); about.addView(SettingsUi.text(c,I18n.text(R.string.ui_made_for_the_small_screen),20f,SettingsUi.ink,true),LinearLayout.LayoutParams(-2,-2).apply { topMargin=SettingsUi.dp(c,16) })
        about.addView(SettingsUi.text(c,I18n.text(R.string.ui_open_apps_on_the_cover_screen_alongside),14f,SettingsUi.muted).apply { gravity=Gravity.CENTER; setLineSpacing(SettingsUi.dp(c,3).toFloat(),1f) },LinearLayout.LayoutParams(-1,-2).apply { topMargin=SettingsUi.dp(c,10) })
        content.addView(about)
        content.addView(SettingsUi.section(c,I18n.text(R.string.ui_device_connection)))
        val device=SettingsUi.card(c)
        device.addView(SettingsUi.row(c,SettingsGlyph.Kind.PHONE,if(Build.MODEL.startsWith("SM-F711")) "Galaxy Z Flip3" else Build.MODEL,"Android ${Build.VERSION.RELEASE} · ${Build.MODEL}"))
        device.addView(SettingsUi.divider(c))
        val cover=Platform.cover(c); val size=Point(); @Suppress("DEPRECATION") cover?.getRealSize(size)
        device.addView(SettingsUi.row(c,SettingsGlyph.Kind.OVERLAY,I18n.text(R.string.ui_cover_screen_3),if(cover!=null) "${size.x} × ${size.y} · ${Platform.stateName(cover.state)}" else I18n.text(R.string.ui_cover_screen_not_found)))
        device.addView(SettingsUi.divider(c))
        device.addView(SettingsUi.row(c,SettingsGlyph.Kind.USB,I18n.text(R.string.ui_adb_rotation_connection),if(CoverRotation.available) I18n.text(R.string.ui_ready_over_usb) else I18n.text(R.string.ui_not_connected)) { c.startActivity(Intent(c,RotationSetupActivity::class.java)) })
        content.addView(device)
        val help=SettingsUi.card(c)
        help.addView(SettingsUi.row(c,SettingsGlyph.Kind.SWIPE,I18n.text(R.string.ui_gesture_guide),I18n.text(R.string.ui_open_apps_return_home_and_navigate)) { gestureGuide() })
        help.addView(SettingsUi.divider(c))
        help.addView(SettingsUi.row(c,SettingsGlyph.Kind.BELL,I18n.text(R.string.ui_notifications_media),I18n.text(R.string.ui_optional_access_for_notification_features)) { c.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        help.addView(SettingsUi.divider(c))
        help.addView(SettingsUi.row(c,SettingsGlyph.Kind.INFO,I18n.text(R.string.ui_diagnostics),if(diagnosticExpanded) I18n.text(R.string.ui_tap_to_collapse) else I18n.text(R.string.ui_view_service_and_display_status)) { diagnosticExpanded=!diagnosticExpanded; render() })
        if(diagnosticExpanded) {
            val details=buildString {
                append(I18n.text(R.string.ui_service, I18n.text(if(CoverMonitorService.running) R.string.ui_running else R.string.ui_stopped)))
                append(prefs.getString("monitor_status",I18n.text(R.string.ui_foldlet_is_not_enabled)))
                prefs.getString("monitor_error",null)?.let { append("\n$it") }
                Platform.displays(c).forEach { display -> val p=Point(); @Suppress("DEPRECATION") display.getRealSize(p); append("\nDisplay ${display.displayId}: ${p.x} × ${p.y} · ${Platform.stateName(display.state)}") }
            }
            help.addView(SettingsUi.text(c,details,13f,SettingsUi.muted).apply { setPadding(SettingsUi.dp(c,20),0,SettingsUi.dp(c,20),SettingsUi.dp(c,20)) })
        }
        if(BuildConfig.DEBUG && Build.MODEL.startsWith("sdk_gphone")) {
            help.addView(SettingsUi.divider(c))
            help.addView(SettingsUi.row(c,SettingsGlyph.Kind.PHONE,I18n.text(R.string.ui_emulate_the_cover_screen),I18n.text(R.string.ui_only_for_the_dedicated_foldlet_emulator),SettingsUi.toggle(c,Platform.isEmulatorOverlay(c),I18n.text(R.string.ui_emulate_the_cover_screen)) { checked -> prefs.edit().putBoolean("emulator_overlay",checked).apply(); if(CoverMonitorService.running) CoverMonitorService.current?.schedule(); render() }))
        }
        content.addView(help)
        content.addView(SettingsUi.text(c,"LIT / LABS  ·  ${BuildConfig.VERSION_NAME}",12f,SettingsUi.muted).apply { gravity=Gravity.CENTER; setPadding(0,SettingsUi.dp(c,12),0,SettingsUi.dp(c,16)) })
    }
    private fun gestureGuide() {
        AlertDialog.Builder(c).setTitle(I18n.text(R.string.ui_cover_screen_gestures))
            .setMessage(I18n.text(R.string.ui_on_the_samsung_clock_page_tap_the))
            .setPositiveButton(I18n.text(R.string.ui_got_it),null).show()
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
    @android.annotation.SuppressLint("ViewConstructor")
    private class GridPreview(context: Context, columns: Int) : FrameLayout(context) {
        private val drawer: AppDrawerView
        private val clip = Path()
        init {
            contentDescription=I18n.text(R.string.ui_preview_of_the_column_drawer, columns)
            importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            descendantFocusability=FOCUS_BLOCK_DESCENDANTS
            val config=android.content.res.Configuration(resources.configuration).apply {
                densityDpi=160; screenWidthDp=512; screenHeightDp=260; smallestScreenWidthDp=260
                orientation=android.content.res.Configuration.ORIENTATION_LANDSCAPE
            }
            val coverContext=ContextThemeWrapper(context.createConfigurationContext(config),R.style.CoverTheme)
            drawer=AppDrawerView(coverContext,1,{})
            addView(drawer,LayoutParams(512,260))
        }
        override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),MeasureSpec.getSize(heightMeasureSpec))
            drawer.measure(MeasureSpec.makeMeasureSpec(512,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(260,MeasureSpec.EXACTLY))
        }
        override fun onLayout(changed: Boolean,left: Int,top: Int,right: Int,bottom: Int) { drawer.layout(0,0,512,260) }
        override fun dispatchDraw(canvas: Canvas) {
            val d=resources.displayMetrics.density
            val w=min(width-48*d,264*d); val h=w*260/512; val x=(width-w)/2; val y=(height-h)/2
            clip.reset(); clip.addRoundRect(x,y,x+w,y+h,16*d,16*d,Path.Direction.CW)
            val save=canvas.save()
            canvas.clipPath(clip); canvas.translate(x,y); canvas.scale(w/512,w/512)
            super.dispatchDraw(canvas)
            canvas.restoreToCount(save)
        }
        // Preview is a real attached drawer, but only the separate preview action
        // opens apps; touching this scaled illustration must not launch anything.
        override fun onInterceptTouchEvent(event: MotionEvent) = true
        override fun onTouchEvent(event: MotionEvent) = false
    }
}
