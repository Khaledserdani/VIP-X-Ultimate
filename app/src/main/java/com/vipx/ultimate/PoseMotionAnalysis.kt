package com.vipx.ultimate

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** Frame-by-frame posture angles from pose landmarks; not medical or body-shape measurements. */
internal data class PoseMotionMetrics(
    val torsoLeanDegrees: Float,
    val shoulderTiltDegrees: Float,
    val hipTiltDegrees: Float,
    val leftElbowFlexionDegrees: Float?,
    val rightElbowFlexionDegrees: Float?,
    val forwardBackBendDegrees: Float?
)

internal object PoseMotionAnalyzer {
    fun analyze(frame: TrackedBodyFrame): PoseMotionMetrics? {
        if (!frame.hasReliablePose(0.55f)) return null
        val p = frame.imageLandmarks
        val ls=p[11]; val rs=p[12]; val lh=p[23]; val rh=p[24]
        val shoulderDx=rs.x-ls.x
        val shoulderDy=rs.y-ls.y
        val hipDx=rh.x-lh.x
        val hipDy=rh.y-lh.y
        val shoulderMidX=(ls.x+rs.x)/2f
        val shoulderMidY=(ls.y+rs.y)/2f
        val hipMidX=(lh.x+rh.x)/2f
        val hipMidY=(lh.y+rh.y)/2f
        val torsoLean=Math.toDegrees(atan2((shoulderMidX-hipMidX).toDouble(),
            (hipMidY-shoulderMidY).toDouble())).toFloat()
        val shoulderTilt=Math.toDegrees(atan2(shoulderDy.toDouble(), shoulderDx.toDouble())).toFloat()
        val hipTilt=Math.toDegrees(atan2(hipDy.toDouble(), hipDx.toDouble())).toFloat()
        val leftElbow=jointAngle(p[11],p[13],p[15])?.let { 180f-it }
        val rightElbow=jointAngle(p[12],p[14],p[16])?.let { 180f-it }
        val world=frame.worldLandmarks
        val backBend=if(world.size>=25) {
            val a=world[11]; val b=world[12]; val c=world[23]; val d=world[24]
            val upperZ=(a.z+b.z)/2f
            val lowerZ=(c.z+d.z)/2f
            val upperY=(a.y+b.y)/2f
            val lowerY=(c.y+d.y)/2f
            Math.toDegrees(atan2((upperZ-lowerZ).toDouble(),
                abs(upperY-lowerY).coerceAtLeast(0.001f).toDouble())).toFloat()
        } else null
        return PoseMotionMetrics(torsoLean, shoulderTilt, hipTilt, leftElbow, rightElbow, backBend)
    }

    private fun jointAngle(a: BodyLandmark,b: BodyLandmark,c: BodyLandmark): Float? {
        if (minOf(a.confidence,b.confidence,c.confidence)<0.45f) return null
        val ux=a.x-b.x; val uy=a.y-b.y
        val vx=c.x-b.x; val vy=c.y-b.y
        val denom=sqrt(ux*ux+uy*uy)*sqrt(vx*vx+vy*vy)
        if (denom<0.0001f) return null
        val cosine=((ux*vx+uy*vy)/denom).coerceIn(-1f,1f)
        return Math.toDegrees(kotlin.math.acos(cosine).toDouble()).toFloat()
    }
}
