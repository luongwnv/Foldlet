package vn.lit.screenos

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.media.AudioManager

/** Use the installed One UI artwork; Samsung assets are not bundled in Foldlet. */
class QuickSettingsIcons(context: Context) {
    private val source=runCatching { context.createPackageContext("com.android.systemui",0) }.getOrNull()
    private val cache=mutableMapOf<String,Drawable.ConstantState?>()
    fun get(action: QuickShortcuts.Action,state: ShortcutStates.State,size: Int): Drawable {
        val name=when(action.id) {
            "wifi" -> "quick_panel_icon_wifi_full"
            "bluetooth" -> "quick_panel_icon_bluetooth"
            "mobile" -> "quick_panel_icon_data_connection"
            "hotspot" -> "quick_panel_icon_wifihotspot"
            "network" -> when {
                state.detail?.startsWith("5G")==true -> "ic_5g_mobiledata"
                state.detail?.startsWith("LTE")==true -> "ic_4g_mobiledata"
                state.detail?.startsWith("3G")==true -> "ic_3g_mobiledata"
                else -> ""
            }
            "sound" -> when(state.soundMode) {
                AudioManager.RINGER_MODE_VIBRATE -> "subroom_quick_panel_icon_sound_vibrate"
                AudioManager.RINGER_MODE_SILENT -> "subroom_quick_panel_icon_sound_mute"
                else -> "subroom_quick_panel_icon_sound"
            }
            "display" -> "subroom_quick_panel_icon_brightness"
            "settings" -> "ic_settings"
            "airplane" -> "quick_panel_icon_airplane_mode"
            "location" -> if(state.enabled==0) "quick_panel_icon_location_off" else "quick_panel_icon_location_on"
            else -> ""
        }
        val native=source
        if(native!=null && name.isNotEmpty()) {
            if(!cache.containsKey(name)) cache[name]=runCatching {
                val id=native.resources.getIdentifier(name,"drawable","com.android.systemui")
                if(id==0) null else native.resources.getDrawable(id,native.theme).constantState
            }.getOrNull()
            cache[name]?.let { return it.newDrawable(native.resources).mutate().apply { setTint(Color.WHITE) } }
        }
        val fallback=if(action.id=="sound") when(state.soundMode) {
            AudioManager.RINGER_MODE_VIBRATE -> Glyph.Kind.VIBRATE
            AudioManager.RINGER_MODE_SILENT -> Glyph.Kind.MUTE
            else -> action.icon
        } else action.icon
        return Glyph(fallback,false,size)
    }
}
