package com.vipx.ultimate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodySurfaceChangeAnalyzerTest {
    private fun silhouette(waistScale: Float): PersonSilhouette {
        val width=100
        val height=160
        val mask=FloatArray(width*height)
        for (y in 8 until 152) {
            val t=(y-8f)/144f
            val base=when {
                t<0.18f -> 30f
                t<0.42f -> 38f
                t<0.62f -> 34f
                t<0.82f -> 40f
                else -> 27f
            }
            val adjusted=if(t in 0.42f..0.62f) base*waistScale else base
            val half=adjusted.toInt().coerceAtLeast(2)
            for(x in (50-half).coerceAtLeast(0)..(50+half).coerceAtMost(width-1)) {
                mask[y*width+x]=0.99f
            }
        }
        return requireNotNull(PersonSilhouette.fromConfidenceMask(width,height,mask))
    }

    @Test fun identicalSilhouettesHaveNoContourChange() {
        val profile=silhouette(1f)
        val result=BodySurfaceChangeAnalyzer.compare(profile,profile)
        assertTrue(result.reliable)
        assertEquals(0f,result.meanContourChangePercent,0.001f)
        assertEquals(0f,result.largestBandChangePercent,0.001f)
    }

    @Test fun wholeSurfaceChangeDetectsWaistContourDifference() {
        val baseline=silhouette(1f)
        val bentOrCompressed=silhouette(0.72f)
        val result=BodySurfaceChangeAnalyzer.compare(baseline,bentOrCompressed)
        assertTrue(result.reliable)
        assertTrue(result.meanContourChangePercent>0f)
        assertTrue(result.largestBandChangePercent>10f)
    }

    @Test fun invalidEmptyMaskIsRejected() {
        assertEquals(null,PersonSilhouette.fromConfidenceMask(32,32,FloatArray(32*32)))
    }
}
