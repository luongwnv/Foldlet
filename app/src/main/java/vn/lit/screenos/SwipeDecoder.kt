package vn.lit.screenos

import android.content.Context
import android.graphics.PointF
import java.text.Normalizer
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min

/** Small offline shape matcher. Vocabulary and traces stay on the device. */
class SwipeDecoder(context: Context, additionalWords: List<String> = emptyList()) {
    private data class Word(val text: String, val keys: String, val rank: Int)
    private val words: List<Word>
    init {
        val base=context.assets.open("swipe-words.txt").bufferedReader().use { it.readLines() }
        val all=additionalWords.flatMap { it.split(Regex("[^\\p{L}]+")) }+base
        words=all.asSequence().map(String::trim).filter { it.length in 2..24 }
            .distinctBy { it.lowercase(Locale.ROOT) }.mapIndexedNotNull { rank,text ->
                val keys=normalize(text).fold("") { out,c -> if(out.lastOrNull()==c) out else out+c }
                if(keys.length<2 || keys.any { it !in 'a'..'z' }) null else Word(text,keys,rank)
            }.toList()
    }
    fun decode(points: List<PointF>,centers: Map<Char,PointF>,keyWidth: Float): List<String> {
        if(points.size<3 || keyWidth<=0) return emptyList()
        val input=resample(points,24)
        val first=input.first();val last=input.last()
        val startKey=centers.minByOrNull { distance(first,it.value) }?.key
        val endKey=centers.minByOrNull { distance(last,it.value) }?.key
        return words.asSequence().mapNotNull { word ->
            if(word.keys.first()!=startKey || word.keys.last()!=endKey) return@mapNotNull null
            val path=word.keys.map { centers[it] ?: return@mapNotNull null }
            val start=distance(first,path.first())/keyWidth;val end=distance(last,path.last())/keyWidth
            if(start>1.1f || end>1.1f) return@mapNotNull null
            val template=resample(path,24)
            var previous=FloatArray(25) { Float.POSITIVE_INFINITY }.apply { this[0]=0f }
            for(i in input.indices) {
                val row=FloatArray(25) { Float.POSITIVE_INFINITY }
                for(j in template.indices) row[j+1]=distance(input[i],template[j])/keyWidth+min(previous[j],min(previous[j+1],row[j]))
                previous=row
            }
            val cost=previous[24]/48f+start*.30f+end*.35f+min(word.rank,5000)*.000005f
            if(cost>1.10f) null else word.text to cost
        }.sortedBy { it.second }.map { it.first }.distinct().take(3).toList()
    }
    private fun resample(points: List<PointF>,count: Int): List<PointF> {
        val lengths=FloatArray(points.size)
        for(i in 1 until points.size) lengths[i]=lengths[i-1]+distance(points[i-1],points[i])
        val total=lengths.last()
        if(total<.01f) return List(count) { PointF(points[0].x,points[0].y) }
        var segment=1
        return List(count) { index ->
            val d=total*index/(count-1)
            while(segment<points.lastIndex && lengths[segment]<d) segment++
            val a=points[segment-1];val b=points[segment]
            val fraction=if(lengths[segment]==lengths[segment-1]) 0f else (d-lengths[segment-1])/(lengths[segment]-lengths[segment-1])
            PointF(a.x+(b.x-a.x)*fraction,a.y+(b.y-a.y)*fraction)
        }
    }
    private fun distance(a: PointF,b: PointF)=hypot(a.x-b.x,a.y-b.y)
    companion object {
        fun normalize(value: String)=Normalizer.normalize(value.lowercase(Locale.ROOT).replace('đ','d'),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
    }
}
