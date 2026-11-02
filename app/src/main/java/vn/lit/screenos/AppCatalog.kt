package vn.lit.screenos

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process

/** Current, launchable activities, including Foldlet and system apps. */
object AppCatalog {
    data class Entry(val component: ComponentName,val name: String,val icon: Drawable) {
        val key get()=component.flattenToString()
    }
    fun load(context: Context): List<Entry> {
        val pm=context.packageManager
        val entries=linkedMapOf<String,Entry>()
        for(category in listOf(Intent.CATEGORY_LAUNCHER,Intent.CATEGORY_INFO,Intent.CATEGORY_LEANBACK_LAUNCHER)) {
            val matches=try { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category),0) } catch(_: RuntimeException) { emptyList() }
            for(info in matches) runCatching {
                val activity=info.activityInfo
                if(!activity.enabled || !activity.exported || !activity.applicationInfo.enabled) return@runCatching
                val entry=Entry(ComponentName(activity.packageName,activity.name),info.loadLabel(pm).toString(),info.loadIcon(pm))
                entries.putIfAbsent(entry.key,entry)
            }
        }
        // LauncherApps can expose launchers absent from a single category query.
        // Ignore synthesized app-info entries: they are not application launchers.
        runCatching {
            context.getSystemService(LauncherApps::class.java).getActivityList(null,Process.myUserHandle()).forEach { activity ->
                runCatching {
                    val info=pm.getActivityInfo(activity.componentName,0)
                    if(!info.enabled || !info.exported || !info.applicationInfo.enabled) return@runCatching
                    val entry=Entry(activity.componentName,activity.label.toString(),activity.getIcon(0))
                    entries.putIfAbsent(entry.key,entry)
                }
            }
        }
        return entries.values.toList()
    }
}
