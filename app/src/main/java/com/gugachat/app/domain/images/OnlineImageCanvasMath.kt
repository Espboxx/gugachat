package com.gugachat.app.domain.images

import com.gugachat.app.data.api.OnlineImageCanvasDto
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ImageEditMode(val wireAction: String) {
    EDIT("edit"),
    VARIATION("variation"),
    INPAINT("inpaint"),
    OUTPAINT("outpaint"),
}

enum class OutpaintAnchor(val row: Int, val col: Int) {
    TOP_LEFT(0, 0),
    TOP(0, 1),
    TOP_RIGHT(0, 2),
    LEFT(1, 0),
    CENTER(1, 1),
    RIGHT(1, 2),
    BOTTOM_LEFT(2, 0),
    BOTTOM(2, 1),
    BOTTOM_RIGHT(2, 2),
}

data class NormalizedMaskPoint(
    val x: Float,
    val y: Float,
)

data class MaskStroke(
    val points: List<NormalizedMaskPoint>,
    val brushRadiusNormalized: Float, // relative to max(width, height) or width
    val isEraser: Boolean = false,
)

object OnlineImageCanvasMath {

    /**
     * Computes a backend-validated `OnlineImageCanvasDto` for `outpaint` that:
     * 1. Preserves source image aspect ratio as closely as integer rounding allows.
     * 2. Places `(image_width, image_height)` at `anchor` inside `(targetWidth, targetHeight)`,
     *    scaled by `imageScale` (0.15..1.0) plus pixel nudge (`offsetXNorm`, `offsetYNorm`).
     * 3. Clamps `(x, y, image_width, image_height)` strictly within `[0..targetWidth, 0..targetHeight]`.
     */
    fun computeOutpaintCanvas(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        anchor: OutpaintAnchor = OutpaintAnchor.CENTER,
        imageScale: Float = 0.65f,
        offsetXNorm: Float = 0f,
        offsetYNorm: Float = 0f,
    ): OnlineImageCanvasDto {
        val safeSrcW = max(1, sourceWidth)
        val safeSrcH = max(1, sourceHeight)
        val safeTargetW = max(64, targetWidth)
        val safeTargetH = max(64, targetHeight)

        val srcAspect = safeSrcW.toDouble() / safeSrcH.toDouble()
        val maxFitW: Int
        val maxFitH: Int
        if (safeTargetW.toDouble() / safeTargetH.toDouble() >= srcAspect) {
            maxFitH = safeTargetH
            maxFitW = (safeTargetH * srcAspect).roundToInt().coerceIn(1, safeTargetW)
        } else {
            maxFitW = safeTargetW
            maxFitH = (safeTargetW / srcAspect).roundToInt().coerceIn(1, safeTargetH)
        }

        val scale = imageScale.coerceIn(0.15f, 1.0f)
        val imgW = (maxFitW * scale).roundToInt().coerceIn(16, safeTargetW)
        val imgH = (imgW / srcAspect).roundToInt().coerceIn(16, safeTargetH)
        // Re-derive imgW from imgH if clamped by height so aspect ratio stays tight
        val finalW = if (imgH == safeTargetH) (safeTargetH * srcAspect).roundToInt().coerceIn(16, safeTargetW) else imgW
        val finalH = imgH

        val maxOffsetX = max(0, safeTargetW - finalW)
        val maxOffsetY = max(0, safeTargetH - finalH)

        val baseAnchorX = when (anchor.col) {
            0 -> 0
            2 -> maxOffsetX
            else -> maxOffsetX / 2
        }
        val baseAnchorY = when (anchor.row) {
            0 -> 0
            2 -> maxOffsetY
            else -> maxOffsetY / 2
        }

        val x = (baseAnchorX + (offsetXNorm * maxOffsetX).roundToInt()).coerceIn(0, maxOffsetX)
        val y = (baseAnchorY + (offsetYNorm * maxOffsetY).roundToInt()).coerceIn(0, maxOffsetY)

        return OnlineImageCanvasDto(
            width = safeTargetW,
            height = safeTargetH,
            x = x,
            y = y,
            image_width = finalW,
            image_height = finalH,
        )
    }

    /**
     * Converts a normalized `[0..1, 0..1]` stroke coordinate to integer pixel space `[0..width-1, 0..height-1]`.
     */
    fun toPixelPoint(point: NormalizedMaskPoint, width: Int, height: Int): Pair<Float, Float> {
        val px = point.x.coerceIn(0f, 1f) * max(1, width)
        val py = point.y.coerceIn(0f, 1f) * max(1, height)
        return px to py
    }

    /**
     * Converts normalized brush radius to pixel radius, ensuring at least 2px in output mask.
     */
    fun toPixelRadius(radiusNormalized: Float, width: Int, height: Int): Float {
        val base = min(max(1, width), max(1, height)).toFloat()
        return max(2f, radiusNormalized.coerceIn(0.005f, 0.25f) * base)
    }

    /**
     * Returns true if at least one non-eraser stroke exists (quick guard before Bitmap rasterization).
     */
    fun hasAnyPaintStrokes(strokes: List<MaskStroke>): Boolean =
        strokes.any { !it.isEraser && it.points.isNotEmpty() }
}
