package com.vipx.ultimate

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/** Source of each cross-section; estimated values must never masquerade as measurements. */
internal enum class SurfaceSectionSource { CALIBRATED_MEASUREMENT, USER_MEASUREMENT, UNAVAILABLE }

/** Elliptical cross-section in centimetres. halfWidth is left/right; halfDepth is front/back. */
internal data class BodySurfaceSection(
    val name: String,
    val heightRatio: Double,
    val halfWidthCm: Double?,
    val halfDepthCm: Double?,
    val source: SurfaceSectionSource,
    val confidence: Double
) {
    val valid: Boolean get() = heightRatio in 0.0..1.0 &&
        confidence in 0.0..1.0 &&
        (halfWidthCm == null || halfWidthCm.isFinite() && halfWidthCm > 0.0) &&
        (halfDepthCm == null || halfDepthCm.isFinite() && halfDepthCm > 0.0)
}

/**
 * Measurement-driven torso surface scaffold.
 * It is a parametric cross-section model, not a scan or a complete animated 3D mesh.
 * No body dimensions are inferred from a single RGB silhouette.
 */
internal data class BodySurfaceModel(
    val sections: List<BodySurfaceSection>,
    val ready: Boolean,
    val limitations: List<String>
) {
    companion object {
        fun fromMeasurements(profile: BodyMorphologyProfile): BodySurfaceModel {
            val validErrors = profile.validate()
            if (validErrors.isNotEmpty()) return BodySurfaceModel(
                emptyList(), false, listOf("قياسات غير صالحة: ${validErrors.joinToString()}")
            )
            val measured = profile.measurementSource == MeasurementSource.CALIBRATED_TAPE ||
                profile.measurementSource == MeasurementSource.DEPTH_SENSOR ||
                profile.measurementSource == MeasurementSource.USER_ENTERED
            if (!measured || profile.confidence < 0.75) return BodySurfaceModel(
                emptyList(), false, listOf("يلزم قياس موثوق لمحيطات الجسم وعمقها")
            )
            val source = when (profile.measurementSource) {
                MeasurementSource.CALIBRATED_TAPE, MeasurementSource.DEPTH_SENSOR -> SurfaceSectionSource.CALIBRATED_MEASUREMENT
                MeasurementSource.USER_ENTERED -> SurfaceSectionSource.USER_MEASUREMENT
                else -> SurfaceSectionSource.UNAVAILABLE
            }
            val sections = listOf(
                section("الصدر", 0.30, profile.chestCircumferenceCm, profile.chestDepthCm, source, profile.confidence),
                section("أسفل الصدر", 0.39, profile.underChestCircumferenceCm, profile.underChestDepthCm, source, profile.confidence),
                section("الخصر", 0.56, profile.waistCircumferenceCm, profile.waistDepthCm, source, profile.confidence),
                section("البطن", 0.68, profile.abdomenCircumferenceCm, profile.abdomenDepthCm, source, profile.confidence),
                section("الوركان", 0.84, profile.hipCircumferenceCm, profile.hipDepthCm, source, profile.confidence)
            )
            val missing = sections.filter { it.halfWidthCm == null || it.halfDepthCm == null }.map { it.name }
            return BodySurfaceModel(
                sections, missing.isEmpty(),
                buildList {
                    if (missing.isNotEmpty()) add("قياسات ناقصة للمقاطع: ${missing.joinToString()}")
                    add("لا يتم تخمين العمق الأمامي/الخلفي؛ يلزم قياس عمق مستقل لكل مقطع")
                    add("النموذج مقاطع جسمية؛ ليس شبكة ثلاثية الأبعاد نهائية")
                }
            )
        }

        private fun section(
            name: String, y: Double, circumference: Double?, depth: Double?,
            source: SurfaceSectionSource, confidence: Double
        ): BodySurfaceSection {
            if (circumference == null || depth == null || circumference <= 0.0 || depth <= 0.0)
                return BodySurfaceSection(name, y, null, depth?.div(2.0), SurfaceSectionSource.UNAVAILABLE, 0.0)
            val halfDepth = depth / 2.0
            val halfWidth = solveHalfWidth(circumference, halfDepth)
            return BodySurfaceSection(name, y, halfWidth, halfDepth, source, confidence)
        }

        /** Binary-searches ellipse half-width using Ramanujan's circumference approximation. */
        private fun solveHalfWidth(circumference: Double, halfDepth: Double): Double {
            fun perimeter(a: Double): Double {
                val b = halfDepth
                val h = ((a - b) * (a - b)) / ((a + b) * (a + b))
                return PI * (a + b) * (1.0 + 3.0 * h / (10.0 + sqrt(4.0 - 3.0 * h)))
            }
            var low = 0.01
            var high = circumference
            repeat(80) {
                val mid = (low + high) / 2.0
                if (perimeter(mid) < circumference) low = mid else high = mid
            }
            return (low + high) / 2.0
        }
    }
}
