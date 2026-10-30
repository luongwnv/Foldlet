package vn.lit.screenos

import kotlin.math.abs

/** Folded SM-F711: camera-left is sensor 180°, camera-above is 270°.
 * The rear cover's natural orientation is opposite the accelerometer's upright axis.
 * This transform is its own inverse, including when seeding the current rotation.
 */
internal fun flip3CoverRotation(sensorQuarter: Int): Int {
    require(sensorQuarter in 0..3)
    return (sensorQuarter+2)%4
}

/** Cardinal positions must settle; flat/diagonal readings never change rotation. */
class SensorRotationPolicy(private val settleMs: Long=350) {
    private var accepted=-1
    private var candidate=-1
    private var since=0L
    fun reset(rotation: Int=-1) { accepted=rotation;candidate=-1;since=0 }
    fun update(angle: Int,now: Long): Int? {
        if(angle !in 0..359) { candidate=-1;return null }
        val rotation=((angle+45)/90)%4
        val distance=abs(angle-rotation*90).let { minOf(it,360-it) }
        if(distance>30 || rotation==accepted) { candidate=-1;return null }
        if(candidate!=rotation) { candidate=rotation;since=now }
        return advance(now)
    }
    fun advance(now: Long): Int? {
        if(candidate<0 || now-since<settleMs) return null
        accepted=candidate;candidate=-1;return accepted
    }
    fun remaining(now: Long): Long?=if(candidate<0) null else (settleMs-(now-since)).coerceAtLeast(0)
}
