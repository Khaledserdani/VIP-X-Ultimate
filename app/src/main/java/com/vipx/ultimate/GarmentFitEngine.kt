package com.vipx.ultimate

import kotlin.math.max

/**
 * Measurement-driven garment fitting core.
 * Garment dimensions adapt to measured body dimensions; body dimensions are read-only.
 * Measurements are centimetres. Abdomen projection is an optional front-to-back depth.
 */
internal enum class FitZone { CHEST, WAIST, ABDOMEN_CIRCUMFERENCE, ABDOMEN_PROJECTION, HIP, UPPER_ARM, THIGH }

internal enum class FabricClass(val stretchRatio: Double, val minimumEaseCm: Double) {
    WOVEN(0.02, 4.0),
    KNIT(0.18, 2.0),
    SPORTS_STRETCH(0.32, 1.0),
    CHIFFON_LINED(0.03, 5.0),
    LACE_LINED(0.05, 4.0);

    val thicknessMm: Double get() = when (this) {
        WOVEN -> 0.7
        KNIT -> 1.0
        SPORTS_STRETCH -> 0.8
        CHIFFON_LINED -> 0.5
        LACE_LINED -> 0.9
    }

    val drapeFlex: Double get() = when (this) {
        WOVEN -> 0.42
        KNIT -> 0.68
        SPORTS_STRETCH -> 0.74
        CHIFFON_LINED -> 0.92
        LACE_LINED -> 0.78
    }
}

internal data class BodyMeasurements(
    val chestCm: Double,
    val waistCm: Double,
    val hipCm: Double,
    val upperArmCm: Double,
    val thighCm: Double,
    val abdomenProjectionCm: Double = 0.0,
    val abdomenCircumferenceCm: Double = 0.0
) {
    fun value(zone: FitZone): Double = when (zone) {
        FitZone.CHEST -> chestCm
        FitZone.WAIST -> waistCm
        FitZone.ABDOMEN_CIRCUMFERENCE -> abdomenCircumferenceCm
        FitZone.ABDOMEN_PROJECTION -> abdomenProjectionCm
        FitZone.HIP -> hipCm
        FitZone.UPPER_ARM -> upperArmCm
        FitZone.THIGH -> thighCm
    }

    fun valid(): Boolean =
        listOf(chestCm, waistCm, hipCm, upperArmCm, thighCm)
            .all { it.isFinite() && it > 0.0 && it < 300.0 } &&
            abdomenProjectionCm.isFinite() && abdomenProjectionCm >= 0.0 && abdomenProjectionCm < 300.0 &&
            abdomenCircumferenceCm.isFinite() && abdomenCircumferenceCm >= 0.0 && abdomenCircumferenceCm < 300.0
}

internal data class GarmentMeasurements(
    val chestCm: Double,
    val waistCm: Double,
    val hipCm: Double,
    val upperArmCm: Double,
    val thighCm: Double,
    val abdomenProjectionCm: Double = 0.0,
    val abdomenCircumferenceCm: Double = 0.0
) {
    fun value(zone: FitZone): Double = when (zone) {
        FitZone.CHEST -> chestCm
        FitZone.WAIST -> waistCm
        FitZone.ABDOMEN_CIRCUMFERENCE -> abdomenCircumferenceCm
        FitZone.ABDOMEN_PROJECTION -> abdomenProjectionCm
        FitZone.HIP -> hipCm
        FitZone.UPPER_ARM -> upperArmCm
        FitZone.THIGH -> thighCm
    }

    fun valid(): Boolean =
        listOf(chestCm, waistCm, hipCm, upperArmCm, thighCm)
            .all { it.isFinite() && it > 0.0 && it < 400.0 } &&
            abdomenProjectionCm.isFinite() && abdomenProjectionCm >= 0.0 && abdomenProjectionCm < 400.0 &&
            abdomenCircumferenceCm.isFinite() && abdomenCircumferenceCm >= 0.0 && abdomenCircumferenceCm < 400.0
}

internal data class ZoneFit(
    val zone: FitZone,
    val bodyCm: Double,
    val garmentCm: Double,
    val easeCm: Double,
    /** Suggested garment-only circumference multiplier. Body is never scaled. */
    val garmentScale: Double,
    val status: String
)

internal data class GarmentFitResult(
    val fits: Boolean,
    val zones: List<ZoneFit>,
    val summary: String,
    val bodyMeasurementsUnchanged: Boolean = true
)

internal object GarmentFitEngine {
    fun evaluate(
        body: BodyMeasurements,
        garment: GarmentMeasurements,
        fabric: FabricClass
    ): GarmentFitResult {
        require(body.valid()) { "قياسات الجسم ناقصة أو غير صالحة" }
        require(garment.valid()) { "قياسات اللباس ناقصة أو غير صالحة" }

        val zones = FitZone.values().map { zone ->
            val bodyCm = body.value(zone)
            val garmentCm = garment.value(zone)
            // Optional abdomen circumference/depth are assessed independently.
            if ((zone == FitZone.ABDOMEN_CIRCUMFERENCE || zone == FitZone.ABDOMEN_PROJECTION) && (bodyCm == 0.0 || garmentCm == 0.0)) {
                return@map ZoneFit(zone, 0.0, 0.0, 0.0, 1.0, "غير مقاس")
            }
            val ease = garmentCm - bodyCm
            val requiredEase = fabric.minimumEaseCm
            val stretchCapacity = bodyCm * fabric.stretchRatio
            val scale = if (ease >= requiredEase || garmentCm <= 0.0) 1.0
                else (bodyCm + requiredEase) / garmentCm
            val status = when {
                ease >= requiredEase -> "مريح"
                ease >= -stretchCapacity -> "ضيق؛ يحتاج ضبطاً"
                else -> "المقاس غير مناسب"
            }
            ZoneFit(zone, bodyCm, garmentCm, ease, max(1.0, scale), status)
        }
        val fits = zones.all { it.status != "المقاس غير مناسب" }
        val summary = if (fits) {
            if (zones.any { it.status == "ضيق؛ يحتاج ضبطاً" }) {
                "قابل للارتداء مع ضبط اتساع اللباس"
            } else {
                "المقاس مناسب وفق القياسات"
            }
        } else {
            "المقاس غير مناسب؛ اختر مقاساً أو قصة أوسع"
        }
        return GarmentFitResult(fits, zones, summary)
    }

    /** Returns the first catalog size that fits without reshaping the body. */
    fun recommendSize(
        body: BodyMeasurements,
        sizes: List<Pair<String, GarmentMeasurements>>,
        fabric: FabricClass
    ): String? = sizes.firstOrNull { (_, garment) ->
        runCatching { evaluate(body, garment, fabric).fits }.getOrDefault(false)
    }?.first
}
