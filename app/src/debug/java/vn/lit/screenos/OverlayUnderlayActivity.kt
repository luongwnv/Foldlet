package vn.lit.screenos

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout

/** A harmless local target for verifying overlay input routing on the emulator. */
class OverlayUnderlayActivity : Activity() {
    var taps = 0; private set
    var backPresses = 0; private set
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        body.addView(Button(this).apply {
            text = "APP BÊN DƯỚI · 0 lần chạm"
            setBackgroundColor(Color.rgb(46, 41, 80))
            setOnClickListener { taps++; text = "APP BÊN DƯỚI · $taps lần chạm" }
        },LinearLayout.LayoutParams(-1,0,1f))
        body.addView(EditText(this).apply { showSoftInputOnFocus=false; setSingleLine(); requestFocus() },LinearLayout.LayoutParams(-1,1))
        setContentView(body)
        window.insetsController?.hide(WindowInsets.Type.systemBars())
    }
    @Deprecated("Test callback")
    override fun onBackPressed() { backPresses++ }
}
