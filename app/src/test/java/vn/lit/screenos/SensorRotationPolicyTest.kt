package vn.lit.screenos

import org.junit.Assert.*
import org.junit.Test

class SensorRotationPolicyTest {
    @Test fun calibratedFoldedFlip3AxesKeepContentUprightInEveryPose() {
        // Measured on SM-F711B: camera-left=180°, camera-above=270°.
        assertEquals(0,flip3CoverRotation(2))
        assertEquals(1,flip3CoverRotation(3))
        assertEquals(2,flip3CoverRotation(0))
        assertEquals(3,flip3CoverRotation(1))
        for(rotation in 0..3) assertEquals(rotation,flip3CoverRotation(flip3CoverRotation(rotation)))
    }
    @Test fun settlesAllFourPositionsWithoutNeedingRepeatedSensorEvents() {
        val policy=SensorRotationPolicy()
        for(rotation in 0..3) {
            val time=rotation*1000L
            assertNull(policy.update(rotation*90,time))
            assertEquals(350L,policy.remaining(time))
            assertNull(policy.advance(time+349))
            assertEquals(rotation,policy.advance(time+350))
            assertNull(policy.advance(time+700))
        }
    }
    @Test fun flatOrDiagonalReadingsCancelPendingChanges() {
        val policy=SensorRotationPolicy().apply { reset(0) }
        policy.update(90,0)
        policy.update(-1,200)
        assertNull(policy.advance(900))
        policy.update(90,1000)
        policy.update(45,1200)
        assertNull(policy.advance(2000))
    }
    @Test fun smallJitterAndWraparoundDoNotRotateAgain() {
        val policy=SensorRotationPolicy().apply { reset(0) }
        for((index,angle) in listOf(359,1,15,30,329,315,0).withIndex()) {
            assertNull(policy.update(angle,index*500L))
            assertNull(policy.advance(index*500L+400))
        }
    }
    @Test fun returningToTheCurrentPositionCancelsTheTimer() {
        val policy=SensorRotationPolicy().apply { reset(0) }
        policy.update(90,0);policy.update(0,300)
        assertNull(policy.advance(900))
        assertNull(policy.remaining(900))
    }
    @Test fun changedCandidateNeedsItsOwnSettlingPeriod() {
        val policy=SensorRotationPolicy().apply { reset(0) }
        policy.update(90,0);policy.update(180,300)
        assertNull(policy.advance(400))
        assertEquals(2,policy.advance(650))
    }
}
