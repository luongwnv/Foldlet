package vn.lit.screenos

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class NavigationService : AccessibilityService() {
    var inputGeneration=0L; private set
    private var selectionStart=-1
    private var selectionEnd=-1
    private var selectionRevision=0L

    @android.annotation.TargetApi(33)
    override fun onCreateInputMethod(): android.accessibilityservice.InputMethod = object: android.accessibilityservice.InputMethod(this) {
        override fun onStartInput(attribute: android.view.inputmethod.EditorInfo,restarting: Boolean) {
            super.onStartInput(attribute,restarting)
            inputGeneration++;selectionRevision++;selectionStart=attribute.initialSelStart;selectionEnd=attribute.initialSelEnd
            CoverMonitorService.current?.schedule()
        }
        override fun onFinishInput() {
            super.onFinishInput();inputGeneration++;selectionRevision++;selectionStart=-1;selectionEnd=-1
            CoverMonitorService.current?.schedule()
        }
        override fun onUpdateSelection(oldSelStart: Int,oldSelEnd: Int,newSelStart: Int,newSelEnd: Int,candidatesStart: Int,candidatesEnd: Int) {
            super.onUpdateSelection(oldSelStart,oldSelEnd,newSelStart,newSelEnd,candidatesStart,candidatesEnd)
            selectionRevision++;selectionStart=newSelStart;selectionEnd=newSelEnd
        }
    }
    fun hasCoverEditor(displayId: Int): Boolean {
        if(Build.VERSION.SDK_INT<33 || !Platform.unlocked(this)) return false
        val display=Platform.overlayDisplay(this) ?: return false
        if(display.displayId!=displayId || display.state!=android.view.Display.STATE_ON) return false
        val ime=inputMethod ?: return false
        val editor=ime.currentInputEditorInfo ?: return false
        return ime.currentInputStarted && ime.currentInputConnection!=null && editor.inputType!=android.text.InputType.TYPE_NULL &&
            editor.packageName !in setOf(packageName,"com.android.systemui","com.samsung.android.app.aodservice") && editor.packageName==Platform.focusedCoverPackage(this,displayId)
    }
    fun coverInputTarget(displayId: Int): CoverInputTarget? {
        if(Build.VERSION.SDK_INT<33 || !hasCoverEditor(displayId)) return null
        val generation=inputGeneration
        val info=inputMethod!!.currentInputEditorInfo!!
        val editorPackage=info.packageName
        val variation=info.inputType and android.text.InputType.TYPE_MASK_VARIATION
        val inputClass=info.inputType and android.text.InputType.TYPE_MASK_CLASS
        return object: CoverInputTarget {
            override val numeric=inputClass==android.text.InputType.TYPE_CLASS_NUMBER || inputClass==android.text.InputType.TYPE_CLASS_PHONE || inputClass==android.text.InputType.TYPE_CLASS_DATETIME
            override val privateInput=if(inputClass==android.text.InputType.TYPE_CLASS_NUMBER) variation==android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD else variation in setOf(android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
            private val space=if(privateInput || variation in setOf(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,android.text.InputType.TYPE_TEXT_VARIATION_URI)) "" else " "
            private var wordEnd=-1
            private var wordLength=0
            private var wordRevision=-1L
            private fun connection(): android.accessibilityservice.InputMethod.AccessibilityInputConnection? {
                if(inputGeneration!=generation || !hasCoverEditor(displayId) || inputMethod?.currentInputEditorInfo?.packageName!=editorPackage) return null
                return inputMethod?.currentInputConnection
            }
            override fun text(value: String): Boolean {
                val connection=connection() ?: return false
                wordEnd=-1;connection.commitText(value,1,null);return true
            }
            override fun backspace(): Boolean {
                val connection=connection() ?: return false
                wordEnd=-1
                val now=SystemClock.uptimeMillis()
                connection.sendKeyEvent(KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL,0))
                connection.sendKeyEvent(KeyEvent(now,now+20,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DEL,0))
                return true
            }
            override fun word(value: String): Boolean {
                val connection=connection() ?: return false
                val start=minOf(selectionStart,selectionEnd)
                val text=value+space
                wordRevision=selectionRevision
                connection.commitText(text,1,null)
                wordLength=text.length;wordEnd=if(start>=0) start+text.length else -1
                return true
            }
            override fun replaceWord(previous: String,replacement: String): Boolean {
                val connection=connection() ?: return false
                if(wordEnd<0 || selectionRevision !in wordRevision..wordRevision+1 || selectionStart!=wordEnd || selectionEnd!=wordEnd || wordLength!=previous.length+space.length) return false
                wordRevision=selectionRevision
                connection.deleteSurroundingText(wordLength,0)
                val text=replacement+space
                connection.commitText(text,1,null)
                wordEnd=wordEnd-wordLength+text.length;wordLength=text.length
                return true
            }
            override fun action(): Boolean {
                val connection=connection() ?: return false
                val action=info.imeOptions and android.view.inputmethod.EditorInfo.IME_MASK_ACTION
                if(action!=android.view.inputmethod.EditorInfo.IME_ACTION_NONE && action!=android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED) connection.performEditorAction(action)
                else {
                    val now=SystemClock.uptimeMillis()
                    connection.sendKeyEvent(KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER,0))
                    connection.sendKeyEvent(KeyEvent(now,now+20,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER,0))
                }
                wordEnd=-1;return true
            }
        }
    }
    enum class BackResult { SENT, LAUNCHER_ROOT, UNAVAILABLE }
    var backRoute = "none"; private set
    fun sendCoverBack(displayId: Int): BackResult {
        val focusedPackage = Platform.focusedCoverPackage(this, displayId)
        if (Build.VERSION.SDK_INT >= 33 && focusedPackage != null) {
            val ime = inputMethod
            val connection = ime?.currentInputConnection
            if (connection != null && ime.currentInputStarted && ime.currentInputEditorInfo?.packageName == focusedPackage) {
                val now = SystemClock.uptimeMillis()
                connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0, 0, -1, 0, KeyEvent.FLAG_SOFT_KEYBOARD, InputDevice.SOURCE_KEYBOARD))
                connection.sendKeyEvent(KeyEvent(now, now + 30, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0, 0, -1, 0, KeyEvent.FLAG_SOFT_KEYBOARD, InputDevice.SOURCE_KEYBOARD))
                backRoute = "accessibility-input"
                return BackResult.SENT
            }
        }
        if (clickAppBack(displayId)) { backRoute="app-navigation"; return BackResult.SENT }
        if (focusedPackage != null && RecentApps.isLauncherRoot(this,focusedPackage)) {
            backRoute="launcher-root"; return BackResult.LAUNCHER_ROOT
        }
        backRoute="no-back-target"; return BackResult.UNAVAILABLE
    }
    private fun clickAppBack(displayId: Int): Boolean {
        val root = windowsOnAllDisplays[displayId]?.firstOrNull { it.isFocused }?.root ?: return false
        @Suppress("DEPRECATION")
        try {
            if(root.actionList.any { it.id == android.view.accessibility.AccessibilityNodeInfo.ACTION_DISMISS } && root.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_DISMISS)) return true
            var visited=0
            fun visit(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
                if(++visited>128 || !node.isVisibleToUser) return false
                val id=node.viewIdResourceName
                val role=node.className?.toString().orEmpty()
                val navigationId=id=="android:id/home" || id?.endsWith(":id/action_bar_up")==true
                val button=role.endsWith("ImageButton") || role.endsWith("Button")
                val label=if(button) node.contentDescription?.toString()?.trim()?.lowercase(java.util.Locale.ROOT) else null
                if(node.isClickable && (navigationId || label in setOf("navigate up","back","quay lại","trở về")) && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return true
                for(i in 0 until node.childCount) {
                    val child=node.getChild(i) ?: continue
                    try { if(visit(child)) return true } finally { child.recycle() }
                }
                return false
            }
            return visit(root)
        } finally { root.recycle() }
    }
    override fun onServiceConnected() { super.onServiceConnected(); current = this; CoverMonitorService.current?.schedule() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() in setOf("com.android.systemui", "com.samsung.android.app.aodservice") || event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            SamsungCoverPage.onEvent(event)
            CoverMonitorService.current?.schedule()
        }
    }
    override fun onInterrupt() = Unit
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (current === this) current = null
        CoverMonitorService.current?.schedule()
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        if (current === this) current = null
        CoverMonitorService.current?.schedule()
        super.onDestroy()
    }
    companion object { var current: NavigationService? = null; private set }
}
