package vn.lit.screenos

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.WindowInsets
import android.widget.Button

/** A harmless local target for verifying overlay input routing on the emulator. */
class OverlayUnderlayActivity : Activity() {
    var taps = 0; private set
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(Button(this).apply {
            text = "APP BÊN DƯỚI · 0 lần chạm"
            setBackgroundColor(Color.rgb(46, 41, 80))
            setOnClickListener { taps++; text = "APP BÊN DƯỚI · $taps lần chạm" }
        })
        window.insetsController?.hide(WindowInsets.Type.systemBars())
    }
}
