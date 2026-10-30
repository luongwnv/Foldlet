package vn.lit.screenos

import android.graphics.*
import android.graphics.drawable.Drawable
import android.content.Context
import android.widget.ImageButton

/** Small original vector glyphs, drawn with platform Canvas. */
class Glyph(private val kind: Kind, private val selected: Boolean = false, private val size: Int = 24) : Drawable() {
    enum class Kind { ROTATE, SEARCH, SORT, RECENT, STAR, BACK, GRID, HOME, HIDE, PORTRAIT, LANDSCAPE, CLOSE, FORWARD, NAV_BACK, NAV_HOME, NAV_RECENT, KEYBOARD }
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        fun line(vararg points: Float) { val path = Path(); path.moveTo(points[0], points[1]); for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1]); canvas.drawPath(path, p) }
        when (kind) {
            Kind.KEYBOARD -> { canvas.drawRoundRect(2f,5f,22f,19f,2f,2f,p); for(y in listOf(9f,12f)) for(x in listOf(6f,10f,14f,18f)) canvas.drawPoint(x,y,p); line(8f,16f,16f,16f) }
            Kind.SEARCH -> { canvas.drawCircle(10f, 10f, 6f, p); line(15f, 15f, 21f, 21f) }
            Kind.BACK -> line(14f, 4f, 6f, 12f, 14f, 20f)
            Kind.NAV_BACK -> {
                p.strokeWidth = 1.8f
                line(15f, 5f, 8f, 12f, 15f, 19f)
            }
            Kind.NAV_HOME -> {
                p.strokeWidth = 1.8f
                canvas.drawRoundRect(5f, 5f, 19f, 19f, 4.5f, 4.5f, p)
            }
            Kind.NAV_RECENT -> {
                p.strokeWidth = 1.8f
                for (x in listOf(5.5f, 12f, 18.5f)) line(x, 5f, x, 19f)
            }
            Kind.FORWARD -> line(9f, 4f, 17f, 12f, 9f, 20f)
            Kind.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            Kind.STAR -> { val path = Path(); for (i in 0..10) { val angle = Math.PI * (i / 5.0 - .5); val radius = if (i % 2 == 0) 9f else 4.1f; val x = 12 + kotlin.math.cos(angle).toFloat() * radius; val y = 12 + kotlin.math.sin(angle).toFloat() * radius; if (i == 0) path.moveTo(x,y) else path.lineTo(x,y) }; if (selected) p.style = Paint.Style.FILL; canvas.drawPath(path,p) }
            Kind.RECENT -> {
                val ring = Path().apply {
                    moveTo(4.2f, 8.8f)
                    cubicTo(5.5f, 5.4f, 8.4f, 3.5f, 12f, 3.5f)
                    cubicTo(16.7f, 3.5f, 20.5f, 7.3f, 20.5f, 12f)
                    cubicTo(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f)
                    cubicTo(8.4f, 20.5f, 5.3f, 18.3f, 4f, 15.5f)
                }
                canvas.drawPath(ring, p)
                line(4.2f, 4.3f, 4.2f, 8.8f, 8.7f, 8.8f)
                line(12f, 7.5f, 12f, 12f, 15.5f, 14f)
            }
            Kind.SORT -> { line(3f, 6f, 20f, 6f); line(3f, 12f, 15f, 12f); line(3f, 18f, 9f, 18f) }
            Kind.GRID -> for (x in listOf(5f, 12f, 19f)) for (y in listOf(5f, 12f, 19f)) { p.style = Paint.Style.FILL; canvas.drawCircle(x,y,1.7f,p) }
            Kind.HOME -> { line(3f, 11f, 12f, 3f, 21f, 11f); line(6f, 9f, 6f, 21f, 10f, 21f, 10f, 15f, 14f, 15f, 14f, 21f, 18f, 21f, 18f, 9f) }
            Kind.HIDE -> { canvas.drawOval(2f, 6f, 22f, 18f,p); canvas.drawCircle(12f,12f,3f,p); line(3f,3f,21f,21f) }
            Kind.PORTRAIT -> canvas.drawRoundRect(6f,2f,18f,22f,2f,2f,p)
            Kind.LANDSCAPE -> canvas.drawRoundRect(2f,6f,22f,18f,2f,2f,p)
            Kind.ROTATE -> { canvas.save(); canvas.rotate(-25f,12f,12f); canvas.drawRoundRect(7f,5f,17f,19f,1.5f,1.5f,p); canvas.restore(); canvas.drawArc(2f,2f,22f,22f,190f,75f,false,p); canvas.drawArc(2f,2f,22f,22f,10f,75f,false,p); line(2f,4f,2f,9f,7f,9f); line(22f,20f,22f,15f,17f,15f) }
        }
        canvas.restoreToCount(save)
    }
    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Platform drawable API") override fun getOpacity() = PixelFormat.TRANSLUCENT
    companion object {
        fun button(context: Context, kind: Kind, description: String, selected: Boolean = false, action: () -> Unit) = ImageButton(context).apply {
            setImageDrawable(Glyph(kind, selected, Ui.dp(context, 26)))
            val shape = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (selected) 0xFF252525.toInt() else Color.TRANSPARENT)
                cornerRadius = Ui.dp(context, 8).toFloat()
            }
            background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x40FFFFFF), shape, null)
            isSelected = selected
            contentDescription = description
            setPadding(Ui.dp(context, 5), Ui.dp(context, 5), Ui.dp(context, 5), Ui.dp(context, 5))
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { action() }
        }
    }
}
