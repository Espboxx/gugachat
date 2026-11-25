package com.gugachat.app.domain.images

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OnlineImageCanvasMathTest {

    @Test
    fun computeOutpaintCanvas_preservesAspectAndClampsWithinTargetBounds() {
        val canvas = OnlineImageCanvasMath.computeOutpaintCanvas(
            sourceWidth = 1024,
            sourceHeight = 512,
            targetWidth = 1536,
            targetHeight = 1536,
            anchor = OutpaintAnchor.BOTTOM_RIGHT,
            imageScale = 0.5f,
            offsetXNorm = 0f,
            offsetYNorm = 0f,
        )

        assertEquals(1536, canvas.width)
        assertEquals(1536, canvas.height)
        assertTrue(canvas.x >= 0 && canvas.y >= 0)
        assertTrue(canvas.x + canvas.image_width <= canvas.width)
        assertTrue(canvas.y + canvas.image_height <= canvas.height)
        assertEquals(canvas.width - canvas.image_width, canvas.x)
        assertEquals(canvas.height - canvas.image_height, canvas.y)
        val aspectDiff = abs((canvas.image_width.toDouble() / canvas.image_height) - 2.0)
        assertTrue(aspectDiff < 0.02)
    }

    @Test
    fun pixelMappingAndStrokePresenceCheck_workAccurately() {
        val (px, py) = OnlineImageCanvasMath.toPixelPoint(NormalizedMaskPoint(0.5f, 0.25f), 800, 1200)
        assertEquals(400f, px, 0.01f)
        assertEquals(300f, py, 0.01f)
        assertTrue(OnlineImageCanvasMath.toPixelRadius(0.05f, 800, 1200) >= 2f)

        assertFalse(OnlineImageCanvasMath.hasAnyPaintStrokes(listOf(MaskStroke(listOf(NormalizedMaskPoint(0.2f, 0.2f)), 0.05f, isEraser = true))))
        assertTrue(OnlineImageCanvasMath.hasAnyPaintStrokes(listOf(MaskStroke(listOf(NormalizedMaskPoint(0.2f, 0.2f)), 0.05f, isEraser = false))))
    }
}
