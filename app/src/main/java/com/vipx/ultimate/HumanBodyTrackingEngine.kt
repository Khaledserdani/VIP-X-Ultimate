package com.vipx.ultimate

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

/**
 * On-device pose tracker. Add pose_landmarker_full.task to app/src/main/assets
 * before runtime use. Pose landmarks are not exact circumferences or a dense surface.
 */
internal class HumanBodyTrackingEngine(
    context: Context,
    modelAssetPath: String = "pose_landmarker_full.task"
) : AutoCloseable {
    private val landmarker: PoseLandmarker

    init {
        val base = BaseOptions.builder().setModelAssetPath(modelAssetPath).build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .setOutputSegmentationMasks(true)
            .setMinPoseDetectionConfidence(0.55f)
            .setMinPosePresenceConfidence(0.55f)
            .setMinTrackingConfidence(0.60f)
            .build()
        landmarker = PoseLandmarker.createFromOptions(context, options)
    }

    fun detect(bitmap: Bitmap, timestampMs: Long): TrackedBodyFrame? {
        require(timestampMs >= 0L)
        val result = landmarker.detectForVideo(BitmapImageBuilder(bitmap).build(), timestampMs)
        val screen = result.landmarks().firstOrNull() ?: return null
        val world = result.worldLandmarks().firstOrNull()
        val screenPoints = screen.mapIndexed { index, p ->
            BodyLandmark(index, p.x(), p.y(), p.z(), p.visibility().orElse(0.0f))
        }
        val worldPoints = world?.mapIndexed { index, p ->
            BodyLandmark(index, p.x(), p.y(), p.z(), p.visibility().orElse(0.0f))
        } ?: emptyList()
        return TrackedBodyFrame(
            timestampMs = timestampMs,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            imageLandmarks = screenPoints,
            worldLandmarks = worldPoints,
            segmentationMaskAvailable = result.segmentationMasks().isPresent
        )
    }

    override fun close() = landmarker.close()
}

internal data class BodyLandmark(
    val index: Int, val x: Float, val y: Float, val z: Float, val confidence: Float
)

internal data class TrackedBodyFrame(
    val timestampMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val imageLandmarks: List<BodyLandmark>,
    val worldLandmarks: List<BodyLandmark>,
    val segmentationMaskAvailable: Boolean
) {
    fun hasReliablePose(minConfidence: Float = 0.60f): Boolean {
        if (imageLandmarks.size < 33) return false
        for (index in intArrayOf(11, 12, 23, 24)) {
            val point = imageLandmarks.getOrNull(index) ?: return false
            if (point.confidence < minConfidence) return false
        }
        return true
    }

    fun regionBounds(indices: IntArray, minConfidence: Float = 0.35f): BodyRegionBounds? {
        val points = ArrayList<BodyLandmark>()
        for (index in indices) {
            val point = imageLandmarks.getOrNull(index) ?: continue
            if (point.confidence >= minConfidence && point.x.isFinite() && point.y.isFinite()) {
                points.add(point)
            }
        }
        if (points.size < 2) return null
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        for (point in points) {
            left = minOf(left, point.x)
            top = minOf(top, point.y)
            right = maxOf(right, point.x)
            bottom = maxOf(bottom, point.y)
        }
        return BodyRegionBounds(left, top, right, bottom)
    }

    /** Landmark envelopes only; not pixel-accurate body contours. */
    fun anatomyRegions(): Map<String, BodyRegionBounds?> = mapOf(
        "shoulders" to regionBounds(intArrayOf(11, 12)),
        "left_arm" to regionBounds(intArrayOf(11, 13, 15)),
        "right_arm" to regionBounds(intArrayOf(12, 14, 16)),
        "torso" to regionBounds(intArrayOf(11, 12, 23, 24)),
        "hips" to regionBounds(intArrayOf(23, 24)),
        "left_leg" to regionBounds(intArrayOf(23, 25, 27, 29, 31)),
        "right_leg" to regionBounds(intArrayOf(24, 26, 28, 30, 32))
    )
}

internal data class BodyRegionBounds(
    val left: Float, val top: Float, val right: Float, val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}
