package com.vipx.ultimate

import org.junit.Assert.*
import org.junit.Test

class BodySurfaceModelTest {
    private fun profile(source: MeasurementSource = MeasurementSource.CALIBRATED_TAPE) =
        BodyMorphologyProfile(
            chestCircumferenceCm = 96.0,
            underChestCircumferenceCm = 82.0,
            waistCircumferenceCm = 78.0,
            abdomenCircumferenceCm = 88.0,
            hipCircumferenceCm = 102.0,
            chestDepthCm = 24.0,
            underChestDepthCm = 21.0,
            waistDepthCm = 22.0,
            abdomenDepthCm = 25.0,
            hipDepthCm = 27.0,
            measurementSource = source,
            confidence = 0.92
        )

    @Test fun buildsOrderedTorsoSectionsFromMeasuredDimensions() {
        val model = BodySurfaceModel.fromMeasurements(profile())
        assertTrue(model.ready)
        assertEquals(5, model.sections.size)
        assertTrue(model.sections.zipWithNext().all { it.first.heightRatio < it.second.heightRatio })
        assertTrue(model.sections.all { it.halfWidthCm!! > 0.0 && it.halfDepthCm!! > 0.0 })
    }

    @Test fun rejectsUnverifiedSingleViewGuess() {
        val model = BodySurfaceModel.fromMeasurements(
            profile(MeasurementSource.MULTI_VIEW_ESTIMATE).copy(confidence = 0.6)
        )
        assertFalse(model.ready)
        assertTrue(model.sections.isEmpty())
    }

    @Test fun missingDepthIsNotInvented() {
        val model = BodySurfaceModel.fromMeasurements(profile().copy(abdomenDepthCm = null))
        assertFalse(model.ready)
        assertNull(model.sections.first { it.name == "البطن" }.halfWidthCm)
        assertTrue(model.limitations.any { it.contains("قياسات ناقصة") })
    }

    @Test fun userMeasurementsRemainClearlyAttributed() {
        val model = BodySurfaceModel.fromMeasurements(profile(MeasurementSource.USER_ENTERED))
        assertTrue(model.ready)
        assertTrue(model.sections.all { it.source == SurfaceSectionSource.USER_MEASUREMENT })
    }
}
