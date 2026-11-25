package vn.lit.screenos

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/** Only exported system settings activities, without privileged radio toggles. */
object QuickShortcuts {
    data class Action(val id: String,val title: Int,val icon: Glyph.Kind,val intents: List<String>)
    val actions=listOf(
        Action("wifi",R.string.ui_shortcut_wifi,Glyph.Kind.WIFI,listOf(Settings.Panel.ACTION_WIFI,Settings.ACTION_WIFI_SETTINGS)),
        Action("bluetooth",R.string.ui_shortcut_bluetooth,Glyph.Kind.BLUETOOTH,listOf(Settings.ACTION_BLUETOOTH_SETTINGS)),
        Action("mobile",R.string.ui_shortcut_mobile,Glyph.Kind.CELLULAR,listOf(Settings.ACTION_DATA_USAGE_SETTINGS,Settings.Panel.ACTION_INTERNET_CONNECTIVITY,Settings.ACTION_DATA_ROAMING_SETTINGS)),
        Action("hotspot",R.string.ui_shortcut_hotspot,Glyph.Kind.HOTSPOT,listOf("android.settings.WIFI_AP_SETTINGS","android.settings.TETHER_SETTINGS")),
        Action("network",R.string.ui_shortcut_network,Glyph.Kind.NETWORK_MODE,listOf(Settings.ACTION_NETWORK_OPERATOR_SETTINGS,Settings.ACTION_DATA_ROAMING_SETTINGS,Settings.ACTION_WIRELESS_SETTINGS)),
        Action("sound",R.string.ui_shortcut_sound,Glyph.Kind.VOLUME,listOf(Settings.Panel.ACTION_VOLUME,Settings.ACTION_SOUND_SETTINGS)),
        Action("display",R.string.ui_shortcut_display,Glyph.Kind.BRIGHTNESS,listOf(Settings.ACTION_DISPLAY_SETTINGS)),
        Action("settings",R.string.ui_settings,Glyph.Kind.SETTINGS,listOf(Settings.ACTION_SETTINGS)),
        Action("airplane",R.string.ui_shortcut_airplane,Glyph.Kind.AIRPLANE,listOf(Settings.ACTION_AIRPLANE_MODE_SETTINGS)),
        Action("location",R.string.ui_shortcut_location,Glyph.Kind.LOCATION,listOf(Settings.ACTION_LOCATION_SOURCE_SETTINGS)),
        Action("clock_awake",R.string.ui_shortcut_clock_awake,Glyph.Kind.BRIGHTNESS,emptyList()),
        Action("hotspot_mode",R.string.ui_shortcut_hotspot_mode,Glyph.Kind.HOTSPOT,emptyList()),
        Action("speedtest",R.string.ui_shortcut_speedtest,Glyph.Kind.NETWORK_MODE,emptyList()),
    )
    private val toggles=mapOf("clock_awake" to CoverOverlay.KEEP_CLOCK_AWAKE,"hotspot_mode" to HotspotView.PREF)
    private val defaults="wifi|bluetooth|mobile|hotspot|network|sound|display|settings|clock_awake|hotspot_mode|speedtest"
    private val added=listOf("home_shortcuts_toggles_added" to listOf("clock_awake","hotspot_mode"),"home_shortcuts_speedtest_added" to listOf("speedtest"))
    fun isToggle(action: Action)=action.id in toggles
    fun isLocal(action: Action)=action.intents.isEmpty()
    fun toggled(context: Context,action: Action)=toggles[action.id]?.let { Platform.preferences(context).getBoolean(it,false) } ?: false
    fun toggle(context: Context,action: Action) {
        val key=toggles[action.id] ?: return
        val prefs=Platform.preferences(context)
        prefs.edit().putBoolean(key,!prefs.getBoolean(key,false)).apply()
    }
    fun intents(context: Context,action: Action): List<Intent> = action.intents.mapNotNull { name ->
        val intent=Intent(name)
        val candidates=context.packageManager.queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo }.filter { info ->
            info.exported && info.applicationInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0 &&
                (info.permission==null || context.checkSelfPermission(info.permission)==PackageManager.PERMISSION_GRANTED)
        }
        val info=candidates.firstOrNull { it.packageName=="com.android.settings" } ?: candidates.firstOrNull() ?: return@mapNotNull null
        intent.setComponent(android.content.ComponentName(info.packageName,info.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    fun available(context: Context)=actions.filter { isLocal(it) || intents(context,it).isNotEmpty() }
    fun selected(context: Context): List<Action> {
        val prefs=Platform.preferences(context)
        for((flag,ids) in added) if(!prefs.getBoolean(flag,false)) {
            val saved=prefs.getString("home_shortcuts",null)
            prefs.edit().putBoolean(flag,true).apply {
                if(saved!=null) putString("home_shortcuts",(saved.split('|')+ids).distinct().joinToString("|"))
            }.apply()
        }
        val order=(Platform.preferences(context).getString("home_shortcuts",defaults) ?: defaults).split('|')
        val available=available(context).associateBy { it.id }
        return order.distinct().mapNotNull { available[it] }
    }
    fun open(context: Context,action: Action,displayId: Int): Boolean {
        if(!Platform.unlocked(context)) return false
        val manager=context.getSystemService(ActivityManager::class.java)
        for(intent in intents(context,action)) try {
            if(manager.isActivityStartAllowedOnDisplay(context,displayId,intent)) {
                context.startActivity(intent,Platform.displayOptions(displayId));return true
            }
        } catch(_: RuntimeException) { /* Try another exported OS entry point. */ }
        return false
    }
}
