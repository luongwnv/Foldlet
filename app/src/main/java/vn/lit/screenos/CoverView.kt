package vn.lit.screenos

import android.app.*
import android.content.*
import android.media.*
import android.media.session.*
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*

@android.annotation.SuppressLint("ViewConstructor") // Constructed in code with an explicit target window.
class CoverView(
    context: Context,
    private val hostWindow: Window,
    private val displayId: Int,
    private val close: () -> Unit,
    private val samsungHome: () -> Unit = close,
) : LinearLayout(context) {
    private enum class Page { APPS, NOTIFICATIONS, MEDIA, TOGGLES }
    private var page = Page.APPS
    fun openSection(section: String) {
        page = when (section.lowercase()) { "apps", "app" -> Page.APPS; "notifications", "tin" -> Page.NOTIFICATIONS; "media", "nhạc" -> Page.MEDIA; "toggles", "nhanh" -> Page.TOGGLES; else -> Page.APPS }
        render()
    }
    private val prefs = Platform.preferences(context)
    private val body = FrameLayout(context)
    private val handler = Handler(Looper.getMainLooper())
    private var wasUnlocked = false
    private var clearModal: (() -> Unit)? = null
    private val privacyTick = object : Runnable {
        override fun run() {
            val unlocked = Platform.unlocked(context)
            if (unlocked != wasUnlocked) {
                wasUnlocked = unlocked
                render()
            }
            handler.postDelayed(this, 500)
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Ui.background)
        addView(body, LayoutParams(-1, 0, 1f))
        wasUnlocked = Platform.unlocked(context)
        render()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(privacyTick)
    }
    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        clearModal?.invoke(); clearModal = null
        super.onDetachedFromWindow()
    }

    private fun render() {
        clearModal?.invoke(); clearModal = null
        body.removeAllViews()
        val content = Ui.column(context, 8)
        body.addView(Ui.scroll(context, content))
        if (!Platform.unlocked(context)) {
            content.addView(Ui.heading(context, "Mở khóa để tiếp tục", 20f))
            content.addView(Ui.text(context, "Dùng vân tay hoặc mở khóa điện thoại.", 13f, Ui.muted))
            val activity = context as? Activity
            if (activity != null) content.addView(Ui.button(context, "Mở khóa") {
                activity.getSystemService(KeyguardManager::class.java).requestDismissKeyguard(activity, null)
            })
            return
        }
        if (page == Page.APPS) {
            body.removeAllViews()
            body.addView(AppDrawerView(context, displayId, close))
            return
        }
        when (page) {
            Page.APPS -> Unit
            Page.NOTIFICATIONS -> notifications(content)
            Page.MEDIA -> media(content)
            Page.TOGGLES -> toggles(content)
        }
    }

    private fun notifications(content: LinearLayout) {
        content.addView(Ui.button(context, "Làm mới thông báo") { render() })
        val listener = CoverNotifications.current
        if (listener == null) { content.addView(Ui.text(context, "Cấp quyền truy cập thông báo trên màn hình chính.", 13f, Ui.muted)); return }
        val entries = listener.snapshot()
        if (entries.isEmpty()) content.addView(Ui.text(context, "Không có thông báo", 14f, Ui.muted))
        entries.forEach { entry ->
            val extras = entry.notification.extras
            val label = try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(entry.packageName, 0)) } catch (_: Exception) { entry.packageName }
            content.addView(Ui.heading(context, label.toString(), 14f))
            content.addView(Ui.text(context, extras.getCharSequence(Notification.EXTRA_TITLE) ?: "", 15f))
            content.addView(Ui.text(context, extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT) ?: "", 13f, Ui.muted))
            entry.notification.contentIntent?.let { pending -> content.addView(Ui.button(context, "Mở thông báo") { send(pending, closeAfter = true) }) }
            entry.notification.actions?.forEach { action ->
                content.addView(Ui.button(context, action.title.toString()) {
                    if (!Platform.unlocked(context)) return@button
                    val input = action.remoteInputs?.firstOrNull { it.allowFreeFormInput }
                    if (input == null) send(action.actionIntent)
                    else reply(action, input)
                })
            }
            if (entry.isClearable) content.addView(Ui.button(context, "Xóa") {
                if (Platform.unlocked(context)) { listener.cancelNotification(entry.key); handler.postDelayed({ render() }, 200) }
            })
        }
    }

    private fun send(pending: PendingIntent, closeAfter: Boolean = false) {
        if (!Platform.unlocked(context)) { Platform.message(context, "Mở khóa để tiếp tục"); return }
        try {
            pending.send(context, 0, null, null, null, null, Platform.displayOptions(displayId))
            if (closeAfter || (Build.VERSION.SDK_INT >= 31 && pending.isActivity)) close()
        } catch (_: PendingIntent.CanceledException) { Platform.message(context, "Thông báo đã hết hiệu lực") }
        catch (_: RuntimeException) { Platform.message(context, "Hệ thống không cho phép thao tác này") }
    }
    private fun reply(action: Notification.Action, input: RemoteInput) {
        val field = EditText(context).apply { hint = "Nhập trả lời"; minLines = 1 }
        val content = Ui.column(context, 8)
        content.addView(Ui.heading(context, "Trả lời", 18f)); content.addView(field)
        content.addView(Ui.button(context, "Gửi") {
            if (!Platform.unlocked(context)) { render(); return@button }
            if (field.text.isNullOrBlank()) return@button
            val intent = Intent()
            val results = Bundle().apply { putCharSequence(input.resultKey, field.text.toString()) }
            RemoteInput.addResultsToIntent(arrayOf(input), intent, results)
            RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
            try { action.actionIntent.send(context, 0, intent); Platform.message(context, "Đã gửi phản hồi đến ứng dụng") }
            catch (_: PendingIntent.CanceledException) { Platform.message(context, "Không gửi được: thông báo đã hết hiệu lực") }
            render()
        })
        content.addView(Ui.button(context, "Hủy") { render() })
        clearModal = { field.text.clear() }
        body.removeAllViews(); body.addView(Ui.scroll(context, content))
        field.requestFocus()
    }

    private fun media(content: LinearLayout) {
        content.addView(Ui.button(context, "Làm mới phiên nhạc") { render() })
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val sessions = try { manager.getActiveSessions(ComponentName(context, CoverNotifications::class.java)) } catch (_: SecurityException) { emptyList() }
        if (sessions.isEmpty()) { content.addView(Ui.text(context, "Phát nhạc trong một app và cấp quyền truy cập thông báo.", 13f, Ui.muted)); return }
        sessions.forEach { session ->
            content.addView(Ui.heading(context, session.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: session.packageName, 18f))
            content.addView(Ui.text(context, session.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "", 13f, Ui.muted))
            val state = session.playbackState
            val actions = state?.actions ?: 0L
            val row = LinearLayout(context)
            fun control(label: String, supported: Boolean, action: () -> Unit) {
                row.addView(Ui.button(context, label) { if (Platform.unlocked(context)) action() }.apply { isEnabled = supported }, LayoutParams(0, Ui.dp(context, 44), 1f))
            }
            control("Trước", actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) { session.transportControls.skipToPrevious() }
            val playing = state?.state == PlaybackState.STATE_PLAYING
            control(if (playing) "Dừng" else "Phát", actions and (if (playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY) != 0L || actions and PlaybackState.ACTION_PLAY_PAUSE != 0L) {
                if (playing) session.transportControls.pause() else session.transportControls.play()
                handler.postDelayed({ render() }, 300)
            }
            control("Sau", actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) { session.transportControls.skipToNext() }
            content.addView(row)
        }
    }

    private fun toggles(content: LinearLayout) {
        val awake = hostWindow.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        content.addView(Ui.button(context, if (awake) "✓ Giữ sáng · nhấn để tắt" else "Giữ màn hình sáng") {
            if (awake) hostWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else hostWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            render()
        })
        content.addView(Ui.text(context, "Độ sáng cửa sổ", 13f, Ui.muted))
        content.addView(SeekBar(context).apply {
            max = 100; progress = ((hostWindow.attributes.screenBrightness.takeIf { it >= 0 } ?: .5f) * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser && Platform.unlocked(context)) hostWindow.attributes = hostWindow.attributes.apply { screenBrightness = value.coerceAtLeast(5) / 100f }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        })
        val audio = context.getSystemService(AudioManager::class.java)
        content.addView(Ui.text(context, "Âm lượng media", 13f, Ui.muted))
        content.addView(SeekBar(context).apply {
            max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC); progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser && Platform.unlocked(context)) audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        })
        content.addView(Ui.button(context, "Cài đặt Wi-Fi") { if (Platform.launch(context, Intent(Settings.Panel.ACTION_WIFI), displayId)) close() })
        content.addView(Ui.button(context, "Cài đặt Bluetooth") { if (Platform.launch(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), displayId)) close() })
        content.addView(Ui.button(context, "Hiện lại ứng dụng đã ẩn") { prefs.edit().remove("hidden").apply(); Platform.message(context, "Đã hiện lại ứng dụng") })
    }
    private var downX = 0f
    private var downY = 0f
    private var edgeDirection = 0
    private var edgeConsumed = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = event.x; downY = event.y; edgeConsumed = false
            val edge = Ui.dp(context, 24)
            edgeDirection = when {
                page != Page.APPS -> 0
                downX <= edge -> 1
                downX >= width - edge -> -1
                else -> 0
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) edgeDirection = 0
        if (edgeConsumed) return true
        if (edgeDirection != 0 && (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_UP)) {
            val dx = event.x - downX; val dy = event.y - downY
            val horizontal = kotlin.math.abs(dx)
            val vertical = kotlin.math.abs(dy)
            if (vertical > touchSlop && vertical >= horizontal) {
                // Once a vertical scroll starts, it cannot become a home swipe.
                edgeDirection = 0
            } else if (dx * edgeDirection >= Ui.dp(context, 48) && horizontal > vertical * 1.4f) {
                edgeConsumed = true; edgeDirection = 0
                // Cancel the pressed icon/search key before closing its window.
                // It must not receive UP and launch an app during the home swipe.
                MotionEvent.obtain(event).let { cancel ->
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                }
                samsungHome()
                return true
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.actionMasked == MotionEvent.ACTION_UP) edgeDirection = 0
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val dx = event.x - downX; val dy = event.y - downY
            val threshold = Ui.dp(context, 45)
            if (page != Page.APPS && downX < Ui.dp(context, 20) && dx > threshold && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                page = Page.APPS; render(); return true
            }
        }
        val handled = super.dispatchTouchEvent(event)
        // Keep receiving edge moves even over an empty favorites/search page,
        // where no child view would otherwise accept the initial DOWN.
        return handled || edgeDirection != 0
    }
}
