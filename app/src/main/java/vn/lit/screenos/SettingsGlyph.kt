package vn.lit.screenos

import android.graphics.*
import android.graphics.drawable.Drawable

/** Original settings pictograms; the cover toolbar keeps its own white glyphs. */
class SettingsGlyph(private val kind: Kind, private val tint: Int, private val pixels: Int) : Drawable() {
    enum class Kind { HOME, SETTINGS, INFO, PHONE, OVERLAY, ACCESS, BELL, GRID, ROTATE, SORT, FILTER, LABEL, EYE, SWIPE, USB, CHECK, NEXT, POWER, PLAY, BACK, COPY }
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        fun line(vararg xy: Float) { val path = Path().apply { moveTo(xy[0],xy[1]); for (i in 2 until xy.size step 2) lineTo(xy[i],xy[i+1]) }; canvas.drawPath(path,p) }
        when(kind) {
            Kind.HOME -> { line(3f,11f,12f,3f,21f,11f); line(6f,10f,6f,21f,10f,21f,10f,15f,14f,15f,14f,21f,18f,21f,18f,10f) }
            Kind.SETTINGS -> {
                canvas.drawPath(gearOutline,p)
                canvas.drawCircle(12f,12f,3.8f,p)
            }
            Kind.INFO -> { canvas.drawCircle(12f,12f,9f,p); line(12f,11f,12f,17f); p.style=Paint.Style.FILL; canvas.drawCircle(12f,7f,1f,p) }
            Kind.PHONE -> { canvas.drawRoundRect(6f,2f,18f,22f,2.5f,2.5f,p); line(6f,12f,18f,12f); line(10f,5f,14f,5f) }
            Kind.OVERLAY -> { canvas.drawRoundRect(3f,7f,17f,21f,2f,2f,p); canvas.drawRoundRect(7f,3f,21f,17f,2f,2f,p) }
            Kind.ACCESS -> {
                p.style=Paint.Style.FILL; canvas.drawCircle(12f,4.5f,1.8f,p)
                p.style=Paint.Style.STROKE
                line(4.8f,8.6f,12f,10f,19.2f,8.6f)
                line(12f,10f,12f,14.8f,8.2f,21f); line(12f,14.8f,15.8f,21f)
            }
            Kind.BELL -> {
                val bell=Path().apply { moveTo(4.5f,17.5f); quadTo(4f,16.8f,5.4f,15.4f); lineTo(6.2f,13.8f); lineTo(6.2f,9.8f); cubicTo(6.2f,2.3f,17.8f,2.3f,17.8f,9.8f); lineTo(17.8f,13.8f); lineTo(18.6f,15.4f); quadTo(20f,16.8f,19.5f,17.5f); close() }
                canvas.drawPath(bell,p); canvas.drawArc(10f,18.5f,14f,22f,0f,180f,false,p)
                line(12f,2f,12f,4f)
            }
            Kind.GRID -> for(x in listOf(4f,14f)) for(y in listOf(4f,14f)) canvas.drawRoundRect(x,y,x+6f,y+6f,1f,1f,p)
            Kind.ROTATE -> {
                val portrait=Path().apply {
                    moveTo(8f,19f); lineTo(5f,19f); quadTo(3f,19f,3f,17f); lineTo(3f,4f)
                    quadTo(3f,2f,5f,2f); lineTo(11.5f,2f); quadTo(13.5f,2f,13.5f,4f); lineTo(13.5f,9f)
                }
                canvas.drawPath(portrait,p)
                canvas.drawRoundRect(10f,11.5f,22f,21.5f,2f,2f,p)
            }
            Kind.SORT -> { line(4f,6f,20f,6f); line(4f,12f,15f,12f); line(4f,18f,10f,18f) }
            Kind.FILTER -> line(3f,4f,21f,4f,14f,12f,14f,19f,10f,21f,10f,12f,3f,4f)
            Kind.LABEL -> { line(3f,5f,21f,5f); line(12f,5f,12f,21f); line(8f,21f,16f,21f) }
            Kind.EYE -> { val path=Path().apply { moveTo(2f,12f); cubicTo(7f,3f,17f,3f,22f,12f); cubicTo(17f,21f,7f,21f,2f,12f) }; canvas.drawPath(path,p); canvas.drawCircle(12f,12f,3f,p) }
            Kind.SWIPE -> { line(2f,12f,22f,12f); line(6f,8f,2f,12f,6f,16f); line(18f,8f,22f,12f,18f,16f) }
            Kind.USB -> { canvas.drawRoundRect(7f,7f,17f,17f,2f,2f,p); line(9f,7f,9f,2f); line(15f,7f,15f,2f); line(12f,17f,12f,22f) }
            Kind.CHECK -> line(5f,12f,10f,17f,20f,7f)
            Kind.NEXT -> line(9f,6f,15f,12f,9f,18f)
            Kind.POWER -> { canvas.drawArc(4f,4f,20f,20f,-50f,280f,false,p); line(12f,2f,12f,11f) }
            Kind.PLAY -> line(8f,4f,20f,12f,8f,20f,8f,4f)
            Kind.BACK -> line(14f,5f,7f,12f,14f,19f)
            Kind.COPY -> { canvas.drawRoundRect(8f,7f,21f,21f,2f,2f,p); line(16f,3f,5f,3f,3f,5f,3f,16f) }
        }
        canvas.restoreToCount(save)
    }
    override fun getIntrinsicWidth() = pixels
    override fun getIntrinsicHeight() = pixels
    private val gearOutline: Path by lazy {
        val vertices=mutableListOf<PointF>()
        val angles=floatArrayOf(-30f,-20f,-12f,12f,20f)
        val radii=floatArrayOf(7.9f,7.9f,10.9f,10.9f,7.9f)
        for(tooth in 0 until 6) for(i in angles.indices) {
            val a=Math.toRadians((-90+tooth*60+angles[i]).toDouble())
            vertices.add(PointF(12f+kotlin.math.cos(a).toFloat()*radii[i],12f+kotlin.math.sin(a).toFloat()*radii[i]))
        }
        fun trimmed(from: PointF,to: PointF): PointF {
            val dx=to.x-from.x; val dy=to.y-from.y; val distance=kotlin.math.sqrt(dx*dx+dy*dy)
            val amount=minOf(.8f,distance*.25f)/distance
            return PointF(from.x+dx*amount,from.y+dy*amount)
        }
        Path().apply {
            vertices.forEachIndexed { i,v ->
                val entry=trimmed(v,vertices[(i+vertices.size-1)%vertices.size])
                val exit=trimmed(v,vertices[(i+1)%vertices.size])
                if(i==0) moveTo(entry.x,entry.y) else lineTo(entry.x,entry.y)
                quadTo(v.x,v.y,exit.x,exit.y)
            }
            close()
        }
    }
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Platform drawable API") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
