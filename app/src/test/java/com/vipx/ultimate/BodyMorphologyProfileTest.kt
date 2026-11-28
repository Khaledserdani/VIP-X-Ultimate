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
}
