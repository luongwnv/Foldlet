package vn.lit.screenos

import android.app.Application
import android.content.Context

class FoldletApplication : Application() {
    override fun onCreate() { super.onCreate(); I18n.initialize(this) }
}

/** Uses Android resources and the current system/app configuration, with English fallback. */
object I18n {
    private lateinit var application: Context
    fun initialize(context: Context) { application=context.applicationContext }
    fun text(id: Int,vararg args: Any): String = if(args.isEmpty()) application.getString(id) else application.getString(id,*args)
}
