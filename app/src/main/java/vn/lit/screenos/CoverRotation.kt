package vn.lit.screenos

import android.content.Context
import android.hardware.SensorManager
import android.os.*
import android.view.Display
import android.view.OrientationEventListener
import org.json.JSONObject
import java.net.*
import java.util.concurrent.Executors

/** Authenticated USB backend; the sensor only controls the cover during app use. */
object CoverRotation {
    private val main=Handler(Looper.getMainLooper())
    private val io=Executors.newSingleThreadExecutor()
    @Volatile private var generation=0
    private var appContext: Context?=null
    var mode="auto";private set
    var available=false;private set
    private var supportsSensor=false
    private var sensor: OrientationEventListener?=null
    private val policy=SensorRotationPolicy()
    private var sensorDisplay=1
    private var sensorOwner: String?=null
    private var appSeen=false
    private var startedAt=0L
    private var pausedAt=0L
    private var desired=-1
    private var applied=-1
    private var writing=false
    private var checking=false
    private var lastProbe=0L
    var sensorAngle=-1;private set
    val sensorActive get()=sensor!=null
    val authorized get()=appContext?.let { Platform.preferences(it).getString("adb_bridge_token",null)!=null } ?: false
    fun initialize(context: Context) { appContext=context.applicationContext }
    private val settle=Runnable { if(mode=="sensor") policy.advance(SystemClock.uptimeMillis())?.let(::queueRotation) }
    private val watch=object: Runnable {
        override fun run() {
            if(mode!="sensor") return
            val context=appContext ?: return
            val display=Platform.overlayDisplay(context)
            val monitor=CoverMonitorService.current
            if(display==null || display.displayId!=sensorDisplay || display.state!=Display.STATE_ON || monitor==null || monitor.foldedForTaskControl()==false || !Platform.unlocked(context)) { clear();return }
            val now=SystemClock.uptimeMillis()
            val accessibility=NavigationService.current
            if(accessibility==null) { clear();return }
            val focused=Platform.focusedCoverPackage(accessibility,sensorDisplay)
            if(focused!=null && (focused==sensorOwner || focused !in setOf(context.packageName,"com.android.systemui","com.samsung.android.app.aodservice","com.sec.android.app.launcher"))) appSeen=true
            if(focused in setOf("com.android.systemui","com.samsung.android.app.aodservice")) {
                SamsungCoverPage.isClock(accessibility,sensorDisplay)
                if(SamsungCoverPage.isSamsungPage && (appSeen || now-startedAt>3000)) { clear();return }
            }
            if(!checking && now-lastProbe>=1500) {
                checking=true;lastProbe=now
                val request=generation
                probe(context) { ready ->
                    if(request!=generation || mode!="sensor") return@probe
                    checking=false
                    if(ready && supportsSensor) { pausedAt=0;pump() }
                    else {
                        available=false
                        if(pausedAt==0L) pausedAt=SystemClock.uptimeMillis()
                        if(SystemClock.uptimeMillis()-pausedAt>=15000) { clear();Platform.message(context,I18n.text(R.string.ui_the_adb_rotation_connection_was_disconnected)) }
                    }
                }
            }
            main.postDelayed(this,300)
        }
    }
    private fun stopSensor() {
        sensor?.disable();sensor=null
        main.removeCallbacks(settle);main.removeCallbacks(watch)
        policy.reset();desired=-1;applied=-1;writing=false;checking=false;pausedAt=0;sensorAngle=-1
    }
    fun invalidate() { generation++;stopSensor();available=false;supportsSensor=false;mode="auto" }
    fun bridgeConfigured(context: Context) {
        initialize(context)
        val resume=mode=="sensor"
        val owner=sensorOwner
        invalidate()
        probe(context) { ready ->
            val display=Platform.overlayDisplay(context)
            if(resume && ready && display!=null && display.state==Display.STATE_ON && Platform.unlocked(context) && CoverMonitorService.current!=null && CoverMonitorService.current?.foldedForTaskControl()!=false)
                apply(context,display.displayId,"sensor",ownerPackage=owner)
        }
    }
    private fun exchange(context: Context,op: String,requested: String?=null,rotation: Int?=null): JSONObject? {
        val prefs=Platform.preferences(context)
        val port=prefs.getInt("adb_bridge_port",0)
        val token=prefs.getString("adb_bridge_token",null) ?: return null
        if(port !in 1024..65535) return null
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"),port),700);socket.soTimeout=6000
                val message=JSONObject().put("auth",token).put("op",op)
                if(requested!=null) message.put("mode",requested)
                if(rotation!=null) message.put("rotation",rotation)
                socket.getOutputStream().write((message.toString()+"\n").toByteArray());socket.getOutputStream().flush()
                val reader=socket.getInputStream().bufferedReader();val response=StringBuilder()
                while(response.length<2048) { val next=reader.read();if(next<0 || next==10) break;response.append(next.toChar()) }
                JSONObject(response.toString()).takeIf { it.optBoolean("ok",false) }
            }
        } catch(_: Exception) { null }
    }
    fun probe(context: Context,complete: (Boolean)->Unit={}) {
        initialize(context)
        val request=generation
        io.execute {
            val result=exchange(context.applicationContext,"ping")
            main.post {
                if(request!=generation) { complete(false);return@post }
                available=result!=null;supportsSensor=result?.optBoolean("sensorRotation",false)==true;complete(available)
            }
        }
    }
    fun prepare(context: Context,displayId: Int,requested: String,complete: (Boolean)->Unit) {
        initialize(context)
        if(requested=="auto") { complete(true);return }
        if(displayId!=1 && !Platform.isEmulatorOverlay(context)) { complete(false);return }
        val request=generation
        probe(context) { ready ->
            if(request!=generation) { complete(false);return@probe }
            val supported=ready && (requested!="sensor" || supportsSensor)
            if(!supported) Platform.message(context,I18n.text(if(ready) R.string.ui_update_rotation_helper else R.string.ui_connect_a_computer_and_run_adb_rotation))
            complete(supported)
        }
    }
    fun apply(context: Context,displayId: Int,requested: String,ownerPackage: String?=null,complete: (Boolean)->Unit={}) {
        initialize(context)
        if(requested !in setOf("auto","sensor","portrait","landscape")) { complete(false);return }
        if(displayId==Display.DEFAULT_DISPLAY && !Platform.isEmulatorOverlay(context)) { complete(false);return }
        val request=++generation
        stopSensor();mode="auto"
        if(requested=="sensor") {
            probe(context) { ready ->
                if(request!=generation) { complete(false);return@probe }
                if(!ready || !supportsSensor) { Platform.message(context,I18n.text(if(ready) R.string.ui_update_rotation_helper else R.string.ui_connect_a_computer_and_run_adb_rotation));complete(false);return@probe }
                val listener=object: OrientationEventListener(context.applicationContext,SensorManager.SENSOR_DELAY_UI) {
                    override fun onOrientationChanged(orientation: Int) {
                        if(request!=generation || mode!="sensor") return
                        sensorAngle=orientation
                        val now=SystemClock.uptimeMillis()
                        policy.update(orientation,now)?.let(::queueRotation)
                        main.removeCallbacks(settle)
                        policy.remaining(now)?.let { main.postDelayed(settle,it) }
                    }
                }
                if(!listener.canDetectOrientation()) { Platform.message(context,I18n.text(R.string.ui_rotation_sensor_unavailable));complete(false);return@probe }
                sensorDisplay=displayId;sensorOwner=ownerPackage;appSeen=false;startedAt=SystemClock.uptimeMillis();lastProbe=startedAt
                desired=Platform.overlayDisplay(context)?.rotation ?: 0
                policy.reset(sensorQuarter(desired));mode="sensor";sensor=listener
                listener.enable();main.post(watch);pump();complete(true)
            }
            return
        }
        if(requested=="auto" && !authorized) { complete(true);return }
        io.execute {
            if(request!=generation) { main.post { complete(false) };return@execute }
            val success=exchange(context.applicationContext,if(requested=="auto") "restore" else "rotate",requested)!=null
            main.post {
                if(request!=generation) { complete(false);return@post }
                available=success
                if(success) mode=requested else if(requested!="auto") Platform.message(context,I18n.text(R.string.ui_the_adb_rotation_connection_was_disconnected))
                complete(success)
            }
        }
    }
    // The supported Flip3 rear panel is mounted opposite the sensor's upright axis.
    // The disposable emulator keeps the normal sensor/display coordinates.
    private fun sensorQuarter(rotation: Int)=if(android.os.Build.MODEL.startsWith("SM-F711")) flip3CoverRotation(rotation) else rotation
    private fun queueRotation(rotation: Int) { if(mode=="sensor") { desired=sensorQuarter(rotation);pump() } }
    private fun pump() {
        val context=appContext ?: return
        if(mode!="sensor" || writing || !available || desired !in 0..3 || desired==applied) return
        val request=generation;val target=desired;writing=true
        io.execute {
            if(request!=generation) return@execute
            val success=exchange(context,"rotate","sensor",target)!=null
            main.post {
                if(request!=generation || mode!="sensor") return@post
                writing=false;available=success
                if(success) { applied=target;pausedAt=0;pump() }
                else if(pausedAt==0L) pausedAt=SystemClock.uptimeMillis()
            }
        }
    }
    fun clear(complete: ()->Unit={}) {
        val request=++generation
        stopSensor();mode="auto"
        val context=appContext
        if(context==null || !authorized) { complete();return }
        io.execute {
            val success=exchange(context,"restore")!=null
            main.post { if(request==generation) available=success;complete() }
        }
    }
}
