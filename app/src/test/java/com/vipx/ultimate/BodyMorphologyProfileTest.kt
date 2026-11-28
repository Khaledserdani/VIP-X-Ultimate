package com.vipx.ultimate

import org.junit.Assert.*
import org.junit.Test

class BodyMorphologyProfileTest {
    @Test fun incompleteOrUnverifiedProfileIsNotReadyForFit() {
        val profile = BodyMorphologyProfile(
            chestCircumferenceCm = 96.0,
            waistCircumferenceCm = 82.0,
            abdomenCircumferenceCm = 90.0,
            hipCircumferenceCm = 100.0,
            confidence = 0.95
        )
        assertFalse(profile.isReadyForGarmentFit())
    }

    @Test fun calibratedCompleteProfileCanBeUsedForFit() {
        val profile = BodyMorphologyProfile(
            heightCm = 170.0,
            shoulderWidthCm = 42.0,
            chestCircumferenceCm = 96.0,
            waistCircumferenceCm = 82.0,
            abdomenCircumferenceCm = 90.0,
            abdomenProjectionCm = 18.0,
            hipCircumferenceCm = 100.0,
            upperArmLeftCm = 29.0,
            upperArmRightCm = 30.0,
            thighLeftCm = 55.0,
            thighRightCm = 56.0,
            softTissueProfile = SoftTissueProfile.SOFT,
            muscleProfile = MuscleProfile.NATURAL_TONE,
            measurementSource = MeasurementSource.CALIBRATED_TAPE,
            confidence = 0.92
        )
        assertTrue(profile.validate().isEmpty())
        assertTrue(profile.isReadyForGarmentFit())
    }

    @Test fun negativeAndNonFiniteMeasurementsAreRejected() {
        val profile = BodyMorphologyProfile(
            chestCircumferenceCm = -2.0,
            waistCircumferenceCm = Double.NaN,
            confidence = 1.2
        )
        assertTrue(profile.validate().isNotEmpty())
        assertFalse(profile.isReadyForGarmentFit())
    }

    @Test fun garmentEaseMustStayWithinSafeBounds() {
        assertTrue(GarmentFitAllowance(4.0, 3.0, 5.0, 4.0, 2.0, 3.0).validate())
        assertFalse(GarmentFitAllowance(-20.0, 3.0, 5.0, 4.0, 2.0, 3.0).validate())
    }
    @Test fun verifiedProfileMapsIndependentAbdomenMeasurementsAndConservativeLimbs() {
        val profile = BodyMorphologyProfile(
            chestCircumferenceCm = 104.0,
            waistCircumferenceCm = 92.0,
            abdomenCircumferenceCm = 101.0,
            abdomenProjectionCm = 17.0,
            hipCircumferenceCm = 110.0,
            upperArmLeftCm = 31.0,
            upperArmRightCm = 33.0,
            thighLeftCm = 57.0,
            thighRightCm = 59.0,
            measurementSource = MeasurementSource.CALIBRATED_TAPE,
            confidence = 0.9
        )
        val measurements = profile.toBodyMeasurementsOrNull()
        assertNotNull(measurements)
        assertEquals(101.0, measurements!!.abdomenCircumferenceCm, 0.0)
        assertEquals(17.0, measurements.abdomenProjectionCm, 0.0)
        assertEquals(33.0, measurements.upperArmCm, 0.0)
        assertEquals(59.0, measurements.thighCm, 0.0)
    }

    @Test fun profileWithoutBothArmAndThighSidesCannotProduceFitMeasurements() {
        val profile = BodyMorphologyProfile(
            chestCircumferenceCm = 104.0,
            waistCircumferenceCm = 92.0,
            abdomenCircumferenceCm = 101.0,
            hipCircumferenceCm = 110.0,
            upperArmLeftCm = 31.0,
            thighLeftCm = 57.0,
            measurementSource = MeasurementSource.CALIBRATED_TAPE,
            confidence = 0.9
        )
        assertNull(profile.toBodyMeasurementsOrNull())
    }

}
