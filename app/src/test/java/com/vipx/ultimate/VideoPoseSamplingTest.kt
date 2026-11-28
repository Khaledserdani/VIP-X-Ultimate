package com.vipx.ultimate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPoseSamplingTest {
    @Test
    fun shortClipUsesDenseEightFpsSampling() {
        assertEquals(125L, VideoPoseSampling.intervalMs(20_000L))
        assertEquals(160, VideoPoseSampling.frameCount(20_000L))
    }

    @Test
    fun mediumClipUsesFourFpsSampling() {
        assertEquals(250L, VideoPoseSampling.intervalMs(60_000L))
        assertEquals(240, VideoPoseSampling.frameCount(60_000L))
    }

    @Test
    fun longClipUsesTwoFpsSampling() {
        assertEquals(500L, VideoPoseSampling.intervalMs(300_000L))
        assertEquals(600, VideoPoseSampling.frameCount(300_000L))
    }

    @Test
    fun veryLongClipNeverExceedsFrameLimit() {
        val duration = 3_600_000L
        assertTrue(VideoPoseSampling.frameCount(duration) <= VideoPoseSampling.MAX_FRAMES)
        assertEquals(2_000L, VideoPoseSampling.intervalMs(duration))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDurationIsRejected() {
        VideoPoseSampling.intervalMs(0L)
    }
}
