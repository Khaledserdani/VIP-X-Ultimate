package com.vipx.ultimate

import org.junit.Assert.*
import org.junit.Test

class GarmentFitEngineTest {
    private val body = BodyMeasurements(112.0, 106.0, 118.0, 38.0, 66.0)

    @Test fun roomyGarmentFitsLargeBodyWithoutChangingMeasurements() {
        val garment = GarmentMeasurements(120.0, 114.0, 126.0, 44.0, 72.0)
        val result = GarmentFitEngine.evaluate(body, garment, FabricClass.WOVEN)
        assertTrue(result.fits)
        assertTrue(result.bodyMeasurementsUnchanged)
        assertEquals(112.0, body.chestCm, 0.0)
        assertEquals(106.0, body.waistCm, 0.0)
        assertEquals(118.0, body.hipCm, 0.0)
    }

    @Test fun undersizedNonStretchGarmentIsRejectedRatherThanSlimmingBody() {
        val garment = GarmentMeasurements(100.0, 94.0, 104.0, 34.0, 58.0)
        val result = GarmentFitEngine.evaluate(body, garment, FabricClass.WOVEN)
        assertFalse(result.fits)
        assertTrue(result.zones.any { it.status == "المقاس غير مناسب" })
        assertEquals(106.0, body.waistCm, 0.0)
    }

    @Test fun stretchySportsFabricAllowsControlledNegativeEase() {
        val garment = GarmentMeasurements(108.0, 102.0, 112.0, 36.0, 62.0)
        val result = GarmentFitEngine.evaluate(body, garment, FabricClass.SPORTS_STRETCH)
        assertTrue(result.fits)
    }

    @Test fun sizeRecommendationChoosesFirstActuallyCompatibleSize() {
        val tooSmall = GarmentMeasurements(100.0, 94.0, 104.0, 34.0, 58.0)
        val correct = GarmentMeasurements(120.0, 114.0, 126.0, 44.0, 72.0)
        val chosen = GarmentFitEngine.recommendSize(
            body, listOf("M" to tooSmall, "XXL" to correct), FabricClass.WOVEN
        )
        assertEquals("XXL", chosen)
    }

    @Test fun abdomenCircumferenceAndProjectionAreMeasuredIndependently() {
        val measuredBody = body.copy(abdomenCircumferenceCm = 124.0, abdomenProjectionCm = 18.0)
        val garment = GarmentMeasurements(
            chestCm = 120.0, waistCm = 114.0, hipCm = 126.0,
            upperArmCm = 44.0, thighCm = 72.0,
            abdomenCircumferenceCm = 130.0, abdomenProjectionCm = 22.0
        )
        val result = GarmentFitEngine.evaluate(measuredBody, garment, FabricClass.WOVEN)
        val circumference = result.zones.single { it.zone == FitZone.ABDOMEN_CIRCUMFERENCE }
        val projection = result.zones.single { it.zone == FitZone.ABDOMEN_PROJECTION }

        assertEquals(124.0, circumference.bodyCm, 0.0)
        assertEquals(130.0, circumference.garmentCm, 0.0)
        assertEquals(18.0, projection.bodyCm, 0.0)
        assertEquals(22.0, projection.garmentCm, 0.0)
        assertEquals(124.0, measuredBody.abdomenCircumferenceCm, 0.0)
        assertEquals(18.0, measuredBody.abdomenProjectionCm, 0.0)
    }

    @Test fun missingAbdomenMeasurementsAreNotInvented() {
        val result = GarmentFitEngine.evaluate(
            body,
            GarmentMeasurements(120.0, 114.0, 126.0, 44.0, 72.0),
            FabricClass.WOVEN
        )
        assertEquals("غير مقاس", result.zones.single {
            it.zone == FitZone.ABDOMEN_CIRCUMFERENCE
        }.status)
        assertEquals("غير مقاس", result.zones.single {
            it.zone == FitZone.ABDOMEN_PROJECTION
        }.status)
    }

    @Test fun invalidMeasurementsAreRejected() {
        val invalid = BodyMeasurements(0.0, 100.0, 100.0, 30.0, 50.0)
        assertFalse(runCatching {
            GarmentFitEngine.evaluate(
                invalid, GarmentMeasurements(120.0, 120.0, 120.0, 40.0, 60.0),
                FabricClass.KNIT
            )
        }.isSuccess)
    }
}
