package com.vipx.ultimate

import kotlin.math.sqrt

/** Small deterministic cloth-mesh stepper for preview prototypes.
 * Coordinates are normalized image-space units. It simulates CLOTH vertices only;
 * body landmarks/shape are read-only anchors and are never displaced.
 */
internal data class ClothPoint(val x: Double, val y: Double)
internal data class ClothLink(val a: Int, val b: Int, val restLength: Double)
internal data class ClothVertex(
    val position: ClothPoint,
    val previous: ClothPoint,
    val pinned: Boolean = false
)
internal data class ClothFrame(
    val vertices: List<ClothVertex>,
    val finite: Boolean,
    val maximumLinkError: Double
)

internal object GarmentClothSolver {
    fun step(
        vertices: List<ClothVertex>,
        links: List<ClothLink>,
        gravityY: Double,
        damping: Double = 0.985,
        constraintPasses: Int = 5,
        fixedAnchors: Map<Int, ClothPoint> = emptyMap()
    ): ClothFrame {
        require(vertices.isNotEmpty())
        require(gravityY.isFinite() && damping in 0.0..1.0)
        require(constraintPasses in 1..20)
        require(links.all { it.a in vertices.indices && it.b in vertices.indices &&
            it.a != it.b && it.restLength.isFinite() && it.restLength > 0.0 })
        require(fixedAnchors.keys.all { it in vertices.indices })
        require(fixedAnchors.values.all { it.x.isFinite() && it.y.isFinite() })

        val next = vertices.toMutableList()
        for (i in next.indices) {
            val old = next[i]
            val anchor = fixedAnchors[i]
            if (old.pinned || anchor != null) {
                val p = anchor ?: old.position
                next[i] = old.copy(position = p, previous = p)
            } else {
                val vx = (old.position.x - old.previous.x) * damping
                val vy = (old.position.y - old.previous.y) * damping
                val p = ClothPoint(old.position.x + vx, old.position.y + vy + gravityY)
                next[i] = old.copy(position = p, previous = old.position)
            }
        }

        repeat(constraintPasses) {
            for (link in links) {
                val va = next[link.a]
                val vb = next[link.b]
                val dx = vb.position.x - va.position.x
                val dy = vb.position.y - va.position.y
                val distance = sqrt(dx * dx + dy * dy)
                if (distance < 1e-9) continue
                val error = (distance - link.restLength) / distance
                val aFixed = va.pinned || fixedAnchors.containsKey(link.a)
                val bFixed = vb.pinned || fixedAnchors.containsKey(link.b)
                val wa = if (aFixed) 0.0 else 1.0
                val wb = if (bFixed) 0.0 else 1.0
                val total = wa + wb
                if (total == 0.0) continue
                val cx = dx * error
                val cy = dy * error
                if (wa > 0.0) next[link.a] = va.copy(position =
                    ClothPoint(va.position.x + cx * wa / total, va.position.y + cy * wa / total))
                if (wb > 0.0) next[link.b] = vb.copy(position =
                    ClothPoint(vb.position.x - cx * wb / total, vb.position.y - cy * wb / total))
            }
            fixedAnchors.forEach { (index, point) ->
                next[index] = next[index].copy(position = point, previous = point)
            }
        }

        val finite = next.all {
            it.position.x.isFinite() && it.position.y.isFinite() &&
            it.previous.x.isFinite() && it.previous.y.isFinite()
        }
        val maxError = links.maxOfOrNull { link ->
            val a = next[link.a].position
            val b = next[link.b].position
            kotlin.math.abs(sqrt((b.x-a.x)*(b.x-a.x)+(b.y-a.y)*(b.y-a.y)) - link.restLength)
        } ?: 0.0
        return ClothFrame(next, finite, maxError)
    }
}
