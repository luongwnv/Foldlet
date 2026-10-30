package vn.lit.screenos.controls

import android.app.KeyguardManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.*

/** Signature-only, bound helper. No network, location, scanning, or device lists. */
@Suppress("DEPRECATION")
class RadioService: Service() {
    private val messenger=Messenger(object: Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            val packages=packageManager.getPackagesForUid(message.sendingUid).orEmpty()
            if("vn.lit.screenos" !in packages || packageManager.checkSignatures(message.sendingUid,applicationInfo.uid)!=PackageManager.SIGNATURE_MATCH) return
            if(message.what !in 1..2) return
            val wifi=applicationContext.getSystemService(WifiManager::class.java)
            val bluetooth=getSystemService(BluetoothManager::class.java)?.adapter
            var accepted=false
            if(message.what==2 && !getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                try {
                    accepted=when(message.data.getString("kind")) {
                        "wifi" -> when(wifi?.wifiState) {
                            WifiManager.WIFI_STATE_ENABLED -> wifi.setWifiEnabled(false)
                            WifiManager.WIFI_STATE_DISABLED -> wifi.setWifiEnabled(true)
                            else -> false
                        }
                        "bluetooth" -> when(bluetooth?.state) {
                            BluetoothAdapter.STATE_ON -> bluetooth.disable()
                            BluetoothAdapter.STATE_OFF -> bluetooth.enable()
                            else -> false
                        }
                        else -> false
                    }
                } catch(_: SecurityException) { /* Firmware can reject a legacy toggle. */ }
            }
            val state=Bundle().apply {
                putBoolean("accepted",accepted)
                putInt("wifi",try { when(wifi?.wifiState) {
                    WifiManager.WIFI_STATE_ENABLED -> 1
                    WifiManager.WIFI_STATE_DISABLED -> 0
                    WifiManager.WIFI_STATE_ENABLING,WifiManager.WIFI_STATE_DISABLING -> 2
                    else -> -1
                } } catch(_: SecurityException) { -1 })
                putInt("bluetooth",try { when(bluetooth?.state) {
                    BluetoothAdapter.STATE_ON -> 1
                    BluetoothAdapter.STATE_OFF -> 0
                    BluetoothAdapter.STATE_TURNING_ON,BluetoothAdapter.STATE_TURNING_OFF -> 2
                    else -> -1
                } } catch(_: SecurityException) { -1 })
            }
            try { message.replyTo?.send(Message.obtain(null,message.what).apply { data=state }) } catch(_: RemoteException) { }
        }
    })
    override fun onBind(intent: Intent)=messenger.binder
}
