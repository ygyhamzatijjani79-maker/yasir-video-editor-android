package com.mc2islamictv.editor

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Single source of truth for the video aperture and fit/zoom calculation.
 * Preview and Media3 export use this exact geometry calculation; only the
 * final coordinate-system mapping differs (TextureView pixels vs Media3 NDC).
 */
internal object VideoCompositionGeometry {
    const val OUTPUT_SIZE = 1080f
    const val APERTURE_WIDTH = 0.575f
    const val APERTURE_HEIGHT = 0.70f
    const val APERTURE_LEFT = 0.38f
    const val APERTURE_TOP = 0.055f
    const val USER_PAN_LIMIT = 0.45f

    fun calculateScale(
        sourceWidth: Float,
        sourceHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        rotationDegrees: Float,
        zoom: Float,
        crop: Float
    ): Float {
        val angle = Math.toRadians(rotationDegrees.toDouble())
        val rotatedWidth = abs(sourceWidth * cos(angle)) +
            abs(sourceHeight * sin(angle))
        val rotatedHeight = abs(sourceHeight * cos(angle)) +
            abs(sourceWidth * sin(angle))
        val baseScale = max(
            viewportWidth / rotatedWidth.toFloat(),
            viewportHeight / rotatedHeight.toFloat()
        )
        val userScale = (
            zoom.coerceIn(1f, 4f) / (1f - crop.coerceIn(0f, 0.4f))
        ).coerceIn(1f, 7f)
        return baseScale * userScale
    }
}
