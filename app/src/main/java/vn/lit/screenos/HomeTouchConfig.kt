package vn.lit.screenos

import android.content.Context
import android.graphics.Rect

/** Rectangle percentages use the available travel range: 0=start, 100=end. */
data class HomeTouchConfig(
    val enabled: Boolean=true,
    val widthPercent: Int=10,
    val heightPercent: Int=60,
    val horizontal: Int=100,
    val vertical: Int=50,
    val indicator: Boolean=true,
) {
    fun bounds(width: Int,height: Int): Rect {
        val w=(width*widthPercent/100f).toInt().coerceIn(1,width)
        val h=(height*heightPercent/100f).toInt().coerceIn(1,height)
        val x=(width-w)*horizontal/100
        val y=(height-h)*vertical/100
        return Rect(x,y,x+w,y+h)
    }
    fun save(context: Context) { Platform.preferences(context).edit()
        .putBoolean("home_tap_enabled",enabled).putInt("home_tap_width",widthPercent)
        .putInt("home_tap_height",heightPercent).putInt("home_tap_horizontal",horizontal)
        .putInt("home_tap_vertical",vertical).putBoolean("home_tap_indicator",indicator).apply() }
    companion object {
        fun read(context: Context): HomeTouchConfig {
            val p=Platform.preferences(context)
            return HomeTouchConfig(p.getBoolean("home_tap_enabled",true),p.getInt("home_tap_width",10).coerceIn(5,60),p.getInt("home_tap_height",60).coerceIn(10,100),p.getInt("home_tap_horizontal",100).coerceIn(0,100),p.getInt("home_tap_vertical",50).coerceIn(0,100),p.getBoolean("home_tap_indicator",true))
        }
    }
}
