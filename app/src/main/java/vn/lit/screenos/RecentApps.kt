package vn.lit.screenos

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Process
import android.provider.Settings

/** System usage metadata only; no task snapshots, screen contents or stored history. */
object RecentApps {
    data class Entry(val component: ComponentName, val label: String, val icon: Drawable, val lastUsed: Long)
    private val excluded = setOf("com.android.systemui", "com.sec.android.app.launcher", "com.google.android.apps.nexuslauncher", "com.android.launcher3")

    fun hasAccess(context: Context): Boolean = context.getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

    fun load(context: Context): List<Entry> {
        if (!hasAccess(context)) return emptyList()
        val now = System.currentTimeMillis()
        val since = now - 7 * 24 * 60 * 60 * 1000L
        val cleared = Platform.preferences(context).getLong("recent_cleared_at",0)
        val pm = context.packageManager
        val launchable = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }.toSet()
        val usage = context.getSystemService(UsageStatsManager::class.java).queryAndAggregateUsageStats(since, now)
        return usage.values.asSequence()
            .filter { it.lastTimeUsed >= since && it.lastTimeUsed > cleared && it.packageName in launchable && it.packageName !in excluded && it.packageName != context.packageName }
            .sortedByDescending { it.lastTimeUsed }.take(30)
            .mapNotNull { stat ->
                try {
                    val component = pm.getLaunchIntentForPackage(stat.packageName)?.component ?: return@mapNotNull null
                    val info = pm.getApplicationInfo(stat.packageName, 0)
                    Entry(component, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info), stat.lastTimeUsed)
                } catch (_: Exception) { null }
            }.toList()
    }

    fun clear(context: Context) { Platform.preferences(context).edit().putLong("recent_cleared_at",System.currentTimeMillis()).apply() }

    fun isLauncherRoot(context: Context, pkg: String): Boolean {
        if (!hasAccess(context)) return false
        return try {
            val launcher = context.packageManager.getLaunchIntentForPackage(pkg)?.component ?: return false
            val info = context.packageManager.getActivityInfo(launcher, 0)
            val main = info.targetActivity ?: info.name
            val now = System.currentTimeMillis()
            val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(now - 15 * 60 * 1000L, now)
            val event = UsageEvents.Event()
            var lastClass: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName == pkg && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) lastClass = event.className
            }
            lastClass == main || lastClass == launcher.className
        } catch (_: Exception) { false }
    }

    fun openAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent, Platform.displayOptions(0)) }
        catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), Platform.displayOptions(0))
        }
    }
}
