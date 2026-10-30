package vn.lit.screenos

import android.content.Context
import android.location.LocationManager
import android.media.AudioManager
import android.provider.Settings
import android.telephony.TelephonyManager

/** Read only switch/configuration state; never infer enabled from connectivity. */
object ShortcutStates {
    data class State(val enabled: Int=-1,val detail: String?=null,val soundMode: Int=AudioManager.RINGER_MODE_NORMAL)
    fun read(context: Context,radios: RadioControls.Result): Map<String,State> {
        fun switch(read: ()->Boolean)=State(try { if(read()) 1 else 0 } catch(_: RuntimeException) { -1 })
        val sound=try { context.getSystemService(AudioManager::class.java).ringerMode } catch(_: RuntimeException) { -1 }
        val soundLabel=when(sound) {
            AudioManager.RINGER_MODE_NORMAL -> R.string.ui_shortcut_sound
            AudioManager.RINGER_MODE_VIBRATE -> R.string.ui_vibrate
            AudioManager.RINGER_MODE_SILENT -> R.string.ui_muted
            else -> R.string.ui_state_unavailable
        }
        val display=try { I18n.text(if(Settings.System.getInt(context.contentResolver,Settings.System.SCREEN_BRIGHTNESS_MODE)==Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) R.string.ui_automatic else R.string.ui_manual) } catch(_: Exception) { I18n.text(R.string.ui_state_unavailable) }
        val network=when(radios.networkMode) {
            in 23..33 -> "5G / LTE"
            8,9,10,11,12,15,17,19,20,22 -> "LTE / 3G"
            in 0..22 -> "3G / 2G"
            else -> I18n.text(R.string.ui_state_unavailable)
        }
        return mapOf(
            "wifi" to State(radios.wifi),"bluetooth" to State(radios.bluetooth),"hotspot" to State(radios.hotspot),
            "mobile" to State(try { if(context.getSystemService(TelephonyManager::class.java).isDataEnabled) 1 else 0 } catch(_: SecurityException) { -1 } catch(_: RuntimeException) { -1 }),
            "airplane" to switch { Settings.Global.getInt(context.contentResolver,Settings.Global.AIRPLANE_MODE_ON)==1 },
            "location" to switch { context.getSystemService(LocationManager::class.java).isLocationEnabled },
            "sound" to State(if(sound<0) -1 else if(sound==AudioManager.RINGER_MODE_SILENT) 0 else 1,I18n.text(soundLabel),sound),
            "display" to State(detail=display),"network" to State(detail=network),"settings" to State(detail="")
        )
    }
}
