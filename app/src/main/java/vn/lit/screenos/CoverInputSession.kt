package vn.lit.screenos

/** Input connections may restart while the user is still editing the same field. */
internal class CoverInputSession {
    data class Editor(val packageName: String?,val fieldId: Int,val fieldName: String?,val inputType: Int)
    private var editor: Editor?=null
    var generation=0L;private set
    fun start(next: Editor,restarting: Boolean) {
        if(!restarting || editor!=next) generation++
        editor=next
    }
    fun finish() { editor=null;generation++ }
}

internal object CoverKeyboardPolicy {
    fun allowsAutomatic(packageName: String?,inputType: Int): Boolean {
        // Samsung's formula editor reports TYPE_CLASS_TEXT despite its own keypad.
        if(packageName in setOf("com.sec.android.app.popupcalculator","com.google.android.calculator","com.android.calculator2")) return false
        // Number/phone/date editors keep their app keypad. Manual opening still works.
        return (inputType and 0xf)==1
    }
}
