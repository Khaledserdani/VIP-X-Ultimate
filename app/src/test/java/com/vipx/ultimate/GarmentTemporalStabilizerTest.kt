package com.vipx.ultimate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GarmentTemporalStabilizerTest {
    private fun mesh(x: Float, valid: Boolean = true) = GarmentOverlayMesh(
        vertices = listOf(
            GarmentOverlayVertex(0.2f, 0.2f, true),
            GarmentOverlayVertex(x, 0.5f, false),
            GarmentOverlayVertex(0.8f, 0.2f, true)
        ),
        bodyAnchorIndices = listOf(0, 2),
        valid = valid,
        reason = "test"
    )

    @Test fun smoothsFreeGarmentVerticesButKeepsBodyAnchorsExact() {
        val previous = mesh(0.4f)
        val current = mesh(0.8f)
        val result = GarmentTemporalStabilizer.smooth(previous, current, 0.5f)
        assertEquals(0.6f, result.vertices[1].x, 0.0001f)
        assertEquals(current.vertices[0], result.vertices[0])
        assertEquals(current.vertices[2], result.vertices[2])
    }

    @Test fun firstFrameAndInvalidMeshPassThroughUnchanged() {
        val current = mesh(0.8f)
        assertSame(current, GarmentTemporalStabilizer.smooth(null, current))
        val invalid = mesh(0.8f, false)
        assertSame(invalid, GarmentTemporalStabilizer.smooth(mesh(0.2f), invalid))
    }

    @Test fun incompatibleTopologyResetsTemporalHistory() {
        val previous = mesh(0.2f)
        val current = mesh(0.8f).copy(vertices = listOf(GarmentOverlayVertex(0.8f, 0.5f)))
        val result = GarmentTemporalStabilizer.smooth(previous, current)
        assertTrue(result === current)
    }
}
