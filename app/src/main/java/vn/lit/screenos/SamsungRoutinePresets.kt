package vn.lit.screenos

import android.app.Activity
import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Own radio presets, reviewed and saved by Samsung's native import UI. */
object SamsungRoutinePresets {
    const val PACKAGE="com.samsung.android.app.routineplus"
    fun add(activity: Activity,key: String) {
        require(key in SamsungRoutineControls.keys)
        val uri=Uri.parse("content://${BuildConfig.APPLICATION_ID}.routine-presets/$key.json")
        val intent=Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/json")
            .setComponent(ComponentName(PACKAGE,"$PACKAGE.main.RoutinePlusActivity"))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData=ClipData.newRawUri("Foldlet routine",uri)
        try { activity.startActivity(intent) }
        catch(_: RuntimeException) {
            Platform.message(activity,I18n.text(R.string.ui_samsung_install_routines_plus))
            try { activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("samsungapps://ProductDetail/$PACKAGE"))) }
            catch(_: RuntimeException) { Platform.message(activity,I18n.text(R.string.ui_samsung_routines_unavailable)) }
        }
    }
}

/** Exposes only four bundled JSON templates through temporary read grants. */
class SamsungRoutinePresetProvider: ContentProvider() {
    override fun onCreate()=true
    private fun file(uri: Uri): File {
        val name=uri.lastPathSegment ?: throw FileNotFoundException()
        if(uri.authority!="${BuildConfig.APPLICATION_ID}.routine-presets" || uri.path!="/$name" ||
            name.removeSuffix(".json") !in SamsungRoutineControls.keys || !name.endsWith(".json")) throw FileNotFoundException()
        val target=File(context!!.cacheDir,"routine-presets/${BuildConfig.VERSION_CODE}/$name")
        synchronized(this) {
            if(!target.exists()) {
                target.parentFile!!.mkdirs()
                context!!.assets.open("samsung-routines/$name").use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        }
        return target
    }
    override fun getType(uri: Uri): String { file(uri);return "application/json" }
    override fun openFile(uri: Uri,mode: String): ParcelFileDescriptor {
        if(mode!="r") throw FileNotFoundException("Read only")
        return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(uri: Uri,projection: Array<out String>?,selection: String?,args: Array<out String>?,sort: String?): Cursor {
        val file=file(uri)
        val columns=projection ?: arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)
        return MatrixCursor(columns).apply { addRow(columns.map { when(it) { OpenableColumns.DISPLAY_NAME -> file.name;OpenableColumns.SIZE -> file.length();else -> null } }) }
    }
    override fun insert(uri: Uri,values: ContentValues?): Uri?=throw UnsupportedOperationException()
    override fun update(uri: Uri,values: ContentValues?,selection: String?,args: Array<out String>?): Int=throw UnsupportedOperationException()
    override fun delete(uri: Uri,selection: String?,args: Array<out String>?): Int=throw UnsupportedOperationException()
}
