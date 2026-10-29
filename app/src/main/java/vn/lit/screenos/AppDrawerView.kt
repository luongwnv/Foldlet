package vn.lit.screenos

import android.content.*
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.*
import android.view.*
import android.widget.*
import java.text.Normalizer
import java.util.Locale

/** Compact icon drawer following the reference's five-command toolbar structure. */
class AppDrawerView(context: Context, private val displayId: Int, private val close: () -> Unit) : LinearLayout(context) {
    private data class Entry(val component: ComponentName, val name: String, val icon: Drawable) { val key get() = component.flattenToString() }
    private val prefs = Platform.preferences(context)
    private val entries = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .map { Entry(ComponentName(it.activityInfo.packageName, it.activityInfo.name), it.loadLabel(context.packageManager).toString(), it.loadIcon(context.packageManager)) }
        .distinctBy { it.key }
    private val toolbar = LinearLayout(context)
    private val browser = FrameLayout(context)
    private val contents = FrameLayout(context)
    private val keyboard = LinearLayout(context).apply { orientation = VERTICAL }
    private var selected: Entry? = null
    private var searching = false
    private var query = ""
    private var searchField: EditText? = null
    private val toolbarHeight = Ui.dp(context, 36)
    private var toolbarShown = true

    init {
        orientation = VERTICAL; setBackgroundColor(Color.BLACK)
        addView(browser, LayoutParams(-1, 0, 1f))
        browser.addView(contents, FrameLayout.LayoutParams(-1, -1))
        toolbar.setBackgroundColor(Color.BLACK)
        browser.addView(toolbar, FrameLayout.LayoutParams(-1, toolbarHeight, Gravity.TOP))
        addView(keyboard, LayoutParams(-1, -2))
        renderToolbar(); renderGrid()
    }

    private fun command(kind: Glyph.Kind, label: String, selected: Boolean = false, action: () -> Unit) {
        toolbar.addView(Glyph.button(context, kind, label, selected, action), LayoutParams(0, -1, 1f))
    }
    private fun showToolbar(show: Boolean, animate: Boolean = true) {
        if (toolbarShown == show && animate) return
        toolbarShown = show
        toolbar.animate().cancel()
        toolbar.importantForAccessibility = if (show) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val target = if (show) 0f else -toolbarHeight.toFloat()
        if (animate) toolbar.animate().translationY(target).setDuration(140).start() else toolbar.translationY = target
    }
    private fun renderToolbar() {
        showToolbar(true, false)
        toolbar.removeAllViews(); keyboard.removeAllViews()
        val selection = selected
        if (selection != null) {
            command(Glyph.Kind.BACK, "Bỏ chọn") { selected = null; renderToolbar() }
            command(Glyph.Kind.HIDE, "Ẩn ${selection.name}") {
                prefs.edit().putStringSet("hidden", prefs.getStringSet("hidden", emptySet())!!.plus(selection.key)).apply()
                selected = null; renderToolbar(); renderGrid()
            }
            command(Glyph.Kind.STAR, "Yêu thích ${selection.name}", selection.key in prefs.getStringSet("favorites", emptySet())!!) {
                val values = prefs.getStringSet("favorites", emptySet())!!
                prefs.edit().putStringSet("favorites", if (selection.key in values) values.minus(selection.key) else values.plus(selection.key)).apply()
                renderToolbar(); renderGrid()
            }
            command(Glyph.Kind.LANDSCAPE, "Mở ${selection.name} nằm ngang") { orient("landscape", selection) }
            command(Glyph.Kind.PORTRAIT, "Mở ${selection.name} dọc") { orient("portrait", selection) }
            return
        }
        if (searching) {
            toolbar.addView(Glyph.button(context, Glyph.Kind.BACK, "Đóng tìm kiếm") { searching = false; query = ""; renderToolbar(); renderGrid() }, LayoutParams(Ui.dp(context, 40), -1))
            searchField = EditText(context).apply {
                hint = "Tìm ứng dụng"; setTextColor(Color.WHITE); setHintTextColor(0xFF888888.toInt())
                textSize = 17f; isSingleLine = true; minimumHeight = 0
                setPadding(0, 0, 0, 0); setBackgroundColor(Color.TRANSPARENT)
                showSoftInputOnFocus = false
                setOnClickListener { if (keyboard.childCount == 0) buildKeyboard() }
                setText(query); setSelection(text.length)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); renderGrid() }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
            }
            toolbar.addView(searchField, LayoutParams(0, -1, 1f))
            buildKeyboard()
            searchField?.requestFocus()
            return
        }
        val orientation = prefs.getString("orientation", "auto")
        command(when (orientation) { "portrait" -> Glyph.Kind.PORTRAIT; "landscape" -> Glyph.Kind.LANDSCAPE; else -> Glyph.Kind.ROTATE }, "Xoay màn hình") {
            sheet(listOf("Tự động" to { orient("auto") }, "Ngang" to { orient("landscape") }, "Dọc" to { orient("portrait") }))
        }
        command(Glyph.Kind.SEARCH, "Tìm kiếm") { prefs.edit().putString("app_mode", "all").apply(); searching = true; renderToolbar(); renderGrid() }
        command(Glyph.Kind.SORT, "Sắp xếp") {
            sheet(listOf("A → Z" to { sort("az") }, "Z → A" to { sort("za") }, "Dùng nhiều nhất" to { sort("used") }, "Tự sắp xếp" to { sort("custom") }))
        }
        command(Glyph.Kind.RECENT, "Gần đây", prefs.getString("app_mode", "all") == "recent") { filter("recent") }
        command(Glyph.Kind.STAR, "Yêu thích", prefs.getString("app_mode", "all") == "favorite") { filter("favorite") }
    }
    private fun sheet(options: List<Pair<String, () -> Unit>>) {
        showToolbar(true, false)
        contents.removeAllViews()
        val column = Ui.column(context, 4).apply { setBackgroundColor(Color.BLACK) }
        options.forEach { (name, action) ->
            column.addView(Ui.button(context, name, action).apply { setBackgroundColor(Color.BLACK) }, LayoutParams(-1, Ui.dp(context, 42)))
        }
        column.addView(Ui.button(context, "Quay lại") { renderGrid() }.apply { setBackgroundColor(Color.BLACK) }, LayoutParams(-1, Ui.dp(context, 42)))
        contents.addView(Ui.scroll(context, column), FrameLayout.LayoutParams(-1, -1).apply { topMargin = toolbarHeight })
    }
    private fun orient(value: String, entry: Entry? = null) {
        if (!Platform.unlocked(context)) return
        prefs.edit().putString(if (entry == null) "orientation" else "orientation.${entry.key}", value).apply()
        if (entry != null) { selected = null; launch(entry) } else { renderToolbar(); renderGrid() }
    }
    private fun sort(value: String) { prefs.edit().putString("app_sort", value).apply(); renderGrid() }
    private fun filter(value: String) {
        prefs.edit().putString("app_mode", if (prefs.getString("app_mode", "all") == value) "all" else value).apply()
        renderToolbar(); renderGrid()
    }
    private fun normalized(value: String) = Normalizer.normalize(value.lowercase(Locale.ROOT).replace('đ','d'), Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "")
    private fun renderGrid() {
        showToolbar(true, false)
        contents.removeAllViews()
        val favorites = prefs.getStringSet("favorites", emptySet())!!
        val hidden = prefs.getStringSet("hidden", emptySet())!!
        val recent = prefs.getString("recents", "")!!.split('|')
        val custom = prefs.getString("custom_order", "")!!.split('|')
        val mode = prefs.getString("app_mode", "all")
        val sort = prefs.getString("app_sort", "az")
        val visible = entries.filter { it.key !in hidden && (mode != "favorite" || it.key in favorites) && (mode != "recent" || it.key in recent) && normalized(it.name).contains(normalized(query)) }
            .sortedWith { a, b ->
                val order = when {
                    mode == "recent" -> recent.indexOf(a.key).compareTo(recent.indexOf(b.key))
                    sort == "za" -> normalized(b.name).compareTo(normalized(a.name))
                    sort == "used" -> prefs.getInt("launches.${b.key}", 0).compareTo(prefs.getInt("launches.${a.key}", 0))
                    sort == "custom" -> custom.indexOf(a.key).let { if (it < 0) Int.MAX_VALUE else it }.compareTo(custom.indexOf(b.key).let { if (it < 0) Int.MAX_VALUE else it })
                    else -> 0
                }
                if (order != 0) order else normalized(a.name).compareTo(normalized(b.name))
            }
        val availableWidth = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val widthDp = (availableWidth / resources.displayMetrics.density).toInt()
        val columns = prefs.getInt("columns", 4).coerceIn(4,6).coerceAtMost((widthDp / 72).coerceAtLeast(2))
        val cellWidth = availableWidth / columns
        val iconSize = minOf(Ui.dp(context,112), cellWidth - Ui.dp(context,16)).coerceAtLeast(Ui.dp(context,32))
        val showNames = prefs.getBoolean("show_names",false)
        val cellHeight = iconSize + Ui.dp(context,if(showNames) 40 else 16)
        val grid = GridLayout(context).apply { columnCount = columns; useDefaultMargins = false; alignmentMode = GridLayout.ALIGN_BOUNDS }
        visible.forEachIndexed { index, app ->
            val tile = FrameLayout(context).apply {
                contentDescription = app.name; isClickable = true; isFocusable = true
                addView(ImageView(context).apply { setImageDrawable(app.icon); scaleType = ImageView.ScaleType.FIT_CENTER }, FrameLayout.LayoutParams(iconSize, iconSize, if(showNames) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.CENTER).apply { if(showNames) topMargin=Ui.dp(context,8) })
                if(showNames) addView(TextView(context).apply {
                    text=app.name; textSize=14f; setTextColor(Color.WHITE); isSingleLine=true
                    ellipsize=android.text.TextUtils.TruncateAt.END; gravity=Gravity.CENTER; includeFontPadding=false
                },FrameLayout.LayoutParams(-1,Ui.dp(context,22),Gravity.BOTTOM).apply { setMargins(Ui.dp(context,4),0,Ui.dp(context,4),Ui.dp(context,4)) })
                setOnClickListener {
                    if (!Platform.unlocked(context)) return@setOnClickListener
                    if (selected != null) { selected = app; renderToolbar() }
                    else launch(app)
                }
                setOnLongClickListener {
                    if (!Platform.unlocked(context)) return@setOnLongClickListener true
                    if (selected?.key == app.key) startDragAndDrop(ClipData.newPlainText("app", app.key), DragShadowBuilder(this), app.key, 0)
                    else { selected = app; renderToolbar() }
                    true
                }
                setOnDragListener { _, event ->
                    if (event.action == DragEvent.ACTION_DROP) {
                        val from = event.localState as? String
                        if (from != null && from != app.key) {
                            val order = entries.sortedBy { entry -> custom.indexOf(entry.key).let { if (it < 0) Int.MAX_VALUE else it } }.map { it.key }.toMutableList()
                            order.remove(from); order.add(order.indexOf(app.key).coerceAtLeast(0), from)
                            prefs.edit().putString("custom_order", order.joinToString("|")).putString("app_sort", "custom").apply()
                            selected = null; renderToolbar(); renderGrid()
                        }
                    }
                    true
                }
            }
            grid.addView(tile, GridLayout.LayoutParams().apply {
                width = cellWidth; height = cellHeight
                columnSpec = GridLayout.spec(index % columns, GridLayout.LEFT)
                rowSpec = GridLayout.spec(index / columns, GridLayout.TOP)
            })
        }
        if (visible.isEmpty()) contents.addView(Ui.text(context, "Không có ứng dụng", 17f, Ui.muted), FrameLayout.LayoutParams(-1, -2).apply { topMargin = toolbarHeight })
        else {
            val list = FrameLayout(context).apply {
                setPadding(0, toolbarHeight, 0, 0)
                addView(grid, FrameLayout.LayoutParams(-1, -2))
            }
            val scroller = Ui.scroll(context, list).apply {
                isVerticalScrollBarEnabled = false
                setOnScrollChangeListener { _: View, _: Int, y: Int, _: Int, oldY: Int ->
                    if (!searching && selected == null) {
                        if (y == 0 || y < oldY) showToolbar(true)
                        else if (y > oldY && y > Ui.dp(context, 8)) showToolbar(false)
                    }
                }
            }
            contents.addView(scroller)
        }
    }
    private fun launch(entry: Entry) {
        val rotation = prefs.getString("orientation.${entry.key}", prefs.getString("orientation", "auto")) ?: "auto"
        CoverRotation.prepare(context, displayId, rotation) { success ->
        if (!success || !isAttachedToWindow || !Platform.unlocked(context)) return@prepare
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(entry.component)
        if (Platform.launch(context, intent, displayId)) {
            val recent = prefs.getString("recents", "")!!.split('|').filter { it.isNotBlank() }
            prefs.edit().putString("recents", (listOf(entry.key) + recent).distinct().take(40).joinToString("|"))
                .putInt("launches.${entry.key}", prefs.getInt("launches.${entry.key}", 0) + 1).apply()
            close()
            CoverRotation.apply(context, displayId, rotation)
        }
        }
    }
    private fun buildKeyboard() {
        listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM").forEach { letters ->
            val row = LinearLayout(context)
            letters.forEach { letter -> row.addView(Ui.button(context, letter.toString()) { searchField?.text?.append(letter.lowercaseChar()) }.apply { minimumHeight=0; minHeight=0; setPadding(0,0,0,0); textSize=14f; setBackgroundColor(Color.BLACK) }, LayoutParams(0,Ui.dp(context,24),1f)) }
            keyboard.addView(row)
        }
        val row = LinearLayout(context)
        listOf("Xóa" to { searchField?.setText(""); Unit }, "Dấu cách" to { searchField?.text?.append(' '); Unit }, "⌫" to { searchField?.text?.let { if (it.isNotEmpty()) it.delete(it.length-1,it.length) }; Unit }, "Xong" to { keyboard.removeAllViews(); Unit }).forEach { (label, action) ->
            row.addView(Ui.button(context,label,action).apply { minimumHeight=0; minHeight=0; setPadding(0,0,0,0); textSize=14f; setBackgroundColor(Color.BLACK) }, LayoutParams(0,Ui.dp(context,24),1f))
        }
        keyboard.addView(row)
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        if (oldw > 0 && oldw != w) post { renderGrid() }
    }
}
