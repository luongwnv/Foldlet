package vn.lit.screenos

import android.os.*
import java.io.File
import java.util.concurrent.TimeUnit

/** Shell-side service: only orientation commands for the cover display are accepted. */
class RotationShellService @JvmOverloads constructor(
    private val recovery: File = File("/data/local/tmp/lit-screenos-rotation-${Process.myUid()}")
) {
    private data class Original(val display: Int, val user: String, val fixed: String)
    private var original: Original? = null

    init {
        require(Process.myUid() == 2000 || Process.myUid() == 0) { "This service requires an authorized shell backend" }
        if (recovery.exists()) {
            val parts = recovery.readText().trim().split('|')
            if (parts.size == 3 && validDisplay(parts[0].toIntOrNull() ?: -1) && parts[1].matches(Regex("free|lock [0-3]")) && parts[2] in setOf("default","enabled","disabled")) {
                original = Original(parts[0].toInt(), parts[1], parts[2]); restoreInternal()
            }
        }
    }
    private fun validDisplay(id: Int) = (id == 1 && Build.MODEL.startsWith("SM-F711")) || (BuildConfig.DEBUG && id == 0 && Build.MODEL.startsWith("sdk_gphone"))
    private fun command(vararg args: String): String {
        val process = ProcessBuilder(listOf("/system/bin/cmd", "window") + args).redirectErrorStream(true).start()
        if (!process.waitFor(4,TimeUnit.SECONDS)) { process.destroyForcibly(); error("Window manager command timed out") }
        val output = process.inputStream.bufferedReader().readText().trim()
        check(process.exitValue() == 0 && !output.startsWith("Error") && !output.contains("Exception")) { output }
        return output
    }
    @Synchronized fun rotate(displayId: Int, mode: String,sensorRotation: Int=-1): String {
        require(validDisplay(displayId)) { "Only the cover display can be controlled" }
        require(mode in setOf("auto","portrait","landscape","sensor"))
        if(mode=="sensor") require(sensorRotation in 0..3)
        if (mode == "auto") { restoreInternal(); return "auto" }
        if (original?.display != displayId) {
            restoreInternal()
            val user = command("user-rotation", "-d", "$displayId").replace(Regex("\\s+"), " ")
            val fixed = command("fixed-to-user-rotation", "-d", "$displayId")
            check(user.matches(Regex("free|lock [0-3]")) && fixed in setOf("default","enabled","disabled")) { "Unknown rotation policy: $user / $fixed" }
            original = Original(displayId,user,fixed)
            recovery.writeText("$displayId|$user|$fixed")
            recovery.setReadable(false,false); recovery.setWritable(false,false)
            recovery.setReadable(true,true); recovery.setWritable(true,true)
        }
        // Flip3's cover panel is naturally landscape. Its portrait rotation is 270°.
        val rotation = if(mode=="sensor") sensorRotation else if (mode == "portrait") 3 else 0
        try {
            command("fixed-to-user-rotation", "-d", "$displayId", "enabled")
            command("user-rotation", "-d", "$displayId", "lock", "$rotation")
        } catch (failure: RuntimeException) { restoreInternal(); throw failure }
        return command("user-rotation", "-d", "$displayId")
    }
    private fun restoreInternal() {
        val saved = original ?: return
        val values = saved.user.split(' ')
        command("user-rotation", "-d", "${saved.display}", *values.toTypedArray())
        command("fixed-to-user-rotation", "-d", "${saved.display}", saved.fixed)
        original = null; recovery.delete()
    }
    @Synchronized fun restore() { restoreInternal() }

    companion object {
        /** ADB-only self-test of the exact service code, always restoring afterward. */
        @JvmStatic fun main(args: Array<String>) {
            require(args.size == 2)
            val service = RotationShellService(File("/data/local/tmp/lit-screenos-rotation-cli-${Process.myUid()}"))
            try {
                println("rotation-result=${service.rotate(args[0].toInt(),args[1])}")
                Thread.sleep(2000)
            } finally { service.restore(); println("rotation-policy-restored") }
        }
    }
}
