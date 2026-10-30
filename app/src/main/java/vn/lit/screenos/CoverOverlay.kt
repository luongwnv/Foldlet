package vn.lit.screenos

import android.app.Dialog
import android.graphics.*
import android.provider.Settings
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView

/** Persistent cover windows: dashboard, right-edge gesture strip, navigation rail. */
class CoverOverlay(
    private val service: NavigationService,
    private val display: Display,
    private val onMinimize: () -> Unit,
    private val onExpand: () -> Unit,
    private val onYieldToStock: () -> Unit,
    private val onNavigation: () -> Unit,
    private val onRecents: () -> Unit,
    private val initialSection: String = "apps",
    private val onSamsungHome: () -> Unit = onYieldToStock,
    private val onBack: () -> Unit = {
        if (Platform.unlocked(service)) when(service.sendCoverBack(display.displayId)) {
            NavigationService.BackResult.SENT -> onMinimize()
            NavigationService.BackResult.LAUNCHER_ROOT -> onSamsungHome()
            NavigationService.BackResult.UNAVAILABLE -> Unit
        }
    },
    private val onKeyboard: () -> Unit = {},
    private val onHomeTap: () -> Unit = onExpand,
    private val onShortcuts: () -> Unit = {},
) {
    enum class Mode { HIDDEN, DASHBOARD, RECENTS, HOME_EDGE, EDGE, SIDEBAR, KEYBOARD, SHORTCUTS }
    var mode = Mode.HIDDEN; private set
    private var panel: Dialog? = null
    private var homeGestures: HomeGestureOverlay?=null
    private var attached: View? = null
    private var applicationWindow = false
    private var preferApplicationWindow = false
    val window: Window? get() = panel?.window
    val view: View? get() = attached
    val displayId get() = display.displayId
    val backend get() = if (applicationWindow) "application-overlay-2038" else "accessibility-overlay-2032"
    private val a11yContext = service.createDisplayContext(display)
    private val appContext = service.applicationContext.createDisplayContext(display)
    private var manager = a11yContext.getSystemService(WindowManager::class.java)

    fun show(requested: Mode) {
        if (requested == Mode.HIDDEN) { hide(); return }
        // addView already owns the pending root before its first frame. Repeated
        // accessibility updates must not replace that window while it lays out.
        if (mode == requested && attached != null) return
        hide()
        val dialog = Dialog(a11yContext, R.style.CoverTheme)
        val window = requireNotNull(dialog.window)
        val full = requested == Mode.DASHBOARD || requested == Mode.RECENTS || requested==Mode.SHORTCUTS
        val title = if(requested==Mode.SHORTCUTS) SHORTCUTS_TITLE else if(requested == Mode.KEYBOARD) KEYBOARD_TITLE else if(requested == Mode.RECENTS) RECENTS_TITLE else if(full) DASHBOARD_TITLE else if(requested == Mode.HOME_EDGE || requested == Mode.EDGE) EDGE_TITLE else NAVIGATION_TITLE
        val homeEdge = requested == Mode.HOME_EDGE
        val edge = homeEdge || requested == Mode.EDGE
        try {
            if(requested==Mode.SHORTCUTS) {
                dialog.setContentView(QuickShortcutsView(dialog.context,displayId,onSamsungHome,onMinimize))
            } else if(requested == Mode.KEYBOARD) {
                val target=service.coverInputTarget(displayId) ?: return
                val input=LinearLayout(dialog.context).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.BLACK) }
                val header=LinearLayout(dialog.context).apply { gravity=Gravity.CENTER_VERTICAL }
                header.addView(Glyph.button(dialog.context,Glyph.Kind.CLOSE,I18n.text(R.string.ui_hide_keyboard),action=onMinimize),LinearLayout.LayoutParams(Ui.dp(dialog.context,40),-1))
                header.addView(TextView(dialog.context).apply { text="Foldlet";textSize=14f;setTextColor(Color.WHITE) },LinearLayout.LayoutParams(0,-2,1f))
                input.addView(header,LinearLayout.LayoutParams(-1,Ui.dp(dialog.context,28)))
                input.addView(CoverKeyboardView(dialog.context,target),LinearLayout.LayoutParams(-1,-2))
                dialog.setContentView(input)
            } else if (requested == Mode.RECENTS) {
                dialog.setContentView(RecentAppsView(dialog.context,displayId,onMinimize))
            } else if (full) {
                val dashboard = CoverView(dialog.context, window, displayId, onMinimize, onSamsungHome)
                if (initialSection != "apps") dashboard.openSection(initialSection)
                dialog.setContentView(dashboard)
            } else if (edge) {
                val strip = object : View(dialog.context) {
                    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0x60FFFFFF }
                    override fun onDraw(canvas: Canvas) {
                        super.onDraw(canvas)
                        if(homeEdge && HomeTouchConfig.read(context).enabled) return
                        canvas.drawRoundRect(width-Ui.dp(context,4).toFloat(),height/2f-20,width-1f,height/2f+20,2f,2f,paint)
                    }
                    override fun performClick(): Boolean { super.performClick();onNavigation();return true }
                }
                strip.isClickable=true
                strip.contentDescription=I18n.text(if(homeEdge) R.string.ui_home_swipe_navigation else R.string.ui_tap_or_swipe_the_right_edge_to_2)
                strip.setOnTouchListener(HomeGestureOverlay.listener(a11yContext,{ if(!homeEdge) strip.performClick() },onNavigation,
                    if(homeEdge && Platform.preferences(service).getBoolean("home_shortcuts_enabled",true)) onShortcuts else null))
                dialog.setContentView(strip)
            } else {
                val rail = LinearLayout(dialog.context).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
                fun item(icon: Glyph.Kind, title: String, action: () -> Unit) {
                    rail.addView(Glyph.button(dialog.context, icon, title, action = action), LinearLayout.LayoutParams(-1, 0, 1f))
                }
                item(Glyph.Kind.NAV_BACK, I18n.text(R.string.ui_back), onBack)
                item(Glyph.Kind.GRID, I18n.text(R.string.ui_apps_2)) { onExpand() }
                item(Glyph.Kind.NAV_RECENT, I18n.text(R.string.ui_recent_apps_across_the_phone)) { onRecents() }
                item(Glyph.Kind.NAV_HOME, I18n.text(R.string.ui_return_to_samsung_home)) { onSamsungHome() }
                if(Platform.preferences(service).getBoolean("force_cover_keyboard",false)) item(Glyph.Kind.KEYBOARD,I18n.text(R.string.ui_open_keyboard),onKeyboard)
                item(Glyph.Kind.CLOSE, I18n.text(R.string.ui_hide_navigation)) { onMinimize() }
                rail.getChildAt(rail.childCount-1).setOnLongClickListener { onYieldToStock(); true }
                dialog.setContentView(rail)
            }
            dialog.setCancelable(false)
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setTitle(title)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            if (!full) window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            if (requested == Mode.SIDEBAR || requested==Mode.KEYBOARD) window.addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            window.setDecorFitsSystemWindows(false)
            window.attributes = window.attributes.apply {
                setTitle(title)
                type = if (preferApplicationWindow) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                format = PixelFormat.TRANSLUCENT
                width = if (full || requested==Mode.KEYBOARD) WindowManager.LayoutParams.MATCH_PARENT else Ui.dp(a11yContext, if (requested == Mode.EDGE || requested==Mode.HOME_EDGE) 18 else 48)
                height = if(requested==Mode.KEYBOARD) Ui.dp(a11yContext,164) else WindowManager.LayoutParams.MATCH_PARENT
                gravity = if(requested==Mode.KEYBOARD) Gravity.BOTTOM or Gravity.LEFT else Gravity.TOP or if (full) Gravity.LEFT else Gravity.RIGHT
                x = 0; y = 0
                setFitInsetsTypes(0)
            }
            val decor = window.decorView
            window.callback = object : Window.Callback by dialog {
                override fun onWindowAttributesChanged(attributes: WindowManager.LayoutParams) {
                    if (attached === decor && decor.isAttachedToWindow) manager.updateViewLayout(decor, attributes)
                }
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (event.keyCode == KeyEvent.KEYCODE_BACK) { if (event.action == KeyEvent.ACTION_UP) { if(requested==Mode.SHORTCUTS) onSamsungHome() else onMinimize() }; return true }
                    return dialog.dispatchKeyEvent(event)
                }
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    if ((requested == Mode.SIDEBAR || requested==Mode.KEYBOARD) && event.action == MotionEvent.ACTION_OUTSIDE) { onMinimize(); return true }
                    return dialog.dispatchTouchEvent(event)
                }
            }
            manager = (if (preferApplicationWindow) appContext else a11yContext).getSystemService(WindowManager::class.java)
            try { manager.addView(decor, window.attributes) }
            catch (error: WindowManager.BadTokenException) {
                if (!Settings.canDrawOverlays(service)) throw IllegalStateException(I18n.text(R.string.ui_the_firmware_did_not_provide_a_cover), error)
                manager = appContext.getSystemService(WindowManager::class.java)
                window.attributes = window.attributes.apply { type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY; token = null }
                manager.addView(decor, window.attributes)
                preferApplicationWindow = true
            }
            applicationWindow = window.attributes.type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            attached = decor; panel = dialog; mode = requested
            if(homeEdge) {
                homeGestures=HomeGestureOverlay(a11yContext,manager,window.attributes,display,onHomeTap,onNavigation,onShortcuts)
                homeGestures!!.show()
            }
        } catch (error: RuntimeException) { hide(); throw error }
    }

    fun hide() {
        homeGestures?.hide();homeGestures=null
        val decor = attached
        attached = null; panel = null; mode = Mode.HIDDEN
        // WindowManager registers the root before the first traversal attaches
        // the decor. Removing only attached views leaks pending cover windows,
        // especially when the panel sleeps or a mode changes before drawing.
        if (decor != null) manager.removeViewImmediate(decor)
    }
    companion object {
        const val DASHBOARD_TITLE = "Foldlet · cover dashboard"
        const val EDGE_TITLE = "Foldlet · right edge"
        const val NAVIGATION_TITLE = "Foldlet · right navigation"
        const val RECENTS_TITLE = "Foldlet · recent applications"
        const val KEYBOARD_TITLE = "Foldlet · cover keyboard"
        const val SHORTCUTS_TITLE = "Foldlet · shortcuts"
    }
}
