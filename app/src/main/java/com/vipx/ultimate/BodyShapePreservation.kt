package com.vipx.ultimate

/**
 * Quality gate for virtual try-on output.
 *
 * The baseline mask is captured before garment rendering. The garment coverage mask
 * excludes pixels legitimately replaced by cloth; all remaining visible body pixels
 * must retain their original silhouette. This class does not perform segmentation
 * or render clothing: it rejects outputs that violate the preservation contract.
 */
internal data class BodyShapeCheck(
    val passed: Boolean,
    val silhouetteIoU: Double,
    val maxLandmarkDrift: Double,
    val reason: String
)

internal object BodyShapePreservation {
    // Strict defaults. Coordinates are normalized to the image width/height.
    const val MIN_SILHOUETTE_IOU = 0.985
    const val MAX_LANDMARK_DRIFT = 0.015

    /**
     * Compares original and rendered person masks outside the garment area.
     *
     * Arrays must describe the same aligned frame. garmentCoverage=true means
     * the renderer is allowed to replace that pixel with garment pixels.
     * An invalid/missing mask fails closed; it never silently approves a frame.
     */
    fun evaluate(
        originalBody: BooleanArray,
        renderedBody: BooleanArray,
        garmentCoverage: BooleanArray,
        originalLandmarks: FloatArray,
        renderedLandmarks: FloatArray
    ): BodyShapeCheck {
        val size = originalBody.size
        if (size == 0 || renderedBody.size != size || garmentCoverage.size != size) {
            return failed("أقنعة الجسم غير متطابقة أو فارغة")
        }
        if (originalLandmarks.isEmpty() || originalLandmarks.size % 2 != 0 ||
            renderedLandmarks.size != originalLandmarks.size) {
            return failed("نقاط الجسم المرجعية ناقصة أو غير متطابقة")
        }

        var intersection = 0
        var union = 0
        for (i in 0 until size) {
            if (garmentCoverage[i]) continue
            val before = originalBody[i]
            val after = renderedBody[i]
            if (before && after) intersection++
            if (before || after) union++
        }
        // Empty visible region is not evidence of preservation.
        if (union == 0) return failed("لا توجد منطقة ظاهرة كافية للتحقق من شكل الجسم")

        val iou = intersection.toDouble() / union.toDouble()
        var maxDrift = 0.0
        for (i in originalLandmarks.indices step 2) {
            val ox = originalLandmarks[i]
            val oy = originalLandmarks[i + 1]
            val rx = renderedLandmarks[i]
            val ry = renderedLandmarks[i + 1]
            if (!ox.isFinite() || !oy.isFinite() || !rx.isFinite() || !ry.isFinite()) {
                return failed("إحداثيات نقاط الجسم غير صالحة")
            }
            val dx = rx - ox
            val dy = ry - oy
            val drift = kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
            if (drift > maxDrift) maxDrift = drift
        }

        val passed = iou >= MIN_SILHOUETTE_IOU && maxDrift <= MAX_LANDMARK_DRIFT
        val reason = when {
            passed -> "شكل الجسم محفوظ ضمن حدود التحقق"
            iou < MIN_SILHOUETTE_IOU -> "تغيّر محيط الجسم خارج مساحة الملابس"
            else -> "تحرّكت نقاط الجسم أكثر من الحد المسموح"
        }
        return BodyShapeCheck(passed, iou, maxDrift, reason)
    }

    private fun failed(reason: String) =
        BodyShapeCheck(false, 0.0, Double.POSITIVE_INFINITY, reason)
}
