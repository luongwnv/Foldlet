package vn.lit.screenos

import android.accessibilityservice.AccessibilityService
import android.content.*
import android.os.*
import android.view.accessibility.AccessibilityNodeInfo

/** Delegates to Samsung's visible Recent task actions through the user's service. */
object SamsungRecentTasks {
    private const val PACKAGE="com.sec.android.app.launcher"
    private const val TASK="$PACKAGE:id/taskView"
    private val handler=Handler(Looper.getMainLooper())
    private var active: Request?=null
    enum class Result { CLOSED, NOT_FOUND, UNAVAILABLE, REFUSED, CANCELED, BUSY }
    var status="idle";private set
    fun cancel() { active?.cancel() }
    fun close(context: Context,displayId: Int,entry: RecentApps.Entry?,done: (Result)->Unit) {
        if(active!=null) { done(Result.BUSY);return }
        fun reject(result: Result) { status=result.name;done(result) }
        val service=NavigationService.current
        if(service==null || !Build.MODEL.startsWith("SM-F711") || displayId!=1) { reject(Result.UNAVAILABLE);return }
        val prefixLabels=mutableSetOf<String>()
        val labels=if(entry==null) emptyList() else runCatching {
            val pm=context.packageManager
            val names=setOf(entry.label,pm.getActivityInfo(entry.component,0).loadLabel(pm).toString()).filter { it.isNotBlank() }
            // Native cards identify apps by their displayed name. Refuse ambiguous
            // names instead of guessing between two installed applications.
            val others=pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
                .filter { it.activityInfo.packageName!=entry.component.packageName }
                .flatMap { listOf(it.loadLabel(pm).toString(),it.activityInfo.applicationInfo.loadLabel(pm).toString()) }
            names.filter { name -> others.none { it==name } }.also { exact ->
                prefixLabels.addAll(exact.filter { name -> others.none { it.startsWith("$name ") || name.startsWith("$it ") } })
            }
        }.getOrDefault(emptyList())
        if(entry!=null && labels.isEmpty()) { reject(Result.REFUSED);return }
        val action=runCatching { context.packageManager.getResourcesForApplication(PACKAGE).getIdentifier("accessibility_close","string",PACKAGE) }.getOrDefault(0)
        if(action==0) { reject(Result.UNAVAILABLE);return }
        Request(service,displayId,labels,prefixLabels,action,entry==null,done).also { active=it;it.begin() }
    }
    private class Request(val service: NavigationService,val displayId: Int,val labels: List<String>,val prefixLabels: Set<String>,val closeAction: Int,val all: Boolean,val done: (Result)->Unit) {
        private var started=SystemClock.uptimeMillis()
        private var moved=false
        private var clickedAt=0L
        private var scrollDirection=0
        private var scrollPages=0
        private var lastViewport: Int?=null
        private var scrollAt=0L
        private val poll=Runnable { poll() }
        fun cancel() { finish(Result.CANCELED) }
        fun begin() {
            status="opening Samsung Recent"
            if(!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)) { finish(Result.UNAVAILABLE);return }
            handler.postDelayed(poll,500)
        }
        private fun root(): AccessibilityNodeInfo? {
            val windows=service.windowsOnAllDisplays[displayId] ?: return null
            for(window in windows) {
                val root=window.root ?: continue
                if(root.packageName?.toString()==PACKAGE) return root
                @Suppress("DEPRECATION") root.recycle()
            }
            return null
        }
        private fun matches(node: AccessibilityNodeInfo): Boolean {
            if(node.viewIdResourceName!=TASK) return false
            val description=node.contentDescription?.toString().orEmpty()
            // Split-screen groups can contain two apps; do not dismiss a group
            // on behalf of a single application.
            if(description.contains(',') || description.contains('\n')) return false
            return description in labels || prefixLabels.any { description.startsWith("$it ") }
        }
        private fun find(root: AccessibilityNodeInfo,predicate: (AccessibilityNodeInfo)->Boolean): AccessibilityNodeInfo? {
            var visited=0
            fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                if(++visited>400) return null
                if(predicate(node)) return AccessibilityNodeInfo.obtain(node)
                for(i in 0 until node.childCount) {
                    val child=node.getChild(i) ?: continue
                    try { visit(child)?.let { return it } } finally { @Suppress("DEPRECATION") child.recycle() }
                }
                return null
            }
            return visit(root)
        }
        private fun scroll(root: AccessibilityNodeInfo): Boolean {
            val container=find(root) { it.viewIdResourceName=="$PACKAGE:id/taskListContainer" } ?: return false
            try {
                val labels=mutableListOf<String>()
                find(container) { if(it.viewIdResourceName==TASK) labels.add(it.contentDescription?.toString().orEmpty());false }
                val viewport=labels.hashCode()
                if(lastViewport==viewport) scrollDirection++
                lastViewport=viewport
                while(scrollDirection<2 && scrollPages<24) {
                    val action=if(scrollDirection==0) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    val pager=find(container) { it.isScrollable && it.actionList.any { a -> a.id==action } }
                    if(pager!=null) try {
                        if(pager.performAction(action)) { scrollPages++;scrollAt=SystemClock.uptimeMillis();return true }
                    } finally { @Suppress("DEPRECATION") pager.recycle() }
                    scrollDirection++;lastViewport=null
                }
                return false
            } finally { @Suppress("DEPRECATION") container.recycle() }
        }
        private fun poll() {
            if(active!==this) return
            val display=Platform.cover(service)
            val now=SystemClock.uptimeMillis()
            if(NavigationService.current!==service || CoverMonitorService.current==null || CoverMonitorService.current?.foldedForTaskControl()==false) { finish(Result.CANCELED);return }
            if(!moved) {
                try {
                    val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_DEFAULT)
                        .setComponent(ComponentName(PACKAGE,"com.android.quickstep.RecentsActivity"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    service.startActivity(intent,Platform.displayOptions(displayId))
                    moved=true
                } catch(_: RuntimeException) { finish(Result.UNAVAILABLE);return }
                handler.postDelayed(poll,500);return
            }
            // The global Recent action briefly switches to the primary display.
            // Move its exported activity back before judging the cover's state.
            // Never inspect or act on tasks while the phone is locked.
            if(!Platform.unlocked(service) || display?.state!=android.view.Display.STATE_ON) {
                if(now-started<2500) { handler.postDelayed(poll,150);return }
                finish(Result.CANCELED);return
            }
            // Samsung rejects locked cards in performAccessibilityAction. After
            // accepting Close, its RecyclerView can retain the dismissed node
            // during/after animation; that cached node is not a live system task.
            if(clickedAt>0 && now-clickedAt>=800) { finish(Result.CLOSED);return }
            val root=runCatching { root() }.getOrNull()
            if(root!=null) try {
                if(clickedAt==0L) {
                    if(all && now-started>1800) {
                        val container=find(root) { it.viewIdResourceName=="$PACKAGE:id/taskListContainer" }
                        if(container!=null) try {
                            val task=find(container) { it.viewIdResourceName==TASK }
                            if(task==null) { finish(Result.CLOSED);return }
                            @Suppress("DEPRECATION") task.recycle()
                        } finally { @Suppress("DEPRECATION") container.recycle() }
                    }
                    val target=if(all) find(root) { it.viewIdResourceName=="$PACKAGE:id/clear_all" && it.isEnabled && it.isClickable } else find(root,::matches)
                    if(target!=null) try {
                        val action=if(all) AccessibilityNodeInfo.ACTION_CLICK else closeAction
                        if(target.actionList.none { it.id==action } || !target.performAction(action)) { finish(Result.REFUSED);return }
                        clickedAt=now;status="Samsung close action sent"
                    } finally { @Suppress("DEPRECATION") target.recycle() }
                    else if(!all && now-started>1500 && now-scrollAt>450) {
                        if(!scroll(root)) { finish(Result.NOT_FOUND);return }
                    }
                }
            } finally { @Suppress("DEPRECATION") root.recycle() }
            if(now-started>14000) { finish(if(clickedAt>0) Result.REFUSED else Result.NOT_FOUND);return }
            handler.postDelayed(poll,200)
        }
        private fun finish(result: Result) {
            if(active!==this) return
            handler.removeCallbacks(poll);active=null;status=result.name;done(result)
        }
    }
}
