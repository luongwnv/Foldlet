package vn.lit.screenos

import android.app.*
import android.content.*
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.widget.Toast

object Platform {
    private val emulator get() = android.os.Build.MODEL.startsWith("sdk_gphone") || android.os.Build.FINGERPRINT.startsWith("generic")
    fun isEmulatorOverlay(context: Context) = BuildConfig.DEBUG && emulator && preferences(context).getBoolean("emulator_overlay", false)
    fun overlayDisplay(context: Context): Display? = if (isEmulatorOverlay(context)) context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) else cover(context)
    fun displays(context: Context): List<Display> = context.getSystemService(DisplayManager::class.java).displays.toList()

    // Public SDK has no Display.getType(). Require Samsung's built-in presentation
    // display and reject private displays such as scrcpy; do not assume ID 1.
    fun cover(context: Context): Display? {
        val manager = context.getSystemService(DisplayManager::class.java)
        // Some Samsung firmware omits the cover panel from getDisplays() even
        // though direct lookup is available. Keep this exception in the Z Flip3 profile.
        val profileDisplay = if (android.os.Build.MODEL.startsWith("SM-F711")) manager.getDisplay(1) else null
        return (displays(context) + listOfNotNull(profileDisplay)).distinctBy { it.displayId }.filter {
        it.displayId != Display.DEFAULT_DISPLAY && it.isValid &&
            it.flags and Display.FLAG_PRESENTATION != 0 && it.flags and Display.FLAG_PRIVATE == 0 &&
            it.name == "Built-in Screen"
    }.minByOrNull {
        val size = android.graphics.Point()
        @Suppress("DEPRECATION")
        it.getRealSize(size)
        size.x.toLong() * size.y
        }
    }

    fun stateName(state: Int): String = when (state) {
        Display.STATE_ON -> "ON"
        Display.STATE_OFF -> "OFF"
        Display.STATE_DOZE -> "DOZE"
        Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
        else -> "state=$state"
    }

    fun preferences(context: Context) = context.getSharedPreferences("screenos", Context.MODE_PRIVATE)
    fun removeLegacyWidgetHost(context: Context) {
        val prefs = preferences(context)
        if (prefs.getBoolean("widget_host_removed", false)) return
        try {
            android.appwidget.AppWidgetHost(context, 1701).deleteHost()
            prefs.edit().remove("widgets").putBoolean("widget_host_removed", true).apply()
        } catch (_: RuntimeException) { /* Retry cleanup next start if the system service is unavailable. */ }
    }
    fun samsungHome(context: Context, displayId: Int): Boolean {
        if (!android.os.Build.MODEL.startsWith("SM-F711") || displayId != 1) return false
        return try {
            // Exported secondary-home activity verified in this Flip3's SystemUI manifest.
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_SECONDARY_HOME)
                .setComponent(ComponentName("com.android.systemui", "com.android.systemui.subscreen.SubHomeActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent, displayOptions(displayId))
            true
        } catch (_: RuntimeException) { message(context, I18n.text(R.string.ui_could_not_open_samsung_home)); false }
    }
    fun unlocked(context: Context): Boolean = !context.getSystemService(KeyguardManager::class.java).isDeviceLocked
    fun coverHasFocus(service: NavigationService, displayId: Int): Boolean = try {
        service.windowsOnAllDisplays[displayId]?.any { it.isFocused } == true
    } catch (_: RuntimeException) { false }

    fun focusedCoverPackage(service: NavigationService, displayId: Int): String? = try {
        val window = service.windowsOnAllDisplays[displayId]?.firstOrNull { it.isFocused }
        val root = window?.root
        try { root?.packageName?.toString() }
        finally { @Suppress("DEPRECATION") root?.recycle() }
    } catch (_: RuntimeException) { null }

    fun message(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    fun messageOnDisplay(context: Context, displayId: Int, message: String) {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(displayId)
        message(if (display != null) context.createDisplayContext(display) else context, message)
    }

    fun launch(context: Context, intent: Intent, displayId: Int): Boolean {
        if (!unlocked(context)) {
            message(context, I18n.text(R.string.ui_unlock_your_phone_to_continue))
            return false
        }
        return try {
            val manager = context.getSystemService(ActivityManager::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!manager.isActivityStartAllowedOnDisplay(context, displayId, intent)) {
                message(context, I18n.text(R.string.ui_the_system_blocked_this_app_on_the))
                false
            } else {
                val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }
                context.startActivity(intent, options.toBundle())
                true
            }
        } catch (e: RuntimeException) {
            message(context, I18n.text(R.string.ui_could_not_open, e.javaClass.simpleName))
            false
        }
    }

    fun displayOptions(displayId: Int): Bundle = ActivityOptions.makeBasic().apply {
        launchDisplayId = displayId
    }.toBundle()
}
