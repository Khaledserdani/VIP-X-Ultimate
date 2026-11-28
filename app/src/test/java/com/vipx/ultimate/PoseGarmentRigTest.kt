package com.vipx.ultimate

import org.junit.Assert.*
import org.junit.Test

class PoseGarmentRigTest {
    private fun frame(): TrackedBodyFrame {
        val points = MutableList(33) { index ->
            BodyLandmark(index, 0.5f, 0.5f, 0f, 0.95f)
        }
        points[11] = BodyLandmark(11, 0.40f, 0.20f, 0f, 0.99f)
        points[12] = BodyLandmark(12, 0.60f, 0.20f, 0f, 0.99f)
        points[13] = BodyLandmark(13, 0.32f, 0.34f, 0f, 0.95f)
        points[14] = BodyLandmark(14, 0.68f, 0.34f, 0f, 0.95f)
        points[15] = BodyLandmark(15, 0.28f, 0.48f, 0f, 0.95f)
        points[16] = BodyLandmark(16, 0.72f, 0.48f, 0f, 0.95f)
        points[23] = BodyLandmark(23, 0.43f, 0.62f, 0f, 0.99f)
        points[24] = BodyLandmark(24, 0.57f, 0.62f, 0f, 0.99f)
        return TrackedBodyFrame(100L, 1000, 1000, points, emptyList(), false)
    }

    @Test fun createsOverlayAnchoredToBodyLandmarks() {
        val original = frame()
        val before = original.imageLandmarks.toList()
        val mesh = PoseGarmentRig.build(original)
        assertTrue(mesh.valid)
        assertEquals(11, mesh.vertices.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 8, 9), mesh.bodyAnchorIndices)
        assertEquals(before, original.imageLandmarks)
    }

    @Test fun rejectsLowConfidenceBodyAnchors() {
        val original = frame()
        val points = original.imageLandmarks.toMutableList()
        points[11] = points[11].copy(confidence = 0.1f)
        val weak = original.copy(imageLandmarks = points)
        assertFalse(PoseGarmentRig.build(weak).valid)
    }

    @Test fun rejectsInvalidNormalizedCoordinates() {
        val original = frame()
        val points = original.imageLandmarks.toMutableList()
        points[12] = points[12].copy(x = 1.4f)
        assertFalse(PoseGarmentRig.build(original.copy(imageLandmarks = points)).valid)
    }
}
