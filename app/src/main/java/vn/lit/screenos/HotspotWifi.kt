package vn.lit.screenos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** User-entered hotspot credentials; Android does not expose them to ordinary apps. */
data class HotspotWifi(val ssid: String, val password: String) {
    val configured get() = ssid.isNotBlank()

    val payload: String get() {
        val security = if (password.isEmpty()) "nopass" else "WPA"
        val secret = if (password.isEmpty()) "" else "P:${escape(password)};"
        return "WIFI:T:$security;S:${escape(ssid)};$secret;"
    }

    fun matrix(size: Int): BitMatrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8"))

    fun bitmap(size: Int): Bitmap {
        val matrix = matrix(size)
        val pixels = IntArray(matrix.width * matrix.height) { if (matrix[it % matrix.width, it / matrix.width]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.RGB_565)
    }

    fun save(context: Context) {
        Platform.preferences(context).edit().putString(SSID, ssid.trim()).putString(PASSWORD, password).apply()
    }

    companion object {
        private const val SSID = "hotspot_ssid"
        private const val PASSWORD = "hotspot_password"

        fun read(context: Context) = Platform.preferences(context).let { HotspotWifi(it.getString(SSID, "").orEmpty(), it.getString(PASSWORD, "").orEmpty()) }

        fun escape(value: String) = value.replace(Regex("""([\\;,:"])"""), """\\$1""")
    }
}
