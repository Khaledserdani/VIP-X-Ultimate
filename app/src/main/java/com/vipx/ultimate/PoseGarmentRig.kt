package com.vipx.ultimate

/**
 * Builds a garment-only 2D overlay mesh from MediaPipe pose landmarks.
 * Coordinates remain normalized to the source frame; source body landmarks are immutable.
 * This is a geometric fitting scaffold, not a photorealistic renderer or cloth simulation.
 */
internal data class GarmentOverlayVertex(
    val x: Float,
    val y: Float,
    val anchor: Boolean = false
)

internal data class GarmentOverlayMesh(
    val vertices: List<GarmentOverlayVertex>,
    val bodyAnchorIndices: List<Int>,
    val valid: Boolean,
    val reason: String
)

internal object PoseGarmentRig {
    private const val MIN_CONFIDENCE = 0.55f

    /**
     * Landmark indices: shoulders 11/12, elbows 13/14, wrists 15/16,
     * hips 23/24. Only a front-facing upper-body frame is accepted.
     */
    fun build(frame: TrackedBodyFrame): GarmentOverlayMesh {
        if (!frame.hasReliablePose(MIN_CONFIDENCE)) return invalid("وضعية الجسم غير موثوقة")
        val required = intArrayOf(11, 12, 13, 14, 15, 16, 23, 24)
        val points = required.associateWith { frame.imageLandmarks[it] }
        if (points.values.any { !it.x.isFinite() || !it.y.isFinite() ||
                    it.x !in 0f..1f || it.y !in 0f..1f || it.confidence < MIN_CONFIDENCE }) {
            return invalid("بعض نقاط الكتفين أو الذراعين أو الوركين غير واضحة")
        }
        val leftShoulder = points.getValue(11)
        val rightShoulder = points.getValue(12)
        val leftHip = points.getValue(23)
        val rightHip = points.getValue(24)
        if (leftShoulder.y >= leftHip.y || rightShoulder.y >= rightHip.y) {
            return invalid("اتجاه الجذع غير صالح لتركيب القالب")
        }
        val shoulderWidth = kotlin.math.abs(rightShoulder.x - leftShoulder.x)
        val hipWidth = kotlin.math.abs(rightHip.x - leftHip.x)
        if (shoulderWidth < 0.025f || hipWidth < 0.02f) return invalid("المسافة بين نقاط الجسم صغيرة جداً")

        val shoulderMidX = (leftShoulder.x + rightShoulder.x) / 2f
        val shoulderMidY = (leftShoulder.y + rightShoulder.y) / 2f
        val hipMidX = (leftHip.x + rightHip.x) / 2f
        val hipMidY = (leftHip.y + rightHip.y) / 2f
        val torsoHeight = hipMidY - shoulderMidY
        val waistY = shoulderMidY + torsoHeight * 0.58f
        val landmarkWaistHalfWidth = (shoulderWidth * 0.34f).coerceAtLeast(hipWidth * 0.48f)
        // Use the dense foreground silhouette as a second contour signal, not as a body-measurement claim.
        val silhouette = frame.silhouette?.takeIf { it.valid }
        val silhouetteWaistHalfWidth = silhouette?.let { mask ->
            val row = mask.rowWidths[(0.58f * (mask.rowWidths.size - 1)).toInt()]
            val maskBoxWidth = (mask.right - mask.left + 1).toFloat()
            row * maskBoxWidth / frame.imageWidth.toFloat() * 0.5f
        }
        val waistHalfWidth = if (silhouetteWaistHalfWidth != null &&
            silhouetteWaistHalfWidth.isFinite() && silhouetteWaistHalfWidth > 0f) {
            // Conservative blend: landmarks keep the fit stable while the pixel mask adapts the visible contour.
            landmarkWaistHalfWidth * 0.65f + minOf(silhouetteWaistHalfWidth, landmarkWaistHalfWidth * 1.45f) * 0.35f
        } else landmarkWaistHalfWidth

        val hipHalfWidth = silhouette?.let { mask ->
            val row = mask.rowWidths[(0.76f * (mask.rowWidths.size - 1)).toInt()]
            row * (mask.right - mask.left + 1).toFloat() / frame.imageWidth.toFloat() * 0.5f
        }?.takeIf { it.isFinite() && it > 0f }?.let {
            hipWidth * 0.35f + minOf(it, hipWidth * 0.75f) * 0.65f
        } ?: hipWidth * 0.5f
        val vertices = listOf(
            GarmentOverlayVertex(leftShoulder.x, leftShoulder.y, true),
            GarmentOverlayVertex(rightShoulder.x, rightShoulder.y, true),
            GarmentOverlayVertex(points.getValue(13).x, points.getValue(13).y, true),
            GarmentOverlayVertex(points.getValue(14).x, points.getValue(14).y, true),
            GarmentOverlayVertex(points.getValue(15).x, points.getValue(15).y, true),
            GarmentOverlayVertex(points.getValue(16).x, points.getValue(16).y, true),
            GarmentOverlayVertex(shoulderMidX - waistHalfWidth, waistY),
            GarmentOverlayVertex(shoulderMidX + waistHalfWidth, waistY),
            GarmentOverlayVertex(hipMidX - hipHalfWidth, leftHip.y, true),
            GarmentOverlayVertex(hipMidX + hipHalfWidth, rightHip.y, true),
            GarmentOverlayVertex(hipMidX, hipMidY)
        )
        return GarmentOverlayMesh(
            vertices = vertices,
            bodyAnchorIndices = listOf(0, 1, 2, 3, 4, 5, 8, 9),
            valid = true,
            reason = "قالب هندسي أولي جاهز؛ شكل الجسم الأصلي لم يتغير"
        )
    }

    private fun invalid(reason: String) = GarmentOverlayMesh(emptyList(), emptyList(), false, reason)
}
