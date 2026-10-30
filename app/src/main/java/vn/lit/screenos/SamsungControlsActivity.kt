package vn.lit.screenos

import android.app.*
import android.appwidget.*
import android.content.*
import android.os.Bundle
import android.view.*
import android.widget.*

/** Single-purpose Samsung routine linking; Android handles binding and configuration. */
class SamsungControlsActivity: Activity() {
    private var pending=-1
    private var pendingKind=""
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        pending=state?.getInt("pending",-1) ?: -1;pendingKind=state?.getString("pendingKind").orEmpty()
        render()
    }
    override fun onResume() { super.onResume();render() }
    override fun onStop() {
        for(kind in SamsungRoutineControls.keys) SamsungRoutineControls.view(this,kind)?.let { (it.parent as? ViewGroup)?.removeView(it) }
        super.onStop()
    }
    override fun onSaveInstanceState(state: Bundle) { state.putInt("pending",pending);state.putString("pendingKind",pendingKind);super.onSaveInstanceState(state) }
    private fun render() {
        val root=SettingsUi.column(this).apply { setBackgroundColor(SettingsUi.background) }
        root.addView(SettingsUi.row(this,SettingsGlyph.Kind.BACK,I18n.text(R.string.ui_samsung_controls),null,null) { finish() })
        val content=SettingsUi.column(this,16)
        content.addView(SettingsUi.text(this,I18n.text(R.string.ui_samsung_routines_help),15f,SettingsUi.muted))
        for((kind,title) in listOf("mobile" to R.string.ui_shortcut_mobile,"hotspot" to R.string.ui_shortcut_hotspot)) {
            val card=SettingsUi.card(this)
            card.addView(SettingsUi.section(this,I18n.text(title)))
            for((suffix,label) in listOf("_on" to R.string.ui_on,"_off" to R.string.ui_off)) {
            val key=kind+suffix
            val linked=SamsungRoutineControls.linked(this,key)
            card.addView(SettingsUi.row(this,SettingsGlyph.Kind.PLAY,I18n.text(label),I18n.text(if(linked) R.string.ui_samsung_linked else R.string.ui_samsung_not_linked),SettingsUi.icon(this,SettingsGlyph.Kind.NEXT)) { bind(key) })
            card.addView(LinearLayout(this).apply {
                gravity=Gravity.CENTER
                addView(Button(this@SamsungControlsActivity).apply {
                    text=I18n.text(R.string.ui_add_samsung_routine);isAllCaps=false
                    contentDescription="${I18n.text(R.string.ui_add_samsung_routine)}: ${I18n.text(title)}, ${I18n.text(label)}"
                    setOnClickListener { SamsungRoutinePresets.add(this@SamsungControlsActivity,key) }
                })
                if(!linked) addView(Button(this@SamsungControlsActivity).apply {
                    text=I18n.text(R.string.ui_samsung_link);isAllCaps=false
                    setOnClickListener { bind(key) }
                })
            })
            if(linked) {
                val widget=SamsungRoutineControls.view(this,key)
                if(widget!=null) {
                    (widget.parent as? ViewGroup)?.removeView(widget)
                    widget.visibility=View.VISIBLE;widget.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    card.addView(widget,LinearLayout.LayoutParams(Ui.dp(this,120),Ui.dp(this,120)).apply { gravity=Gravity.CENTER_HORIZONTAL })
                }
                card.addView(Button(this).apply { text=I18n.text(R.string.ui_samsung_unlink);isAllCaps=false;setOnClickListener { SamsungRoutineControls.unlink(this@SamsungControlsActivity,key);render() } })
            }
            }
            content.addView(card)
        }
        root.addView(ScrollView(this).apply { addView(content) },LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        SettingsUi.configureWindow(this,root)
    }
    private fun bind(kind: String) {
        val provider=SamsungRoutineControls.provider(this)
        if(provider==null) { Platform.message(this,I18n.text(R.string.ui_samsung_routines_unavailable));return }
        pendingKind=kind;pending=SamsungRoutineControls.host(this).allocateAppWidgetId()
        val manager=getSystemService(AppWidgetManager::class.java)
        try {
            if(manager.bindAppWidgetIdIfAllowed(pending,provider.provider)) configure()
            else startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pending)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,provider.provider),1)
        } catch(_: RuntimeException) { discard();Platform.message(this,I18n.text(R.string.ui_samsung_routines_unavailable)) }
    }
    private fun configure() {
        if(getSystemService(AppWidgetManager::class.java).getAppWidgetInfo(pending)?.configure==null) { discard();return }
        try { SamsungRoutineControls.host(this).startAppWidgetConfigureActivityForResult(this,pending,0,2,null) }
        catch(_: RuntimeException) { discard();Platform.message(this,I18n.text(R.string.ui_samsung_routines_unavailable)) }
    }
    private fun discard() { if(pending>0) SamsungRoutineControls.host(this).deleteAppWidgetId(pending);pending=-1;pendingKind="" }
    @Deprecated("Platform activity result API")
    override fun onActivityResult(request: Int,result: Int,data: Intent?) {
        super.onActivityResult(request,result,data)
        if(pending<0) return
        if(result!=RESULT_OK) { discard();render();return }
        if(request==1) configure()
        else if(request==2) {
            try { SamsungRoutineControls.save(this,pendingKind,pending);pending=-1;pendingKind="" }
            catch(_: RuntimeException) { discard();Platform.message(this,I18n.text(R.string.ui_samsung_routines_unavailable)) }
            render()
        }
    }
}
