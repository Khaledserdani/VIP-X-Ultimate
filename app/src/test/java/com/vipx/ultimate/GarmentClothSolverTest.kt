package com.vipx.ultimate

import org.junit.Assert.*
import org.junit.Test

class GarmentClothSolverTest {
    @Test fun fixedShoulderAnchorDoesNotMove() {
        val anchor = ClothPoint(0.25, 0.10)
        val vertices = listOf(
            ClothVertex(anchor, anchor, pinned = true),
            ClothVertex(ClothPoint(0.25, 0.30), ClothPoint(0.25, 0.29))
        )
        val frame = GarmentClothSolver.step(
            vertices, listOf(ClothLink(0, 1, 0.20)), gravityY = 0.001,
            fixedAnchors = mapOf(0 to anchor)
        )
        assertTrue(frame.finite)
        assertEquals(anchor, frame.vertices[0].position)
    }

    @Test fun clothVerticesMoveUnderGravityButBodyAnchorStaysFixed() {
        val anchor = ClothPoint(0.5, 0.1)
        val vertices = listOf(
            ClothVertex(anchor, anchor, pinned = true),
            ClothVertex(ClothPoint(0.5, 0.3), ClothPoint(0.5, 0.3))
        )
        val frame = GarmentClothSolver.step(
            vertices, listOf(ClothLink(0, 1, 0.20)), gravityY = 0.002,
            fixedAnchors = mapOf(0 to anchor)
        )
        assertTrue(frame.finite)
        assertEquals(anchor, frame.vertices[0].position)
        assertTrue(frame.vertices[1].position.y > 0.3)
    }

    @Test fun invalidMeshIsRejected() {
        val v = listOf(ClothVertex(ClothPoint(0.0,0.0), ClothPoint(0.0,0.0)))
        assertFalse(runCatching {
            GarmentClothSolver.step(v, listOf(ClothLink(0, 2, 1.0)), 0.01)
        }.isSuccess)
    }
}
