package com.vipx.ultimate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcclusionAwarePoseTrackerTest {
    private fun point(x: Float, confidence: Float = 0.9f) =
        BodyLandmark(11, x, 0.4f, 0f, confidence)

    @Test fun visibleObservationIsPreserved() {
        val result = OcclusionAwarePoseTracker().update(listOf(point(0.3f)), 0L).single()
        assertEquals(LandmarkSource.OBSERVED, result.source)
        assertEquals(0.3f, result.x, 0.0001f)
    }

    @Test fun shortOcclusionPredictsWithReducedConfidence() {
        val tracker = OcclusionAwarePoseTracker()
        tracker.update(listOf(point(0.3f)), 0L)
        tracker.update(listOf(point(0.34f)), 100L)
        val predicted = tracker.update(listOf(point(0.34f, 0.1f)), 200L).single()
        assertEquals(LandmarkSource.TEMPORAL_PREDICTED, predicted.source)
        assertTrue(predicted.x > 0.34f)
        assertTrue(predicted.confidence < 0.9f)
    }

    @Test fun prolongedOcclusionReturnsUnknown() {
        val tracker = OcclusionAwarePoseTracker()
        tracker.update(listOf(point(0.3f)), 0L)
        val missing = tracker.update(listOf(point(0.3f, 0.1f)), 500L).single()
        assertEquals(LandmarkSource.UNKNOWN, missing.source)
        assertEquals(0f, missing.confidence, 0f)
    }

    @Test fun visiblePointReplacesPrediction() {
        val tracker = OcclusionAwarePoseTracker()
        tracker.update(listOf(point(0.3f)), 0L)
        val restored = tracker.update(listOf(point(0.5f)), 100L).single()
        assertEquals(LandmarkSource.OBSERVED, restored.source)
        assertEquals(0.5f, restored.x, 0.0001f)
    }

    @Test fun timestampGapResetsHistory() {
        val tracker = OcclusionAwarePoseTracker()
        tracker.update(listOf(point(0.3f)), 0L)
        val missing = tracker.update(listOf(point(0.3f, 0.1f)), 1500L).single()
        assertEquals(LandmarkSource.UNKNOWN, missing.source)
    }
}
