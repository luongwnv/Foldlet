package vn.lit.screenos

/** Write-only operations on the editor selected by the user. Never reads its text. */
interface CoverInputTarget {
    val privateInput: Boolean get() = false
    val numeric: Boolean get() = false
    fun text(value: String): Boolean
    fun backspace(): Boolean
    fun word(value: String): Boolean
    fun replaceWord(previous: String, replacement: String): Boolean
    fun action(): Boolean
}
