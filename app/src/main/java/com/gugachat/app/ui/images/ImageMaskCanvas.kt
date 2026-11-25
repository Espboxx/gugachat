package com.gugachat.app.ui.images

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gugachat.app.AppContainer
import com.gugachat.app.core.theme.LocalAppTheme
import com.gugachat.app.domain.images.MaskStroke
import com.gugachat.app.domain.images.NormalizedMaskPoint
import com.gugachat.app.domain.images.OnlineImageCanvasMath
import com.gugachat.app.domain.images.OutpaintAnchor
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun InpaintMaskCanvas(
    container: AppContainer,
    generationId: String,
    imageIndex: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    strokes: List<MaskStroke>,
    brushRadiusNormalized: Float,
    isEraser: Boolean,
    onAddStroke: (MaskStroke) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppTheme.current.colors
    val ratio = (sourceWidth.toFloat() / sourceHeight.coerceAtLeast(1).toFloat()).coerceIn(0.4f, 2.5f)
    var activePoints by remember { mutableStateOf<List<NormalizedMaskPoint>>(emptyList()) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(1.5.dp, c.outline, RoundedCornerShape(12.dp)),
    ) {
        AuthenticatedImage(
            container = container,
            generationId = generationId,
            imageIndex = imageIndex,
            variant = "original",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .pointerInput(brushRadiusNormalized, isEraser) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            val ny = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                            activePoints = listOf(NormalizedMaskPoint(nx, ny))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val nx = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            val ny = (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                            activePoints = activePoints + NormalizedMaskPoint(nx, ny)
                        },
                        onDragEnd = {
                            if (activePoints.isNotEmpty()) {
                                onAddStroke(
                                    MaskStroke(
                                        points = activePoints,
                                        brushRadiusNormalized = brushRadiusNormalized,
                                        isEraser = isEraser,
                                    ),
                                )
                            }
                            activePoints = emptyList()
                        },
                        onDragCancel = {
                            activePoints = emptyList()
                        },
                    )
                },
        ) {
            fun drawOneStroke(stroke: MaskStroke) {
                if (stroke.points.isEmpty()) return
                val baseDim = min(size.width, size.height)
                val strokeWidthPx = (stroke.brushRadiusNormalized * baseDim * 2f).coerceAtLeast(6f)
                val color = Color(0x99EF4444)
                val blend = if (stroke.isEraser) BlendMode.Clear else BlendMode.Src

                if (stroke.points.size == 1) {
                    val pt = stroke.points[0]
                    drawCircle(
                        color = color,
                        radius = strokeWidthPx / 2f,
                        center = Offset(pt.x * size.width, pt.y * size.height),
                        blendMode = blend,
                    )
                } else {
                    val path = Path().apply {
                        val first = stroke.points.first()
                        moveTo(first.x * size.width, first.y * size.height)
                        for (i in 1 until stroke.points.size) {
                            val p = stroke.points[i]
                            lineTo(p.x * size.width, p.y * size.height)
                        }
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        blendMode = blend,
                    )
                }
            }

            strokes.forEach(::drawOneStroke)
            if (activePoints.isNotEmpty()) {
                drawOneStroke(MaskStroke(activePoints, brushRadiusNormalized, isEraser))
            }
        }
    }
}

@Composable
fun OutpaintPreviewCanvas(
    container: AppContainer,
    generationId: String,
    imageIndex: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
    anchor: OutpaintAnchor,
    imageScale: Float,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppTheme.current.colors
    val canvasDto = remember(sourceWidth, sourceHeight, targetWidth, targetHeight, anchor, imageScale) {
        OnlineImageCanvasMath.computeOutpaintCanvas(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            anchor = anchor,
            imageScale = imageScale,
        )
    }
    val targetRatio = (canvasDto.width.toFloat() / canvasDto.height.coerceAtLeast(1).toFloat()).coerceIn(0.4f, 2.5f)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(targetRatio)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A2230))
            .border(1.5.dp, c.outline, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.TopStart,
    ) {
        val boxWPx = with(density) { maxWidth.toPx() }
        val boxHPx = with(density) { maxHeight.toPx() }
        val scaleX = boxWPx / canvasDto.width.coerceAtLeast(1)
        val scaleY = boxHPx / canvasDto.height.coerceAtLeast(1)

        val offsetX = (canvasDto.x * scaleX).roundToInt()
        val offsetY = (canvasDto.y * scaleY).roundToInt()
        val imgWDp = with(density) { (canvasDto.image_width * scaleX).toDp() }
        val imgHDp = with(density) { (canvasDto.image_height * scaleY).toDp() }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX, offsetY) }
                .size(width = imgWDp, height = imgHDp)
                .border(1.dp, c.accent),
        ) {
            AuthenticatedImage(
                container = container,
                generationId = generationId,
                imageIndex = imageIndex,
                variant = "original",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
