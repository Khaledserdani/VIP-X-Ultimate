package com.vipx.ultimate

/** Immutable torso mesh generated only from complete, independently measured sections. */
internal data class BodySurfaceVertex(
    val xCm: Double,
    val yRatio: Double,
    val zCm: Double,
    val sectionName: String,
    val source: SurfaceSectionSource,
    val confidence: Double
)

internal data class BodySurfaceMesh(
    val vertices: List<BodySurfaceVertex>,
    /** Each face is a triangle of indices into [vertices]. */
    val triangles: List<IntArray>,
    val ringCount: Int,
    val segmentsPerRing: Int
) {
    companion object {
        /**
         * Builds closed elliptical rings and joins adjacent rings.
         * Coordinates are body-local: x=left/right, y=normalized torso height,
         * z=front/back. This mesh is a torso scaffold, not a full human scan.
         */
        fun fromModel(model: BodySurfaceModel, segmentsPerRing: Int = 24): BodySurfaceMesh? {
            if (!model.ready || model.sections.size < 2 || segmentsPerRing < 8 ||
                model.sections.any { !it.valid || it.halfWidthCm == null ||
                    it.halfDepthCm == null || it.source == SurfaceSectionSource.UNAVAILABLE }) {
                return null
            }

            val vertices = ArrayList<BodySurfaceVertex>(model.sections.size * segmentsPerRing)
            model.sections.forEach { section ->
                val width = section.halfWidthCm ?: return null
                val depth = section.halfDepthCm ?: return null
                repeat(segmentsPerRing) { index ->
                    val angle = 2.0 * Math.PI * index / segmentsPerRing
                    vertices += BodySurfaceVertex(
                        xCm = width * kotlin.math.cos(angle),
                        yRatio = section.heightRatio,
                        zCm = depth * kotlin.math.sin(angle),
                        sectionName = section.name,
                        source = section.source,
                        confidence = section.confidence
                    )
                }
            }

            // Add cap centers so the torso scaffold is a watertight closed surface.
            val bottom = model.sections.first()
            val top = model.sections.last()
            val bottomCenter = vertices.size
            vertices += BodySurfaceVertex(0.0, bottom.heightRatio, 0.0, "bottom-cap", bottom.source, bottom.confidence)
            val topCenter = vertices.size
            vertices += BodySurfaceVertex(0.0, top.heightRatio, 0.0, "top-cap", top.source, top.confidence)

            val triangles = ArrayList<IntArray>((model.sections.size - 1) * segmentsPerRing * 2 + segmentsPerRing * 2)
            for (ring in 0 until model.sections.lastIndex) {
                for (segment in 0 until segmentsPerRing) {
                    val next = (segment + 1) % segmentsPerRing
                    val a = ring * segmentsPerRing + segment
                    val b = ring * segmentsPerRing + next
                    val c = (ring + 1) * segmentsPerRing + segment
                    val d = (ring + 1) * segmentsPerRing + next
                    triangles += intArrayOf(a, c, b)
                    triangles += intArrayOf(b, c, d)
                }
            }
            val lastRingStart = (model.sections.lastIndex) * segmentsPerRing
            for (segment in 0 until segmentsPerRing) {
                val next = (segment + 1) % segmentsPerRing
                // Opposite winding on the two caps keeps their normals outward.
                triangles += intArrayOf(bottomCenter, next, segment)
                triangles += intArrayOf(topCenter, lastRingStart + segment, lastRingStart + next)
            }
            return BodySurfaceMesh(vertices, triangles, model.sections.size, segmentsPerRing)
        }
    }
}
