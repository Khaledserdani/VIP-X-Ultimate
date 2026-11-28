package com.vipx.ultimate

import kotlin.math.abs

/** Dense person foreground mask summary. Ratios describe the visible silhouette, not body circumference. */
internal data class PersonSilhouette(
    val maskWidth: Int,
    val maskHeight: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val foregroundRatio: Float,
    val horizontalBands: List<Float>,
    val rowWidths: List<Float>
) {
    val valid: Boolean get() = right > left && bottom > top &&
        foregroundRatio in 0.015f..0.92f && rowWidths.size >= 12

    companion object {
        fun fromConfidenceMask(width: Int, height: Int, confidence: FloatArray): PersonSilhouette? {
            if (width <= 0 || height <= 0 || confidence.size != width * height) return null
            val minConfidence = 0.50f
            var left=width; var top=height; var right=-1; var bottom=-1
            var foreground=0
            for (y in 0 until height) for (x in 0 until width) {
                if (confidence[y*width+x] >= minConfidence) {
                    foreground++
                    left=minOf(left,x); right=maxOf(right,x)
                    top=minOf(top,y); bottom=maxOf(bottom,y)
                }
            }
            if (right <= left || bottom <= top || foreground < width*height*0.01f) return null
            val boxWidth=(right-left+1).toFloat()
            val boxHeight=(bottom-top+1).toFloat()
            val rows=ArrayList<Float>(32)
            for (band in 0 until 32) {
                val y=(top + ((band+0.5f)/32f*boxHeight).toInt()).coerceIn(top,bottom)
                var rowLeft=width; var rowRight=-1
                for (x in left..right) if (confidence[y*width+x] >= minConfidence) {
                    rowLeft=minOf(rowLeft,x); rowRight=maxOf(rowRight,x)
                }
                rows.add(if(rowRight>=rowLeft) ((rowRight-rowLeft+1)/boxWidth).coerceIn(0f,1f) else 0f)
            }
            val levels=floatArrayOf(.16f,.28f,.40f,.52f,.64f,.76f,.88f)
            val bands=levels.map { level -> rows[(level*31f).toInt().coerceIn(0,31)] }
            return PersonSilhouette(width,height,left,top,right,bottom,
                foreground.toFloat()/(width*height),bands,rows).takeIf { it.valid }
        }
    }
}

internal data class BodySurfaceChange(
    val meanContourChangePercent: Float,
    val bandChangesPercent: List<Float>,
    val largestBandChangePercent: Float,
    val reliable: Boolean
)

/** Compares normalized visible contours, reducing sensitivity to camera distance and translation. */
internal object BodySurfaceChangeAnalyzer {
    fun compare(reference: PersonSilhouette, current: PersonSilhouette): BodySurfaceChange {
        if (!reference.valid || !current.valid ||
            reference.rowWidths.size != current.rowWidths.size ||
            reference.horizontalBands.size != current.horizontalBands.size) {
            return BodySurfaceChange(0f, emptyList(), 0f, false)
        }
        val differences=reference.horizontalBands.indices.map { i ->
            abs(current.horizontalBands[i]-reference.horizontalBands[i])*100f
        }
        val mean=reference.rowWidths.indices.map { i ->
            abs(current.rowWidths[i]-reference.rowWidths[i])*100f
        }.average().toFloat()
        return BodySurfaceChange(mean,differences,differences.maxOrNull() ?: 0f,
            mean.isFinite() && mean < 65f)
    }
}
