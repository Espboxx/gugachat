package com.gugachat.app.domain.images

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Encodes user inpaint brush strokes into an exact `sourceWidth x sourceHeight` ARGB_8888 PNG.
 *
 * Per `sub2api-auto/backend/internal/service/online_image_edit.go`:
 * - Editable / painted pixels MUST have `alpha < 255` (e.g. `alpha = 0` transparent).
 * - Preserved / unmasked pixels MUST have `alpha == 255` (opaque).
 */
object MaskPngEncoder {

    fun encodeMaskPng(
        sourceWidth: Int,
        sourceHeight: Int,
        strokes: List<MaskStroke>,
    ): ByteArray? {
        if (!OnlineImageCanvasMath.hasAnyPaintStrokes(strokes)) return null
        val w = max(1, sourceWidth)
        val h = max(1, sourceHeight)

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            // Fill opaque black: alpha == 255 everywhere = preserved
            canvas.drawColor(Color.argb(255, 0, 0, 0), PorterDuff.Mode.SRC)

            val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            val restorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = Color.argb(255, 0, 0, 0)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
            }

            for (stroke in strokes) {
                if (stroke.points.isEmpty()) continue
                val radiusPx = OnlineImageCanvasMath.toPixelRadius(stroke.brushRadiusNormalized, w, h)
                val paint = if (stroke.isEraser) restorePaint else erasePaint
                paint.strokeWidth = radiusPx * 2f

                if (stroke.points.size == 1) {
                    val (px, py) = OnlineImageCanvasMath.toPixelPoint(stroke.points[0], w, h)
                    val dotPaint = Paint(paint).apply { style = Paint.Style.FILL }
                    canvas.drawCircle(px, py, radiusPx, dotPaint)
                } else {
                    val path = Path()
                    val (sx, sy) = OnlineImageCanvasMath.toPixelPoint(stroke.points[0], w, h)
                    path.moveTo(sx, sy)
                    for (i in 1 until stroke.points.size) {
                        val (px, py) = OnlineImageCanvasMath.toPixelPoint(stroke.points[i], w, h)
                        path.lineTo(px, py)
                    }
                    canvas.drawPath(path, paint)
                }
            }

            if (!hasAnyEditablePixel(bitmap)) return null

            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            return out.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    private fun hasAnyEditablePixel(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (p in pixels) {
                val alpha = (p ushr 24) and 0xFF
                if (alpha < 255) return true
            }
        }
        return false
    }
}
