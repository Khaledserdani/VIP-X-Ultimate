package com.vipx.ultimate

/**
 * Temporal stabilizer for garment-only preview vertices.
 * Body anchors always follow the current frame exactly; only free cloth vertices
 * are blended to reduce frame-to-frame jitter. This does not estimate depth.
 */
internal object GarmentTemporalStabilizer {
    fun smooth(
        previous: GarmentOverlayMesh?,
        current: GarmentOverlayMesh,
        smoothing: Float = 0.65f
    ): GarmentOverlayMesh {
        require(smoothing in 0f..1f)
        if (!current.valid || previous == null || !previous.valid ||
            previous.vertices.size != current.vertices.size ||
            previous.bodyAnchorIndices != current.bodyAnchorIndices) return current

        val anchors = current.bodyAnchorIndices.toSet()
        val vertices = current.vertices.mapIndexed { index, now ->
            if (index in anchors) now
            else {
                val before = previous.vertices[index]
                now.copy(
                    x = before.x + (now.x - before.x) * smoothing,
                    y = before.y + (now.y - before.y) * smoothing
                )
            }
        }
        return current.copy(vertices = vertices)
    }
}
