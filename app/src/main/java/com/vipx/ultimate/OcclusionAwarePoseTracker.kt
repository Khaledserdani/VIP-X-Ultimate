package com.vipx.ultimate

/** Short-horizon tracker for temporary occlusion. Predictions are never marked as observations. */
internal class OcclusionAwarePoseTracker(
    private val observationThreshold: Float = 0.45f,
    private val maxPredictionAgeMs: Long = 350L,
    private val confidenceDecayPer100Ms: Float = 0.16f
) {
    private data class History(
        val point: BodyLandmark,
        val timestampMs: Long,
        val olderPoint: BodyLandmark?,
        val olderTimestampMs: Long?
    )
    private val history = HashMap<Int, History>()
    private var lastTimestampMs: Long? = null

    fun update(points: List<BodyLandmark>, timestampMs: Long): List<BodyLandmark> {
        require(timestampMs >= 0L)
        val last = lastTimestampMs
        if (last != null && (timestampMs <= last || timestampMs - last > 1000L)) reset()
        lastTimestampMs = timestampMs
        return points.map { raw ->
            if (raw.x.isFinite() && raw.y.isFinite() && raw.z.isFinite() &&
                raw.confidence >= observationThreshold) {
                val previous = history[raw.index]
                history[raw.index] = History(raw, timestampMs, previous?.point, previous?.timestampMs)
                raw.copy(source = LandmarkSource.OBSERVED)
            } else predict(raw, timestampMs)
        }
    }

    private fun predict(raw: BodyLandmark, nowMs: Long): BodyLandmark {
        val h = history[raw.index]
            ?: return raw.copy(source = LandmarkSource.UNKNOWN, confidence = 0f)
        val age = nowMs - h.timestampMs
        if (age <= 0L || age > maxPredictionAgeMs) {
            history.remove(raw.index)
            return raw.copy(source = LandmarkSource.UNKNOWN, confidence = 0f)
        }
        val older = h.olderPoint
        val olderTime = h.olderTimestampMs
        val scale = if (older != null && olderTime != null && h.timestampMs > olderTime) {
            (age.toFloat() / (h.timestampMs - olderTime).toFloat()).coerceIn(0f, 1.5f)
        } else 0f
        fun estimate(now: Float, before: Float?): Float {
            if (before == null) return now
            return now + ((now - before) * scale).coerceIn(-0.12f, 0.12f)
        }
        val confidence = (h.point.confidence - age / 100f * confidenceDecayPer100Ms)
            .coerceIn(0.05f, 0.44f)
        return BodyLandmark(
            raw.index,
            estimate(h.point.x, older?.x),
            estimate(h.point.y, older?.y),
            estimate(h.point.z, older?.z),
            confidence,
            LandmarkSource.TEMPORAL_PREDICTED
        )
    }

    fun reset() {
        history.clear()
        lastTimestampMs = null
    }
}
