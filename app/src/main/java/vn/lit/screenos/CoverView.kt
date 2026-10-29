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
            content.addView(Ui.heading(context, I18n.text(R.string.ui_unlock_to_continue), 20f))
            content.addView(Ui.text(context, I18n.text(R.string.ui_use_your_fingerprint_or_unlock_your_phone), 13f, Ui.muted))
            val activity = context as? Activity
            if (activity != null) content.addView(Ui.button(context, I18n.text(R.string.ui_unlock)) {
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
        content.addView(Ui.button(context, I18n.text(R.string.ui_refresh_notifications)) { render() })
        val listener = CoverNotifications.current
        if (listener == null) { content.addView(Ui.text(context, I18n.text(R.string.ui_allow_notification_access_on_the_main_screen), 13f, Ui.muted)); return }
        val entries = listener.snapshot()
        if (entries.isEmpty()) content.addView(Ui.text(context, I18n.text(R.string.ui_no_notifications), 14f, Ui.muted))
        entries.forEach { entry ->
            val extras = entry.notification.extras
            val label = try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(entry.packageName, 0)) } catch (_: Exception) { entry.packageName }
            content.addView(Ui.heading(context, label.toString(), 14f))
            content.addView(Ui.text(context, extras.getCharSequence(Notification.EXTRA_TITLE) ?: "", 15f))
            content.addView(Ui.text(context, extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT) ?: "", 13f, Ui.muted))
            entry.notification.contentIntent?.let { pending -> content.addView(Ui.button(context, I18n.text(R.string.ui_open_notification)) { send(pending, closeAfter = true) }) }
            entry.notification.actions?.forEach { action ->
                content.addView(Ui.button(context, action.title.toString()) {
                    if (!Platform.unlocked(context)) return@button
                    val input = action.remoteInputs?.firstOrNull { it.allowFreeFormInput }
                    if (input == null) send(action.actionIntent)
                    else reply(action, input)
                })
            }
            if (entry.isClearable) content.addView(Ui.button(context, I18n.text(R.string.ui_delete)) {
                if (Platform.unlocked(context)) { listener.cancelNotification(entry.key); handler.postDelayed({ render() }, 200) }
            })
        }
    }

    private fun send(pending: PendingIntent, closeAfter: Boolean = false) {
        if (!Platform.unlocked(context)) { Platform.message(context, I18n.text(R.string.ui_unlock_to_continue)); return }
        try {
            pending.send(context, 0, null, null, null, null, Platform.displayOptions(displayId))
            if (closeAfter || (Build.VERSION.SDK_INT >= 31 && pending.isActivity)) close()
        } catch (_: PendingIntent.CanceledException) { Platform.message(context, I18n.text(R.string.ui_this_notification_has_expired)) }
        catch (_: RuntimeException) { Platform.message(context, I18n.text(R.string.ui_the_system_blocked_this_action)) }
    }
    private fun reply(action: Notification.Action, input: RemoteInput) {
        val field = EditText(context).apply { hint = I18n.text(R.string.ui_type_a_reply); minLines = 1 }
        val content = Ui.column(context, 8)
        content.addView(Ui.heading(context, I18n.text(R.string.ui_reply), 18f)); content.addView(field)
        content.addView(Ui.button(context, I18n.text(R.string.ui_send)) {
            if (!Platform.unlocked(context)) { render(); return@button }
            if (field.text.isNullOrBlank()) return@button
            val intent = Intent()
            val results = Bundle().apply { putCharSequence(input.resultKey, field.text.toString()) }
            RemoteInput.addResultsToIntent(arrayOf(input), intent, results)
            RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
            try { action.actionIntent.send(context, 0, intent); Platform.message(context, I18n.text(R.string.ui_reply_sent_to_the_app)) }
            catch (_: PendingIntent.CanceledException) { Platform.message(context, I18n.text(R.string.ui_could_not_send_the_notification_expired)) }
            render()
        })
        content.addView(Ui.button(context, I18n.text(R.string.ui_cancel)) { render() })
        clearModal = { field.text.clear() }
        body.removeAllViews(); body.addView(Ui.scroll(context, content))
        field.requestFocus()
    }

    private fun media(content: LinearLayout) {
        content.addView(Ui.button(context, I18n.text(R.string.ui_refresh_media)) { render() })
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val sessions = try { manager.getActiveSessions(ComponentName(context, CoverNotifications::class.java)) } catch (_: SecurityException) { emptyList() }
        if (sessions.isEmpty()) { content.addView(Ui.text(context, I18n.text(R.string.ui_play_media_in_an_app_and_allow), 13f, Ui.muted)); return }
        sessions.forEach { session ->
            content.addView(Ui.heading(context, session.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: session.packageName, 18f))
            content.addView(Ui.text(context, session.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "", 13f, Ui.muted))
            val state = session.playbackState
            val actions = state?.actions ?: 0L
            val row = LinearLayout(context)
            fun control(label: String, supported: Boolean, action: () -> Unit) {
                row.addView(Ui.button(context, label) { if (Platform.unlocked(context)) action() }.apply { isEnabled = supported }, LayoutParams(0, Ui.dp(context, 44), 1f))
            }
            control(I18n.text(R.string.ui_previous), actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) { session.transportControls.skipToPrevious() }
            val playing = state?.state == PlaybackState.STATE_PLAYING
            control(if (playing) I18n.text(R.string.ui_pause) else I18n.text(R.string.ui_play), actions and (if (playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY) != 0L || actions and PlaybackState.ACTION_PLAY_PAUSE != 0L) {
                if (playing) session.transportControls.pause() else session.transportControls.play()
                handler.postDelayed({ render() }, 300)
            }
            control(I18n.text(R.string.ui_next), actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) { session.transportControls.skipToNext() }
            content.addView(row)
        }
    }

    private fun toggles(content: LinearLayout) {
        val awake = hostWindow.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        content.addView(Ui.button(context, if (awake) I18n.text(R.string.ui_keep_awake_tap_to_turn_off) else I18n.text(R.string.ui_keep_screen_awake)) {
            if (awake) hostWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else hostWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            render()
        })
        content.addView(Ui.text(context, I18n.text(R.string.ui_window_brightness), 13f, Ui.muted))
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
        content.addView(Ui.text(context, I18n.text(R.string.ui_media_volume), 13f, Ui.muted))
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
        content.addView(Ui.button(context, I18n.text(R.string.ui_wi_fi_settings)) { if (Platform.launch(context, Intent(Settings.Panel.ACTION_WIFI), displayId)) close() })
        content.addView(Ui.button(context, I18n.text(R.string.ui_bluetooth_settings)) { if (Platform.launch(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), displayId)) close() })
        content.addView(Ui.button(context, I18n.text(R.string.ui_restore_hidden_apps)) { prefs.edit().remove("hidden").apply(); Platform.message(context, I18n.text(R.string.ui_hidden_apps_restored)) })
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
