package com.vipx.ultimate

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

/**
 * On-device body pose tracker.
 * Add pose_landmarker_full.task to app/src/main/assets before enabling it.
 * Landmarks are not exact body circumferences or a dense body surface.
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

    /** VIDEO timestamps must increase monotonically. */
    fun detect(bitmap: Bitmap, timestampMs: Long): TrackedBodyFrame? {
        require(timestampMs >= 0L)
        val result = landmarker.detectForVideo(BitmapImageBuilder(bitmap).build(), timestampMs)
        val screen = result.landmarks().firstOrNull() ?: return null
        val world = result.worldLandmarks().firstOrNull().orEmpty()
        fun convert(points: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>) =
            points.mapIndexed { index, p ->
                BodyLandmark(index, p.x(), p.y(), p.z(), p.visibility())
            }
        return TrackedBodyFrame(
            timestampMs = timestampMs,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            imageLandmarks = convert(screen),
            worldLandmarks = convert(world),
            segmentationMaskAvailable = result.segmentationMasks()?.isNotEmpty() == true
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
    fun hasReliablePose(minConfidence: Float = 0.60f): Boolean =
        imageLandmarks.size >= 33 && listOf(11, 12, 23, 24).all { i ->
            imageLandmarks.getOrNull(i)?.confidence?.let { it >= minConfidence } == true
        }

    fun regionBounds(indices: IntArray, minConfidence: Float = 0.35f): BodyRegionBounds? {
        val points = indices.mapNotNull { imageLandmarks.getOrNull(it) }
            .filter { it.confidence >= minConfidence && it.x.isFinite() && it.y.isFinite() }
        if (points.size < 2) return null
        return BodyRegionBounds(
            points.minOf { it.x }, points.minOf { it.y },
            points.maxOf { it.x }, points.maxOf { it.y }
        )
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
