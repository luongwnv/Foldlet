package vn.lit.screenos.controls

import android.app.KeyguardManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import android.telephony.SubscriptionManager

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
                putInt("hotspot",when(readHotspotState(wifi)) {
                    13 -> 1
                    11 -> 0
                    10,12 -> 2
                    else -> -1
                })
                putInt("networkMode",readNetworkMode())
            }
            try { message.replyTo?.send(Message.obtain(null,message.what).apply { data=state }) } catch(_: RemoteException) { }
        }
    })
    private fun readHotspotState(wifi: WifiManager?): Int {
        try { return WifiManager::class.java.getMethod("getWifiApState").invoke(wifi) as? Int ?: -1 } catch(_: Exception) { }
        return try {
            registerReceiver(null,IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED"))?.getIntExtra("wifi_state",-1) ?: -1
        } catch(_: RuntimeException) { -1 }
    }
    private fun readNetworkMode(): Int = try {
        val subscription=SubscriptionManager.getDefaultDataSubscriptionId()
        val perSubscription=if(subscription>=0) Settings.Global.getString(contentResolver,"preferred_network_mode$subscription")?.toIntOrNull() else null
        if(perSubscription!=null) perSubscription
        else {
            val modes=Settings.Global.getString(contentResolver,"preferred_network_mode").orEmpty().split(',').map { it.trim().toIntOrNull() }
            val slot=if(Build.VERSION.SDK_INT>=29 && subscription>=0) SubscriptionManager.getSlotIndex(subscription) else -1
            if(slot in modes.indices) modes[slot] ?: -1
            else if(modes.distinct().size==1) modes.firstOrNull() ?: -1 else -1
        }
    } catch(_: Exception) { -1 }
    override fun onBind(intent: Intent)=messenger.binder
}
