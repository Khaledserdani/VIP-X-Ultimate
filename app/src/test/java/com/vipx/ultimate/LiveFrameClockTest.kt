package com.vipx.ultimate

import org.junit.Assert.*
import org.junit.Test

class LiveFrameClockTest {
    @Test fun acceptsFramesAtConfiguredInterval() {
        val clock = LiveFrameClock(100L)
        assertTrue(clock.accept(1000L))
        assertFalse(clock.accept(1050L))
        assertTrue(clock.accept(1100L))
        assertEquals(1100L, clock.lastTimestampMs())
    }

    @Test fun rejectsNegativeAndDuplicateTimestamps() {
        val clock = LiveFrameClock(0L)
        assertFalse(clock.accept(-1L))
        assertTrue(clock.accept(10L))
        assertFalse(clock.accept(10L))
        assertTrue(clock.accept(11L))
    }

    @Test fun resetStartsANewSession() {
        val clock = LiveFrameClock(100L)
        assertTrue(clock.accept(500L))
        clock.reset()
        assertNull(clock.lastTimestampMs())
        assertTrue(clock.accept(0L))
    }

    @Test fun nextAfterGuaranteesStrictMonotonicity() {
        val clock = LiveFrameClock()
        assertEquals(100L, clock.nextAfter(100L))
        assertEquals(101L, clock.nextAfter(90L))
        assertEquals(200L, clock.nextAfter(200L))
    }

    @Test fun latestFrameGateDropsFramesThatWouldQueueBehindCurrentWork() {
        val gate = LatestFrameGate(100L)
        assertTrue(gate.shouldAnalyze(1000L))
        assertFalse(gate.shouldAnalyze(1080L))
        assertTrue(gate.shouldAnalyze(1100L))
        gate.reset()
        assertTrue(gate.shouldAnalyze(0L))
    }
}
