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
        assertEquals(80, mesh.vertices.size)
        assertEquals(128, mesh.triangles.size)
        assertTrue(mesh.triangles.all { face ->
            face.size == 3 && face.all { it in mesh.vertices.indices } &&
                face.toSet().size == 3
        })
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
