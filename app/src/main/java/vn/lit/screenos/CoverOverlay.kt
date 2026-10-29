package vn.lit.screenos

import android.accessibilityservice.AccessibilityService
import android.app.Dialog
import android.graphics.*
import android.provider.Settings
import android.view.*
import android.widget.LinearLayout

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
) {
    enum class Mode { HIDDEN, DASHBOARD, HOME_EDGE, EDGE, SIDEBAR }
    var mode = Mode.HIDDEN; private set
    private var panel: Dialog? = null
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
        val full = requested == Mode.DASHBOARD
        val homeEdge = requested == Mode.HOME_EDGE
        val edge = homeEdge || requested == Mode.EDGE
        try {
            if (full) {
                val dashboard = CoverView(dialog.context, window, displayId, onMinimize, onSamsungHome)
                if (initialSection != "apps") dashboard.openSection(initialSection)
                dialog.setContentView(dashboard)
            } else if (edge) {
                var downX = 0f; var downY = 0f; var revealed = false
                var tapCandidate = false; var verticalGesture = false
                val tapSlop = ViewConfiguration.get(a11yContext).scaledTouchSlop.coerceAtLeast(Ui.dp(a11yContext,12))
                val strip = object : View(dialog.context) {
                    override fun onDraw(canvas: Canvas) {
                        super.onDraw(canvas)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x60FFFFFF }
                        canvas.drawRoundRect(width - Ui.dp(context,4).toFloat(), height / 2f - 20, width - 1f, height / 2f + 20, 2f, 2f, paint)
                    }
                    override fun performClick(): Boolean { super.performClick(); onNavigation(); return true }
                }
                strip.isClickable = true
                strip.contentDescription = if (homeEdge) "Chạm hoặc vuốt cạnh phải để mở ứng dụng" else "Chạm hoặc vuốt cạnh phải để mở điều hướng"
                strip.setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.rawX; downY = event.rawY; revealed = false
                            tapCandidate = true; verticalGesture = false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = downX - event.rawX
                            val dy = kotlin.math.abs(event.rawY - downY)
                            if (kotlin.math.abs(dx) > tapSlop || dy > tapSlop) tapCandidate = false
                            if (dy > tapSlop && dy > kotlin.math.abs(dx)) verticalGesture = true
                            if (!revealed && !verticalGesture && dx > Ui.dp(a11yContext,24) && dx > dy * 1.25f) {
                                revealed = true; onNavigation()
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!revealed && tapCandidate && kotlin.math.abs(event.rawX - downX) <= tapSlop && kotlin.math.abs(event.rawY - downY) <= tapSlop) strip.performClick()
                            tapCandidate = false
                        }
                        MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> { tapCandidate = false; verticalGesture = true }
                    }
                    true
                }
                dialog.setContentView(strip)
            } else {
                val rail = LinearLayout(dialog.context).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
                fun item(icon: Glyph.Kind, title: String, action: () -> Unit) {
                    rail.addView(Glyph.button(dialog.context, icon, title, action = action), LinearLayout.LayoutParams(-1, 0, 1f))
                }
                item(Glyph.Kind.NAV_BACK, "Quay lại") {
                    if (Platform.unlocked(service)) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    onMinimize()
                }
                item(Glyph.Kind.GRID, "Ứng dụng") { onExpand() }
                item(Glyph.Kind.NAV_RECENT, "Gần đây") { onRecents() }
                item(Glyph.Kind.NAV_HOME, "Về màn hình Samsung") { onSamsungHome() }
                item(Glyph.Kind.FORWARD, "Ẩn điều hướng") { onMinimize() }
                rail.getChildAt(4).setOnLongClickListener { onYieldToStock(); true }
                dialog.setContentView(rail)
            }
            dialog.setCancelable(false)
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setTitle(if (full) DASHBOARD_TITLE else if (edge) EDGE_TITLE else NAVIGATION_TITLE)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            if (!full) window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            if (requested == Mode.SIDEBAR) window.addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            window.setDecorFitsSystemWindows(false)
            window.attributes = window.attributes.apply {
                setTitle(if (full) DASHBOARD_TITLE else if (edge) EDGE_TITLE else NAVIGATION_TITLE)
                type = if (preferApplicationWindow) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                format = PixelFormat.TRANSLUCENT
                width = if (full) WindowManager.LayoutParams.MATCH_PARENT else Ui.dp(a11yContext, if (requested == Mode.EDGE) 18 else 48)
                height = WindowManager.LayoutParams.MATCH_PARENT
                gravity = Gravity.TOP or if (full) Gravity.LEFT else Gravity.RIGHT
                x = 0; y = 0
                setFitInsetsTypes(0)
            }
            val decor = window.decorView
            window.callback = object : Window.Callback by dialog {
                override fun onWindowAttributesChanged(attributes: WindowManager.LayoutParams) {
                    if (attached === decor && decor.isAttachedToWindow) manager.updateViewLayout(decor, attributes)
                }
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (event.keyCode == KeyEvent.KEYCODE_BACK) { if (event.action == KeyEvent.ACTION_UP) onMinimize(); return true }
                    return dialog.dispatchKeyEvent(event)
                }
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    if (requested == Mode.SIDEBAR && event.action == MotionEvent.ACTION_OUTSIDE) { onMinimize(); return true }
                    return dialog.dispatchTouchEvent(event)
                }
            }
            manager = (if (preferApplicationWindow) appContext else a11yContext).getSystemService(WindowManager::class.java)
            try { manager.addView(decor, window.attributes) }
            catch (error: WindowManager.BadTokenException) {
                if (!Settings.canDrawOverlays(service)) throw IllegalStateException("Firmware không cấp token overlay cho màn hình ngoài. Bật quyền Hiển thị trên ứng dụng khác trong LIT.", error)
                manager = appContext.getSystemService(WindowManager::class.java)
                window.attributes = window.attributes.apply { type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY; token = null }
                manager.addView(decor, window.attributes)
                preferApplicationWindow = true
            }
            applicationWindow = window.attributes.type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            attached = decor; panel = dialog; mode = requested
        } catch (error: RuntimeException) { hide(); throw error }
    }

    fun hide() {
        val decor = attached
        attached = null; panel = null; mode = Mode.HIDDEN
        // WindowManager registers the root before the first traversal attaches
        // the decor. Removing only attached views leaks pending cover windows,
        // especially when the panel sleeps or a mode changes before drawing.
        if (decor != null) manager.removeViewImmediate(decor)
    }
    companion object {
        const val DASHBOARD_TITLE = "LIT Screen OS · cover dashboard"
        const val EDGE_TITLE = "LIT Screen OS · right edge"
        const val NAVIGATION_TITLE = "LIT Screen OS · right navigation"
    }
}
