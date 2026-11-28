package com.vipx.ultimate

import org.junit.Assert.*
import org.junit.Test

class BodySurfaceMeshTest {
    private fun model(): BodySurfaceModel = BodySurfaceModel.fromMeasurements(
        BodyMorphologyProfile(
            chestCircumferenceCm = 96.0, underChestCircumferenceCm = 82.0,
            waistCircumferenceCm = 78.0, abdomenCircumferenceCm = 88.0,
            hipCircumferenceCm = 102.0, chestDepthCm = 24.0,
            underChestDepthCm = 21.0, waistDepthCm = 22.0,
            abdomenDepthCm = 25.0, hipDepthCm = 27.0,
            measurementSource = MeasurementSource.CALIBRATED_TAPE,
            confidence = 0.92
        )
    )

    @Test fun buildsClosedRingsAndConnectsAdjacentSections() {
        val mesh = BodySurfaceMesh.fromModel(model(), 16)!!
        assertEquals(5, mesh.ringCount)
        assertEquals(16, mesh.segmentsPerRing)
        assertEquals(82, mesh.vertices.size)
        assertEquals(160, mesh.triangles.size)
        assertTrue(mesh.triangles.all { face ->
            face.size == 3 && face.all { it in mesh.vertices.indices } &&
                face.toSet().size == 3
        })
    }

    @Test fun producesWatertightClosedSurfaceWithEveryEdgeUsedTwice() {
        val mesh = BodySurfaceMesh.fromModel(model(), 16)!!
        val edgeUse = mutableMapOf<Pair<Int, Int>, Int>()
        mesh.triangles.forEach { face ->
            for (i in 0..2) {
                val a = face[i]
                val b = face[(i + 1) % 3]
                val edge = if (a < b) a to b else b to a
                edgeUse[edge] = (edgeUse[edge] ?: 0) + 1
            }
        }
        assertTrue(edgeUse.isNotEmpty())
        assertTrue("Every mesh edge must belong to exactly two triangles", edgeUse.values.all { it == 2 })
        assertEquals("bottom-cap", mesh.vertices[mesh.vertices.size - 2].sectionName)
        assertEquals("top-cap", mesh.vertices.last().sectionName)
    }

    @Test fun carriesMeasurementProvenanceToEveryVertex() {
        val mesh = BodySurfaceMesh.fromModel(model())!!
        assertTrue(mesh.vertices.all {
            it.source == SurfaceSectionSource.CALIBRATED_MEASUREMENT &&
                it.confidence == 0.92 && it.sectionName.isNotBlank()
        })
    }

    @Test fun refusesIncompleteOrLowResolutionInput() {
        val incomplete = BodySurfaceModel.fromMeasurements(
            BodyMorphologyProfile(
                chestCircumferenceCm = 96.0, chestDepthCm = 24.0,
                measurementSource = MeasurementSource.CALIBRATED_TAPE,
                confidence = 0.9
            )
        )
        assertNull(BodySurfaceMesh.fromModel(incomplete))
        assertNull(BodySurfaceMesh.fromModel(model(), 6))
    }
}
