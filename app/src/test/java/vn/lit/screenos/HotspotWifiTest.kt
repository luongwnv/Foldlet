package vn.lit.screenos

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test

class HotspotWifiTest {
    @Test fun payloadEscapesSpecialCharacters() {
        assertEquals("WIFI:T:WPA;S:My\\;Net\\:5G;P:p\\\\a\\,ss\\\";;", HotspotWifi("My;Net:5G", "p\\a,ss\"").payload)
    }

    @Test fun openNetworkHasNoPassword() {
        assertEquals("WIFI:T:nopass;S:Cafe;;", HotspotWifi("Cafe", "").payload)
    }

    @Test fun qrCodeDecodesToPayload() {
        val wifi = HotspotWifi("Galaxy Z Flip3 Hồng", "a very long password with spaces 1234567890!@#")
        val matrix = wifi.matrix(240)
        val pixels = IntArray(matrix.width * matrix.height) { if (matrix[it % matrix.width, it / matrix.width]) 0xFF000000.toInt() else -1 }
        val source = RGBLuminanceSource(matrix.width, matrix.height, pixels)
        assertEquals(wifi.payload, QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text)
    }
}
