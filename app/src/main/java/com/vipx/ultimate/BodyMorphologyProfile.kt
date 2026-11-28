package com.vipx.ultimate

/**
 * Measured body morphology used to fit garments to the person, never to reshape
 * the person. Values are centimetres unless explicitly stated otherwise.
 *
 * Measurements must come from a calibrated multi-view/depth workflow or be
 * entered by the user. A single RGB pose frame cannot provide true circumferences.
 */
internal data class BodyMorphologyProfile(
    val heightCm: Double? = null,
    val shoulderWidthCm: Double? = null,
    val chestCircumferenceCm: Double? = null,
    val underChestCircumferenceCm: Double? = null,
    val waistCircumferenceCm: Double? = null,
    val abdomenCircumferenceCm: Double? = null,
    val abdomenProjectionCm: Double? = null,
    val hipCircumferenceCm: Double? = null,
    val upperArmLeftCm: Double? = null,
    val upperArmRightCm: Double? = null,
    val thighLeftCm: Double? = null,
    val thighRightCm: Double? = null,
    val calfLeftCm: Double? = null,
    val calfRightCm: Double? = null,
    val torsoLengthCm: Double? = null,
    val inseamCm: Double? = null,
    val leftRightAsymmetry: Map<String, Double> = emptyMap(),
    val softTissueProfile: SoftTissueProfile = SoftTissueProfile.UNASSESSED,
    val muscleProfile: MuscleProfile = MuscleProfile.UNASSESSED,
    val posture: PostureProfile = PostureProfile.UNASSESSED,
    val measurementSource: MeasurementSource = MeasurementSource.UNVERIFIED,
    val confidence: Double = 0.0
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        val values = listOf(
            "height" to heightCm, "shoulder width" to shoulderWidthCm,
            "chest" to chestCircumferenceCm, "under chest" to underChestCircumferenceCm,
            "waist" to waistCircumferenceCm, "abdomen" to abdomenCircumferenceCm,
            "abdomen projection" to abdomenProjectionCm, "hips" to hipCircumferenceCm,
            "left upper arm" to upperArmLeftCm, "right upper arm" to upperArmRightCm,
            "left thigh" to thighLeftCm, "right thigh" to thighRightCm,
            "left calf" to calfLeftCm, "right calf" to calfRightCm,
            "torso length" to torsoLengthCm, "inseam" to inseamCm
        )
        values.forEach { (name, value) ->
            if (value != null && (!value.isFinite() || value <= 0.0)) errors += "$name must be positive"
        }
        if (!confidence.isFinite() || confidence !in 0.0..1.0) errors += "confidence must be 0..1"
        if (leftRightAsymmetry.values.any { !it.isFinite() || it !in -100.0..100.0 }) {
            errors += "asymmetry values must be finite centimetres"
        }
        return errors
    }

    /** No inferred or missing measurements are silently treated as exact. */
    fun isReadyForGarmentFit(): Boolean =
        validate().isEmpty() &&
            measurementSource != MeasurementSource.UNVERIFIED &&
            confidence >= 0.75 &&
            listOf(chestCircumferenceCm, waistCircumferenceCm,
                abdomenCircumferenceCm, hipCircumferenceCm).all { it != null }

    /**
     * Converts only sufficiently verified circumference measurements into the fit engine.
     * The larger left/right arm and thigh values provide conservative garment ease;
     * asymmetry remains available in this profile for side-specific rendering.
     */
    fun toBodyMeasurementsOrNull(): BodyMeasurements? {
        if (!isReadyForGarmentFit()) return null
        val leftArm = upperArmLeftCm ?: return null
        val rightArm = upperArmRightCm ?: return null
        val leftThigh = thighLeftCm ?: return null
        val rightThigh = thighRightCm ?: return null
        return BodyMeasurements(
            chestCm = chestCircumferenceCm!!,
            waistCm = waistCircumferenceCm!!,
            hipCm = hipCircumferenceCm!!,
            upperArmCm = maxOf(leftArm, rightArm),
            thighCm = maxOf(leftThigh, rightThigh),
            abdomenProjectionCm = abdomenProjectionCm ?: 0.0,
            abdomenCircumferenceCm = abdomenCircumferenceCm!!
        )
    }
}

internal enum class SoftTissueProfile {
    UNASSESSED,
    FIRM,
    NATURAL,
    SOFT,
    LOOSE,
    MIXED
}

internal enum class MuscleProfile {
    UNASSESSED,
    LOW_DEFINITION,
    NATURAL_TONE,
    DEFINED,
    HIGHLY_DEFINED,
    MIXED
}

internal enum class PostureProfile {
    UNASSESSED,
    NEUTRAL,
    FORWARD_SHOULDERS,
    ARCHED_BACK,
    UNEVEN_SHOULDERS,
    CUSTOM
}

internal enum class MeasurementSource {
    UNVERIFIED,
    USER_ENTERED,
    CALIBRATED_TAPE,
    MULTI_VIEW_ESTIMATE,
    DEPTH_SENSOR
}

/**
 * A garment-fit instruction derived from measured anatomy.
 * Positive ease means extra garment room; negative ease means stretch tension.
 */
internal data class GarmentFitAllowance(
    val chestEaseCm: Double,
    val waistEaseCm: Double,
    val abdomenEaseCm: Double,
    val hipEaseCm: Double,
    val armEaseCm: Double,
    val thighEaseCm: Double
) {
    fun validate(): Boolean = listOf(
        chestEaseCm, waistEaseCm, abdomenEaseCm,
        hipEaseCm, armEaseCm, thighEaseCm
    ).all { it.isFinite() && it in -8.0..20.0 }
}
