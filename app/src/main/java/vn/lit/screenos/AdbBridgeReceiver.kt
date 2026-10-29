package vn.lit.screenos

import android.content.*
import java.security.MessageDigest

/** Signature-protected provisioning: only ADB/system can set the session secret. */
class AdbBridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CoverRotation.initialize(context)
        val prefs = Platform.preferences(context)
        val token = intent.getStringExtra("token") ?: return
        when (intent.action) {
            "vn.lit.screenos.SET_ADB_BRIDGE" -> {
                val port = intent.getIntExtra("port",0)
                if (port !in 1024..65535 || !token.matches(Regex("[A-Za-z0-9_-]{32,128}"))) return
                prefs.edit().putInt("adb_bridge_port",port).putString("adb_bridge_token",token).apply()
                if (BuildConfig.DEBUG && android.os.Build.MODEL.startsWith("sdk_gphone") && intent.getBooleanExtra("emulator",false)) {
                    prefs.edit().putBoolean("emulator_overlay",true).apply()
                }
                CoverRotation.invalidate()
            }
            "vn.lit.screenos.CLEAR_ADB_BRIDGE" -> {
                val current = prefs.getString("adb_bridge_token", "")!!
                if (MessageDigest.isEqual(token.toByteArray(),current.toByteArray())) {
                    prefs.edit().remove("adb_bridge_port").remove("adb_bridge_token").apply()
                    CoverRotation.invalidate()
                }
            }
        }
    }
}
