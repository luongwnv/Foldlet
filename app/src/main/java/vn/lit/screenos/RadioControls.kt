package vn.lit.screenos

import android.content.*
import android.content.pm.PackageManager
import android.os.*

/** Each short-lived request is bound to the helper signed with our certificate. */
object RadioControls {
    private const val helper="vn.lit.screenos.controls"
    data class Result(val installed: Boolean,val accepted: Boolean=false,val wifi: Int=-1,val bluetooth: Int=-1)
    fun request(context: Context,kind: String?=null,complete: (Result)->Unit) {
        val app=context.applicationContext
        if(app.packageManager.checkSignatures(app.packageName,helper)!=PackageManager.SIGNATURE_MATCH) { complete(Result(false));return }
        val handler=Handler(Looper.getMainLooper())
        var finished=false;var bound=false
        lateinit var connection: ServiceConnection
        lateinit var timeout: Runnable
        fun finish(result: Result) {
            if(finished) return
            finished=true;handler.removeCallbacks(timeout)
            if(bound) runCatching { app.unbindService(connection) }
            complete(result)
        }
        timeout=Runnable { finish(Result(true)) }
        val reply=Messenger(object: Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: Message) {
                val data=message.data
                finish(Result(true,data.getBoolean("accepted"),data.getInt("wifi",-1),data.getInt("bluetooth",-1)))
            }
        })
        connection=object: ServiceConnection {
            override fun onServiceConnected(name: ComponentName,binder: IBinder) {
                if(finished) return
                try {
                    Messenger(binder).send(Message.obtain(null,if(kind==null) 1 else 2).apply {
                        replyTo=reply;data=Bundle().apply { putString("kind",kind) }
                    })
                } catch(_: RemoteException) { finish(Result(true)) }
            }
            override fun onServiceDisconnected(name: ComponentName) { finish(Result(true)) }
            override fun onNullBinding(name: ComponentName) { finish(Result(true)) }
            override fun onBindingDied(name: ComponentName) { finish(Result(true)) }
        }
        try {
            bound=app.bindService(Intent().setComponent(ComponentName(helper,"$helper.RadioService")),connection,Context.BIND_AUTO_CREATE)
            if(bound) handler.postDelayed(timeout,4000) else finish(Result(true))
        } catch(_: RuntimeException) { finish(Result(true)) }
    }
}
