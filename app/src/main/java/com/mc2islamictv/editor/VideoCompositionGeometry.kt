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

    /**
     * Returns safe normalized pan limits for the visible, cropped portion of
     * the source. This lets long-press drag work at 1x when the source already
     * overflows the aperture, without exposing empty space outside the video.
     */
    fun calculatePanLimits(
        sourceWidth: Float,
        sourceHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        rotationDegrees: Float,
        zoom: Float,
        crop: Float
    ): Pair<Float, Float> {
        if (sourceWidth <= 0f || sourceHeight <= 0f ||
            viewportWidth <= 0f || viewportHeight <= 0f
        ) return 0f to 0f

        val angle = Math.toRadians(rotationDegrees.toDouble())
        val rotatedWidth = abs(sourceWidth * cos(angle)) +
            abs(sourceHeight * sin(angle))
        val rotatedHeight = abs(sourceHeight * cos(angle)) +
            abs(sourceWidth * sin(angle))
        val scale = calculateScale(
            sourceWidth, sourceHeight, viewportWidth, viewportHeight,
            rotationDegrees, zoom, crop
        )
        val availableX = max(0f, (rotatedWidth.toFloat() * scale - viewportWidth) / 2f)
        val availableY = max(0f, (rotatedHeight.toFloat() * scale - viewportHeight) / 2f)
        val panRangeX = viewportWidth * USER_PAN_LIMIT
        val panRangeY = viewportHeight * USER_PAN_LIMIT
        val limitX = if (panRangeX > 0f) (availableX / panRangeX).coerceIn(0f, 1f) else 0f
        val limitY = if (panRangeY > 0f) (availableY / panRangeY).coerceIn(0f, 1f) else 0f
        return limitX to limitY
    }
}
