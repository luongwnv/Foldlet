package vn.lit.screenos

import android.appwidget.*
import android.content.*
import android.os.*
import android.view.*

/** User-bound Samsung routine widgets delegate their own click through PendingIntent. */
object SamsungRoutineControls {
    const val PACKAGE="com.samsung.android.app.routines"
    private const val HOST_ID=1702
    val keys=listOf("mobile_on","mobile_off","hotspot_on","hotspot_off")
    private val providers=setOf("$PACKAGE.ui.appwidget.RoutineAppWidgetProvider","$PACKAGE.appwidget.RoutineAppWidgetProvider")
    private var host: AppWidgetHost?=null
    class RoutineView(context: Context): AppWidgetHostView(context) {
        var revision=0;private set
        override fun updateAppWidget(value: android.widget.RemoteViews?) { super.updateAppWidget(value);revision++ }
    }
    private val views=mutableMapOf<Int,RoutineView>()
    private val handler=Handler(Looper.getMainLooper())
    private var active: Request?=null
    enum class Result { SUCCESS, NOT_LINKED, NO_CHANGE, CANCELED, BUSY }
    var status="idle";private set
    fun host(context: Context): AppWidgetHost {
        val value=host ?: object: AppWidgetHost(context.applicationContext,HOST_ID) {
            override fun onCreateView(context: Context,id: Int,info: AppWidgetProviderInfo)=RoutineView(context)
        }.also { host=it }
        runCatching { value.startListening() }
        return value
    }
    fun provider(context: Context): AppWidgetProviderInfo? {
        val available=context.getSystemService(AppWidgetManager::class.java).installedProviders
            .filter { it.provider.packageName==PACKAGE && it.provider.className in providers }
        return available.firstOrNull { it.provider.className=="$PACKAGE.ui.appwidget.RoutineAppWidgetProvider" } ?: available.firstOrNull()
    }
    fun id(context: Context,kind: String)=Platform.preferences(context).getInt("samsung_routine_widget_$kind",-1)
    fun linked(context: Context,kind: String): Boolean {
        val info=context.getSystemService(AppWidgetManager::class.java).getAppWidgetInfo(id(context,kind)) ?: return false
        return info.provider.packageName==PACKAGE && info.provider.className in providers
    }
    fun save(context: Context,kind: String,id: Int) {
        require(kind in keys)
        val info=context.getSystemService(AppWidgetManager::class.java).getAppWidgetInfo(id)
        require(info!=null && info.provider.packageName==PACKAGE && info.provider.className in providers)
        val old=id(context,kind)
        Platform.preferences(context).edit().putInt("samsung_routine_widget_$kind",id).apply()
        if(old>0 && old!=id) { views.remove(old);host(context).deleteAppWidgetId(old) }
    }
    fun unlink(context: Context,kind: String) {
        val id=id(context,kind)
        Platform.preferences(context).edit().remove("samsung_routine_widget_$kind").apply()
        views.remove(id)
        if(id>0) host(context).deleteAppWidgetId(id)
    }
    fun view(context: Context,kind: String): RoutineView? {
        if(!linked(context,kind)) return null
        val id=id(context,kind)
        return views.getOrPut(id) {
            val app=ContextThemeWrapper(context.applicationContext,R.style.SettingsTheme)
            val info=app.getSystemService(AppWidgetManager::class.java).getAppWidgetInfo(id)
            (host(app).createView(app,id,info) as RoutineView).apply {
                setPadding(0,0,0,0)
                @Suppress("DEPRECATION")
                updateAppWidgetSize(null,120,120,120,120)
                val size=Ui.dp(app,120)
                measure(View.MeasureSpec.makeMeasureSpec(size,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size,View.MeasureSpec.EXACTLY))
                layout(0,0,size,size)
            }
        }
    }
    fun cancel(owner: Any) { active?.takeIf { it.owner===owner }?.finish(Result.CANCELED) }
    fun toggle(owner: Any,context: Context,kind: String,complete: (Result)->Unit) {
        if(active!=null) { complete(Result.BUSY);return }
        val request=Request(owner,context.applicationContext,kind,complete)
        active=request;request.begin()
    }
    private class Request(val owner: Any,val context: Context,val kind: String,val complete: (Result)->Unit) {
        private var widget: RoutineView?=null
        private var before=-1
        private var started=0L
        private var clicked=false
        private var clickedAt=0L
        private var revision=0
        private var attempts=0
        private var appliedAt=0L
        private var settledAt=0L
        private val step=Runnable { step() }
        private fun read(done: (Int)->Unit) {
            if(kind=="hotspot") RadioControls.request(context) { done(it.hotspot) }
            else done(ShortcutStates.read(context,RadioControls.Result(false))[kind]?.enabled ?: -1)
        }
        fun begin() {
            read { state ->
                if(active!==this) return@read
                before=state
                if(before !in 0..1) { finish(Result.NO_CHANGE);return@read }
                widget=runCatching { view(context,kind+if(before==0) "_on" else "_off") }.getOrNull()
                if(widget==null) { finish(Result.NOT_LINKED);return@read }
                started=SystemClock.uptimeMillis();status="$kind: linked routine ready"
                handler.post(step)
            }
        }
        private fun step() {
            if(active!==this) return
            if(!Platform.unlocked(context) || (owner as? View)?.isAttachedToWindow==false) { finish(Result.CANCELED);return }
            if(!clicked) {
                val target=clickTarget(widget ?: return)
                if(target==null) {
                    if(SystemClock.uptimeMillis()-started>2500) finish(Result.NO_CHANGE) else handler.postDelayed(step,150)
                    return
                }
                // Invoke Samsung's user-selected widget listener, not a forged intent.
                revision=widget!!.revision;attempts++
                clicked=target.performClick();clickedAt=SystemClock.uptimeMillis()
                if(!clicked) { finish(Result.NO_CHANGE);return }
                status="$kind: native routine clicked"
            }
            read { state ->
                if(active!==this) return@read
                val now=SystemClock.uptimeMillis()
                if(appliedAt>0) {
                    if(settledAt==0L && (widget!!.revision>revision || now-appliedAt>1500)) {
                        // Stop the one-shot manual routine after its action. Reverse
                        // actions must be disabled, so the requested setting remains.
                        if(widget!!.revision>revision) clickTarget(widget!!)?.performClick()
                        settledAt=now
                    }
                    if(settledAt>0 && now-settledAt>800) finish(if(state in 0..1 && state!=before) Result.SUCCESS else Result.NO_CHANGE)
                    else handler.postDelayed(step,200)
                } else if(state in 0..1 && state!=before) {
                    appliedAt=now;handler.postDelayed(step,200)
                } else if(state==before && attempts<2 && widget!!.revision>revision && now-clickedAt>900) {
                    // A previously running routine was stopped; start its explicit
                    // On/Off action with the refreshed native widget listener.
                    clicked=false;handler.postDelayed(step,100)
                } else if(now-clickedAt>7000) finish(Result.NO_CHANGE)
                else handler.postDelayed(step,250)
            }
        }
        fun finish(result: Result) {
            if(active!==this) return
            handler.removeCallbacks(step);active=null;status="$kind: $result";complete(result)
        }
    }
    private fun clickTarget(root: View): View? {
        val name=runCatching { root.resources.getResourceEntryName(root.id) }.getOrDefault("")
        if(root !is AppWidgetHostView && root.isEnabled && root.isClickable && root.hasOnClickListeners() && listOf("setting","edit","config","more").none { name.contains(it,true) }) return root
        if(root is ViewGroup) for(i in 0 until root.childCount) clickTarget(root.getChildAt(i))?.let { return it }
        return null
    }
}
