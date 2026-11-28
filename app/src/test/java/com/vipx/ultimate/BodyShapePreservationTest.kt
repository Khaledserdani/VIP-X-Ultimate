package com.vipx.ultimate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyShapePreservationTest {
    @Test
    fun unchangedBodyPasses() {
        val body = BooleanArray(100) { it in 20..79 }
        val result = BodyShapePreservation.evaluate(
            body, body.copyOf(), BooleanArray(100),
            floatArrayOf(0.25f, 0.3f, 0.5f, 0.7f),
            floatArrayOf(0.25f, 0.3f, 0.5f, 0.7f)
        )
        assertTrue(result.passed)
    }

    @Test
    fun slimmingOutsideGarmentFails() {
        val original = BooleanArray(100) { it in 20..79 }
        val slimmed = original.copyOf().also { mask ->
            for (i in 35..44) mask[i] = false
        }
        val result = BodyShapePreservation.evaluate(
            original, slimmed, BooleanArray(100),
            floatArrayOf(0.25f, 0.3f, 0.5f, 0.7f),
            floatArrayOf(0.25f, 0.3f, 0.5f, 0.7f)
        )
        assertFalse(result.passed)
    }

    @Test
    fun missingOrMismatchedMasksFailClosed() {
        val result = BodyShapePreservation.evaluate(
            BooleanArray(0), BooleanArray(0), BooleanArray(0),
            floatArrayOf(0.2f, 0.3f), floatArrayOf(0.2f, 0.3f)
        )
        assertFalse(result.passed)
    }

    @Test
    fun landmarkMovementFails() {
        val body = BooleanArray(16) { true }
        val result = BodyShapePreservation.evaluate(
            body, body.copyOf(), BooleanArray(16),
            floatArrayOf(0.2f, 0.2f), floatArrayOf(0.24f, 0.2f)
        )
        assertFalse(result.passed)
    }
}
